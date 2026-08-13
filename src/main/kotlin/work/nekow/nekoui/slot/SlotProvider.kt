package work.nekow.nekoui.slot

import work.nekow.nekoui.element.UiSlot

/**
 * 槽位提供者：暴露一组可交互槽位（如 [work.nekow.nekoui.common.SpellSlotGrid]）。
 * 供 [work.nekow.nekoui.FloatingWindow] 等通用窗口在渲染与输入分发时统一收集槽位，
 * 避免与具体槽位容器耦合。
 */
interface SlotProvider {
    fun slots(): List<UiSlot>
}
