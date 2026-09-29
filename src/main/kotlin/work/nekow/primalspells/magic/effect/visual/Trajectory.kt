package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import kotlin.random.Random
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 拖尾特效：用 ParticleDrawing 程序化粒子在投射物后方喷出拖尾（替换原版 sendParticles 拖尾）。
 *
 * 法术向前飞行时，在投射物后方发射少量带随机偏移、向后移动的拖尾粒子；
 * 投射物静止（速度 ≈ 0）时退化为在当前位置缓慢飘散的氛围粒子。
 *
 * 阶段：[EffectPhase.VISUAL] —— 在位置积分之后执行，读到本 tick 的最终位置与速度。
 * 视觉上的「在身后」由发射点的 [TRAIL_BACK] 反向偏移实现，而不是靠延后一 tick 采样。
 *
 * @param color 拖尾粒子颜色
 */
class Trajectory(
    val color: Color,
) : BaseEffect() {
    override val phase = EffectPhase.VISUAL

    override fun onTick() {
        val manager = wand.status.particleManager ?: return
        val pos = status.pos
        val velocity = status.velocity
        val speed = velocity.length()

        if (speed < MIN_SPEED) {
            // 静止投射物：原地飘散氛围粒子
            val vx = (Random.nextFloat() - 0.5f) * 2f * AMBIENT_SPEED
            val vy = (Random.nextFloat() - 0.5f) * 2f * AMBIENT_SPEED
            val vz = (Random.nextFloat() - 0.5f) * 2f * AMBIENT_SPEED
            spawn(
                manager,
                Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()),
                Vec3(vx.toDouble(), vy.toDouble(), vz.toDouble()),
                AMBIENT_LIFETIME,
            )
            return
        }

        val dir = Vector3f(velocity).div(speed)
        repeat(COUNT) {
            val spawnPos = Vec3(
                (pos.x - dir.x * TRAIL_BACK + randomOffset()).toDouble(),
                (pos.y - dir.y * TRAIL_BACK + randomOffset()).toDouble(),
                (pos.z - dir.z * TRAIL_BACK + randomOffset()).toDouble(),
            )
            val backVelocity = Vec3(
                (-dir.x * BACK_SPEED + randomJitter()).toDouble(),
                (-dir.y * BACK_SPEED + randomJitter()).toDouble(),
                (-dir.z * BACK_SPEED + randomJitter()).toDouble(),
            )
            spawn(manager, spawnPos, backVelocity, TRAIL_LIFETIME)
        }
    }

    private fun spawn(manager: ParticleManager, pos: Vec3, velocity: Vec3, lifetime: Int) {
        manager.create()
            .position(pos)
            .color(color)
            .scale(SCALE)
            .lifetime(lifetime)
            .spawn()
            ?.setVelocity(velocity)
    }

    private fun randomOffset(): Float = (Random.nextFloat() - 0.5f) * 2f * OFFSET

    private fun randomJitter(): Float = (Random.nextFloat() - 0.5f) * 2f * JITTER

    companion object {
        /** 每 tick 发射的拖尾粒子数 */
        private const val COUNT = 2

        /** 发射点位于投射物后方的距离（方块） */
        private const val TRAIL_BACK = 0.35f

        /** 发射位置随机偏移幅度（方块） */
        private const val OFFSET = 0.12f

        /** 向后漂移速度（方块/tick） */
        private const val BACK_SPEED = 0.08f

        /** 向后速度随机抖动幅度 */
        private const val JITTER = 0.03f

        /** 拖尾粒子缩放 */
        private const val SCALE = 0.4f

        /** 拖尾粒子存活 tick */
        private const val TRAIL_LIFETIME = 10

        /** 静止氛围粒子存活 tick */
        private const val AMBIENT_LIFETIME = 8

        /** 静止氛围粒子漂移速度（方块/tick） */
        private const val AMBIENT_SPEED = 0.02f

        /** 视为静止的速度阈值 */
        private const val MIN_SPEED = 0.01f
    }
}