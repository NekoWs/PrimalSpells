package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.Explosion

class LargeExplosion : Projectile() {

    override val id = "large_explosion"

    init {
        mana = 5.0
        // 定价见 docs/design/combat-pricing.md §2：AoE 单体 8 DPS → 中心 24 伤害 / 60 tick（3 秒一发）。
        // 半径 6 是全场最大的范围，所以单发最重、节奏最慢：贴脸一发必死。
        delay = 24
        recharge = 60
        maxAge = 1
        speed = 0f
        hitRadius = 0.0
        // 蓄力 18 tick（0.9 s）：半径最大，蓄力也最长（明显的预兆 —— 够跑出去）
        effects += Explosion(onDeath = true, onHit = false, chargeTicks = 18)
    }

    override fun onSpell() {
        status.explosionRadius = 6.0f
        status.explosionDamage = 24.0
    }
}
