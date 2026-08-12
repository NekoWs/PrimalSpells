package work.nekow.primalspells.ui.element

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import work.nekow.primalspells.ui.UiScreen

/** 文本对齐方式 */
enum class TextAlign { LEFT, CENTER, RIGHT }

/**
 * 文本标签：在指定位置绘制文字，支持多行与对齐。
 */
class UiLabel(
    var text: Component,
    x: Int,
    y: Int,
    width: Int = 0,
    height: Int = 0,
    val color: Int = 0xFF_E0E0E0.toInt(),
    val align: TextAlign = TextAlign.LEFT,
    val lineSpacing: Int = 1,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        val font = fontOf(screen)
        val lines = text.getString().split('\n')
        lines.forEachIndexed { i, line ->
            if (line.isEmpty()) return@forEachIndexed
            val ly = y + i * (font.lineHeight + lineSpacing)
            when (align) {
                TextAlign.LEFT -> graphics.text(font, Component.literal(line), x, ly, color)
                TextAlign.CENTER -> graphics.centeredText(font, Component.literal(line), x + width / 2, ly, color)
                TextAlign.RIGHT -> graphics.text(font, Component.literal(line), x + width - font.width(line), ly, color)
            }
        }
    }
}
