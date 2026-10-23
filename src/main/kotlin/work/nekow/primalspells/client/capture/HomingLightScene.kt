package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.lighting.DynamicLightManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.effect.visual.FlightTrace
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.acos

/** 实际曲线碰撞、六格索敌、十刻蓄能和PD世界照明的独立回归与取景。 */
object HomingLightScene {
    fun scene(): Scene {
        val name = "homing_light"
        var floor = 0
        var display: ConcentratedLight? = null
        var target: TestDummyEntity? = null
        var lightPoint = Vec3.ZERO
        val light = ConcurrentHashMap<Int, Double>()
        val checks = linkedMapOf<Int, MutableList<Check>>()
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
        }
        fun shot(ctx: CaptureDriver.SceneCtx, id: String, start: Vec3, homing: Int = 1): Projectile {
            val p = MagicManager.create(id) as Projectile
            SpellAssembly.apply(p, List(homing) { MagicManager.create("homing") as Modifier })
            p.caster = ctx.player; p.wand = Wand("homing-light-$id")
            p.position = start.toVector3f(); p.velocity = Vector3f(0f, 0f, 1f); p.spell()
            return p
        }
        return Scene(name, "六格追踪 / 实际弧线路径 / 聚光照明", 24, 150,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                for (x in -12..12) for (z in -12..12) for (y in floor + 1..floor + 12)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 9.0, floor + 12.0, -12.0, 39f, 30f)
                ctx.command("time set midnight")
            }, camera = { _, _ -> Pose(9.0, floor + 12.0, -12.0, 39f, 30f) },
            tick = { ctx, t ->
                if (t == 8) {
                    for ((range, copies, expected) in listOf(Triple(5.9, 1, true), Triple(6.1, 1, false), Triple(8.0, 2, true))) {
                        val dummy = ctx.spawnDummy(-3.0 + range, floor + 2.0, 1.0)
                        val p = shot(ctx, "fireball", Vec3(-3.0, dummy.boundingBox.center.y, 0.0), copies)
                        p.collisionEnabled = false; p.tick()
                        record(t, "expanded-homing-range-$range-$copies", (p.guidance != null) == expected, "guidance=${p.guidance}")
                        p.alive = false; p.die(); dummy.discard()
                    }
                    val dummy = ctx.spawnDummy(-3.25, floor + 2.0, -3.25)
                    val start = Vec3(-8.25, dummy.boundingBox.center.y, -5.25)
                    fun ready(): ConcentratedLight = (shot(ctx, "concentrated_light", start) as ConcentratedLight).also {
                        it.status.age = ConcentratedLight.CHARGE_TICKS
                    }
                    val reference = ready(); reference.collisionEnabled = false
                    reference.tick()
                    val path = reference.flightPath.toList()
                    val chord = path.last().subtract(path.first())
                    fun chordGap(point: Vec3): Double {
                        val q = point.subtract(start).dot(chord) / chord.lengthSqr()
                        return point.distanceTo(start.add(chord.scale(q.coerceIn(0.0, 1.0))))
                    }
                    val directions = path.zipWithNext { a, b -> b.subtract(a).normalize() }
                    val bend = directions.zipWithNext().maxOf { (a, b) -> acos(a.dot(b).coerceIn(-1.0, 1.0)) }
                    val sampled = FlightTrace.sample(reference, start, path.last(), 0.125)
                    record(t, "flight-and-light-trace-are-curved", path.maxOf(::chordGap) > 0.25 && sampled.maxOf { chordGap(it.position) } > 0.25,
                        "sweepGap=${path.maxOf(::chordGap)} traceSamples=${sampled.size}")
                    record(t, "substep-turns-are-continuous-with-speed-conserved", bend < 0.22 && abs(reference.status.velocity.length() - 6f) < 1e-4,
                        "maxAngle=$bend velocity=${reference.status.velocity}")
                    val blocker = path.map(BlockPos::containing).distinct().firstOrNull { block ->
                        !AABB(block).contains(start) && AABB(block).clip(start, path.last()).isEmpty
                    }
                    reference.alive = false; reference.die()
                    if (blocker != null) ctx.level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 2)
                    val wallProbe = ready(); wallProbe.tick()
                    record(t, "curved-sweep-hits-block-missed-by-straight-chord", blocker != null && wallProbe.status.bounces == 4,
                        "block=$blocker bounces=${wallProbe.status.bounces} end=${wallProbe.status.pos}")
                    wallProbe.alive = false; wallProbe.die()
                    if (blocker != null) ctx.level.setBlock(blocker, Blocks.AIR.defaultBlockState(), 2)
                    val hit = ready()
                    repeat(12) { if (hit.alive) { hit.tick(); hit.status.age++ } }
                    record(t, "curved-light-actually-hits-entity-center", dummy.totalDamage == 36.0, "damage=${dummy.totalDamage}")
                    hit.alive = false; hit.die(); dummy.discard()
                }
                if (t == 40) {
                    val dummy = ctx.spawnDummy(1.0, floor + 2.0, 8.0)
                    target = dummy
                    lightPoint = Vec3(-4.0, dummy.boundingBox.center.y, -8.0)
                    display = (shot(ctx, "concentrated_light", lightPoint) as ConcentratedLight)
                        .also(MagicManager::add)
                }
                if (t == 47) {
                    val beam = checkNotNull(display)
                    record(t, "charge-remains-at-deployment-point", beam.awaitingRelease && beam.status.pos.z == -8f,
                        "age=${beam.status.age} pos=${beam.status.pos}")
                }
                if (t == 54) {
                    val beam = checkNotNull(display)
                    record(t, "release-at-ten-ticks", beam.releasedAtAge == 10, "released=${beam.releasedAtAge}")
                }
                if (t == 82) {
                    val charge = light.filterKeys { it in 42..49 }.values
                    record(t, "charge-emits-real-light-and-ramps-up", charge.size >= 4 && charge.max() > charge.min() + 2.0,
                        "samples=$light")
                    val dummy = checkNotNull(target)
                    record(t, "runtime-guided-beam-hits", dummy.totalDamage == 36.0, "damage=${dummy.totalDamage}")
                    dummy.discard()
                }
                if (t == 120) MagicManager.projectiles.forEach { it.alive = false }
            }, clientTick = { _, t ->
                if (t in 40..100) ClientParticleEngine.instance()?.let { engine ->
                    DynamicLightManager.renderDynamicLights(engine)
                    light[t] = DynamicLightManager.getDynamicLightLevel(lightPoint.x, lightPoint.y, lightPoint.z)
                }
            }, checksAt = { ctx, t ->
                if (t == 149) {
                    record(t, "light-and-particles-cleaned", light[100] == 0.0 && ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
                        "light=${light[100]} particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                    record(t, "projectiles-cleaned", MagicManager.projectiles.isEmpty(), "remaining=${MagicManager.projectiles.size}")
                }
                checks[t].orEmpty()
            }, finish = { target?.discard() })
    }
}
