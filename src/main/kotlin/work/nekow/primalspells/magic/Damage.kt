package work.nekow.primalspells.magic

import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.lore.Lore
import work.nekow.primalspells.magic.lore.LoreEntry

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
        lore += LoreEntry(Lore.MANA, arrayOf(Lore.formatDouble(mana)))
        lore += LoreEntry(Lore.DELAY, arrayOf(Lore.formatTicks(delay)))
        lore += LoreEntry(Lore.DAMAGE, arrayOf("+2"))
    }
}