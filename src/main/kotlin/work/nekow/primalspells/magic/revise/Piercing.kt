package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

class Piercing : Revise() {
    override val id = "piercing"

    init {
        mana = 200.0
        effects += object: BaseEffect() {
            override fun onActive() {
                projectile.maxHitTargets = -1
                status.damage -= 8
            }
        }
    }
}