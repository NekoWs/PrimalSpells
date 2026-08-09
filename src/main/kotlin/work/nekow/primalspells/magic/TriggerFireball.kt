package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.HitEntity
import work.nekow.primalspells.magic.effect.Hurt
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.magic.effect.Trigger

class TriggerFireball: Projectile(), TriggerSpell {
    override val id = "trigger_fireball"

    override var triggerCast = 1
    override var payload = arrayListOf<Magic>()

    init {
        mana = 5.0
        delay = 1
        recharge = 1
        maxAge = 100
        status.damage = 1.0
        effects += Trajectory(ParticleTypes.FLAME)
        effects += HitEntity(0.2)
        effects += Hurt()
        effects += Trigger()
    }
}