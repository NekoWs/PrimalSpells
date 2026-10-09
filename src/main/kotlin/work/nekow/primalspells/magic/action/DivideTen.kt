package work.nekow.primalspells.magic.action

import work.nekow.primalspells.magic.core.DrawAction

class DivideTen : DrawAction() {
    override val id = "divide_ten"
    override val kind = Kind.DIVIDE
    override val copies = 10
    init { mana = 180.0; delay = 45 }
}
