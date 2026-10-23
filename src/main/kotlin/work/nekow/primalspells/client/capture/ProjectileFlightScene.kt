package work.nekow.primalspells.client.capture

import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ChargedProjectiles
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.particledrawing.core.client.RenderParticle
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.util.toPosition
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 实机比较原版弩箭、验证高速掠过索敌，以及真实发射器的逐粒透明度与变速更新。 */
object ProjectileFlightScene {
    /** 仅由显式开发录制注册；所有伤害断言走实际扫掠命中。 */
    fun scene(): Scene {
        val name = "projectile_flight"
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        val alphaBefore = IdentityHashMap<RenderParticle, Float>()
        val alphaFalls = AtomicInteger()
        val faded = AtomicInteger()
        val alphaSamples = ConcurrentHashMap<String, Double>()
        var floor = 0
        var display: Projectile? = null
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
        }
        fun shot(ctx: CaptureDriver.SceneCtx, position: Vec3, direction: Vector3f, mods: List<String> = emptyList()): Projectile {
            val p = MagicManager.create("fireball") as Projectile
            SpellAssembly.apply(p, mods.map { MagicManager.create(it) as Modifier })
            p.caster = ctx.player; p.wand = Wand("flight-$position")
            p.position = position.toPosition(); p.velocity = direction; p.spell()
            return p
        }
        return Scene(name, "高速发射 / 飞行段追踪 / 拖尾寿命淡出", 24, 130,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0); ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 0.0, floor + 4.0, -10.0, 0f, 5f)
            }, camera = { _, _ -> Pose(0.0, floor + 4.0, -10.0, 0f, 5f) },
            tick = { ctx, t ->
                if (t == 8) {
                    val bow = ItemStack(Items.CROSSBOW)
                    bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(listOf(ItemStack(Items.ARROW))))
                    ctx.player.setItemInHand(InteractionHand.OFF_HAND, bow)
                    ctx.player.deltaMovement = Vec3.ZERO
                    Items.CROSSBOW.use(ctx.level, ctx.player, InteractionHand.OFF_HAND)
                    val arrows = ctx.level.getEntitiesOfClass(AbstractArrow::class.java, ctx.player.boundingBox.inflate(8.0))
                    val arrowSpeed = arrows.maxOfOrNull { it.deltaMovement.length() } ?: 0.0
                    val p = shot(ctx, Vec3(-5.0, floor + 3.0, 0.0), Vector3f(0f, 0f, 1f))
                    val launch = p.status.velocity.length()
                    p.collisionEnabled = false; p.tick()
                    record(t, "default-launch-and-first-move-exceed-real-crossbow", arrowSpeed in 3.0..3.3 && launch == 4f && p.status.velocity.length() > arrowSpeed,
                        "crossbow=$arrowSpeed launch=$launch moved=${p.status.velocity.length()}")
                    val child = SpellAssembly.copy(p)
                    child.caster = ctx.player; child.wand = p.wand; child.position = p.position
                    child.velocity = SpellAssembly.launch(child, Vector3f(0f, 0f, 1f), 4f); child.spell()
                    record(t, "copied-projectile-keeps-speed-without-double-scaling", child.status.velocity.length() == 4f, child.status.velocity.toString())
                    p.alive = false; p.die(); child.alive = false; child.die()
                    arrows.forEach { it.discard() }; ctx.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
                }
                if (t == 20 || t == 30) {
                    val offset = if (t == 20) 5.9 else 6.1
                    val target = ctx.spawnDummy(-5.0 + offset, floor + 2.0, 2.0)
                    val start = Vec3(-5.0, target.boundingBox.center.y, 0.0)
                    val p = shot(ctx, start, Vector3f(0f, 0f, 1f), listOf("homing"))
                    val before = p.status.velocity.length()
                    p.tick()
                    if (t == 20) {
                        record(t, "swept-homing-acquires-between-tick-endpoints", p.guidance != null && p.status.velocity.x > 0.1f,
                            "velocity=${p.status.velocity} damage=${target.totalDamage}")
                        record(t, "homing-does-not-accelerate-projectile", p.status.velocity.length() <= before + 1e-5f, "before=$before after=${p.status.velocity.length()}")
                    } else record(t, "homing-stays-within-six-blocks-of-flight-path", p.status.velocity.x == 0f && target.totalDamage == 0.0,
                        "velocity=${p.status.velocity} damage=${target.totalDamage}")
                    if (t == 20) {
                        repeat(12) { if (p.alive) { p.tick(); p.status.age++ } }
                        record(t, "expanded-homing-actually-hits", target.totalDamage == 5.0, "damage=${target.totalDamage}")
                    }
                    p.alive = false; p.die(); target.discard()
                }
                if (t == 40) {
                    val p = shot(ctx, Vec3(-5.0, floor + 3.0, 0.0), Vector3f(0f, 0f, 1f), listOf("homing"))
                    p.status.velocity.zero(); p.tick()
                    record(t, "stationary-homing-remains-finite", p.status.velocity.isFinite && p.status.pos.isFinite, p.status.velocity.toString())
                    p.alive = false; p.die()
                }
                if (t == 60) {
                    display = shot(ctx, Vec3(-12.0, floor + 3.0, 0.0), Vector3f(1f, 0f, 0f)).also {
                        it.maxAge = 26; MagicManager.add(it)
                    }
                }
                // 外部受力可改变速度；验证发射器热更新寿命后仍保留alpha通道，而不是只验证静态曲线。
                if (t == 63) display?.status?.velocity?.set(0.45f, 0f, 0f)
                if (t == 100) record(t, "real-trail-particles-fade-after-speed-change", alphaFalls.get() > 2 && faded.get() > 2 && (alphaSamples["minimum"] ?: 1.0) < 0.4,
                    "decreasing=${alphaFalls.get()} faded=${faded.get()} minimum=${alphaSamples["minimum"]}")
                if (t == 125) record(t, "projectiles-cleaned", MagicManager.projectiles.isEmpty(), "remaining=${MagicManager.projectiles.size}")
            }, clientTick = { _, t ->
                if (t in 64..90) ClientParticleEngine.instance()?.getGlowingParticles()?.forEach { p ->
                    val alpha = p.effectiveAlpha()
                    if (alpha in 0.01f..0.98f) {
                        faded.incrementAndGet()
                        alphaSamples["minimum"] = minOf(alphaSamples["minimum"] ?: 1.0, alpha.toDouble())
                        alphaBefore[p]?.let { if (alpha < it - 0.02f) alphaFalls.incrementAndGet() }
                        alphaBefore[p] = alpha
                    }
                }
                if (t == 126) record(t, "pd-particles-zero", ClientParticleEngine.instance()?.activeCount() == 0, "remaining=${ClientParticleEngine.instance()?.activeCount()}")
            }, checksAt = { _, t -> checks[t]?.toList().orEmpty() }, finish = { _ -> alphaBefore.clear() })
    }
}
