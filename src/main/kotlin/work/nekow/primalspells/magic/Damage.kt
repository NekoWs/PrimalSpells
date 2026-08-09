package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.BaseEffect

class Damage: Revise() {
    override val id = "damage"

    init {
        mana = 5.0
        delay = 1
        effects += object: BaseEffect() {
            override fun onActive() {
                projectile.status.damage += 2
            }
        }
    }
}