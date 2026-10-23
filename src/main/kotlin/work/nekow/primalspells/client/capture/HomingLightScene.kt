package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.acos
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.lighting.DynamicLightManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.visual.FlightTrace
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3
import work.nekow.primalspells.wand.Wand

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
        fun shot(ctx: CaptureDriver.SceneCtx, id: String, start: Vec3, homing: Int = 1,
                 extras: List<String> = emptyList(), flightSpeed: Float? = null): Projectile {
            val p = SpellRegistry.create(id) as Projectile
            SpellAssembly.apply(p, (List(homing) { "homing" } + extras).map { SpellRegistry.create(it) as Modifier })
            p.caster = ctx.player; p.wand = Wand("homing-light-$id")
            p.position = start.toPosition(); p.velocity = Vector3f(0f, 0f, 1f)
            if (flightSpeed != null) p.speed = flightSpeed / Projectile.LAUNCH_SPEED_SCALE
            p.spell()
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
                    val aimOrigin = Vec3(0.0, floor + 8.0, -8.0)
                    val rear = ctx.spawnDummy(0.0, floor + 7.0, -9.0)
                    val ahead = ctx.spawnDummy(0.0, rear.y, -3.0)
                    val lateral = ctx.spawnDummy(2.5, rear.y, -6.0)
                    val picker = shot(ctx, "fireball", Vec3(aimOrigin.x, ahead.boundingBox.center.y, aimOrigin.z))
                    picker.collisionEnabled = false; picker.status.velocity.set(0f, 0f, 0.5f); picker.tick()
                    record(t, "forward-alignment-beats-nearer-rear-and-side-targets", picker.guidance?.target == ahead.boundingBox.center,
                        "selected=${picker.guidance?.target} ahead=${ahead.boundingBox.center}")
                    ahead.discard(); lateral.discard()
                    picker.tick()
                    record(t, "rear-only-target-does-not-cause-u-turn", picker.guidance == null && picker.status.velocity.z > 0f,
                        "guidance=${picker.guidance} velocity=${picker.status.velocity}")
                    picker.alive = false; picker.die(); rear.discard()

                    val first = ctx.spawnDummy(0.0, floor + 7.0, -4.0)
                    val second = ctx.spawnDummy(1.5, first.y, 3.0)
                    val chainStart = Vec3(0.0, first.boundingBox.center.y, -8.0)
                    val chain = shot(ctx, "concentrated_light", chainStart, extras = listOf("piercing")) as ConcentratedLight
                    chain.status.age = ConcentratedLight.CHARGE_TICKS; chain.tick()
                    val contact = chain.flightPath.indexOfFirst { first.boundingBox.inflate(chain.hitRadius).contains(it) }
                    val join = chain.flightPath.drop((contact - 2).coerceAtLeast(0)).take(12)
                        .zipWithNext { a, b -> b.subtract(a) }.filter { it.lengthSqr() > 1e-10 }.map { it.normalize() }
                    val joinAngle = join.zipWithNext().maxOfOrNull { (a, b) -> acos(a.dot(b).coerceIn(-1.0, 1.0)) } ?: 0.0
                    record(t, "instant-light-pierces-and-seeks-next-target-in-same-tick", first.totalDamage > 0.0 && second.totalDamage > 0.0,
                        "damage=${first.totalDamage}/${second.totalDamage} speed=${chain.status.velocity.length()}")
                    record(t, "handoff-preserves-tangent-and-blends-turn-in", contact >= 0 && join.size >= 8 && joinAngle < 0.025,
                        "contact=$contact maxJoinAngle=$joinAngle")
                    chain.alive = false; chain.die(); first.discard(); second.discard()

                    val straight = shot(ctx, "concentrated_light", aimOrigin, homing = 0) as ConcentratedLight
                    straight.collisionEnabled = false; straight.status.age = ConcentratedLight.CHARGE_TICKS; straight.tick()
                    val trace = FlightTrace.sample(straight, aimOrigin, straight.status.pos.toVec3(), 0.125)
                    record(t, "laser-speed-and-dense-continuous-trace", abs(straight.status.pos.toVec3().distanceTo(aimOrigin) - 64.0) < 0.001 &&
                        trace.size >= 513 && trace.zipWithNext().all { (a, b) -> a.position.distanceTo(b.position) <= 0.126 },
                        "distance=${straight.status.pos.toVec3().distanceTo(aimOrigin)} samples=${trace.size}")
                    straight.alive = false; straight.die()

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
                    val entryBend = directions.take(10).zipWithNext().maxOf { (a, b) -> acos(a.dot(b).coerceIn(-1.0, 1.0)) }
                    record(t, "gentler-long-range-steering", entryBend < 0.023, "entryMaxAngle=$entryBend")
                    val sampled = FlightTrace.sample(reference, start, path.last(), 0.125)
                    record(t, "flight-and-light-trace-are-curved", path.maxOf(::chordGap) > 0.25 && sampled.maxOf { chordGap(it.position) } > 0.25,
                        "sweepGap=${path.maxOf(::chordGap)} traceSamples=${sampled.size}")
                    record(t, "substep-turns-are-continuous-with-speed-conserved", bend < 0.22 && abs(reference.status.velocity.length() - ConcentratedLight.FLIGHT_SPEED) < 1e-4,
                        "maxAngle=$bend velocity=${reference.status.velocity}")
                    val blocker = path.map(BlockPos::containing).distinct().firstOrNull { block ->
                        !AABB(block).contains(start) && AABB(block).clip(start, path.last()).isEmpty
                    }
                    reference.alive = false; reference.die()
                    if (blocker != null) ctx.level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 2)
                    var contactedBlock = false
                    val wallProbe = ready()
                    wallProbe.effects += object : work.nekow.primalspells.magic.effect.BaseEffect() {
                        override fun onHitBlock(pos: Vector3d) {
                            if (blocker != null && AABB(blocker).inflate(1e-6).contains(pos.toVec3())) contactedBlock = true
                        }
                    }
                    wallProbe.tick()
                    record(t, "curved-sweep-hits-block-missed-by-straight-chord", blocker != null && contactedBlock,
                        "block=$blocker bounces=${wallProbe.status.bounces} end=${wallProbe.status.pos}")
                    wallProbe.alive = false; wallProbe.die()
                    if (blocker != null) ctx.level.setBlock(blocker, Blocks.AIR.defaultBlockState(), 2)
                    val previousDamage = dummy.totalDamage
                    val hit = ready()
                    repeat(12) { if (hit.alive) { hit.tick(); hit.status.age++ } }
                    record(t, "curved-light-actually-hits-entity-center", dummy.totalDamage - previousDamage == 36.0, "damage=${dummy.totalDamage - previousDamage}")
                    hit.alive = false; hit.die(); dummy.discard()

                    // 真正穿透后，当前刻余程保持接触切线；之后不能回锁已穿过的目标。
                    for ((index, offset) in listOf(Vec3(0.0, 0.0001, 2.0), Vec3(1.5, 0.15, 4.0),
                            Vec3(5.5, 0.0, 2.0), Vec3(0.25, -0.4, 0.8)).withIndex()) {
                        // 高处隔离地面反弹；墙面反射回归由独立场景负责。
                        val origin = Vec3(-8.0, floor + 8.0, -6.0)
                        val victim = ctx.spawnDummy(origin.x + offset.x, origin.y + offset.y, origin.z + offset.z)
                        victim.setPos(victim.x, origin.y + offset.y - victim.bbHeight / 2.0, victim.z)
                        val pierce = shot(ctx, "concentrated_light", origin, extras = listOf("piercing"), flightSpeed = 6f) as ConcentratedLight
                        pierce.status.age = ConcentratedLight.CHARGE_TICKS
                        repeat(16) { if (pierce.alive && victim.totalDamage == 0.0) { pierce.tick(); pierce.status.age++ } }
                        val crossed = victim.totalDamage > 0.0
                        val points = pierce.flightPath.toList()
                        val contact = points.indexOfFirst { victim.boundingBox.inflate(pierce.hitRadius).contains(it) }
                        val exit = points.drop((contact + 1).coerceAtLeast(0)).zipWithNext { a, b -> b.subtract(a) }
                            .filter { it.lengthSqr() > 1e-10 }.map { it.normalize() }
                        val turnAfterHit = exit.zipWithNext().maxOfOrNull { (a, b) -> acos(a.dot(b).coerceIn(-1.0, 1.0)) } ?: 0.0
                        record(t, "piercing-hits-and-exits-tangentially-$index", crossed && pierce.alive && pierce.status.bounces == 5 && contact >= 0 && turnAfterHit < 0.002,
                            "damage=${victim.totalDamage} contact=$contact exitBend=$turnAfterHit velocity=${pierce.status.velocity} bounces=${pierce.status.bounces} target=${victim.boundingBox.center} pos=${pierce.status.pos}")
                        val heading = Vec3(pierce.status.velocity).normalize()
                        val speed = pierce.status.velocity.length()
                        pierce.collisionEnabled = false
                        var unlocked = true
                        val guidanceHistory = arrayListOf<String>()
                        repeat(3) { pierce.tick(); pierce.status.age++; unlocked = unlocked && pierce.guidance == null; guidanceHistory += "${pierce.guidance}" }
                        record(t, "piercing-does-not-return-or-gain-speed-$index", crossed && unlocked &&
                            Vec3(pierce.status.velocity).normalize().dot(heading) > 0.99999 && abs(pierce.status.velocity.length() - speed) < 1e-4,
                            "unlocked=$unlocked velocity=${pierce.status.velocity} originalSpeed=$speed target=${victim.boundingBox.center} guidance=$guidanceHistory")
                        if (index == 0 && crossed) {
                            // 回到真实重叠区，证明不再选靶并没有变成伤害去重。
                            pierce.collisionEnabled = true; pierce.status.pos.set(victim.boundingBox.center.toVector3f())
                            pierce.status.velocity.set(0f, 0f, 0.01f)
                            val before = victim.totalDamage
                            pierce.tick(); pierce.status.age++; val first = victim.totalDamage
                            pierce.tick(); pierce.status.age++
                            record(t, "piercing-still-damages-on-consecutive-ticks", first > before && victim.totalDamage > first,
                                "damage=$before/$first/${victim.totalDamage}")
                            // 在未经过的目标上重新制导，不能因跳过旧目标而关闭整个追踪修正。
                            val next = ctx.spawnDummy(origin.x + 1.0, victim.y, origin.z + 5.0)
                            pierce.status.pos.set(origin.x, victim.boundingBox.center.y, (origin.z + 3.0))
                            pierce.status.velocity.set(0f, 0f, ConcentratedLight.FLIGHT_SPEED)
                            repeat(12) { if (pierce.alive && next.totalDamage == 0.0) { pierce.tick(); pierce.status.age++ } }
                            record(t, "piercing-can-seek-another-target", next.totalDamage > 0.0, "damage=${next.totalDamage}")
                            next.discard()
                        }
                        pierce.alive = false; pierce.die(); victim.discard()
                    }

                    val recipes = listOf(listOf("fireball") to 1.0, listOf("piercing", "fireball") to 11.0,
                        listOf("fireball", "piercing") to 11.0, listOf("piercing", "piercing", "fireball") to 21.0,
                        listOf("piercing", "chainsaw") to 0.0)
                    recipes.forEachIndexed { index, (cards, expected) ->
                        // 两张投射物预算将尾部修正也纳入当前链，避免把下一链误当牌序差异。
                        val wand = Wand("pierce-delay-$index"); wand.cast = 2
                        wand.innate += cards.map { requireNotNull(SpellRegistry.create(it)) }; wand.load(); wand.status.mana = wand.mana
                        val plan = wand.preview()
                        val cast = wand.spell(ctx.player)
                        record(t, "piercing-cooldown-through-real-cast-$index", cast && plan.delay == expected && wand.status.delay == expected,
                            "cards=$cards preview=${plan.delay} real=${wand.status.delay} expected=$expected")
                    }
                }
                if (t == 9) ProjectileRuntime.projectiles.filter { it.wand.id.startsWith("pierce-delay-") }.forEach { it.alive = false }
                if (t == 40) {
                    val dummy = ctx.spawnDummy(1.0, floor + 2.0, 8.0)
                    target = dummy
                    lightPoint = Vec3(-4.0, dummy.boundingBox.center.y, -8.0)
                    display = (shot(ctx, "concentrated_light", lightPoint, extras = listOf("light")) as ConcentratedLight)
                        .also(ProjectileRuntime::add)
                }
                if (t == 47) {
                    val beam = checkNotNull(display)
                    record(t, "charge-remains-at-deployment-point", beam.awaitingRelease && beam.status.pos.z == -8.0,
                        "age=${beam.status.age} pos=${beam.status.pos}")
                }
                if (t == 54) {
                    val beam = checkNotNull(display)
                    record(t, "release-at-ten-ticks", beam.releasedAtAge == 10, "released=${beam.releasedAtAge}")
                }
                if (t == 82) {
                    val charge = light.filterKeys { it in 42..49 }.values
                    record(t, "charge-with-light-modifier-emits-real-light", charge.size >= 4 && charge.max() > 10.0,
                        "samples=$light")
                    val dummy = checkNotNull(target)
                    record(t, "runtime-guided-beam-hits", dummy.totalDamage == 36.0, "damage=${dummy.totalDamage}")
                    dummy.discard()
                }
                if (t == 120) ProjectileRuntime.projectiles.forEach { it.alive = false }
            }, clientTick = { _, t ->
                if (t in 40..100) ClientParticleEngine.instance()?.let { engine ->
                    DynamicLightManager.renderDynamicLights(engine)
                    light[t] = DynamicLightManager.getDynamicLightLevel(lightPoint.x, lightPoint.y, lightPoint.z)
                }
            }, checksAt = { ctx, t ->
                if (t == 149) {
                    record(t, "light-and-particles-cleaned", light[100] == 0.0 && ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
                        "light=${light[100]} particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                    record(t, "projectiles-cleaned", ProjectileRuntime.projectiles.isEmpty(), "remaining=${ProjectileRuntime.projectiles.size}")
                }
                checks[t].orEmpty()
            }, finish = { target?.discard() })
    }
}
