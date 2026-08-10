package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore

class DamageBoost: Revise() {
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