package work.nekow.primalspells.magic.core

/** 不属于投射物或修正的主动法术；由施法入口或已付费荷载执行。 */
abstract class OtherSpell : Magic() {
    override var cast = 0

    /** 直接施法与荷载共用的本链修正入口；主动状态只读取自己支持的参数，不激活弹体效果。 */
    open fun prepare(modifiers: List<Modifier>) { }
}
