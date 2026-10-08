package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes
import kotlin.math.*

/** 实色像素的冲击、折线和晶面造型；本侧只给几何与初值，积分、旋转、寿命交给PD。 */
object PixelVfx {
    /** 实色片元的尺寸起落；不改变位置或透明度，曲线由PD逐帧求值。 */
    fun lifeKeys(life: Int): Array<CurveKey> {
        val end = life.coerceAtLeast(2).toDouble()
        return arrayOf(CurveKey.at(0, 0), CurveKey.at(min(1.5, end * 0.2), 1, EasingType.EASE_OUT),
            CurveKey.at(max(end * 0.35, end - 5), 1), CurveKey.at(end, 0, EasingType.EASE_IN_OUT))
    }

    /** 从当前合成倍率有限收束至零；出生、膨胀或脉冲中断仍由PD接续。 */
    fun retire(group: ParticleGroup?, ticks: Int = 6) {
        group?.scaleTo(0f, ticks, EasingType.EASE_IN_OUT)?.destroyAfter(ticks + 2)
    }

    /** 飞散碎片只收片元尺寸，保留原速度与位置，不把整批拉回场心。 */
    fun retireBatch(manager: ParticleManager, batch: ParticleBatch?, center: Vec3, ticks: Int = 6) {
        if (batch == null || batch.isEmpty()) return
        val handles = batch.handles().toList()
        handles.forEach { it.resize(0f, ticks, EasingType.EASE_IN_OUT) }
        work.nekow.primalspells.util.Deferred.after(manager.level, ticks + 2) { handles.forEach { it.remove() } }
    }

    /** 破裂时径向外移和片元熄灭同时进行；抵消scale的几何内缩，轮廓只向外打开。 */
    fun shatter(group: ParticleGroup?, ticks: Int = 5, radiusScale: Float = 1f) {
        group?.moveAlongOffset(radiusScale * 2f, ticks, EasingType.EASE_IN_OUT)
            ?.scaleTo(0f, ticks, EasingType.EASE_IN_OUT)?.destroyAfter(ticks + 2)
    }

    /** 碎屑保持运动，单片起落并错峰结束；不把几何整体缩向轴心。 */
    fun burst(manager: ParticleManager, center: Vec3, color: Color, count: Int = 72,
              speed: Double = 0.25, life: Int = 16, gravity: Double = 0.015, spread: Double = 0.12) {
        val specs = ArrayList<ParticleSpawnSpec>()
        val velocities = ArrayList<Vec3>()
        val turn = manager.level.random.nextDouble() * PI * 2
        ParticleShapes.fibonacciSphere(count).forEachIndexed { i, direction ->
            val d = Vec3(direction.x * cos(turn) - direction.z * sin(turn), direction.y, direction.x * sin(turn) + direction.z * cos(turn))
            specs += ParticleSpawnSpec().position(center.add(d.scale(spread)))
                .color(if (i % 7 == 0) Color.ofInt(240, 250, 255) else color)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(
                    if (i % 5 == 0) 0.1875f else 0.0625f, if (i % 5 == 0) 0.0625f else 0.0625f)
                    .glowing(true).spin(i * 0.37))
                .lifetime(life + i % 13).sizeCurve(*lifeKeys(life + i % 13))
            velocities += d.scale(speed * (0.55 + manager.level.random.nextDouble() * 0.8)).add(0.0, 0.06, 0.0)
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
                .lifetime(life + i % 5).sizeCurve(*lifeKeys(life + i % 5)).spawn())
        }
        group.scale(growth, 5, EasingType.EASE_OUT).destroyAfter(life + 5)
    }

    /** 连续导向目标的折线电流；白热前锋顺线推进，旧线在原位收尾。 */
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
            val arrival = 0.05 + (i * 8 + j) / 143.0 * 4.5
            // 每片沿固定折线先后展开，寿命曲线不接管位置；白热前锋跟随出现时间推进。
            specs += ParticleSpawnSpec().position(p).color(if (j % 3 == 0) Color.ofInt(224, 250, 255) else color)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.09375f, 0.09375f)
                    .glowing(true).additive(false)).lifetime(12).sizeCurve(
                    CurveKey.at(0, 0), CurveKey.at(arrival, 0),
                    CurveKey.at(arrival + 0.9, 1, EasingType.EASE_OUT), CurveKey.at(arrival + 1.5, 1),
                    CurveKey.at(arrival + 5.4, 0, EasingType.EASE_IN_OUT))
        }
        manager.spawnAll(specs)
        burst(manager, to, color, 24, 0.12, 8, 0.0)
    }

    /** 单枚青金双尖晶体：四条腰棱、上下尖端和琥珀束带，区别于紫水晶簇。 */
    fun crystal(manager: ParticleManager, center: Vec3, icy: Boolean = false): ParticleGroup {
        val group = manager.createGroup(center)
        val palette = if (icy) listOf(Color.ofInt(70, 139, 205), Color.ofInt(119, 213, 240), Color.ofInt(220, 249, 255), Color.ofInt(105, 181, 225))
            else listOf(Color.ofInt(24, 83, 91), Color.ofInt(47, 150, 146), Color.ofInt(114, 216, 185), Color.ofInt(210, 245, 210))
        val top = center.add(0.0, 0.72, 0.0)
        val bottom = center.add(0.0, -0.32, 0.0)
        repeat(4) { face ->
            fun waist(i: Int) = center.add(cos(PI / 4 + i * PI / 2) * 0.32, 0.06, sin(PI / 4 + i * PI / 2) * 0.32)
            val a = waist(face); val b = waist(face + 1)
            facet(manager, group, a, b, top, palette[face])
            facet(manager, group, b, a, bottom, palette[(face + 2) % 4])
            Draw.line(manager, a, b, 9, ColorSource.of(Color.ofInt(224, 174, 65)), 0.5f,
                group = group, visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))
        }
        group.anchor(Anchor.Movable(center, Vec3.ZERO, work.nekow.particledrawing.api.Orient.WORLD)).scaleTo(0f, 0).scaleTo(1f, 3, EasingType.EASE_OUT)
        return group
    }

    /** 沿横截面按实际面积采样；细长三角形不再按长边长度平方过度铺粒。 */
    private fun facet(manager: ParticleManager, group: ParticleGroup, a: Vec3, b: Vec3, c: Vec3, color: Color) {
        val normal = b.subtract(a).cross(c.subtract(a)).normalize()
        val rows = ceil(max(a.distanceTo(c), b.distanceTo(c)) / 0.055).toInt().coerceAtLeast(1)
        val visual = ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.0625f, 0.0625f)
            .billboard(false).spin(atan2(-normal.y, normal.z), asin(normal.x.coerceIn(-1.0, 1.0)), 0.0).glowing(true)
        for (row in 0..rows) {
            val t = row.toDouble() / rows
            val left = a.lerp(c, t); val right = b.lerp(c, t)
            val columns = ceil(left.distanceTo(right) / 0.055).toInt().coerceAtLeast(1)
            for (column in 0..columns) {
                val p = left.lerp(right, column.toDouble() / columns)
                group.add(manager.create().position(p).color(color).visual(visual).lifetime(-1).spawn())
            }
        }
    }
}
