package work.nekow.primalspells.magic.effect.lifecycle

import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile

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

        PayloadRelease.emit(projectile, payload, Vector3d(status.pos), Vector3f(delayProjectile.spellDirection))
    }
}
