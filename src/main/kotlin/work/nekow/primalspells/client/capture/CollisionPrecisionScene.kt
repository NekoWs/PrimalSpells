package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.geometry.SweptCollision
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3
import work.nekow.primalspells.wand.Wand
import kotlin.math.abs
import kotlin.random.Random

/** 实际高速反弹/薄形状/命中排序回归；解析几何也覆盖负坐标与世界边缘。 */
object CollisionPrecisionScene {
    fun scene(): Scene {
        val name = "collision_precision"
        var floor = 0
        val checks = linkedMapOf<Int, MutableList<Check>>()
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
        }
        fun light(ctx: CaptureDriver.SceneCtx, start: Vec3, direction: Vec3, visual: Boolean = false,
                  flightSpeed: Float = ConcentratedLight.FLIGHT_SPEED): ConcentratedLight {
            val p = MagicManager.create("concentrated_light") as ConcentratedLight
            if (!visual) p.effects.removeIf { it.phase == EffectPhase.VISUAL }
            p.caster = ctx.player; p.wand = Wand("precision")
            p.position = start.toPosition(); p.velocity = direction.normalize().toVector3f()
            p.speed = flightSpeed / Projectile.LAUNCH_SPEED_SCALE
            p.spell(); p.status.age = ConcentratedLight.CHARGE_TICKS
            return p
        }
        fun wall(ctx: CaptureDriver.SceneCtx, block: net.minecraft.world.level.block.Block) {
            for (x in -5..5) for (z in -5..5) for (y in floor + 2..floor + 12)
                if (x == -5 || x == 5 || z == -5 || z == 5)
                    ctx.level.setBlock(BlockPos(x, y, z), block.defaultBlockState(), 2)
        }
        return Scene(name, "高速多次反弹 / 真实接点 / 双精度", 24, 140,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                for (x in -14..14) for (z in -14..14) for (y in floor + 1..floor + 18)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 0.5, floor + 15.0, 0.5, 0f, 90f)
                ctx.command("time set midnight")
            }, camera = { _, _ -> Pose(0.5, floor + 15.0, 0.5, 0f, 90f) },
            tick = { ctx, t ->
                if (t == 8) {
                    record(t, "aim-passive-is-removed", MagicManager.create("aim") == null, "unregistered aim")
                    val casterCenter = ctx.player.boundingBox.center
                    val self = light(ctx, casterCenter, Vec3(1.0, 0.0, 0.0), flightSpeed = 2f)
                    self.selfDamage = true; self.tick()
                    record(t, "self-damage-waits-until-after-leaving-caster", self.alive && self.status.hitTargets == 0 &&
                        self.position.toVec3() == casterCenter, "alive=${self.alive} hits=${self.status.hitTargets} birth=${self.position}")
                    self.status.velocity.negate(); self.tick()
                    record(t, "returning-projectile-can-still-hit-caster", !self.alive && self.status.hitTargets == 1,
                        "alive=${self.alive} hits=${self.status.hitTargets}")
                    self.alive = false; self.die()
                    val random = Random(8174)
                    for (offset in listOf(0.0, -10000.0, 29_999_900.0, -29_999_900.0)) {
                        var maxError = 0.0
                        var passed = 0
                        repeat(128) {
                            val x = offset + random.nextDouble(-8.0, 8.0)
                            val expected = random.nextDouble(0.05, 0.95)
                            val from = Vec3(x - expected * 0.0625, 2.25, 0.25)
                            val to = from.add(0.0625, 0.0, 0.0)
                            val hit = SweptCollision.entry(from, to, AABB(x, 2.0, 0.0, x + 0.0001, 3.0, 1.0))
                            val error = if (hit == null) Double.POSITIVE_INFINITY else abs(hit.point.x - x)
                            maxError = maxOf(maxError, error)
                            if (hit != null && error < 1e-8 && hit.axes == 1 && hit.normal.x == -1.0) passed++
                        }
                        record(t, "thin-box-exact-entry-$offset", passed == 128, "passed=$passed maxWorldError=$maxError")
                    }
                    val corner = SweptCollision.entry(Vec3(-2.0, 0.5, -1.0), Vec3(2.0, 0.5, 1.0), AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
                    record(t, "corner-keeps-both-impact-axes", corner?.axes == 5 && corner.point == Vec3(0.0, 0.5, 0.0), "$corner")
                    record(t, "departing-wall-contact-does-not-bounce", SweptCollision.entry(Vec3(0.0, 0.5, 0.5),
                        Vec3(-1.0, 0.5, 0.5), AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)) == null, "surface departure")

                    wall(ctx, Blocks.STONE)
                    for (i in 0 until 24) {
                        val contacts = arrayListOf<Vec3>()
                        val p = light(ctx, Vec3(0.23, floor + 7.25, 0.17), Vec3(1.0, 0.0, 0.12 + i * 0.053))
                        p.effects += object : BaseEffect() {
                            override fun onHitBlock(pos: Vector3d) { contacts += pos.toVec3() }
                        }
                        var stuck = false
                        var ticks = 0
                        while (p.alive && ticks++ < 8) {
                            p.tick(); p.status.age++
                            val pos = p.status.pos.toVec3()
                            if (p.alive && (pos.x <= -4 || pos.x >= 5 || pos.z <= -4 || pos.z >= 5)) stuck = true
                        }
                        val exact = contacts.all { abs(it.x + 4) < 1e-7 || abs(it.x - 5) < 1e-7 ||
                            abs(it.z + 4) < 1e-7 || abs(it.z - 5) < 1e-7 }
                        record(t, "six-oblique-contacts-without-sticking-$i", !p.alive && p.status.bounces == 0 &&
                            contacts.size == 6 && exact && !stuck && ticks <= 2,
                            "contacts=${contacts.size} exact=$exact stuck=$stuck ticks=$ticks bounces=${p.status.bounces}")
                        p.alive = false; p.die()
                    }
                    val p = light(ctx, Vec3(0.2, floor + 7.0, 0.4), Vec3(1.0, 0.0, 0.31))
                    p.status.bounces = 100
                    p.tick()
                    val length = p.flightPath.zipWithNext { a, b -> a.distanceTo(b) }.sum()
                    record(t, "all-remaining-time-survives-multiple-bounces", p.alive && p.status.bounces < 95 && abs(length - 64) < 2e-4,
                        "distance=$length bounces=${100 - p.status.bounces} speed=${p.status.velocity.length()}")
                    val crystal = MagicManager.create("dormant_crystal") as Projectile
                    crystal.caster = ctx.player; crystal.wand = Wand("precision-crystal")
                    crystal.position = p.status.pos.toVec3().subtract(Vec3(p.status.velocity).scale(0.5)).toPosition()
                    crystal.velocity = Vector3f(0f, 0f, 1f); crystal.spell()
                    work.nekow.primalspells.magic.effect.lifecycle.SpellInteractions.tick(listOf(p, crystal))
                    record(t, "reflected-velocity-does-not-invent-a-crystal-hit", crystal.storedEnergy == 0.0,
                        "energy=${crystal.storedEnergy} crystal=${crystal.status.pos}")
                    crystal.status.pos.set(p.flightPath[p.flightPath.size / 2].toPosition())
                    work.nekow.primalspells.magic.effect.lifecycle.SpellInteractions.tick(listOf(p, crystal))
                    record(t, "crystal-on-actual-bounced-path-is-hit", crystal.storedEnergy == 36.0,
                        "energy=${crystal.storedEnergy}")
                    crystal.alive = false; crystal.die()
                    p.alive = false; p.die()
                    wall(ctx, Blocks.AIR)

                    // 实体的扩张命中盒只比墙面晚0.01格，原来的先实体后墙会误伤。
                    val dummy = ctx.spawnDummy(0.0, floor + 6.0, 0.0)
                    val bodyFront = dummy.boundingBox.minZ - 0.15
                    val start = Vec3(0.0, dummy.boundingBox.center.y, bodyFront - 1.01)
                    val blockZ = kotlin.math.floor(bodyFront).toInt()
                    // 故意让实体箱与墙重叠，箱的入射面仅比墙面晚0.01格。
                    dummy.setPos(0.0, dummy.y, blockZ + dummy.bbWidth / 2.0 + 0.16)
                    ctx.level.setBlock(BlockPos(0, kotlin.math.floor(start.y).toInt(), blockZ), Blocks.STONE.defaultBlockState(), 2)
                    val blocked = light(ctx, Vec3(0.2, start.y, blockZ - 1.01), Vec3(0.0, 0.0, 1.0))
                    blocked.status.bounces = 0; blocked.tick()
                    record(t, "block-occludes-entity-before-damage", dummy.totalDamage == 0.0 && !blocked.alive &&
                        abs(blocked.status.pos.z - blockZ) < 1e-8, "damage=${dummy.totalDamage} point=${blocked.status.pos}")
                    blocked.die(); dummy.discard()
                    ctx.level.setBlock(BlockPos(0, kotlin.math.floor(start.y).toInt(), blockZ), Blocks.AIR.defaultBlockState(), 2)

                    val farTarget = ctx.spawnDummy(-2.0, floor + 6.0, 1.0)
                    val nearTarget = ctx.spawnDummy(-2.0, floor + 6.0, -1.0)
                    val nearest = light(ctx, Vec3(-2.0, nearTarget.boundingBox.center.y, -3.01), Vec3(0.0, 0.0, 1.0))
                    nearest.tick()
                    record(t, "nearest-entry-wins-over-entity-creation-order", nearTarget.totalDamage == 36.0 && farTarget.totalDamage == 0.0 &&
                        abs(nearest.status.pos.z - (nearTarget.boundingBox.minZ - nearest.hitRadius)) < 1e-8,
                        "near=${nearTarget.totalDamage} far=${farTarget.totalDamage} point=${nearest.status.pos}")
                    nearest.alive = false; nearest.die(); nearTarget.discard(); farTarget.discard()

                    // 原版末地烛边角只穿过极短弦段，两个子步端点都在外面。
                    val rod = BlockPos(0, floor + 8, 0)
                    ctx.level.setBlock(rod, Blocks.END_ROD.defaultBlockState(), 2)
                    val thin = light(ctx, Vec3(-1.005, floor + 8.5, 2.237), Vec3(1.0, 0.0, -1.0))
                    thin.status.bounces = 0; thin.tick()
                    record(t, "native-thin-shape-grazing-is-not-skipped", !thin.alive &&
                        thin.status.pos.x in 0.374999..0.625001 && thin.status.pos.z in 0.374999..0.625001,
                        "alive=${thin.alive} point=${thin.status.pos}")
                    thin.alive = false; thin.die()
                    ctx.level.setBlock(rod, Blocks.AIR.defaultBlockState(), 2)

                    // 栅栏上半段在所属方块上方，仍必须检测到，不能只查当前格。
                    val fence = BlockPos(0, floor + 8, 0)
                    ctx.level.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 2)
                    val tall = light(ctx, Vec3(-1.01, floor + 9.25, 0.5), Vec3(1.0, 0.0, 0.0))
                    tall.status.bounces = 0; tall.tick()
                    record(t, "native-shape-above-owner-block-is-detected", !tall.alive && abs(tall.status.pos.x - 0.375) < 1e-8,
                        "alive=${tall.alive} point=${tall.status.pos}")
                    tall.alive = false; tall.die()
                    ctx.level.setBlock(fence, Blocks.AIR.defaultBlockState(), 2)

                    // 角点不等长入射分量分别反射，不能按平均法线交换X/Z速度。
                    val asymmetric = light(ctx, Vec3(0.0, floor + 9.0, 0.0), Vec3(1.0, 0.0, 0.0))
                    asymmetric.status.velocity.set(4f, 0f, 2f)
                    asymmetric.hitBlock(asymmetric.status.pos.toVec3().toPosition(), Vector3f(-1f, 0f, -1f).normalize(), 5)
                    record(t, "unequal-corner-components-are-reflected-independently", asymmetric.status.velocity == Vector3f(-4f, 0f, -2f),
                        "velocity=${asymmetric.status.velocity}")
                    asymmetric.alive = false; asymmetric.die()

                    // 远坐标无碰撞实跑：每刻1/16格不能被float世界坐标吃掉。
                    val far = light(ctx, Vec3(29_999_900.125, floor + 7.123456, -29_999_900.375), Vec3(1.0, 0.0, 0.0))
                    far.collisionEnabled = false
                    val origin = far.status.pos.toVec3()
                    far.tick(); far.status.velocity.set(0.0625f, 0f, 0f)
                    val before = far.status.pos.toVec3()
                    repeat(64) { far.tick() }
                    record(t, "world-edge-retains-sub-block-motion", abs(far.status.pos.x - before.x - 4.0) < 1e-7 &&
                        abs(far.status.pos.y - origin.y) < 1e-10, "travel=${far.status.pos.x - before.x} yError=${far.status.pos.y - origin.y}")
                    far.alive = false; far.die()
                }
                if (t == 18) wall(ctx, Blocks.STONE)
                if (t in listOf(20, 55, 90)) {
                    val p = light(ctx, Vec3(-2.75, floor + 6.0, -2.5), Vec3(1.0, 0.015, 0.51 + t * 0.003), true)
                    // 录下真实部署、蓄能、五次反射及余辉，不能用预画路径代替Move。
                    p.status.age = 0
                    MagicManager.add(p)
                }
                if (t == 116) MagicManager.projectiles.forEach { it.alive = false }
            }, checksAt = { _, t -> checks.remove(t) ?: emptyList() })
    }
}
