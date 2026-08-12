package work.nekow.nekoui.element

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import work.nekow.nekoui.UiScreen

/**
 * 窗口/面板：带边框和标题的半透明背景区域，可选背景贴图。
 */
class UiWindow(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val title: Component = Component.empty(),
    val background: Int = 0xF0_1A_1A_1E.toInt(),
    val borderColor: Int = 0xFF_3D_3D_42.toInt(),
    val titleColor: Int = 0xFF_FF_FF_FF.toInt(),
    val image: Identifier? = null,
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        val bg = cssColorState("background", background, background, mouseX, mouseY)
        val border = cssColorState("border-color", borderColor, borderColor, mouseX, mouseY)
        graphics.fill(x, y, x + width, y + height, bg)
        if (image != null) {
            graphics.blit(image, x, y, x + width, y + height, 0f, 1f, 0f, 1f)
        }
        graphics.fill(x, y, x + width, y + 1, border)
        graphics.fill(x, y, x + 1, y + height, border)
        graphics.fill(x + width - 1, y, x + width, y + height, border and 0x60FFFFFF)
        graphics.fill(x, y + height - 1, x + width, y + height, border and 0x60FFFFFF)
        if (title != Component.empty()) {
            graphics.text(fontOf(screen), title, x + 8, y + 5, titleColor)
        }
    }
}
