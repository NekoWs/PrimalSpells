package work.nekow.primalspells.magic.projectile

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.Magic
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.TriggerSpell
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
        maxAge = 200
        status.damage = 1.0
        hitRadius = 0.2
        effects += Trajectory(ParticleTypes.FLAME)
        effects += Hurt()
        effects += Trigger()
    }
}