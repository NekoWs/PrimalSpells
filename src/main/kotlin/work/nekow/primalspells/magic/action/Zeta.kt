package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Zeta : DrawAction() {
    override val id = "zeta"
    override val kind = Kind.OTHER_WAND
    init { mana = 40.0; delay = 5 }
}
