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
                // 定价见 docs/design/combat-pricing.md §2：与火球同价位的一档（+3 = 火球 5 → 8）
                projectile.status.damage += 3
            }
        }
        lore(Lore.DAMAGE, "+3")
    }
}