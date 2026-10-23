package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.level.ServerLevel
import work.nekow.particledrawing.api.CurveKey
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.util.pdParticles
import work.nekow.primalspells.util.spellParticle
import work.nekow.primalspells.util.spellVisual
import work.nekow.primalspells.util.toVec3

/** 每枚弹体仅一个光源，尾迹/碎片不登记光源；插值和光强过渡由PD处理。 */
class SpellLightFx : BaseEffect() {
    override val phase = EffectPhase.VISUAL
    private var source: ParticleHandle? = null
    private var manager: ParticleManager? = null

    override fun onActive() {
        if (!isStackPrimary) return
        val level = caster.level() as? ServerLevel ?: return
        val pm = wand.pdParticles(level)
        manager = pm
        source = pm.spellParticle()
            .position(status.pos.toVec3()).color(projectile.bodyColor)
            .visual(spellVisual().anisoWorld(0.0625f, 0.0625f).lightLevel(14))
            .lightLevel(14).lifetime(-1)
            .lightCurve(CurveKey.at(0, 0), CurveKey.at(2, 1, EasingType.EASE_OUT))
            .spawn()
    }

    override fun onTick() {
        source?.track(status.pos.toVec3())
    }

    override fun onDie() {
        val handle = source ?: return
        source = null
        handle.track(status.pos.toVec3())
        handle.lightIntensity(0f, 4, EasingType.EASE_IN_OUT)
        handle.resize(0f, 4, EasingType.EASE_IN_OUT)
        Deferred.after(checkNotNull(manager).level, 6) { handle.remove() }
    }
}
