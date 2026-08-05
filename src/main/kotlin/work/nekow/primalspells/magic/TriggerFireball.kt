package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.Trajectory

class TriggerFireball: TriggerMagic() {
    override val id = "trigger_fireball"
    override fun clone() = TriggerFireball().also { copyTo(it) }

    init {
        mana = 3.0
        delay = 2
        recharge = 1
        maxAge = 100
        effects += Trajectory(ParticleTypes.FLAME)
    }

    companion object {
        init {
            register("trigger_fireball") { TriggerFireball() }
        }
    }
}