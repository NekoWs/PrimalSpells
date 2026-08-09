package work.nekow.primalspells

import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import work.nekow.primalspells.client.WandHudRenderer
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.network.SyncWandStatsPayload
import work.nekow.primalspells.wand.WandManager

@Mod(PrimalSpells.MODID)
class PrimalSpells(bus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(WandEventHandler)
        NeoForge.EVENT_BUS.register(MagicManager)
        NeoForge.EVENT_BUS.register(WandManager)
        ModItems.COMPONENTS.register(bus)
        ModItems.ITEMS.register(bus)
        ModItems.TABS.register(bus)

        bus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar("1.0.0")
            registrar.playToClient(
                SyncWandStatsPayload.ID,
                SyncWandStatsPayload.STREAM_CODEC
            ) { payload, _ ->
                WandHudRenderer.stats = payload
            }
        }
    }

    companion object {
        const val MODID = "primalspells"
        val LOGGER: Logger = LogUtils.getLogger()

        fun Entity.debug(string: String, vararg args: Any) {
            if (this !is Player) return
            this.sendSystemMessage(Component.literal(string.format(*args)))
        }
        fun Entity.overlay(string: String, vararg args: Any) {
            if (this !is Player) return
            this.sendOverlayMessage(Component.literal(string.format(*args)))
        }
    }
}
