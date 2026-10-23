package work.nekow.primalspells.event

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent
import work.nekow.primalspells.magic.effect.lifecycle.ShieldGuard

/**
 * 「原版投射物 → 能量护盾」的**命中兜底**接线（第 40 轮；用户口径：护盾能够防御/反弹原版的投射物）。
 *
 * 为什么只有每 tick 的扫场不够：原版箭矢 3 格/tick、火球更快，而护盾的扫场跑在**持有者的实体 tick** 里
 * —— 弹可能在同一 tick 内就贴到身上（与自家 `laser` 是同一个理由，见 `EnergyShield.absorbProjectile`）。
 * 这里在**真正结算命中之前**再问一次护盾：挡下就取消这次命中，投射物已经被反弹、继续飞。
 *
 * 事件是原版所有投射物通用的（也包含别的模组按原版 `Projectile` 写的弹），
 * 所以这一条同时把"第三方模组的箭"也覆盖了；不认的弹由 `EnergyShield.vanillaStrength` 返回 `null` 放过。
 */
object ProjectileShieldHandler {

    @SubscribeEvent
    fun onProjectileImpact(event: ProjectileImpactEvent) {
        val hit = event.rayTraceResult as? EntityHitResult ?: return
        val entity = hit.entity as? LivingEntity ?: return
        val level = entity.level() as? ServerLevel ?: return
        if (ShieldGuard.absorbVanillaProjectile(level, entity, event.projectile)) {
            event.isCanceled = true
        }
    }
}
