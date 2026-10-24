package work.nekow.primalspells.magic.effect.lifecycle

import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime

/**
 * 延迟释放 payload —— 投射物**寿命耗尽的当刻**从 [DelayedCastProjectile.spellDirection] 取施法瞬间的朝向，
 * 在寿命结束位置把 payload 里的法术沿该方向释放（`delayed_cast` 的「拦截下一发法术」就是这样实现的）。
 *
 * 延迟荷载仍一次性释放，释放前清空；与可重复的生物命中触发分开。
 */
class DelayedTrigger(
    private val delayProjectile: DelayedCastProjectile
) : BaseEffect() {

    override fun onExpire() {
        val triggerSpell = projectile as? TriggerSpell ?: return
        val payload = triggerSpell.payload
        if (payload.isEmpty()) return

        val modifiers = payload.filterIsInstance<Modifier>().toList()
        val shots = payload.filterIsInstance<Projectile>().toList()
        val dir = delayProjectile.spellDirection // 施法时保存的朝向
        payload.clear()

        shots.forEach { shot ->
            work.nekow.primalspells.magic.core.SpellAssembly.apply(shot, modifiers)
            shot.caster = caster
            shot.wand = wand
            shot.position = Vector3d(status.pos) // 寿命结束位置
            shot.velocity = Vector3f(dir)        // 原施法朝向
            shot.spell()
            ProjectileRuntime.add(shot)
        }
    }
}
