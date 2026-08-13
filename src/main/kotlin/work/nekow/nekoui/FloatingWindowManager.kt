package work.nekow.nekoui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ContainerScreenEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import work.nekow.nekoui.NekoUi
import work.nekow.primalspells.item.WandItem
import work.nekow.nekoui.pouch.PouchWindowManager
import work.nekow.nekoui.wand.WandInfoWindow

/**
 * 悬浮窗口管理器：跟随任意游戏内 UI（背包/容器/编辑台等）显示，位于其他 UI 之上。
 *
 * 行为：
 * - 打开游戏内 UI 时，浮窗以最小化形态（窄顶栏）显示（首次显示时定位到屏幕右侧，
 *   之后保留用户拖动的位置，开关 UI 不重置）
 * - 用户关闭（×）后，悬停物品栏中的法杖会重新显示浮窗（最小化形态）
 * - 悬停法杖**不会**自动解除最小化；恢复大小只能通过点击 — 按钮
 * - 最小化窄顶栏可拖动到屏幕任意位置（含最左侧）
 * - 窗口尺寸可通过拖动边框（左/右/下边与下角）缩放
 *
 * 交互（拖动 / 缩放 / 最小化 / 关闭 / 槽位拖动交换/放入）通过 Screen 输入事件前置拦截实现，
 * 窗口区域内的点击一律消费，不穿透到下层 UI。
 */
object FloatingWindowManager {

    private var info: WandInfoWindow? = null

    /** 用户点击 × 后标记，悬停法杖时解除 */
    private var userClosed = false

    /** 窗口是否已完成首次定位（true 后开关 UI 不再重置位置） */
    private var positioned = false

    /** 当前显示的悬浮窗口（无 UI 打开时为 null） */
    fun current(): FloatingWindow? = info?.window?.takeIf { it.visible }

    /** 最近一次渲染的窗口矩形 [left, top, right, bottom]（屏幕坐标），供 mixin 屏蔽下方 UI 悬停 */
    @Volatile
    private var lastBounds: IntArray? = null

