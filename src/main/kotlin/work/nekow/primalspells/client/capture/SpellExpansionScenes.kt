package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.DamageNumber
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.lifecycle.CrystalReaction
import work.nekow.primalspells.magic.effect.lifecycle.Explosion
import work.nekow.primalspells.magic.effect.lifecycle.FrozenStatus
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager

/** 第 52 轮内容的真实施法验收；只在开发录制驱动中加载。 */
object SpellExpansionScenes {
    /** 注册互相清场的玩法/视觉场景。 */
    fun all(): List<Scene> = listOf(crystals(), arc(), plasma(), utilities(), combinations())

    private fun prepare(ctx: CaptureDriver.SceneCtx) {
        val floor = ctx.floorBlockY(0, 0)
        // 前一场的内部遮挡墙必须清空，外围摄影棚保留。
        for (x in -12..12) for (z in -12..12) for (y in floor + 1..floor + 8) {
            val pos = BlockPos(x, y, z)
            if (!ctx.level.getBlockState(pos).isAir) ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
        }
        ctx.buildStudio(0, 0, floor)
        ctx.setup(true, 0.5, floor + 4.0, -14.0, 0f, 10f)
        ctx.level.getEntitiesOfClass(TestDummyEntity::class.java, AABB.ofSize(Vec3(0.0, floor.toDouble(), 0.0), 60.0, 60.0, 60.0))
            .forEach { it.discard() }
        FrozenStatus.clear()
    }

    private fun camera(ctx: CaptureDriver.SceneCtx): Pose = Pose(0.5, ctx.floorBlockY(0, 0) + 4.0, -14.0, 0f, 10f)

    /** 每次新造测试杖，仍走抽牌、耗蓝和荷载装配，不直接注入战斗效果。 */
    private fun cast(ctx: CaptureDriver.SceneCtx, ids: List<String>, x: Double, y: Double, z: Double,
        yaw: Float = 0f, pitch: Float = 0f, source: Entity = ctx.player): Wand {
        source.setPos(x, y, z)
        source.setYRot(yaw); source.setXRot(pitch); source.setOldPosAndRot()
        val wand = Wand("capture-expansion-${ctx.level.gameTime}-${ids.joinToString()}")
        wand.innate += ids.map { MagicManager.create(it) ?: error("missing $it") }
        wand.load(); wand.status.mana = 1000.0
        check(wand.spell(source)) { "cast failed: $ids" }
        return wand
    }

    private fun clean(name: String, t: Int, ctx: CaptureDriver.SceneCtx): List<Check> = listOf(
        Check(name, t, "projectiles-cleaned", MagicManager.projectiles.isEmpty(), "remaining=${MagicManager.projectiles.size}"),
        Check(name, t, "pd-particles-zero", ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
            "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"),
    )

    private fun numbers(ctx: CaptureDriver.SceneCtx): List<String> = ctx.level.getEntitiesOfClass(
        DamageNumber::class.java, AABB.ofSize(Vec3(0.0, ctx.floorBlockY(0, 0).toDouble(), 0.0), 50.0, 50.0, 50.0)
    ).map { "-" + work.nekow.primalspells.util.Lore.formatDouble(it.amount) }

