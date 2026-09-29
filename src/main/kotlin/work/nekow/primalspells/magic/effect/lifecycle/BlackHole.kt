package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle

/**
 * 黑洞 —— 半径 5 的引力场：
 * - **生物**：迟缓 I，并按 `0.12 / (距离 + 0.5)` 每 tick 拉向中心；
 * - **魔法投射物**：每 tick 沿指向中心的方向加速（吸力同样随距离衰减）—— 把飞进来的法术吸进去；
 * - **粒子**（ParticleDrawing）分两层：
 *   1. **范围球壳**：在半径 [EFFECT_RADIUS] 的球面上均匀铺 [SHELL_PARTICLES] 个**灰黑色**半透明粒子并永存，
 *      用来标识黑洞的**大小/作用范围**（场本身静止，故不需要每 tick 跟踪；场结束时统一销毁）；
 *   2. **被吸入的粒子**：每 tick 在球壳上生成 [PARTICLES_PER_TICK] 个紫色粒子，初速度取**随机切向**
 *      （绕中心旋转），随后被吸向中心，抵达中心（[CONSUME_RADIUS] 内）即销毁。
 *
 * 成员簿记交给 PD 的 [ParticleBatch]（增量登记、过期自动出列、`removeIf` 按条件回收、`ensureSize` 补齐球壳）；
 * **运动状态也全在 PD 手里**（见下），本模组只算「本 tick 每颗粒子该有的速度」。
 *
 * ## 吸力：越近越强 + 阻尼（阻尼系数是「能否落到中心」的唯一开关）
 *
 * 加速度随距离增强（`0.12/(距离+0.5)`），并且每 tick 顺带乘一点阻尼（[DAMPING_PER_TICK]）让切向速度衰减。
 * 阻尼太弱时粒子会形成**偏心率很大的椭圆轨道**：半径降到某个值后来回振荡，永远进不了中心。
 * 实测（500 个随机样本数值复算）：`0.995` → 抵达中心 **0/500**（在 r≈2.2~5.0 之间绕到寿命结束）；
 * 现用的 **[DAMPING_PER_TICK] = 0.9** → **100%** 抵达中心，平均 **34 tick**（峰值存量随之从 60 降到 ~34）。
 * 参数取值过程见 `docs/issues/resolved.md` 第 11 轮。
 *
 * ## 运动：速度每 tick 由本模组重算，位置积分归 PD
 *
 * 吸力**随距离变化**，PD 的 `applyForce` 那种「一次下发的恒定加速度」表达不了 —— 每 tick 都得重算每颗粒子
 * 该有的速度。但**位置不再由本模组积分**：`ParticleBatch.setVelocityAll(velocities)` 一次包覆盖全组
 * （**1 包/tick，与粒子数无关**），之后由 PD 逐 tick 做 `pos += vel`（服务端权威；暂停时客户端不再外推）。
 *
 * 好处：位置与速度都读 PD 的权威值（`forEach` / `removeIf`），**本模组不持有任何跨 tick 的粒子状态**
 * （第 11 轮之前这里挂着一张「粒子 id → 速度」表，那是当时 PD 缺批量设速/施力的临时降级；
 * 1.0.10-ALPHA 补上 `setVelocityAll` / `applyForceAll` 后已删除 —— 见 `docs/issues/resolved.md` 第 12 轮）。
 * 渲染/同步/寿命/可见性依旧由 ParticleDrawing 负责。
 */
class BlackHole : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
) {
    /** 范围球壳：永存粒子，首 tick 铺满，之后只在 PD 侧有损耗时补齐（[ParticleBatch.ensureSize]）。 */
    private var shell: ParticleBatch? = null

    /** 被吸入的粒子：成员、速度、位置全部由 PD 维护，本模组只每 tick 重算一次速度。 */
    private var sucked: ParticleBatch? = null

    /** 球壳偏移（相对中心，半径 [EFFECT_RADIUS]）：Fibonacci 球结果确定，算一次即可。 */
    private val shellOffsets: List<Vec3> by lazy {
        ParticleShapes.fibonacciSphere(SHELL_PARTICLES).map { it.scale(EFFECT_RADIUS) }
    }

    /** 每 tick 在球壳上生成粒子，初速度沿随机切向；范围球壳只在首 tick 铺一次。 */
    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val random = caster.level().random

        val shellBatch = shell ?: ParticleBatch(manager).also { shell = it }
        // 均匀（Fibonacci 球）灰黑球壳：标识黑洞大小，永存到本效果结束
        shellBatch.ensureSize(SHELL_PARTICLES) { index ->
            manager.spawnParticle(center.add(shellOffsets[index]), SHELL_COLOR, SHELL_SCALE)
        }

        val suckedBatch = sucked ?: ParticleBatch(manager).also { sucked = it }
        repeat(PARTICLES_PER_TICK) {
            val radial = ParticleShapes.randomUnitVector(random)
            val pos = center.add(radial.scale(EFFECT_RADIUS))
            // 初速度取随机切向（绕中心转）：一次速度包，之后每 tick 由 updateVisuals 重算
            val tangent = ParticleShapes.randomTangent(radial, random).scale(TANGENTIAL_SPEED)
            suckedBatch.add(manager.spawnParticle(pos, COLOR, SCALE, LIFETIME_TICKS, tangent))
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

    override fun clearVisuals() {
        shell?.clear()
        sucked?.clear()
    }

    /** 投射物：每 tick 沿指向中心的方向加速（吸力随距离衰减）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        val strength = (PULL_PER_TICK / (dist + 0.5)).toFloat()
        projectile.status.velocity.add(Vector3f(dirToCenter).mul(strength))
    }

    private companion object {
        /** 生效半径：同时是范围球壳与粒子生成半径 */
        const val EFFECT_RADIUS = 5.0

        /** 范围球壳：均匀（Fibonacci）灰黑球，标识黑洞大小 */
        const val SHELL_PARTICLES = 96
        const val SHELL_SCALE = 0.5f

        /** 灰黑色（半透明）：作为「黑洞大小」的标识，不抢被吸入粒子的视觉 */
        val SHELL_COLOR: Color = Color.ofInt(70, 70, 80, 140)

        /** 每 tick 生成的被吸入粒子数 */
        const val PARTICLES_PER_TICK = 1

        /** 初始切向速度（blocks/tick）：决定起始绕转快慢 */
        const val TANGENTIAL_SPEED = 0.22

        /** 抵达中心判定半径（方块）：进入这个范围就销毁粒子 */
        const val CONSUME_RADIUS = 0.1

        /** 每次积分的阻尼系数。**这是「能否落到中心」的唯一开关**：0.995 太弱（实测 0/500 到中心，只在 r≈2.2~5.0 绕圈），0.9 → 100% 抵达、平均 34 tick（见类注释） */
        const val DAMPING_PER_TICK = 0.9

        /** 粒子尺寸与寿命上限（0.9 阻尼下平均 34 tick 就抵达中心被销毁，这条寿命只是兜底） */
        const val SCALE = 0.55f
        const val LIFETIME_TICKS = 60

        /** 紫色 */
        val COLOR: Color = Color.ofInt(158, 64, 255)

        /** 吸力系数（每 tick，对生物与投射物一致，按 1/(距离+0.5) 衰减） */
        const val PULL_PER_TICK = 0.12
    }
}
