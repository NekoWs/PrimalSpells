package work.nekow.primalspells.client.capture

import com.mojang.blaze3d.platform.InputConstants
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.client.hud.*
import work.nekow.primalspells.client.screen.HudEditorScreen
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.SpellImpact
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.network.SyncWandStatsPayload
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.*

/** 真实HUD同步/按键/编辑器输入/落盘与自然下坠、地面反弹的实机回归。 */
object HudSawScene {
    fun hud(): Scene {
        val name = "magic_hud"
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        val shared = ConcurrentHashMap<String, String>()
        var floor = 0
        var wand: Wand? = null
        var target: LivingEntity? = null
        val burstTargets = arrayListOf<LivingEntity>()
        var attacker: work.nekow.primalspells.entity.TestDummyEntity? = null
        var oldLayout: HudLayout? = null
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
        }
        fun mouse(x: Double, y: Double) = MouseButtonEvent(x, y, MouseButtonInfo(0, 0))
        return Scene(name, "魔法HUD / 真实冷却 / 拖动缩放保存取消", 24, 200, showHud = true, allowScreen = true,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0); ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.0, floor + 2.0, -7.0, 0f, 8f)
                ctx.command("gamemode creative @a")
                ctx.command("difficulty normal")
                ctx.player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
            }, camera = { _, _ -> Pose(0.0, floor + 2.0, -7.0, 0f, 8f) },
            clientTick = { mc, t ->
                val width = mc.window.guiScaledWidth; val height = mc.window.guiScaledHeight
                if (t == 0) { oldLayout = HudSettings.current.duplicate(); HudSettings.save(HudLayout(damageDirection = true)) }
                if (t == 3) {
                    checks.getOrPut(t) { arrayListOf() }.addAll(HudImpactChecks.run(name, t))
                    val sample = SyncWandStatsPayload("fps-hud", 68.0, 100.0, 4.0, 12.0, 1, 8.0, 24.0, 0.0, 8.0, 24.0, 6)
                    fun run(step: Double): WandHudFrame {
                        WandHudRenderer.clear(); WandHudRenderer.frame(sample, 0.0)
                        for (i in 1..(4 / step).toInt()) WandHudRenderer.frame(sample, i * step)
                        return WandHudRenderer.frame(sample, 4.0)!!
                    }
                    val slow = run(1.0); val fast = run(0.25)
                    record(t, "hud-animation-independent-of-frame-rate", abs(slow.presence - fast.presence) < 1e-6 &&
                        abs(slow.rechargeAlpha - fast.rechargeAlpha) < 1e-6, "presence=${slow.presence}/${fast.presence}")
                    WandHudRenderer.clear()
                    val fresh = WandHudRenderer.frame(sample, 5.0)!!
                    record(t, "first-frame-starts-hidden", fresh.presence == 0.0 && fresh.rechargeAlpha == 0.0, "presence=${fresh.presence}")
                    val layout = HudLayout(); layout.move(HudElement.MANA, -100.0, 99999.0, width, height)
                    layout.mana.scale = 2.5
                    record(t, "layout-clamps-after-resize-and-scale", listOf(320 to 180, 640 to 360, 2560 to 1440).all { (w, h) ->
                        HudElement.entries.all { val b = layout.box(it, w, h); b.x >= 0 && b.y >= 0 && b.x + b.width <= w && b.y + b.height <= h }
                    }, "three viewports")
                    WandHudRenderer.clear()
                }
                if (t == 24 || t == 39 || t == 84) {
                    val s = shared["id"]?.let { WandHudRenderer.getStats(it) }
                    record(t, "real-network-hud-$t", s != null && when (t) {
                        24 -> s.maxMana == 120.0 && s.equipRemaining == 0
                        39 -> s.currentDelay > 0 && s.currentRecharge > s.currentDelay
                        else -> s.manaBlocked && s.currentMana < 4
                    }, "stats=$s")
                }
                if (t == 57 || t == 62) {
                    val f = WandHudRenderer.snapshot()
                    record(t, "accepted-damage-reaches-hud-$t", f != null && f.impacts.any {
                        it.killed == (t == 62) && it.alpha > 0
                    }, "frame=$f")
                }
                if (t == 95 || t == 98) {
                    val events = WandHudRenderer.snapshot()?.impacts.orEmpty()
                    record(t, "real-same-tick-damage-preserves-each-feedback-$t",
                        events.count { !it.killed } == (if (t == 95) 3 else 1) && events.count { it.killed } == (if (t == 95) 0 else 2),
                        "events=$events")
                }
                if (t == 58) {
                    val events = WandHudRenderer.snapshot()?.impacts.orEmpty()
                    record(t, "real-repeated-single-target-shows-one-feedback", events.size == 1 && !events.single().killed,
                        "events=$events")
                }
                if (t == 70 || t == 77) {
                    val f = WandHudRenderer.snapshot()
                    val expected = if (t == 70) 0.0 else -Math.PI / 2
                    record(t, "real-incoming-direction-$t", f != null && f.hurtAlpha > 0 && f.hurtAngle != null &&
                        abs(f.hurtAngle - expected) < 0.01, "frame=$f")
                }
                if (t == 94) {
                    val s = shared["id"]?.let { WandHudRenderer.getStats(it) }
                    record(t, "mana-warning-clears-after-recovery", s != null && s.currentMana == 100.0 && !s.manaBlocked, "stats=$s")
                }
                if (t == 100 || t == 122) KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H))
                if (t == 103) {
                    val screen = mc.gui.screen() as? HudEditorScreen
                    record(t, "registered-key-opens-real-editor", screen != null, "screen=${mc.gui.screen()?.javaClass?.simpleName}")
                    screen?.let {
                        val b = it.draft.box(HudElement.MANA, width, height)
                        it.mouseClicked(mouse(b.x + b.width / 2, b.y + b.height / 2), false)
                        it.mouseDragged(mouse(width * 0.25, height * 0.35), 0.0, 0.0)
                        it.mouseReleased(mouse(width * 0.25, height * 0.35))
                        record(t, "real-drag-moves-selected-component-only", abs((it.draft.mana.x ?: 0.0) - 0.25) < 0.01 &&
                            it.draft.cooldown.x == null && HudSettings.current.mana.x == null, "draft=${it.draft.mana}")
                    }
                }
                if (t == 108) (mc.gui.screen() as? HudEditorScreen)?.let {
                    val b = it.draft.box(HudElement.MANA, width, height)
                    it.mouseScrolled(b.x + b.width / 2, b.y + b.height / 2, 0.0, 5.0)
                    record(t, "real-wheel-scales-selected-component", abs(it.draft.mana.scale - 1.25) < 1e-6, "scale=${it.draft.mana.scale}")
                }
                if (t == 112) (mc.gui.screen() as? HudEditorScreen)?.let {
                    val b = it.draft.box(HudElement.MANA, width, height)
                    it.mouseClicked(mouse(b.x + b.width, b.y + b.height), false)
                    it.mouseDragged(mouse(b.x + b.width + 32, b.y + b.height + 20), 32.0, 20.0)
                    it.mouseReleased(mouse(b.x + b.width + 32, b.y + b.height + 20))
                    record(t, "real-corner-handle-scales", it.draft.mana.scale > 1.4, "scale=${it.draft.mana.scale}")
                }
                if (t == 118) (mc.gui.screen() as? HudEditorScreen)?.let {
                    val bw = (width / 5).coerceIn(50, 110)
                    it.mouseClicked(mouse(width / 2.0 + bw + 6, height - 15.0), false)
                    val loaded = HudSettings.load(HudSettings.path)
                    record(t, "real-save-button-persists-layout", mc.gui.screen() == null && loaded.mana.scale > 1.4 &&
                        loaded.mana.x == HudSettings.current.mana.x, "loaded=${loaded.mana}")
                }
                if (t == 125) (mc.gui.screen() as? HudEditorScreen)?.let {
                    val saved = HudSettings.current.mana.copy()
                    it.mouseScrolled(0.0, 0.0, 0.0, -4.0)
                    it.keyPressed(KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0))
                    record(t, "escape-discards-editor-draft", mc.gui.screen() == null && HudSettings.current.mana == saved,
                        "current=${HudSettings.current.mana}")
                }
                if (t == 130) oldLayout?.let { HudSettings.save(it) }
            }, tick = { ctx, t ->
                if (t == 5) {
                    val refund = SpellRegistry.create("mana_injection")!!
                    val w = Wand("refund-cap").also { it.mana = 100.0; it.charge = 0.0 }
                    w.status.mana = 99.0
                    record(t, "refund-caps-near-full", w.consumeMana(refund) && w.status.mana == 100.0, "mana=${w.status.mana}")
                    repeat(64) { w.consumeMana(refund) }
                    record(t, "stacked-refunds-never-overflow", w.status.mana == 100.0, "mana=${w.status.mana}")
                    w.status.mana = 0.0; w.consumeMana(refund)
                    record(t, "refund-from-empty-keeps-thirty", w.status.mana == 30.0, "mana=${w.status.mana}")
                    w.status.mana = 999.0; w.tick()
                    record(t, "old-runtime-overflow-corrected", w.status.mana == 100.0, "mana=${w.status.mana}")
                    val tag = w.saveTag(); tag.putDouble("status_mana", 999.0)
                    record(t, "old-save-overflow-corrected", Wand.fromTag(tag).status.mana == 100.0, "saved=999 max=100")
                    w.status.mana = 999.0
                    record(t, "overflow-cannot-buy-expensive-spell", !w.spendMana(200.0) && w.status.mana == 100.0, "mana=${w.status.mana}")
                    w.mana = 0.0; w.consumeMana(refund)
                    record(t, "zero-capacity-refund-stays-zero", w.status.mana == 0.0, "mana=${w.status.mana}")
                    for (ids in listOf(listOf("mana_injection", "fireball"),
                        listOf("mana_injection", "mana_injection", "mana_injection", "fireball"),
                        listOf("trigger_fireball", "mana_injection", "fireball"))) {
                        val chain = Wand("refund-chain-$ids").also {
                            it.mana = 35.0; it.status.mana = 35.0; it.charge = 0.0
                            it.magics += ids.map { id -> SpellRegistry.create(id)!! }; it.load()
                        }
                        record(t, "real-chain-refund-cap-$ids", chain.spell(ctx.player) && chain.status.mana == 32.0,
                            "mana=${chain.status.mana}")
                        ProjectileRuntime.projectiles.forEach { it.alive = false }
                    }
                }
                if (t == 10) {
                    val stack = ModItems.wandStack(WandTier.T2)
                    wand = WandManager[WandManager.createWand(stack)].also {
                        it.magics += SpellRegistry.create("fireball")!!; it.load(); it.charge = 0.0
                        it.delayTickCount = 45.0; it.rechargeTickCount = 90.0; it.status.mana = 100.0
                        shared["id"] = it.id
                    }
                    ctx.player.setItemSlot(EquipmentSlot.MAINHAND, stack)
                }
                if (t == 30) record(t, "actual-wand-cast-starts-cooldowns", wand!!.spell(ctx.player), "cast")
                if (t == 55) {
                    target = EntityTypes.COW.create(ctx.level, EntitySpawnReason.COMMAND)?.also {
                        it.setPos(12.0, floor + 1.0, 8.0); it.isNoAi = true; ctx.level.addFreshEntity(it)
                    }
                    record(t, "real-hit-accepted", target?.let { SpellImpact.hurt(ctx.level, it, ctx.player, 2.0, Vec3.ZERO) } == true, "target=$target")
                }
                if (t == 59) record(t, "real-kill-accepted", target?.let {
                    SpellImpact.hurt(ctx.level, it, ctx.player, 100.0, Vec3.ZERO) && !it.isAlive
                } == true, "health=${target?.health}")
                if (t in 56..58) record(t, "real-repeated-damage-accepted-$t", (0..15).all {
                    SpellImpact.hurt(ctx.level, requireNotNull(target), ctx.player, 0.01, Vec3.ZERO)
                }, "16 accepted hits on one target")
                if (t == 67) {
                    ctx.command("gamemode survival @a")
                    attacker = ctx.spawnDummy(-12.0, floor + 1.0, -7.0)
                    record(t, "real-damage-from-right", SpellImpact.hurt(ctx.level, ctx.player, attacker, 2.0,
                        Vec3(1.0, 0.0, 0.0)), "health=${ctx.player.health} difficulty=${ctx.level.difficulty}")
                }
                if (t == 74) record(t, "real-damage-from-front", SpellImpact.hurt(ctx.level, ctx.player, attacker!!, 2.0,
                    Vec3(0.0, 0.0, -1.0)), "health=${ctx.player.health}")
                if (t == 80) {
                    val active = requireNotNull(wand)
                    active.status.delay = 0.0; active.status.recharge = 0.0; active.status.mana = 2.0
                    active.spell(ctx.player)
                }
                if (t == 90) requireNotNull(wand).status.mana = 100.0
                if (t == 93) {
                    repeat(3) { i ->
                        EntityTypes.COW.create(ctx.level, EntitySpawnReason.COMMAND)?.also {
                            it.setPos(15.0 + i * 2, floor + 1.0, 8.0); it.isNoAi = true
                            ctx.level.addFreshEntity(it); burstTargets += it
                        }
                    }
                    record(t, "real-three-hits-in-one-tick", burstTargets.size == 3 && burstTargets.all {
                        SpellImpact.hurt(ctx.level, it, ctx.player, 2.0, Vec3.ZERO)
                    }, "targets=${burstTargets.size}")
                }
                if (t == 96) record(t, "real-two-kills-in-one-tick", burstTargets.take(2).count {
                    SpellImpact.hurt(ctx.level, it, ctx.player, 100.0, Vec3.ZERO) && !it.isAlive
                } == 2, "kills=2")
                if (t == 130) {
                    target?.discard()
                    burstTargets.forEach { it.discard() }
                    attacker?.discard()
                    ctx.player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
                    ProjectileRuntime.projectiles.forEach { it.alive = false }
                }
                if (t == 199) record(t, "hud-scene-cleans-world-effects", ParticleManager.of(ctx.level).getEngine().particleCount() == 0 &&
                    ProjectileRuntime.projectiles.isEmpty(), "particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
            }, checksAt = { _, t -> checks[t] ?: emptyList() }, finish = { ctx -> target?.discard(); burstTargets.forEach { it.discard() }; attacker?.discard(); ctx.command("difficulty peaceful"); Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.setScreen(null); oldLayout?.let { HudSettings.save(it) }; WandHudRenderer.clear()
            } })
    }

    fun saw(): Scene {
        val name = "sawblade_falling"
        val checks = linkedMapOf<Int, MutableList<Check>>()
        var shot: Projectile? = null
        var floor = 0
        var initialY = 0.0
        var bounced = false
        var previousY = 0.0
        var caster: work.nekow.primalspells.entity.TestDummyEntity? = null
        return Scene(name, "锯片 / 自然下坠 / 地面反弹 / 金属碎散", 24, 130,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0); ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 0.0, floor + 3.0, -4.0, 0f, 8f)
                ctx.command("gamemode spectator @a")
                caster = ctx.spawnDummy(-8.0, floor + 1.0, -5.0)
            }, camera = { _, _ -> Pose(shot?.status?.pos?.x ?: 0.0, floor + 3.0, -4.0, 0f, 8f) }, tick = { ctx, t ->
                fun record(id: String, ok: Boolean, detail: String) { checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail)) }
                if (t == 10) {
                    shot = (SpellRegistry.create("sawblade") as Projectile).also {
                        it.caster = caster!!; it.wand = Wand("falling-saw"); it.wand.status.particleManager = ParticleManager.of(ctx.level)
                        it.position = Vector3d((-3f).toDouble(), (floor + 4f).toDouble(), (0f).toDouble()); it.velocity = Vector3f(0.15f, 0f, 0f)
                        it.spell(); ProjectileRuntime.add(it); initialY = it.status.pos.y; previousY = initialY
                    }
                }
                if (t == 16) {
                    val p = requireNotNull(shot)
                    record("intrinsic-gravity-curves-real-trajectory", p.status.pos.y < initialY - 0.3f &&
                        p.status.velocity.y < -0.15f, "y=${p.status.pos.y} vy=${p.status.velocity.y}")
                }
                if (t in 11..65) {
                    val p = shot!!
                    if (p.status.bounces < 8 && p.status.velocity.y > 0 && p.status.pos.y > previousY) bounced = true
                    previousY = p.status.pos.y
                }
                if (t == 55) {
                    val p = requireNotNull(shot)
                    record("ground-hit-rebounds-with-real-collision", bounced && p.alive && p.status.bounces < 8,
                        "bounces=${p.status.bounces} y=${p.status.pos.y}")
                }
                if (t == 65) { shot?.alive = false; caster?.discard() }
                if (t == 129) record("saw-retires-all-metal-particles", ProjectileRuntime.projectiles.isEmpty() &&
                    ParticleManager.of(ctx.level).getEngine().particleCount() == 0, "particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
            }, checksAt = { _, t -> checks[t] ?: emptyList() }, finish = { _ -> shot?.alive = false; caster?.discard() })
    }
}
