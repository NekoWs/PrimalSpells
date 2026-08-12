package work.nekow.primalspells.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import work.nekow.primalspells.ui.element.UiElement

/**
 * 通用 UI 屏幕：托管一组 [UiElement]，负责渲染与输入分发。
 *
 * 窗口模式（[setWindow]）：以可拖动窗口形式显示，像 Windows 窗口一样：
 * - 按住标题栏拖动窗口到屏幕任意位置
 * - 右上角最小化 / 最大化 / 关闭 三个按钮
 * - 点击窗口外部区域直接关闭
 */
open class UiScreen(
    title: Component,
    elements: List<UiElement> = emptyList(),
) : Screen(title) {

    protected val elements = elements.toMutableList()
    private var rebuild: (UiScreen.() -> Unit)? = null

    override fun isPauseScreen() = false

    /** 游戏内 UI：背景不模糊、不遮暗，HUD（快捷栏等）保持显示 */
    override fun isInGameUi() = true

    // ---------- 窗口状态 ----------
    private var windowed = false
    private var windowX = 0
    private var windowY = 0
    private var windowWidth = 0
    private var windowHeight = 0
    private var minimized = false
    private var maximized = false
    private var restoreX = 0
    private var restoreY = 0
    private var restoreW = 0
    private var restoreH = 0
    private var dragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    /** 启用窗口模式并设置内容区尺寸（首次调用自动居中） */
    fun setWindow(width: Int, height: Int): UiScreen {
        if (!windowed) {
            windowWidth = width
            windowHeight = height
            centerWindow()
        } else {
            windowWidth = width
            windowHeight = height
        }
        windowed = true
        return this
    }

    fun isWindowed() = windowed

    private fun centerWindow() {
        windowX = ((width - windowWidth) / 2).coerceAtLeast(0)
        windowY = ((height - windowHeight - TITLE_BAR_HEIGHT) / 2).coerceAtLeast(0)
    }

    private fun isInsideWindow(mx: Int, my: Int): Boolean {
        val h = if (minimized) TITLE_BAR_HEIGHT else TITLE_BAR_HEIGHT + windowHeight
        return mx >= windowX && mx < windowX + windowWidth && my >= windowY && my < windowY + h
    }

    private fun toggleMaximize() {
        if (maximized) {
            windowX = restoreX; windowY = restoreY
            windowWidth = restoreW; windowHeight = restoreH
            maximized = false
        } else {
            restoreX = windowX; restoreY = windowY
            restoreW = windowWidth; restoreH = windowHeight
            windowX = 0; windowY = 0
            windowWidth = width; windowHeight = height - TITLE_BAR_HEIGHT
            maximized = true
        }
    }

    // ---------- 元件管理 ----------
    fun add(element: UiElement): UiScreen {
        elements += element
        return this
    }

    fun build(init: UiBuilder.() -> Unit): UiScreen {
        val builder = UiBuilder()
        builder.init()
        elements += builder.elements
        return this
    }

    /** 注册重建函数：下次 [reload] 时清空并重新生成全部元件 */
    fun setRebuild(init: UiScreen.() -> Unit): UiScreen {
        rebuild = init
        return this
    }

    /** 清空并重建全部元件（用于资源重载后的热更新） */
    fun reload() {
        val fn = rebuild ?: return
        elements.clear()
        fn(this)
    }

    fun close() = onClose()

    // ---------- 渲染 ----------
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        if (windowed) {
            renderWindow(graphics, mouseX, mouseY, partialTick)
        } else {
            graphics.fill(0, 0, width, height, 0xC0_0A_0A_0E.toInt())
            // 鼠标位于悬浮窗矩形内时屏蔽本界面元素的 hover（哨兵坐标，仅影响高亮判断）
            val hx = if (FloatingWindowManager.isPointInsideCurrentWindow(mouseX, mouseY)) -100000 else mouseX
            val hy = if (FloatingWindowManager.isPointInsideCurrentWindow(mouseX, mouseY)) -100000 else mouseY
            elements.forEach { it.render(this, graphics, hx, hy, partialTick) }
        }
    }

    private fun renderWindow(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 标题栏
        graphics.fill(windowX, windowY, windowX + windowWidth, windowY + TITLE_BAR_HEIGHT, 0xF0_2A_2A_32.toInt())
        graphics.fill(windowX, windowY, windowX + windowWidth, windowY + 1, 0xFF_5A_5A_66.toInt())
        if (!minimized) {
            graphics.fill(windowX, windowY + TITLE_BAR_HEIGHT, windowX + windowWidth, windowY + TITLE_BAR_HEIGHT + 1, 0xFF_55_55_60.toInt())
        }
        graphics.text(font, title, windowX + 6, windowY + 4, 0xFF_FF_FF_FF.toInt())

        // 右上角按钮
        windowButtons().forEach { btn ->
            val hovered = btn.contains(mouseX, mouseY)
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, if (hovered) 0xFF_4A_4A_55.toInt() else 0x00_00_00_00.toInt())
            graphics.centeredText(font, btn.label, btn.x + btn.w / 2, btn.y + 3, if (hovered) 0xFF_FF_FF_FF.toInt() else 0xFF_B0_B0_B8.toInt())
        }

        if (minimized) return

        // 窗口边框
        graphics.fill(windowX, windowY + TITLE_BAR_HEIGHT, windowX + windowWidth, windowY + TITLE_BAR_HEIGHT + 1, 0xFF_55_55_60.toInt())
        graphics.fill(windowX, windowY + TITLE_BAR_HEIGHT, windowX + 1, windowY + TITLE_BAR_HEIGHT + windowHeight, 0xFF_55_55_60.toInt())
        graphics.fill(windowX + windowWidth - 1, windowY + TITLE_BAR_HEIGHT, windowX + windowWidth, windowY + TITLE_BAR_HEIGHT + windowHeight, 0xFF_55_55_60.toInt())
        graphics.fill(windowX, windowY + TITLE_BAR_HEIGHT + windowHeight - 1, windowX + windowWidth, windowY + TITLE_BAR_HEIGHT + windowHeight, 0xFF_55_55_60.toInt())

        // 内容区（相对窗口坐标；鼠标位于悬浮窗矩形内时屏蔽本窗口元素的 hover）
        graphics.pose().pushMatrix()
        graphics.pose().translate(windowX.toFloat(), (windowY + TITLE_BAR_HEIGHT).toFloat())
        val insideFloat = FloatingWindowManager.isPointInsideCurrentWindow(mouseX, mouseY)
        val hx = if (insideFloat) -100000 else mouseX - windowX
        val hy = if (insideFloat) -100000 else mouseY - windowY - TITLE_BAR_HEIGHT
        elements.forEach {
            it.render(this, graphics, hx, hy, partialTick)
        }
        graphics.pose().popMatrix()
    }

    private class WinButton(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private fun windowButtons(): List<WinButton> {
        val s = WINDOW_BUTTON_SIZE
        val top = windowY + 1
        val right = windowX + windowWidth - 2
        return listOf(
            WinButton(right - s * 3 - 2, top, s, s - 2, "—", { minimized = true }),
            WinButton(right - s * 2 - 1, top, s, s - 2, if (maximized) "❒" else "□", { toggleMaximize() }),
            WinButton(right - s, top, s, s - 2, "×", { close() }),
        )
    }

    // ---------- 输入 ----------
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true

        if (!windowed) {
            for (el in elements.asReversed()) {
                if (el.visible && el.mouseClicked(this, event.button(), event.x(), event.y())) return true
            }
            return false
        }

        val mx = event.x().toInt()
        val my = event.y().toInt()

        // 点击窗口外 → 关闭
        if (!isInsideWindow(mx, my)) {
            onClose()
            return true
        }

        // 窗口按钮
        windowButtons().firstOrNull { it.contains(mx, my) }?.let {
            it.action()
            return true
        }

        // 标题栏：最小化时点击恢复，否则开始拖动
        if (my < windowY + TITLE_BAR_HEIGHT) {
            if (minimized) minimized = false
            else {
                dragging = true
                dragOffsetX = mx - windowX
                dragOffsetY = my - windowY
            }
            return true
        }

        // 内容区：分发（相对窗口坐标）
        val rx = mx - windowX
        val ry = my - windowY - TITLE_BAR_HEIGHT
        for (el in elements.asReversed()) {
            if (el.visible && el.mouseClicked(this, event.button(), rx.toDouble(), ry.toDouble())) return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging) {
            val mx = event.x().toInt()
            val my = event.y().toInt()
            windowX = (mx - dragOffsetX).coerceIn(0, maxOf(0, width - windowWidth))
            windowY = (my - dragOffsetY).coerceIn(0, maxOf(0, height - windowHeight - TITLE_BAR_HEIGHT))
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    companion object {
        const val TITLE_BAR_HEIGHT = 16
        const val WINDOW_BUTTON_SIZE = 14

        /** 直接打开一个使用构建器定义的 UI */
        fun open(title: Component, init: UiBuilder.() -> Unit) {
            val builder = UiBuilder()
            builder.init()
            Minecraft.getInstance().gui.setScreen(UiScreen(title, builder.elements))
        }

        /**
         * 打开一个由 HTML 描述的窗口化 UI（可拖动、可最小化/最大化/关闭）。
         * @param blockId 方块 id（对应资源文件 `ui_<blockId>_<uiNumber>.html`）
         * @param uiNumber UI 编号
         */
        fun openHtml(title: Component, blockId: String, uiNumber: Int, clicks: Map<String, UiScreen.() -> Unit> = emptyMap()) {
            val screen = UiScreen(title)
            screen.setRebuild {
                elements.clear()
                val ui = HtmlUiLoader.loadWindowed(blockId, uiNumber, clicks)
                setWindow(ui.windowWidth, ui.windowHeight)
                elements += ui.elements
            }
            screen.reload()
            Minecraft.getInstance().gui.setScreen(screen)
        }
    }
}
