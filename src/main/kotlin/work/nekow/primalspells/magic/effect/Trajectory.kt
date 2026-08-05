package work.nekow.primalspells.magic.effect

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d

class Trajectory(
    private val particle: ParticleOptions,
    private val dist: Vector3d = Vector3d()
): BaseEffect() {
    override fun onTick() {
        val level = caster.level()
        if (level is ServerLevel) {
            level.sendParticles(
                particle,
                status.pos.x.toDouble(),
                status.pos.y.toDouble(),
                status.pos.z.toDouble(),
                1, dist.x, dist.y, dist.z,
                0.0
            )
        }
    }
}