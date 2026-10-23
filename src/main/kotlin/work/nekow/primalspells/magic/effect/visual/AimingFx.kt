package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType

/** 硬边细光丝：PD组表达式接管全部成员、空间端点与有限入退场，不额外照亮世界。 */
class AimingFx(val level: ServerLevel, start: Vec3, end: Vec3) {
    private val manager = ParticleManager.of(level)
    private val group = manager.createGroup(start)
    private var previousStart = start
    private var previousEnd = end
    val particleCount: Int get() = group.size()
    internal var sampleId: java.util.UUID? = null
        private set

    init {
        // 64格内片元间距不超过1/64格；薄方片广告牌使任意观察角度都可读。
        val specs = List(4097) {
            ParticleSpawnSpec().position(start).color(Color.TRANSPARENT).glowing(true).lightLevel(0)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.015625f, 0.015625f)
                    .glowing(true).lightLevel(0).additive(false)).lifetime(-1)
        }
        specs.chunked(256).forEach { batch ->
            val handles = manager.spawnAll(batch)
            if (sampleId == null) sampleId = handles.firstOrNull()?.id
            handles.forEach(group::add)
        }
        group.setVariable("sx", start.x).setVariable("sy", start.y).setVariable("sz", start.z)
            .setVariable("ex", end.x).setVariable("ey", end.y).setVariable("ez", end.z)
            .setVariable("presence", 1.0).expression("""
                u=i/max(1,n-1);x=sx+(ex-sx)*u;y=sy+(ey-sy)*u;z=sz+(ez-sz)*u;
                appear=min(1,t/100);sc=appear*presence;r=0.32;g=0.88;b=0.86;a=1;
            """.trimIndent())
    }

    fun update(start: Vec3, end: Vec3) {
        fun point(prefix: String, value: Vec3) {
            group.setVariableInterpolated(prefix + "x", value.x.toString(), 1, EasingType.LINEAR)
                .setVariableInterpolated(prefix + "y", value.y.toString(), 1, EasingType.LINEAR)
                .setVariableInterpolated(prefix + "z", value.z.toString(), 1, EasingType.LINEAR)
        }
        if (start != previousStart) point("s", start)
        if (end != previousEnd) point("e", end)
        previousStart = start; previousEnd = end
    }

    fun retire() { group.setVariableInterpolated("presence", "0", 3, EasingType.EASE_OUT).retire() }
}
