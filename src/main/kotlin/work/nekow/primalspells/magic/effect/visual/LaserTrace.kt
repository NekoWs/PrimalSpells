package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 把真实扫掠线段画成短促白芯蓝边激光；只留短暂余辉，不用飞弹头部和爆散尾巴。 */
class LaserTrace : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var previous = Vec3.ZERO
    override fun onActive() { previous = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble()) }
    override fun onTick() {
        val end = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        val distance = previous.distanceTo(end)
        if (distance > 0.01) {
            val manager = ParticleManager.of(caster.level())
            val count = (distance / 0.0625).toInt().coerceIn(2, 192)
            for (layer in 0..1) {
                val specs = (0..count).map { i -> ParticleSpawnSpec().position(previous.lerp(end, i.toDouble()/count))
                    .color(if (layer == 0) Color.ofInt(63,172,246) else Color.ofInt(225,249,255))
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(if (layer == 0) 0.125f else 0.0625f,
                        if (layer == 0) 0.125f else 0.0625f).glowing(true).additive(false))
                    .lifetime(5).sizeCurve(CurveKey.at(0,0),CurveKey.at(0.5,1),CurveKey.at(1.5,1),CurveKey.at(5,0)) }
                manager.spawnAll(specs)
            }
        }
        previous = end
    }
}
