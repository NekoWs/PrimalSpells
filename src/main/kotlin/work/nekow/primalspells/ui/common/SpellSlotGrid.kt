package work.nekow.primalspells.ui.common

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.ui.UiScreen
import work.nekow.primalspells.ui.element.UiElement
import work.nekow.primalspells.ui.element.UiSlot

/**
 * 通用法术槽位网格：管理一组槽位并按法术 id 列表刷新内容。
 * 法杖信息窗与法术小包窗共用。
 */
class SpellSlotGrid(
    val slots: List<UiSlot>,
) {
    private var lastSpells: List<String>? = null

    /** 槽位在网格中的索引 */
    fun indexOf(slot: UiSlot): Int = slots.indexOf(slot)

    /** 按法术 id 列表刷新槽位内容（仅在变化时更新） */
    fun refresh(spells: List<String>) {
        if (lastSpells == spells) return
        lastSpells = spells
        slots.forEachIndexed { i, slot ->
            val id = spells.getOrNull(i)
            val item = id?.takeIf { it.isNotEmpty() }?.let { ModItems.MAGICS[it]?.get() }
            slot.stack = if (item != null) ItemStack(item) else ItemStack.EMPTY
        }
    }

    companion object {
        const val SLOT_SIZE = 18
        const val SLOT_GAP = 2

        /** 创建网格（每行 [perRow] 个，从 (startX, startY) 起排列） */
        fun create(count: Int, startX: Int, startY: Int, perRow: Int): SpellSlotGrid {
            val slots = (0 until count).map { i ->
                UiSlot(
                    startX + (i % perRow) * (SLOT_SIZE + SLOT_GAP),
                    startY + (i / perRow) * (SLOT_SIZE + SLOT_GAP),
                )
            }
            return SpellSlotGrid(slots)
        }
    }
}

/**
 * HTML `<spell-grid>` 标签生成的占位元素：标记网格的起始坐标与每行槽位数，
 * 由容器窗口在构建时替换为实际 [SpellSlotGrid] 槽位（见 SpellSlotGrid.create）。
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
