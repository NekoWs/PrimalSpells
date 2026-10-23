package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.lighting.DynamicLightManager

/** 单颗原生PD光源缓慢越过section边界，再以同位置/光强重建源作等价对照。 */
object LightBoundaryScene {
    fun scene(): Scene {
        val name = "light_boundary"
        var floor = 0
        var source: ParticleHandle? = null
        var position = Vec3.ZERO
        val samples = ConcurrentHashMap<Int, Double>()
        fun spawn(ctx: CaptureDriver.SceneCtx): ParticleHandle? = ParticleManager.of(ctx.level).create()
            .position(position).color(Color.WHITE).scale(0.1f).glowing(true).lightLevel(14).lifetime(-1).spawn()
        return Scene(name, "单光源慢移 / 区块边界 / 等价光场对照", 40, 180,
            prepare = { ctx ->
                floor = ctx.floorBlockY(80, 64)
                for (x in 48..112) for (z in 32..96) {
                    ctx.level.setBlock(BlockPos(x, floor, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2)
                    for (y in floor + 1..floor + 20) ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                }
                ctx.setup(true, 80.0, floor + 12.0, 44.0, 0f, 40f)
                ctx.command("time set midnight")
            }, camera = { _, _ -> Pose(80.0, floor + 12.0, 44.0, 0f, 40f) },
            tick = { ctx, t ->
                if (t == 8) {
                    position = Vec3(79.5, floor + 2.0, 63.5)
                    source = spawn(ctx)
                }
                if (t in 32..111) {
                    position = Vec3(79.5 + (t - 32) * 0.1, floor + 2.0, 63.5)
                    source?.track(position)
                }
                // 只用于诊断：两幅画面的物理光场应相同，不能把重建源当生产修复。
                if (t == 130) { source?.remove(); source = spawn(ctx) }
                if (t == 158) { source?.remove(); source = null }
            }, clientTick = { _, t ->
                ClientParticleEngine.instance()?.let {
                    DynamicLightManager.renderDynamicLights(it)
                    samples[t] = DynamicLightManager.getDynamicLightLevel(87.4, floor + 1.0, 63.5)
                }
            }, checksAt = { ctx, t ->
                if (t != 179) emptyList() else listOf(
                    Check(name, t, "equivalent-sources-produce-same-queried-light", samples[120] != null && samples[150] != null &&
                        abs(samples[120]!! - samples[150]!!) < 0.001 && samples[150]!! > 10.0,
                        "before=${samples[120]} after=${samples[150]}"),
                    Check(name, t, "native-source-cleaned", ParticleManager.of(ctx.level).getEngine().particleCount() == 0 && samples[178] == 0.0,
                        "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()} light=${samples[178]}")
                )
            }, finish = { source?.remove() })
    }
}
