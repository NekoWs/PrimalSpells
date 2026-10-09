package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class DivideTwo : DrawAction() {
    override val id = "divide_two"
    override val kind = Kind.DIVIDE
    override val copies = 2
    init { mana = 30.0; delay = 10 }
}
