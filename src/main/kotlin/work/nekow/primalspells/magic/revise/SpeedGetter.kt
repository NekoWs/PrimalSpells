package work.nekow.primalspells.magic.revise

import work.nekow.primalspells.PrimalSpells.Companion.overlay
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.effect.BaseEffect

class SpeedGetter: Revise() {
    override val id = "speed_getter"

    init {
        effects += object : BaseEffect() {
            override fun onTick() {
                val distance = status.pos.distance(projectile.position)
                caster.overlay("D=%.3f S=%.3f/T", distance, distance / status.age)
            }
        }
    }
}