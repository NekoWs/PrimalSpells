package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.BaseEffect

abstract class Revise: Magic() {
    fun effects(): List<BaseEffect> {
        return effects.map { it.clone() }
    }
}
