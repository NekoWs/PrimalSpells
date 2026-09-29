package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.Trajectory

class Laser: Projectile() {
    override val id = "laser"
    override val bodyColor = Color.of(0.5f, 0.8f, 1f)

    init {
        mana = 10.0
        speed = 5f
        delay = -10
        recharge = -15
        maxAge = 1
        status.damage = 0.5
        hitRadius = 0.1
        effects += Trajectory(Color.of(0.5f, 0.8f, 1f))
        effects += Hurt()
    }
}