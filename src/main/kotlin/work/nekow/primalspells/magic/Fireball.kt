package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.effect.HitEntity
import work.nekow.primalspells.magic.effect.Hurt
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.magic.lore.Lore
import work.nekow.primalspells.magic.lore.LoreEntry

class Fireball: Projectile() {
    override val id = "fireball"

    init {
        mana = 1.0
        delay = 2
        recharge = 1
        maxAge = 100
        status.damage = 5.0
        effects += Trajectory(ParticleTypes.END_ROD)
        effects += HitEntity(0.2)
        effects += Hurt()
        lore += LoreEntry(Lore.MANA, arrayOf(Lore.formatDouble(mana)))
        lore += LoreEntry(Lore.DELAY, arrayOf(Lore.formatTicks(delay)))
        lore += LoreEntry(Lore.RECHARGE, arrayOf(Lore.formatTicks(recharge)))
        lore += LoreEntry(Lore.DAMAGE, arrayOf(Lore.formatDouble(status.damage)))
    }

    override fun onHitEntity(entity: Entity) {
        this.alive = false
    }
}