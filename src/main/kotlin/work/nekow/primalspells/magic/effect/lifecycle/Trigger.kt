package work.nekow.primalspells.magic.effect.lifecycle

import org.joml.Vector3d
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.HitResult

/**
 * 命中时释放触发法术的 payload（挂在 `TriggerFireball` 这类触发投射物上）。
 *
 * payload 语义：
 * - `Wand.drawChain` 在施法时按 `triggerCast` 预算**递归预留一条独立法术链**，把抽到的法术（已克隆、尚未施放）
 *   装进 payload；若抽到的是触发法术，会递归填充它自己的 payload，
 *   于是形成 `触发A( 触发B( 火球 ) )` 这样的链式结构。
 * - 命中（实体或方块）时，把 payload 里的投射物**就地施放**：起点 = 命中点，方向 = 命中法线
 *   （实体命中时是反弹方向），并把 payload 里的修饰器效果注入这些投射物。
 * - **释放后立即清空 payload**：payload 持有的是同一批实例，重复释放会让效果被反复注入并叠加，
 *   所以触发法术是一次性的 —— 穿刺（`maxHitTargets = -1`）导致多次命中时不会重复释放。
 */
class Trigger : BaseEffect() {
    override fun onHit(result: HitResult) {
        // 穿刺只保留本体伤害；荷载一直带到方块命中。
        if (projectile.maxHitTargets < 0 && result.type == work.nekow.primalspells.util.HitType.ENTITY) return
        val triggerSpell = projectile as? TriggerSpell ?: return
        val payload = triggerSpell.payload
        if (payload.isEmpty()) return

        val modifiers = payload.filterIsInstance<Modifier>().toList()
        val shots = payload.filterIsInstance<Projectile>().toList()
        // 先清空，保证即使施放过程中出现异常也不会重复释放同一批实例
        payload.clear()

        shots.forEach { shot ->
            // 每枚子弹拿一份独立克隆的效果实例，避免多枚子弹共享同一个效果对象
            work.nekow.primalspells.magic.core.SpellAssembly.apply(shot, modifiers)
            shot.caster = caster
            shot.wand = wand
            shot.position = Vector3d(result.pos)
            shot.velocity = org.joml.Vector3f(result.normal)
            shot.spell()
            MagicManager.add(shot)
        }
    }
}
