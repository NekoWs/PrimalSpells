package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.entity.LivingEntity
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.HitType

/** 生物命中可重复释放；20刻定时和失效仍为一次性条件。 */
class AppendedTrigger : BaseEffect() {
    override val phase = EffectPhase.OVERRIDE
    override fun onHit(result: HitResult) {
        projectile.addedTriggers.forEach { trigger ->
            if (trigger.kind == DrawAction.Kind.HIT && result.type == HitType.ENTITY && result.entity is LivingEntity)
                PayloadRelease.emit(projectile, trigger.cards, result.pos, result.normal, repeatable = true)
            else if (trigger.kind == DrawAction.Kind.TIMER && !projectile.alive)
                PayloadRelease.emit(projectile, trigger.cards, result.pos, result.normal)
        }
    }
    override fun onTick() {
        if (status.age + 1 >= 20) projectile.addedTriggers.filter { it.kind == DrawAction.Kind.TIMER }
            .forEach { PayloadRelease.emit(projectile, it.cards, status.pos, direction()) }
    }
    override fun onDie() {
        projectile.addedTriggers.forEach {
            if (it.kind == DrawAction.Kind.EXPIRE) PayloadRelease.emit(projectile, it.cards, status.pos, direction())
            else it.cards.clear()
        }
    }
    override fun onExpire() = onDie()
    private fun direction() = if (status.velocity.lengthSquared() > 1e-8f) Vector3f(status.velocity).normalize()
        else projectile.launchDirection.toVector3f()
}
