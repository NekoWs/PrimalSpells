package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.Trajectory

class Fireball: Projectile() {
    override val id = "fireball"
    override val bodyColor = Color.ORANGE

    init {
        mana = 3.0
        // 定价见 docs/design/combat-pricing.md §2：单体 10 DPS = 5 伤害 / 10 tick 间隔。
        // 单发伤害与间隔必须一起改，只抬伤害会直接超模。
        delay = 0
        recharge = 0
        maxAge = 200
        status.damage = 0.5
        hitRadius = 0.2
        effects += Trajectory(Color.ORANGE)
        effects += Hurt()
        speed = 0.5f
    }
}