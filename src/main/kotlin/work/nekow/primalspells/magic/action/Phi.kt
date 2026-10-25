package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Phi : DrawAction() {
    override val id = "phi"
    override val kind = Kind.PROJECTILES
    init { mana = 100.0; delay = 20 }
}
