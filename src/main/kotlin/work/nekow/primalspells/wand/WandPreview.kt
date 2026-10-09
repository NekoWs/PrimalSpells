package work.nekow.primalspells.wand

/** 服务端真实抽牌/付费的只读结果；scope隔离根链和各触发荷载。 */
data class WandPreview(val rows: List<Row>, val manaAfter: Double, val delay: Double, val recharge: Double,
                       val reasons: List<String>, val shots: Int) {
    /** 展开树的一行；同scope修正无牌序，控制牌expanded仍保留层级。 */
    data class Row(val id: String, val scope: String, val depth: Int, val kind: String,
                   val mana: Double, val accepted: Boolean, val modifiers: List<String>)
}