    /** 鼠标是否位于悬浮窗口矩形内 */
    @JvmStatic
    fun isPointInsideCurrentWindow(mx: Int, my: Int): Boolean {
        val b = lastBounds ?: return false
        return mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]
    }

    /**
     * 容器屏幕（背包/箱子等）在 Foreground 事件渲染：位于槽位之上、拖拽物品与 tooltip 之下。
     * Foreground 事件在 extractContents 内触发，pose 已平移到菜单原点，需先重置回屏幕坐标。
     */
    @SubscribeEvent
    fun onRenderContainerForeground(event: ContainerScreenEvent.Render.Foreground) {
        val screen = event.containerScreen
        if (!screen.isInGameUi()) return
        val graphics = event.guiGraphics
        graphics.pose().pushMatrix()
        graphics.pose().translate(-screen.getLeftPos().toFloat(), -screen.getTopPos().toFloat())
        renderWindow(graphics, event.mouseX, event.mouseY, screen)
        graphics.pose().popMatrix()
    }

    /** 非容器屏幕（编辑台等无拖拽物品）在 Post 事件渲染（容器屏幕已由 Foreground 处理，避免重复） */
    @SubscribeEvent
    fun onRenderScreenPost(event: ScreenEvent.Render.Post) {
        if (event.screen is AbstractContainerScreen<*>) return
        if (!event.screen.isInGameUi()) return
        renderWindow(event.guiGraphics, event.mouseX.toInt(), event.mouseY.toInt(), event.screen)
    }

    private fun renderWindow(graphics: GuiGraphicsExtractor, mx: Int, my: Int, screen: Screen) {
        val m = Minecraft.getInstance()
        val info = ensureWindow() ?: return
        val win = info.window
        val screenW = m.window.guiScaledWidth
        val screenH = m.window.guiScaledHeight

        // 用户已关闭：悬停物品栏法杖时重新显示（最小化形态，不自动展开）
        if (userClosed) {
            if (isHoveringWandInInventory(screen, mx, my)) {
                userClosed = false
                win.visible = true
                win.minimized = true
            } else {
                // 修复：窗口隐藏期间清空边界，避免残留矩形继续截获鼠标/屏蔽悬停
                lastBounds = null
                return
            }
        }

        if (!win.visible) {
            win.visible = true
            win.minimized = true
            if (!positioned) {
                info.placeAtRight(screenW, screenH)
                positioned = true
            }
        }

        // 约束在屏幕内（GUI 尺寸变化后窗口仍可达）
        win.clampToScreen(screenW, screenH)

        info.refresh(m.player, screen)
        // 记录当前窗口矩形（供下方 UI 悬停屏蔽使用）
        lastBounds = win.windowRect()
        win.render(graphics, mx, my, screenW, screenH)
    }

    @SubscribeEvent
    fun onScreenClose(event: ScreenEvent.Closing) {
        info?.window?.visible = false
        lastBounds = null
    }

    /** 鼠标是否悬停在背包（物品栏）中的法杖物品上 */
    private fun isHoveringWandInInventory(screen: Screen, mx: Int, my: Int): Boolean {
        if (screen !is AbstractContainerScreen<*>) return false
        val slot = screen.hoveredSlot ?: return false
        return !slot.item.isEmpty && slot.item.item is WandItem
    }

    @SubscribeEvent
    fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        // 小包浮窗渲染在法杖浮窗之上：点击其区域时让位给小包窗处理
        if (PouchWindowManager.isPointInsideWindow(event.mouseX.toInt(), event.mouseY.toInt())) return
        val win = info?.window ?: return
        if (!win.visible) return
        if (win.mouseClicked(event.mouseX.toInt(), event.mouseY.toInt(), event.button)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onMouseDragged(event: ScreenEvent.MouseDragged.Pre) {
        val win = info?.window ?: return
        if (!win.visible) return
        val dragging = win.isDragging() || win.isResizing() || (win.slotDrag?.isDragging() == true)
        if (dragging) {
            win.mouseDragged(event.mouseX.toInt(), event.mouseY.toInt())
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onMouseReleased(event: ScreenEvent.MouseButtonReleased.Pre) {
        val info = this.info ?: return
        val win = info.window
        if (!win.visible) return
        val mx = event.mouseX.toInt()
        val my = event.mouseY.toInt()

        // 1) 浮窗内槽位拖动（拾起/交换/放入/放回/卸载到容器）
        if (win.isDragging() || win.isResizing() || win.slotDrag?.isDragging() == true) {
            // 拖到下层容器槽位 → 卸载法术
            if (info.tryRemoveToContainer(mx, my, event.screen)) {
                event.isCanceled = true
                return
            }
            win.mouseReleased(mx, my)
            event.isCanceled = true
            return
        }

        // 2) 背包拾起的物品拖入法术槽（编杖：加入法杖）
        if (info.tryAddFromCarried(mx, my, Minecraft.getInstance().player)) {
            event.isCanceled = true
        }
    }

    /** 惰性创建浮窗：启动早期注册表未绑定时返回 null，待游戏就绪后自动重试；HTML 源文件变化时整体重建（热更新） */
    private fun ensureWindow(): WandInfoWindow? {
        val existing = info
        if (existing != null) {
            if (existing.htmlModified()) {
                val old = existing
                info = null
                val rebuilt = try {
                    WandInfoWindow.create()
                } catch (e: Exception) {
                    NekoUi.LOGGER.debug("重建法杖信息悬浮窗失败（HTML 或注册表问题），保留旧窗口", e)
                    null
                } ?: return old
                rebuilt.window.onCloseRequest = { userClosed = true }
                // 保留窗口位置与显示状态
                rebuilt.window.x = old.window.x
                rebuilt.window.y = old.window.y
                rebuilt.window.visible = old.window.visible
                rebuilt.window.minimized = old.window.minimized
                rebuilt.trackedId = old.trackedId
                info = rebuilt
            }
            return info
        }
        return try {
            WandInfoWindow.create().also { w ->
                info = w
                w.window.onCloseRequest = { userClosed = true }
            }
        } catch (e: Exception) {
            NekoUi.LOGGER.debug("创建法杖信息悬浮窗失败（可能注册表未就绪），稍后重试", e)
            null
        }
    }
}
