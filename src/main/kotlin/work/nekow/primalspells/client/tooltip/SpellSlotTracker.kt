package work.nekow.primalspells.client.tooltip

/**
 * 客户端「当前预览/选中的法术槽位」缓存：`标识 id → 槽位索引`。
 *
 * 法杖与法术小包共用同一实现，键分别是 `wand_id` 与 `pouch_id`。
 * 它只决定本地 tooltip 的高亮与「当前选中法术」的展示；**权威值**是物品上的
 * `WAND_SELECTED_SLOT` / `POUCH_SELECTED_SLOT` 组件（服务端同步）。
 *
 * 缓存按 id 只增不减，因此在退出世界/断线时由 [clear] 统一清理。
 */
object SpellSlotTracker {
    private val selectedSlots = mutableMapOf<String, Int>()

    fun getSelectedSlot(targetId: String): Int = selectedSlots[targetId] ?: 0

    fun setSelectedSlot(targetId: String, slot: Int) {
        selectedSlots[targetId] = slot
    }

    /** 登出/断线时清空，避免长时间游玩下缓存只增不减。 */
    fun clear() {
        selectedSlots.clear()
    }
}
