package work.nekow.primalspells.magic.effect.geometry

import work.nekow.primalspells.magic.effect.BaseEffect

class Bounce(
    val amount: Int = 2
): BaseEffect() {
    override fun onActive() {
        projectile.maxBounces += amount
    }
}