package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.PrimalSpells.Companion.overlay
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.utils.Lore
import work.nekow.primalspells.utils.LoreEntry

class SpeedUp: Revise() {
    override val id = "speed_up"

    init {
        mana = 3.0
        effects += object: BaseEffect() {
            override fun onActive() {
                status.velocity.mul(2f)
            }

            override fun onTick() {
                caster.overlay("Velocity: ${status.velocity}")
            }
        }
        lore += LoreEntry(Lore.SPEED, arrayOf(2))
    }
}