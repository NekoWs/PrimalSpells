package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class AddTrigger : DrawAction() {
    override val id = "add_trigger"
    override val kind = Kind.HIT
    override val copies = 1
    init { mana = 10.0; delay = 0 }
}
