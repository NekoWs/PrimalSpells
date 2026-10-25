package work.nekow.primalspells.item.component

import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.registry.ModItems

/** 实体法术卡与物品的转换边界；缺少次数组件的旧卡按满次数读取，0明确表示耗尽。 */
object SpellCardData {
    fun read(stack: ItemStack): Magic? {
        val card = ModItems.spellIdOf(stack.item)?.let(SpellRegistry::create) ?: return null
        val uses = stack.get(ModItems.SPELL_USES)
        if (uses != null && uses >= 0) card.remainingUses = uses
        return card
    }

    fun stack(card: Magic): ItemStack = stack(card.id, if (card.maxUses > 0) card.remainingUses else -1)

    fun stack(id: String, uses: Int = -1): ItemStack {
        val item = ModItems.spellItem(id) ?: return ItemStack.EMPTY
        return ItemStack(item).also { if (uses >= 0) it.set(ModItems.SPELL_USES, uses) }
    }
}
