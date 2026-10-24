package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3
import work.nekow.primalspells.wand.Wand

/** 在真实木桩上测量穿刺的命中数与伤害，不以HUD合并跳字代替实际结算。 */
object PiercingContactScene {
    fun scene(): Scene {
        val name = "piercing_contact"
        var floor = 0
        var visualDummy: TestDummyEntity? = null
        val checks = linkedMapOf<Int, MutableList<Check>>()
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
        }
        // 用真实增伤抵消穿刺减伤，保持5HP的接触时钟标尺；实际减伤另由触发回归验证。
        fun frequencyModifiers() = (listOf("piercing") + List(5) { "damage_boost" }).map { SpellRegistry.create(it) as Modifier }
        fun shot(ctx: CaptureDriver.SceneCtx, start: Vec3, multiplier: Float = 1f,
                 visual: Boolean = false): Projectile {
            val p = SpellRegistry.create("fireball") as Projectile
            SpellAssembly.apply(p, frequencyModifiers())
            if (!visual) p.effects.removeIf { it.phase == EffectPhase.VISUAL }
            p.caster = ctx.player; p.wand = Wand("piercing-contact")
            p.position = start.toPosition(); p.velocity = Vector3f(0f, 0f, 1f)
            p.speed *= multiplier
            p.spell()
            return p
        }
        return Scene(name, "穿刺接触计时 / 法术伤害频率", 24, 140,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                for (x in -12..12) for (z in -12..12) for (y in floor + 1..floor + 12)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 2.0, floor + 3.0, -4.0, 22f, 25f)
            }, camera = { _, _ -> Pose(2.0, floor + 3.0, -4.0, 22f, 25f) },
            tick = { ctx, t ->
                if (t == 8) {
                    checks.getOrPut(t) { arrayListOf() }.addAll(DamageFrequencyChecks.run(ctx, t, floor))
                    for ((multiplier, expected) in listOf(0.25f to 15, 0.5f to 8, 1f to 4, 2f to 2, 4f to 1)) {
                        val dummy = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                        val start = Vec3(0.0, dummy.boundingBox.center.y, dummy.boundingBox.minZ - 0.2 - 0.0625)
                        val p = shot(ctx, start, multiplier)
                        var ticks = 0
                        while (p.alive && p.status.pos.z <= dummy.boundingBox.maxZ + p.hitRadius && ticks++ < 8) {
                            p.tick(); p.status.age++
                        }
                        record(t, "speed-$multiplier-hit-count", p.status.hitTargets == expected && dummy.totalDamage == expected * 5.0 && p.alive,
                            "hits=${p.status.hitTargets} damage=${dummy.totalDamage} expected=$expected alive=${p.alive}")
                        p.alive = false; p.die(); dummy.discard()
                    }
                    // 接点在刻内不同位置、或跨越刻边界，不应改变默认穿透的次数。
                    for (distance in listOf(0.001, 0.031, 1.127, 3.8)) {
                        val dummy = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                        val p = shot(ctx, Vec3(0.0, dummy.boundingBox.center.y, dummy.boundingBox.minZ - 0.2 - distance))
                        repeat(3) { p.tick(); p.status.age++ }
                        record(t, "entry-offset-$distance", p.status.hitTargets == 4 && dummy.totalDamage == 20.0,
                            "hits=${p.status.hitTargets} damage=${dummy.totalDamage}")
                        p.alive = false; p.die(); dummy.discard()
                    }
                    val dummy = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                    val slow = shot(ctx, dummy.boundingBox.center, 0.00025f)
                    slow.tick(); slow.status.age++
                    val first = dummy.totalDamage
                    slow.tick(); slow.status.age++
                    record(t, "persistent-contact-is-time-based", first == 80.0 && dummy.totalDamage == 160.0 && slow.status.hitTargets == 32,
                        "first=$first total=${dummy.totalDamage} hits=${slow.status.hitTargets}")
                    slow.status.pos.set(dummy.boundingBox.center.add(0.0, 0.0, 2.0).toPosition())
                    slow.tick(); slow.status.age++
                    slow.status.pos.set(dummy.boundingBox.center.toPosition())
                    slow.tick(); slow.status.age++
                    record(t, "reentry-is-not-permanent-immunity", dummy.totalDamage == 240.0,
                        "total=${dummy.totalDamage}")
                    slow.alive = false; slow.die(); dummy.discard()

                    // 扩张箱入射面为精确的-0.25，避免夹具自身的浮点误差把刻末移到下一刻。
                    val boundaryTarget = ctx.spawnDummy(0.0, floor + 5.0, 0.2)
                    val boundary = SpellRegistry.create("fireball") as Projectile
                    SpellAssembly.apply(boundary, frequencyModifiers())
                    boundary.effects.removeIf { it.phase == EffectPhase.VISUAL }
                    boundary.dragCoeff = 0f
                    boundary.caster = ctx.player; boundary.wand = Wand("contact-boundary")
                    boundary.position = Vec3(0.0, boundaryTarget.boundingBox.center.y,
                        boundaryTarget.boundingBox.minZ - boundary.hitRadius - 4.0).toPosition()
                    boundary.velocity = Vector3f(0f, 0f, 1f); boundary.spell(); boundary.tick(); boundary.status.age++
                    val boundaryFirst = boundary.status.hitTargets
                    boundary.tick()
                    record(t, "first-entry-at-tick-end-is-immediate-and-not-duplicated", boundaryFirst == 1 && boundary.status.hitTargets == 4 && boundaryTarget.totalDamage == 20.0,
                        "first=$boundaryFirst hits=${boundary.status.hitTargets} damage=${boundaryTarget.totalDamage}")
                    boundary.alive = false; boundary.die(); boundaryTarget.discard()

                    val near = ctx.spawnDummy(0.0, floor + 5.0, -1.0)
                    val far = ctx.spawnDummy(0.0, floor + 5.0, 1.0)
                    val multi = shot(ctx, Vec3(0.0, near.boundingBox.center.y, -2.0))
                    multi.tick(); multi.status.age++; multi.tick()
                    record(t, "targets-have-independent-contact-clocks", near.totalDamage == 20.0 && far.totalDamage == 20.0,
                        "near=${near.totalDamage} far=${far.totalDamage} hits=${multi.status.hitTargets}")
                    multi.alive = false; multi.die(); near.discard(); far.discard()

                    val sawTarget = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                    val saw = SpellRegistry.create("sawblade") as Projectile
                    saw.effects.removeIf { it.phase == EffectPhase.VISUAL }
                    saw.caster = ctx.player; saw.wand = Wand("contact-saw")
                    saw.position = sawTarget.boundingBox.center.toPosition(); saw.velocity = Vector3f(0f, 0f, 0.0001f)
                    saw.spell(); saw.tick(); saw.status.age++
                    val firstSawDamage = sawTarget.totalDamage
                    saw.tick()
                    record(t, "saw-still-damages-once-per-target-per-tick", firstSawDamage == 12.0 && sawTarget.totalDamage == 24.0,
                        "first=$firstSawDamage total=${sawTarget.totalDamage} callbacks=${saw.status.hitTargets}")
                    saw.alive = false; saw.die(); sawTarget.discard()

                    val returnTarget = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                    val returnY = returnTarget.boundingBox.center.y
                    val returnWall = BlockPos(0, kotlin.math.floor(returnY).toInt(), 2)
                    ctx.level.setBlock(returnWall, Blocks.STONE.defaultBlockState(), 2)
                    val returning = shot(ctx, Vec3(0.1, returnY, -1.0), 2f)
                    returning.maxBounces = 1; returning.status.bounces = 1; returning.bounceScaler.set(1f)
                    returning.tick()
                    record(t, "same-tick-wall-return-can-damage-again", returning.alive && returning.status.bounces == 0 &&
                        returning.status.hitTargets == 4 && returnTarget.totalDamage == 20.0 && returning.status.velocity.z < 0,
                        "hits=${returning.status.hitTargets} damage=${returnTarget.totalDamage} pos=${returning.status.pos}")
                    returning.alive = false; returning.die(); returnTarget.discard()
                    ctx.level.setBlock(returnWall, Blocks.AIR.defaultBlockState(), 2)

                    val ordinaryTarget = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                    val ordinary = SpellRegistry.create("fireball") as Projectile
                    ordinary.effects.removeIf { it.phase == EffectPhase.VISUAL }
                    ordinary.caster = ctx.player; ordinary.wand = Wand("contact-ordinary")
                    ordinary.position = Vec3(0.0, ordinaryTarget.boundingBox.center.y, -1.0).toPosition()
                    ordinary.velocity = Vector3f(0f, 0f, 1f); ordinary.spell(); ordinary.tick()
                    record(t, "ordinary-projectile-still-hits-once", !ordinary.alive && ordinary.status.hitTargets == 1 && ordinaryTarget.totalDamage == 5.0,
                        "hits=${ordinary.status.hitTargets} damage=${ordinaryTarget.totalDamage}")
                    ordinary.die(); ordinaryTarget.discard()

                    val thinTarget = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                    val thinY = thinTarget.boundingBox.center.y
                    thinTarget.boundingBox = net.minecraft.world.phys.AABB(-0.25, thinY - 0.5, 0.012, 0.25, thinY + 0.5, 0.0125)
                    // 整个交集都在一个空间子步内部，两个端点均在箱外。
                    val narrow = SpellRegistry.create("fireball") as Projectile
                    SpellAssembly.apply(narrow, frequencyModifiers())
                    narrow.effects.removeIf { it.phase == EffectPhase.VISUAL }
                    narrow.hitRadius = 0.0001; narrow.speed *= 4f
                    narrow.caster = ctx.player; narrow.wand = Wand("contact-thin")
                    narrow.position = Vec3(0.0, thinY, -1.0).toPosition(); narrow.velocity = Vector3f(0f, 0f, 1f)
                    narrow.spell(); narrow.tick()
                    record(t, "substep-thin-contact-is-not-lost", narrow.status.hitTargets == 1 && thinTarget.totalDamage == 5.0,
                        "hits=${narrow.status.hitTargets} damage=${thinTarget.totalDamage}")
                    narrow.alive = false; narrow.die(); thinTarget.discard()

                    // 穿刺的多次接触仍须按真实时间排序，不能穿墙追加伤害。
                    val occluded = ctx.spawnDummy(0.0, floor + 5.0, 0.7)
                    val y = occluded.boundingBox.center.y
                    val wall = BlockPos(0, kotlin.math.floor(y).toInt(), 0)
                    ctx.level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2)
                    val blocked = shot(ctx, Vec3(0.1, y, -1.0))
                    blocked.tick()
                    record(t, "wall-precedes-all-entity-pulses", !blocked.alive && occluded.totalDamage == 0.0,
                        "alive=${blocked.alive} damage=${occluded.totalDamage}")
                    blocked.die(); occluded.discard()
                    ctx.level.setBlock(wall, Blocks.AIR.defaultBlockState(), 2)

                    val turningTarget = ctx.spawnDummy(0.0, floor + 5.0, 0.0)
                    val turning = shot(ctx, Vec3(0.0, turningTarget.boundingBox.center.y, -1.0))
                    val contacts = arrayListOf<Vec3>()
                    turning.effects += object : BaseEffect() {
                        override fun onHitEntity(target: net.minecraft.world.entity.Entity) { contacts += turning.status.pos.toVec3() }
                    }
                    turning.tick()
                    record(t, "repeat-hits-advance-along-flight", contacts.size == 4 && contacts.zipWithNext().all { (a, b) -> b.z > a.z + 0.2 },
                        "points=${contacts.map { it.z }}")
                    turning.alive = false; turning.die(); turningTarget.discard()
                }
                if (t in listOf(40, 65, 90)) {
                    visualDummy?.discard()
                    val dummy = ctx.spawnDummy(0.0, floor + 1.0, 1.0)
                    dummy.isInvisible = true // 暖色像素门禁只观察法术，不把木桩木材当作特效。
                    visualDummy = dummy
                    val p = shot(ctx, Vec3(0.0, dummy.boundingBox.center.y, -5.0),
                        when (t) { 40 -> 0.5f; 65 -> 1f; else -> 2f }, visual = true)
                    p.maxAge = 10
                    ProjectileRuntime.add(p)
                }
                if (t == 115) {
                    ProjectileRuntime.projectiles.forEach { it.alive = false }
                    visualDummy?.discard()
                }
            }, checksAt = { _, t -> checks[t].orEmpty() })
    }
}
