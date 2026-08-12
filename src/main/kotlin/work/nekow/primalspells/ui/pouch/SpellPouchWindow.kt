package work.nekow.primalspells.ui.pouch

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.network.EditPouchPayload
import work.nekow.primalspells.ui.FloatingWindow
import work.nekow.primalspells.ui.HtmlUiLoader
import work.nekow.primalspells.ui.common.ContainerWindowBase
import work.nekow.primalspells.ui.common.SpellGridPlaceholder
import work.nekow.primalspells.ui.common.SpellSlotGrid

/**
 * 法术小包浮窗：显示小包容器内的法术物品，支持完整交互
 * （槽位交换、背包拖入、拖出卸载）。
 * 交互与窗口逻辑通过 [ContainerWindowBase] 复用，同步走 [EditPouchPayload]。
 *
 * 窗口骨架由 HTML 描述（`assets/primalspells/html/pouch.html`），
 * 槽位网格通过 `<spell-grid>` 占位替换为实际槽位。
 *
 * 小包定位：记录 [pouchId]（物品组件），每次刷新在玩家背包中按 ID 查找。
 * 不记录菜单槽位索引——容器菜单（背包 / 小包 UI）切换会使索引失效。
 */
class SpellPouchWindow private constructor(
    window: FloatingWindow,
    grid: SpellSlotGrid,
) : ContainerWindowBase(window, grid) {

    /** 当前打开的小包唯一 id（组件 POUCH_ID），用于跨菜单定位 */
    var pouchId: String? = null

    /** 当前打开的小包（客户端引用，随容器同步更新） */
    var pouchStack: ItemStack? = null

    /** HTML 源文件是否在最近一次加载后被修改（外部检测后应重建窗口） */
    fun htmlModified(): Boolean = HtmlUiLoader.isModified(FILE_NAME)

    companion object {
        const val SLOT_COUNT = 16

        /** HTML 源文件名（`assets/primalspells/html/` 下） */
        const val FILE_NAME = "pouch.html"

        fun create(): SpellPouchWindow {
            val ui = HtmlUiLoader.loadWindowedFile("pouch.html")
            val placeholder = ui.elements.filterIsInstance<SpellGridPlaceholder>().first()
            val grid = SpellSlotGrid.create(SLOT_COUNT, placeholder.x, placeholder.y, placeholder.perRow)
            // 将 HTML 中的 spell-grid 占位替换为实际槽位
            val content = ui.elements.flatMap { el ->
                if (el === placeholder) grid.slots else listOf(el)
            }
            val window = FloatingWindow(
                ui.title ?: Component.literal("法术小包"),
                content,
                ui.windowWidth, ui.windowHeight,
            )
            return SpellPouchWindow(window, grid)
        }
    }

    /**
     * 从玩家背包（手持/物品栏）按 [pouchId] 重新获取小包的最新 ItemStack 引用
     * （容器同步会替换槽位对象，缓存的旧引用读不到新组件；
     *  且容器菜单切换会使槽位索引失效，故按 ID 遍历背包而非索引）。
     * @return true 表示小包仍可定位
     */
    fun locatePouch(player: Player?): Boolean {
        if (player == null) return false
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (s.isEmpty || s.item !is SpellPouchItem) continue
            if (SpellPouchItem.getPouchId(s) == pouchId) {
                pouchStack = s
                return true
            }
        }
        return false
    }

    /** 每帧刷新槽位内容（数据来自小包组件，每次重新解析避免引用过期） */
    override fun refresh(player: Player?, screen: Screen?) {
        if (!locatePouch(player)) return
        grid.refresh(SpellPouchItem.getSpells(pouchStack!!))
    }

    /** 将浮窗置于屏幕右侧（与法杖信息浮窗错开，偏上方） */
    fun placeAtRight(screenW: Int, screenH: Int) {
        window.x = (screenW - window.windowWidth - 8).coerceAtLeast(0)
        window.y = (FloatingWindow.TITLE_BAR_HEIGHT + 8).coerceAtLeast(0)
    }

    // ---------- 同步实现 ----------
    // 注意：pouchId 可能为 null（未初始化小包），此时发空串，
    // 由服务端 findPouch 兜底匹配唯一无 ID 的小包并补写 ID。

    override fun syncSwap(a: Int, b: Int) {
        EditPouchPayload(EditPouchPayload.ACTION_SWAP, pouchId.orEmpty(), a, b, "").send()
    }

    override fun syncAdd(index: Int, spellId: String) {
        EditPouchPayload(EditPouchPayload.ACTION_ADD, pouchId.orEmpty(), index, -1, spellId).send()
    }

    override fun syncRemove(index: Int, targetSlot: Int, silent: Boolean) {
        EditPouchPayload(EditPouchPayload.ACTION_REMOVE, pouchId.orEmpty(), index, targetSlot, "", silent).send()
    }
}
