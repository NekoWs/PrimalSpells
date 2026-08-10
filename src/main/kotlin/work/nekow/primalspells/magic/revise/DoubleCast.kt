package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise

class DoubleCast: Revise() {
    override val id = "double_cast"

    init {
        cast = 2
    }
}