package work.nekow.nekoui.common

import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import work.nekow.nekoui.FloatingWindow

/**
 * 追踪物品的容器浮窗基类：按唯一 ID 在玩家背包（手持/副手/背包）中定位物品。
 *
 * 核心约定（此前反复出问题的根源）：
 * - 每次 refresh 都**重新按 ID 定位**，绝不缓存 ItemStack 引用（容器同步会替换对象）
 * - 定位不到 → 统一走 [onLost]（默认隐藏窗口），子类可覆盖（如法杖窗常驻显示 "--"）
 * - 网格数量与内容统一由 [updateGrid] 更新（先 setCount 后 refresh，槽位数变化自动重填）
 *
 * 子类只需实现：ID 读写（[trackedId]/[idOf]）、物品匹配（[matches]）、
 * 法术读取（[spellsOf]）、槽位数量（[slotCountOf]），
 * 以及 [onContentChanged]（内容更新后的 UI 更新）与可选覆盖 [onLost]。
 */
abstract class TrackedContainerWindow(
    window: FloatingWindow,
    grid: SpellSlotGrid,
) : ContainerWindowBase(window, grid) {

    /** 当前追踪的物品 ID（悬停/右键时由管理器或子类更新） */
    var trackedId: String? = null

    /** 本次刷新定位到的物品栈（仅当帧有效，供同步操作使用） */
    protected var currentStack: ItemStack? = null
        private set

    /** 从物品栈读取追踪 ID */
    protected abstract fun idOf(stack: ItemStack): String?

    /** 物品类型匹配（is WandItem / is SpellPouchItem） */
    protected abstract fun matches(stack: ItemStack): Boolean

    /** 从物品栈读取法术 id 列表 */
    protected abstract fun spellsOf(stack: ItemStack): List<String>

    /** 槽位数量（法杖按容量动态、小包固定 16） */
    protected abstract fun slotCountOf(stack: ItemStack, spells: List<String>): Int

    /** ID 匹配（子类可覆盖实现"空 ID 兜底匹配并补 ID"） */
    protected open fun matchesId(stack: ItemStack): Boolean = idOf(stack) == trackedId

    /** 更新槽位网格（数量 + 内容），统一顺序：先 resize 后填充 */
    protected fun updateGrid(stack: ItemStack, spells: List<String>) {
        grid.setCount(slotCountOf(stack, spells))
        grid.refresh(spells)
    }

    /** 按 ID 重新定位物品（手持/副手/背包），找不到返回 null */
    protected open fun relocate(player: Player?): ItemStack? {
        if (player == null) return null
        listOf(player.mainHandItem, player.offhandItem).firstOrNull {
            matches(it) && matchesId(it)
        }?.let { return it }
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val st = inv.getItem(i)
            if (matches(st) && matchesId(st)) return st
        }
        return null
    }

    /** 物品丢失回调（默认隐藏窗口并清除追踪） */
    protected open fun onLost() {
        window.visible = false
        trackedId = null
    }

    /** 内容更新回调（子类更新标签/窗口高度等，此时网格已更新完毕） */
    protected open fun onContentChanged(stack: ItemStack, player: Player?, screen: Screen?) {}

    /** 统一刷新：重新定位 → 丢失走 [onLost]；否则更新网格 + [onContentChanged] */
    override fun refresh(player: Player?, screen: Screen?) {
        val stack = relocate(player)
        if (stack == null) {
            onLost()
            return
        }
        currentStack = stack
        val spells = spellsOf(stack)
        updateGrid(stack, spells)
        onContentChanged(stack, player, screen)
    }
}
