package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.lifecycle.LifetimeAdjustment
import work.nekow.primalspells.util.Lore

/** 与延寿按加法结算，最终寿命由装配入口统一钳制，避免牌序影响。 */
class ReducedLifetime : Modifier() {
    override val id = "reduced_lifetime"
    init {
        mana = 10.0
        effects += LifetimeAdjustment(-25)
        lore(Lore.MAX_AGE, "-25")
    }
}
