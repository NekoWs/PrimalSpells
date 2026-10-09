package work.nekow.primalspells.loot

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.network.Filterable
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.WandManager
import work.nekow.primalspells.wand.WandVariant

/** 奖励是可直接使用、拆改的真实法杖；不生成带同一wandId的模板副本。 */
object SpellLoot {
    /** 服务端生成指定地点的成品杖；复用等级/变体工厂与唯一运行时实例。 */
    fun wand(profile: SpellLootProfile, random: RandomSource, elite: Boolean = false): ItemStack {
        val tier = profile.tier(profile.minTier + random.nextInt(profile.maxTier - profile.minTier + 1))
        val variant = if (random.nextDouble() < profile.chargedChance) WandVariant.CHARGED else WandVariant.NORMAL
        val stack = ModItems.wandStack(tier, variant)
        val wand = WandManager[WandManager.createWand(stack, random)]
        val recipes = if (elite && profile.eliteLoadouts.isNotEmpty()) profile.eliteLoadouts else profile.loadouts
        val recipe = recipes[random.nextInt(recipes.size)]
        // 数据包可给出长配方；成品必须能容纳真实卡牌，不丢弃尾部。
        wand.size = maxOf(wand.size, recipe.size)
        wand.magics += recipe.map { requireNotNull(MagicManager.create(it)) { "Unknown exploration spell: $it" } }
        wand.load()
        wand.status.mana = wand.mana
        stack.set(ModItems.WAND_SPELLS, recipe + List(wand.size - recipe.size) { "" })
        return stack
    }

    /** GLM调用：按地点追加散卡、概率成品杖；入门箱保证成品杖和操作手记。 */
    fun rewards(profile: SpellLootProfile, random: RandomSource): List<ItemStack> = buildList {
        if (random.nextDouble() < profile.wandChance) add(wand(profile, random))
        repeat(profile.cardCount) {
            add(ItemStack(ModItems.MAGICS.getValue(profile.cards[random.nextInt(profile.cards.size)]).get()))
        }
        if (profile == SpellLootProfile.STARTER) { add(handbook()); add(ItemStack(ModItems.SPELL_COMPASS.get())) }
    }

    private fun handbook() = ItemStack(Items.WRITTEN_BOOK).also { stack ->
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent(
            Filterable.passThrough("Field Notes"), "Primal Spells", 0,
            (1..4).map { Filterable.passThrough<Component>(Component.translatable("book.primalspells.exploration.$it")) }, true,
        ))
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("book.primalspells.exploration.title"))
    }
}