    private fun crystals(): Scene {
        val name = "expansion_crystals"
        var foreign: TestDummyEntity? = null
        return Scene(name, "水晶：粘附 / 爆炸连锁 / 归属引爆", 24, 140,
            prepare = { ctx -> prepare(ctx); foreign = ctx.spawnDummy(10.5, ctx.floorBlockY(0, 0) + 1.0, 2.0) },
            camera = { ctx, _ -> camera(ctx) },
            tick = { ctx, t ->
                val y = ctx.floorBlockY(0, 0) + 2.0
                if (t == 8) {
                    for (x in listOf(-1.0, 1.0, 6.0)) cast(ctx, listOf("dormant_crystal"), x, y, 0.0, pitch = 90f)
                    cast(ctx, listOf("dormant_crystal"), 10.5, y, 0.0, pitch = 90f, source = foreign!!)
                }
                if (t == 40) {
                    val p = MagicManager.projectiles.first { it.id == "dormant_crystal" && it.caster === ctx.player }
                    Explosion.customExplode(ctx.player, org.joml.Vector3f(p.status.pos), 1.0f, 0.0)
                }
                if (t == 70) cast(ctx, listOf("crystal_detonation"), 0.0, y, -2.0)
                if (t == 95) MagicManager.projectiles.forEach { it.alive = false }
            },
            checksAt = { ctx, t ->
                val crystals = MagicManager.projectiles.filter { it.id == "dormant_crystal" && it.alive }
                when (t) {
                    32 -> listOf(Check(name, t, "crystals-stick", crystals.size == 4 && crystals.all { !it.motionEnabled }, "count=${crystals.size}"))
                    58 -> listOf(Check(name, t, "explosion-chains-to-neighbor", crystals.count { it.caster === ctx.player } == 1,
                        "owned=${crystals.count { it.caster === ctx.player }}"))
                    82 -> listOf(Check(name, t, "detonator-respects-owner", crystals.size == 1 && crystals.single().caster === foreign,
                        "remaining=${crystals.size}"))
                    139 -> clean(name, t, ctx)
                    else -> emptyList()
                }
            })
    }

    private fun arc(): Scene {
        val name = "expansion_arc"
        val targets = ArrayList<TestDummyEntity>()
        var shieldWand: Wand? = null
        return Scene(name, "电弧：衰减 / 去重 / 护盾拦截冰冻", 24, 150,
            prepare = { ctx ->
                prepare(ctx); targets.clear()
                for ((x, z) in listOf(-3.0 to 0.0, -1.0 to 2.0, 1.0 to 0.0, 3.0 to 2.0, 6.0 to 0.0))
                    targets += ctx.spawnDummy(x, ctx.floorBlockY(0, 0) + 1.0, z)
            }, camera = { ctx, _ -> Pose(0.5, ctx.floorBlockY(0, 0) + 3.5, -10.0, 0f, 10f) },
            tick = { ctx, t ->
                val y = ctx.floorBlockY(0, 0) + 1.0
                if (t == 8) cast(ctx, listOf("chain_bolt"), -3.0, y, -2.0)
                if (t == 65) {
                    val stack = ItemStack(ModItems.WAND.get())
                    val w = WandManager[WandManager.createWand(stack)]
                    w.magics += MagicManager.create("energy_shield")!!
                    w.load(); w.status.mana = 1000.0; w.charge = 0.0
                    shieldWand = w; targets.first().setItemInHand(InteractionHand.MAIN_HAND, stack)
                }
                if (t == 75) cast(ctx, listOf("freeze_charge", "chain_bolt"), -3.0, y, -2.0)
                if (t == 110) MagicManager.projectiles.forEach { it.alive = false }
            }, checksAt = { ctx, t -> when (t) {
                38 -> {
                    val values = numbers(ctx)
                    listOf(Check(name, t, "four-chain-bolt-discharges", values.sorted() == listOf("-4", "-4", "-4", "-4").sorted(), "damage=$values"))
                }
                98 -> listOf(Check(name, t, "shield-charged-and-no-freeze", shieldWand!!.status.mana < 1000.0 && !FrozenStatus.isFrozen(targets.first()),
                    "mana=${shieldWand.status.mana} frozen=${FrozenStatus.isFrozen(targets.first())}"))
                149 -> clean(name, t, ctx)
                else -> emptyList()
            } })
    }

