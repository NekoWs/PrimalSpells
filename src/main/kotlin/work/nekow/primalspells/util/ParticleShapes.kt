package work.nekow.primalspells.util

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin

/**
 * 球体采样工具 —— 爆炸/黑洞/重力场等共用同一套采样与撒点逻辑。
 *
 * 采样用 `phi = acos(2u - 1)` 保证球面**面积均匀**；若直接对 phi 均匀取值，
 * 粒子会在两极堆积成两团。球内取点再乘 `cbrt(u)` 保证**体积均匀**。
 */
object ParticleShapes {

    /** 球面均匀随机**单位方向**。 */
    fun randomUnitVector(random: RandomSource): Vec3 {
        val phi = acos(2.0 * random.nextDouble() - 1.0)
        val theta = random.nextDouble() * Math.PI * 2.0
        return Vec3(sin(phi) * cos(theta), sin(phi) * sin(theta), cos(phi))
    }

    /** 球体内均匀随机**偏移**（相对球心，长度 ≤ [radius]，体积均匀）。 */
    fun randomPointInSphere(random: RandomSource, radius: Double): Vec3 {
        return randomUnitVector(random).scale(radius * cbrt(random.nextDouble()))
    }

    /**
     * 与 [normal] 垂直的随机**单位切向**（用于「绕球心做切向运动」的初始速度）。
     *
     * 做法：取随机向量、减去其在法线上的投影后归一化；若退化（与法线共线）则换一个坐标轴重试。
     */
    fun randomTangent(normal: Vec3, random: RandomSource): Vec3 {
        var candidate = randomUnitVector(random)
        var tangent = candidate.subtract(normal.scale(candidate.dot(normal)))
        if (tangent.lengthSqr() < 1e-6) {
            candidate = if (kotlin.math.abs(normal.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
            tangent = candidate.subtract(normal.scale(candidate.dot(normal)))
        }
        return tangent.normalize()
    }

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
        val random = level.random
        for (i in 0 until count) {
            val offset = if (volume) randomPointInSphere(random, radius) else randomUnitVector(random).scale(radius)
            level.sendParticles(particle, center.x + offset.x, center.y + offset.y, center.z + offset.z, 1, 0.0, 0.0, 0.0, speed)
        }
    }
}
