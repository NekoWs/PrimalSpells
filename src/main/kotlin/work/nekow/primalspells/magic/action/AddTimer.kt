package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class AddTimer : DrawAction() {
    override val id = "add_timer"
    override val kind = Kind.TIMER
    override val copies = 1
    init { mana = 20.0; delay = 0 }
}
