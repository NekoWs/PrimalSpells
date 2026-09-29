package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.effect.visual.ParticleSwarm
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes

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
 * ## 吸力：越近越强 + 轻微阻尼
 *
 * 加速度随距离增强（`0.12/(距离+0.5)`），并且每 tick 顺带乘一点阻尼（[DAMPING_PER_TICK]）让切向速度衰减。
 * 二者缺一，粒子会在某个半径上稳定环绕 —— 现象就是「离中心一段距离有明显空域、到不了中心」。
 *
 * ## 位置由本模组积分，用一次 `trackAll` 批量下发
 *
 * 吸力随距离变化，交给引擎的恒定加速度表达不了；因此位置由本模组算好后，
 * 用 [work.nekow.particledrawing.api.ParticleManager.trackAll] **一次包**写出全部粒子
 * （与粒子数无关，见 [ParticleSwarm] 的三种模式表）。好处：轨迹确定、能保证「进入中心 0.1 格才消失」，
 * 且暂停时不会因客户端外推而飘出范围。
 * 渲染/同步/寿命/可见性依旧由 ParticleDrawing 负责。
 */
class BlackHole : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
) {
    private val swarm = ParticleSwarm()

    /** 每 tick 在球壳上生成粒子，初速度沿随机切向；范围球壳只在第一 tick 铺一次。 */
    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val random = caster.level().random

        if (swarm.size == 0) {
            // 均匀（Fibonacci 球）灰黑球壳：标识黑洞大小，永存到本效果结束
            for (unit in ParticleShapes.fibonacciSphere(SHELL_PARTICLES)) {
                swarm.spawn(manager, center.add(unit.scale(EFFECT_RADIUS)), SHELL_COLOR, SHELL_SCALE, -1)
            }
        }

        repeat(PARTICLES_PER_TICK) {
            val radial = ParticleShapes.randomUnitVector(random)
            val pos = center.add(radial.scale(EFFECT_RADIUS))
            val tangent = ParticleShapes.randomTangent(radial, random).scale(TANGENTIAL_SPEED)
            swarm.spawnSimulated(manager, pos, tangent, COLOR, SCALE, LIFETIME_TICKS)
        }
    }

    /** 每 tick 施力 + 积分 + **一次 `trackAll` 批量写回位置**；抵达中心（返回 null）即销毁。 */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        swarm.tickSimulated(manager, step = { pos, vel ->
            val toCenter = center.subtract(pos)
            val dist = toCenter.length()
            if (dist <= CONSUME_RADIUS) {
                null // 已抵达中心：销毁
            } else {
                // 随距离增强的向心加速度 + 轻微阻尼：越接近中心吸得越狠，切向速度逐渐衰减
                vel.scale(DAMPING_PER_TICK).add(toCenter.normalize().scale(PULL_PER_TICK / (dist + 0.5)))
            }
        })
    }

    override fun clearVisuals() = swarm.clear()

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

        /** 每次积分的阻尼系数：让切向速度逐渐衰减，轨道半径单调收缩（否则会在某半径稳定环绕） */
        const val DAMPING_PER_TICK = 0.995

        /** 粒子尺寸与安全寿命（正常会在被吸入时提前销毁） */
        const val SCALE = 0.55f
        const val LIFETIME_TICKS = 60

        /** 紫色 */
        val COLOR: Color = Color.ofInt(158, 64, 255)

        /** 吸力系数（每 tick，对生物与投射物一致，按 1/(距离+0.5) 衰减） */
        const val PULL_PER_TICK = 0.12
    }
}
