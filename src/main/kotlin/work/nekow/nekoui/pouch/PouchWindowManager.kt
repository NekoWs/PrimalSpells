package work.nekow.nekoui.pouch

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import work.nekow.nekoui.NekoUi
import work.nekow.primalspells.item.SpellPouchItem

/**
 * 法术小包悬浮窗管理器：
 * - 仅在已有游戏内 UI 时，右键容器槽位中的小包打开悬浮窗（替代原版拾取）
 * - 手持小包右键由服务端打开容器 UI（见 PouchEventHandler），不弹悬浮窗
 * - 渲染：有 Screen 时在其之上渲染；无 Screen（游戏世界）时在 HUD 层渲染
 */
object PouchWindowManager {

    private var pouch: SpellPouchWindow? = null

    /** 当前打开的小包浮窗 */
    fun current(): SpellPouchWindow? = pouch?.takeIf { it.window.visible }

    /** 鼠标是否位于当前可见的小包浮窗矩形内（供其他浮窗管理器让位输入） */
    @JvmStatic
    fun isPointInsideWindow(mx: Int, my: Int): Boolean {
        val w = pouch?.window ?: return false
        return w.visible && w.isInsideWindow(mx, my)
    }

    /** 打开小包悬浮窗（记录小包唯一 id，菜单切换不影响定位） */
    private fun openPouch(stack: net.minecraft.world.item.ItemStack, player: Player?) {
        val win = ensureWindow() ?: return
        win.pouchId = SpellPouchItem.getPouchId(stack)
        win.locatePouch(player)
        win.refresh(player, null)
        win.window.visible = true
        win.window.minimized = false
        val m = Minecraft.getInstance()
        win.placeAtRight(m.window.guiScaledWidth, m.window.guiScaledHeight)
    }

    // ---------- 渲染 ----------

    /** 无 Screen（游戏世界）时在 HUD 层渲染 */
    @SubscribeEvent
    fun onRenderGuiLayer(event: RenderGuiLayerEvent.Post) {
        if (event.name != VanillaGuiLayers.CROSSHAIR) return
        if (Minecraft.getInstance().gui.screen() != null) return
        renderWindow(event.guiGraphics)
    }

    /** 有游戏内 Screen 时在其之上渲染 */
    @SubscribeEvent
    fun onRenderScreenPost(event: ScreenEvent.Render.Post) {
        if (!event.screen.isInGameUi()) return
        renderWindow(event.guiGraphics, event.mouseX.toInt(), event.mouseY.toInt())
    }

    private fun renderWindow(graphics: net.minecraft.client.gui.GuiGraphicsExtractor, mx: Int = 0, my: Int = 0) {
        val win = pouch?.window ?: return
        if (!win.visible) return
        val m = Minecraft.getInstance()
        // 鼠标悬停下层容器槽位（物品）时让位隐藏，避免遮挡查看/操作（窗口内悬停不受影响）
        val screen = m.gui.screen()
        if (screen is AbstractContainerScreen<*> && !win.isInsideWindow(mx, my) && screen.hoveredSlot != null) {
            return
        }
        val mouseX = if (m.gui.screen() != null) mx
        else m.mouseHandler.getScaledXPos(m.window).toInt()
        val mouseY = if (m.gui.screen() != null) my
        else m.mouseHandler.getScaledYPos(m.window).toInt()
        pouch?.refresh(m.player, m.gui.screen())
        win.render(graphics, mouseX, mouseY, m.window.guiScaledWidth, m.window.guiScaledHeight)
    }

    // ---------- 输入（有 Screen 时） ----------

    @SubscribeEvent
    fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        val win = pouch?.window
        val mx = event.mouseX.toInt()
        val my = event.mouseY.toInt()

        // 1) 浮窗自身区域点击（左键交互 / 右键消费）
        if (win != null && win.visible && win.mouseClicked(mx, my, event.button)) {
            event.isCanceled = true
            return
        }

        // 2) 右键点击容器槽位中的小包 → 打开悬浮窗（替代原版拾取/拆分）
        if (event.button == 1 && event.screen is AbstractContainerScreen<*>) {
            val screen = event.screen as AbstractContainerScreen<*>
            val hovered = screen.hoveredSlot
            if (hovered != null && hovered.isActive &&
                !hovered.item.isEmpty && hovered.item.item is SpellPouchItem) {
                openPouch(hovered.item, Minecraft.getInstance().player)
                event.isCanceled = true
            }
        }
    }

    @SubscribeEvent
    fun onMouseDragged(event: ScreenEvent.MouseDragged.Pre) {
        val win = pouch?.window ?: return
        if (!win.visible) return
        val dragging = win.isDragging() || win.slotDrag?.isDragging() == true
        if (dragging) {
            win.mouseDragged(event.mouseX.toInt(), event.mouseY.toInt())
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onMouseReleased(event: ScreenEvent.MouseButtonReleased.Pre) {
        val p = pouch ?: return
        val win = p.window
        if (!win.visible) return
        val mx = event.mouseX.toInt()
        val my = event.mouseY.toInt()

        if (win.isDragging() || win.slotDrag?.isDragging() == true) {
            // 拖出窗口 → 卸载
            if (p.tryRemoveToContainer(mx, my, event.screen)) {
                event.isCanceled = true
                return
            }
            win.mouseReleased(mx, my)
            event.isCanceled = true
            return
        }

        // 背包拾起物品拖入空槽
        if (p.tryAddFromCarried(mx, my, Minecraft.getInstance().player)) {
            event.isCanceled = true
        }
    }

    // ---------- 生命周期 ----------

    /** 关闭小包浮窗（如点击 × 时调用） */
    fun close() {
        pouch?.window?.visible = false
    }

    private fun ensureWindow(): SpellPouchWindow? {
        val existing = pouch
        if (existing != null) {
            if (existing.htmlModified()) {
                val old = existing
                pouch = null
                val rebuilt = try {
                    SpellPouchWindow.create()
                } catch (e: Exception) {
                    NekoUi.LOGGER.debug("重建法术小包浮窗失败（HTML 或注册表问题），保留旧窗口", e)
                    null
                } ?: return old
                rebuilt.window.onCloseRequest = { close() }
                // 保留窗口位置、显示状态与小包定位信息
                rebuilt.window.x = old.window.x
                rebuilt.window.y = old.window.y
                rebuilt.window.visible = old.window.visible
                rebuilt.window.minimized = old.window.minimized
                rebuilt.pouchId = old.pouchId
                rebuilt.pouchStack = old.pouchStack
                pouch = rebuilt
            }
            return pouch
        }
        return try {
            SpellPouchWindow.create().also { w ->
                pouch = w
                w.window.onCloseRequest = { close() }
            }
        } catch (e: Exception) {
            NekoUi.LOGGER.debug("创建法术小包浮窗失败，稍后重试", e)
            null
        }
    }
}

