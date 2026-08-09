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

    /** 法杖悬停时添加法术槽位提示框组件，按下 Shift 时额外显示法杖属性。 */
    @SubscribeEvent
    fun onGatherTooltip(event: RenderTooltipEvent.GatherComponents) {
        val (wandId, spells) = getWandData(event.itemStack) ?: return
        if (Minecraft.getInstance().hasShiftDown()) {
            addStatLines(event, event.itemStack)
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

    /** 向提示框添加法杖属性行（魔力、延迟、充能、施放数），仅在 Shift 按下时调用。 */
    private fun addStatLines(event: RenderTooltipEvent.GatherComponents, stack: ItemStack) {
        val stats = stack.get(ModItems.WAND_STATS.get()) ?: return
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.mana", "%.1f".format(stats.mana), "%.1f".format(stats.maxMana))
                .withStyle(ChatFormatting.BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.delay", stats.delay)
                .withStyle(ChatFormatting.GOLD)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.recharge", stats.recharge)
                .withStyle(ChatFormatting.DARK_BLUE)
        ))
        event.tooltipElements.add(Either.left(
            Component.translatable("tooltip.primalspells.cast", stats.cast)
                .withStyle(ChatFormatting.WHITE)
        ))
    }
}
