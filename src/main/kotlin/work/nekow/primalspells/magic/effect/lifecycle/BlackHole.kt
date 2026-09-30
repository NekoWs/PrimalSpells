package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle

/**
 * 黑洞 —— 半径 5 的引力场：
 * - **生物**：迟缓 I，并按 `0.12 / (距离 + 0.5)` 每 tick 拉向中心；
 * - **魔法投射物**：每 tick 沿指向中心的方向加速（吸力同样随距离衰减）—— 把飞进来的法术吸进去；
 * - **粒子**（ParticleDrawing）分两层：
 *   1. **范围球壳**：半径 [EFFECT_RADIUS] 的球面，[SHELL_PARTICLES] 个**永存**粒子，
 *      标识黑洞的**大小/作用范围**，并带**进场 / 退场动画**（见下）；
 *   2. **被吸入的粒子**：每 tick 在球壳上生成 [PARTICLES_PER_TICK] 个（第一颗亮、其余暗紫），
 *      初速度取**随机切向**（绕中心旋转），随后被吸向中心，抵达中心（[CONSUME_RADIUS] 内）即销毁。
 *
 * ## 球体的进入 / 退出动画：**整壳的每颗粒子都参与**
 *
 * 第 15 轮的做法是「球壳淡入 + 另外撒一圈向心粒子」，实机反馈是「外面的大球不动、
 * 里面另有一个小球在缩」—— 动画与球壳脱节。第 16 轮改成**球壳本身**做径向运动：
 *
 * - **进场**：整壳粒子先在球心附近（[ENTER_START_RADIUS_SCALE] 倍半径）生成，随即逐粒子下发
 *   `ParticleHandle.move(自己的球面位置, [ENTER_TICKS], EASE_OUT)` —— **PD 在客户端逐帧缓动**，
 *   服务端只发一次指令。观感是「这颗球**从中心长出来**」；
 * - **退场**：把整壳粒子按**原位置、原颜色**重新生成一次，初速指向中心、寿命 = [EXIT_TICKS]
 *   （速度 = 距离 / 寿命）→ 整颗球**同步向中心塌缩**，抵达中心那一刻全部过期消失。
 *   为什么是「重新生成」而不是给旧粒子设速度：球壳粒子是永存的，而 `ParticleHandle` 没有改寿命的入口 ——
 *   不重新生成就会在抵达中心后继续飞出去，变成永远留在世界里的粒子。
 *
 * 两条路径都是**一次性**的：进场 1 包/粒子（缓动移动）、退场 2 包/粒子（销毁 + 重新生成 + 初速），
 * 稳态 **0 包/tick**。（第 15 轮的组级 `fadeIn`/`spin` 随之去掉：组要额外维护程序与生命周期，
 * 而自转在这种球壳上几乎看不出来；见 `docs/issues/resolved.md` 第 16 轮。）
 *
 * ## 吸力：越近越强 + 阻尼（阻尼系数是「能否落到中心」的唯一开关）
 *
 * 加速度随距离增强（`0.12/(距离+0.5)`），并且每 tick 顺带乘一点阻尼（[DAMPING_PER_TICK]）让切向速度衰减。
 * 阻尼太弱时粒子会形成**偏心率很大的椭圆轨道**：半径降到某个值后来回振荡，永远进不了中心。
 * 实测（500 个随机样本数值复算）：`0.995` → 抵达中心 **0/500**（在 r≈2.2~5.0 之间绕到寿命结束）；
 * 现用的 **[DAMPING_PER_TICK] = 0.9** → **100%** 抵达中心，平均 **34 tick**。
 * 参数取值过程见 `docs/issues/resolved.md` 第 11 轮。
 *
 * ## 运动：速度每 tick 由本模组重算，位置积分归 PD
 *
 * 吸力**随距离变化**，PD 的 `applyForce` 那种「一次下发的恒定加速度」表达不了 —— 每 tick 都得重算每颗粒子
 * 该有的速度。但**位置不再由本模组积分**：`ParticleBatch.setVelocityAll(velocities)` 一次包覆盖全组
 * （**1 包/tick，与粒子数无关**），之后由 PD 逐 tick 做 `pos += vel`（服务端权威；暂停时客户端不再外推）。
 *
 * 好处：位置与速度都读 PD 的权威值（`forEach` / `removeIf`），**本模组不持有任何跨 tick 的粒子状态**。
 * 渲染/同步/寿命/可见性依旧由 ParticleDrawing 负责。
 */
