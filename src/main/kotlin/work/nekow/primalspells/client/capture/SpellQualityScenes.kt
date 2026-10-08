@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand

/** 第54轮：真实法杖发射、生命周期和跨投射物反应的实机回归。 */
object SpellQualityScenes {
    /** 注册联动验收场景。 */
    fun all(): List<Scene> = listOf(resonance(), stability())
    private fun cast(ctx: CaptureDriver.SceneCtx, ids: List<String>, x: Double, y: Double, z: Double,
        yaw: Float = 0f, pitch: Float = 0f, source: Entity = ctx.player) {
        source.setPos(x, y, z); source.setYRot(yaw); source.setXRot(pitch); source.setOldPosAndRot()
        val wand = Wand("quality-${ctx.level.gameTime}-${ids.joinToString()}")
        wand.innate += ids.map { MagicManager.create(it)!! }; wand.load(); wand.status.mana = 1000.0
        check(wand.spell(source))
    }
    /** 长时双场受力穿墙与超过512成员的PD批量载荷、即时追踪与高速穿刺的真实运行回归。 */
    private fun stability(): Scene {
        val name = "spell_stability"
        var holes = listOf<Projectile>()
        var probe: Projectile? = null
        var target: TestDummyEntity? = null
        var firstTurn = false
        var crystalBudget = 0
        var largeBatchOk = false
        return Scene(name, "双黑洞互吸穿墙 / 即时追踪 / 晶面预算", 24, 180,
            prepare = { ctx ->
                val floor = ctx.floorBlockY(0, 0)
                for (x in -12..12) for (z in -12..12) for (y in floor+1..floor+8)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(0, 0, floor); ctx.setup(true, 0.5, floor+5.0, -14.0, 0f, 10f)
                for (x in -4..4) for (y in floor+1..floor+6)
                    ctx.level.setBlock(BlockPos(x, y, 3), Blocks.STONE.defaultBlockState(), 2)
                holes = emptyList(); probe = null; firstTurn = false; crystalBudget = 0
            }, camera = { ctx, _ -> Pose(0.5, ctx.floorBlockY(0, 0)+5.0, -14.0, 0f, 10f) },
            tick = { ctx, t ->
                val floor = ctx.floorBlockY(0, 0)
                if (t == 8) {
                    cast(ctx, listOf("black_hole"), -1.5, floor+2.0, 0.0)
                    cast(ctx, listOf("black_hole"), 1.5, floor+2.0, 0.0)
                }
                if (t == 10) {
                    holes = MagicManager.projectiles.filter { it.id == "black_hole" }
                    holes.forEach { it.status.velocity.z = 0.25f }
                }
                if (t == 129) MagicManager.projectiles.forEach { it.alive = false }
                if (t == 92) {
                    val manager = ParticleManager.of(ctx.level)
                    val batch = work.nekow.particledrawing.api.ParticleBatch(manager)
                    // spawnAll声明上限256；用公开ensureSize建立完整集合，运动更新仍一次交给PD自动拆包。
                    batch.ensureSize(1024) { i -> manager.create()
                        .position(Vec3(-5.0 + (i % 32) * 0.025, floor+3.0, (i / 32) * 0.025))
                        .color(work.nekow.particledrawing.api.Color.ofInt(100, 205, 255)).scale(0.25f)
                        .lifetime(4).sizeCurve(*work.nekow.primalspells.magic.effect.visual.PixelVfx.lifeKeys(4)).spawn() }
                    batch.setVelocityAll(List(1024) { Vec3(0.0, 0.02, 0.0) })
                    largeBatchOk = batch.size() == 1024
                }
                if (t == 125) {
                    target = ctx.spawnDummy(2.0, floor+2.0, -2.0)
                    val p = MagicManager.create("fireball") as Projectile
                    SpellAssembly.apply(p, listOf(MagicManager.create("homing") as Modifier))
                    p.caster = ctx.player; p.wand = Wand("first-tick-tracking")
                    p.position = Vector3f(0f, floor+2.0f, -2f); p.velocity = Vector3f(0f, 0f, 1f)
                    p.spell(); probe = p
                    val launchSpeed = p.status.velocity.length()
                    p.tick()
                    firstTurn = p.status.velocity.x > 0 && launchSpeed > 0.79f && launchSpeed < 0.81f && p.status.age == 0
                    p.alive = false; p.die()
                    target!!.discard()
                    val piercing = MagicManager.create("fireball") as Projectile
                    piercing.caster = ctx.player; piercing.wand = Wand("wall-pass")
                    piercing.position = Vector3f(0f, floor+3f, 2.8f); piercing.velocity = Vector3f(0f, 0f, 1f)
                    piercing.throughBlocks = true; piercing.spell(); piercing.tick(); probe = piercing
                    piercing.alive = false; piercing.die()
                }
                if (t == 130) {
                    val manager = ParticleManager.of(ctx.level)
                    val before = manager.getEngine().particleCount()
                    val crystal = work.nekow.primalspells.magic.effect.visual.PixelVfx.crystal(manager, Vec3(0.0, floor+3.0, 0.0))
                    crystalBudget = manager.getEngine().particleCount() - before
                    crystal.destroyAfter(20)
                }
            }, checksAt = { ctx, t -> when(t) {
                40 -> listOf(Check(name, t, "two-holes-survive-attraction-through-wall", holes.size == 2 && holes.all { it.alive && !it.collisionEnabled && it.status.pos.z > 5f && it.status.pos.x.isFinite() && it.status.velocity.length().isFinite() }, "holes=${holes.map { Triple(it.alive, it.status.pos, it.status.velocity) }}"))
                96 -> listOf(Check(name, t, "pd-1024-velocity-members-no-disconnect", largeBatchOk, "all1024=$largeBatchOk"))
                128 -> listOf(Check(name, t, "two-holes-long-running-finite", holes.size == 2 && holes.all { it.alive && it.status.pos.x.isFinite() && it.status.velocity.length().isFinite() }, "holes=${holes.map { it.status.pos }}"))
                126 -> listOf(Check(name, t, "homing-first-tick-and-faster-launch", firstTurn, "firstTurn=$firstTurn"), Check(name, t, "piercing-integrates-through-solid-wall", probe != null && probe!!.status.pos.z > 3.4f, "position=${probe?.status?.pos}"))
                140 -> listOf(Check(name, t, "bounded-solid-crystal-budget", crystalBudget in 300..1000, "particles=$crystalBudget"))
                179 -> listOf(Check(name, t, "clean-projectiles", MagicManager.projectiles.isEmpty(), "shots=${MagicManager.projectiles.size}"), Check(name, t, "pd-particles-zero", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                else -> emptyList()
            } })
    }
    private fun resonance(): Scene {
        val name = "spell_resonance"
        var crystal: Projectile? = null
        var bomb: Projectile? = null
        var hole: Projectile? = null
        var frozenTarget: TestDummyEntity? = null
        var enemy: TestDummyEntity? = null
        var clusterHit = false
        var teleportTarget: TestDummyEntity? = null
        var droneBoltSeen = false
        var droneBoltEvidence = ""
        var matrix = 0
        var matrixOk = true
        return Scene(name, "晶体导能 / 冰火蒸汽 / 吞弹储能 / 护盾弹 / 子弹继承", 24, 320,
            prepare = { ctx ->
                val floor = ctx.floorBlockY(0, 0)
                for (x in -12..12) for (z in -12..12) for (y in floor+1..floor+8)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.level.getEntitiesOfClass(TestDummyEntity::class.java, AABB.ofSize(Vec3.ZERO, 100.0, 200.0, 100.0)).forEach { it.discard() }
                ctx.buildStudio(0, 0, floor); ctx.setup(true, 0.5, floor+4.0, -14.0, 0f, 10f)
                FrozenStatus.clear()
                frozenTarget = ctx.spawnDummy(1.0, floor+1.0, 0.0)
                enemy = ctx.spawnDummy(10.0, floor+1.0, 8.0)
                matrix = 0; matrixOk = true; clusterHit = false; droneBoltSeen = false
                val modifiers = MagicManager.registry.keys.filter { MagicManager.create(it) is Modifier }
                val shots = MagicManager.registry.keys.filter { MagicManager.create(it) is Projectile }
                val ordered = MagicManager.create("fireball") as Projectile
                SpellAssembly.apply(ordered, listOf("damage_boost", "volatile", "damage_boost").map { MagicManager.create(it) as Modifier })
                val copy = ordered.clone(); SpellAssembly.inherit(ordered, copy)
                for (p in listOf(ordered, copy)) {
                    p.caster = ctx.player; p.wand = Wand("ordered-probe")
                    p.position = Vector3f(0f, floor+3f, 0f); p.velocity = Vector3f(0f, 0f, 1f); p.spell()
                }
                matrixOk = copy.modifierOrder == ordered.modifierOrder && copy.status.damage == 11.0 && ordered.status.damage == 11.0 && copy.status.explosionDamage == 21.0 && ordered.status.explosionDamage == 21.0
                for (id in shots) for (mod in modifiers) {
                    val p = MagicManager.create(id) as Projectile
                    SpellAssembly.apply(p, listOf(MagicManager.create(mod) as Modifier))
                    SpellAssembly.prepare(p)
                    if ((id in listOf("small_explosion", "large_explosion", "drone_swarm", "crystal_detonation")) && mod in listOf("cluster_bomb", "explosion_projectile", "bounce_duplicate")) matrixOk = matrixOk && p.maxAge >= 40
                    val child = p.clone(); SpellAssembly.inherit(p, child)
                    matrixOk = matrixOk && p.modifiers[mod] == 1 && child.modifiers == SpellAssembly.inheritable(p) && child.modifierOrder == SpellAssembly.inheritableOrder(p) && p.status !== child.status && p.effects.none { it in child.effects }
                    matrix++
                }
            }, camera = { ctx, _ -> Pose(0.5, ctx.floorBlockY(0, 0)+4.0, -14.0, 0f, 10f) },
            tick = { ctx, t ->
                val floor = ctx.floorBlockY(0, 0)
                if (t == 8) cast(ctx, listOf("dormant_crystal"), -3.0, floor+2.0, 0.0, pitch=90f)
                if (t == 18) crystal = MagicManager.projectiles.first { it.id == "dormant_crystal" }
                if (t == 20) cast(ctx, listOf("freeze_charge", "plasma_beam"), -3.0, floor-0.5, -4.0)
                if (t == 45 || t == 90 || t == 115 || t == 145 || t == 170 || t == 190) MagicManager.projectiles.forEach { it.alive = false }
                if (t == 65) {
                    cast(ctx, listOf("speed_boost", "diffusion", "volatile", "freeze_charge", "small_explosion"), 0.0, floor+1.0, -3.0)
                    // 真实施法先进入pendingAdd；flush后、首次移动前保存对象，不遗漏断言。
                    work.nekow.primalspells.util.Deferred.after(ctx.level, 0) {
                        bomb = MagicManager.projectiles.firstOrNull { it.id == "small_explosion" }
                        bomb?.let {
                            it.status.pos.set(0f, floor+2.0f, 0f)
                            it.alive = false
                        }
                    }
                }
                if (t == 100) {
                    cast(ctx, listOf("freeze_charge", "fireball"), -4.0, floor+1.0, -1.0)
                    cast(ctx, listOf("fireball"), -4.0, floor+1.0, 1.0, yaw=180f)
                }
                if (t == 125) {
                    cast(ctx, listOf("black_hole"), 0.0, floor+1.0, 0.0)
                    cast(ctx, listOf("fireball"), 0.0, floor+1.0, -1.0)
                }
                if (t == 128) hole = MagicManager.projectiles.firstOrNull { it.id == "black_hole" }
                if (t == 150) {
                    cast(ctx, listOf("energy_shield", "plasma_beam"), 3.0, floor+1.0, 0.0)
                    cast(ctx, listOf("fireball"), 3.0, floor+1.0, -1.0, source=enemy!!)
                    enemy!!.setPos(10.0, floor+1.0, 8.0)
                }
                if (t == 200) {
                    teleportTarget = ctx.spawnDummy(-5.0, floor+1.0, 0.0)
                    cast(ctx, listOf("damage_boost", "freeze_charge", "teleport"), -5.0, floor+1.0, -3.0, source=enemy!!)
                }
                if (t == 220) cast(ctx, listOf("damage_boost", "freeze_charge", "homing", "drone_swarm"), 4.0, floor+1.0, 0.0)
                if (t == 235) {
                    val drone = ctx.level.getEntitiesOfClass(work.nekow.primalspells.entity.DroneEntity::class.java, AABB.ofSize(Vec3.ZERO, 100.0, 200.0, 100.0)).first()
                    drone.setPos(6.0, floor+4.0, 0.0); drone.attackCooldown = 0
                    frozenTarget!!.setPos(-6.0, floor+1.0, 0.0)
                    drone.performRangedAttack(frozenTarget!!, 1f)
                }
                if (t in 225..249) {
                    val bolts = MagicManager.projectiles.filter { it.id == "drone_bolt" }
                    if (bolts.isNotEmpty()) droneBoltEvidence = bolts.map { Triple(it.status.damage, it.status.freezeTicks, it.modifiers) }.toString()
                    droneBoltSeen = droneBoltSeen || bolts.any { it.status.damage == 3.75 && it.status.freezeTicks == 30 && "homing" in it.modifiers }
                }
                if (t == 250) {
                    ctx.level.getEntitiesOfClass(work.nekow.primalspells.entity.DroneEntity::class.java, AABB.ofSize(Vec3.ZERO, 100.0, 200.0, 100.0)).forEach { it.discard() }
                    MagicManager.projectiles.forEach { it.alive = false }
                }
                if (t == 175) cast(ctx, listOf("damage_boost", "freeze_charge", "speed_boost", "cluster_bomb", "fireball"), -4.0, floor+3.0, -3.0)
                if (t in 176..182 && !clusterHit) MagicManager.projectiles.firstOrNull { it.id == "fireball" && "cluster_bomb" in it.modifiers }?.let { p ->
                    p.hitBlock(Vector3f(p.status.pos), Vector3f(0f, 0f, -1f)); clusterHit = true
                }
            }, checksAt = { ctx, t -> when (t) {
                2 -> listOf(Check(name, t, "all-modifier-projectile-assemblies", matrixOk && matrix >= 400, "pairs=$matrix independent=$matrixOk"))
                35 -> listOf(Check(name, t, "beam-charges-crystal-and-transfers-ice", crystal!!.storedEnergy > 0 && crystal!!.status.freezeTicks > 0 && !crystal!!.alive,
                    "energy=${crystal!!.storedEnergy} freeze=${crystal!!.status.freezeTicks} alive=${crystal!!.alive}"))
                68 -> listOf(Check(name, t, "moving-bomb-keeps-modified-explosion", bomb != null && bomb!!.speed > 0 && bomb!!.maxAge >= 40 && bomb!!.status.explosionRadius == 6f && bomb!!.status.explosionDamage == 12.0,
                    "speed=${bomb?.speed} age=${bomb?.maxAge} radius=${bomb?.status?.explosionRadius} damage=${bomb?.status?.explosionDamage}"),
                    Check(name, t, "explosion-applies-freeze", FrozenStatus.isFrozen(frozenTarget!!), "frozen=${FrozenStatus.isFrozen(frozenTarget)}"))
                110 -> listOf(Check(name, t, "ice-fire-react-and-consume-both", MagicManager.projectiles.none { it.id == "fireball" && it.alive }, "shots=${MagicManager.projectiles.map { it.id }}"))
                140 -> listOf(Check(name, t, "black-hole-stores-consumed-shot", hole != null && hole!!.storedEnergy > 0 && MagicManager.projectiles.none { it.id == "fireball" && it.alive }, "energy=${hole?.storedEnergy}"))
                155 -> listOf(Check(name, t, "shield-projectile-reflects-and-changes-owner", MagicManager.projectiles.any { it.id == "fireball" && it.caster === ctx.player && it.status.velocity.z < 0 }, "shots=${MagicManager.projectiles.map { it.id to it.status.velocity }}"))
                185 -> {
                    val children = MagicManager.projectiles.filter { it.id == "fireball" && it.alive && "cluster_bomb" !in it.modifiers }
                    listOf(Check(name, t, "terminal-block-hit-creates-inheriting-cluster", children.size == 3 && children.all { it.modifiers["damage_boost"] == 1 && it.status.freezeTicks > 0 && it.status.damage == 6.0 && it.status.velocity.isFinite && it.status.velocity.length() in (0.45f * Projectile.LAUNCH_SPEED_SCALE)..(0.9f * Projectile.LAUNCH_SPEED_SCALE) }, "hit=$clusterHit all=${MagicManager.projectiles.map { it.id to it.modifiers }} children=${children.map { it.modifiers to it.status.damage }}"))
                }
                212 -> listOf(Check(name, t, "teleport-hit-carries-damage-and-freeze", FrozenStatus.isFrozen(teleportTarget!!) && enemy!!.position().distanceToSqr(teleportTarget!!.position()) < 4.0, "frozen=${FrozenStatus.isFrozen(teleportTarget!!)} caster=${enemy!!.position()}"))
                248 -> {
                    val drones = ctx.level.getEntitiesOfClass(work.nekow.primalspells.entity.DroneEntity::class.java, AABB.ofSize(Vec3.ZERO, 100.0, 200.0, 100.0))
                    listOf(Check(name, t, "drone-recipe-persists-and-modifies-bolts", drones.size == 5 && drones.all { it.spellRecipe == listOf("damage_boost", "freeze_charge", "homing") } && droneBoltSeen, "drones=${drones.size} bolt=$droneBoltSeen evidence=$droneBoltEvidence recipe=${drones.map { it.spellRecipe }}"))
                }
                319 -> listOf(Check(name, t, "projectiles-cleaned", MagicManager.projectiles.isEmpty(), "remaining=${MagicManager.projectiles.size}"),
                    Check(name, t, "pd-particles-zero", ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                else -> emptyList()
            } })
    }
}
