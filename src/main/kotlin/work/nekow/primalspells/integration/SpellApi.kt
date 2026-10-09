package work.nekow.primalspells.integration

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.wand.WandManager

/** 公共入口v1；非玩家同样消费真实法杖的牌堆/MP/冷却，不另造结算器。 */
object SpellApi {
    const val VERSION = 1
    @JvmStatic fun cast(caster: Entity, stack: ItemStack): Boolean {
        val level = caster.level() as? ServerLevel ?: return false
        if (!level.server.isSameThread || !caster.isAlive || stack.item !is WandItem) return false
        WandManager.createWand(stack)
        val wand = WandItem.getWand(stack) ?: return false
        wand.tick(caster)
        return wand.spell(caster)
    }
}
