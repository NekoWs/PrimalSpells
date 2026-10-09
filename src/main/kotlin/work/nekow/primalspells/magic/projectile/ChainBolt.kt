package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.ChainArc
import work.nekow.primalspells.magic.effect.visual.ChainBoltFx

/** 连锁电弧：15刻内分四次跃迁，3格索敌，默认约四次跃迁。 */
class ChainBolt : Projectile() {
    override val id = "chain_bolt"
    override val bodyColor = Color.ofInt(120, 210, 255)
    init {
        damageType = work.nekow.primalspells.magic.core.SpellDamageType.ELECTRICITY
        mana = 18.0; delay = 1; maxAge = 15; speed = 0.46875f; dragCoeff = 0f; hitRadius = 0.0; collisionEnabled = false
        status.damage = 4.0
        maxHitTargets = 0 // 跃迁次数由寿命决定，实体命中不能终结载体；穿刺卡仍可改为-1。
        effects += ChainArc()
        effects += ChainBoltFx()
    }
}
