package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Omega : DrawAction() {
    override val id = "omega"
    override val kind = Kind.ALL_COPIES
    init { mana = 120.0; delay = 30 }
}
