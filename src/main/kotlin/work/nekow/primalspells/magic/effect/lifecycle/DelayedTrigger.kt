package work.nekow.primalspells.magic.effect.lifecycle

import org.joml.Vector3f
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile
import work.nekow.primalspells.magic.projectile.Projectile

/**
 * 延迟释放 payload —— 投射物**寿命耗尽的当刻**从 [DelayedCastProjectile.spellDirection] 取施法瞬间的朝向，
 * 在寿命结束位置把 payload 里的法术沿该方向释放（`delayed_cast` 的「拦截下一发法术」就是这样实现的）。
 *
 * 与 [Trigger] 保持一致：payload 一次性释放，释放前清空，避免同一批实例被重复注入效果。
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
            work.nekow.primalspells.magic.core.SpellAssembly.inherit(projectile, shot)
            work.nekow.primalspells.magic.core.SpellAssembly.apply(shot, modifiers)
            shot.caster = caster
            shot.wand = wand
            shot.position = Vector3f(status.pos) // 寿命结束位置
            shot.velocity = Vector3f(dir)        // 原施法朝向
            shot.spell()
            MagicManager.add(shot)
        }
    }
}
