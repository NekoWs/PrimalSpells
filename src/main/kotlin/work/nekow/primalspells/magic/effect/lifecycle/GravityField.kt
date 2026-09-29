package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spawnParticle
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 重力场 —— 半径 5 的重力区域：
 * - **生物**：每 tick 刷新迟缓 IV（离开范围立刻解除）；
 * - **魔法投射物**：每 tick 附加一份向下的力（[GRAVITY_PER_TICK]），穿过区域的法术会下坠；
 * - **粒子**（ParticleDrawing）分两层：
 *   1. **范围球壳**：在半径 [EFFECT_RADIUS] 的球面上均匀铺 [SHELL_PARTICLES] 个半透明淡蓝灰粒子并永存，
 *      用来标识重力场的**作用范围**。场本身静止，所以球壳不需要每 tick 跟踪；场结束（[clearVisuals]）统一销毁。
 *   2. **下落粒子**：每 tick 在**上半球表面**生成 [FALLING_PER_TICK] 个淡灰色粒子，竖直向下匀速下落，
 *      抵达**下半球表面**时销毁 —— 速度交给 PD 逐 tick 积分（零包），回收判定只读 PD 的权威位置
 *      （[ParticleBatch.removeIf]）。
 *
 * 成员簿记全部交给 PD 的 [ParticleBatch]（球壳用 `ensureSize` 补齐、下落粒子用 `removeIf` 按几何面回收）；
 * 粒子在世界空间里下落、不跟随投射物；位置积分/同步/渲染由 ParticleDrawing 负责
 * （见 `docs/conventions.md` §9 与 `docs/design/effects-api.md` §0）。
 */
class GravityField : AreaFieldEffect(
    radius = EFFECT_RADIUS,
    slownessAmplifier = 3,
    pullPerTick = 0.0,
) {
    /** 范围球壳（永存粒子）：首 tick 铺满，之后只在 PD 侧有损耗时补齐。 */
    private var shell: ParticleBatch? = null

    /** 下落粒子（匀速，按位置条件回收）。 */
    private var falling: ParticleBatch? = null

    /** 球壳偏移（相对中心，半径 [EFFECT_RADIUS]）：Fibonacci 球结果确定，算一次即可。 */
    private val shellOffsets: List<Vec3> by lazy {
        ParticleShapes.fibonacciSphere(SHELL_PARTICLES).map { it.scale(EFFECT_RADIUS) }
    }

    override fun spawnVisuals(manager: ParticleManager, center: Vec3) {
        val random = caster.level().random

        val shellBatch = shell ?: ParticleBatch(manager).also { shell = it }
        // 均匀球壳（Fibonacci 球）：点到点间距近似相等，才像「范围标识」
        shellBatch.ensureSize(SHELL_PARTICLES) { index ->
            manager.spawnParticle(center.add(shellOffsets[index]), SHELL_COLOR, SHELL_SCALE)
        }

        val fallingBatch = falling ?: ParticleBatch(manager).also { falling = it }
        repeat(FALLING_PER_TICK) {
            // 上半球表面：把随机球面方向翻到 y ≥ 0
            val dir = ParticleShapes.randomUnitVector(random)
            val upper = if (dir.y < 0.0) dir.scale(-1.0) else dir
            // 匀速下落：速度交给 PD 逐 tick 积分（零逐 tick 包）；生命靠「碰到下半球表面」的判定收掉
            fallingBatch.add(
                manager.spawnParticle(
                    center.add(upper.scale(EFFECT_RADIUS)),
                    FALL_COLOR,
                    FALL_SCALE,
                    FALL_LIFETIME_TICKS,
                    FALL_VELOCITY,
                )
            )
        }
    }

    /** 只读 PD 的权威位置判定：下落粒子碰到下半球表面即销毁（读位置不产生任何包）。 */
    override fun updateVisuals(manager: ParticleManager, center: Vec3) {
        falling?.removeIf { _, pos, _ -> pos.y <= lowerSurfaceY(center, pos) }
    }

    override fun clearVisuals() {
        shell?.clear()
        falling?.clear()
    }

    /** 投射物：每 tick 附加向下的力（与 `gravity` 修正同一量级）。 */
    override fun applyProjectileForce(projectile: Projectile, dirToCenter: Vector3f, dist: Double) {
        projectile.status.velocity.y -= GRAVITY_PER_TICK
    }

    /**
     * 指定水平位置处「下半球表面」的 y 坐标：`center.y - √(r² − dx² − dz²)`。
     *
     * 水平偏移超出球半径时按球心平面处理（根号内取 0），保证粒子一定会被回收而不是永远下落。
     */
    private fun lowerSurfaceY(center: Vec3, pos: Vec3): Double {
        val dx = pos.x - center.x
        val dz = pos.z - center.z
        val vertical = sqrt(max(0.0, EFFECT_RADIUS * EFFECT_RADIUS - dx * dx - dz * dz))
        return center.y - vertical
    }

    private companion object {
        /** 生效半径：同时也是球壳标识半径 */
        const val EFFECT_RADIUS = 5.0

        /** 范围球壳粒子数（永存） */
        const val SHELL_PARTICLES = 96

        const val SHELL_SCALE = 0.45f

        /** 球壳颜色：半透明淡蓝灰（只作为范围标识，不抢下落粒子的视觉） */
        val SHELL_COLOR: Color = Color.ofInt(150, 175, 205, 110)

        /** 每 tick 生成的下落粒子数 */
        const val FALLING_PER_TICK = 3

        const val FALL_SCALE = 0.6f

        /** 下落速度（blocks/tick）：中等速度，肉眼可见 */
        const val FALL_SPEED = 0.25

        /** 下落粒子安全寿命（tick）：正常会在碰到下半球表面时提前销毁 */
        const val FALL_LIFETIME_TICKS = 120

        /** 下落粒子颜色：淡灰色 */
        val FALL_COLOR: Color = Color.ofInt(205, 205, 205)

        /** 初速度：竖直向下（引擎每 tick 自己 `pos += velocity`） */
        val FALL_VELOCITY: Vec3 = Vec3(0.0, -FALL_SPEED, 0.0)

        /** 施加给投射物的向下加速度（每 tick，与 `ConstantAcceleration` 默认值一致） */
        const val GRAVITY_PER_TICK = 0.03f
    }
}
