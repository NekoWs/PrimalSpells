package work.nekow.primalspells.event

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.block.BreakBlockEvent
import net.neoforged.neoforge.common.damagesource.DamageContainer
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus
import work.nekow.primalspells.registry.ModEffects

/** 虚空保护与交互限制；诅咒生命代价使用原版伤害流程但不接受各类减免。 */
object VoidAndCurseEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun damage(event: LivingIncomingDamageEvent) {
        if (event.source.msgId == "primalspells_curse") {
            event.amount = event.originalAmount
            DamageContainer.Reduction.entries.forEach { event.addReductionModifier(it) { _, _ -> 0f } }
        } else if (VoidStatus.isPhased(event.entity) || VoidStatus.isPhased(event.source.entity)) event.isCanceled = true
    }
    @SubscribeEvent fun target(event: LivingChangeTargetEvent) {
        if (VoidStatus.isPhased(event.newAboutToBeSetTarget) || VoidStatus.isPhased(event.entity)) event.newAboutToBeSetTarget = null
    }
    @SubscribeEvent fun attack(event: AttackEntityEvent) {
        if (VoidStatus.isPhased(event.entity) || VoidStatus.isPhased(event.target)) event.isCanceled = true
    }
    @SubscribeEvent fun use(event: LivingEntityUseItemEvent.Start) { if (VoidStatus.isPhased(event.entity)) event.isCanceled = true }
    @SubscribeEvent fun useTick(event: LivingEntityUseItemEvent.Tick) { if (VoidStatus.isPhased(event.entity)) event.isCanceled = true }
    @SubscribeEvent fun release(event: LivingEntityUseItemEvent.Stop) { if (VoidStatus.isPhased(event.entity)) event.isCanceled = true }
    @SubscribeEvent fun rightItem(event: PlayerInteractEvent.RightClickItem) { if (VoidStatus.isPhased(event.entity)) event.isCanceled = true }
    @SubscribeEvent(priority = EventPriority.LOWEST) fun rightBlock(event: PlayerInteractEvent.RightClickBlock) { if (VoidStatus.isPhased(event.entity)) event.isCanceled = true }
    @SubscribeEvent(priority = EventPriority.LOWEST) fun rightEntity(event: PlayerInteractEvent.EntityInteract) {
        if (VoidStatus.isPhased(event.entity) || VoidStatus.isPhased(event.target)) event.isCanceled = true
    }
    @SubscribeEvent fun leftBlock(event: PlayerInteractEvent.LeftClickBlock) { if (VoidStatus.isPhased(event.entity)) event.isCanceled = true }
    @SubscribeEvent fun breakBlock(event: BreakBlockEvent) { if (VoidStatus.isPhased(event.player)) event.isCanceled = true }
    @SubscribeEvent fun dimension(event: PlayerEvent.PlayerChangedDimensionEvent) { event.entity.removeEffect(ModEffects.VOID_PHASE) }
    @SubscribeEvent fun logout(event: PlayerEvent.PlayerLoggedOutEvent) { event.entity.removeEffect(ModEffects.VOID_PHASE) }
}
