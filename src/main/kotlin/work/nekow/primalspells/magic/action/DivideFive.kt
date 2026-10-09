package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class DivideFive : DrawAction() {
    override val id = "divide_five"
    override val kind = Kind.DIVIDE
    override val copies = 5
    init { mana = 90.0; delay = 25 }
}