class BlackHole : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
) {
    /**
     * 范围球壳：成员固定的 `ParticleBatch`（进场时一次性铺满、永存）。
     *
     * 用 `ParticleBatch` 而不是 `ParticleGroup`：本轮进/退场都是**逐粒子径向**动画，
     * 而组级编排只能整组统一变换（且 `ParticleGroup.scale` 目前不缩「到轴心的距离」，
     * 见 `docs/issues/pd-api-requests.md`）—— 逐粒子的径向运动会与组的客户端指令互相覆盖。
     * 代价：球壳是**固定成员集**，不再有 `ensureSize` 的缺量补齐（个别生成被上限拒绝时留空缺）。
     */
    private var shell: ParticleBatch? = null

    /** 被吸入的粒子：成员、速度、位置全部由 PD 维护，本模组只每 tick 重算一次速度。 */
    private var sucked: ParticleBatch? = null

    /** 球壳取点（单位方向，Fibonacci 均匀分布）：结果确定，算一次即可。 */
    private val shellOffsets: List<Vec3> by lazy { ParticleShapes.fibonacciSphere(SHELL_PARTICLES) }

    /**
     * 球壳每颗粒子的颜色：`fibonacciSphere` 由 +y 往 −y 排，所以下标 i 直接对应「从顶到底」的渐变参数；
     * 每隔 [SPARKLE_EVERY] 颗掺一个亮紫星点。
     */
    private val shellColors: List<Color> by lazy {
        List(SHELL_PARTICLES) { i ->
            val base = SHELL_RIM.lerp(SHELL_CORE, ((i + 0.5) / SHELL_PARTICLES).toFloat())
            if (i % SPARKLE_EVERY == 0) base.lerp(SHELL_SPARK, 0.7f) else base
        }
    }

    /** 首 tick 铺球壳（含进场动画），之后每 tick 只补被吸入的粒子。 */
    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        if (shell == null) spawnShell(manager, center)
        spawnSucked(manager, center)
    }

    /**
     * 铺球壳 + 进场动画：每颗粒子在球心附近生成，随即被下一条指令「缓动」送到自己的球面位置。
     *
     * 缓动由 PD 在客户端求值（服务端只发一次指令），所以整个过程 **0 逐 tick 包**；
     * `EASE_OUT` = 起步快、收尾慢，看起来是「炸开之后稳稳停在球面上」。
     */
    private fun spawnShell(manager: ParticleManager, center: Vec3) {
        val batch = ParticleBatch(manager)
        for (i in 0 until SHELL_PARTICLES) {
            val dir = shellOffsets[i]
            val start = center.add(dir.scale(EFFECT_RADIUS * ENTER_START_RADIUS_SCALE))
            val target = center.add(dir.scale(EFFECT_RADIUS))
            val handle = manager.spawnParticle(start, shellColors[i], SHELL_SCALE)
            handle?.move(target, ENTER_TICKS, EasingType.EASE_OUT)
            batch.add(handle)
        }
        shell = batch
    }

    /** 每 tick 在球壳上生成被吸入的粒子（第一颗亮、其余暗紫），初速沿随机切向。 */
    private fun spawnSucked(manager: ParticleManager, center: Vec3) {
        val random = manager.level.random
        val batch = sucked ?: ParticleBatch(manager).also { sucked = it }
        repeat(PARTICLES_PER_TICK) { index ->
            val radial = ParticleShapes.randomUnitVector(random)
            val pos = center.add(radial.scale(EFFECT_RADIUS))
            // 初速度取随机切向（绕中心转）：一次速度包，之后每 tick 由 updateVisuals 重算
            val tangent = ParticleShapes.randomTangent(radial, random).scale(TANGENTIAL_SPEED)
            val spark = index == 0
            batch.add(
                manager.spawnParticle(
                    pos,
                    if (spark) SPARK_COLOR else SUCTION_COLOR,
                    if (spark) SPARK_SCALE else SUCTION_SCALE,
                    LIFETIME_TICKS,
                    tangent,
                )
            )
        }
    }

    /**
     * 每 tick 重算每颗粒子的速度并**一次包**下发；位置由 PD 积分，抵达中心的粒子销毁。
     *
     * 顺序：`forEach` 读 PD 的权威 pos/vel 算出本 tick 速度 → `setVelocityAll` 一包下发 →
     * 再按位置回收（读 PD 权威位置，读不产包）。全程本模组不留跨 tick 状态。
     */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        val batch = sucked ?: return
        if (batch.isEmpty()) return

        // 速度迭代：v ← v·阻尼 + 向心加速度（随距离增强）。位置由 PD 做 pos += v，与这里一一对应
        val velocities = ArrayList<Vec3>(batch.size())
        batch.forEach { _, _, pos, vel ->
            val toCenter = center.subtract(pos)
            velocities += vel.scale(DAMPING_PER_TICK)
                .add(toCenter.normalize().scale(PULL_PER_TICK / (toCenter.length() + 0.5)))
        }
        batch.setVelocityAll(velocities)

        // 抵达中心即销毁（读 PD 的权威位置）
        batch.removeIf { _, pos, _ -> pos.distanceTo(center) <= CONSUME_RADIUS }
    }

    /**
     * 效果结束：球壳（若已铺出）**整壳向中心塌缩**，被吸入的粒子立即销毁。
     *
     * 塌缩做法：先 `clear()` 掉原球壳，再在**同一批位置**上用相同的颜色/尺寸重新生成一遍，
     * 初速指向中心、大小 = `距离 / [EXIT_TICKS]`、寿命 = [EXIT_TICKS] —— 常量速度 + 自带寿命，
     * 位置由 PD 逐 tick 积分，所以**效果死掉之后动画照样放完**（本模组不需要再回来 tick），
     * 且粒子在抵达中心的那一刻全部过期，不会留在世界里。
     */
    override fun clearVisuals(manager: ParticleManager, center: Vec3) {
        if (shell != null) {
            shell?.clear()
            for (i in 0 until SHELL_PARTICLES) {
                val pos = center.add(shellOffsets[i].scale(EFFECT_RADIUS))
                val velocity = center.subtract(pos).scale(1.0 / EXIT_TICKS)
                manager.spawnParticle(pos, shellColors[i], SHELL_SCALE, EXIT_TICKS, velocity)
            }
        }
        shell = null
        sucked?.clear()
        sucked = null
    }

    /** 投射物：每 tick 沿指向中心的方向加速（吸力随距离衰减）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        val strength = (PULL_PER_TICK / (dist + 0.5)).toFloat()
        projectile.status.velocity.add(Vector3f(dirToCenter).mul(strength))
    }

    private companion object {
        /** 生效半径：同时是范围球壳与粒子生成半径 */
        const val EFFECT_RADIUS = 5.0

        /** 范围球壳：均匀（Fibonacci）球面取点，粒子越多球体越「实」 */
        const val SHELL_PARTICLES = 144
        const val SHELL_SCALE = 0.5f

        /** 球壳上缘（偏紫）/ 下缘（近黑）/ 星点 */
        val SHELL_RIM: Color = Color.ofInt(126, 74, 214, 120)
        val SHELL_CORE: Color = Color.ofInt(26, 18, 46, 165)
        val SHELL_SPARK: Color = Color.ofInt(214, 176, 255, 190)

        /** 每隔几颗掺一个星点 */
        const val SPARKLE_EVERY = 5

        // ---- 球壳进场 / 退场动画 ----

        /** 进场的起点半径（球壳半径的倍数）：整壳从这么小的球长到满半径 */
        const val ENTER_START_RADIUS_SCALE = 0.18

        /** 进场 / 退场时长（tick）：都走 `EASE_OUT` / 匀速，约 0.7 秒 */
        const val ENTER_TICKS = 14
        const val EXIT_TICKS = 14

        // ---- 被吸入的粒子 ----

        /** 每 tick 生成的被吸入粒子数（第一颗是亮紫小点，其余是暗紫大点） */
        const val PARTICLES_PER_TICK = 3

        /** 初始切向速度（blocks/tick）：决定起始绕转快慢 */
        const val TANGENTIAL_SPEED = 0.22

        /** 抵达中心判定半径（方块）：进入这个范围就销毁粒子 */
        const val CONSUME_RADIUS = 0.1

        /** 每次积分的阻尼系数。**这是「能否落到中心」的唯一开关**：0.995 太弱（实测 0/500 到中心，只在 r≈2.2~5.0 绕圈），0.9 → 100% 抵达、平均 34 tick（见类注释） */
        const val DAMPING_PER_TICK = 0.9

        /** 被吸入粒子的尺寸与寿命上限（0.9 阻尼下平均 34 tick 就抵达中心被销毁，这条寿命只是兜底） */
        const val SUCTION_SCALE = 0.55f
        const val SPARK_SCALE = 0.32f
        const val LIFETIME_TICKS = 60

        /** 暗紫（被吸入的主力）与亮紫（每 tick 的第一颗，给球壳一层「星尘」） */
        val SUCTION_COLOR: Color = Color.ofInt(158, 64, 255)
        val SPARK_COLOR: Color = Color.ofInt(226, 196, 255)

        /** 吸力系数（每 tick，对生物与投射物一致，按 1/(距离+0.5) 衰减） */
        const val PULL_PER_TICK = 0.12
    }
}
