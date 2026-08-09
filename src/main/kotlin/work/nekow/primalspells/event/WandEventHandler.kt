package work.nekow.primalspells.event

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import work.nekow.primalspells.item.WandItem

object WandEventHandler {
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.itemStack.item is WandItem) event.isCanceled = true
    }
}
