package work.nekow.primalspells.client

import com.mojang.datafixers.util.Either
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.WandItem

object ClientTooltipHandler {

    /** 法杖悬停时添加法术槽位提示框组件。 */
    @SubscribeEvent
    fun onGatherTooltip(event: RenderTooltipEvent.GatherComponents) {
        val (wandId, spells) = getWandData(event.itemStack) ?: return
        if (Minecraft.getInstance().hasShiftDown()) {
            event.tooltipElements.add(Either.left(
                Component.translatable("tooltip.primalspells.wand_id", wandId).withStyle(ChatFormatting.GRAY)
            ))
        }
        event.tooltipElements.add(Either.right(WandSpellTooltip(spells, wandId)))
    }

    /** 悬停法杖时滚轮切换选中格，并同步到 WAND_SELECTED_SLOT 组件。 */
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

    /** 从物品栈提取法杖 ID 与法术列表，不符合条件返回 null。 */
    private fun getWandData(stack: ItemStack): Pair<String, List<String>>? {
        if (stack.item !is WandItem) return null
        val spells = stack.get(ModItems.WAND_SPELLS.get()) ?: return null
        if (spells.isEmpty()) return null
        val wandId = stack.get(ModItems.WAND_ID.get())?.wandId ?: return null
        return wandId to spells
    }
}
