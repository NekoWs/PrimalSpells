package work.nekow.primalspells

import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.Logger
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.wand.WandManager

@Mod(PrimalSpells.MODID)
class PrimalSpells(bus: IEventBus, container: ModContainer) {
    init {
        ModItems.COMPONENTS.register(bus)
        ModItems.ITEMS.register(bus)
        ModItems.TABS.register(bus)
        NeoForge.EVENT_BUS.register(WandEventHandler)
        NeoForge.EVENT_BUS.register(MagicManager)
        NeoForge.EVENT_BUS.register(WandManager)
    }

    companion object {
        const val MODID = "primalspells"
        val LOGGER: Logger = LogUtils.getLogger()

        fun Entity.debug(string: String, vararg args: Any) {
            if (this !is Player) return
            this.sendSystemMessage(Component.literal(string.format(*args)))
        }
    }
}