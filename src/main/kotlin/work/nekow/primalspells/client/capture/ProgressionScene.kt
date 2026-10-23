package work.nekow.primalspells.client.capture

import com.google.gson.JsonParser
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.entity.*
import work.nekow.primalspells.integration.*
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.loot.*
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.*
import work.nekow.primalspells.wand.*

/** 公共事件真实取消/伤害、原版拾取权限、实际结构罗盘及精英原杖掉落。 */
object ProgressionScene {
    class Probe {
        var cancelCast = false; var cancelImpact = false; var denied: UUID? = null
        var amount: Double? = null; var casts = 0; var impacts = 0; var states = 0
        @SubscribeEvent fun before(e: SpellEvents.BeforeCast) { if (cancelCast) e.isCanceled = true }
        @SubscribeEvent fun after(e: SpellEvents.AfterCast) { casts++ }
        @SubscribeEvent fun impact(e: SpellEvents.BeforeImpact) { if (cancelImpact) e.isCanceled = true; amount?.let { e.amount = it } }
        @SubscribeEvent fun impacted(e: SpellEvents.AfterImpact) { impacts++ }
        @SubscribeEvent fun status(e: SpellEvents.StatusChanged) { states++ }
        @SubscribeEvent fun utility(e: SpellEvents.UtilityAction) { if (e.target.uuid == denied) e.isCanceled = true }
    }
    fun scene(): Scene {
        val checks = linkedMapOf<Int, MutableList<Check>>()
        var floor = 0
        val probe = Probe()
        val mages = arrayListOf<RogueMageEntity>()
        var eliteId = ""
        var eliteCards = emptyList<String>()
        var originalStats: Wand? = null
        fun wand(vararg ids: String): ItemStack = ItemStack(ModItems.WAND.get()).also { stack ->
            val w = WandManager[WandManager.createWand(stack)]
            w.magics += ids.map { SpellRegistry.create(it)!! }; w.load(); w.status.mana = w.mana
            stack.set(ModItems.WAND_SPELLS, ids.toList() + List(w.size - ids.size) { "" })
        }
        return Scene("progression", "数据包 / 安全世界作用 / 寻法 / 流派精英", 24, 240, showHud = true,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0); ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.0, floor + 2.0, -7.0, 0f, 5f)
                ctx.command("difficulty normal"); NeoForge.EVENT_BUS.register(probe)
            }, camera = { _, _ -> Pose(0.0, floor + 2.0, -7.0, 0f, 5f) },
            tick = { ctx, t ->
                fun check(name: String, ok: Boolean, detail: Any?) { checks.getOrPut(t) { arrayListOf() }.add(Check("progression", t, name, ok, detail.toString())) }
                if (t == 8) {
                    val rules = PackRules.current
                    check("real-data-reload-loaded-profiles-and-tiers", rules.tiers.size == 6 && SpellLootProfile.RAIDERS.eliteLoadouts.isNotEmpty() && "item_pull" in SpellLootProfile.STARTER.cards, "profiles=${rules.profiles.size}; tiers=${rules.tiers.size}")
                    check("all-spells-have-data-rewards", SpellLootProfile.entries.flatMap { it.cards + it.loadouts.flatten() }.toSet() == SpellRegistry.factories.keys, SpellRegistry.factories.size)
                    val invalid = JsonParser.parseString("""{"min_tier":1,"max_tier":1,"cards":["missing_spell"],"loadouts":[["fireball"]]}""").asJsonObject
                    check("unknown-data-spell-rejected-without-changing-rules", runCatching { PackRules.parseProfile("test:invalid", invalid) }.isFailure && PackRules.current === rules, "snapshot unchanged")
                    val caster = EntityTypes.ZOMBIE.create(ctx.level, EntitySpawnReason.COMMAND)!!
                    caster.setPos(0.0, floor + 1.0, 4.0); caster.isNoAi = true; caster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)!!.baseValue = 0.0; ctx.level.addFreshEntity(caster)
                    val stack = wand("fireball"); val w = WandItem.getWand(stack)!!
                    val before = w.saveTag().toString(); probe.cancelCast = true
                    check("public-nonplayer-cast-cancellation-does-not-consume", !SpellApi.cast(caster, stack) && w.saveTag().toString() == before && probe.casts == 0, w.status.mana)
                    probe.cancelCast = false
                    check("public-nonplayer-cast-uses-real-transaction", SpellApi.cast(caster, stack) && w.status.mana == w.mana - 3 && probe.casts == 1, w.status.mana)
                    probe.cancelImpact = true
                    val health = caster.health
                    check("impact-cancel-preserves-health", !SpellImpact.hurt(ctx.level, caster, ctx.player, 5.0, Vec3.ZERO) && caster.health == health && probe.impacts == 0, caster.health)
                    probe.cancelImpact = false; probe.amount = 1.0
                    check("impact-event-changes-real-damage", SpellImpact.hurt(ctx.level, caster, ctx.player, 5.0, Vec3.ZERO) && caster.health == health - 1 && probe.impacts == 1, caster.health)
                    probe.amount = null
                    FrozenStatus.freeze(caster, 20); FrozenStatus.shatter(caster)
                    check("freeze-enter-and-exit-events", probe.states == 2 && !FrozenStatus.isFrozen(caster), probe.states)
                    caster.discard()
                    ProjectileRuntime.projectiles.forEach { it.alive = false }
                }
                if (t == 25) {
                    fun item(x: Double, z: Double, type: net.minecraft.world.item.Item) = ItemEntity(ctx.level, x, floor + 2.0, z, ItemStack(type)).also { it.setNoPickUpDelay(); ctx.level.addFreshEntity(it) }
                    val visible = item(0.0, -4.0, Items.DIAMOND)
                    val owned = item(-1.0, -4.0, Items.EMERALD); owned.setTarget(UUID.randomUUID())
                    val delayed = item(-2.0, -5.0, Items.GOLD_INGOT); delayed.setPickUpDelay(1000)
                    val denied = item(-1.0, -6.0, Items.IRON_INGOT); probe.denied = denied.uuid
                    val blocked = item(2.0, -4.0, Items.QUARTZ)
                    for (x in 1..3) for (y in 1..4) ctx.level.setBlock(BlockPos(x, floor + y, -5), Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState(), 3)
                    check("gather-cast", SpellApi.cast(ctx.player, wand("item_pull")), "real player")
                    check("gather-respects-owner-delay-wall-and-permission", !visible.isAlive && owned.isAlive && delayed.isAlive && denied.isAlive && blocked.isAlive, "${visible.isAlive}/${owned.isAlive}/${delayed.isAlive}/${denied.isAlive}/${blocked.isAlive}")
                    listOf(owned, delayed, denied, blocked).forEach { it.discard() }; probe.denied = null
                    for (x in 1..3) for (y in 1..4) ctx.level.setBlock(BlockPos(x, floor + y, -5), Blocks.AIR.defaultBlockState(), 3)
                    ctx.player.fallDistance = 15.0
                    check("rescue-resets-fall-and-adds-vanilla-effect", SpellApi.cast(ctx.player, wand("feather_ward")) && ctx.player.fallDistance == 0.0 && ctx.player.hasEffect(MobEffects.SLOW_FALLING), ctx.player.activeEffects)
                    val zombie = EntityTypes.ZOMBIE.create(ctx.level, EntitySpawnReason.COMMAND)!!
                    zombie.isNoAi = true; zombie.setPos(0.0, floor + 1.0, 1.0); ctx.level.addFreshEntity(zombie)
                    check("survey-reveals-real-visible-hostile", SpellApi.cast(ctx.player, wand("survey_pulse")) && zombie.hasEffect(MobEffects.GLOWING), zombie.activeEffects)
                    zombie.discard()
                }
                if (t == 40) {
                    val input = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, listOf(ItemStack(Items.PAPER), ItemStack(Items.COMPASS), ItemStack(Items.AMETHYST_SHARD)))
                    val recipe = ctx.server.recipeManager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, ctx.level).orElse(null)
                    check("real-compass-crafting-recipe", recipe?.value()?.assemble(input)?.item == ModItems.SPELL_COMPASS.get(), recipe?.id())
                    val compass = ItemStack(ModItems.SPELL_COMPASS.get()); ctx.player.setItemInHand(InteractionHand.MAIN_HAND, compass)
                    compass.item.use(ctx.level, ctx.player, InteractionHand.MAIN_HAND)
                    val tracker = compass.get(DataComponents.LODESTONE_TRACKER)
                    check("compass-locates-real-village-and-retains-target", tracker?.target()?.isPresent == true && !tracker.tracked() && tracker.target().get().dimension() == ctx.level.dimension(), tracker)
                    check("compass-search-has-cooldown", ctx.player.cooldowns.isOnCooldown(compass), ctx.player.cooldowns.isOnCooldown(compass))
                    ctx.player.isShiftKeyDown = true; compass.item.use(ctx.level, ctx.player, InteractionHand.MAIN_HAND); ctx.player.isShiftKeyDown = false
                    check("compass-cycle-clears-stale-target", work.nekow.primalspells.item.SpellCompassItem.selected(compass) == "ruins" && !compass.has(DataComponents.LODESTONE_TRACKER), compass)
                    ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                }
                if (t == 55) {
                    for ((i, profile) in listOf(SpellLootProfile.RUINS, SpellLootProfile.RAIDERS, SpellLootProfile.ARCANE, SpellLootProfile.RELIC).withIndex()) {
                        val mage = ModEntities.ROGUE_MAGE.get().create(ctx.level, EntitySpawnReason.COMMAND)!!
                        mage.setPos(-6.0 + i * 4, floor + 1.0, 3.0); mage.isNoAi = true; mage.equip(profile, true)
                        ctx.level.addFreshEntity(mage); mages += mage; mage.prepareCast()
                    }
                    check("schools-match-actual-wand-recipes", mages.map { it.school }.toSet().size == 4, mages.map { it.school })
                    check("elites-are-not-health-buffs", mages.all { it.maxHealth == 36f && it.elite && it.hasCustomName() }, mages.map { it.maxHealth })
                    val elite = mages.first(); eliteId = elite.mainHandItem.get(ModItems.WAND_ID)!!.wandId
                    eliteCards = WandItem.getWand(elite.mainHandItem)!!.magics.mapNotNull { it?.id }
                    val roundtrip = Wand.fromTag(WandItem.getWand(elite.mainHandItem)!!.saveTag())
                    check("elite-recipe-persists-in-original-wand", roundtrip.magics.mapNotNull { it?.id } == eliteCards && eliteCards in SpellLootProfile.RUINS.eliteLoadouts, eliteCards)
                }
                if (t == 75) {
                    val mage = mages.first()
                    // 靠近目标时真实寻路目标向外；恢复期可选择真实墙体后的可站立掩体。
                    for (x in -10..-5) for (y in 1..4) ctx.level.setBlock(BlockPos(x, floor + y, 1), Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState(), 3)
                    val point = MageTactics.destination(mage, ctx.player, true)
                    check("tactics-select-real-cover", point != null, point)
                    val retreat = MageTactics.destination(mage, ctx.player, false)
                    check("tactics-select-retreat-away-from-target", retreat != null && retreat.distanceToSqr(ctx.player.position()) > mage.distanceToSqr(ctx.player), retreat)
                    for (x in -10..-5) for (y in 1..4) ctx.level.setBlock(BlockPos(x, floor + y, 1), Blocks.AIR.defaultBlockState(), 3)
                }
                if (t == 100) mages.first().hurtServer(ctx.level, ctx.level.damageSources().playerAttack(ctx.player), 1000f)
                if (t == 105) {
                    val drops = ctx.level.getEntitiesOfClass(ItemEntity::class.java, AABB(-15.0, floor.toDouble(), -15.0, 15.0, floor + 10.0, 15.0))
                    check("elite-drops-original-wand-and-one-reward-card", drops.count { it.item.item is WandItem && it.item.get(ModItems.WAND_ID)?.wandId == eliteId } == 1 && drops.count { ModItems.spellIdOf(it.item.item) != null } == 1, drops.map { it.item })
                    drops.forEach { it.discard() }; mages.forEach { it.discard() }
                    ProjectileRuntime.projectiles.forEach { it.alive = false }; FrozenStatus.clear()
                    ctx.player.removeAllEffects()
                }
                if (t == 115) {
                    originalStats = WandItem.getWand(SpellLoot.wand(SpellLootProfile.STARTER, RandomSource.create(17)))!!
                    val pack = ctx.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("capture-api")
                    fun write(path: String, text: String) { val out = pack.resolve(path); java.nio.file.Files.createDirectories(out.parent); java.nio.file.Files.writeString(out, text) }
                    write("pack.mcmeta", """{"pack":{"description":"Capture API fixture","min_format":[107,1],"max_format":[107,1]}}""")
                    write("data/capture/primalspells/loot_profiles/probe.json", """{"min_tier":1,"max_tier":1,"cards":["fireball"],"loadouts":[["fireball"]],"elite_loadouts":[],"structure_tag":"primalspells:spell_sites/ruins","dimensions":["minecraft:overworld"],"spawn_weight":0,"elite_chance":0,"wand_chance":1,"charged_chance":0,"card_count":1}""")
                    write("data/primalspells/primalspells/wand_tiers/1.json", """{"max_mana":1234,"mana_per_tick":2,"delay_ticks":11,"recharge_ticks":22,"slots":7,"casts":[1]}""")
                    ctx.server.packRepository.reload()
                    ctx.command("datapack enable \"file/capture-api\"")
                }
                if (t == 165) {
                    val profile = SpellLootProfile.byKey("capture:probe")
                    check("real-datapack-enable-adds-custom-pool", profile != null && profile.spawnWeight == 0 && profile.allows(ctx.level), profile)
                    val generated = profile?.let { WandItem.getWand(SpellLoot.wand(it, RandomSource.create(18))) }
                    check("tier-override-affects-new-wands-only", generated?.mana == 1234.0 && generated.size == 7 && originalStats?.mana == 80.0 && Wand.fromTag(originalStats.saveTag()).mana == 80.0, "new=${generated?.mana}; old=${originalStats?.mana}")
                    ctx.command("datapack disable \"file/capture-api\"")
                }
                if (t == 215) check("real-datapack-disable-restores-defaults", SpellLootProfile.byKey("capture:probe") == null && PackRules.current.tiers[1]?.mana == 80.0, PackRules.current.tiers[1]?.mana)
                if (t == 235) check("cleanup-leaves-no-projectiles-or-particles", ProjectileRuntime.projectiles.isEmpty() && work.nekow.particledrawing.api.ParticleManager.of(ctx.level).getEngine().particleCount() == 0, ProjectileRuntime.projectiles.size)
            }, checksAt = { _, t -> checks[t].orEmpty() }, finish = { ctx ->
                NeoForge.EVENT_BUS.unregister(probe); mages.forEach { it.discard() }; ctx.player.removeAllEffects(); ctx.command("difficulty peaceful")
            })
    }
}
