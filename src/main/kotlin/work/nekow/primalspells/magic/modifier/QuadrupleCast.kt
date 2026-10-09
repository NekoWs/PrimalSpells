package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

class QuadrupleCast : Modifier() {
    override val id = "quadruple_cast"
    init { cast = 4; mana = 3.0 }
}
