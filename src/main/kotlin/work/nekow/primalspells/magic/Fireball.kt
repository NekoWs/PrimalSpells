package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.HitEntity
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
        effects += Trajectory(ParticleTypes.END_ROD)
        effects += HitEntity(0.2)
        effects += Hurt()
    }
}