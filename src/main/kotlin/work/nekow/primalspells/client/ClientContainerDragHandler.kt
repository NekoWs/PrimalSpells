package work.nekow.primalspells.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CraftingScreen
import net.minecraft.util.Util
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import work.nekow.primalspells.mixin.AbstractContainerScreenInvokerMixin

/**
 * 原版容器拖放交换：在背包/箱子等容器内 **按住 Ctrl + 左键** 拖动槽位物品到另一槽位，松开即交换。
 * 默认（不按 Ctrl）完全保持原版交互，互不干扰。
 *
 * 交互通过原版 slotClicked(PICKUP) 协议实现，与服务端完全同步：
 * - 按下（Ctrl 按下、容器槽位、非浮窗区域、无携带、非 Shift、非双击、非工作台）：
 *   PICKUP 拾起并取消原版按下（阻止原版 clickedSlot/skipNextRelease 内部状态干扰）
 * - 释放：
 *   * 同一槽位（单击）→ 不取消，物品保留在光标 = 原版"单击拾起"语义
 *   * 其他空槽 → 单次 PICKUP（移动）
 *   * 其他有物品槽 → 两步 PICKUP 实现真正互换（双方都留在槽位，光标清空）
 *   * 空白 → PICKUP 放回原槽
 *   * 浮窗区域 → 不接管，由悬浮窗（tryAddFromCarried）消费光标物品
 * - 工作台（CraftingScreen）：整体禁用（与快速填充/双击合成等原版交互冲突）
 */
object ClientContainerDragHandler {

    /** 本次拖动的来源槽位（null 表示未在拖动） */
    private var dragOrigin: Slot? = null

    /** 上一次点击的槽位与时间（双击检测，<250ms 同槽位交给原版拾取全部） */
    private var lastClickSlot: Slot? = null
    private var lastClickTime = 0L

    /** 按屏幕坐标定位容器槽位（getLeftPos/getTopPos + 相对坐标） */
    private fun findSlot(screen: AbstractContainerScreen<*>, mx: Int, my: Int): Slot? {
        val leftPos = screen.getLeftPos()
        val topPos = screen.getTopPos()
        return screen.menu.slots.firstOrNull { s ->
            s.isActive && mx >= leftPos + s.x && mx < leftPos + s.x + 18 &&
                my >= topPos + s.y && my < topPos + s.y + 18
        }
    }

    private fun click(screen: AbstractContainerScreen<*>, slot: Slot, button: Int, type: ContainerInput) {
        (screen as AbstractContainerScreenInvokerMixin)
            .primalspells_slotClicked(slot, slot.index, button, type)
    }

    @SubscribeEvent
    fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.button != 0) return                       // 仅左键
        if (dragOrigin != null) return                      // 已在拖动
        if (!Minecraft.getInstance().hasControlDown()) return // 仅 Ctrl+左键 触发拖动交换模式
        if (Minecraft.getInstance().hasShiftDown()) return  // 保留 Shift 快速移动
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        if (screen is CraftingScreen) return                // 工作台：交给原版（快速填充等）
        val mx = event.mouseX.toInt()
        val my = event.mouseY.toInt()
        val slot = findSlot(screen, mx, my) ?: return
        if (!slot.hasItem()) return
        if (!screen.menu.carried.isEmpty) return            // 已有携带（原版点击处理）
        val now = Util.getMillis()
        if (slot === lastClickSlot && now - lastClickTime < 250) return // 双击：原版拾取全部
        lastClickSlot = slot
        lastClickTime = now
        dragOrigin = slot
        click(screen, slot, 0, ContainerInput.PICKUP)       // 拾起（服务端同步，拖动跟手渲染自动）
        event.isCanceled = true
    }

    @SubscribeEvent
    fun onMouseReleased(event: ScreenEvent.MouseButtonReleased.Pre) {
        val origin = dragOrigin ?: return
        dragOrigin = null
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        val mx = event.mouseX.toInt()
        val my = event.mouseY.toInt()
        val target = findSlot(screen, mx, my)

        // 同一槽位（未拖动）：不取消，物品保留在光标 = 原版单击拾起
        if (target === origin) return

        if (target == null) {
            // 空白释放：放回原槽
            click(screen, origin, 0, ContainerInput.PICKUP)
        } else {
            // 目标槽有物品：两步 PICKUP 实现真正互换（光标清空，双方留在槽位）
            val swapped = target.hasItem()
            click(screen, target, 0, ContainerInput.PICKUP)
            if (swapped) click(screen, origin, 0, ContainerInput.PICKUP)
        }
        event.isCanceled = true
    }

    /** 菜单关闭时清空拖动状态，避免残留 */
    @SubscribeEvent
    fun onScreenClosed(event: ScreenEvent.Closing) {
        dragOrigin = null
    }
}
