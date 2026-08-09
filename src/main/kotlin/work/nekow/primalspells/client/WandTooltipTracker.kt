package work.nekow.primalspells.client

/** 客户端追踪每个法杖当前滚轮选中的法术槽位。 */
object WandTooltipTracker {
    private val selectedSlots = mutableMapOf<String, Int>()

    fun getSelectedSlot(wandId: String) = selectedSlots[wandId] ?: 0

    fun setSelectedSlot(wandId: String, slot: Int) {
        selectedSlots[wandId] = slot
    }
}
