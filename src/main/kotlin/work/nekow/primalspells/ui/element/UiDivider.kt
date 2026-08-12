package work.nekow.primalspells.ui.element

import net.minecraft.client.gui.GuiGraphicsExtractor
import work.nekow.primalspells.ui.UiScreen

/**
 * 水平分隔线：纯色横线（对应 `<hr>` 标签）。
 */
class UiDivider(
    x: Int,
    y: Int,
    width: Int,
    height: Int = 1,
    val color: Int = 0xFF_4A_4A_55.toInt(),
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        graphics.fill(x, y, x + width, y + height, color)
    }
}
