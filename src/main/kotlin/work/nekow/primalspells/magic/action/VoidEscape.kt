package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.OtherSpell
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus

/** 主动进入无界之界；不生成碰撞弹体，也不将保护状态附到敌人。 */
class VoidEscape : OtherSpell() {
    override val id = "void_escape"
    override val maxUses = 25
    init { mana = 0.0; delay = 200; recharge = 200 }
    override fun onSpell() { VoidStatus.enter(caster) }
}
