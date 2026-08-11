package work.nekow.primalspells.magic.effect

import org.joml.Vector3f
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.magic.Projectile
import work.nekow.primalspells.magic.projectile.DelayedCastProjectile
import work.nekow.primalspells.magic.Revise
import work.nekow.primalspells.magic.TriggerSpell

/**
 * 延迟触发效果 —— 投射物死亡时从 [DelayedCastProjectile.spellDirection]
 * 读取施法朝向并释放 payload。
 */
class DelayedTrigger(
    private val delayProjectile: DelayedCastProjectile
) : BaseEffect() {

    override fun onDie() {
        val triggerSpell = projectile as? TriggerSpell ?: return
        val payload = triggerSpell.payload
        val dir = delayProjectile.spellDirection // 施法时保存的朝向

        val revises = payload.filterIsInstance<Revise>().toList()
        revises.forEach { payload.remove(it) }

        payload.filterIsInstance<Projectile>().forEach { magic ->
            val effects = revises.flatMap { it.effects() }
            magic.effects += effects
            magic.caster = caster
            magic.wand = wand
            magic.position = Vector3f(status.pos) // 死亡位置
            magic.velocity = Vector3f(dir) // 原施法朝向
            magic.spell()
            MagicManager.add(magic)
        }
    }
}
