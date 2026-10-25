package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.lifecycle.LifetimeAdjustment
import work.nekow.primalspells.util.Lore

class ExtendedLifetime : Modifier() {
    override val id = "extended_lifetime"

    init {
        mana = 40.0
        delay = 5
        effects += LifetimeAdjustment(25)
        lore(Lore.MAX_AGE, "+25")
    }
}