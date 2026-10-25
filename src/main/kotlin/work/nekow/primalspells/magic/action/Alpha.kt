package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Alpha : DrawAction() {
    override val id = "alpha"
    override val kind = Kind.FIRST
    init { mana = 30.0; delay = 5 }
}
