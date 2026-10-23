package work.nekow.primalspells.client.capture

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.CurveKey
import work.nekow.particledrawing.api.ParticleGroup
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.lighting.DynamicLightManager
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** 只观察PD原生光场与生命周期，不提供光照平滑或渲染替代实现。 */
object ParticleLightingScene {
    private data class Sample(val tick: Int, val alpha: Float?, val light: Double)

    /** 渐变透明度与组缩放分别退场，检查场景照明是否同步收束。 */
    fun scene(): Scene {
        val name = "particle_lighting"
        var floor = 0
        var sourceId: UUID? = null
        var shrinking: ParticleGroup? = null
        val samples = CopyOnWriteArrayList<Sample>()
        return Scene(name, "粒子照明 / 出生与退场连续性", 24, 110,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 0.5, floor + 2.0, -7.0, 0f, 0f)
                ctx.command("time set midnight")
            },
            camera = { _, _ -> Pose(0.5, floor + 2.0, -7.0, 0f, 0f) },
            tick = { ctx, t ->
                val pm = ParticleManager.of(ctx.level)
                val center = Vec3(0.5, floor + 2.0, 1.0)
                if (t == 8) sourceId = pm.create().position(center)
                    .color(Color.ofInt(100, 210, 255)).scale(3f).glowing(true).lightLevel(15)
                    .lifetime(28).alphaCurve(CurveKey.at(0, 0), CurveKey.at(8, 1),
                        CurveKey.at(16, 1), CurveKey.at(27, 0)).spawn()?.id
                if (t == 48) shrinking = pm.createGroup(center).also { group ->
                    group.add(pm.create().position(center).color(Color.ofInt(100, 210, 255))
                        .scale(3f).glowing(true).lightLevel(15).lifetime(-1).spawn())
                    group.scaleTo(1f, 4)
                }
                if (t == 64) shrinking?.scaleTo(0f, 8)?.retire()
            },
            clientTick = { _, t ->
                val engine = ClientParticleEngine.instance()
                if (engine != null) {
                    DynamicLightManager.renderDynamicLights(engine)
                    val alpha = engine.getGlowingParticles().firstOrNull { it.id() == sourceId }?.effectiveAlpha()
                    samples += Sample(t, alpha, DynamicLightManager.getDynamicLightLevel(0.5, floor + 2.0, 1.0))
                }
            },
            checksAt = { ctx, t ->
                if (t != 109) emptyList() else {
                    val faded = samples.filter { it.tick in 8..39 && it.alpha != null && it.alpha in 0.02f..0.35f }
                    val exit = samples.filter { it.tick in 64..100 }
                    val maxStep = exit.zipWithNext().maxOfOrNull { abs(it.second.light - it.first.light) } ?: 0.0
                    val detail = "alphaSamples=$faded; scaleExit=$exit"
                    listOf(
                        Check(name, t, "native-alpha-transition-observed", faded.size >= 2, detail),
                        Check(name, t, "world-light-follows-alpha", faded.isNotEmpty() && faded.all { it.light <= 15 * it.alpha!! + 0.5 }, detail),
                        Check(name, t, "world-light-exit-has-no-full-brightness-cut", exit.any { it.light >= 10 } && exit.any { it.light == 0.0 } && maxStep <= 5.0, "maxStep=$maxStep; $detail"),
                        Check(name, t, "pd-particles-zero", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                    )
                }
            },
            finish = { shrinking?.remove() })
    }
}
