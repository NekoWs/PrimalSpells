package work.nekow.nekoui.element

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import work.nekow.nekoui.UiScreen

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
    val fontScale: Float = 1f,
    val wrap: Boolean = false,
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        val font = fontOf(screen)
        val color = cssColorState("color", color, color, mouseX, mouseY)
        val lineH = (font.lineHeight * fontScale).toInt()
        // 仅显式声明了宽度（wrap=true）且文本超宽时自动折行；否则原样输出（动态文本变长不换行）
        val lines = if (wrap && width > 0) wrapLines(text.getString(), font, width) else text.getString().split('\n')
        lines.forEachIndexed { i, line ->
            if (line.isEmpty()) return@forEachIndexed
            val ly = y + i * (lineH + lineSpacing)
            if (fontScale == 1f) {
                when (align) {
                    TextAlign.LEFT -> graphics.text(font, Component.literal(line), x, ly, color)
                    TextAlign.CENTER -> graphics.centeredText(font, Component.literal(line), x + width / 2, ly, color)
                    TextAlign.RIGHT -> graphics.text(font, Component.literal(line), x + width - font.width(line), ly, color)
                }
            } else {
                graphics.pose().pushMatrix()
                graphics.pose().translate(x.toFloat(), ly.toFloat())
                graphics.pose().scale(fontScale, fontScale)
                when (align) {
                    TextAlign.LEFT -> graphics.text(font, Component.literal(line), 0, 0, color)
                    TextAlign.CENTER -> graphics.centeredText(font, Component.literal(line), (width / fontScale / 2).toInt(), 0, color)
                    TextAlign.RIGHT -> graphics.text(font, Component.literal(line), (width / fontScale).toInt() - font.width(line), 0, color)
                }
                graphics.pose().popMatrix()
            }
        }
    }

    /** 按 [maxWidth] 折行：先按 \n 分段，每段再逐字符累积到超宽拆行 */
    private fun wrapLines(text: String, font: net.minecraft.client.gui.Font, maxWidth: Int): List<String> {
        val out = mutableListOf<String>()
        for (segment in text.split('\n')) {
            var cur = StringBuilder()
            for (ch in segment) {
                val test = cur.toString() + ch
                if (cur.isNotEmpty() && font.width(test) > maxWidth) {
                    out += cur.toString()
                    cur = StringBuilder(ch.toString())
                } else {
                    cur.append(ch)
                }
            }
            if (cur.isNotEmpty()) out += cur.toString()
        }
        if (out.isEmpty()) out += ""
        return out
    }
}
