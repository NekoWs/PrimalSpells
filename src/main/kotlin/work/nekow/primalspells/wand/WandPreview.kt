package work.nekow.primalspells.wand

/** 服务端真实抽牌/付费的只读结果；scope隔离根链和各触发荷载。 */
data class WandPreview(val rows: List<Row>, val manaAfter: Double, val delay: Double, val recharge: Double,
                       val reasons: List<String>, val shots: Int, val slots: List<Slot> = emptyList()) {
    /** 整副配置的槽位归属；不随下一发牌堆游标或当前MP变化。空槽scope为空。 */
    data class Slot(val id: String, val scope: String)
    /** 展开树的一行；同scope修正无牌序，控制牌expanded仍保留层级。 */
    data class Row(val id: String, val scope: String, val depth: Int, val kind: String,
                   val mana: Double, val accepted: Boolean, val modifiers: List<String>)
}
