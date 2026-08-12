package work.nekow.primalspells.ui.element

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import work.nekow.primalspells.ui.UiScreen

/**
 * UI 元素基类。所有元件都通过 (x, y, width, height) 定位，类似 HTML 的绝对定位。
 * [screen] 在无宿主屏幕渲染时（如悬浮窗口）为 null。
 */
abstract class UiElement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    var visible: Boolean = true

    /** 判断坐标是否落在元件范围内 */
    fun contains(mx: Double, my: Double): Boolean =
        mx >= x && mx < x + width && my >= y && my < y + height

    /** 获取可用字体（悬浮窗口等无宿主场景回退到 Minecraft 主字体） */
    protected fun fontOf(screen: UiScreen?): Font =
        screen?.font ?: Minecraft.getInstance().font

    open fun render(screen: UiScreen?, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    /** @return true 表示消费了这次点击 */
    open fun mouseClicked(screen: UiScreen?, button: Int, mx: Double, my: Double): Boolean = false
}
