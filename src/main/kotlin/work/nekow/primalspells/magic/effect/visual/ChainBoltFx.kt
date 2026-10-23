package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.lifecycle.ChainArc
import work.nekow.primalspells.util.toVec3

/** 常驻像素魔弹与四枚蓄电齿：跟随最终位置，接近跃迁时收拢；电流连接只在真实跃迁时生成。 */
class ChainBoltFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var manager: ParticleManager? = null
    private var batch: ParticleBatch? = null
    private fun points(): List<Vec3> {
        val p = status.pos.toVec3()
        val progress = (status.age % ChainArc.JUMP_INTERVAL) / ChainArc.JUMP_INTERVAL
        val radius = (0.12 + 0.12 * (1.0-progress)) * projectile.areaScale
        return listOf(p, p.add(radius,0.0,0.0),p.add(-radius,0.0,0.0),p.add(0.0,radius,0.0),p.add(0.0,-radius,0.0))
    }
    override fun onTick() {
        val world = caster.level() as? ServerLevel ?: return
        val pm = manager ?: ParticleManager.of(world).also { manager=it }
        val particles = batch ?: ParticleBatch(pm).also { b ->
            b.addAll(pm.spawnAll(points().mapIndexed { i,p -> ParticleSpawnSpec().position(p)
                .color(if(i==0) Color.ofInt(232,251,255) else projectile.bodyColor)
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(if(i==0) 0.1875f else 0.125f,0.125f).glowing(true))
                .lifetime(-1).sizeCurve(CurveKey.at(0,0),CurveKey.at(2,1)) }));batch=b
        }
        particles.trackAll(points())
    }
    override fun onDie() {
        manager?.let { PixelVfx.retireBatch(it,batch,status.pos.toVec3(),4) };batch=null
    }
}
