package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

class DoubleCast: Modifier() {
    override val id = "double_cast"

    init {
        cast = 2
    }
}