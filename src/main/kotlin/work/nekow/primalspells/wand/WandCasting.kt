package work.nekow.primalspells.wand

import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.integration.SpellEvents
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3

/** 服务端施法入口：门槛与事件、真实发射、事务结算和成功表现通知。 */
internal object WandCasting {
    /**
     * 进行一次施法：检查 delay/recharge 是否就绪，然后抽牌并执行。
     *
     * @param caster 施法者实体
     * @return 完成抽牌事务时为true；装备预热、冷却或事件取消时为false
     */
    fun cast(wand: Wand, caster: Entity): Boolean {
        // 持有者记录入口 2/2：任何实体主动施法都算一次「确认持有」。
        // 手持法杖的**生物**不走 WandItem.inventoryTick 的被动驱动路径，只能在这里补记，
        // 否则 WandManager 的回收判定会把它当成无人持有；玩家两处都会记，重复写同一张表无副作用。
        WandManager.markHeld(WandID(wand.id), caster.uuid, caster.level().gameTime)
        wand.status.failedReason.clear()
        if (!WandCastingFx.ready(caster, wand)) {
            wand.status.failedReason.add("Equip charge")
            return false
        }
        if (wand.status.delay > 0.0) {
            wand.status.failedReason.add("Delay ${wand.status.delay.toInt()}/${wand.delayTicks().toInt()}")
            return false
        }
        if (wand.status.recharge > 0.0) {
            wand.status.failedReason.add("Recharge ${wand.status.recharge.toInt()}/${wand.rechargeTicks().toInt()}")
            return false
        }
        if (NeoForge.EVENT_BUS.post(SpellEvents.BeforeCast(caster, wand)).isCanceled) return false
        val manaBefore = wand.status.mana
        wand.status.delay = 0.0
        wand.status.recharge = 0.0
        val transaction = CastTransaction(wand)
        val paid = transaction.pay(WandDeck(wand).draw(wand.cast))
        launch(wand, caster, paid)
        transaction.finish()
        wand.status.lastDelay = wand.status.delay
        wand.status.lastRecharge = wand.status.recharge
        WandCastingFx.cooldownsChanged(caster, wand)
        NeoForge.EVENT_BUS.post(SpellEvents.AfterCast(caster, wand.id, manaBefore, wand.status.mana, wand.status.delay, wand.status.recharge))
        return true
    }

    /** 各链的修正只装配到本链投射物；荷载留到触发时独立装配。 */
    private fun launch(wand: Wand, caster: Entity, paid: List<Magic>) {
        val modifiers = paid.filterIsInstance<Modifier>()
        var castVisualPlayed = false
        for (p in paid.filterIsInstance<Projectile>()) {
            SpellAssembly.apply(p, modifiers)
            p.caster = caster
            p.wand = wand
            p.position = WandCastingFx.origin(caster, wand).toPosition()
            p.velocity = WandAim.direction(caster, p.position.toVec3()).toVector3f()
            p.castFromWand = true
            p.spell()
            p.queueCastRecoil()
            if (!castVisualPlayed) {
                WandCastingFx.cast(caster, wand)
                castVisualPlayed = true
            }
            ProjectileRuntime.add(p)
        }
    }
}
