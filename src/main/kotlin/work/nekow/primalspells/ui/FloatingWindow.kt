package work.nekow.primalspells.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import work.nekow.primalspells.ui.element.UiElement
import work.nekow.primalspells.ui.element.UiSlot
import work.nekow.primalspells.ui.slot.SlotDragController

/**
 * 悬浮窗口：独立于任何 [UiScreen] 存在，可在任意画面（HUD 层）渲染的窗口。
 * 支持拖动标题栏、最小化 / 最大化 / 关闭（关闭 = 隐藏）。
 *
 * 最小化时渲染为"标题栏 + 紧凑内容"（[collapsedContent]）。
 * 窗口区域内的点击一律消费（不穿透到下层 UI）。
 */
class FloatingWindow(
    val title: Component,
    var content: List<UiElement>,
    var windowWidth: Int,
    var windowHeight: Int,
) {

    var x = 0
    var y = 0
    var visible = false
    var minimized = false
    var maximized = false

    /** 槽位交互控制器（可选） */
    var slotDrag: SlotDragController? = null

    /** 用户点击关闭按钮时回调（关闭 = 隐藏） */
    var onCloseRequest: (() -> Unit)? = null

    /** 槽位拖动交换完成后回调（参数为发生交换的两个槽位） */
    var onSlotSwapped: ((UiSlot, UiSlot) -> Unit)? = null

    private var dragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    private var restoreX = 0
    private var restoreY = 0
    private var restoreW = 0
    private var restoreH = 0
    private var screenW = 0
    private var screenH = 0

    /** 最小化形态占用的内容高度 */
    private val collapsedHeight: Int get() = 0

    /** 当前窗口宽度（最小化时为窄顶栏宽，右侧对齐） */
    fun currentWidth(): Int = if (minimized) MINIMIZED_WIDTH else windowWidth

    /** 当前窗口总高度（最小化时仅标题栏） */
    fun currentHeight(): Int =
        if (minimized) TITLE_BAR_HEIGHT else TITLE_BAR_HEIGHT + windowHeight

    /** 初始居中 */
    fun center(screenW: Int, screenH: Int) {
        x = ((screenW - windowWidth) / 2).coerceAtLeast(0)
        y = ((screenH - windowHeight - TITLE_BAR_HEIGHT) / 2).coerceAtLeast(0)
    }

    fun isDragging() = dragging

    /** 判断鼠标是否位于窗口（含最小化窄顶栏）区域内 */
    fun isInsideWindow(mx: Int, my: Int): Boolean {
        val r = windowRect()
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]
    }

    /** 窗口实际矩形 [left, top, right, bottom]（屏幕坐标），渲染与判定共用 */
    fun windowRect(): IntArray {
        val left = x + windowWidth - currentWidth()
        return intArrayOf(left, y, x + windowWidth, y + currentHeight())
    }

    private fun toggleMaximize() {
        if (maximized) {
            x = restoreX; y = restoreY
            windowWidth = restoreW
            windowHeight = restoreH
            maximized = false
        } else {
            restoreX = x; restoreY = y
            restoreW = windowWidth; restoreH = windowHeight
            x = 0; y = 0
            windowWidth = screenW
            windowHeight = screenH - TITLE_BAR_HEIGHT
            maximized = true
        }
    }

    // ---------- 渲染 ----------
    fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
        this.screenW = screenW
        this.screenH = screenH
        val font = Minecraft.getInstance().font
        val ctrl = slotDrag
        ctrl?.tick()

        // 最小化：仅渲染窄顶栏（右侧对齐），无内容区
        val width = currentWidth()
        val left = x + windowWidth - width
        graphics.fill(left, y, left + width, y + TITLE_BAR_HEIGHT, 0xF0_2A_2A_32.toInt())
        graphics.fill(left, y, left + width, y + 1, 0xFF_5A_5A_66.toInt())

        if (!minimized) {
            graphics.text(font, title, x + 6, y + 4, 0xFF_FF_FF_FF.toInt())
        }

        windowButtons().forEach { btn ->
            val hovered = btn.contains(mouseX, mouseY)
            graphics.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, if (hovered) 0xFF_4A_4A_55.toInt() else 0x00_00_00_00.toInt())
            graphics.centeredText(font, btn.label, btn.x + btn.w / 2, btn.y + 3, if (hovered) 0xFF_FF_FF_FF.toInt() else 0xFF_B0_B0_B8.toInt())
        }

        if (minimized) return

        graphics.fill(x, y + TITLE_BAR_HEIGHT, x + windowWidth, y + TITLE_BAR_HEIGHT + 1, 0xFF_55_55_60.toInt())
        graphics.fill(x, y + TITLE_BAR_HEIGHT, x + 1, y + TITLE_BAR_HEIGHT + windowHeight, 0xFF_55_55_60.toInt())
        graphics.fill(x + windowWidth - 1, y + TITLE_BAR_HEIGHT, x + windowWidth, y + TITLE_BAR_HEIGHT + windowHeight, 0xFF_55_55_60.toInt())
        graphics.fill(x, y + TITLE_BAR_HEIGHT + windowHeight - 1, x + windowWidth, y + TITLE_BAR_HEIGHT + windowHeight, 0xFF_55_55_60.toInt())

        renderContent(graphics, font, content, mouseX, mouseY, ctrl)

        // 悬停槽位时显示物品信息（tooltip 渲染在一切之上）
        val hoveredSlot = content.filterIsInstance<UiSlot>().firstOrNull {
            it.visible && it.contains((mouseX - x).toDouble(), (mouseY - y - TITLE_BAR_HEIGHT).toDouble())
        }
        if (hoveredSlot != null && !hoveredSlot.stack.isEmpty) {
            graphics.setTooltipForNextFrame(font, hoveredSlot.stack, mouseX, mouseY)
        }
    }

    private fun renderContent(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        list: List<UiElement>,
        mouseX: Int,
        mouseY: Int,
        ctrl: SlotDragController?
    ) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), (y + TITLE_BAR_HEIGHT).toFloat())
        val rx = mouseX - x
        val ry = mouseY - y - TITLE_BAR_HEIGHT
        list.forEach { el ->
            if (el is UiSlot && ctrl != null && ctrl.hasAnimationFor(el)) {
                // 动画期间：先画槽位背景，再画飞行中的物品（槽位数据仍以服务端同步为准）
                el.drawBackground(graphics, rx, ry)
                ctrl.renderAnimatedSlot(graphics, font, el)
            } else {
                el.render(null, graphics, rx, ry, 0f)
            }
        }
        ctrl?.renderCarried(graphics, font)
        graphics.pose().popMatrix()
    }

    private class WinButton(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private fun windowButtons(): List<WinButton> {
        val s = WINDOW_BUTTON_SIZE
        val top = y + 1
        val right = x + windowWidth - 2
        return listOf(
            WinButton(right - s * 3 - 2, top, s, s - 2, "—", { minimized = true }),
            WinButton(right - s * 2 - 1, top, s, s - 2, if (maximized) "❒" else "□", { toggleMaximize() }),
            WinButton(right - s, top, s, s - 2, "×", { visible = false; onCloseRequest?.invoke() }),
        )
    }
    // ---------- 输入 ----------
    /** @return true 表示消费了点击（阻止穿透到下层 UI） */
    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible) return false
        if (!isInsideWindow(mx, my)) return false

        windowButtons().firstOrNull { it.contains(mx, my) }?.let {
            it.action()
            return true
        }

        // 标题栏：始终只响应拖动（最小化状态也允许拖动，但不取消最小化；
        // 取消最小化仅由悬停法杖图标触发，见 FloatingWindowManager）
        if (my < y + TITLE_BAR_HEIGHT) {
            dragging = true
            dragOffsetX = mx - x
            dragOffsetY = my - y
            return true
        }

        // 内容区：相对窗口坐标分发（最小化时无内容区）
        if (minimized) return true
        val rx = mx - x
        val ry = my - y - TITLE_BAR_HEIGHT
        val ctrl = slotDrag
        if (ctrl != null) {
            val slot = content.filterIsInstance<UiSlot>().firstOrNull { it.contains(rx.toDouble(), ry.toDouble()) }
            if (ctrl.pressed(slot, button)) return true
        }
        for (el in content.asReversed()) {
            if (el.visible && el.mouseClicked(null, button, rx.toDouble(), ry.toDouble())) return true
        }
        return true // 窗口区域内一律消费，不穿透
    }

    fun mouseDragged(mx: Int, my: Int) {
        if (dragging) {
            x = (mx - dragOffsetX).coerceIn(0, maxOf(0, screenW - windowWidth))
            y = (my - dragOffsetY).coerceIn(0, maxOf(0, screenH - currentHeight()))
            return
        }
        slotDrag?.let { ctrl ->
            if (ctrl.isDragging()) {
                ctrl.dragged(mx - x, my - y - TITLE_BAR_HEIGHT)
            }
        }
    }

    fun mouseReleased(mx: Int, my: Int) {
        if (dragging) {
            dragging = false
            return
        }
        val ctrl = slotDrag ?: return
        ctrl.released(
            content.filterIsInstance<UiSlot>(),
            mx - x, my - y - TITLE_BAR_HEIGHT
        )
        val swap = ctrl.lastSwap
        if (swap != null) {
            ctrl.lastSwap = null
            onSlotSwapped?.invoke(swap.first, swap.second)
        }
    }

    companion object {
        const val TITLE_BAR_HEIGHT = 16
        const val WINDOW_BUTTON_SIZE = 14

        /** 最小化时窄顶栏宽度（约 4 个顶栏按钮宽） */
        const val MINIMIZED_WIDTH = 56
    }
}
