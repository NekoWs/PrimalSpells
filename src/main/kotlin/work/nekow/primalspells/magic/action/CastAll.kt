package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class CastAll : DrawAction() {
    override val id = "cast_all"
    override val kind = Kind.ALL
    override val copies = 1
    init { mana = 30.0; delay = 0 }
}
