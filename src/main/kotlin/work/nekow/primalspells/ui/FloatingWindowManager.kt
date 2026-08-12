package work.nekow.primalspells.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.ui.pouch.PouchWindowManager
import work.nekow.primalspells.ui.wand.WandInfoWindow

/**
 * 悬浮窗口管理器：跟随任意游戏内 UI（背包/容器/编辑台等）显示，位于其他 UI 之上。
 *
 * 行为：
 * - 打开游戏内 UI 时，浮窗以最小化形态（窄顶栏）显示在屏幕右侧空白处
 * - 鼠标悬停在背包（物品栏）中的法杖物品上时，浮窗自动展开
 *   （显示该法杖的蓝量、容量等基础信息）
 * - 移出窗口不会自动最小化；展开状态保持，直到点击 — 按钮或关闭 UI
 * - 点击 × 后消失，直到再次将鼠标悬停在物品栏中的法杖上才重新显示
 *
 * 交互（拖动 / 最小化 / 最大化 / 关闭 / 槽位拖动交换/放入）通过 Screen 输入事件前置拦截实现，
 * 窗口区域内的点击一律消费，不穿透到下层 UI。
 */
object FloatingWindowManager {

    private var info: WandInfoWindow? = null

    /** 用户点击 × 后标记，悬停法杖时解除 */
    private var userClosed = false

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

    @SubscribeEvent
    fun onRenderScreenPost(event: ScreenEvent.Render.Post) {
        // 仅在游戏内 UI（背包/容器/编辑台等）打开时显示
        if (!event.screen.isInGameUi()) return
        val m = Minecraft.getInstance()

        val info = ensureWindow() ?: return
        val win = info.window
        val screenW = m.window.guiScaledWidth
        val screenH = m.window.guiScaledHeight
        val mx = event.mouseX.toInt()
        val my = event.mouseY.toInt()

        // 用户已关闭：悬停物品栏法杖时重新显示
        if (userClosed) {
            if (isHoveringWandInInventory(event.screen, mx, my)) {
                userClosed = false
                win.visible = true
                win.minimized = false
            } else {
                return
            }
        }

        if (!win.visible) {
            win.visible = true
            win.minimized = true
            info.placeAtRight(screenW, screenH)
        }

        // 最小化时悬停物品栏法杖 → 展开（移出不会自动收起）
        if (win.minimized && isHoveringWandInInventory(event.screen, mx, my)) {
            win.minimized = false
        }

        info.refresh(m.player, event.screen)
        // 记录当前窗口矩形（供下方 UI 悬停屏蔽使用）
        lastBounds = win.windowRect()
        win.render(event.guiGraphics, mx, my, screenW, screenH)
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
        val dragging = win.isDragging() || (win.slotDrag?.isDragging() == true)
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
        if (win.isDragging() || win.slotDrag?.isDragging() == true) {
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
                    PrimalSpells.LOGGER.debug("重建法杖信息悬浮窗失败（HTML 或注册表问题），保留旧窗口", e)
                    null
                } ?: return old
                rebuilt.window.onCloseRequest = { userClosed = true }
                // 保留窗口位置与显示状态
                rebuilt.window.x = old.window.x
                rebuilt.window.y = old.window.y
                rebuilt.window.visible = old.window.visible
                rebuilt.window.minimized = old.window.minimized
                rebuilt.trackedWandId = old.trackedWandId
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
            PrimalSpells.LOGGER.debug("创建法杖信息悬浮窗失败（可能注册表未就绪），稍后重试", e)
            null
        }
    }
}
