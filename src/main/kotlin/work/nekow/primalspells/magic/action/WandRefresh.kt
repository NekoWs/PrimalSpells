package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class WandRefresh : DrawAction() {
    override val id = "wand_refresh"
    override val kind = Kind.REFRESH
    init { mana = 20.0; delay = 0; recharge = -10 }
}
