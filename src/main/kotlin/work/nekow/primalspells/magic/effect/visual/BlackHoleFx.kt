package work.nekow.primalspells.magic.effect.visual

import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Anchor
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ColorSource
import work.nekow.particledrawing.api.Draw
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleGroup
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleStyle
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.spellParticle
import work.nekow.primalspells.util.spellVisual

/**
 * 黑洞的服务端表现接线，由 BlackHole 的三个视觉钩子驱动，每次施法独立持有。
 * 暗核与星点边界分别编排；固定几何用 PD 组跟随场心，
 * 吸入星尘用 PD 批次读权威位置并批量设速。这里只决定造型与吸力，不实现粒子引擎。
 */
class BlackHoleFx {
    private var core: ParticleGroup? = null
    private var boundary: ParticleGroup? = null
    private var sucked: ParticleBatch? = null
    private var emissionIndex = 0
    private var lastCenter: Vec3? = null
    private var activeScale = 1.0

    /** 首次铺暗核与稀疏星点边界；之后跟随场心并补九颗星尘。 */
    fun spawn(manager: ParticleManager, center: Vec3, areaScale: Double = 1.0) {
        activeScale = areaScale
        if (core == null) {
            createStructure(manager, center)
            core?.scaleTo(0f, 0)?.scaleTo(areaScale.toFloat(), 3, EasingType.EASE_OUT)
            boundary?.scaleTo(0f, 0)?.scaleTo(areaScale.toFloat(), 4, EasingType.EASE_OUT)
        }
        followCenter(center)
        val batch = sucked ?: ParticleBatch(manager).also { sucked = it }
        repeat(9) {
            // 黄金角取点使连续发射均匀铺开，造型可复录，不依赖世界随机数。
            val index = emissionIndex++
            val angle = index * GOLDEN_ANGLE
            val radial = if (index % 3 == 0) {
                DUST_DIRECTIONS[index % DUST_DIRECTIONS.size]
            } else {
                discOffset(1.0, angle, 0.08 * sin(index.toDouble())).normalize()
            }
            val tangent = DISC_NORMAL.cross(radial).normalize().scale(0.22)
            batch.add(
                manager.spellParticle().position(center.add(radial.scale(RADIUS * areaScale)))
                    .color(if (index % 3 == 0) DUST_BRIGHT else DUST_COLOR)
                    .scale(if (index % 3 == 0) 0.55f else 0.8f)
                    .style(ParticleStyle.SQUARE).additive(false)
                    .lifetime(60).sizeCurve(*PixelVfx.lifeKeys(60)).spawn()?.also { it.setVelocity(tangent) },
            )
        }
    }

    /** 星尘越靠近暗核越快，位置积分与成员回收仍由 PD 负责，每 tick 一次批量速度包。 */
    fun update(center: Vec3) {
        val batch = sucked ?: return
        batch.removeIf { _, pos, _ -> pos.distanceTo(center) <= CORE_RADIUS * activeScale }
        if (batch.isEmpty()) return
        val velocities = ArrayList<Vec3>(batch.size())
        batch.forEach { _, _, pos, vel ->
            val delta = center.subtract(pos)
            velocities += vel.scale(0.9).add(delta.normalize().scale(0.12 / (delta.length() + 0.5)))
        }
        batch.setVelocityAll(velocities)
    }

    /** 保持实色，原位旋转着塌缩并按时销毁，直接给现有组追加指令；提前死亡也无需重铺或延迟任务。 */
    fun clear(manager: ParticleManager) {
        PixelVfx.retireBatch(manager, sucked, lastCenter ?: Vec3.ZERO, 12)
        sucked = null
        PixelVfx.retire(boundary, 10)
        boundary = null
        PixelVfx.retire(core, 10)
        core = null
    }

    /** 结构层只生成一次：固定结构只保留暗核与半径五格的低亮星点。 */
    private fun createStructure(manager: ParticleManager, center: Vec3) {
        val dark = manager.createGroup(center)
        // 用实色小方粒子密铺球面，保留 MC 的颗粒尺度；暗核遮挡后方星点。
        Draw.sphere(manager, center, CORE_RADIUS, 160, ColorSource.of(CORE_COLOR), 1.4f,
            group = dark, visual = spellVisual().style(ParticleStyle.SQUARE))

        core = dark.anchor(Anchor.Movable(center, Vec3.ZERO, work.nekow.particledrawing.api.Orient.WORLD))

        val edge = manager.createGroup(center)
        Draw.sphere(manager, center, RADIUS, 128, ColorSource.of(BOUNDARY_COLOR), 0.55f,
            group = edge, visual = spellVisual().style(ParticleStyle.SQUARE))

        boundary = edge.anchor(Anchor.Movable(center, Vec3.ZERO, work.nekow.particledrawing.api.Orient.WORLD))
    }

    /** 暗核和稀疏边界紧跟最终位置；环形装饰已移除。 */
    private fun followCenter(center: Vec3) {
        if (lastCenter != center) {
            core?.updateAnchor(lastCenter ?: center, center)
            boundary?.updateAnchor(lastCenter ?: center, center)
        }
        lastCenter = center
    }

    /** 星尘的倾斜旋流基底；仅用于散点初始方向，不绘制固定盘。 */
    private fun discOffset(radius: Double, angle: Double, height: Double): Vec3 =
        DISC_U.scale(radius * cos(angle)).add(DISC_V.scale(radius * sin(angle))).add(DISC_NORMAL.scale(height))

    private companion object {
        const val RADIUS = 5.0
        const val CORE_RADIUS = 0.65
        const val GOLDEN_ANGLE = 2.399963229728653
        val DISC_NORMAL: Vec3 = Vec3(0.28, 1.0, 0.22).normalize()
        val DISC_U: Vec3 = Vec3(1.0, -0.28, 0.0).normalize()
        val DISC_V: Vec3 = DISC_NORMAL.cross(DISC_U).normalize()
        val DUST_DIRECTIONS: List<Vec3> = ParticleShapes.fibonacciSphere(64)
        val CORE_COLOR: Color = Color.ofInt(4, 3, 9, 255)
        val BOUNDARY_COLOR: Color = Color.ofInt(110, 74, 174, 255)
        val DUST_COLOR: Color = Color.ofInt(153, 80, 245, 255)
        val DUST_BRIGHT: Color = Color.ofInt(214, 186, 255, 255)
    }
}
