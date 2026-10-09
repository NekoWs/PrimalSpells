package work.nekow.primalspells.loot

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.common.loot.LootModifier

/** 数据包条件匹配结构箱子，追加奖励而不替换原版战利品。 */
class ExplorationLootModifier(conditions: Array<LootItemCondition>, priority: Int, val profile: String) : LootModifier(conditions, priority) {
    override fun doApply(generatedLoot: ObjectArrayList<ItemStack>, context: LootContext): ObjectArrayList<ItemStack> {
        SpellLootProfile.byKey(profile)?.let { generatedLoot.addAll(SpellLoot.rewards(it, context.random)) }
        return generatedLoot
    }

    override fun codec(): MapCodec<out IGlobalLootModifier> = CODEC

    companion object {
        val CODEC: MapCodec<ExplorationLootModifier> = RecordCodecBuilder.mapCodec { instance ->
            codecStart(instance).and(Codec.STRING.fieldOf("profile").forGetter<ExplorationLootModifier> { it.profile })
                .apply(instance, ::ExplorationLootModifier)
        }
    }
}
