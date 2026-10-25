package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Gamma : DrawAction() {
    override val id = "gamma"
    override val kind = Kind.LAST
    init { mana = 30.0; delay = 5 }
}
