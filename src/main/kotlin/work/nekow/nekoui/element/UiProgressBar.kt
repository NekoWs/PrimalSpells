package work.nekow.nekoui.element

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import work.nekow.nekoui.UiScreen

/**
 * 进度条：背景 + 前景填充（对应 `<progress>` 标签）。
 * `value` 可变，运行时可通过 `id` 查找并更新；变化时按游戏 tick 平滑过渡（帧率无关）。
 * `animated=true`（HTML 属性）时自动循环动画：0 → max 往复。
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
    var animated: Boolean = false,
    override val id: String? = null,
) : UiElement(x, y, width, height) {

    /** 当前显示值（动画/过渡目标 [value]） */
    private var displayValue = 0f
    private var animDir = 1f
    private var lastGameTime = -1L

    override fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        advance()
        val bg = cssColorState("background", backgroundColor, backgroundColor, mouseX, mouseY)
        val fg = cssColorState("color", color, color, mouseX, mouseY)
        graphics.fill(x, y, x + width, y + height, bg)
        val fill = (width * (displayValue / max).coerceIn(0f, 1f)).toInt()
        if (fill > 0) graphics.fill(x, y, x + fill, y + height, fg)
    }

    /** 按游戏 tick 推进显示值：animated 时循环动画，否则平滑逼近 value */
    private fun advance() {
        val gameTime = Minecraft.getInstance().level?.gameTime ?: 0L
        val dt = if (lastGameTime < 0) 0f else (gameTime - lastGameTime).toFloat()
        lastGameTime = gameTime
        if (dt <= 0f) return
        if (animated) {
            displayValue += animDir * max * dt * 0.05f // 约 1 秒一个完整周期
            if (displayValue >= max) { displayValue = max; animDir = -1f }
            if (displayValue <= 0f) { displayValue = 0f; animDir = 1f }
        } else {
            displayValue += (value - displayValue) * minOf(1f, dt * 0.2f)
        }
    }
}
