package work.nekow.primalspells.magic.effect

class Bounce(
    val amount: Int = 2
): BaseEffect() {
    override fun onActive() {
        projectile.maxBounces += amount
    }
}