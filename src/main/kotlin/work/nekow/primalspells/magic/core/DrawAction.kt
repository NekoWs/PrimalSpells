package work.nekow.primalspells.magic.core

/** 抽牌控制卡：展开内容仍属于当前链，费用不足时整段展开不执行。 */
abstract class DrawAction : OtherSpell() {
    override var cast = 1
    enum class Kind {
        DIVIDE, ALL, HIT, TIMER, EXPIRE,
        FIRST, LAST, NEXT_TWO, ALL_COPIES, MODIFIERS, PROJECTILES, STATIC_PROJECTILES, OTHER_WAND, REFRESH,
    }
    abstract val kind: Kind
    open val copies = 1
    val expanded = arrayListOf<Magic>()
}
