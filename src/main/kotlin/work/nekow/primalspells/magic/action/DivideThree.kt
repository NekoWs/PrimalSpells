package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class DivideThree : DrawAction() {
    override val id = "divide_three"
    override val kind = Kind.DIVIDE
    override val copies = 3
    init { mana = 50.0; delay = 15 }
}
