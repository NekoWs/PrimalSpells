package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class AddExpiration : DrawAction() {
    override val id = "add_expiration"
    override val kind = Kind.EXPIRE
    override val copies = 1
    init { mana = 15.0; delay = 0 }
}
