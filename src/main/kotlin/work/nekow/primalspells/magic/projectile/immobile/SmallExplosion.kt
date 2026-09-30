package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.Explosion

class SmallExplosion : Projectile() {

    override val id = "small_explosion"

    init {
        mana = 2.0
        // 定价见 docs/design/combat-pricing.md §2：AoE 单体 8 DPS → 中心 12 伤害 / 30 tick（1.5 秒一发）。
        // AoE 能同时打多个目标，所以单体 DPS 刻意比单体弹（10）低一档。
        delay = 12
        recharge = 30
        maxAge = 1
        speed = 0f
        hitRadius = 0.0
        // 蓄力 8 tick（0.4 s）：内爆看起来是"埋下去、蓄一下、炸"，见 ExplosionFx
        effects += Explosion(onDeath = true, onHit = false, chargeTicks = 8)
    }

    override fun onSpell() {
        status.explosionRadius = 3.0f
        status.explosionDamage = 12.0
    }
}
