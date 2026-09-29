package work.nekow.primalspells.client.input

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import work.nekow.primalspells.client.tooltip.SpellSlotTracker
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.SelectSpellSlotPayload
import work.nekow.primalspells.registry.ModItems

/**
 * 客户端输入：用鼠标滚轮切换「法杖 / 法术小包」当前选中的法术槽位。
 *
 * 触发条件：鼠标悬停在容器界面中的法杖或小包物品上。行为：
 * 1. 本地立即更新选中槽组件与 [SpellSlotTracker]，让 tooltip 马上反映新槽位；
 * 2. 发 [SelectSpellSlotPayload] 给服务端做**权威写入**（物品组件只有服务端权威，
 *    服务端的「物品对物品」装卸逻辑读的正是这个组件）；
 * 3. 取消原版滚动事件，避免容器界面同时滚动。
 *
 * 归属：**纯客户端**，由 `client/PrimalSpellsClient` 注册，不要挪回公共侧事件处理器。
 */
object SpellSlotScrollHandler {

    @SubscribeEvent
    fun onMouseScrolled(event: ScreenEvent.MouseScrolled.Pre) {
        val screen = event.screen
        if (screen !is AbstractContainerScreen<*>) return
        val stack = screen.hoveredSlot?.item ?: return
        if (stack.isEmpty) return

        val direction = if (event.scrollDeltaY > 0) -1 else 1

        // 法杖：槽位数量取自同步到客户端的法术列表
        val wandData = WandItem.getWandData(stack)
        if (wandData != null) {
            val (wandId, spells) = wandData
            cycle(stack, wandId, spells.size, SpellSlotTracker.getSelectedSlot(wandId), direction, pouch = false)
            event.isCanceled = true
            return
        }

        // 法术小包：容量固定
        if (stack.item is SpellPouchItem) {
            val pouchId = SpellPouchItem.getPouchId(stack) ?: return
            cycle(stack, pouchId, SpellPouchItem.SLOT_COUNT, SpellPouchItem.getSelectedSlot(stack), direction, pouch = true)
            event.isCanceled = true
        }
    }

    /** 循环切换选中槽：更新本地缓存与组件，并把结果同步到服务端。 */
    private fun cycle(stack: ItemStack, targetId: String, size: Int, current: Int, delta: Int, pouch: Boolean) {
        if (size <= 0) return
        val newSlot = (current + delta).mod(size)
        SpellSlotTracker.setSelectedSlot(targetId, newSlot)
        stack.set(if (pouch) ModItems.POUCH_SELECTED_SLOT.get() else ModItems.WAND_SELECTED_SLOT.get(), newSlot)
        SelectSpellSlotPayload(targetId, newSlot, pouch).send()
    }
}
