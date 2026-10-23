package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.toVec3
import kotlin.math.*

/** 厚齿钢盘、深色盘身和亮轴；沿真实轨迹插值，碰撞少量火星，结束向外碎散。 */
class SawbladeFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var manager: ParticleManager? = null
    private var batch: ParticleBatch? = null
    private var axis = Vec3(1.0, 0.0, 0.0)
    private var phaseAngle = 0.0
    private var lastSpark = -10

    private fun points(): List<Vec3> {
        val center = status.pos.toVec3()
        val forward = axis
        val up = Vec3(0.0, 1.0, 0.0)
        val normal = forward.cross(up).normalize()
        val out = arrayListOf<Vec3>()
        fun local(radius: Double, angle: Double, depth: Double) = center.add(forward.scale(cos(angle) * radius * projectile.areaScale))
            .add(up.scale(sin(angle) * radius * projectile.areaScale)).add(normal.scale(depth * projectile.areaScale))
        // 双面锯齿形成真正厚度，盘身为紧凑实色金属，不再只有一圈白点。
        for (face in listOf(-1, 1)) repeat(32) { i ->
            out += local(if (i % 2 == 0) 0.41 else 0.33, phaseAngle + i * PI / 16, face * 0.035)
        }
        repeat(24) { i -> out += local(0.23, phaseAngle + i * PI / 12, 0.0) }
        repeat(8) { i -> out += local(0.09, phaseAngle + i * PI / 4, 0.0) }
        repeat(8) { i -> out += local(0.16, phaseAngle + i * PI / 4, 0.0) }
        return out
    }
    override fun onActive() {
        val pm = ParticleManager.of(caster.level()); manager = pm
        val direction = Vec3(status.velocity).multiply(1.0, 0.0, 1.0)
        if (direction.lengthSqr() > 1e-8) axis = direction.normalize()
        batch = ParticleBatch(pm).also { b -> b.addAll(pm.spawnAll(points().mapIndexed { i, p ->
            ParticleSpawnSpec().position(p).color(when {
                i < 64 -> if (i % 2 == 0) Color.ofInt(221, 235, 238) else Color.ofInt(133, 157, 170)
                i < 88 -> Color.ofInt(88, 105, 123)
                i < 96 -> Color.ofInt(185, 229, 241)
                else -> Color.ofInt(190, 157, 87)
            }).visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(if (i < 64) 0.109375f else 0.125f,
                if (i < 64) 0.109375f else 0.125f).glowing(true).additive(false))
                .lifetime(-1).sizeCurve(CurveKey.at(0, 0), CurveKey.at(1.5, 1, EasingType.EASE_OUT))
        })) }
    }
    override fun onTick() {
        val horizontal = Vec3(status.velocity).multiply(1.0, 0.0, 1.0)
        if (horizontal.lengthSqr() > 1e-8) {
            var desired = horizontal.normalize()
            // 盘面有方向对称性：反弹时取最近轴，避免突然翻转180度。
            if (desired.dot(axis) < 0) desired = desired.scale(-1.0)
            axis = axis.lerp(desired, 0.35).normalize()
        }
        phaseAngle += 0.47 + min(0.22, status.velocity.length() * 0.08)
        batch?.trackAll(points())
    }
    override fun onHitBlock(pos: Vector3d) = sparks(pos.toVec3(), Color.ofInt(255, 211, 114))
    override fun onHitEntity(target: net.minecraft.world.entity.Entity) = sparks(status.pos.toVec3(), Color.ofInt(204, 232, 239))
    private fun sparks(point: Vec3, color: Color) {
        if (status.age - lastSpark < 3) return
        lastSpark = status.age
        val pm = manager ?: return
        repeat(3) { i ->
            val angle = phaseAngle + i * 2.4
            pm.create().position(point).color(color).visual(ParticleVisual().style(ParticleStyle.SQUARE)
                .anisoWorld(0.0625f, 0.0625f).glowing(true)).lifetime(6)
                .sizeCurve(*PixelVfx.lifeKeys(6)).spawn()?.setVelocity(Vec3(cos(angle) * 0.10, 0.05 + i * 0.025, sin(angle) * 0.10))
        }
    }
    override fun onDie() {
        val pm = manager ?: return
        val particles = batch ?: return
        val center = status.pos.toVec3()
        particles.forEach { _, handle, position, _ ->
            val radial = position.subtract(center).normalize()
            handle.setVelocity(radial.scale(0.10).add(Vec3(status.velocity).scale(0.12)))
                .applyForce(Vec3(0.0, -0.025, 0.0), -1)
        }
        // 线性缩小单片，保持径向碎散；缓入缓出的中段会让可见面积骤降。
        particles.handles().forEach { it.resize(0f, 8, EasingType.LINEAR) }
        work.nekow.primalspells.util.Deferred.after(pm.level, 10) { particles.clear() }
        batch = null
    }
}
