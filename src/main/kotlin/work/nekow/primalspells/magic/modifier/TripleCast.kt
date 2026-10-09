package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

class TripleCast : Modifier() {
    override val id = "triple_cast"
    init { cast = 3; mana = 1.0 }
}
