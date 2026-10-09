package work.nekow.primalspells.loot

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import work.nekow.primalspells.wand.WandTier

/** 探索地点决定法术用途与法杖等级；战斗配方同时用于敌人持杖和箱子成品。 */
enum class SpellLootProfile(
    val key: String, val minTier: Int, val maxTier: Int,
    val cards: List<String>, val loadouts: List<List<String>>,
) {
    STARTER("starter", 1, 1, listOf("fireball", "torch", "bounce"),
        listOf(listOf("fireball"))),
    RUINS("ruins", 1, 2, listOf("fireball", "speed_boost", "bounce", "double_cast", "torch", "extended_lifetime", "damage_boost", "drone_bolt", "gradual_accelerate", "gradual_decelerate", "random_path", "reciprocate", "sine_wave"),
        listOf(listOf("fireball", "bounce"), listOf("fireball", "speed_boost"))),
    RAIDERS("raiders", 2, 3, listOf("homing", "energy_shield", "freeze_charge", "sawblade", "reduce_recharge", "chainsaw", "double_cast", "drone_swarm", "recoil_boost", "repulsion_field", "chain_bolt", "self_orbit"),
        listOf(listOf("fireball", "homing"), listOf("fireball", "freeze_charge"))),
    ARCANE("arcane", 3, 4, listOf("trigger_fireball", "add_trigger", "add_timer", "add_expiration", "triple_cast", "omnidirectional_scatter", "mana_injection", "healing_bolt", "laser", "delayed_cast", "gravity", "gravity_field", "long_distance_cast", "teleport", "dormant_crystal", "crystal_detonation"),
        listOf(listOf("trigger_fireball", "small_explosion"), listOf("fireball", "homing", "freeze_charge"))),
    NETHER("nether", 4, 5, listOf("plasma_beam", "volatile", "large_explosion", "diffusion", "piercing", "recoil_damper", "quadruple_cast", "bounce_explosion", "explosion_projectile", "explosion_remove", "implosion"),
        listOf(listOf("trigger_fireball", "small_explosion"), listOf("fireball", "double_cast", "fireball", "speed_boost"))),
    RELIC("relic", 5, 6, listOf("concentrated_light", "black_hole", "divide_two", "divide_three", "divide_five", "divide_ten", "cast_all", "quintuple_cast", "quantum_entanglement", "bounce_duplicate"),
        listOf(listOf("concentrated_light"), listOf("trigger_fireball", "large_explosion")));

    val structureTag = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("primalspells", "spell_sites/$key"))
    /** 获取地点允许的等级；生成奖励不使用调试杖。 */
    fun tier(value: Int) = WandTier.byLevel(value.coerceIn(minTier, maxTier))!!

    companion object {
        /** GLM配置键解析；未知配置不产生奖励。 */
        fun byKey(key: String) = entries.firstOrNull { it.key == key }

        /** 只查询已载入结构元数据，不生成新区块；重叠时以更危险地点为准。 */
        fun at(level: ServerLevel, pos: BlockPos): SpellLootProfile? = entries.reversed().firstOrNull {
            it != STARTER && level.structureManager().getStructureWithPieceAt(pos, it.structureTag).isValid
        }
    }
}
