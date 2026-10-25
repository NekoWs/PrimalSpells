package work.nekow.primalspells.client.capture

import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModEffects
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.*

/** 对真实抽牌器、结算器与服务端施法入口的引用/刷新/生命代价回归。 */
internal object ReferenceSpellChecks {
    fun run(ctx: CaptureDriver.SceneCtx, record: (String, Boolean, String) -> Unit) {
        fun wand(vararg ids: String, skip: Int = 0, casts: Int = 1): Wand = Wand("reference-${ids.joinToString()}-$skip").also {
            it.mana = 10000.0; it.status.mana = 10000.0; it.cast = casts
            it.magics += ids.map { id -> requireNotNull(SpellRegistry.create(id)) }; it.load()
            repeat(skip) { _ -> it.discardPile += it.drawPile.removeFirst() }
        }
        data class Paid(val cards: List<Magic>, val transaction: CastTransaction) {
            val shots get() = cards.filterIsInstance<Projectile>()
        }
        fun pay(w: Wand, refs: List<Wand> = emptyList()): Paid {
            val tx = CastTransaction(w)
            val cards = tx.pay(WandDeck(w, refs, kotlin.random.Random(w.referenceSeed)).draw(w.cast))
            tx.finish()
            return Paid(cards, tx)
        }
        fun check(id: String, ok: Boolean, detail: Any?) = record("reference-$id", ok, detail.toString())
        val a = wand("fireball", "alpha", skip = 1); val ap = pay(a)
        check("alpha-first-copy-normal-cost", ap.shots.map { it.id } == listOf("fireball") && a.status.mana == 9967.0, a.status.mana)
        check("alpha-reloads-originals-once", a.drawPile.size == 2 && a.hand.isEmpty() && a.discardPile.isEmpty(), a.drawPile.map { it.id })
        val g = wand("gamma", "concentrated_light"); val gp = pay(g)
        check("gamma-last-copy-keeps-original", gp.shots.single().id == "concentrated_light" && g.drawPile.map { it.id } == listOf("concentrated_light") && g.status.mana == 9880.0, g.status.mana)
        val t = wand("tau", "fireball", "healing_bolt", "damage_boost"); val tp = pay(t)
        check("tau-copies-two-cards-not-chains", tp.shots.map { it.id } == listOf("fireball", "healing_bolt") && tp.cards.none { it.id == "damage_boost" } && t.drawPile.size == 3, tp.cards.map { it.id })
        val tm = wand("tau", "speed_boost", "fireball", "healing_bolt"); val tmp = pay(tm)
        check("tau-modifier-not-whole-chain", tmp.shots.size == 1 && tmp.cards.count { it.id == "speed_boost" } == 1 && tm.drawPile.size == 3, tmp.cards.map { it.id })
        val o = wand("omega", "fireball", "wand_refresh", "speed_boost", "healing_bolt"); val op = pay(o)
        check("omega-excludes-only-self-and-refresh", op.shots.size == 2 && op.transaction.accepted.none { it.id == "wand_refresh" } && op.cards.count { it.id == "speed_boost" } == 1 && o.drawPile.size == 4, op.transaction.accepted.map { it.id })
        val multi = wand("omega", "double_cast", "fireball", "healing_bolt"); val multiPaid = pay(multi)
        check("copy-all-multicast-budget-does-not-overflow", multiPaid.shots.map { it.id } == listOf("fireball", "healing_bolt"), multiPaid.cards.map { it.id })
        val noZeta = wand("zeta", "fireball"); val noZetaPaid = pay(noZeta)
        check("missing-other-wand-does-not-cast-next-card", noZetaPaid.shots.isEmpty() && noZeta.drawPile.map { it.id } == listOf("fireball"), noZeta.drawPile.map { it.id })
        val self = wand("alpha", "fireball"); val selfPaid = pay(self)
        check("self-reference-fizzles-without-recursing", selfPaid.shots.isEmpty() && selfPaid.transaction.accepted.size == 1, selfPaid.transaction.accepted.map { it.id })
        val cycle = wand("alpha", "omega", "gamma"); val cyclePaid = pay(cycle)
        check("mutual-reference-is-finite", cyclePaid.transaction.accepted.size < 100, cyclePaid.transaction.accepted.size)
        val bounded = wand("divide_ten", "divide_ten", "divide_ten", "divide_ten", "fireball")
        val tree = WandDeck(bounded).draw(1)
        check("nested-copy-has-bounded-expansion", "Expansion limit" in bounded.status.failedReason && tree.isNotEmpty(), bounded.status.failedReason)

        val appends = wand("add_trigger", "divide_ten", "divide_ten", "fireball", "divide_ten", "divide_ten", "fireball")
        val appendTree = WandDeck(appends).draw(1)
        fun treeSize(c: Magic): Int = 1 + (if (c is DrawAction) c.expanded.sumOf(::treeSize) else 0) +
            (if (c is TriggerSpell) c.payload.sumOf(::treeSize) else 0) +
            (if (c is Projectile) c.addedTriggers.sumOf { it.cards.sumOf(::treeSize) } else 0)
        val attachedSize = appendTree.sumOf(::treeSize)
        check("appended-payload-clones-share-expansion-limit", attachedSize <= 4096 && "Expansion limit" in appends.status.failedReason, attachedSize)

        val mu = wand("mana_injection", "reduce_recharge", "mu", "fireball", skip = 2)
        mu.rechargeTickCount = 60.0; mu.delayTickCount = 20.0
        val mup = pay(mu)
        check("mu-waives-mana-refund-and-timing", mu.status.mana == 9937.0 && mu.status.delay == 20.0 && mu.status.recharge == 60.0, "${mu.status.mana}/${mu.status.delay}/${mu.status.recharge}")
        check("mu-draws-real-next-projectile", mup.shots.single().id == "fireball" && mup.cards.filterIsInstance<Modifier>().all { it.castingCostsWaived }, mup.cards.map { it.id })
        val phi = wand("phi", "fireball", "chainsaw", "concentrated_light", "small_explosion")
        phi.delayTickCount = 40.0
        val pp = pay(phi)
        check("phi-selects-only-moving-projectiles", pp.shots.map { it.id } == listOf("fireball", "chainsaw", "concentrated_light"), pp.shots.map { it.id })
        check("phi-waives-cost-and-chainsaw-reset", phi.status.mana == 9900.0 && phi.status.delay == 40.0 && phi.status.recharge == 0.0, "${phi.status.mana}/${phi.status.delay}/${phi.status.recharge}")
        val sigma = wand("sigma", "fireball", "small_explosion", "plasma_beam"); val sp = pay(sigma)
        check("sigma-selects-only-static-projectiles", sp.shots.map { it.id } == listOf("small_explosion", "plasma_beam") && sigma.status.mana == 9900.0 && sigma.status.delay == 20.0, sp.shots.map { it.id })
        val poor = wand("phi", "concentrated_light"); poor.status.mana = 99.0
        val poorPaid = pay(poor)
        check("unpaid-reference-cannot-release-free-copies", poorPaid.shots.isEmpty() && poor.status.mana == 99.0, poorPaid.cards.map { it.id })
        val nested = wand("divide_two", "phi", "fireball"); val nestedPaid = pay(nested)
        check("divided-free-copies-retain-waiver", nestedPaid.shots.size == 2 && nested.status.mana == 9770.0 && nestedPaid.shots.all { it.mana == 0.0 && it.castingCostsWaived }, nested.status.mana)
        val tauPayload = wand("tau", "trigger_fireball", "fireball"); val tauPayloadPaid = pay(tauPayload)
        check("tau-trigger-uses-next-copy-as-independent-payload", tauPayloadPaid.shots.size == 1 &&
            (tauPayloadPaid.shots.single() as TriggerSpell).payload.map { it.id } == listOf("fireball") && tauPayload.drawPile.size == 2,
            tauPayloadPaid.shots.map { it.id })
        val muBlood = wand("blood_magic", "mu", "fireball", skip = 1); val muBloodPaid = pay(muBlood)
        check("free-blood-copy-still-pays-hp", muBloodPaid.transaction.healthCost == 1f && muBlood.status.mana == 9937.0, muBloodPaid.transaction.healthCost)
        val payload = wand("trigger_fireball", "fireball", "alpha", "speed_boost", "chainsaw", skip = 2)
        val payloadPaid = pay(payload); val carrier = payloadPaid.shots.single() as TriggerSpell
        check("copied-trigger-draws-isolated-live-payload", carrier.payload.map { it.id } == listOf("speed_boost", "chainsaw") && payloadPaid.cards.none { it is Modifier }, carrier.payload.map { it.id })
        val clone = SpellAssembly.copyCard(carrier as Magic) as TriggerSpell
        check("copied-trigger-payload-is-not-shared", clone.payload[1] !== carrier.payload[1], clone.payload.size)

        val refresh = wand("wand_refresh", "fireball"); refresh.rechargeTickCount = 60.0
        pay(refresh)
        check("single-refresh-reloads-immediately", refresh.status.mana == 9980.0 && refresh.status.recharge == 0.0 && refresh.drawPile.map { it.id } == listOf("wand_refresh", "fireball"), refresh.status.recharge)
        val two = wand("double_cast", "wand_refresh", "wand_refresh", "fireball"); two.rechargeTickCount = 60.0
        pay(two)
        check("two-refreshes-force-normal-recharge", two.status.recharge == 40.0 && two.drawPile.size == 4, two.status.recharge)
        val copiedRefresh = wand("wand_refresh", "divide_two", "alpha", skip = 1); copiedRefresh.rechargeTickCount = 60.0
        pay(copiedRefresh)
        check("copied-refreshes-count-together", copiedRefresh.status.recharge == 40.0 && copiedRefresh.drawPile.size == 3, copiedRefresh.status.recharge)
        val unfundedRefresh = wand("wand_refresh", "fireball"); unfundedRefresh.status.mana = 19.0
        pay(unfundedRefresh)
        check("unpaid-refresh-does-not-reload", unfundedRefresh.drawPile.map { it.id } == listOf("fireball") && unfundedRefresh.status.mana == 19.0, unfundedRefresh.drawPile.map { it.id })
        for (recipe in listOf(listOf("omega", "fireball", "wand_refresh"), listOf("mana_injection", "mu", "fireball"), listOf("double_cast", "wand_refresh", "wand_refresh"))) {
            val w = wand(*recipe.toTypedArray()); w.rechargeTickCount = 60.0
            val before = w.saveTag().toString(); val plan = w.preview(); val unchanged = before == w.saveTag().toString(); val actual = pay(w)
            check("preview-readonly-${recipe.first()}", unchanged, "preview invoked without gameplay hooks")
            // 费用/冷却使用同一原生解析与事务，同时检查真正执行前保存的副本不被改变。
            check("preview-matches-${recipe.first()}", plan.manaAfter == w.status.mana && plan.delay == w.status.delay && plan.recharge == w.status.recharge && plan.shots == actual.shots.size, "$plan")
        }
        val saved = wand("fireball", "alpha", skip = 1); saved.referenceSeed = 9876
        val restored = Wand.fromTag(saved.saveTag()); val restoredPaid = pay(restored)
        check("reload-preserves-reference-identity-and-seed", restoredPaid.shots.size == 1 && restored.drawPile.all { it in restored.loadedMagics } && restored.referenceSeed == 9876L, restored.referenceSeed)
        val source = EntityTypes.PIG.create(ctx.level, EntitySpawnReason.COMMAND)!!
        source.setPos(-12.0, ctx.floorBlockY(0,0) + 2.0, -12.0); source.setNoAi(true); source.setNoGravity(true)
        ctx.level.addFreshEntity(source)
        fun activate(mods: List<String>): Projectile {
            val shot = SpellRegistry.create("fireball") as Projectile
            SpellAssembly.apply(shot, mods.map { SpellRegistry.create(it) as Modifier })
            shot.caster = source; shot.wand = Wand("reference-lifetime"); shot.position = Vector3d(source.x, source.y + 1, source.z); shot.velocity = Vector3f(0f,0f,1f)
            shot.effects.removeAll { it.phase == EffectPhase.VISUAL }
            shot.spell(); shot.die(); return shot
        }
        check("reduce-lifetime-minus-25", activate(listOf("reduced_lifetime")).maxAge == 175, "200→175")
        check("lifetime-floor-before-visuals", activate(List(10) { "reduced_lifetime" }).maxAge == 1, "minimum1t")
        check("lifetime-addition-order-independent", activate(listOf("reduced_lifetime", "extended_lifetime")).maxAge == 200 && activate(listOf("extended_lifetime", "reduced_lifetime")).maxAge == 200, "sum before clamp")
        source.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 100.0; source.health = 100f
        source.getAttribute(Attributes.ARMOR)!!.baseValue = 30.0
        source.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 200, 4))
        source.getAttribute(Attributes.MAX_ABSORPTION)!!.baseValue = 20.0; source.absorptionAmount = 20f
        source.invulnerableTime = 20
        val blood = wand("blood_magic", "fireball"); blood.status.mana = 0.0; blood.delayTickCount = 20.0; blood.rechargeTickCount = 60.0
        blood.spell(source)
        check("blood-curse-bypasses-armor-resistance-absorption", source.health == 99f && source.absorptionAmount == 20f, "health=${source.health}; absorption=${source.absorptionAmount}")
        check("blood-refund-and-tick-reductions", blood.status.mana == 97.0 && blood.status.delay == 13.0 && blood.status.recharge == 53.0, "${blood.status.mana}/${blood.status.delay}/${blood.status.recharge}")
        val bloodMulti = wand("blood_magic", "double_cast", "fireball", "fireball")
        bloodMulti.spell(source)
        check("blood-cost-per-card-not-per-projectile", source.health == 98f, source.health)
        val stack = ItemStack(ModItems.WAND.get()); val other = WandManager[WandManager.createWand(stack)]
        other.magics += SpellRegistry.create("fireball")!!; other.load(); other.status.mana = 123.0
        source.setItemInHand(InteractionHand.OFF_HAND, stack)
        val otherBefore = other.saveTag().toString()
        val z = wand("zeta"); val zPreview = z.preview(source); z.spell(source)
        ProjectileRuntime.tick()
        check("zeta-copies-real-other-wand", z.status.mana == 9957.0 && zPreview.manaAfter == z.status.mana, "${z.status.mana}; $zPreview")
        check("zeta-keeps-other-wand-unchanged", otherBefore == other.saveTag().toString(), other.status.mana)
        val delayed = wand("delayed_cast", "void_escape")
        delayed.spell(source); ProjectileRuntime.tick()
        val delayedCarrier = ProjectileRuntime.projectiles.single { it.id == "delayed_cast" }
        delayedCarrier.expire()
        check("delayed-payload-executes-other-spell", VoidStatus.isPhased(source) && (delayedCarrier as TriggerSpell).payload.isEmpty(), source.getEffect(ModEffects.VOID_PHASE)?.duration)
        source.removeEffect(ModEffects.VOID_PHASE)
        var fatalSource: net.minecraft.world.damagesource.DamageSource? = null
        var fatalMessage: net.minecraft.network.chat.Component? = null
        val probe = object {
            @net.neoforged.bus.api.SubscribeEvent
            fun onDeath(event: net.neoforged.neoforge.event.entity.living.LivingDeathEvent) {
                if (event.entity === source) { fatalSource = event.source; fatalMessage = source.combatTracker.deathMessage.copy() }
            }
        }
        NeoForge.EVENT_BUS.register(probe)
        source.health = 0.5f
        val lethal = wand("blood_magic", "fireball")
        try { lethal.spell(source) } finally { NeoForge.EVENT_BUS.unregister(probe) }
        check("curse-can-kill-with-own-source", !source.isAlive && fatalSource?.entity === source &&
            (fatalMessage?.contents as? net.minecraft.network.chat.contents.TranslatableContents)?.key == "death.attack.primalspells_curse.self", fatalMessage?.string)
        source.discard()
        ProjectileRuntime.projectiles.forEach { it.alive = false }; ProjectileRuntime.tick()
    }
}
