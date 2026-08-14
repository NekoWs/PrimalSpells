package work.nekow.primalspells.magic.effect

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleStyle
import work.nekow.primalspells.utils.HitResult

class Trajectory(
    private val particle: ParticleOptions,
    private val dist: Vector3d = Vector3d()
): BaseEffect() {
    var lastPos: Vector3f? = null
    var handle: ParticleHandle? = null
    lateinit var manager: ParticleManager

    override fun onActive() {
        lastPos = Vector3f(projectile.position)
        manager = wand.status.particleManager ?: return
        val pos = status.pos
        handle = manager.create()
            .style(ParticleStyle.DUST)
            .position(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            .color(Color.RED)
            .lifetime(projectile.maxAge)
            .spawn()
    }

    override fun onTick() {
        val pos = status.pos
        manager.create()
            .style(ParticleStyle.DUST)
            .position(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            .color(Color.BLUE)
            .lifetime(5)
            .spawn()
        lastPos = Vector3f(status.pos)
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