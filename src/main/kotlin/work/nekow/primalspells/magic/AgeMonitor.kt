package work.nekow.primalspells.magic

import work.nekow.primalspells.PrimalSpells.Companion.overlay
import work.nekow.primalspells.magic.effect.BaseEffect

class AgeMonitor: Revise() {
    override val id = "age_monitor"

    init {
        effects += object: BaseEffect() {
            override fun onTick() {
                caster.overlay("Age: ${status.age}")
                val pos = status.pos
                caster.teleportTo(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            }
        }
    }
}