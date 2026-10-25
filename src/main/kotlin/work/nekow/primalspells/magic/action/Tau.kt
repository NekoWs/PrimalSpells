package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Tau : DrawAction() {
    override val id = "tau"
    override val kind = Kind.NEXT_TWO
    init { mana = 50.0; delay = 10 }
}