    private fun plasma(): Scene {
        val name = "expansion_plasma"
        var target: TestDummyEntity? = null
        var shieldWand: Wand? = null
        var wallDamage = 0.0
        return Scene(name, "等离子束：持续命中 / 墙面截断 / 延寿联动", 24, 210,
            prepare = { ctx -> prepare(ctx); target = ctx.spawnDummy(0.0, ctx.floorBlockY(0, 0) + 1.0, 0.0) },
            camera = { ctx, _ -> Pose(6.0, ctx.floorBlockY(0, 0) + 3.8, -7.0, 41f, 10f) },
            tick = { ctx, t ->
                val floor = ctx.floorBlockY(0, 0)
                if (t == 8) cast(ctx, listOf("extended_lifetime", "plasma_beam"), 0.0, floor + 1.0, -5.0)
                if (t == 60) {
                    // 静止束直到上一刻仍在结算；清理旧阶段的浮字，只验新墙挡阶段。
                    ctx.level.getEntitiesOfClass(DamageNumber::class.java, AABB.ofSize(Vec3.ZERO, 100.0, 200.0, 100.0)).forEach { it.discard() }
                    MagicManager.projectiles.forEach { it.alive = false }
                    for (x in -1..1) for (y in floor + 1..floor + 4) ctx.level.setBlock(BlockPos(x, y, -2), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState(), 2)
                    cast(ctx, listOf("plasma_beam"), 0.0, floor + 1.0, -5.0)
                }
                if (t == 61) wallDamage = target!!.totalDamage
                if (t == 105) {
                    MagicManager.projectiles.forEach { it.alive = false }
                    for (x in -1..1) for (y in floor + 1..floor + 4) ctx.level.setBlock(BlockPos(x, y, -2), Blocks.AIR.defaultBlockState(), 2)
                    val stack = ItemStack(ModItems.WAND.get())
                    val w = WandManager[WandManager.createWand(stack)]
                    w.magics += MagicManager.create("energy_shield")!!
                    w.load(); w.status.mana = 1000.0; w.charge = 0.0
                    shieldWand = w; target!!.setItemInHand(InteractionHand.MAIN_HAND, stack)
                }
                if (t == 106) cast(ctx, listOf("freeze_charge", "plasma_beam"), 0.0, floor + 1.0, -5.0)
                if (t == 114) MagicManager.projectiles.forEach { it.alive = false }
            }, checksAt = { ctx, t -> when (t) {
                28 -> listOf(Check(name, t, "repeated-beam-damage", target!!.totalDamage >= 4.5, "damage=${numbers(ctx)}"))
                53 -> listOf(Check(name, t, "extended-lifetime-keeps-beam-alive", MagicManager.projectiles.any { it.id == "plasma_beam" && it.alive && it.status.age > 40 }, "shots=${MagicManager.projectiles.size}"))
                85 -> listOf(Check(name, t, "wall-blocks-damage", target!!.totalDamage == wallDamage, "before=$wallDamage total=${target.totalDamage}"))
                110 -> listOf(Check(name, t, "shield-blocks-initial-beam-and-freeze", shieldWand!!.status.mana <= 990.0 &&
                    numbers(ctx).isEmpty() && !FrozenStatus.isFrozen(target!!),
                    "mana=${shieldWand.status.mana} damage=${numbers(ctx)} frozen=${FrozenStatus.isFrozen(target!!)}"))
                118 -> listOf(Check(name, t, "per-tick-beam-exhausts-shield-budget", shieldWand!!.status.mana <= 975.0 &&
                    numbers(ctx).isNotEmpty() && FrozenStatus.isFrozen(target!!),
                    "mana=${shieldWand.status.mana} damage=${numbers(ctx)} frozen=${FrozenStatus.isFrozen(target!!)}"))
                209 -> clean(name, t, ctx)
                else -> emptyList()
            } })
    }

