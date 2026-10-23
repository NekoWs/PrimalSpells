package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.magic.projectile.Projectile
import kotlin.math.ceil

/** 沿真实碰撞路径等距选点供PD造型；不平滑或改写游戏轨迹。 */
object FlightTrace {
    data class Sample(val position: Vec3, val direction: Vec3)

    /** 保留起止接点，按总路程采样；无运动记录时兼容静态释放和既有视觉入口。 */
    fun sample(projectile: Projectile, from: Vec3, to: Vec3, spacing: Double): List<Sample> {
        val points = arrayListOf(from)
        (projectile.flightPath + to).forEach { if (it.distanceToSqr(points.last()) > 1e-12) points += it }
        if (points.size < 2) return emptyList()
        val lengths = points.zipWithNext { a, b -> a.distanceTo(b) }
        val length = lengths.sum()
        // 高速聚光一刻可跨64格，仍保留八分之一格光脊，避免旧上限把激光拉成断续点线。
        val count = ceil(length / spacing).toInt().coerceIn(1, 4096)
        var segment = 0
        var passed = 0.0
        return (0..count).map { index ->
            val at = length * index / count
            while (segment < lengths.lastIndex && at > passed + lengths[segment]) passed += lengths[segment++]
            val a = points[segment]; val b = points[segment + 1]
            Sample(a.lerp(b, ((at - passed) / lengths[segment]).coerceIn(0.0, 1.0)), b.subtract(a).normalize())
        }
    }
}
