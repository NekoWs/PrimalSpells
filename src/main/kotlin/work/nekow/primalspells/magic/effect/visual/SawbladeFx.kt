package work.nekow.primalspells.magic.effect.visual

import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect

/** 锯齿金属盘持续旋转，位置由PD热更轨迹插值，死亡后短促收尾。 */
class SawbladeFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var group: ParticleGroup? = null
    override fun onActive() {
        val pm = wand.status.particleManager ?: return
        val g = pm.createGroup(net.minecraft.world.phys.Vec3(status.pos))
        repeat(40) { g.add(pm.create().position(net.minecraft.world.phys.Vec3(status.pos))
            .color(Color.TRANSPARENT).visual(ParticleVisual().style(ParticleStyle.SQUARE)
                .anisoWorld(0.125f,0.125f).glowing(true).lightLevel(3)).lifetime(-1).spawn()) }
        g.setVariable("presence",1.0).setVariable("cx",status.pos.x.toDouble()).setVariable("cy",status.pos.y.toDouble()).setVariable("cz",status.pos.z.toDouble())
            .setVariable("radius",projectile.areaScale).expression("""
                theta=i*2*PI/n+t*0.023;rim=1-floor(i/28);
                rad=(rim*(0.31+0.05*(i%2))+(1-rim)*0.16)*radius;
                x=cx+cos(theta)*rad;y=cy+sin(theta)*rad;z=cz;
                sc=min(1,t/60)*presence;r=0.70+rim*0.25;g=0.75+rim*0.20;b=0.78+rim*0.17;a=1;
            """.trimIndent())
        group = g
    }
    override fun onTick() {
        group?.setVariableLive("cx",status.pos.x.toString())
        group?.setVariableLive("cy",status.pos.y.toString())
        group?.setVariableLive("cz",status.pos.z.toString())
    }
    override fun onDie() { group?.setVariableInterpolated("presence","0",3,work.nekow.particledrawing.core.easing.EasingType.LINEAR)?.destroyAfter(4); group=null }
}
