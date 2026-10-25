package work.nekow.primalspells.magic.core

/** 不属于投射物或修正的主动法术；由施法入口或已付费荷载执行。 */
abstract class OtherSpell : Magic() {
    override var cast = 0
}
