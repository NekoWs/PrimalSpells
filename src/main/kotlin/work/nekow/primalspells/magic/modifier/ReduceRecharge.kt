package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

class ReduceRecharge : Modifier() {
    override val id = "reduce_recharge"
    init { mana = 12.0; delay = -3; recharge = -7 }
}
