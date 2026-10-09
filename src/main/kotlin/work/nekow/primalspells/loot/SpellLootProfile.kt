package work.nekow.primalspells.loot

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import work.nekow.primalspells.wand.WandTier
import work.nekow.primalspells.integration.PackRules

/** 数据包定义的地点奖励/配方；已生成的法杖不随重载重新掷点。 */
data class SpellLootProfile(
    val key: String, val minTier: Int, val maxTier: Int,
    val cards: List<String>, val loadouts: List<List<String>>,
    val structure: String = "primalspells:spell_sites/$key",
    val dimensions: Set<String> = emptySet(), val spawnWeight: Int = 35,
    val eliteChance: Double = 0.15, val wandChance: Double = 0.4,
    val chargedChance: Double = 0.125, val cardCount: Int = 2,
    val eliteLoadouts: List<List<String>> = emptyList(), val priority: Int = 0,
) {
    val structureTag = TagKey.create(Registries.STRUCTURE, Identifier.parse(structure))
    fun tier(value: Int) = WandTier.byLevel(value.coerceIn(minTier, maxTier))!!
    fun allows(level: ServerLevel) = dimensions.isEmpty() || level.dimension().identifier().toString() in dimensions
    companion object {
        val defaults = listOf(
            SpellLootProfile("starter", 1, 1, listOf("fireball", "torch", "bounce"),
        listOf(listOf("fireball"))),
            SpellLootProfile("ruins", 1, 2, listOf("fireball", "speed_boost", "bounce", "double_cast", "torch", "extended_lifetime", "damage_boost", "drone_bolt", "gradual_accelerate", "gradual_decelerate", "random_path", "reciprocate", "sine_wave"),
        listOf(listOf("fireball", "bounce"), listOf("fireball", "speed_boost"))),
            SpellLootProfile("raiders", 2, 3, listOf("homing", "energy_shield", "freeze_charge", "sawblade", "reduce_recharge", "chainsaw", "double_cast", "drone_swarm", "recoil_boost", "repulsion_field", "chain_bolt", "self_orbit"),
        listOf(listOf("fireball", "homing"), listOf("fireball", "freeze_charge"))),
            SpellLootProfile("arcane", 3, 4, listOf("trigger_fireball", "add_trigger", "add_timer", "add_expiration", "triple_cast", "omnidirectional_scatter", "mana_injection", "healing_bolt", "laser", "delayed_cast", "gravity", "gravity_field", "long_distance_cast", "teleport", "dormant_crystal", "crystal_detonation"),
        listOf(listOf("trigger_fireball", "small_explosion"), listOf("fireball", "homing", "freeze_charge"))),
            SpellLootProfile("nether", 4, 5, listOf("plasma_beam", "volatile", "large_explosion", "diffusion", "piercing", "recoil_damper", "quadruple_cast", "bounce_explosion", "explosion_projectile", "explosion_remove", "implosion"),
        listOf(listOf("trigger_fireball", "small_explosion"), listOf("fireball", "double_cast", "fireball", "speed_boost"))),
            SpellLootProfile("relic", 5, 6, listOf("concentrated_light", "black_hole", "divide_two", "divide_three", "divide_five", "divide_ten", "cast_all", "quintuple_cast", "quantum_entanglement", "bounce_duplicate"),
        listOf(listOf("concentrated_light"), listOf("trigger_fireball", "large_explosion")))
        )
        val STARTER get() = byKey("starter") ?: defaults.first { it.key == "starter" }
        val RUINS get() = byKey("ruins") ?: defaults.first { it.key == "ruins" }
        val RAIDERS get() = byKey("raiders") ?: defaults.first { it.key == "raiders" }
        val ARCANE get() = byKey("arcane") ?: defaults.first { it.key == "arcane" }
        val NETHER get() = byKey("nether") ?: defaults.first { it.key == "nether" }
        val RELIC get() = byKey("relic") ?: defaults.first { it.key == "relic" }
        val entries get() = PackRules.current.profiles.values.toList()
        fun byKey(key: String) = PackRules.current.profiles[key.removePrefix("primalspells:")]
        /** 只查询已载入结构元数据；多池重叠时高等级优先。 */
        fun at(level: ServerLevel, pos: BlockPos): SpellLootProfile? = entries.sortedWith(compareByDescending<SpellLootProfile> { it.priority }.thenByDescending { it.maxTier }).firstOrNull {
            it.spawnWeight > 0 && it.allows(level) && level.structureManager().getStructureWithPieceAt(pos, it.structureTag).isValid
        }
    }
}
