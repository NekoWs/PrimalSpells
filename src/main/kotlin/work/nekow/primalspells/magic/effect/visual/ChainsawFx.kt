package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 一次短促锯齿切割：大颗粒白刃、金色齿尖，沿刃方向散开后熄灭。 */
class ChainsawFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var lastCutAge = -2
    var cutsEmitted = 0
        private set
    override fun onTick() {
        if (status.age - lastCutAge < 2) return
        lastCutAge = status.age
        cutsEmitted++
        val world = caster.level() as? ServerLevel ?: return
        val direction = projectile.launchDirection
        val side = direction.cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize()
        }
        val up = side.cross(direction).normalize()
        val center = Vec3(status.pos)
        val velocities = ArrayList<Vec3>()
        val specs = (0 until 24).map { i ->
            val across = (i % 8 - 3.5) * 0.125 * projectile.areaScale
            val tooth = if (i % 2 == 0) 0.125 else -0.125
            val axis = if (i < 8) side else if (i < 16) up else side.add(up).normalize()
            velocities += axis.scale(across * 0.18)
            ParticleSpawnSpec().position(center.add(axis.scale(across)).add(direction.scale(tooth)))
                .color(if (i % 2 == 0) Color.WHITE else projectile.bodyColor)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.125f, 0.125f).glowing(true))
                .lifetime(5).sizeCurve(*PixelVfx.lifeKeys(5))
        }
        val pm = ParticleManager.of(world)
        ParticleBatch(pm).also { it.addAll(pm.spawnAll(specs)); it.setVelocityAll(velocities) }
    }
}
