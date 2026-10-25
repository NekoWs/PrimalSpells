package work.nekow.primalspells.magic.modifier

import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.Modifier

/** 持杖时环绕的实色火种；真实动态照明使用PD发光粒子的公开API。 */
class Torch : Modifier() {
    override val id = "torch"
    override var needInventory = true
    private var group: ParticleGroup? = null
    init { mana = 0.0 }
    override fun onInventoryTick() {
        if (wand.loadedMagics.firstOrNull { it.id == id } !== this || group != null) return
        val pm = wand.status.particleManager ?: return
        val g = pm.createGroup(caster.position())
        repeat(5) { i -> g.add(pm.create().position(caster.position()).color(Color.TRANSPARENT)
            // 发光索引由Builder标志登记，visual仅描述片元外观。
            .glowing(true).lightLevel(if(i==0)14 else 0)
            .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.15625f,0.15625f)
                .glowing(true).lightLevel(if(i==0)14 else 0)).lifetime(-1).spawn()) }
        g.setVariable("presence",1.0).defineEntity("holder",caster.uuid).expression("""
            theta=t*0.0009;h=sin(t*0.008+i*2)*0.025;
            x=get_entity_x(holder)+cos(theta)*0.8+(i%2)*0.065;
            y=get_entity_y(holder)+1.25+i*0.045+h;
            z=get_entity_z(holder)+sin(theta)*0.8;
            sc=min(1,t/150)*(1-i*0.12)*presence;r=1;g=0.35+i*0.10;b=0.07+i*0.08;a=1;
        """.trimIndent())
        group=g
    }
    override fun onPassiveIdle() = retire()
    override fun onUnloaded() = retire()
    private fun retire() {
        // 六刻缓入缓出使最后一个可见采样已接近零，避免剩余约1/3光强直接熄灭。
        group?.setVariableInterpolated("presence", "0", 6, work.nekow.particledrawing.core.easing.EasingType.EASE_IN_OUT)
            ?.destroyAfter(7)
        group = null
    }
}
