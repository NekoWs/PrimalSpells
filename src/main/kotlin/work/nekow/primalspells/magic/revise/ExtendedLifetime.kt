package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore

class ExtendedLifetime : Revise() {
    override val id = "extended_lifetime"

    init {
        mana = 40.0
        effects += object: BaseEffect() {
            override fun onActive() {
                projectile.maxAge += 200
            }
        }
        lore(Lore.MAX_AGE, "+200")
    }
}