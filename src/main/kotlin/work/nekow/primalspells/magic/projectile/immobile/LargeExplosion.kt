package work.nekow.primalspells.magic.projectile.immobile

import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.Explosion

class LargeExplosion : Projectile() {

    override val id = "large_explosion"

    init {
        mana = 80.0
        // 定价见 docs/design/combat-pricing.md §2：AoE 单体 8 DPS → 中心 24 伤害 / 60 tick（3 秒一发）。
        // 半径 6 是全场最大的范围，所以单发最重、节奏最慢：贴脸一发必死。
        delay = 0
        recharge = 0
        maxAge = 1
        speed = 0f
        hitRadius = 0.0
        effects += Explosion(onDeath = true, onHit = false)
        status.explosionRadius = 6.0f
        status.explosionDamage = 24.0
    }
}
