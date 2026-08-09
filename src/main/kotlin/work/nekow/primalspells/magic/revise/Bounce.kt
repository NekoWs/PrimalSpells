package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

class Bounce: Revise() {
    override val id = "bounce"

    init {
        mana = 2.0
        delay = 1
        effects += object: BaseEffect() {
            override fun onActive() {
                projectile.maxBounces = 10
            }
        }
    }
}
