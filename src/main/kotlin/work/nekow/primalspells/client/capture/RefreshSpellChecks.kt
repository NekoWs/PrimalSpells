package work.nekow.primalspells.client.capture

import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.lifecycle.PayloadRelease
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModEffects
import work.nekow.primalspells.wand.CastTransaction
import work.nekow.primalspells.wand.Wand

/** 检查真实抽牌边界、付费、逐卡次数及预览/NBT，不重写抽牌或刷新算法。 */
internal object RefreshSpellChecks {
    fun run(ctx: CaptureDriver.SceneCtx, record: (String, Boolean, String) -> Unit) {
        fun check(id: String, ok: Boolean, detail: Any?) = record("refresh-$id", ok, detail.toString())
        fun wand(vararg ids: String, casts: Int = 1, mana: Double = 10000.0) = Wand("refresh-${ids.joinToString()}-$casts-$mana").also {
            it.mana = mana; it.status.mana = mana; it.cast = casts; it.rechargeTickCount = 60.0
            it.magics += ids.map { id -> requireNotNull(SpellRegistry.create(id)) }; it.load()
        }
        data class Paid(val cards: List<Magic>, val tx: CastTransaction) {
            val shots get() = cards.filterIsInstance<Projectile>()
            val refreshes get() = tx.accepted.filterIsInstance<DrawAction>().count { it.kind == DrawAction.Kind.REFRESH }
        }
        fun settle(w: Wand, commit: Boolean = true): Paid {
            val tx = CastTransaction(w)
            val paid = tx.pay(tx.draw())
            if (commit) tx.commitUses()
            tx.finish()
            return Paid(paid, tx)
        }
        val source = requireNotNull(EntityTypes.PIG.create(ctx.level, EntitySpawnReason.COMMAND))
        source.setPos(15.0, ctx.floorBlockY(0, 0) + 6.0, -15.0)
        source.isNoAi = true; source.setNoGravity(true); source.isSilent = true; ctx.level.addFreshEntity(source)
        fun ready(w: Wand) { source.removeEffect(ModEffects.VOID_PHASE); w.status.delay = 0.0; w.status.recharge = 0.0 }
        try {
            val storage = wand("double_cast", "fireball", "wand_refresh", "black_hole")
            val storagePaid = settle(storage)
            check("right-side-is-storage", storagePaid.shots.map { it.id } == listOf("fireball") && storagePaid.tx.accepted.none { it.id == "black_hole" }, storagePaid.tx.accepted.map { it.id })
            check("first-refresh-reloads-before-finish", storage.drawPile == storage.loadedMagics && storage.hand.isEmpty() && storage.discardPile.isEmpty() && storage.status.recharge == 0.0, storage.drawPile.map { it.id })

            val loop = wand("double_cast", "wand_refresh", "black_hole")
            val loopPaid = settle(loop)
            check("remaining-draws-return-to-head", loopPaid.tx.accepted.count { it.id == "double_cast" } == 2 && loopPaid.refreshes == 2 && loopPaid.shots.isEmpty(), loopPaid.tx.accepted.map { it.id })
            check("second-refresh-stops-wrapping-and-recharges", loop.status.recharge == 40.0 && loop.drawPile == loop.loadedMagics, "recharge=${loop.status.recharge}")

            // 原生多抽与双重施放叠加会重抽刷新；减寿只影响虚空持续时间，不减双冷却。
            fun voidRecipe(casts: Int, double: Boolean = true) = wand(*(
                (if (double) listOf("double_cast") else emptyList()) + List(5) { "reduced_lifetime" } +
                    listOf("void_escape", "wand_refresh")
                ).toTypedArray(), casts = casts, mana = 450.0).also {
                it.delayTickCount = 6.0; it.rechargeTickCount = 10.0
            }
            val doubleVoid = voidRecipe(2)
            val doubleVoidPreview = doubleVoid.preview()
            val doubleVoidPaid = settle(doubleVoid)
            check("native-two-draws-repeat-void-and-refresh", doubleVoidPaid.refreshes == 2 && doubleVoidPaid.cards.count { it.id == "void_escape" } == 2 && doubleVoid.magics[6]!!.remainingUses == 25,
                doubleVoidPaid.tx.accepted.map { it.id })
            check("native-two-draws-accumulate-both-cooldowns", doubleVoid.status.delay == 400.0 && doubleVoid.status.recharge == 390.0 && doubleVoid.status.mana == 310.0,
                "delay=${doubleVoid.status.delay}; recharge=${doubleVoid.status.recharge}; mana=${doubleVoid.status.mana}")
            check("native-two-draws-preview-matches", doubleVoidPreview.delay == doubleVoid.status.delay && doubleVoidPreview.recharge == doubleVoid.status.recharge && doubleVoidPreview.manaAfter == doubleVoid.status.mana,
                "delay=${doubleVoidPreview.delay}; recharge=${doubleVoidPreview.recharge}; mana=${doubleVoidPreview.manaAfter}")
            val singleVoid = voidRecipe(2, double = false)
            ready(singleVoid); singleVoid.spell(source)
            check("native-two-without-double-refreshes-once", singleVoid.status.delay == 200.0 && singleVoid.status.recharge == 0.0 && singleVoid.status.mana == 380.0 && singleVoid.magics[5]!!.remainingUses == 25,
                "delay=${singleVoid.status.delay}; recharge=${singleVoid.status.recharge}; mana=${singleVoid.status.mana}")
            check("five-lifetime-reductions-leave-one-tick-of-void", source.getEffect(ModEffects.VOID_PHASE)?.duration == 1, source.getEffect(ModEffects.VOID_PHASE)?.duration)
            val nativeOne = voidRecipe(1)
            val nativeOnePaid = settle(nativeOne)
            check("native-one-with-double-refreshes-once", nativeOnePaid.refreshes == 1 && nativeOne.status.delay == 200.0 && nativeOne.status.recharge == 0.0 && nativeOne.status.mana == 380.0,
                "delay=${nativeOne.status.delay}; recharge=${nativeOne.status.recharge}; mana=${nativeOne.status.mana}")

            val preserved = wand("double_cast", "void_escape", "wand_refresh", "black_hole")
            val original = preserved.magics[1]!!; original.remainingUses = 7
            repeat(2) { ready(preserved); preserved.spell(source) }
            check("actual-casts-preserve-pre-refresh-uses", original.remainingUses == 7 && VoidStatus.isPhased(source), original.remainingUses)
            val exhausted = wand("double_cast", "void_escape", "wand_refresh")
            exhausted.magics[1]!!.remainingUses = 0
            ready(exhausted); exhausted.spell(source)
            check("refresh-does-not-enable-exhausted-direct-card", exhausted.magics[1]!!.remainingUses == 0 && !VoidStatus.isPhased(source), exhausted.status.failedReason)

            val earlier = wand("void_escape", "wand_refresh", "black_hole")
            ready(earlier); earlier.spell(source)
            ready(earlier); earlier.spell(source)
            check("previous-cast-uses-are-not-restored", earlier.magics[0]!!.remainingUses == 24 && !VoidStatus.isPhased(source), earlier.magics[0]!!.remainingUses)

            val after = wand("void_escape", "wand_refresh", "black_hole", casts = 3)
            val afterPaid = settle(after)
            check("same-card-after-refresh-spends-once", afterPaid.cards.count { it.id == "void_escape" } == 2 && after.magics[0]!!.remainingUses == 24 && afterPaid.refreshes == 1,
                "uses=${after.magics[0]!!.remainingUses}; paid=${afterPaid.cards.map { it.id }}")
            check("post-refresh-cursor-and-hand-survive-finish", after.drawPile.map { it.id } == listOf("wand_refresh", "black_hole") && after.discardPile.singleOrNull() === after.magics[0], after.drawPile.map { it.id })
            val restored = Wand.fromTag(after.saveTag())
            check("post-refresh-nbt-preserves-cursor-and-count", restored.drawPile.map { it.id } == after.drawPile.map { it.id } && restored.magics[0]!!.remainingUses == 24 && restored.discardPile.singleOrNull() === restored.magics[0], restored.saveTag())

            val poor = wand("double_cast", "void_escape", "wand_refresh", "black_hole", mana = 19.0)
            ready(poor); poor.spell(source)
            check("unpaid-refresh-does-not-waive-uses-or-hide-storage", poor.magics[1]!!.remainingUses == 24 && poor.drawPile.map { it.id } == listOf("black_hole"), "uses=${poor.magics[1]!!.remainingUses}; pile=${poor.drawPile.map { it.id }}")
            val futureRefund = wand("double_cast", "void_escape", "wand_refresh", "mana_injection", casts = 2, mana = 19.0)
            val refundPaid = settle(futureRefund)
            check("later-refund-cannot-fund-earlier-refresh", refundPaid.refreshes == 0 && futureRefund.magics[1]!!.remainingUses == 24 && refundPaid.tx.accepted.any { it.id == "mana_injection" }, refundPaid.tx.accepted.map { it.id })

            val payload = wand("double_cast", "trigger_fireball", "void_escape", "wand_refresh", "black_hole")
            val payloadPaid = settle(payload)
            check("root-refresh-preserves-payload-card-uses", payload.magics[2]!!.remainingUses == 25 && (payloadPaid.shots.single() as TriggerSpell).payload.single().id == "void_escape", payload.magics[2]!!.remainingUses)
            val carrier = payloadPaid.shots.single()
            carrier.caster = source; carrier.wand = payload; source.removeEffect(ModEffects.VOID_PHASE)
            PayloadRelease.emit(carrier, (carrier as TriggerSpell).payload, Vector3d(source.x, source.y, source.z), Vector3f(0f, 0f, 1f))
            check("waived-payload-releases-without-spending-again", VoidStatus.isPhased(source) && payload.magics[2]!!.remainingUses == 25, payload.magics[2]!!.remainingUses)

            val child = wand("trigger_fireball", "double_cast", "void_escape", "wand_refresh", "black_hole")
            val childPaid = settle(child)
            check("payload-refresh-reloads-the-whole-wand", childPaid.refreshes == 1 && child.drawPile == child.loadedMagics && child.magics[2]!!.remainingUses == 25, childPaid.tx.accepted.map { it.id })
            val appended = wand("add_trigger", "speed_boost", "fireball", "double_cast", "void_escape", "wand_refresh", "black_hole")
            val appendedPaid = settle(appended)
            check("appended-refresh-sees-free-carrier-modifiers", appendedPaid.refreshes == 1 && appendedPaid.tx.accepted.single { it.id == "speed_boost" }.mana == 0.0 && appended.magics[4]!!.remainingUses == 25, appendedPaid.tx.accepted.map { it.id to it.mana })
            val reentry = wand("add_trigger", "fireball", "double_cast", "wand_refresh", "black_hole")
            val reentryPaid = settle(reentry)
            check("physical-head-control-is-not-a-reference-cycle", reentryPaid.tx.accepted.count { it.id == "add_trigger" } == 2 && reentryPaid.refreshes == 2,
                reentryPaid.tx.accepted.map { it.id })
            val reentryPreview = wand("add_trigger", "fireball", "double_cast", "wand_refresh", "black_hole").preview()
            check("reentrant-payload-scopes-remain-independent", reentryPreview.rows.filter { it.id == "fireball" }.map { it.scope }.distinct().size == 2,
                reentryPreview.rows.map { it.id to it.scope })

            val divided = wand("divide_two", "wand_refresh", "black_hole")
            val dividedPaid = settle(divided)
            check("division-executes-two-refreshes-not-template", dividedPaid.refreshes == 2 && divided.status.recharge == 40.0 && dividedPaid.shots.isEmpty(), dividedPaid.tx.accepted.map { it.id })
            val dividedChild = wand("divide_two", "trigger_fireball", "double_cast", "void_escape", "wand_refresh", "black_hole")
            val dividedChildPaid = settle(dividedChild)
            check("division-replays-nested-refreshes", dividedChildPaid.refreshes == 2 && dividedChildPaid.shots.size == 2 && dividedChild.magics[3]!!.remainingUses == 25, dividedChildPaid.tx.accepted.map { it.id })

            val greek = wand("double_cast", "gamma", "wand_refresh", "void_escape")
            greek.magics[3]!!.remainingUses = 0
            ready(greek); greek.spell(source)
            check("greek-can-copy-exhausted-storage-card", VoidStatus.isPhased(source) && greek.magics[3]!!.remainingUses == 0, greek.magics[3]!!.remainingUses)
            val unpaidParent = wand("gamma", "black_hole", "wand_refresh", mana = 0.0)
            val unpaidPaid = settle(unpaidParent)
            check("refresh-inside-unpaid-reference-does-nothing", unpaidPaid.refreshes == 0 && unpaidParent.drawPile.map { it.id } == listOf("black_hole", "wand_refresh"), unpaidParent.drawPile.map { it.id })

            for ((index, w) in listOf(storage, loop, after, child, appended, dividedChild,
                wand("gamma", "black_hole", "wand_refresh"), wand("omega", "fireball", "wand_refresh")).withIndex()) {
                val before = w.saveTag()
                val preview = w.preview()
                check("preview-readonly-$index", before == w.saveTag(), preview.reasons)
                val tx = CastTransaction(w)
                val paid = tx.pay(tx.draw()); tx.finish()
                check("preview-matches-settlement-$index", preview.manaAfter == w.status.mana && preview.delay == w.status.delay && preview.recharge == w.status.recharge && preview.shots == paid.count { it is Projectile },
                    "preview=${preview.manaAfter}/${preview.recharge}/${preview.shots}; actual=${w.status.mana}/${w.status.recharge}/${paid.count { it is Projectile }}")
            }
            val colors = wand("double_cast", "fireball", "wand_refresh", "black_hole").preview().slots
            check("storage-slots-are-not-marked-as-cast-chains", colors[3].scope.isEmpty() && colors.take(3).all { it.scope.isNotEmpty() }, colors)
        } finally {
            source.removeEffect(ModEffects.VOID_PHASE); source.discard()
            ProjectileRuntime.projectiles.filter { it.caster === source }.forEach { it.alive = false }
        }
    }
}
