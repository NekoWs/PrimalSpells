package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.magic.lore.Lore
import work.nekow.primalspells.magic.lore.LoreEntry

class TriggerFireball: Projectile(), TriggerSpell {
    override val id = "trigger_fireball"

    override var triggerCast = 1
    override var payload = arrayListOf<Magic>()

    init {
        mana = 3.0
        delay = 2
        recharge = 1
        maxAge = 100
        effects += Trajectory(ParticleTypes.FLAME)
        lore += LoreEntry(Lore.MANA, arrayOf(Lore.formatDouble(mana)))
        lore += LoreEntry(Lore.DELAY, arrayOf(Lore.formatTicks(delay)))
        lore += LoreEntry(Lore.RECHARGE, arrayOf(Lore.formatTicks(recharge)))
        lore += LoreEntry(Lore.MAX_AGE, arrayOf(maxAge.toString()))
    }
}