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
        recoil = 0.05
        mana = 5.0
        // 自身4伤害；荷载费用由Wand整次结算，不写回本弹delay/recharge。
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