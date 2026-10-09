package work.nekow.primalspells.client.capture

import net.minecraft.client.Minecraft
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.client.BridgeParticle
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.lighting.DynamicLightManager
import work.nekow.particledrawing.core.easing.EasingType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** 开发环境验证PD跟踪曲线、原生渲染插值、静止光照刷新及客户端完成回报。 */
object PdApiScene {
    /** 由CaptureScenes注册，夹具与断言仅在开发录制运行。 */
    fun scene(): Scene {
        val name = "pd_api"
        var floor = 0
        var tracked: ParticleBatch? = null
        var trackedId: UUID? = null
        var light: ParticleHandle? = null
        var panel: ParticleHandle? = null
        var finite: ParticleGroup? = null
        var variable: ParticleGroup? = null
        var variableId: UUID? = null
        var variableCompletedAt = -1L
        var expressionCompletedAt = -1L
        var finiteExpression: ParticleGroup? = null
        var completedAt = -1L
        var beganAt = 0L
        var probe: BridgeParticle? = null
        var interpolation: BridgeParticle? = null
        val samples = ConcurrentHashMap<String, Double>()
        fun sample(label: String) = Minecraft.getInstance().execute {
            val engine = ClientParticleEngine.instance() ?: return@execute
            DynamicLightManager.renderDynamicLights(engine)
            probe?.let {
                val capture = LightSample()
                it.extract(capture, Minecraft.getInstance().gameRenderer.mainCamera(), 0.5f)
                samples[label] = capture.light.toDouble()
            }
        }
        return Scene(name, "PD跟踪曲线 / 静止受光 / 帧间尺寸 / 完成销毁", 24, 120,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 0.5, floor + 2.0, -7.0, 0f, 0f)
                ctx.command("time set midnight")
            }, camera = { _, _ -> Pose(0.5, floor + 2.0, -7.0, 0f, 0f) },
            tick = { ctx, t ->
                val pm = ParticleManager.of(ctx.level)
                if (t == 8) {
                    val h = pm.create().position(-1.5, floor + 2.0, 1.0).color(Color.ofInt(90, 220, 255))
                        .scale(5f).glowing(true).lightLevel(0).lifetime(70).sizeCurve(CurveKey.at(0, 0), CurveKey.at(8, 1),
                            CurveKey.at(42, 1), CurveKey.at(60, 0)).spawn()
                    trackedId = h?.id
                    tracked = ParticleBatch(pm).also { it.add(h) }
                    val center = Vec3(2.0, floor + 2.0, 1.0)
                    panel = pm.create().position(center).color(Color.ofInt(100, 210, 250)).scale(5f).glowing(false).lifetime(-1).spawn()
                    Minecraft.getInstance().execute {
                        val world = Minecraft.getInstance().level ?: return@execute
                        // 同一静止桥接实例多次调用原生getLightCoords；不注入渲染器，也不替换PD缓存。
                        probe = BridgeParticle(UUID.randomUUID(), world, center.x, center.y, center.z, Color.ofInt(100,210,250), 5f, false)
                        interpolation = BridgeParticle(UUID.randomUUID(), world, center.x, center.y, center.z, Color.ofInt(255,255,255), 1f, true)
                    }
                }
                if (t in 9..68) tracked?.trackAll(listOf(Vec3(-1.5 + (t-8)*0.012, floor + 2.0, 1.0)))
                if (t in listOf(10, 24, 65)) Minecraft.getInstance().execute {
                    trackedId?.let { ClientParticleEngine.instance()?.snapshot(it)?.let { s -> samples["track$t"] = s.position.x } }
                }
                if (t == 12) {
                    beganAt = ctx.level.gameTime
                    finite = pm.createGroup(Vec3(0.0, floor + 2.0, 1.0)).also { g ->
                        g.add(pm.create().position(0.0, floor + 2.0, 1.0).color(Color.ofInt(90,220,255)).scale(4f).glowing(true).lightLevel(0).lifetime(-1).spawn())
                        g.scaleTo(1.2f, 8, EasingType.EASE_OUT)
                    }
                    variable = pm.createGroup(Vec3(0.0, floor + 3.0, 1.0)).also { g ->
                        val h = pm.create().position(0.0, floor+3.0, 1.0).scale(2f).glowing(true).lightLevel(0).lifetime(-1).spawn()
                        variableId = h?.id; g.add(h)
                        g.setVariable("presence", 1.0).setVariable("px", 0.0).setVariable("py", floor+3.0)
                            .expression("x=px;y=py;z=1;sc=presence;r=0.2;g=0.8;b=1;a=1;")
                    }
                }
                if (t in 13..35) variable?.setVariableInterpolated("px", ((t-12)*0.04).toString(), 1)
                if (t == 18) Minecraft.getInstance().execute {
                    val world=Minecraft.getInstance().level ?: return@execute
                    // 复现applyProgramFrame首次接管的调用顺序；只观察原生桥接输出，不替换渲染。
                    val birth=BridgeParticle(UUID.randomUUID(),world,0.0,0.0,0.0,Color.TRANSPARENT,5f,true)
                    birth.syncColor(1f,1f,1f,1f)
                    birth.syncScale(0f)
                    for(part in listOf(0.25f,0.5f,0.75f)) {
                        val capture=LightSample()
                        birth.extract(capture,Minecraft.getInstance().gameRenderer.mainCamera(),part)
                        samples["birthVisible$part"]=capture.visibleSize.toDouble()
                    }
                    birth.remove()
                    interpolation?.let { p ->
                        p.syncScale(3f)
                        val v = (0..4).map { p.getQuadSize(it/4f) }
                        samples["subframeInterpolation"] = if (v.zipWithNext().all { it.second > it.first } && abs(v[2]-(v[0]+v[4])*0.5f)<1e-6f) 1.0 else 0.0
                    }
                }
                if (t == 36) Minecraft.getInstance().execute {
                    variableId?.let { ClientParticleEngine.instance()?.snapshot(it)?.let { s -> samples["variableX"] = s.position.x } }
                }
                if (t == 20) sample("dark")
                if (t == 24) light = pm.create().position(2.0, floor + 2.1, 1.15).scale(0.3f).glowing(true).lightLevel(15).lifetime(-1).spawn()
                if (t == 34) sample("lit")
                if (t == 40) light?.track(Vec3(40.0, floor+2.0, 1.0))
                if (t == 50) sample("moved")
                if (t == 55) light?.track(Vec3(2.0, floor+2.1, 1.15))
                if (t == 65) sample("relit")
                if (t == 70) light?.remove()
                if (t == 80) sample("removed")
                if (t == 52) finite?.scaleTo(0f, 8, EasingType.EASE_IN_OUT)
                    ?.onAnimationComplete { completedAt = ctx.level.gameTime - beganAt }?.retire()
                if (t == 50) variable?.setVariableInterpolated("presence", "0", 20)?.onAnimationComplete {
                    variableCompletedAt = ctx.level.gameTime - beganAt
                }?.retire()
                // 重定向既取消旧结束点，也保留当前值；不能在原来的t70完成。
                if (t == 55) variable?.setVariableInterpolated("presence", "0", 24)?.retire()
                if (t == 60) finiteExpression = pm.createGroup(Vec3(1.0, floor+3.0, 1.0)).also { g ->
                    g.add(pm.create().position(1.0, floor+3.0, 1.0).scale(2f).glowing(true).lifetime(-1).spawn())
                    g.expression("x=1;y=${floor+3.0};z=1;sc=max(0,1-t/400);r=0.2;g=0.8;b=1;a=1;", 8)
                        .onAnimationComplete { expressionCompletedAt = ctx.level.gameTime - beganAt }.retire()
                }
                if (t == 90) panel?.remove()
            }, checksAt = { ctx, t -> if(t == 119) {
                val s = samples
                listOf(
                    Check(name,t,"tracked-position-continues-during-life-curve", s.keys.containsAll(listOf("track10","track24","track65")) && s["track24"]!!>s["track10"]!! && s["track65"]!!>s["track24"]!!, "samples=$s"),
                    Check(name,t,"first-program-frame-does-not-flash",listOf(0.25f,0.5f,0.75f).all { s["birthVisible$it"]==0.0 },"samples=$s"),
                    Check(name,t,"native-size-interpolates-subframes",s["subframeInterpolation"]==1.0,"samples=$s"),
                    Check(name,t,"repeated-variable-target-converges", (s["variableX"] ?: 0.0)>0.75,"samples=$s"),
                    Check(name,t,"stationary-particle-dark-bright-dark", s.keys.containsAll(listOf("dark","lit","moved","relit","removed")) && s["lit"]!!>s["dark"]!! && s["relit"]!!>s["dark"]!! && s["moved"]==s["dark"] && s["removed"]==s["dark"],"samples=$s"),
                    Check(name,t,"client-completion-before-fallback",completedAt in 47..55,"callbackAfter=$completedAt expected~48 fallback>=68"),
                    Check(name,t,"completed-group-removed",finite?.size()==0,"members=${finite?.size()}"),
                    Check(name,t,"expression-variable-redirect-completes-before-fallback",variableCompletedAt in 67..77 && variable?.size()==0,"callbackAfter=$variableCompletedAt expected~67 firstFallback=78 redirectedFallback=87"),
                    Check(name,t,"finite-expression-completes-before-fallback",expressionCompletedAt in 56..63 && finiteExpression?.size()==0,"callbackAfter=$expressionCompletedAt expected~56 fallback>=76"),
                    Check(name,t,"pd-particles-zero",pmCount(ctx)==0,"remaining=${pmCount(ctx)}")
                )
            } else emptyList() }, finish = { probe?.remove(); interpolation?.remove() })
    }
    /** 仅接收原版extract的光照输出，不提交顶点或注册替代渲染器。 */
    private class LightSample : QuadParticleRenderState() {
        var light = -1
        var visibleSize = 0f
        override fun add(layer: SingleQuadParticle.Layer, x: Float, y: Float, z: Float,
                         xRot: Float, yRot: Float, zRot: Float, wRot: Float, scale: Float,
                         u0: Float, u1: Float, v0: Float, v1: Float, color: Int, lightCoords: Int) {
            light = lightCoords
            visibleSize = if((color ushr 24)>0) scale else 0f
        }
    }
    private fun pmCount(ctx: CaptureDriver.SceneCtx) = ParticleManager.of(ctx.level).getEngine().particleCount()
}
