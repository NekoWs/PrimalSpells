package work.nekow.primalspells.util

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 球体粒子采样工具 —— 爆炸、黑洞、重力场三处曾各写一份同样的采样代码，现在共用这里。
 *
 * 采样用 `phi = acos(2u - 1)` 保证球面**面积均匀**；若直接对 phi 均匀取值，
 * 粒子会在两极堆积成两团。
 */
object ParticleShapes {

    /** 在**球壳**上均匀撒点（粒子都落在半径 [radius] 的球面上）。 */
    fun sphereSurface(
        level: ServerLevel,
        center: Vec3,
        radius: Double,
        count: Int,
        particle: ParticleOptions,
        speed: Double = 0.0,
    ) = emit(level, center, radius, count, particle, speed, volume = false)

    /** 在**球体内部**均匀撒点（爆炸这类需要填充体积的效果用）。 */
    fun sphereVolume(
        level: ServerLevel,
        center: Vec3,
        radius: Double,
        count: Int,
        particle: ParticleOptions,
        speed: Double = 0.0,
    ) = emit(level, center, radius, count, particle, speed, volume = true)

    private fun emit(
        level: ServerLevel,
        center: Vec3,
        radius: Double,
        count: Int,
        particle: ParticleOptions,
        speed: Double,
        volume: Boolean,
    ) {
        for (i in 0 until count) {
            val phi = acos(2.0 * level.random.nextDouble() - 1.0)
            val theta = level.random.nextDouble() * Math.PI * 2.0
            val r = if (volume) level.random.nextDouble() * radius else radius
            level.sendParticles(
                particle,
                center.x + sin(phi) * cos(theta) * r,
                center.y + sin(phi) * sin(theta) * r,
                center.z + cos(phi) * r,
                1,
                0.0, 0.0, 0.0,
                speed,
            )
        }
    }
}
