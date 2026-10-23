package work.nekow.primalspells.magic.effect.visual

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.spellParticleSpec
import work.nekow.primalspells.util.spellVisual
import work.nekow.primalspells.util.toVec3

/** 单道随机弧形锯切：实色齿尖沿刃外移，短促熄灭，不叠固定十字。 */
class ChainsawFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var lastCutAge = -2
    var cutsEmitted = 0
        private set
    /** 本次切痕方向，供实机验证连续切割不会锁在同一平面。 */
    var lastCutAxis: Vec3 = Vec3.ZERO
        private set
    override fun onTick() {
        if (status.age - lastCutAge < 2) return
        lastCutAge = status.age
        cutsEmitted++
        val world = caster.level() as? ServerLevel ?: return
        val random = world.random
        // 使用修正后的真实运动来向，散射/追踪后的刃面不能仍锁在原施法平面。
        val direction = if (status.velocity.lengthSquared() > 1e-8f) Vec3(status.velocity).normalize() else projectile.launchDirection
        val side = direction.cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize()
        }
        val up = side.cross(direction).normalize()
        val roll = random.nextDouble() * PI * 2
        val axis = side.scale(cos(roll)).add(up.scale(sin(roll)))
        lastCutAxis = axis
        val bend = direction.cross(axis).normalize()
        val center = status.pos.toVec3()
        val radius = (0.28 + random.nextDouble() * 0.18) * projectile.areaScale
        val curve = (if (random.nextBoolean()) 1 else -1) * (0.1 + random.nextDouble() * 0.12) * projectile.areaScale
        val count = 7 + random.nextInt(3)
        val velocities = ArrayList<Vec3>()
        val specs = (0 until count).map { i ->
            val across = (i.toDouble() / (count - 1) * 2 - 1)
            val tooth = if (i % 2 == 0) 0.03125 else -0.03125
            val offset = axis.scale(across * radius).add(bend.scale((1 - across * across) * curve))
                .add(direction.scale(tooth))
            velocities += axis.scale(across * 0.035).add(bend.scale(curve * 0.12))
            val life = 3 + random.nextInt(2)
            spellParticleSpec().position(center.add(offset))
                .color(if (i % 3 == 0) projectile.bodyColor else Color.WHITE)
                .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(0.125f, 0.125f))
                .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
        }
        val pm = ParticleManager.of(world)
        ParticleBatch(pm).also { it.addAll(pm.spawnAll(specs)); it.setVelocityAll(velocities) }
    }
}
