package work.nekow.primalspells.util

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 球体采样工具 —— 原版粒子撒点（`Explosion`）与 PD 粒子取点（黑洞/重力场/护盾）共用的采样逻辑。
 *
 * 采样用 `phi = acos(2u - 1)` 保证球面**面积均匀**；若直接对 phi 均匀取值，
 * 粒子会在两极堆积成两团。球内取点再乘 `cbrt(u)` 保证**体积均匀**。
 *
 * 两类用法的取舍：需要「看起来均匀」的**固定球壳标识**用 [fibonacciSphere]（确定性、点距近似相等），
 * 需要随机感的一次性撒点用 [randomUnitVector] / [randomPointInSphere]（配 [sphereVolume]）。
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

    /**
     * 球面**均匀**取点（Fibonacci 球 / 黄金角螺旋）：返回 [count] 个单位方向，相邻间距近似相等。
     *
     * 与随机采样（[randomUnitVector]）的区别：随机采样会有结块与空洞；
     * 做「范围球壳」这类需要看起来均匀的视觉标识时用这个。结果确定（同一 count 得到同一组方向），
     * 便于复现问题，也便于按 index 稳定配对。
     */
    fun fibonacciSphere(count: Int): List<Vec3> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(Vec3(0.0, 1.0, 0.0))
        val points = ArrayList<Vec3>(count)
        val goldenAngle = Math.PI * (3.0 - sqrt(5.0))
        for (i in 0 until count) {
            // y 在 [-1, 1] 上按面积均匀分布；theta 按黄金角递增 → 间距近似相等
            val y = 1.0 - 2.0 * (i + 0.5) / count
            val ringRadius = sqrt(max(0.0, 1.0 - y * y))
            val theta = goldenAngle * i
            points += Vec3(cos(theta) * ringRadius, y, sin(theta) * ringRadius)
        }
        return points
    }

    /** 在**球体内部**均匀撒点（爆炸这类需要填充体积的效果用）。 */
    fun sphereVolume(
        level: ServerLevel,
        center: Vec3,
        radius: Double,
        count: Int,
        particle: ParticleOptions,
        speed: Double = 0.0,
    ) {
        val random = level.random
        for (i in 0 until count) {
            val offset = randomPointInSphere(random, radius)
            level.sendParticles(particle, center.x + offset.x, center.y + offset.y, center.z + offset.z, 1, 0.0, 0.0, 0.0, speed)
        }
    }
}
