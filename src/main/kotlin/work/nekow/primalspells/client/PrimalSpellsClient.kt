package work.nekow.primalspells.client

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.PrimalSpells

@Mod(value = PrimalSpells.MODID, dist = [Dist.CLIENT])
class PrimalSpellsClient(bus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(ClientTooltipHandler::class.java)

        bus.addListener<RegisterClientTooltipComponentFactoriesEvent> { event ->
            event.register(WandSpellTooltip::class.java, ::ClientWandSpellTooltip)
        }
    }
}
