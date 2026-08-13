package work.nekow.nekoui.pouch

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.network.EditPouchPayload
import work.nekow.nekoui.FloatingWindow
import work.nekow.nekoui.HtmlUiLoader
import work.nekow.nekoui.common.SpellGridPlaceholder
import work.nekow.nekoui.common.SpellSlotGrid
import work.nekow.nekoui.common.TrackedContainerWindow

/**
 * 法术小包浮窗：显示小包容器内的法术物品，支持完整交互
 * （槽位交换、背包拖入、拖出卸载）。
 * 交互与窗口逻辑通过 [TrackedContainerWindow]/[ContainerWindowBase] 复用，
 * 同步走 [EditPouchPayload]。
 *
 * 窗口骨架由 HTML 描述（`assets/primalspells/html/pouch.html`），
 * 槽位网格通过 `<spell-grid>` 占位替换。
 *
 * 小包定位：记录 pouchId（物品组件），每次刷新在玩家背包中按 ID 查找；
 * 空 ID（未初始化）时兜底匹配第一个小包并补上服务端分配的 ID。
 * 不缓存物品引用——容器菜单（背包 / 小包 UI）切换会使引用失效。
 */
class SpellPouchWindow private constructor(
    window: FloatingWindow,
    grid: SpellSlotGrid,
) : TrackedContainerWindow(window, grid) {

    /** HTML 源文件是否在最近一次加载后被修改（外部检测后应重建窗口） */
    fun htmlModified(): Boolean = HtmlUiLoader.isModified(FILE_NAME)

    companion object {
        const val SLOT_COUNT = 16

        /** HTML 源文件名（`assets/primalspells/html/` 下） */
        const val FILE_NAME = "pouch.html"

        fun create(): SpellPouchWindow {
            val ui = HtmlUiLoader.loadWindowedFile("pouch.html")
            val placeholder = ui.elements.filterIsInstance<SpellGridPlaceholder>().first()
            val grid = SpellSlotGrid(placeholder.x, placeholder.y, placeholder.perRow, id = "grid")
            grid.setCount(SLOT_COUNT)
            // 将 HTML 中的 spell-grid 占位替换为实际网格
            val content = ui.elements.flatMap { el ->
                if (el === placeholder) listOf(grid) else listOf(el)
            }
            val window = FloatingWindow(
                ui.title ?: Component.literal("法术小包"),
                content,
                ui.windowWidth, ui.windowHeight,
            )
            return SpellPouchWindow(window, grid)
        }
    }

    // ---------- 追踪实现 ----------

    override fun idOf(stack: ItemStack): String? = SpellPouchItem.getPouchId(stack)

    override fun matches(stack: ItemStack): Boolean = stack.item is SpellPouchItem

    override fun spellsOf(stack: ItemStack): List<String> = SpellPouchItem.getSpells(stack)

    override fun slotCountOf(stack: ItemStack, spells: List<String>): Int = SLOT_COUNT

    /** 空 ID 兜底：未初始化的小包按类型匹配，并同步补上服务端分配的 ID */
    override fun matchesId(stack: ItemStack): Boolean {
        val id = idOf(stack)
        if (id == trackedId) return true
        if (trackedId == null) {
            if (id != null) trackedId = id
            return true
        }
        return false
    }

    // 小包丢失（被丢弃/移出背包）→ 隐藏窗口（基类默认行为）

    // ---------- 布局 ----------

    /** 将浮窗置于屏幕右侧（与法杖信息浮窗错开，偏上方） */
    fun placeAtRight(screenW: Int) {
        window.x = (screenW - window.windowWidth - 8).coerceAtLeast(0)
        window.y = (FloatingWindow.TITLE_BAR_HEIGHT + 8).coerceAtLeast(0)
    }

    // ---------- 同步实现 ----------
    // 注意：trackedId 可能为 null（未初始化小包），此时发空串，
    // 由服务端 findPouch 兜底匹配唯一无 ID 的小包并补写 ID。

    override fun syncSwap(a: Int, b: Int) {
        EditPouchPayload(EditPouchPayload.ACTION_SWAP, trackedId.orEmpty(), a, b, "").send()
    }

    override fun syncAdd(index: Int, spellId: String) {
        EditPouchPayload(EditPouchPayload.ACTION_ADD, trackedId.orEmpty(), index, -1, spellId).send()
    }

    override fun syncRemove(index: Int, targetSlot: Int, silent: Boolean) {
        EditPouchPayload(EditPouchPayload.ACTION_REMOVE, trackedId.orEmpty(), index, targetSlot, "", silent).send()
    }
}
