package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.Explosion

class LargeExplosion : Projectile() {

    override val id = "large_explosion"

    init {
        mana = 5.0
        delay = 2
        recharge = 3
        maxAge = 1
        speed = 0f
        hitRadius = 0.0
        effects += Explosion(onDeath = true, onHit = false)
    }

    override fun onSpell() {
        status.explosionRadius = 6.0f
        status.explosionDamage = 8.0
    }
}