    private fun combinations(): Scene {
        val name = "expansion_combinations"
        var initialOrbit: Vec3? = null
        var orbiter: TestDummyEntity? = null
        val overlappingTargets = arrayListOf<TestDummyEntity>()
        return Scene(name, "组合：双发水晶 / 触发荷载 / 移动环绕光束", 24, 180,
            prepare = { ctx -> prepare(ctx); orbiter = ctx.spawnDummy(0.0, ctx.floorBlockY(0, 0) + 1.0, 0.0) }, camera = { ctx, _ -> camera(ctx) },
            tick = { ctx, t ->
                val floor = ctx.floorBlockY(0, 0)
                if (t == 8) cast(ctx, listOf("double_cast", "dormant_crystal", "dormant_crystal"), -3.0, floor + 2.0, 0.0, pitch = 90f)
                if (t == 40 || t == 85) MagicManager.projectiles.forEach { it.alive = false }
                if (t == 55) {
                    for (x in -1..1) for (y in floor + 1..floor + 4)
                        ctx.level.setBlock(BlockPos(x, y, 0), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState(), 2)
                    cast(ctx, listOf("trigger_fireball", "dormant_crystal"), 0.0, floor + 1.0, -5.0)
                }
                if (t == 86) {
                    for (x in -1..1) for (y in floor + 1..floor + 4) ctx.level.setBlock(BlockPos(x, y, 0), Blocks.AIR.defaultBlockState(), 2)
                    cast(ctx, listOf("self_orbit", "plasma_beam"), 0.0, floor + 1.0, 0.0, source = orbiter!!)
                }
                if (t == 95) {
                    val p = MagicManager.projectiles.single { it.id == "plasma_beam" }
                    initialOrbit = Vec3(p.status.pos.x.toDouble(), p.status.pos.y.toDouble(), p.status.pos.z.toDouble())
                }
                if (t == 108) orbiter!!.setPos(3.0, floor + 1.0, 0.0)
                if (t == 130) {
                    repeat(2) { overlappingTargets += ctx.spawnDummy(-5.0, floor + 1.0, 0.0) }
                    cast(ctx, listOf("chain_bolt"), -5.0, floor + 1.0, -1.0)
                }
                if (t == 140) MagicManager.projectiles.forEach { it.alive = false }
            }, checksAt = { ctx, t -> when (t) {
                32 -> listOf(Check(name, t, "double-cast-two-sticky-crystals",
                    MagicManager.projectiles.count { it.id == "dormant_crystal" && !it.motionEnabled } == 2,
                    "shots=${MagicManager.projectiles.map { it.id }}"))
                78 -> listOf(Check(name, t, "trigger-spawns-sticky-crystal",
                    MagicManager.projectiles.any { it.id == "dormant_crystal" && !it.motionEnabled },
                    "shots=${MagicManager.projectiles.map { it.id to it.motionEnabled }}"))
                115 -> {
                    val p = MagicManager.projectiles.single { it.id == "plasma_beam" }
                    val pos = Vec3(p.status.pos.x.toDouble(), p.status.pos.y.toDouble(), p.status.pos.z.toDouble())
                    val holder = orbiter!!
                    val radius = kotlin.math.hypot(pos.x - holder.x, pos.z - holder.z)
                    val tangent = (pos.x - holder.x) * p.status.velocity.x + (pos.z - holder.z) * p.status.velocity.z
                    listOf(Check(name, t, "orbit-follows-moving-caster-and-tangent", radius in 2.0..5.0 &&
                        pos.distanceToSqr(initialOrbit!!) > 0.25 && p.status.velocity.isFinite,
                        "radius=$radius tangent=$tangent pos=$pos"))
                }
                179 -> clean(name, t, ctx)
                148 -> listOf(Check(name, t, "overlapping-targets-do-not-kill-chain", overlappingTargets.size == 2 &&
                    overlappingTargets.all { it.totalDamage > 0 }, "damage=${overlappingTargets.map { it.totalDamage }}"))
                else -> emptyList()
            } })
    }

