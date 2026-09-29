package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.Trajectory

class Fireball: Projectile() {
    override val id = "fireball"
    override val bodyColor = Color.ORANGE

    init {
        mana = 3.0
        delay = 1
        recharge = 1
        maxAge = 200
        status.damage = 1.0
        hitRadius = 0.2
        effects += Trajectory(Color.ORANGE)
        effects += Hurt()
        speed = 0.5f
    }
}