package work.nekow.primalspells.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.ModItems

class WandSpellTooltip(
    val spells: List<String>,
    val wandId: String
): TooltipComponent

class ClientWandSpellTooltip(
    private val tooltip: WandSpellTooltip
): ClientTooltipComponent {

    override fun getHeight(font: Font) =
        ((tooltip.spells.size + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW).coerceAtLeast(1) * SLOT_SIZE + 4

    override fun getWidth(font: Font) =
        tooltip.spells.size.coerceAtMost(SLOTS_PER_ROW) * SLOT_SIZE + 4

    override fun showTooltipWithItemInHand() = true

    override fun extractImage(font: Font, x: Int, y: Int, w: Int, h: Int, graphics: GuiGraphicsExtractor) {
        val selected = WandTooltipTracker.getSelectedSlot(tooltip.wandId)
        for (i in tooltip.spells.indices) {
            val sx = x + 2 + (i % SLOTS_PER_ROW) * SLOT_SIZE
            val sy = y + 2 + (i / SLOTS_PER_ROW) * SLOT_SIZE
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BG, sx, sy, SLOT_SIZE, SLOT_SIZE)
            if (i == selected) {
                graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x80_FF_FF_FF.toInt())
            }
            val spellId = tooltip.spells.getOrElse(i) { "" }
            if (spellId.isNotEmpty()) {
                val item = ModItems.spellItem(spellId) ?: continue
                val stack = ItemStack(item)
                graphics.item(stack, sx + 1, sy + 1)
                graphics.itemDecorations(font, stack, sx + 1, sy + 1)
            }
        }
    }

    companion object {
        const val SLOT_SIZE = 18
        const val SLOTS_PER_ROW = 9
        val SLOT_BG = Identifier.withDefaultNamespace("container/bundle/slot_background")
    }
}
