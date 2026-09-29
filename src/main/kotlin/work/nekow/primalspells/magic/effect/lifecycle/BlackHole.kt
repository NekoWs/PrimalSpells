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
 * - **魔法投射物**：每 tick 沿指向中心的方向加速（吸力同样随距离衰减）—— 这就是「把敌方/自家法术吸进去」的实现；
 * - **粒子**（ParticleDrawing）：每 tick 在 5 格球壳上生成 [PARTICLES_PER_TICK] 个紫色粒子，
 *   初速度取**随机切向**（绕中心旋转），随后每 tick 把「指向中心的一份加速度」写回速度，
 *   于是粒子一边绕转一边被吸入，落到 [CONSUME_DISTANCE] 内即销毁（在中心消失）。
 *
 * 力的计算在本模组，位置积分/跨端同步/渲染全部由 ParticleDrawing 负责
 * （见 [ParticleSwarm] 的职责边界说明与 `docs/conventions.md` §9）。
 */
class BlackHole : AreaFieldEffect(
    radius = 5.0,
    slownessAmplifier = 0,
    pullPerTick = PULL_PER_TICK,
) {
    private val swarm = ParticleSwarm()

    /** 施力节拍计数器（见 [updateVisuals] 的说明） */
    private var forceTick = 0

    /** 每 tick 在球壳上生成一批紫色粒子，初速度沿随机切向。 */
    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val random = caster.level().random
        repeat(PARTICLES_PER_TICK) {
            val radial = ParticleShapes.randomUnitVector(random)
            val pos = center.add(radial.scale(SPAWN_RADIUS))
            val tangent = ParticleShapes.randomTangent(radial, random).scale(TANGENTIAL_SPEED)
            swarm.spawn(manager, pos, COLOR, SCALE, LIFETIME_TICKS, tangent)
        }
    }

    /**
     * 每 [FORCE_INTERVAL_TICKS] tick 给已有粒子加一份指向中心的加速度；到达中心即销毁。
     *
     * 为什么要隔几 tick 才施力：PD 的 [work.nekow.particledrawing.api.ParticleHandle.setVelocity]
     * 会向可见玩家广播一次速度包，而[每 tick 都对上千个粒子广播]代价太大；
     * 引擎会持续按当前速度积分位置，所以隔几 tick 修正一次速度，视觉上仍是被平滑吸进去。
     */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        forceTick++
        if (forceTick % FORCE_INTERVAL_TICKS != 0) {
            swarm.prune()
            return
        }
        swarm.update { pos, velocity ->
            val toCenter = center.subtract(pos)
            val dist = toCenter.length()
            if (dist < CONSUME_DISTANCE) return@update null
            // 隔了 FORCE_INTERVAL_TICKS 才施力，按 tick 数补齐加速度
            velocity.add(toCenter.normalize().scale(ATTRACT_PER_TICK * FORCE_INTERVAL_TICKS))
        }
    }

    override fun clearVisuals() = swarm.clear()

    /** 投射物：每 tick 沿指向中心的方向加速（吸力随距离衰减）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        val strength = (PULL_PER_TICK / (dist + 0.5)).toFloat()
        projectile.status.velocity.add(Vector3f(dirToCenter).mul(strength))
    }

    private companion object {
        /** 每 tick 生成的粒子数 */
        const val PARTICLES_PER_TICK = 32

        /** 粒子生成半径（球壳） */
        const val SPAWN_RADIUS = 5.0

        /** 初始切向速度（blocks/tick）：决定起始绕转快慢 */
        const val TANGENTIAL_SPEED = 0.22

        /** 每 tick 写入速度的向心加速度增量：越大吸得越快 */
        const val ATTRACT_PER_TICK = 0.022

        /**
         * 施力间隔（tick）：PD 每次 `setVelocity` 都要广播，
         * 对上千个粒子每 tick 广播代价太大，因此隔几 tick 修正一次速度。
         */
        const val FORCE_INTERVAL_TICKS = 3

        /** 落到这个距离内视为「已被吸入中心」，销毁粒子 */
        const val CONSUME_DISTANCE = 0.35

        /** 粒子尺寸与安全寿命（正常会在被吸入时提前销毁） */
        const val SCALE = 0.55f
        const val LIFETIME_TICKS = 60

        /** 紫色 */
        val COLOR: Color = Color.ofInt(158, 64, 255)

        /** 吸力系数（每 tick，对生物与投射物一致，按 1/(距离+0.5) 衰减） */
        const val PULL_PER_TICK = 0.12
    }
}
