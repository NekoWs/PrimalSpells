package work.nekow.primalspells.magic

import net.minecraft.core.particles.ParticleTypes
import work.nekow.primalspells.magic.effect.Trajectory

class Fireball: Projectile() {
    override val id = "fireball"

    init {
        mana = 1.0
        delay = 2
        recharge = 1
        maxAge = 100
        effects += Trajectory(ParticleTypes.END_ROD)

    }

    override fun clone() = Fireball().also { copyTo(it) }

    companion object {
        init {
            register("fireball") { Fireball() }
        }
    }
}