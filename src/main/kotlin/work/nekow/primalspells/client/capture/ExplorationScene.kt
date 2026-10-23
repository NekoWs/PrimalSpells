package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.util.RandomSource
import net.minecraft.util.random.Weighted
import net.minecraft.util.random.WeightedList
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.MobSpawnSettings
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.RogueMageEntity
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.loot.SpellLoot
import work.nekow.primalspells.loot.SpellLootProfile
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand

/** 原生箱子战利品、完整卡池、真实敌人施法/掩体/打断/装备掉落回归。 */
object ExplorationScene {
    fun scene(): Scene {
        val name = "exploration"
        var floor = 0
        var mage: RogueMageEntity? = null
        var beforeWall = 0
        var originalId = ""
        var recipe = emptyList<String>()
        val checks = linkedMapOf<Int, MutableList<Check>>()
        return Scene(name, "结构法术战利品 / 游荡术士实战与夺杖", 24, 240,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 2.0, floor + 3.0, -9.0, 0f, 9f)
                ctx.command("difficulty normal")
            }, camera = { _, _ -> Pose(2.0, floor + 3.0, -9.0, 0f, 9f) },
            tick = { ctx, t ->
                fun record(id: String, ok: Boolean, detail: String) {
                    checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
                }
                fun items() = ctx.level.getEntitiesOfClass(ItemEntity::class.java, AABB(-30.0, floor.toDouble(), -30.0, 30.0, floor + 15.0, 30.0))
                fun wall(block: net.minecraft.world.level.block.Block) {
                    for (x in -4..4) for (y in 1..4) ctx.level.setBlock(BlockPos(x, floor + y, 0), block.defaultBlockState(), 3)
                }
                if (t == 8) {
                    val pool = SpellLootProfile.entries.flatMap { it.cards + it.loadouts.flatten() }.toSet()
                    record("all-registered-spells-have-valid-reward-sources", pool == SpellRegistry.factories.keys, "pool=${pool.size} registry=${SpellRegistry.factories.size}")
                    val ids = hashSetOf<String>()
                    var valid = true
                    var charged = 0
                    for (profile in SpellLootProfile.entries) repeat(24) { seed ->
                        val stack = SpellLoot.wand(profile, RandomSource.create(seed.toLong()))
                        val wand = WandItem.getWand(stack)!!
                        val saved = Wand.fromTag(wand.saveTag())
                        valid = valid && ModItems.wandTierOf(stack.item)!!.level in profile.minTier..profile.maxTier &&
                            wand.magics.filterNotNull().map { it.id } in profile.loadouts &&
                            wand.magics.size <= wand.size && wand.status.mana == wand.mana &&
                            saved.magics.filterNotNull().map { it.id } == wand.magics.filterNotNull().map { it.id } &&
                            saved.mana == wand.mana && ids.add(wand.id)
                        if (stack.getOrDefault(ModItems.WAND_CHARGED, false)) {
                            charged++
                            valid = valid && stack.has(DataComponents.CUSTOM_NAME)
                        }
                    }
                    record("144-reward-wands-unique-loaded-tiered-and-save-safe", valid && charged > 0, "unique=${ids.size} charged=$charged")
                    val profiles = listOf("village/village_temple", "desert_pyramid", "pillager_outpost", "stronghold_library", "nether_bridge", "end_city_treasure")
                    for ((index, table) in profiles.withIndex()) {
                        // 真正的/loot命令经过数据包GLM，不能只测试奖励工厂。
                        ctx.command("loot spawn 12 ${floor + 1} 12 loot minecraft:chests/$table")
                        val drops = items().map { it.item }
                        val cards = drops.filter { ModItems.spellIdOf(it.item) != null }
                        val wands = drops.filter { it.item is WandItem }
                        record("native-chest-$index", cards.size >= (if (index == 0) 1 else 2) &&
                            (index != 0 || wands.size == 1 && drops.any { it.`is`(Items.WRITTEN_BOOK) }), "table=$table cards=${cards.size} wands=${wands.size}")
                        items().forEach { it.discard() }
                    }
                    ctx.command("loot spawn 12 ${floor + 1} 12 loot minecraft:chests/spawn_bonus_chest")
                    record("unrelated-chest-is-not-modified", items().none { it.item.item is WandItem || ModItems.spellIdOf(it.item.item) != null }, "drops=${items().size}")
                    items().forEach { it.discard() }
                    record("all-site-tags-resolve", SpellLootProfile.entries.filter { it != SpellLootProfile.STARTER }.all {
                        ctx.level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(it.structureTag).map { set -> set.size() > 0 }.orElse(false)
                    }, "structure tags loaded")
                    val event = LevelEvent.PotentialSpawns(ctx.level, MobCategory.MONSTER, BlockPos(0, floor + 1, 0), WeightedList.of())
                    NeoForge.EVENT_BUS.post(event)
                    record("no-caster-spawns-outside-structures", event.spawnerDataList.isEmpty() &&
                        !RogueMageEntity.canSpawn(ModEntities.ROGUE_MAGE.get(), ctx.level, EntitySpawnReason.NATURAL, BlockPos(0, floor + 1, 0), ctx.level.random), "entries=${event.spawnerDataList.size}")
                    val holder = ctx.level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("pillager_outpost")))
                    val generator = ctx.level.chunkSource.generator
                    val chunkPos = ChunkPos(6, 6)
                    // 用原版生成器创建真实结构pieces，再登记原版结构元数据；不伪造at()返回值。
                    val start = holder.value().generate(holder, ctx.level.dimension(), ctx.level.registryAccess(), generator,
                        generator.biomeSource, ctx.level.chunkSource.randomState(), ctx.level.structureManager, ctx.level.seed,
                        chunkPos, 0, ctx.level) { true }
                    if (start.isValid) {
                        val point = start.pieces.first().boundingBox.center
                        val startChunk = ctx.level.getChunk(chunkPos.x, chunkPos.z)
                        val pointChunk = ctx.level.getChunkAt(point)
                        ctx.level.structureManager().setStartForStructure(SectionPos.of(chunkPos, ctx.level.minSectionY), holder.value(), start, startChunk)
                        ctx.level.structureManager().addReferenceForStructure(SectionPos.of(point), holder.value(), chunkPos.pack(), pointChunk)
                        val originals = WeightedList.of(Weighted(MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE, 1, 1), 100))
                        val inside = LevelEvent.PotentialSpawns(ctx.level, MobCategory.MONSTER, point, originals)
                        NeoForge.EVENT_BUS.post(inside)
                        record("real-structure-adds-caster-preserves-vanilla-list", SpellLootProfile.at(ctx.level, point) == SpellLootProfile.RAIDERS &&
                            inside.spawnerDataList.size == 2 && inside.spawnerDataList.any { it.value().type() == EntityTypes.ZOMBIE && it.weight() == 100 } &&
                            inside.spawnerDataList.count { it.value().type() == ModEntities.ROGUE_MAGE.get() } == 1, "entries=${inside.spawnerDataList.size} profile=${SpellLootProfile.at(ctx.level, point)}")
                    } else record("real-structure-adds-caster-preserves-vanilla-list", false, "outpost generation failed")
                }
                if (t == 12) {
                    ctx.command("gamemode survival @a")
                    ctx.player.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 1000.0
                    ctx.player.health = 1000f
                    mage = ModEntities.ROGUE_MAGE.get().create(ctx.level, EntitySpawnReason.COMMAND)?.also {
                        it.setPos(0.0, floor + 1.0, 5.0)
                        it.equip(SpellLootProfile.RUINS, false)
                        it.target = ctx.player
                        ctx.level.addFreshEntity(it)
                        val wand = WandItem.getWand(it.mainHandItem)!!
                        originalId = wand.id
                        recipe = wand.magics.filterNotNull().map { card -> card.id }
                    }
                }
                if (t == 22) record("enemy-telegraphs-before-first-shot", mage?.charging == true && mage?.successfulCasts == 0, "charging=${mage?.charging} casts=${mage?.successfulCasts} target=${mage?.target?.type} ticks=${mage?.tickCount} noAi=${mage?.isNoAi}")
                if (t == 65) {
                    record("enemy-uses-real-wand-and-deals-damage", (mage?.successfulCasts ?: 0) > 0 && ctx.player.health < 1000f,
                        "casts=${mage?.successfulCasts} health=${ctx.player.health} target=${mage?.target?.type} ticks=${mage?.tickCount} noAi=${mage?.isNoAi}")
                    wall(Blocks.CONCRETE.pick(DyeColor.BLACK))
                }
                if (t == 70) beforeWall = mage?.successfulCasts ?: 0
                if (t == 90) {
                    record("cover-stops-casting", mage?.successfulCasts == beforeWall, "before=$beforeWall after=${mage?.successfulCasts}")
                    wall(Blocks.AIR)
                }
                if (t in 92..155 && mage?.charging == true && checks.values.flatten().none { it.render().contains("damage-interrupts-preparation") }) {
                    mage!!.hurtServer(ctx.level, ctx.level.damageSources().playerAttack(ctx.player), 1f)
                    record("damage-interrupts-preparation", !mage!!.charging, "charging=${mage!!.charging}")
                }
                if (t == 160) mage?.hurtServer(ctx.level, ctx.level.damageSources().playerAttack(ctx.player), 1000f)
                if (t == 165) {
                    val drops = items().filter { it.item.item is WandItem }
                    val looted = drops.singleOrNull()?.item
                    record("death-drops-exactly-the-used-wand", drops.size == 1 && looted?.get(ModItems.WAND_ID)?.wandId == originalId &&
                        WandItem.getWand(looted)?.magics?.filterNotNull()?.map { it.id } == recipe,
                        "drops=${drops.size} expected=$originalId actual=${looted?.get(ModItems.WAND_ID)?.wandId}")
                    items().forEach { it.discard() }
                    ctx.command("gamemode spectator @a")
                    ctx.player.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 20.0
                    ctx.player.health = 20f
                    ProjectileRuntime.projectiles.forEach { it.alive = false }
                }
                if (t == 239) {
                    record("combat-does-not-break-floor", (-3..3).all { x -> (-5..6).all { z -> ctx.level.getBlockState(BlockPos(x, floor, z)).`is`(Blocks.CONCRETE.pick(DyeColor.BLACK)) } }, "floor intact")
                    record("scene-cleans-particles-and-projectiles", ProjectileRuntime.projectiles.isEmpty() && ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
                        "shots=${ProjectileRuntime.projectiles.size} particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
            }, checksAt = { _, t -> checks[t] ?: emptyList() }, finish = { ctx -> mage?.discard(); ctx.command("gamemode spectator @a"); ctx.command("difficulty peaceful"); ctx.player.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 20.0; ctx.player.health = 20f })
    }
}
