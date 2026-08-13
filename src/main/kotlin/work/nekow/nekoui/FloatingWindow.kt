package work.nekow.nekoui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import work.nekow.nekoui.element.UiElement
import work.nekow.nekoui.element.UiSlot
import work.nekow.nekoui.element.UiWindow
import work.nekow.nekoui.slot.SlotDragController
import work.nekow.nekoui.slot.SlotProvider

/**
 * 悬浮窗口：独立于任何 [UiScreen] 存在，可在任意画面（HUD 层）渲染的窗口。
 * 支持拖动标题栏、最小化（点击 — 恢复）、边框缩放（Windows 风格）与关闭（关闭 = 隐藏）。
 *
 * 最小化时仅渲染窄顶栏；最小化状态下仍可拖动，且可拖到屏幕最左侧。
 * 窗口区域内的点击一律消费（不穿透到下层 UI）。
 *
 * 缩放：鼠标位于窗口下/左/右边缘（含下角）4px 内时按住拖动即可缩放；
 * 顶边不参与缩放（由标题栏拖动占用）。缩放后 [userResized] 置位，
 * 供动态高度窗口（法杖窗）区分"自动布局高度"与"用户手动尺寸"。
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

    /** 用户是否手动缩放过尺寸（动态布局窗口据此避免自动高度覆盖用户尺寸） */
    var userResized = false

    /** 槽位交互控制器（可选） */
    var slotDrag: SlotDragController? = null

    /** 用户点击关闭按钮时回调（关闭 = 隐藏） */
    var onCloseRequest: (() -> Unit)? = null

    /** 槽位拖动交换完成后回调（参数为发生交换的两个槽位） */
    var onSlotSwapped: ((UiSlot, UiSlot) -> Unit)? = null

    private var dragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    private var resizing = ResizeEdge.NONE
    private var resizeStartX = 0
    private var resizeStartY = 0
    private var resizeStartW = 0
    private var resizeStartH = 0
    private var screenW = 0
    private var screenH = 0

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

    /** 是否正在通过边框缩放尺寸 */
    fun isResizing() = resizing != ResizeEdge.NONE

    /**
     * 将窗口完整地约束在屏幕内。
     * 注意：最小化时窗口可位于"全宽窗口"左边缘之外（窄顶栏可达屏幕最左），
     * 因此最小化状态下左边界下限为负值；恢复大小后由调用方重新钳制。
     */
    fun clampToScreen(screenW: Int, screenH: Int) {
        val w = currentWidth()
        val h = currentHeight()
        val loX = if (minimized) w - windowWidth else 0
        val hiX = maxOf(loX, screenW - w)
        val hiY = maxOf(0, screenH - h)
        x = x.coerceIn(loX, hiX)
        y = y.coerceIn(0, hiY)
    }

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

    /** 背景面板（UiWindow）拉伸至当前窗口尺寸（缩放后的动态布局适配） */
    fun stretchBackgroundPanels() {
        content.filterIsInstance<UiWindow>().forEach {
            it.width = windowWidth
            it.height = windowHeight
        }
    }

    // ---------- 渲染 ----------
    fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
        this.screenW = screenW
        this.screenH = screenH
        val font = Minecraft.getInstance().font
        val ctrl = slotDrag
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

        renderContent(graphics, content, mouseX, mouseY, ctrl)

        // 悬停槽位时显示物品信息（tooltip 渲染在一切之上）
        val hoveredSlot = allSlots().firstOrNull {
            it.visible && it.contains((mouseX - x).toDouble(), (mouseY - y - TITLE_BAR_HEIGHT).toDouble())
        }
        if (hoveredSlot != null && !hoveredSlot.stack.isEmpty) {
            graphics.setTooltipForNextFrame(font, hoveredSlot.stack, mouseX, mouseY)
        }
    }

    /** 收集内容区所有可交互槽位（含独立 UiSlot 与槽位网格） */
    private fun allSlots(): List<UiSlot> =
        content.flatMap { el ->
            when (el) {
                is UiSlot -> listOf(el)
                is SlotProvider -> el.slots()
                else -> emptyList()
            }
        }

    private fun renderContent(
        graphics: GuiGraphicsExtractor,
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
            el.render(null, graphics, rx, ry, 0f)
        }
        ctrl?.renderCarried(graphics)
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
            WinButton(right - s * 2 - 1, top, s, s - 2, "—", {
                minimized = !minimized
                if (!minimized) clampToScreen(screenW, screenH)
            }),
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

        // 边框缩放（仅非最小化；顶边由标题栏拖动占用）
        if (!minimized && button == 0) {
            val edge = resizeEdgeAt(mx, my)
            if (edge != ResizeEdge.NONE) {
                resizing = edge
                resizeStartX = mx
                resizeStartY = my
                resizeStartW = windowWidth
                resizeStartH = windowHeight
                return true
            }
        }

        // 标题栏：始终只响应拖动（最小化状态也允许拖动，但不取消最小化；
        // 取消最小化仅由点击 — 按钮触发）
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
            val slot = allSlots().firstOrNull { it.contains(rx.toDouble(), ry.toDouble()) }
            if (ctrl.pressed(slot, button)) return true
        }
        for (el in content.asReversed()) {
            if (el.visible && el.mouseClicked(null, button, rx.toDouble(), ry.toDouble())) return true
        }
        return true // 窗口区域内一律消费，不穿透
    }

    fun mouseDragged(mx: Int, my: Int) {
        if (dragging) {
            // 最小化时窄顶栏可拖到屏幕最左侧（此时全宽窗口左边缘可为负）
            val loX = if (minimized) currentWidth() - windowWidth else 0
            x = (mx - dragOffsetX).coerceIn(loX, maxOf(loX, screenW - windowWidth))
            y = (my - dragOffsetY).coerceIn(0, maxOf(0, screenH - currentHeight()))
            return
        }
        if (resizing != ResizeEdge.NONE) {
            val dx = mx - resizeStartX
            val dy = my - resizeStartY
            val minW = MIN_WINDOW_WIDTH
            val minH = MIN_WINDOW_HEIGHT
            var newW = windowWidth
            var newH = windowHeight
            var newX = x
            when (resizing) {
                ResizeEdge.E -> newW = (resizeStartW + dx)
                    .coerceIn(minW, maxOf(minW, screenW - x))
                ResizeEdge.S -> newH = (resizeStartH + dy)
                    .coerceIn(minH, maxOf(minH, screenH - y - TITLE_BAR_HEIGHT))
                ResizeEdge.W -> {
                    newW = (resizeStartW - dx).coerceIn(minW, maxOf(minW, x + windowWidth))
                    newX = x + (windowWidth - newW)
                }
                ResizeEdge.SE -> {
                    newW = (resizeStartW + dx).coerceIn(minW, maxOf(minW, screenW - x))
                    newH = (resizeStartH + dy).coerceIn(minH, maxOf(minH, screenH - y - TITLE_BAR_HEIGHT))
                }
                ResizeEdge.SW -> {
                    newW = (resizeStartW - dx).coerceIn(minW, maxOf(minW, x + windowWidth))
                    newX = x + (windowWidth - newW)
                    newH = (resizeStartH + dy).coerceIn(minH, maxOf(minH, screenH - y - TITLE_BAR_HEIGHT))
                }
                else -> {}
            }
            if (newW != windowWidth || newH != windowHeight || newX != x) {
                windowWidth = newW
                windowHeight = newH
                x = newX
                userResized = true
                stretchBackgroundPanels()
            }
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
        if (resizing != ResizeEdge.NONE) {
            resizing = ResizeEdge.NONE
            return
        }
        val ctrl = slotDrag ?: return
        ctrl.released(
            allSlots(),
            mx - x, my - y - TITLE_BAR_HEIGHT
        )
        val swap = ctrl.lastSwap
        if (swap != null) {
            ctrl.lastSwap = null
            onSlotSwapped?.invoke(swap.first, swap.second)
        }
    }

    /** 鼠标所在位置的缩放边（不在边缘/最小化/顶边 → NONE） */
    private fun resizeEdgeAt(mx: Int, my: Int): ResizeEdge {
        val r = windowRect()
        val left = r[0]
        val right = r[2]
        val bottom = r[3]
        val m = RESIZE_MARGIN
        val nearLeft = mx >= left && mx < left + m
        val nearRight = mx < right && mx >= right - m
        val nearBottom = my < bottom && my >= bottom - m
        if (nearRight && nearBottom) return ResizeEdge.SE
        if (nearLeft && nearBottom) return ResizeEdge.SW
        if (nearRight) return ResizeEdge.E
        if (nearLeft) return ResizeEdge.W
        if (nearBottom) return ResizeEdge.S
        return ResizeEdge.NONE
    }

    private enum class ResizeEdge { NONE, E, S, W, SE, SW }

    companion object {
        const val TITLE_BAR_HEIGHT = 16
        const val WINDOW_BUTTON_SIZE = 14

        /** 最小化时窄顶栏宽度（约 2 个顶栏按钮宽 + 留白） */
        const val MINIMIZED_WIDTH = 44

        /** 边框缩放的最小窗口尺寸 */
        const val MIN_WINDOW_WIDTH = 96
        const val MIN_WINDOW_HEIGHT = 40

        /** 边缘缩放判定区域宽度（px） */
        const val RESIZE_MARGIN = 4
    }
}
