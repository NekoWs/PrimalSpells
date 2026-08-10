package work.nekow.primalspells.magic.effect

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.utils.HitResult

class Trajectory(
    private val particle: ParticleOptions,
    private val dist: Vector3d = Vector3d()
): BaseEffect() {
    var lastPos: Vector3f? = null

    override fun onActive() {
        lastPos = Vector3f(projectile.position)
    }

    override fun onTick() {
        val level = caster.level()
        if (level is ServerLevel) {
            lastPos?.let { drawLine(level, it, status.pos) }

            level.sendParticles(
                particle,
                status.pos.x.toDouble(),
                status.pos.y.toDouble(),
                status.pos.z.toDouble(),
                1, dist.x, dist.y, dist.z,
                0.0
            )

            lastPos = Vector3f(status.pos)
        }
    }

    override fun onHit(result: HitResult) {
        val level = caster.level()
        if (level is ServerLevel) {
            lastPos?.let { drawLine(level, it, result.pos) }
        }
    }

    private fun drawLine(level: ServerLevel, from: Vector3f, to: Vector3f) {
        val diff = Vector3f(to).sub(from)
        val length = diff.length()
        val stepSize = 0.2f
        if (length <= stepSize) return
        val steps = (length / stepSize).toInt()
        val stepVec = Vector3f(diff).div(length).mul(stepSize)
        for (i in 1..steps) {
            val p = Vector3f(from).fma(i.toFloat(), stepVec)
            level.sendParticles(particle, p.x.toDouble(), p.y.toDouble(), p.z.toDouble(),
                1, dist.x, dist.y, dist.z, 0.0)
        }
    }
}