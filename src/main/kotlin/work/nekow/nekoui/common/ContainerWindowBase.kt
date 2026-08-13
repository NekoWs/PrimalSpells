package work.nekow.nekoui.common

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.ModItems
import work.nekow.nekoui.FloatingWindow
import work.nekow.nekoui.slot.SlotDragController

/**
 * 通用法术容器浮窗基类：封装窗口化、槽位拖拽与装卸交互，
 * 具体容器（法杖 / 法术小包）只需实现三个同步操作。
 *
 * 拖出窗口时支持：
 * - 拖到其他容器浮窗的槽位 → 跨浮窗移动（目标加入 + 源静默移除）
 * - 拖到容器槽位 → 放置 / 交换
 * - 其他 → 回退玩家背包（背包满回退光标）
 *
 * 数据权威化：槽位内容始终以服务端同步（组件刷新）为准，拖动过程不修改槽位数据；
 * 释放时携带的是拾起瞬间的权威快照（SlotDragController.carriedStack）。
 */
abstract class ContainerWindowBase(
    val window: FloatingWindow,
    protected val grid: SpellSlotGrid,
) {
    init {
        window.slotDrag = SlotDragController()
        window.onSlotSwapped = { a, b ->
            val ia = grid.indexOf(a)
            val ib = grid.indexOf(b)
            if (ia >= 0 && ib >= 0) syncSwap(ia, ib)
        }
        register(this)
    }

    /** 交换容器内槽位 [a] 与 [b] */
    protected abstract fun syncSwap(a: Int, b: Int)

    /** 将法术 [spellId] 加入容器内槽位 [index]（服务端处理空槽放入/有物品槽交换） */
    protected abstract fun syncAdd(index: Int, spellId: String)

    /** 移除容器内槽位 [index] 的法术；[targetSlot] 为目标容器槽（-1 背包）；[silent] 为 true 时不发放物品 */
    protected abstract fun syncRemove(index: Int, targetSlot: Int, silent: Boolean)

    /** 子类刷新数据（每帧调用） */
    open fun refresh(player: Player?, screen: Screen?) {}

    /** 窗口内容区坐标 → 网格槽位索引（无则 -1） */
    private fun slotIndexAt(screenMx: Int, screenMy: Int): Int {
        val rx = screenMx - window.x
        val ry = screenMy - window.y - FloatingWindow.TITLE_BAR_HEIGHT
        return grid.slotIndexAt(rx, ry)
    }

    /** 背包拾起的法术拖入空槽（ADD） */
    fun tryAddFromCarried(mx: Int, my: Int, player: Player?): Boolean {
        if (player == null || window.minimized) return false
        val carried = player.containerMenu.carried
        if (carried.isEmpty) return false
        val spellId = ModItems.spellIdOf(carried.item) ?: return false

        val index = slotIndexAt(mx, my)
        if (index < 0) return false

        syncAdd(index, spellId)
        player.containerMenu.setCarried(ItemStack.EMPTY)
        return true
    }

    /**
     * 拖出窗口时的投放决策（优先级）：
     * 1. 其他容器浮窗槽位 → 跨浮窗移动（目标 ADD + 源静默移除）
     * 2. 容器槽位 → 放置 / 交换
     * 3. 其他 → 回退玩家背包（背包满回退光标）
     *
     * @return true 表示已处理本次投放
     */
    fun tryRemoveToContainer(mx: Int, my: Int, screen: Screen?): Boolean {
        if (window.isInsideWindow(mx, my)) return false
        val ctrl = window.slotDrag ?: return false
        val origin = ctrl.dragOrigin() ?: return false
        val index = grid.indexOf(origin)
        if (index < 0) return false

        // 拖动内容：拾起瞬间的权威快照（源槽位数据从未被修改，两者等价）
        val carried = ctrl.carriedStack()
        val spellId = ModItems.spellIdOf(carried.item) ?: return false

        // 1) 跨浮窗移动
        for (other in otherWindows()) {
            if (other.acceptExternalSpell(spellId, mx, my)) {
                syncRemove(index, -1, true)
                ctrl.cancel()
                return true
            }
        }

        // 2) 容器槽位（按坐标精确定位；26.2 NeoForge 提供 getLeftPos/getTopPos getter）
        var target = -1
        if (screen is AbstractContainerScreen<*>) {
            val leftPos = screen.getLeftPos()
            val topPos = screen.getTopPos()
            val slot = screen.menu.slots.firstOrNull { s ->
                s.isActive && mx >= leftPos + s.x && mx < leftPos + s.x + 18 &&
                    my >= topPos + s.y && my < topPos + s.y + 18
            }
            if (slot != null) target = slot.index
        }

        // 3) 回退：创造模式直接删除（不再放回背包/快捷栏）；生存模式按目标槽放置/交换/回退背包
        val creative = Minecraft.getInstance().player?.abilities?.instabuild == true
        syncRemove(index, target, creative)
        ctrl.cancel()
        return true
    }

    /**
     * 接受外部拖入的法术（拖到本窗口槽位时调用）。
     * @return true 表示接受（目标槽加入；有物品槽由服务端处理交换）
     */
    fun acceptExternalSpell(spellId: String, mx: Int, my: Int): Boolean {
        if (!window.visible || window.minimized) return false
        val index = slotIndexAt(mx, my)
        if (index < 0) return false
        syncAdd(index, spellId)
        return true
    }

    companion object {
        private val windows = mutableListOf<ContainerWindowBase>()

        /** 注册容器浮窗（供跨浮窗拖放） */
        fun register(w: ContainerWindowBase) {
            if (w !in windows) windows += w
        }
    }

    private fun otherWindows(): List<ContainerWindowBase> =
        windows.filter { it !== this }
}
