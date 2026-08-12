package work.nekow.primalspells.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.ModItems

/** 法术小包 tooltip 数据：容器内的法术 id 列表 */
class PouchTooltip(
    val spells: List<String>
) : TooltipComponent

/** 法术小包 tooltip 渲染：收纳袋式槽位网格 */
class ClientPouchTooltip(
    private val tooltip: PouchTooltip
) : ClientTooltipComponent {

    override fun getHeight(font: Font): Int {
        val rows = (tooltip.spells.size + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW
        return rows.coerceAtLeast(1) * SLOT_SIZE + 4
    }

    override fun getWidth(font: Font): Int =
        tooltip.spells.size.coerceAtMost(SLOTS_PER_ROW) * SLOT_SIZE + 4

    override fun showTooltipWithItemInHand() = true

    override fun extractImage(font: Font, x: Int, y: Int, w: Int, h: Int, graphics: GuiGraphicsExtractor) {
        for (i in tooltip.spells.indices) {
            val sx = x + 2 + (i % SLOTS_PER_ROW) * SLOT_SIZE
            val sy = y + 2 + (i / SLOTS_PER_ROW) * SLOT_SIZE
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BG, sx, sy, SLOT_SIZE, SLOT_SIZE)
            val spellId = tooltip.spells[i]
            if (spellId.isNotEmpty()) {
                val item = ModItems.MAGICS[spellId]?.get() ?: continue
                val stack = ItemStack(item)
                graphics.item(stack, sx + 1, sy + 1)
                graphics.itemDecorations(font, stack, sx + 1, sy + 1)
            }
        }
    }

    companion object {
        const val SLOT_SIZE = 18
        const val SLOTS_PER_ROW = 9
        val SLOT_BG: Identifier = Identifier.withDefaultNamespace("container/bundle/slot_background")
    }
}
