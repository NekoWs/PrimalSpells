package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class Sigma : DrawAction() {
    override val id = "sigma"
    override val kind = Kind.STATIC_PROJECTILES
    init { mana = 100.0; delay = 20 }
}
