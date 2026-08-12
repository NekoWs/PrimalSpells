package work.nekow.nekoui.slot

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import work.nekow.nekoui.element.UiSlot

/**
 * 槽位交换动画：两个物品沿直线同时移动到对方槽位。
 * 速度曲线为缓入缓出（先慢后快后慢）。
 *
 * 使用时先完成逻辑交换（两个槽位的 stack 已互换），
 * 动画期间 [renderFor] 覆盖对应槽位的物品绘制：
 * - 槽位 A 绘制"原 B 物品"从 B 位置飞向 A 位置
 * - 槽位 B 绘制"原 A 物品"从 A 位置飞向 B 位置
 *
 * 时间推进按游戏 tick（[net.minecraft.client.DeltaTracker.getGameTimeDeltaTicks]）
 * 累积，与渲染帧率无关（高帧率下不会"瞬移"）。
 */
class SlotSwapAnimation(
    private val slotA: UiSlot,
    private val slotB: UiSlot,
    private val itemAToB: ItemStack,
    private val itemBToA: ItemStack,
    private val duration: Float = 14f,
) {
    private var elapsed = 0f

    val done: Boolean get() = elapsed >= duration

    /** 每帧推进（按游戏 tick 时间增量，1.0 = 1 tick） */
    fun tick() {
        if (!done) elapsed += Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks()
    }

    fun involves(slot: UiSlot): Boolean = slot === slotA || slot === slotB

    /** 缓入缓出（先慢后快后慢） */
    private fun ease(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t
        else 1f - ((-2f * t + 2f).let { it * it * it }) / 2f

    private fun lerp(from: Int, to: Int, t: Float): Int = (from + (to - from) * t).toInt()

    /** 为动画涉及的一个槽位绘制飞行中的物品 */
    fun renderFor(graphics: GuiGraphicsExtractor, font: Font, slot: UiSlot) {
        val t = ease((elapsed / duration).coerceIn(0f, 1f))
        val (ax, ay) = center(slotA)
        val (bx, by) = center(slotB)
        if (slot === slotA) {
            // 原 B 物品飞向 A
            if (!itemBToA.isEmpty) graphics.item(itemBToA, lerp(bx, ax, t) - 8, lerp(by, ay, t) - 8)
        } else {
            // 原 A 物品飞向 B
            if (!itemAToB.isEmpty) graphics.item(itemAToB, lerp(ax, bx, t) - 8, lerp(ay, by, t) - 8)
        }
    }

    private fun center(slot: UiSlot): Pair<Int, Int> =
        (slot.x + slot.width / 2) to (slot.y + slot.height / 2)
}
