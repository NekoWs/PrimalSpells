package work.nekow.nekoui.wand

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.client.WandHudRenderer
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.network.EditWandPayload
import work.nekow.nekoui.FloatingWindow
import work.nekow.nekoui.HtmlUiLoader
import work.nekow.nekoui.common.SpellGridPlaceholder
import work.nekow.nekoui.common.SpellSlotGrid
import work.nekow.nekoui.common.TrackedContainerWindow
import work.nekow.nekoui.element.UiElement
import work.nekow.nekoui.element.UiLabel
import work.nekow.nekoui.element.UiSlot

/**
 * 法杖信息悬浮窗：显示当前追踪法杖的基础信息（蓝量、容量、充能等）与法术槽位。
 *
 * 窗口骨架由 HTML 描述（`assets/primalspells/html/wand_info.html`），
 * 动态部分（槽位网格、信息文本）按元素 `id` 查找并更新。
 * 追踪规则：鼠标悬停背包中的法杖时追踪该法杖；移开后保持显示上一次悬停的法杖；
 * 法杖丢失时窗口常驻并显示 "--"（不隐藏）。
 * 定位与内容刷新复用 [TrackedContainerWindow]，同步走 [EditWandPayload]。
 */
class WandInfoWindow private constructor(
    window: FloatingWindow,
    private val wandSlot: UiSlot,
    private val manaLabel: UiLabel,
    private val capacityLabel: UiLabel,
    private val chargeLabel: UiLabel,
    private val castLabel: UiLabel,
    grid: SpellSlotGrid,
    private val gridTop: Int,
    private val perRow: Int,
) : TrackedContainerWindow(window, grid) {

    /** HTML 源文件是否在最近一次加载后被修改（外部检测后应重建窗口） */
    fun htmlModified(): Boolean = HtmlUiLoader.isModified(FILE_NAME)

    /** 内容缓存（仅变化时刷新，避免每帧重建导致闪烁） */
    private var lastWandStack: ItemStack? = null
    private var lastManaText: String? = null
    private var lastChargeText: String? = null
    private var lastCastText: String? = null
    private var lastCapacityText: String? = null

    /** 最近一次自动布局时的槽位数（用户手动缩放后，槽位数变化时重新应用动态高度） */
    private var lastAutoSlotCount = -1

    companion object {
        const val MAX_SLOTS = 32

        /** HTML 源文件名（`assets/primalspells/html/` 下） */
        const val FILE_NAME = "wand_info.html"

        /** 槽位区底部留白（动态窗口高度 = 槽位区 + 留白） */
        const val BOTTOM_PADDING = 6

        /** 按槽位数计算窗口内容区高度（槽位 18px + 2px 间距/行） */
        fun contentHeight(slotTop: Int, slotCount: Int, perRow: Int = GRID_PER_ROW): Int {
            val need = slotCount.coerceIn(0, MAX_SLOTS)
            val rows = (need + perRow - 1) / perRow
            return slotTop + rows * (SpellSlotGrid.SLOT_SIZE + SpellSlotGrid.SLOT_GAP) - SpellSlotGrid.SLOT_GAP + BOTTOM_PADDING
        }

        /** HTML 中 spell-grid 的每行槽位数（与 wand_info.html 的 per-row 一致） */
        const val GRID_PER_ROW = 8

        /** HTML 中未找到 spell-grid 时的回退起始 Y */
        private const val GRID_TOP_FALLBACK = 66

        fun create(): WandInfoWindow {
            val ui = HtmlUiLoader.loadWindowedFile(FILE_NAME)

            fun <T : UiElement> byId(clazz: Class<T>, id: String): T? =
                ui.elements.filterIsInstance(clazz).firstOrNull { it.id == id }

            val wandSlot = byId(UiSlot::class.java, "wand")
                ?: UiSlot(8, 8, id = "wand")
            val manaLabel = byId(UiLabel::class.java, "mana")
                ?: UiLabel(Component.literal("蓝量：-- / --"), 34, 10, color = 0xFF_55_AA_FF.toInt(), id = "mana")
            val capacityLabel = byId(UiLabel::class.java, "capacity")
                ?: UiLabel(Component.literal("容量：-- 格"), 34, 22, id = "capacity")
            val chargeLabel = byId(UiLabel::class.java, "charge")
                ?: UiLabel(Component.literal("充能：--"), 34, 34, id = "charge")
            val castLabel = byId(UiLabel::class.java, "cast")
                ?: UiLabel(Component.literal("施放数：--"), 34, 46, id = "cast")

            // 由占位符确定网格位置，并整体替换为可变槽位的网格（网格对象固定，只增删内部槽位）
            val placeholder = ui.elements.filterIsInstance<SpellGridPlaceholder>().firstOrNull()
            val gridTop = placeholder?.y ?: GRID_TOP_FALLBACK
            val perRow = placeholder?.perRow ?: GRID_PER_ROW
            val grid = SpellSlotGrid(placeholder?.x ?: 8, gridTop, perRow, id = "grid")
            val content = ui.elements.flatMap { el -> if (el === placeholder) listOf(grid) else listOf(el) }

            val window = FloatingWindow(
                ui.title ?: Component.literal("法杖信息"),
                content,
                ui.windowWidth, contentHeight(gridTop, 0, perRow),
            )
            return WandInfoWindow(window, wandSlot, manaLabel, capacityLabel, chargeLabel, castLabel, grid, gridTop, perRow)
        }
    }

    /** 当前追踪的法杖物品（最新引用） */
    fun currentWand(): ItemStack? = currentStack

    // ---------- 追踪实现 ----------

    override fun idOf(stack: ItemStack): String? = stack.get(ModItems.WAND_ID)?.wandId

    override fun matches(stack: ItemStack): Boolean = stack.item is WandItem

    override fun spellsOf(stack: ItemStack): List<String> = stack.get(ModItems.WAND_SPELLS.get()) ?: emptyList()

    override fun slotCountOf(stack: ItemStack, spells: List<String>): Int {
        val stats = idOf(stack)?.let { WandHudRenderer.getStats(it) }
        return (stats?.size ?: spells.size).coerceIn(0, MAX_SLOTS)
    }

    /** 每帧刷新：先悬停追踪（悬停法杖更新目标），再统一定位 + 更新 */
    override fun refresh(player: Player?, screen: Screen?) {
        if (screen is AbstractContainerScreen<*>) {
            val slot = screen.hoveredSlot
            if (slot != null && !slot.item.isEmpty && slot.item.item is WandItem) {
                trackedId = slot.item.get(ModItems.WAND_ID)?.wandId
            }
        }
        super.refresh(player, screen)
    }

    /** 法杖丢失：窗口常驻，清除追踪并显示 "--"（网格已清空） */
    override fun onLost() {
        if (trackedId == null) return
        trackedId = null
        updateInfo(null)
        grid.setCount(0)
        grid.refresh(emptyList())
    }

    /** 内容更新：标签 + 窗口高度（网格已由基类更新） */
    override fun onContentChanged(stack: ItemStack, player: Player?, screen: Screen?) {
        updateInfo(stack)
    }

    /** 更新法杖图标、信息标签与窗口高度（仅变化时刷新） */
    private fun updateInfo(stack: ItemStack?) {
        if (lastWandStack != stack) {
            lastWandStack = stack
            wandSlot.stack = stack ?: ItemStack.EMPTY
        }

        val stats = stack?.let { idOf(it) }?.let { WandHudRenderer.getStats(it) }

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
        val slotCount = if (stack != null) slotCountOf(stack, spellsOf(stack)) else 0
        val capacityText = "容量：$slotCount 格"
        if (lastCapacityText != capacityText) {
            lastCapacityText = capacityText
            capacityLabel.text = Component.literal(capacityText)
        }

        // 窗口高度随槽位数同步（用户手动缩放后暂停自动高度，直到槽位数变化）
        val height = contentHeight(gridTop, slotCount, perRow)
        if (window.userResized) {
            if (slotCount != lastAutoSlotCount) {
                window.userResized = false
                window.windowHeight = height
                lastAutoSlotCount = slotCount
                window.stretchBackgroundPanels()
            }
        } else {
            lastAutoSlotCount = slotCount
            if (window.windowHeight != height) {
                window.windowHeight = height
                window.stretchBackgroundPanels()
            }
        }
    }

    /** 将浮窗置于屏幕右侧空白处（最小化形态常用位置） */
    fun placeAtRight(screenW: Int, screenH: Int) {
        window.x = (screenW - window.windowWidth - 8).coerceAtLeast(0)
        window.y = ((screenH - window.windowHeight - FloatingWindow.TITLE_BAR_HEIGHT) / 2).coerceAtLeast(0)
    }

    // ---------- 同步实现 ----------

    override fun syncSwap(a: Int, b: Int) {
        val wandId = currentWand()?.let { idOf(it) } ?: return
        EditWandPayload(EditWandPayload.ACTION_SWAP, wandId, a, b, "").send()
    }

    override fun syncAdd(index: Int, spellId: String) {
        val wandId = currentWand()?.let { idOf(it) } ?: return
        EditWandPayload(EditWandPayload.ACTION_ADD, wandId, index, -1, spellId).send()
    }

    override fun syncRemove(index: Int, targetSlot: Int, silent: Boolean) {
        val wandId = currentWand()?.let { idOf(it) } ?: return
        EditWandPayload(EditWandPayload.ACTION_REMOVE, wandId, index, targetSlot, "", silent).send()
    }
}
