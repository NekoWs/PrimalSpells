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
    fun all(): List<Scene> = listOf(resonance())
    private fun cast(ctx: CaptureDriver.SceneCtx, ids: List<String>, x: Double, y: Double, z: Double,
        yaw: Float = 0f, pitch: Float = 0f, source: Entity = ctx.player) {
        source.setPos(x, y, z); source.setYRot(yaw); source.setXRot(pitch); source.setOldPosAndRot()
        val wand = Wand("quality-${ctx.level.gameTime}-${ids.joinToString()}")
        wand.innate += ids.map { MagicManager.create(it)!! }; wand.load(); wand.status.mana = 1000.0
        check(wand.spell(source))
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
                if (t == 65) cast(ctx, listOf("speed_boost", "diffusion", "volatile", "freeze_charge", "small_explosion"), 0.0, floor+1.0, -3.0)
                if (t == 66) {
                    bomb = MagicManager.projectiles.first { it.id == "small_explosion" }
                    bomb!!.status.pos.set(0f, floor+2.0f, 0f)
                    bomb!!.alive = false
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
                68 -> listOf(Check(name, t, "moving-bomb-keeps-modified-explosion", bomb!!.speed > 0 && bomb!!.maxAge >= 40 && bomb!!.status.explosionRadius == 6f && bomb!!.status.explosionDamage == 12.0,
                    "speed=${bomb!!.speed} age=${bomb!!.maxAge} radius=${bomb!!.status.explosionRadius} damage=${bomb!!.status.explosionDamage}"),
                    Check(name, t, "explosion-applies-freeze", FrozenStatus.isFrozen(frozenTarget!!), "frozen=${FrozenStatus.isFrozen(frozenTarget)}"))
                110 -> listOf(Check(name, t, "ice-fire-react-and-consume-both", MagicManager.projectiles.none { it.id == "fireball" && it.alive }, "shots=${MagicManager.projectiles.map { it.id }}"))
                140 -> listOf(Check(name, t, "black-hole-stores-consumed-shot", hole != null && hole!!.storedEnergy > 0 && MagicManager.projectiles.none { it.id == "fireball" && it.alive }, "energy=${hole?.storedEnergy}"))
                155 -> listOf(Check(name, t, "shield-projectile-reflects-and-changes-owner", MagicManager.projectiles.any { it.id == "fireball" && it.caster === ctx.player && it.status.velocity.z < 0 }, "shots=${MagicManager.projectiles.map { it.id to it.status.velocity }}"))
                185 -> {
                    val children = MagicManager.projectiles.filter { it.id == "fireball" && it.alive && "cluster_bomb" !in it.modifiers }
                    listOf(Check(name, t, "terminal-block-hit-creates-inheriting-cluster", children.size == 3 && children.all { it.modifiers["damage_boost"] == 1 && it.status.freezeTicks > 0 && it.status.damage == 6.0 && it.status.velocity.isFinite && it.status.velocity.length() in 0.45f..0.9f }, "hit=$clusterHit all=${MagicManager.projectiles.map { it.id to it.modifiers }} children=${children.map { it.modifiers to it.status.damage }}"))
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
