package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.lighting.DynamicLightManager
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.projectile.TriggerFireball
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.spellParticle
import work.nekow.primalspells.util.spellVisual
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.Wand

/** 原生全目录零光源、昼夜全亮、单光源预算、复制继承与荷载隔离。 */
object SpellLightingScene {
    fun scene(): Scene {
        val name = "spell_lighting"
        var floor = 0
        var center = Vec3.ZERO
        var probe: ParticleHandle? = null
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        val sourceCounts = ConcurrentHashMap<Int, Int>()
        val light = ConcurrentHashMap<Int, Double>()
        val particleCounts = ConcurrentHashMap<Int, Int>()
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() } += Check(name, t, id, ok, detail)
        }
        fun cast(ctx: CaptureDriver.SceneCtx, cards: List<String>): Pair<Wand, List<Projectile>> {
            val wand = Wand("lighting-$cards")
            wand.cast = 2
            wand.innate += cards.map { checkNotNull(SpellRegistry.create(it)) }
            wand.load(); wand.status.mana = wand.mana
            val before = ProjectileRuntime.projectiles.toSet()
            check(wand.spell(ctx.player))
            ProjectileRuntime.tick()
            return wand to (ProjectileRuntime.projectiles - before).onEach {
                it.collisionEnabled = false; it.maxAge = 20
                it.status.pos.set(center.toPosition()); it.status.velocity.zero()
            }
        }
        return Scene(name, "法术全亮 / 光照修正 / 单源预算与独立荷载", 24, 220,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                center = Vec3(0.5, floor + 3.0, 1.0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 0.5, floor + 2.0, -7.0, 0f, 0f)
                ctx.command("time set midnight")
            }, camera = { _, _ -> Pose(0.5, floor + 2.0, -7.0, 0f, 0f) },
            tick = { ctx, t ->
                if (t == 8) {
                    val shots = SpellRegistry.factories.values.mapNotNull { it() as? Projectile }
                    shots.forEachIndexed { i, p ->
                        p.caster = ctx.player; p.wand = Wand("default-light-${p.id}")
                        p.position = center.add((i % 5 - 2) * 1.5, i / 5 * 0.3, 0.0).toPosition()
                        p.velocity = Vector3f(0f, 0f, 1f)
                        p.speed = if (p.speed == 0f) 0f else 0.02f
                        p.maxAge = minOf(p.maxAge, 12); p.collisionEnabled = false
                        p.spell(); ProjectileRuntime.add(p)
                    }
                    record(t, "default-projectile-catalog-exercised", shots.size >= 20, "ids=${shots.map { it.id }}")
                }
                if (t == 40) ctx.level.getEntitiesOfClass(DroneEntity::class.java, ctx.player.boundingBox.inflate(64.0)).forEach { it.discard() }
                if (t == 65) {
                    ctx.command("time set noon")
                    probe = ParticleManager.of(ctx.level).spellParticle().position(center)
                        .color(Color.ofInt(155, 90, 235)).visual(spellVisual().anisoWorld(1f, 1f)).lifetime(-1).spawn()
                    val data = probe?.let { ParticleManager.of(ctx.level).getEngine().getParticle(it.id) }
                    record(t, "full-bright-is-not-a-world-light", data != null && data.glowing() && data.lightLevel() == 0,
                        "glowing=${data?.glowing()} level=${data?.lightLevel()}")
                }
                if (t == 75) ctx.command("time set midnight")
                if (t == 90) {
                    probe?.remove(); probe = null
                    record(t, "default-spells-register-no-world-lights", sourceCounts.filterKeys { it in 8..89 }.values.all { it == 0 }, "counts=$sourceCounts")
                    record(t, "default-spells-really-rendered", (particleCounts.filterKeys { it in 8..35 }.values.maxOrNull() ?: 0) > 100,
                        "peak=${particleCounts.values.maxOrNull()}")
                }
                if (t == 100) {
                    val (wand, shots) = cast(ctx, listOf("fireball", "light", "light"))
                    record(t, "real-cast-pays-for-each-light-card", shots.single().modifiers["light"] == 2 && wand.status.mana == wand.mana - 9,
                        "mana=${wand.status.mana}/${wand.mana} mods=${shots.single().modifiers}")
                }
                if (t == 135) {
                    record(t, "duplicate-light-cards-share-one-source", sourceCounts.filterKeys { it in 105..116 }.values.let { it.size >= 8 && it.all { n -> n == 1 } }, "counts=$sourceCounts")
                    val lit = light.filterKeys { it in 105..116 }.values
                    record(t, "light-modifier-illuminates-world", lit.isNotEmpty() && lit.max() > 10, "light=$light")
                    val exit = light.filterKeys { it in 120..134 }.toSortedMap().values.toList()
                    val step = exit.zipWithNext().maxOfOrNull { abs(it.first - it.second) } ?: 99.0
                    record(t, "light-retires-continuously", exit.any { it > 10 } && exit.last() == 0.0 && step < 10,
                        "exit=$exit maxStep=$step")
                }
                if (t == 140) {
                    val (_, shots) = cast(ctx, listOf("light", "fireball"))
                    val parent = shots.single()
                    val child = SpellAssembly.copy(parent)
                    child.caster = ctx.player; child.wand = parent.wand
                    child.position = center.add(2.0, 0.0, 0.0).toPosition(); child.velocity = Vector3f(0f, 0f, 1f)
                    child.speed = 0f; child.maxAge = 20; child.collisionEnabled = false
                    child.spell(); ProjectileRuntime.add(child)
                    record(t, "copies-inherit-light-with-independent-effects", child.modifiers["light"] == 1 &&
                        child.effects.none { effect -> parent.effects.any { it === effect } }, "mods=${child.modifiers}")
                }
                if (t == 170) record(t, "two-projectiles-use-only-two-lights", sourceCounts.filterKeys { it in 145..156 }.values.let { it.size >= 8 && it.all { n -> n == 2 } }, "counts=$sourceCounts")
                if (t == 175) {
                    val (_, shots) = cast(ctx, listOf("trigger_fireball", "light", "fireball"))
                    val carrier = shots.single() as TriggerFireball
                    record(t, "payload-light-does-not-leak-to-carrier", "light" !in carrier.modifiers && carrier.payload.any { it.id == "light" }, "mods=${carrier.modifiers}")
                    carrier.hitBlock(center.toPosition(), Vector3f(0f, 0f, 1f))
                    ProjectileRuntime.tick()
                    val child = ProjectileRuntime.projectiles.last()
                    record(t, "released-payload-receives-light", child !== carrier && child.modifiers["light"] == 1, "mods=${child.modifiers}")
                    child.collisionEnabled = false; child.maxAge = 5; child.status.velocity.zero()
                    carrier.alive = false
                }
                if (t == 215) record(t, "light-particles-and-projectiles-cleaned", sourceCounts[t - 1] == 0 &&
                    ParticleManager.of(ctx.level).getEngine().particleCount() == 0 && ProjectileRuntime.projectiles.isEmpty(),
                    "sources=${sourceCounts[t - 1]} particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
            }, clientTick = { _, t ->
                ClientParticleEngine.instance()?.let { engine ->
                    DynamicLightManager.renderDynamicLights(engine)
                    sourceCounts[t] = engine.getGlowingParticles().size
                    particleCounts[t] = engine.activeCount()
                    light[t] = DynamicLightManager.getDynamicLightLevel(center.x, center.y, center.z)
                }
            }, checksAt = { _, t -> checks[t].orEmpty() }, finish = { probe?.remove() })
    }
}
