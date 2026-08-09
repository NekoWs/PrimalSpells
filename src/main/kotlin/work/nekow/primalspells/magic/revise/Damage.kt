package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore
import work.nekow.primalspells.utils.LoreEntry

class Damage: Revise() {
    override val id = "damage"

    init {
        mana = 5.0
        delay = 2
        effects += object: BaseEffect() {
            override fun onActive() {
                projectile.status.damage += 2
            }
        }
        lore += LoreEntry(Lore.DAMAGE, arrayOf("+2"))
    }
}