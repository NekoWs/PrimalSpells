package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore

class DamageBoost: Modifier() {
    override val id = "damage_boost"

    init {
        mana = 5.0
        delay = 2
        effects += object: BaseEffect() {
            override fun onActive() {
                projectile.status.damage += 2
            }
        }
        lore(Lore.DAMAGE, "+2")
    }
}