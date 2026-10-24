package work.nekow.primalspells.magic.effect.lifecycle

import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.HitResult

/** 终结命中/20刻定时/失效三种一次性条件；存活反弹保留荷载，发射前清空。 */
class AppendedTrigger : BaseEffect() {
    override val phase = EffectPhase.OVERRIDE
    override fun onHit(result: HitResult) {
        if (projectile.alive) return
        projectile.addedTriggers.filter { it.kind != DrawAction.Kind.EXPIRE }.forEach { release(it, result.pos, result.normal) }
    }
    override fun onTick() {
        if (status.age + 1 >= 20) projectile.addedTriggers.filter { it.kind == DrawAction.Kind.TIMER }
            .forEach { release(it, status.pos, direction()) }
    }
    override fun onDie() {
        projectile.addedTriggers.filter { it.kind == DrawAction.Kind.EXPIRE || it.kind == DrawAction.Kind.HIT }
            .forEach { release(it, status.pos, direction()) }
    }
    override fun onExpire() = onDie()
    private fun direction() = if (status.velocity.lengthSquared() > 1e-8f) Vector3f(status.velocity).normalize()
        else projectile.launchDirection.toVector3f()
    private fun release(trigger: AppendedPayload, pos: Vector3d, dir: Vector3f) {
        val cards = trigger.cards.toList()
        trigger.cards.clear()
        val modifiers = cards.filterIsInstance<Modifier>()
        cards.filterIsInstance<Projectile>().forEach { shot ->
            SpellAssembly.apply(shot, modifiers)
            shot.caster = caster; shot.wand = wand
            shot.position = Vector3d(pos); shot.velocity = Vector3f(dir)
            shot.spell(); ProjectileRuntime.add(shot)
        }
    }
}
