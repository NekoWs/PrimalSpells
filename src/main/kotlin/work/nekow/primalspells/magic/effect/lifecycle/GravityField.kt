package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.effect.visual.ParticleSwarm
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes

/**
 * 重力场 —— 半径 5 的重力区域：
 * - **生物**：每 tick 刷新迟缓 IV（离开范围立刻解除）；
 * - **魔法投射物**：每 tick 附加一份**向下的力**（[GRAVITY_PER_TICK]），让穿过区域的法术下坠；
 * - **粒子**（ParticleDrawing）：每 tick 在 2.5 格球体内随机位置生成 **1 个淡灰色粒子**，
 *   初速度竖直向下（中等速度 [FALL_SPEED]），此外不再施加任何力 —— 表现为「区域内持续飘落」。
 *
 * 粒子在世界空间里下落、不跟随投射物（场自身移动时粒子留在原地），
 * 位置积分/同步/渲染由 ParticleDrawing 负责（见 [ParticleSwarm] 与 `docs/conventions.md` §9）。
 */
class GravityField : AreaFieldEffect(
    radius = 5.0,
    slownessAmplifier = 3,
    pullPerTick = 0.0,
) {
    private val swarm = ParticleSwarm()

    /** 每 tick 生成 1 个淡灰色下落粒子。 */
    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val pos = center.add(ParticleShapes.randomPointInSphere(caster.level().random, SPAWN_RADIUS))
        swarm.spawn(manager, pos, COLOR, SCALE, LIFETIME_TICKS, FALL_VELOCITY)
    }

    /** 粒子只需清理已过期的句柄（速度恒定，不再施力）。 */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) = swarm.prune()

    override fun clearVisuals() = swarm.clear()

    /** 投射物：每 tick 附加向下的力（与 `gravity` 修正同一量级）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        projectile.status.velocity.y -= GRAVITY_PER_TICK
    }

    private companion object {
        /** 粒子生成范围（球体半径） */
        const val SPAWN_RADIUS = 2.5

        /** 下落速度（blocks/tick）：中等速度，肉眼可见但不至于瞬移 */
        const val FALL_SPEED = 0.18

        /** 施加给投射物的向下加速度（每 tick，与 `ConstantAcceleration` 默认值一致） */
        const val GRAVITY_PER_TICK = 0.03f

        const val SCALE = 0.7f
        const val LIFETIME_TICKS = 60

        /** 淡灰色 */
        val COLOR: Color = Color.ofInt(205, 205, 205)

        /** 初速度：竖直向下（引擎每 tick 自己 `pos += velocity`） */
        val FALL_VELOCITY: Vec3 = Vec3(0.0, -FALL_SPEED, 0.0)
    }
}
