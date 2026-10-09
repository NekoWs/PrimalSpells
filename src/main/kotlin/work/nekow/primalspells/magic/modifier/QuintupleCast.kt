package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

class QuintupleCast : Modifier() {
    override val id = "quintuple_cast"
    init { cast = 5; mana = 6.0 }
}
