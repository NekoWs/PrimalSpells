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
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val parallelX = abs(dx) < 1e-15
        val parallelY = abs(dy) < 1e-15
        val parallelZ = abs(dz) < 1e-15
        // 用标量slab测试；未命中是最常见路径，不为它分配数组/临时向量。
        if (parallelX && (from.x < box.minX || from.x > box.maxX) ||
            parallelY && (from.y < box.minY || from.y > box.maxY) ||
            parallelZ && (from.z < box.minZ || from.z > box.maxZ)) return null
        val tx0 = if (parallelX) Double.NEGATIVE_INFINITY else (if (dx > 0) box.minX - from.x else box.maxX - from.x) / dx
        val ty0 = if (parallelY) Double.NEGATIVE_INFINITY else (if (dy > 0) box.minY - from.y else box.maxY - from.y) / dy
        val tz0 = if (parallelZ) Double.NEGATIVE_INFINITY else (if (dz > 0) box.minZ - from.z else box.maxZ - from.z) / dz
        val tx1 = if (parallelX) Double.POSITIVE_INFINITY else (if (dx > 0) box.maxX - from.x else box.minX - from.x) / dx
        val ty1 = if (parallelY) Double.POSITIVE_INFINITY else (if (dy > 0) box.maxY - from.y else box.minY - from.y) / dy
        val tz1 = if (parallelZ) Double.POSITIVE_INFINITY else (if (dz > 0) box.maxZ - from.z else box.minZ - from.z) / dz
        val enter = maxOf(tx0, ty0, tz0)
        val exit = minOf(tx1, ty1, tz1)
        if (enter > exit + TIE || exit < 0.0 || enter > 1.0 || exit <= 0.0 && enter < 0.0) return null
        if (enter < -TIE) {
            if (from.x <= box.minX || from.x >= box.maxX || from.y <= box.minY || from.y >= box.maxY ||
                from.z <= box.minZ || from.z >= box.maxZ) return null
            val xm = from.x - box.minX; val xp = box.maxX - from.x
            val ym = from.y - box.minY; val yp = box.maxY - from.y
            val zm = from.z - box.minZ; val zp = box.maxZ - from.z
            val nearest = minOf(minOf(xm, xp, ym), minOf(yp, zm, zp))
            return when (nearest) {
                xm -> Contact(0.0, Vec3(box.minX, from.y, from.z), Vec3(-1.0, 0.0, 0.0), 1)
                xp -> Contact(0.0, Vec3(box.maxX, from.y, from.z), Vec3(1.0, 0.0, 0.0), 1)
                ym -> Contact(0.0, Vec3(from.x, box.minY, from.z), Vec3(0.0, -1.0, 0.0), 2)
                yp -> Contact(0.0, Vec3(from.x, box.maxY, from.z), Vec3(0.0, 1.0, 0.0), 2)
                zm -> Contact(0.0, Vec3(from.x, from.y, box.minZ), Vec3(0.0, 0.0, -1.0), 4)
                else -> Contact(0.0, Vec3(from.x, from.y, box.maxZ), Vec3(0.0, 0.0, 1.0), 4)
            }
        }
        val x = abs(tx0 - enter) <= TIE
        val y = abs(ty0 - enter) <= TIE
        val z = abs(tz0 - enter) <= TIE
        val normal = Vec3(if (x) if (dx > 0) -1.0 else 1.0 else 0.0,
            if (y) if (dy > 0) -1.0 else 1.0 else 0.0,
            if (z) if (dz > 0) -1.0 else 1.0 else 0.0).normalize()
        val fraction = enter.coerceIn(0.0, 1.0)
        return Contact(fraction, from.lerp(to, fraction), normal, (if (x) 1 else 0) or (if (y) 2 else 0) or (if (z) 4 else 0))
    }
}
