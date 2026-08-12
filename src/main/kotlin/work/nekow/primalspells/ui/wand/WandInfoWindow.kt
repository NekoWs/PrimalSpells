package work.nekow.primalspells.ui.wand

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.client.WandHudRenderer
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.EditWandPayload
import work.nekow.primalspells.ui.FloatingWindow
import work.nekow.primalspells.ui.common.ContainerWindowBase
import work.nekow.primalspells.ui.common.SpellSlotGrid
import work.nekow.primalspells.ui.element.UiLabel
import work.nekow.primalspells.ui.element.UiSlot

/**
 * 法杖信息悬浮窗：显示当前追踪法杖的基础信息（蓝量、容量、充能等）与法术槽位。
 *
 * 追踪规则：鼠标悬停背包中的法杖时追踪该法杖；移开后保持显示上一次悬停的法杖。
 * 法术槽位按法杖容量动态生成（每行 8 格）。
 * 交互（交换/装卸）通过 [ContainerWindowBase] 复用，同步走 [EditWandPayload]。
 */
class WandInfoWindow private constructor(
    window: FloatingWindow,
    private val wandSlot: UiSlot,
    private val manaLabel: UiLabel,
    private val capacityLabel: UiLabel,
    private val chargeLabel: UiLabel,
    private val castLabel: UiLabel,
    grid: SpellSlotGrid,
) : ContainerWindowBase(window, grid) {

    /** 当前追踪的法杖 id（上一次悬停的），每次刷新从容器重新获取最新引用 */
    private var trackedWandId: String? = null

    /** 本次刷新解析到的法杖物品（供编杖操作使用） */
    private var currentStack: ItemStack? = null

    /** 内容缓存（仅变化时刷新，避免每帧重建导致闪烁） */
    private var lastWandStack: ItemStack? = null
    private var lastManaText: String? = null
    private var lastChargeText: String? = null
    private var lastCastText: String? = null
    private var lastCapacityText: String? = null

    companion object {
        const val WIDTH = 176
        const val SLOTS_PER_ROW = 8
        const val SLOT_TOP = 66
        const val MAX_SLOTS = 32

        /** 槽位区底部留白（动态窗口高度 = 槽位区 + 留白） */
        const val BOTTOM_PADDING = 6

        /** 按槽位数计算窗口内容区高度（槽位 18px + 2px 间距/行） */
        fun contentHeight(slotCount: Int): Int {
            val need = slotCount.coerceIn(0, MAX_SLOTS)
            val rows = (need + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW
            return SLOT_TOP + rows * (SpellSlotGrid.SLOT_SIZE + SpellSlotGrid.SLOT_GAP) - SpellSlotGrid.SLOT_GAP + BOTTOM_PADDING
        }

        fun create(): WandInfoWindow {
            val wandSlot = UiSlot(8, 8)
            val manaLabel = UiLabel(Component.literal(""), 34, 10, color = 0xFF_55_AA_FF.toInt())
            val capacityLabel = UiLabel(Component.literal(""), 34, 22)
            val chargeLabel = UiLabel(Component.literal(""), 34, 34)
            val castLabel = UiLabel(Component.literal(""), 34, 46)
            val grid = SpellSlotGrid(emptyList())
            val window = FloatingWindow(
                Component.literal("法杖信息"),
                listOf(wandSlot, manaLabel, capacityLabel, chargeLabel, castLabel),
                WIDTH, contentHeight(0),
            )
            val info = WandInfoWindow(window, wandSlot, manaLabel, capacityLabel, chargeLabel, castLabel, grid)
            info.rebuildSlots(0)
            return info
        }
    }

    /** 当前追踪的法杖物品（最新引用） */
    fun currentWand(): ItemStack? = currentStack

    /** 按 wandId 在手持或容器槽位中查找法杖的最新 ItemStack 引用 */
    private fun findWandById(player: Player?, wandId: String): ItemStack? {
        if (player == null) return null
        listOf(player.mainHandItem, player.offhandItem).firstOrNull {
            it.item is WandItem && it.get(ModItems.WAND_ID)?.wandId == wandId
        }?.let { return it }
        val menu = player.containerMenu
        for (i in 0 until menu.slots.size) {
            val st = menu.getSlot(i).item
            if (!st.isEmpty && st.item is WandItem && st.get(ModItems.WAND_ID)?.wandId == wandId) return st
        }
        return null
    }

    /** 按容量重建槽位网格与窗口高度（动态布局：窗口高度随槽位数增长） */
    private fun rebuildSlots(count: Int) {
        val need = count.coerceIn(0, MAX_SLOTS)
        if (grid.slots.size == need) return
        grid = SpellSlotGrid.create(need, 8, SLOT_TOP, SLOTS_PER_ROW)
        window.content = listOf(wandSlot, manaLabel, capacityLabel, chargeLabel, castLabel) + grid.slots
        window.windowHeight = contentHeight(need)
        // 槽位对象已重建，重置拖拽控制器（基类交换回调引用 var grid，自动指向新网格）
        window.slotDrag = work.nekow.primalspells.ui.slot.SlotDragController()
    }

    /** 每帧刷新数据与槽位内容 */
    override fun refresh(player: Player?, screen: Screen?) {
        // 悬停法杖优先更新追踪目标；否则按上次追踪的 wandId 从容器重新获取最新引用
        var hovered: ItemStack? = null
        if (screen is AbstractContainerScreen<*>) {
            val slot = screen.hoveredSlot
            if (slot != null && !slot.item.isEmpty && slot.item.item is WandItem) hovered = slot.item
        }

        var stack: ItemStack? = null
        if (hovered != null) {
            trackedWandId = hovered.get(ModItems.WAND_ID)?.wandId
            stack = hovered
        } else if (trackedWandId != null) {
            stack = findWandById(player, trackedWandId!!)
            if (stack == null) trackedWandId = null
        }
        currentStack = stack

        val wandId = stack?.get(ModItems.WAND_ID)?.wandId
        val stats = wandId?.let { WandHudRenderer.getStats(it) }

        // 法杖图标仅在变化时更新
        if (lastWandStack != stack) {
            lastWandStack = stack
            wandSlot.stack = stack ?: ItemStack.EMPTY
        }

        val spells: List<String> = stack?.get(ModItems.WAND_SPELLS.get()) ?: emptyList()

        val manaText = if (stats != null)
            "蓝量：%.0f / %.0f".format(stats.currentMana, stats.maxMana)
        else "蓝量：-- / --"
        if (lastManaText != manaText) {
            lastManaText = manaText
            manaLabel.text = Component.literal(manaText)
        }

        val chargeText = if (stats != null) "充能：%.1f /s".format(stats.charge) else "充能：--"
        if (lastChargeText != chargeText) {
            lastChargeText = chargeText
            chargeLabel.text = Component.literal(chargeText)
        }

        val castText = if (stats != null) "施放数：${stats.cast}" else "施放数：--"
        if (lastCastText != castText) {
            lastCastText = castText
            castLabel.text = Component.literal(castText)
        }

        // 容量：优先使用网络同步的法杖槽位数量（wand.size），未同步时回退法术列表长度
        val capacityText = "容量：${stats?.size ?: spells.size} 格"
        if (lastCapacityText != capacityText) {
            lastCapacityText = capacityText
            capacityLabel.text = Component.literal(capacityText)
        }

        // 槽位网格数量随容量同步
        rebuildSlots(stats?.size ?: spells.size)

        // 法术列表仅在内容变化时更新
        grid.refresh(spells)
    }

    /** 将浮窗置于屏幕右侧空白处（最小化形态常用位置） */
    fun placeAtRight(screenW: Int, screenH: Int) {
        window.x = (screenW - window.windowWidth - 8).coerceAtLeast(0)
        window.y = ((screenH - window.windowHeight - FloatingWindow.TITLE_BAR_HEIGHT) / 2).coerceAtLeast(0)
    }

    // ---------- 同步实现 ----------

    override fun syncSwap(a: Int, b: Int) {
        val stack = currentWand() ?: return
        val wandId = stack.get(ModItems.WAND_ID)?.wandId ?: return
        EditWandPayload(EditWandPayload.ACTION_SWAP, wandId, a, b, "").send()
    }

    override fun syncAdd(index: Int, spellId: String) {
        val stack = currentWand() ?: return
        val wandId = stack.get(ModItems.WAND_ID)?.wandId ?: return
        EditWandPayload(EditWandPayload.ACTION_ADD, wandId, index, -1, spellId).send()
    }

    override fun syncRemove(index: Int, targetSlot: Int, silent: Boolean) {
        val stack = currentWand() ?: return
        val wandId = stack.get(ModItems.WAND_ID)?.wandId ?: return
        EditWandPayload(EditWandPayload.ACTION_REMOVE, wandId, index, targetSlot, "", silent).send()
    }
}
