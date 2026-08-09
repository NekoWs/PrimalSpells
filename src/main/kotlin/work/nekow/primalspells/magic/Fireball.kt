package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.effect.Damage
import work.nekow.primalspells.magic.effect.HitEntity
import work.nekow.primalspells.magic.effect.Trajectory

class Fireball: Projectile() {
    override val id = "fireball"

    init {
        mana = 1.0
        delay = 2
        recharge = 1
        maxAge = 100
        status.damage = 5.0
        effects += Trajectory(ParticleTypes.END_ROD)
        effects += HitEntity(1.0)
        effects += Damage()
    }

    override fun onHitEntity(entity: Entity) {
        this.alive = false
    }
}