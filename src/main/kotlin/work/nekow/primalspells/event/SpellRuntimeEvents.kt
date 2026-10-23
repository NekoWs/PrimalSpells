package work.nekow.primalspells.event

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.particledrawing.config.ParticleDrawingConfig
import work.nekow.primalspells.magic.runtime.ProjectileRuntime

/** 世界生命周期与领域运行时的接线；PD容量策略也只在服务器启动时配置。 */
object SpellRuntimeEvents {
    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) = ProjectileRuntime.tick()

    /** 高配特效档，避免完整晶面被默认可见粒子预算截断。 */
    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        ParticleDrawingConfig.SERVER.maxParticlesPerDimension.set(1_000_000)
        ParticleDrawingConfig.SERVER.maxParticlesPerPlayer.set(100_000)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) = ProjectileRuntime.clear()
}
