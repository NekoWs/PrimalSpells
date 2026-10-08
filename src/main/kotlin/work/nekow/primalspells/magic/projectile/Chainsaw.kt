package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.ChainsawFx
import work.nekow.primalspells.util.Lore

/** 廉价的近距切割脉冲；在抽牌顺序中的当前位置清除施放延迟，缩短3刻充能。 */
class Chainsaw : Projectile() {
    override val id = "chainsaw"
    override val bodyColor = Color.ofInt(255, 230, 139)
    override val resetsCastDelay = true
    init {
        mana = 1.0
        recharge = -3
        maxAge = 1
        speed = 1.2f
        dragCoeff = 0f
        hitRadius = 0.45
        status.damage = 2.0
        effects += Hurt()
        effects += ChainsawFx()
    }
    override fun initLore() { super.initLore(); lore(Lore.DESCRIPTION) }
}
