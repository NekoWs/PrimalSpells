package work.nekow.primalspells.event

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import work.nekow.primalspells.client.WandTooltipTracker
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.WandItem

object WandEventHandler {
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.itemStack.item is WandItem) event.isCanceled = true
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.item is WandItem) {
                WandItem.syncStats(stack, player)
            }
        }
    }

    @SubscribeEvent
    fun onMouseScrolled(event: ScreenEvent.MouseScrolled.Pre) {
        val screen = event.screen
        if (screen !is AbstractContainerScreen<*>) return
        val slot = screen.hoveredSlot ?: return
        val (wandId, spells) = getWandData(slot.item) ?: return
        val current = WandTooltipTracker.getSelectedSlot(wandId)
        val direction = if (event.scrollDeltaY > 0) -1 else 1
        val newSlot = (current + direction).mod(spells.size)
        WandTooltipTracker.setSelectedSlot(wandId, newSlot)
        slot.item.set(ModItems.WAND_SELECTED_SLOT.get(), newSlot)
        event.isCanceled = true
    }

    fun getWandData(stack: ItemStack): Pair<String, List<String>>? {
        if (stack.item !is WandItem) return null
        val spells = stack.get(ModItems.WAND_SPELLS.get()) ?: return null
        if (spells.isEmpty()) return null
        val wandId = stack.get(ModItems.WAND_ID.get())?.wandId ?: return null
        return wandId to spells
    }
}
