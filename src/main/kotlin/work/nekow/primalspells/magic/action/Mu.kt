package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Mu : DrawAction() {
    override val id = "mu"
    override val kind = Kind.MODIFIERS
    init { mana = 60.0; delay = 10 }
}
