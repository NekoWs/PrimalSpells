package work.nekow.primalspells.client

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.PrimalSpells

@Mod(value = PrimalSpells.MODID, dist = [Dist.CLIENT])
class PrimalSpellsClient(container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(ClientTooltipHandler)
    }
}