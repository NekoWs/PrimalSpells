package work.nekow.primalspells.magic.effect.geometry

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** 双精度线段/AABB进入点；曲线由Move分段，分段内仍解析求交，不能只检查端点。 */
object SweptCollision {
    const val SEPARATION = 1e-7
    private const val TIE = 1e-9

    /** axes按X=1、Y=2、Z=4记录同时进入的平面，供墙角独立反射。 */
    data class Contact(val fraction: Double, val point: Vec3, val normal: Vec3, val axes: Int)

    /** 包含边界；从内部开始时返回最近外表面，沿表面离开不重复命中。 */
    fun entry(from: Vec3, to: Vec3, box: AABB): Contact? {
        val origin = doubleArrayOf(from.x, from.y, from.z)
        val delta = doubleArrayOf(to.x - from.x, to.y - from.y, to.z - from.z)
        val lo = doubleArrayOf(box.minX, box.minY, box.minZ)
        val hi = doubleArrayOf(box.maxX, box.maxY, box.maxZ)
        var enter = Double.NEGATIVE_INFINITY
        var exit = Double.POSITIVE_INFINITY
        var axes = 0
        val normals = DoubleArray(3)
        for (axis in 0..2) {
            val d = delta[axis]
            if (abs(d) < 1e-15) {
                // 边界包含：光线恰好处于两个实心方块的拼缝时仍必须被墙挡住。
                if (origin[axis] < lo[axis] || origin[axis] > hi[axis]) return null
                continue
            }
            val a = (lo[axis] - origin[axis]) / d
            val b = (hi[axis] - origin[axis]) / d
            val near = minOf(a, b)
            exit = minOf(exit, maxOf(a, b))
            if (near > enter + TIE) {
                enter = near; axes = 0; normals.fill(0.0)
            }
            if (abs(near - enter) <= TIE) {
                axes = axes or (1 shl axis)
                normals[axis] = if (d > 0) -1.0 else 1.0
            }
            if (enter > exit + TIE) return null
        }
        if (exit < 0.0 || enter > 1.0 || exit <= 0.0 && enter < 0.0) return null
        if (enter < -TIE) {
            // 出生/外力传送到实心内部时，恢复到最近表面；不能返回零法线困在里面。
            if ((0..2).any { origin[it] <= lo[it] || origin[it] >= hi[it] }) return null
            val face = (0..5).minBy { face ->
                val axis = face / 2
                if (face % 2 == 0) origin[axis] - lo[axis] else hi[axis] - origin[axis]
            }
            val axis = face / 2
            origin[axis] = if (face % 2 == 0) lo[axis] else hi[axis]
            normals.fill(0.0); normals[axis] = if (face % 2 == 0) -1.0 else 1.0
            return Contact(0.0, Vec3(origin[0], origin[1], origin[2]),
                Vec3(normals[0], normals[1], normals[2]), 1 shl axis)
        }
        val fraction = enter.coerceIn(0.0, 1.0)
        return Contact(fraction, from.lerp(to, fraction),
            Vec3(normals[0], normals[1], normals[2]).normalize(), axes)
    }
}
