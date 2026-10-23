package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.util.toVec3
import kotlin.math.*

/** 粗像素棱片向白金核心聚焦，再接连续光束与短暂余辉；全程实色硬边。 */
class ConcentratedLightFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var manager: ParticleManager? = null
    private var charge: ParticleBatch? = null
    private var previous = Vec3.ZERO
    private var chargeRest = emptyList<Vec3>()
    private fun point() = status.pos.toVec3()
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
                // 主芯承担真实照明，其余棱片保持全亮，避免几十颗满级光源同时开关。
                .glowing(true).lightLevel(if(i==0)15 else 0)
                .lifetime(-1).sizeCurve(CurveKey.at(0,0),CurveKey.at(2,1,EasingType.EASE_OUT))
                .lightCurve(CurveKey.at(0,0),CurveKey.at(2,0.25),
                    CurveKey.at(ConcentratedLight.CHARGE_TICKS,1,EasingType.EASE_IN))
        }))
            chargeRest = points(1.0)
            // 位置只由track接管；出生尺寸仍由PD寿命曲线逐帧播放。
        }
    }
    override fun onTick() {
        val pm=manager ?: return
        if(projectile.awaitingRelease) {
            val progress = (status.age.toDouble() / ConcentratedLight.CHARGE_TICKS).coerceIn(0.0, 1.0)
            charge?.trackAll(points(progress * progress))
            previous=point();return
        }
        if(charge!=null) {
            // 阵体留在部署点承受反冲：先后退张开，再回弹熄灭，不追着高速光束移动。
            releaseRecoil(pm, charge!!);charge=null
            releaseFlash(pm)
        }
        val end=point()
        val samples=FlightTrace.sample(projectile,previous,end,0.125)
        if(samples.isNotEmpty()) {
            // 白色光脊与金色侧线在空间上分开，实色片元不共面覆盖。
            val specs=(0..2).flatMap { layer -> samples.mapIndexed { i, sample ->
                val side=sample.direction.cross(Vec3(0.0,1.0,0.0)).let{if(it.lengthSqr()<1e-8)Vec3(1.0,0.0,0.0)else it.normalize()}
                val size=if(layer==0)0.25f else 0.09375f
                val offset=if(layer==0)Vec3.ZERO else side.scale(if(layer==1)0.1875 else -0.1875)
                val life=12+i%3
                ParticleSpawnSpec().position(sample.position.add(offset))
                    .color(if(layer==0)Color.ofInt(255,253,233)else Color.ofInt(255,204,88))
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(size,size).glowing(true).additive(false))
                    .glowing(true).lightLevel(if(layer==0 && i%8==0)12 else 0)
                    .lifetime(life).sizeCurve(CurveKey.at(0,0),CurveKey.at(0.5,1),CurveKey.at(2,1),CurveKey.at(life,0,EasingType.EASE_OUT))
                    .lightCurve(CurveKey.at(0,0),CurveKey.at(1,0.8),CurveKey.at(3,0.5),CurveKey.at(life,0))
            } }
            specs.chunked(256).forEach { pm.spawnAll(it) }
        }
        previous=end
    }
    override fun onDie() {
        charge?.handles()?.forEach { it.lightIntensity(0f,4) }
        manager?.let { PixelVfx.retireBatch(it,charge,point(),4) };charge=null
    }

    private fun releaseRecoil(pm: ParticleManager, batch: ParticleBatch) {
        val origin = previous
        val forward = projectile.launchDirection
        val handles = batch.handles().toList()
        handles.forEachIndexed { i, handle ->
            handle.lightIntensity(0f,5,EasingType.EASE_OUT)
            val rest = chargeRest[i]
            val offset = rest.subtract(origin)
            val radial = offset.subtract(forward.scale(offset.dot(forward)))
            handle.move(rest.add(forward.scale(-0.12)).add(radial.scale(0.8)), 2, EasingType.EASE_OUT)
        }
        // Deferred只编排两拍；位置、接续和尺寸求值均由PD完成。
        work.nekow.primalspells.util.Deferred.after(pm.level, 1) {
            handles.forEachIndexed { i, handle -> handle.move(chargeRest[i], 4, EasingType.EASE_OUT) }
            PixelVfx.retireBatch(pm, batch, origin, 4)
        }
    }

    private fun releaseFlash(pm: ParticleManager) {
        val forward=projectile.launchDirection
        val side=forward.cross(Vec3(0.0,1.0,0.0)).let{if(it.lengthSqr()<1e-8)Vec3(1.0,0.0,0.0)else it.normalize()}
        val up=side.cross(forward).normalize()
        val velocities=arrayListOf<Vec3>()
        val specs=(0 until 16).map { i ->
            val angle=i*PI/8
            val radial=side.scale(cos(angle)).add(up.scale(sin(angle)))
            // 短促的反向金色排光，与向前的白光脊形成发射反冲。
            velocities+=forward.scale(-0.025).add(radial.scale(0.018))
            val life=8+i%4
            ParticleSpawnSpec().position(previous.add(radial.scale(0.1)))
                .color(if(i%4==0)Color.ofInt(255,253,233)else Color.ofInt(255,214,112))
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.0625f,0.0625f).glowing(true).additive(false))
                .glowing(true).lightLevel(if(i==0)14 else 0)
                .lifetime(life).sizeCurve(CurveKey.at(0,0),CurveKey.at(1,1),CurveKey.at(3,1),CurveKey.at(life,0,EasingType.EASE_IN_OUT))
                .lightCurve(CurveKey.at(0,0),CurveKey.at(1,1),CurveKey.at(3,0.65),CurveKey.at(life,0))
        }
        ParticleBatch(pm).also{it.addAll(pm.spawnAll(specs));it.setVelocityAll(velocities)}
    }
}
