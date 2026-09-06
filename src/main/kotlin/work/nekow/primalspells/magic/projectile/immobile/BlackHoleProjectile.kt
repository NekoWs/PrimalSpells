package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.effect.BlackHole
import work.nekow.primalspells.magic.effect.Trajectory
import work.nekow.primalspells.utils.Lore

/**
 * 黑洞，将范围内实体拉向中心并施加迟缓 I。
 */
class BlackHoleProjectile : Projectile() {

    override val id = "black_hole"
    override val bodyColor = Color.of(0.3f, 0f, 0.5f)

    init {
        mana = 12.0
        delay = 5
        recharge = 10
        maxAge = 200
        speed = 0f
        hitRadius = 0.0
        effects += Trajectory(Color.of(0.3f, 0f, 0.5f))
        effects += BlackHole()
    }

    override fun onSpell() {}

    override fun initLore() {
        super.initLore()
        lore(Lore.DESCRIPTION)
    }
}