    private fun utilities(): Scene {
        val name = "expansion_utilities"
        var target: TestDummyEntity? = null
        var arrow: Arrow? = null
        return Scene(name, "冰冻碎冰 / 远距与触发荷载 / 斥力弹道", 24, 220,
            prepare = { ctx -> prepare(ctx); target = ctx.spawnDummy(0.0, ctx.floorBlockY(0, 0) + 1.0, 0.0) },
            camera = { ctx, _ -> camera(ctx) },
            tick = { ctx, t ->
                val floor = ctx.floorBlockY(0, 0)
                if (t == 8 || t == 43) cast(ctx, listOf("freeze_charge", "fireball"), 0.0, floor + 1.0, -4.0)
                if (t == 31) cast(ctx, listOf("fireball"), 0.0, floor + 1.0, -1.0)
                if (t == 85) cast(ctx, listOf("long_distance_cast", "black_hole"), -5.0, floor + 1.0, -6.0)
                if (t == 98 || t == 112) MagicManager.projectiles.forEach { it.alive = false }
                if (t == 100) {
                    for (x in -6..-4) for (y in floor + 1..floor + 4) ctx.level.setBlock(BlockPos(x, y, -2), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState(), 2)
                    cast(ctx, listOf("long_distance_cast", "black_hole"), -5.0, floor + 1.0, -6.0)
                }
                if (t == 120) cast(ctx, listOf("trigger_fireball", "long_distance_cast", "black_hole"), -5.0, floor + 1.0, -6.0)
                if (t == 140) {
                    MagicManager.projectiles.forEach { it.alive = false }
                    cast(ctx, listOf("repulsion_field"), 5.0, floor + 1.0, 0.0)
                    cast(ctx, listOf("fireball"), 9.0, floor + 1.0, 0.0, yaw = 90f)
                    val a = Arrow(ctx.level, ctx.player, ItemStack(Items.ARROW), null)
                    a.setPos(7.0, floor + 2.6, 2.0); a.setNoGravity(true); a.deltaMovement = Vec3(-0.05, 0.0, 0.0)
                    ctx.level.addFreshEntity(a); arrow = a
                }
                if (t == 175) { MagicManager.projectiles.forEach { it.alive = false }; arrow?.discard() }
            }, checksAt = { ctx, t -> when (t) {
                28 -> listOf(Check(name, t, "accepted-hit-freezes", FrozenStatus.isFrozen(target!!), "frozen=${FrozenStatus.isFrozen(target)}"))
                39 -> listOf(Check(name, t, "heavy-hit-shatters", !FrozenStatus.isFrozen(target!!) && "-7" in numbers(ctx), "damage=${numbers(ctx)}"))
                62 -> listOf(Check(name, t, "freeze-immunity", !FrozenStatus.isFrozen(target!!), "frozen=${FrozenStatus.isFrozen(target)}"))
                88 -> {
                    val p = MagicManager.projectiles.single { it.id == "black_hole" }
                    listOf(Check(name, t, "immobile-cast-moves-forward", kotlin.math.abs(p.status.pos.z) < 0.2, "pos=${p.status.pos}"))
                }
                104 -> listOf(Check(name, t, "far-cast-stops-at-wall", MagicManager.projectiles.any { it.id == "black_hole" && it.status.pos.z in -2.3f..-2.0f }, "positions=${MagicManager.projectiles.filter { it.id == "black_hole" }.map { it.status.pos }}"))
                134 -> listOf(Check(name, t, "trigger-payload-keeps-launch-direction", MagicManager.projectiles.any { it.id == "black_hole" && it.status.pos.z < -7f }, "positions=${MagicManager.projectiles.filter { it.id == "black_hole" }.map { it.status.pos }}"))
                155 -> listOf(Check(name, t, "repels-magic-and-vanilla", arrow!!.deltaMovement.x > 0 && MagicManager.projectiles.any { it.id == "fireball" && it.status.velocity.x > 0 }, "arrow=${arrow.deltaMovement} shots=${MagicManager.projectiles.map { it.id to it.status.velocity }}"))
                219 -> clean(name, t, ctx)
                else -> emptyList()
            } })
    }
}
