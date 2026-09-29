package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle
import java.util.UUID

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
 * 成员簿记全部交给 PD 的 [ParticleBatch]（增量登记、过期自动出列、`removeIf` 按条件回收、
 * `ensureSize` 补齐球壳）；本模组只算**数值**：位置、速度、颜色、寿命与回收判据。
 *
 * ## 吸力：越近越强 + 阻尼（阻尼系数是「能否落到中心」的唯一开关）
 *
 * 加速度随距离增强（`0.12/(距离+0.5)`），并且每 tick 顺带乘一点阻尼（[DAMPING_PER_TICK]）让切向速度衰减。
 * 阻尼太弱时粒子会形成**偏心率很大的椭圆轨道**：半径降到某个值后来回振荡，永远进不了中心。
 * 实测（500 个随机样本数值复算）：`0.995` → 抵达中心 **0/500**（在 r≈2.2~5.0 之间绕到寿命结束）；
 * 现用的 **[DAMPING_PER_TICK] = 0.9** → **100%** 抵达中心，平均 **34 tick**（峰值存量随之从 60 降到 ~34）。
 * 参数取值过程见 `docs/issues/resolved.md` 第 11 轮。
 *
 * ## 位置由本模组积分，用一次 `trackAll` 批量下发
 *
 * 吸力**随距离变化**，所以本模组每 tick 自己算好位置后用 [ParticleBatch.trackAll] 写出
 * （1 包/tick，与粒子数无关）。好处：轨迹确定、「进入中心 0.1 格才消失」这类判定可以精确表达，
 * 且服务端暂停时不会因客户端外推而飘出范围。渲染/同步/寿命/可见性依旧由 ParticleDrawing 负责。
 *
 * 备选（PD 1.0.10-ALPHA 起可用）：`ParticleBatch.applyForceAll(加速列表, ticks = 1)` 能把
 * 「每颗粒子各自的力」一包下发、位置交回 PD 两端积分（同样 1 包/tick）。当前没换，是因为这里
 * 需要精确的中心判定与确定的轨迹，不是 PD 表达不了 —— 见 `docs/issues/resolved.md` 附录 B.2。
 *
 * 代价：PD 的**位置指令会清掉粒子上的速度与力**（`ServerParticleEngine.takeOverPosition`），
 * 所以速度只能记在本模组（[velocities]）。
 */
class BlackHole : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
) {
    /** 范围球壳：永存粒子，首 tick 铺满，之后只在 PD 侧有损耗时补齐（[ParticleBatch.ensureSize]）。 */
    private var shell: ParticleBatch? = null

    /** 被吸入的粒子：成员由 PD 簿记，位置每 tick 由本模组积分后用一次 `trackAll` 下发。 */
    private var sucked: ParticleBatch? = null

    /**
     * 被吸入粒子的速度（本模组持有，键 = 粒子 id）。
     *
     * 键集每 tick 按「[ParticleBatch.forEach] 见到的存活成员」重建，所以 PD 侧寿命到点/被上限挤掉的
     * 成员不会在这里留下残项；不依赖任何下标配对（历史上一类 bug 的来源，见 `docs/known-issues.md` §6）。
     */
    private var velocities = HashMap<UUID, Vec3>()

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
            // 生成时不设速度：位置每条 tick 由本模组算好下发（速度指令会被位置指令清掉，见类注释）
            val handle = manager.spawnParticle(pos, COLOR, SCALE, LIFETIME_TICKS) ?: return@repeat
            suckedBatch.add(handle)
            velocities[handle.id] = ParticleShapes.randomTangent(radial, random).scale(TANGENTIAL_SPEED)
        }
    }

    /** 每 tick 施力 + 积分 + **一次 `trackAll` 批量写回位置**，并把抵达中心的粒子销毁。 */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        val batch = sucked ?: return
        if (batch.isEmpty()) {
            velocities.clear()
            return
        }

        // 1) 按 PD 的权威位置推进本模组的速度，并算出这一 tick 的新位置
        val positions = ArrayList<Vec3>(batch.size())
        val advanced = HashMap<UUID, Vec3>(velocities.size)
        batch.forEach { _, handle, pos, _ ->
            val vel = velocities[handle.id] ?: Vec3.ZERO
            val toCenter = center.subtract(pos)
            val dist = toCenter.length()
            // 随距离增强的向心加速度 + 轻微阻尼：越接近中心吸得越狠，切向速度逐渐衰减
            val next = vel.scale(DAMPING_PER_TICK).add(toCenter.normalize().scale(PULL_PER_TICK / (dist + 0.5)))
            advanced[handle.id] = next
            positions.add(pos.add(next))
        }
        velocities = advanced

        // 2) 一次包下发全部位置（与粒子数无关）
        batch.trackAll(positions)

        // 3) 抵达中心的粒子销毁。放在 trackAll 之后：中途出列会打乱「成员顺序 ↔ positions」的对应关系
        batch.removeIf { handle, pos, _ ->
            val consumed = pos.distanceTo(center) <= CONSUME_RADIUS
            if (consumed) velocities.remove(handle.id)
            consumed
        }
    }

    override fun clearVisuals() {
        shell?.clear()
        sucked?.clear()
        velocities.clear()
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

        /** 每次积分的阻尼系数。**这是「能否落到中心」的唯一开关**：0.995 太弱（实测 0/500 到中心，只在 r≈2.2~5.0 绕圈），现用 0.9 → 100% 抵达、平均 34 tick（见类注释） */
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
