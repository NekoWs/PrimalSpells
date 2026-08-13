package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.Explosion

class SmallExplosion : Projectile() {

    override val id = "small_explosion"

    init {
        mana = 2.0
        delay = 1
        recharge = 2
        maxAge = 1
        speed = 0f
        hitRadius = 0.0
        effects += Explosion(onDeath = true, onHit = false)
    }

    override fun onSpell() {
        status.explosionRadius = 3.0f
        status.explosionDamage = 4.0
        status.explosionLevel = 0
    }
}
