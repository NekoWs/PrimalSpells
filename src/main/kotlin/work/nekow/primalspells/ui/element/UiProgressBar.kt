package work.nekow.primalspells.ui.element

import net.minecraft.client.gui.GuiGraphicsExtractor
import work.nekow.primalspells.ui.UiScreen

/**
 * 进度条：背景 + 前景填充（对应 `<progress>` 标签）。
 * `value` 可变，运行时可通过 `id` 查找并更新。
 */
class UiProgressBar(
    x: Int,
    y: Int,
    width: Int,
    height: Int = 4,
    var value: Float = 0f,
    var max: Float = 100f,
    val color: Int = 0xFF_33_99_FF.toInt(),
    val backgroundColor: Int = 0xFF_2A_2A_32.toInt(),
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        graphics.fill(x, y, x + width, y + height, backgroundColor)
        val fill = (width * (value / max).coerceIn(0f, 1f)).toInt()
        if (fill > 0) graphics.fill(x, y, x + fill, y + height, color)
    }
}
