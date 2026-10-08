package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.ChainArc
import work.nekow.primalspells.magic.effect.visual.Trajectory

/** 连锁电弧：首击四点伤害，首击后4刻导电窗口、3格索敌，默认共约四次命中，每跳乘 0.7。 */
class ChainBolt : Projectile() {
    override val id = "chain_bolt"
    override val bodyColor = Color.ofInt(120, 210, 255)
    init {
        mana = 18.0; delay = 1; maxAge = 4; speed = 0.46875f; dragCoeff = 0f; hitRadius = 0.2
        status.damage = 4.0
        effects += ChainArc()
        effects += Trajectory(bodyColor, length = 2.0)
    }
}
