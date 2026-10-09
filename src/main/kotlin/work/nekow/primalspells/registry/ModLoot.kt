package work.nekow.primalspells.registry

import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.loot.ExplorationLootModifier

/** 战利品扩展编解码器；条件与生效箱子由数据包配置。 */
object ModLoot {
    val SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, PrimalSpells.MODID)
    val EXPLORATION = SERIALIZERS.register("exploration") { _ -> ExplorationLootModifier.CODEC }
}
