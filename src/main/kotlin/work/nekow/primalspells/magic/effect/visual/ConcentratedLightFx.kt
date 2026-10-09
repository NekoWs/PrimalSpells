package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import kotlin.math.*

/** 粗像素棱片向白金核心聚焦，再接连续光束与短暂余辉；全程实色硬边。 */
class ConcentratedLightFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var manager: ParticleManager? = null
    private var charge: ParticleBatch? = null
    private var previous = Vec3.ZERO
    private fun point() = Vec3(status.pos)
    private fun points(progress: Double = (status.age.toDouble()/ConcentratedLight.CHARGE_TICKS).coerceIn(0.0,1.0)): List<Vec3> {
        val forward = projectile.launchDirection
        val side = forward.cross(Vec3(0.0,1.0,0.0)).let { if(it.lengthSqr()<1e-8) Vec3(1.0,0.0,0.0) else it.normalize() }
        val up = side.cross(forward).normalize()
        val center = point()
        fun local(x:Double,y:Double,z:Double)=center.add(side.scale(x * 0.5)).add(up.scale(y * 0.5)).add(forward.scale(z * 0.5))
        val out=arrayListOf(center)
        // 两层方晶镜阵相向合拢；双菱框和轴向光矛直接指示已锁定的发射方向。
        for(layer in 0..1) {
            val radius=if(layer==0)0.52-progress*0.36 else 0.37-progress*0.25
            val depth=(if(layer==0)-1 else 1)*(0.36-progress*0.25)
            val angle=(if(layer==0)1 else -1)*progress*0.18
            val corners=listOf(0.0 to radius,radius to 0.0,0.0 to -radius,-radius to 0.0)
            for(edge in 0..3)for(i in 0..4) {
                val a=corners[edge];val b=corners[(edge+1)%4];val t=(i+0.5)/5
                val x=a.first+(b.first-a.first)*t;val y=a.second+(b.second-a.second)*t
                out+=local(x*cos(angle)-y*sin(angle),x*sin(angle)+y*cos(angle),depth)
            }
        }
        for(i in 0..8)out+=local(0.0,0.0,-0.12+i*(0.025+progress*0.055))
        for(arm in 0..3)for(row in 0..3) {
            val angle=arm*PI/2+PI/4
            val radius=0.48-progress*0.30+row*0.04
            out+=local(cos(angle)*radius,sin(angle)*radius,-0.12-row*0.04*(1-progress))
        }
        return out
    }

    override fun onActive() {
        val pm = ParticleManager.of(caster.level());manager=pm;previous=point()
        charge = ParticleBatch(pm).also { batch -> batch.addAll(pm.spawnAll(points().mapIndexed { i,p ->
            ParticleSpawnSpec().position(p).color(if(i==0 || i in 41..49 || i%5==0) Color.ofInt(255,250,220) else Color.ofInt(246,193,66))
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(if(i==0) 0.125f else 0.0625f,if(i==0) 0.125f else 0.0625f)
                    .glowing(true).additive(false))
                .lifetime(-1).sizeCurve(CurveKey.at(0,0),CurveKey.at(3,1,EasingType.EASE_OUT))
        }))
            val targets = points(1.0)
            batch.handles().forEachIndexed { i, handle -> handle.move(targets[i], ConcentratedLight.CHARGE_TICKS, EasingType.EASE_IN) }
        }
    }
    override fun onTick() {
        val pm=manager ?: return
        if(projectile.awaitingRelease) { previous=point();return }
        if(charge!=null) {
            // 蓄能棱片在最后的枪口位置熄灭，不跟着首刻高速位移跳到光束前端。
            PixelVfx.retireBatch(pm,charge,previous,4);charge=null
            releaseFlash(pm)
        }
        val end=point();val distance=previous.distanceTo(end)
        if(distance>0.001) {
            val count=(distance/0.125).toInt().coerceIn(2,192)
            val direction=end.subtract(previous).normalize()
            val side=direction.cross(Vec3(0.0,1.0,0.0)).let{if(it.lengthSqr()<1e-8)Vec3(1.0,0.0,0.0)else it.normalize()}
            // 白色光脊与金色侧线在空间上分开，实色片元不共面覆盖。
            val specs=(0..2).flatMap { layer -> (0..count).map { i ->
                val size=if(layer==0)0.25f else 0.09375f
                val offset=if(layer==0)Vec3.ZERO else side.scale(if(layer==1)0.1875 else -0.1875)
                val life=12+i%3
                ParticleSpawnSpec().position(previous.lerp(end,i.toDouble()/count).add(offset))
                    .color(if(layer==0)Color.ofInt(255,253,233)else Color.ofInt(255,204,88))
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(size,size).glowing(true).additive(false))
                    .lifetime(life).sizeCurve(CurveKey.at(0,0),CurveKey.at(0.5,1),CurveKey.at(2,1),CurveKey.at(life,0,EasingType.EASE_OUT))
            } }
            specs.chunked(256).forEach { pm.spawnAll(it) }
        }
        previous=end
    }
    override fun onDie() {
        manager?.let { PixelVfx.retireBatch(it,charge,point(),4) };charge=null
    }

    private fun releaseFlash(pm: ParticleManager) {
        val forward=Vec3(status.velocity).normalize()
        val side=forward.cross(Vec3(0.0,1.0,0.0)).let{if(it.lengthSqr()<1e-8)Vec3(1.0,0.0,0.0)else it.normalize()}
        val up=side.cross(forward).normalize()
        val velocities=arrayListOf<Vec3>()
        val specs=(0 until 16).map { i ->
            val angle=i*PI/8
            val radial=side.scale(cos(angle)).add(up.scale(sin(angle)))
            // 沿射线释放，避免通用爆散的向上漂移把近景片元快速甩出视野。
            velocities+=forward.scale(0.065).add(radial.scale(0.012))
            val life=12+i%4
            ParticleSpawnSpec().position(previous.add(radial.scale(0.1)))
                .color(if(i%4==0)Color.ofInt(255,253,233)else Color.ofInt(255,214,112))
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.125f,0.125f).glowing(true).additive(false))
                .lifetime(life).sizeCurve(CurveKey.at(0,0),CurveKey.at(1,1),CurveKey.at(3,1),CurveKey.at(life,0,EasingType.EASE_IN_OUT))
        }
        ParticleBatch(pm).also{it.addAll(pm.spawnAll(specs));it.setVelocityAll(velocities)}
    }
}
