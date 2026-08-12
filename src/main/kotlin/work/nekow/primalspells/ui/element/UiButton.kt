package work.nekow.primalspells.ui.element

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import work.nekow.primalspells.ui.UiScreen

/**
 * 按钮：悬停高亮，左键点击触发回调。
 * 支持自定义背景色、悬停背景色、边框色、文本对齐与禁用状态。
 */
class UiButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val text: Component,
    val onClick: (UiScreen) -> Unit,
    val enabled: Boolean = true,
    val background: Int = 0xFF_2B_2B_30.toInt(),
    val hoverBackground: Int = 0xFF_40_40_46.toInt(),
    val borderColor: Int = 0xFF_4E_4E_55.toInt(),
    val textColor: Int = 0xFF_C8_C8_C8.toInt(),
    val hoverTextColor: Int = 0xFF_FF_FF_FF.toInt(),
    val align: TextAlign = TextAlign.CENTER,
    val fontScale: Float = 1f,
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        val font = fontOf(screen)
        val hovered = contains(mouseX.toDouble(), mouseY.toDouble()) && enabled
        val bg = when {
            !enabled -> background and 0x7FFFFFFF
            hovered -> hoverBackground
            else -> background
        }
        graphics.fill(x, y, x + width, y + height, bg)
        graphics.fill(x, y, x + width, y + 1, borderColor)
        graphics.fill(x, y, x + 1, y + height, borderColor)
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor and 0x60FFFFFF)
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor and 0x60FFFFFF)
        val color = when {
            !enabled -> textColor and 0x7FFFFFFF
            hovered -> hoverTextColor
            else -> textColor
        }
        val lineH = (font.lineHeight * fontScale).toInt()
        val ty = y + (height - lineH) / 2
        if (fontScale == 1f) {
            when (align) {
                TextAlign.LEFT -> graphics.text(font, text, x + 6, ty, color)
                TextAlign.CENTER -> graphics.centeredText(font, text, x + width / 2, ty, color)
                TextAlign.RIGHT -> graphics.text(font, text, x + width - 6 - font.width(text), ty, color)
            }
        } else {
            graphics.pose().pushMatrix()
            graphics.pose().translate(x.toFloat(), ty.toFloat())
            graphics.pose().scale(fontScale, fontScale)
            when (align) {
                TextAlign.LEFT -> graphics.text(font, text, 6, 0, color)
                TextAlign.CENTER -> graphics.centeredText(font, text, (width / fontScale / 2).toInt(), 0, color)
                TextAlign.RIGHT -> graphics.text(font, text, (width / fontScale).toInt() - 6 - font.width(text), 0, color)
            }
            graphics.pose().popMatrix()
        }
    }

    override fun mouseClicked(screen: UiScreen?, button: Int, mx: Double, my: Double): Boolean {
        if (!visible || !enabled || !contains(mx, my)) return false
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false
        if (screen != null) onClick(screen)
        return true
    }
}
