package work.nekow.primalspells.wand

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import work.nekow.primalspells.item.WandItem

/** Zeta只读取实际随身法杖的配置，不抽取或改写被引用法杖的牌堆与资源。 */
internal object WandReferences {
    fun others(caster: Entity?, current: Wand): List<Wand> {
        val stacks = when (caster) {
            is Player -> (0 until caster.inventory.containerSize).map { caster.inventory.getItem(it) } + caster.offhandItem
            is LivingEntity -> listOf(caster.mainHandItem, caster.offhandItem)
            else -> emptyList()
        }
        return stacks.filter { it.item is WandItem }.mapNotNull(WandItem::getWand)
            .filter { it.id != current.id }.distinctBy { it.id }
    }
}
