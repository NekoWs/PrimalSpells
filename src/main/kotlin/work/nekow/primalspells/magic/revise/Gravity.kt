package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
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