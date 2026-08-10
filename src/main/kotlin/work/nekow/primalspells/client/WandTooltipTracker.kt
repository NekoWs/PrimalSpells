package work.nekow.primalspells.client

object WandTooltipTracker {
    private val selectedSlots = mutableMapOf<String, Int>()

    fun getSelectedSlot(wandId: String) = selectedSlots[wandId] ?: 0

    fun setSelectedSlot(wandId: String, slot: Int) {
        selectedSlots[wandId] = slot
    }
}
