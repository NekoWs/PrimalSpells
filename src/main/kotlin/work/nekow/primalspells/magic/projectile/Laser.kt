package work.nekow.primalspells.magic.projectile

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.Hurt
import work.nekow.primalspells.magic.effect.Trajectory

class Laser: Projectile() {
    override val id = "laser"

    init {
        mana = 10.0
        speed = 5f
        delay = -10
        recharge = -15
        maxAge = 1
        status.damage = 0.5
        hitRadius = 0.1
        effects += Trajectory(ParticleTypes.END_ROD)
        effects += Hurt()
    }
}