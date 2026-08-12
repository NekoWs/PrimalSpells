package work.nekow.nekoui.slot

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import work.nekow.nekoui.element.UiSlot

/**
 * 槽位拖动控制器（数据权威化实现）：
 * - 左键按下槽位拾起：仅记录快照并标记 [UiSlot.picked]（渲染层隐藏物品），
 *   槽位 [UiSlot.stack] 本身**从不修改**——数据始终以服务端同步为准
 * - 拖动中：跟随鼠标绘制携带物品（[renderCarried]）
 * - 松开：
 *   - 落在窗口内其他槽位 → 记录交换意图（[lastSwap]，供外层发 SWAP 包）+ 纯视觉交换动画
 *   - 落在窗口内空白 → 无任何操作（数据从未被修改，天然放回）
 *   - 落在窗口外 → 由外层（ContainerWindowBase）做卸载/跨窗处理
 *
 * 槽位内容的最终刷新由 SpellSlotGrid.refresh（服务端组件数据）完成，
 * 因此本控制器不承担任何数据一致性责任。
 *
 * 坐标约定：所有传入坐标均为"槽位所在坐标系"（如窗口内容区相对坐标）。
 */
class SlotDragController {

    private var origin: UiSlot? = null
    private var carriedStack: ItemStack = ItemStack.EMPTY
    private var dragX = 0
    private var dragY = 0
    private var animation: SlotSwapAnimation? = null

    /** 最近一次 [released] 的交换意图（读取后请清空） */
    var lastSwap: Pair<UiSlot, UiSlot>? = null

    /** 当前是否正在拾起拖动 */
    fun isDragging(): Boolean = origin != null

    /** 当前拖动的来源槽位（无拖动时 null） */
    fun dragOrigin(): UiSlot? = origin

    /** 拾起瞬间的权威快照（拖动期间携带的内容，非空保证） */
    fun carriedStack(): ItemStack = carriedStack

    /** 槽位是否正处于拾起状态（渲染层隐藏物品） */
    fun isPickedUp(slot: UiSlot): Boolean = origin === slot

    /** 丢弃当前拖动（物品数据未被修改，直接复位状态） */
    fun cancel() {
        origin?.picked = false
        origin = null
        carriedStack = ItemStack.EMPTY
    }

    /** 动画是否正在进行 */
    fun isAnimating(): Boolean = animation != null && !animation!!.done

    /** 按下拾起。@return true 表示消费 */
    fun pressed(slot: UiSlot?, button: Int): Boolean {
        if (button != 0 || isAnimating()) return false
        val s = slot ?: return false
        if (s.stack.isEmpty) return false
        origin = s
        carriedStack = s.stack
        s.picked = true
        dragX = s.x + s.width / 2
        dragY = s.y + s.height / 2
        return true
    }

    /** 拖动更新携带物品位置 */
    fun dragged(mx: Int, my: Int) {
        dragX = mx
        dragY = my
    }

    /**
     * 松开（窗口内）：只产生意图与动画，不修改任何槽位数据。
     * - 窗口内其他槽位（含空槽）：记录交换意图并播放交换动画
     * - 窗口内空白：直接放回（无操作）
     */
    fun released(slots: List<UiSlot>, mx: Int, my: Int) {
        val o = origin ?: return
        val itemA = carriedStack
        origin = null
        o.picked = false
        if (itemA.isEmpty) return

        val target = slots.firstOrNull {
            it !== o && it.visible && it.contains(mx.toDouble(), my.toDouble())
        }

        if (target == null) {
            // 窗口内空白释放：数据未被修改，天然放回
            carriedStack = ItemStack.EMPTY
            return
        }

        // 有物品槽：交换；空槽：移动（服务端 SWAP 对空槽等效移动）
        val itemB = target.stack
        animation = SlotSwapAnimation(o, target, itemA, itemB)
        lastSwap = o to target
        carriedStack = ItemStack.EMPTY
    }

    /** 每帧推进动画 */
    fun tick() {
        if (animation != null && animation!!.done) animation = null
        animation?.tick()
    }

    /** 槽位是否被当前动画覆盖渲染 */
    fun hasAnimationFor(slot: UiSlot): Boolean = animation?.involves(slot) == true

    /** 绘制动画中该槽位的飞行物品（槽位背景由调用方先绘制） */
    fun renderAnimatedSlot(graphics: GuiGraphicsExtractor, font: Font, slot: UiSlot) {
        animation?.renderFor(graphics, font, slot)
    }

    /** 绘制跟随鼠标的携带物品（居中于鼠标） */
    fun renderCarried(graphics: GuiGraphicsExtractor, font: Font) {
        if (carriedStack.isEmpty) return
        graphics.item(carriedStack, dragX - 8, dragY - 8)
    }
}
