package work.nekow.primalspells.magic.core

/** 抽牌控制卡：展开内容仍属于当前链，费用不足时整段展开不执行。 */
abstract class DrawAction : Magic() {
    enum class Kind { DIVIDE, ALL, HIT, TIMER, EXPIRE }
    abstract val kind: Kind
    open val copies = 1
    val expanded = arrayListOf<Magic>()
}
