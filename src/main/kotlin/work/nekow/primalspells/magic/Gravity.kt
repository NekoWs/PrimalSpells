package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.BaseEffect

class Gravity: Revise() {
    override val id = "gravity"

    init {
        effects += object: BaseEffect() {
            override fun onTick() {
                status.velocity.y -= 0.03F
            }
        }
    }
}