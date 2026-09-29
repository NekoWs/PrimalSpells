package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

class ExtendedLifetime : Modifier() {
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