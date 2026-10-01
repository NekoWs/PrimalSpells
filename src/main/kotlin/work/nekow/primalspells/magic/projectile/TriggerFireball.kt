package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.Trajectory
import work.nekow.primalspells.magic.effect.lifecycle.Trigger

class TriggerFireball: Projectile(), TriggerSpell {
    override val id = "trigger_fireball"
    override val bodyColor = Color.ORANGE

    override var triggerCast = 1
    override var payload = arrayListOf<Magic>()

    init {
        mana = 5.0
        // 定价见 docs/design/combat-pricing.md §2：自身 4 伤害 / 6 tick —— 比火球低一档，
        // 因为荷载的牌会在 loadPayload 时把自己的 delay/recharge 累加到本弹上（Wand.kt）。
        delay = 0
        recharge = 0
        maxAge = 200
        status.damage = 4.0
        hitRadius = 0.2
        effects += Trajectory(Color.ORANGE)
        effects += Hurt()
        effects += Trigger()
    }
}