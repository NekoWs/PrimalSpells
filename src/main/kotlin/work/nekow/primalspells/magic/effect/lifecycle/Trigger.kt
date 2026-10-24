package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.entity.LivingEntity
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.HitResult
import work.nekow.primalspells.util.HitType

/**
 * 每次有效生物命中都复制并释放独立荷载；穿刺保留模板直到载体结束。
 * 方块、护盾反射、自然到期和其它死亡不会释放命中荷载。
 */
class Trigger : BaseEffect() {
    override fun onHit(result: HitResult) {
        if (result.type != HitType.ENTITY || result.entity !is LivingEntity) return
        val payload = (projectile as? TriggerSpell)?.payload ?: return
        PayloadRelease.emit(projectile, payload, result.pos, result.normal, repeatable = true)
    }

    override fun onExpire() { (projectile as? TriggerSpell)?.payload?.clear() }
    override fun onDie() { (projectile as? TriggerSpell)?.payload?.clear() }
}
