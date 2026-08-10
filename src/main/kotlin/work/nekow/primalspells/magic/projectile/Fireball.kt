package work.nekow.primalspells.magic.projectile

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.Hurt
import work.nekow.primalspells.magic.effect.Trajectory

class Fireball: Projectile() {
    override val id = "fireball"

    init {
        mana = 3.0
        delay = 1
        recharge = 1
        maxAge = 200
        status.damage = 1.0
        hitRadius = 0.2
        effects += Trajectory(ParticleTypes.END_ROD)
        effects += Hurt()
    }
}