package work.nekow.primalspells.integration

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.AddServerReloadListenersEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import work.nekow.primalspells.loot.SpellLootProfile
import work.nekow.primalspells.magic.core.MagicManager

/** 原子重载：任何非法条目使本次重载失败，不能把半份配置发布给运行中的世界。 */
object PackRules {
    data class Tier(val mana: Double, val charge: Double, val delay: Int, val recharge: Int, val size: Int, val casts: List<Int>)
    data class Rules(val profiles: Map<String, SpellLootProfile>, val tiers: Map<Int, Tier>)
    @Volatile var current = defaults(); private set
    private fun defaults() = Rules(SpellLootProfile.defaults.associateBy { it.key }, emptyMap())
    @SubscribeEvent fun stop(event: ServerStoppedEvent) { current = defaults() }
    @SubscribeEvent fun register(event: AddServerReloadListenersEvent) {
        event.addListener(Identifier.parse("primalspells:pack_rules"), object : SimplePreparableReloadListener<Rules>() {
            override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Rules {
                val profiles = SpellLootProfile.defaults.associateBy { it.key }.toMutableMap()
                val tiers = linkedMapOf<Int, Tier>()
                manager.listResources("primalspells/loot_profiles") { it.path.endsWith(".json") }.toSortedMap().forEach { (id, resource) ->
                    val path = id.path.removePrefix("primalspells/loot_profiles/").removeSuffix(".json")
                    val key = if (id.namespace == "primalspells") path else "${id.namespace}:$path"
                    profiles[key] = resource.openAsReader().use { parseProfile(key, JsonParser.parseReader(it).asJsonObject) }
                }
                manager.listResources("primalspells/wand_tiers") { it.path.endsWith(".json") }.forEach { (id, resource) ->
                    require(id.namespace == "primalspells") { "Tier overrides use primalspells namespace: $id" }
                    val level = id.path.substringAfterLast('/').removeSuffix(".json").toInt()
                    require(level in 1..6)
                    val j = resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
                    val t = Tier(j.number("max_mana", 1.0, 1000000.0), j.number("mana_per_tick", 0.0, 100000.0),
                        j.integer("delay_ticks", 0, 72000), j.integer("recharge_ticks", 0, 72000), j.integer("slots", 1, 18),
                        j.getAsJsonArray("casts").map { value -> val n = value.asDouble; require(n.isFinite() && n in 1.0..18.0 && n == kotlin.math.floor(n)); n.toInt() })
                    require(t.casts.isNotEmpty() && t.casts.size <= 1024)
                    tiers[level] = t
                }
                return Rules(profiles.toMap(), tiers.toMap())
            }
            override fun apply(preparations: Rules, manager: ResourceManager, profiler: ProfilerFiller) { current = preparations }
        })
    }
    /** 同一校验器用于重载与实机反例；未知法术不能在玩家开箱时才炸掉。 */
    fun parseProfile(key: String, j: JsonObject): SpellLootProfile {
        fun cards(name: String): List<String> = j.getAsJsonArray(name).map { it.asString.also { id -> require(id in MagicManager.registry) { "Unknown spell $id in $key/$name" } } }
        fun recipes(name: String) = j.getAsJsonArray(name)?.map { row -> row.asJsonArray.map { it.asString.also { id -> require(id in MagicManager.registry) { "Unknown spell $id in $key/$name" } } }.also { require(it.isNotEmpty() && it.size <= 18) } }.orEmpty()
        val lo = j.integer("min_tier", 1, 6); val hi = j.integer("max_tier", lo, 6)
        val pool = cards("cards"); val loadouts = recipes("loadouts"); val elite = recipes("elite_loadouts")
        require(pool.isNotEmpty() && pool.size <= 4096 && loadouts.isNotEmpty() && loadouts.size <= 256 && elite.size <= 256)
        val tag = Identifier.parse(j.get("structure_tag").asString).toString()
        val dims = j.getAsJsonArray("dimensions")?.map { Identifier.parse(it.asString).toString() }?.toSet().orEmpty()
        return SpellLootProfile(key, lo, hi, pool, loadouts, tag, dims, j.integer("spawn_weight", 0, 10000),
            j.number("elite_chance", 0.0, 1.0), j.number("wand_chance", 0.0, 1.0), j.number("charged_chance", 0.0, 1.0), j.integer("card_count", 0, 64), elite, if (j.has("priority")) j.integer("priority", -100000, 100000) else 0)
    }
    private fun JsonObject.number(name: String, min: Double, max: Double) = get(name).asDouble.also { require(it.isFinite() && it in min..max) { "Invalid $name: $it" } }
    private fun JsonObject.integer(name: String, min: Int, max: Int): Int {
        val n = number(name, min.toDouble(), max.toDouble()); require(n == kotlin.math.floor(n)) { "$name must be an integer" }; return n.toInt()
    }
}
