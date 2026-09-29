package work.nekow.primalspells.client.tooltip

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.registry.ModItems

/**
 * 法术小包 tooltip 数据：容器内的法术 id 列表 + 小包标识（用于查询本地选中的槽位做高亮）。
 */
class PouchTooltip(
    val spells: List<String>,
    val pouchId: String,
) : TooltipComponent

/** 法术小包 tooltip 渲染：收纳袋式槽位网格（固定容量，空槽留空，选中槽高亮）。 */
class ClientPouchTooltip(
    private val tooltip: PouchTooltip
) : ClientTooltipComponent {

    override fun getHeight(font: Font): Int {
        val rows = (SLOT_COUNT + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW
        return rows * SLOT_SIZE + 4
    }

    override fun getWidth(font: Font): Int = SLOTS_PER_ROW * SLOT_SIZE + 4

    override fun showTooltipWithItemInHand() = true

    override fun extractImage(font: Font, x: Int, y: Int, w: Int, h: Int, graphics: GuiGraphicsExtractor) {
        val selected = SpellSlotTracker.getSelectedSlot(tooltip.pouchId)
        for (i in 0 until SLOT_COUNT) {
            val sx = x + 2 + (i % SLOTS_PER_ROW) * SLOT_SIZE
            val sy = y + 2 + (i / SLOTS_PER_ROW) * SLOT_SIZE
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BG, sx, sy, SLOT_SIZE, SLOT_SIZE)
            if (i == selected) {
                graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, SELECTED_HIGHLIGHT)
            }
            val spellId = tooltip.spells.getOrNull(i).orEmpty()
            if (spellId.isNotEmpty()) {
                val item = ModItems.spellItem(spellId) ?: continue
                val stack = ItemStack(item)
                graphics.item(stack, sx + 1, sy + 1)
                graphics.itemDecorations(font, stack, sx + 1, sy + 1)
            }
        }
    }

    companion object {
        /** 容量与组件语义共用同一常量，避免两处硬编码不一致。 */
        const val SLOT_COUNT = SpellPouchItem.SLOT_COUNT
        const val SLOTS_PER_ROW = 8
        const val SLOT_SIZE = 18

        /** 选中槽的半透明白色高亮（与法杖 tooltip 一致） */
        const val SELECTED_HIGHLIGHT = 0x80_FF_FF_FF.toInt()

        val SLOT_BG: Identifier = Identifier.withDefaultNamespace("container/bundle/slot_background")
    }
}
