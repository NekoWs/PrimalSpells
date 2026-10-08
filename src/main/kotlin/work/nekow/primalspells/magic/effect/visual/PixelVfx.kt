package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes
import kotlin.math.*

/** 实色像素的冲击、折线和晶面造型；本侧只给几何与初值，积分、旋转、寿命交给PD。 */
object PixelVfx {
    /** 固定尺寸碎屑，错峰寿命；不把几何整体缩向轴心。 */
    fun burst(manager: ParticleManager, center: Vec3, color: Color, count: Int = 72,
              speed: Double = 0.25, life: Int = 16, gravity: Double = 0.015, spread: Double = 0.12) {
        val specs = ArrayList<ParticleSpawnSpec>()
        val velocities = ArrayList<Vec3>()
        ParticleShapes.fibonacciSphere(count).forEachIndexed { i, d ->
            specs += ParticleSpawnSpec().position(center.add(d.scale(spread)))
                .color(if (i % 7 == 0) Color.ofInt(240, 250, 255) else color)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(
                    if (i % 5 == 0) 0.1875f else 0.0625f, if (i % 5 == 0) 0.0625f else 0.0625f)
                    .glowing(true).spin(i * 0.37))
                .lifetime(life + i % 13)
            velocities += d.scale(speed * (0.55 + i % 9 / 12.0)).add(0.0, 0.06, 0.0)
        }
        val batch = ParticleBatch(manager).also { it.addAll(manager.spawnAll(specs)) }
        batch.setVelocityAll(velocities)
        if (gravity != 0.0) batch.applyForceAll(List(specs.size) { Vec3(0.0, -gravity, 0.0) }, -1)
    }

    /** 有方向的冲击环：快速膨胀、错峰断裂，PD负责径向求值。 */
    fun ring(manager: ParticleManager, center: Vec3, normal: Vec3, radius: Double,
             color: Color, life: Int = 12, growth: Float = 3f) {
        val n = if (normal.lengthSqr() < 1e-8) Vec3(0.0, 1.0, 0.0) else normal.normalize()
        val u = n.cross(if (abs(n.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)).normalize()
        val v = n.cross(u).normalize()
        val group = manager.createGroup(center)
        repeat(128) { i ->
            val a = i * PI * 2 / 128
            val p = center.add(u.scale(cos(a) * radius)).add(v.scale(sin(a) * radius))
            group.add(manager.create().position(p).color(color).style(ParticleStyle.SQUARE)
                .visual(ParticleVisual().anisoWorld(0.0625f, 0.0625f).glowing(true))
                .lifetime(life + i % 5).spawn())
        }
        group.scale(growth, 5, EasingType.EASE_OUT).destroyAfter(life + 5)
    }

    /** 多层闪电主干与分叉，按离散色阶/寿命闪烁；无缩小整条电弧的动作。 */
    fun arc(manager: ParticleManager, from: Vec3, to: Vec3, color: Color = Color.ofInt(100, 205, 255)) {
        val dir = to.subtract(from)
        val u = dir.normalize().cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() < 1e-8) Vec3(1.0, 0.0, 0.0) else it.normalize()
        }
        val v = dir.normalize().cross(u)
        val points = (0..18).map { i ->
            val base = from.lerp(to, i / 18.0)
            if (i == 0 || i == 18) base else base.add(u.scale(sin(i * 7.13) * 0.24)).add(v.scale(cos(i * 3.71) * 0.17))
        }
        val specs = ArrayList<ParticleSpawnSpec>()
        for (i in 0 until points.lastIndex) repeat(8) { j ->
            val p = points[i].lerp(points[i + 1], j / 8.0)
            specs += ParticleSpawnSpec().position(p).color(if (j % 3 == 0) Color.ofInt(224, 250, 255) else color)
                .visual(ParticleVisual().anisoWorld(0.09375f, 0.09375f).glowing(true)).lifetime(5 + i % 6)
            if (i % 4 == 0) specs += ParticleSpawnSpec().position(p.add(u.scale(j * 0.04)))
                .color(color).visual(ParticleVisual().anisoWorld(0.046875f, 0.046875f).glowing(true)).lifetime(3 + j % 5)
        }
        manager.spawnAll(specs)
        burst(manager, to, color, 24, 0.12, 8, 0.0)
    }

    /** 六面长柱加尖顶的实体晶簇，晶面用不同实色，三根晶柱有不同倾角和尺寸。 */
    fun crystal(manager: ParticleManager, center: Vec3, icy: Boolean = false): ParticleGroup {
        val group = manager.createGroup(center)
        val palette = if (icy) listOf(Color.ofInt(70, 139, 205), Color.ofInt(119, 213, 240), Color.ofInt(220, 249, 255))
            else listOf(Color.ofInt(80, 43, 137), Color.ofInt(140, 78, 200), Color.ofInt(197, 156, 249), Color.ofInt(233, 212, 255))
        repeat(3) { shard ->
            val offset = Vec3((shard - 1) * 0.24, -0.3, if (shard == 1) -0.10 else 0.12)
            val height = if (shard == 1) 1.25 else 0.78
            val lean = Vec3((shard - 1) * 0.24, 0.0, if (shard == 1) 0.0 else 0.12)
            val base = center.add(offset)
            val shoulder = base.add(lean).add(0.0, height * 0.68, 0.0)
            val tip = base.add(lean.scale(1.35)).add(0.0, height, 0.0)
            val radius = if (shard == 1) 0.19 else 0.14
            repeat(6) { face ->
                fun radial(i: Int) = Vec3(cos(i * PI / 3) * radius, 0.0, sin(i * PI / 3) * radius)
                val a = base.add(radial(face)); val b = base.add(radial(face + 1))
                val c = shoulder.add(radial(face)); val d = shoulder.add(radial(face + 1))
                val color = palette[face % palette.size]
                facet(manager, group, a, b, c, color)
                facet(manager, group, b, d, c, color)
                facet(manager, group, c, d, tip, palette[(face + 1) % palette.size])
            }
        }
        return group
    }

    private fun facet(manager: ParticleManager, group: ParticleGroup, a: Vec3, b: Vec3, c: Vec3, color: Color) {
        val normal = b.subtract(a).cross(c.subtract(a)).normalize()
        val steps = max(5, (max(a.distanceTo(c), b.distanceTo(c)) * 26).toInt())
        val visual = ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.046875f, 0.046875f)
            .billboard(false).spin(atan2(-normal.y, normal.z), asin(normal.x.coerceIn(-1.0, 1.0)), 0.0).glowing(true)
        for (i in 0..steps) for (j in 0..steps - i) {
            val p = a.add(b.subtract(a).scale(i.toDouble() / steps)).add(c.subtract(a).scale(j.toDouble() / steps))
            group.add(manager.create().position(p).color(color).visual(visual).lifetime(-1).spawn())
        }
    }
}
