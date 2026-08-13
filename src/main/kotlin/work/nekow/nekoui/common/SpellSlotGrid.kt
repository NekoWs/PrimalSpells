package work.nekow.nekoui.common

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.ModItems
import work.nekow.nekoui.UiScreen
import work.nekow.nekoui.element.UiElement
import work.nekow.nekoui.element.UiSlot
import work.nekow.nekoui.slot.SlotProvider

/**
 * 法术槽位网格：一个持有可变槽位列表的 UI 元素，负责槽位增删、内容刷新与渲染。
 *
 * 由容器窗口（法杖信息窗 / 小包窗）调用 [setCount] 调整槽位数量、[refresh] 同步法术内容。
 * 槽位对象只增删、不整体重建，避免刷新时网格"消失"或闪烁；
 * 每次内容变化由服务端同步（组件刷新）驱动，网格仅做展示。
 */
class SpellSlotGrid(
    private val startX: Int,
    private val startY: Int,
    private val perRow: Int,
    override val id: String? = null,
) : UiElement(startX, startY, 0, 0), SlotProvider {

    private val slots = mutableListOf<UiSlot>()
    private var lastSpells: List<String>? = null

    /** 当前槽位数量 */
    val size: Int get() = slots.size

    /** 全部槽位（供拖拽/交换/tooltip 遍历） */
    override fun slots(): List<UiSlot> = slots

    /** 槽位在网格中的索引 */
    fun indexOf(slot: UiSlot): Int = slots.indexOf(slot)

    /** 返回包含坐标 (mx,my) 的槽位索引，无则 -1（坐标相对内容区） */
    fun slotIndexAt(mx: Int, my: Int): Int =
        slots.indexOfFirst { it.contains(mx.toDouble(), my.toDouble()) }

    /** 调整槽位数量：不足则追加、超出则移除；已有槽位对象保持不变 */
    fun setCount(count: Int) {
        val need = count.coerceAtLeast(0)
        if (slots.size == need) return
        lastSpells = null // 槽位数变化，强制下次 refresh 重新填充
        while (slots.size < need) {
            val i = slots.size
            slots += UiSlot(
                startX + (i % perRow) * (SLOT_SIZE + SLOT_GAP),
                startY + (i / perRow) * (SLOT_SIZE + SLOT_GAP),
            )
        }
        while (slots.size > need) {
            slots.removeAt(slots.size - 1)
        }
        val rows = (need + perRow - 1) / perRow
        width = if (need > 0) perRow * SLOT_SIZE + (perRow - 1) * SLOT_GAP else 0
        height = if (need > 0) rows * SLOT_SIZE + (rows - 1) * SLOT_GAP else 0
    }

    /** 按法术 id 列表刷新槽位内容（仅在内容变化时更新） */
    fun refresh(spells: List<String>) {
        if (lastSpells == spells) return
        lastSpells = spells
        slots.forEachIndexed { i, slot ->
            val id = spells.getOrNull(i)
            val item = id?.takeIf { it.isNotEmpty() }?.let { ModItems.spellItem(it) }
            slot.stack = if (item != null) ItemStack(item) else ItemStack.EMPTY
        }
    }

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        slots.forEach { it.render(screen, graphics, mouseX, mouseY, partialTick) }
    }

    override fun mouseClicked(screen: UiScreen?, button: Int, mx: Double, my: Double): Boolean {
        if (!visible) return false
        for (slot in slots) {
            if (slot.mouseClicked(screen, button, mx, my)) return true
        }
        return false
    }

    companion object {
        const val SLOT_SIZE = 18
        const val SLOT_GAP = 2
    }
}

/**
 * HTML `<spell-grid>` 标签生成的占位元素：标记网格的起始坐标与每行槽位数，
 * 由容器窗口在构建时替换为实际 [SpellSlotGrid]。
 * 本身不渲染任何内容；[width]/[height] 用于父级 `height/width:auto` 的尺寸估算。
 */
class SpellGridPlaceholder(
    override val id: String?,
    x: Int,
    y: Int,
    val perRow: Int,
    width: Int = 0,
    height: Int = 0,
) : UiElement(x, y, width, height) {
    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
