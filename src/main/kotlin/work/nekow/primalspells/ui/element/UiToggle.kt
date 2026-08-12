package work.nekow.primalspells.ui.element

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import work.nekow.primalspells.ui.UiScreen

/**
 * 复选框/开关：点击切换勾选状态（对应 `<checkbox>` 标签）。
 * 勾选时背景高亮，文字前绘制方框标记。
 */
class UiToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val text: Component,
    var checked: Boolean = false,
    val onClick: (UiScreen?, Boolean) -> Unit = { _, _ -> },
    val checkedBackground: Int = 0xFF_2F_6F_3F.toInt(),
    val uncheckedBackground: Int = 0xFF_2B_2B_30.toInt(),
    val borderColor: Int = 0xFF_4E_4E_55.toInt(),
    val textColor: Int = 0xFF_E0E0E0.toInt(),
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        val font = fontOf(screen)
        graphics.fill(x, y, x + width, y + height, if (checked) checkedBackground else uncheckedBackground)
        graphics.fill(x, y, x + width, y + 1, borderColor)
        graphics.fill(x, y, x + 1, y + height, borderColor)
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor and 0x60FFFFFF)
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor and 0x60FFFFFF)
        // 方框标记
        val box = (height - 8).coerceAtLeast(4)
        graphics.fill(x + 4, y + (height - box) / 2, x + 4 + box, y + (height - box) / 2 + box,
            if (checked) 0xFF_7F_FF_7F.toInt() else 0xFF_55_55_60.toInt())
        if (checked) {
            graphics.fill(x + 5, y + (height - box) / 2 + 2, x + 4 + box - 1, y + (height - box) / 2 + 2 + 2, 0xFF_7F_FF_7F.toInt())
        }
        graphics.text(font, text, x + 4 + box + 4, y + (height - font.lineHeight) / 2, textColor)
    }

    override fun mouseClicked(screen: UiScreen?, button: Int, mx: Double, my: Double): Boolean {
        if (!visible || !contains(mx, my)) return false
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false
        checked = !checked
        onClick(screen, checked)
        return true
    }
}
