package work.nekow.primalspells.client.capture

import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import work.nekow.primalspells.integration.SpellEvents
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.item.component.SpellCardData
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModEffects
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.*

/** 真实施法、实体卡转移和读档回归；同名卡用不同次数检验串卡/重置。 */
internal object LimitedUseChecks {
    fun run(ctx: CaptureDriver.SceneCtx, record: (String, Boolean, String) -> Unit) {
        fun check(id: String, ok: Boolean, detail: Any?) = record("uses-$id", ok, detail.toString())
        fun wand(vararg ids: String) = Wand("uses-${ids.joinToString()}").also { w ->
            w.mana = 10000.0; w.status.mana = w.mana
            w.magics += ids.map { requireNotNull(SpellRegistry.create(it)) }; w.load()
        }
        val source = EntityTypes.PIG.create(ctx.level, EntitySpawnReason.COMMAND)!!
        source.setPos(-15.0, ctx.floorBlockY(0, 0) + 2.0, -15.0)
        source.setNoAi(true); source.setNoGravity(true); ctx.level.addFreshEntity(source)
        fun ready(w: Wand) { source.removeEffect(ModEffects.VOID_PHASE); w.status.delay = 0.0; w.status.recharge = 0.0 }
        try {
            val w = wand("void_escape", "void_escape")
            val first = w.magics[0]!!; val second = w.magics[1]!!
            check("fresh-independent-cards", first !== second && first.remainingUses == 25 && second.remainingUses == 25, "25/25")
            w.spell(source)
            check("direct-spends-only-first-card", first.remainingUses == 24 && second.remainingUses == 25 && VoidStatus.isPhased(source), "${first.remainingUses}/${second.remainingUses}")
            check("phase-block-does-not-spend", !w.spell(source) && first.remainingUses == 24 && second.remainingUses == 25, w.status.failedReason)
            source.removeEffect(ModEffects.VOID_PHASE)
            check("cooldown-does-not-spend", !w.spell(source) && second.remainingUses == 25, w.status.delay)
            ready(w); w.spell(source)
            check("second-card-has-own-budget", first.remainingUses == 24 && second.remainingUses == 24, "${first.remainingUses}/${second.remainingUses}")
            val single = wand("void_escape"); val card = single.magics[0]!!
            var successes = 0
            repeat(25) { ready(single); single.spell(source); if (VoidStatus.isPhased(source)) successes++ }
            ready(single); single.spell(source)
            check("exactly-25-then-exhausted", successes == 25 && card.remainingUses == 0 && !VoidStatus.isPhased(source) && single.status.delay == 0.0,
                "$successes; remaining=${card.remainingUses}; reasons=${single.status.failedReason}")
            val snapshot = single.saveTag()
            val plan = single.preview()
            check("preview-does-not-refill-or-spend", snapshot == single.saveTag() && plan.rows.none { it.accepted } && plan.slots[0].remainingUses == 0, plan)
            single.load()
            check("reload-does-not-refill", card.remainingUses == 0, single.magics[0]?.remainingUses)

            for (id in listOf("alpha", "gamma", "tau", "omega", "divide_two", "divide_ten")) {
                val copied = if (id == "alpha") wand("void_escape", id) else wand(id, "void_escape")
                val original = copied.magics.filterNotNull().single { it.id == "void_escape" }
                original.remainingUses = 0
                if (id == "alpha") copied.discardPile += copied.drawPile.removeFirst()
                val before = copied.saveTag(); copied.preview()
                check("$id-readonly", before == copied.saveTag(), "exhausted source remains untouched")
                ready(copied); copied.spell(source)
                check("$id-copies-exhausted-card", original.remainingUses == 0 && VoidStatus.isPhased(source), "remaining=${original.remainingUses}")
            }
            val split = wand("divide_two", "void_escape")
            ready(split); split.spell(source)
            check("copy-does-not-spend-nonempty-source", split.magics[1]?.remainingUses == 25, split.magics[1]?.remainingUses)
            val otherStack = ItemStack(ModItems.WAND.get())
            val other = WandManager[WandManager.createWand(otherStack)]
            other.magics += requireNotNull(SpellRegistry.create("void_escape")).also { it.remainingUses = 0 }; other.load()
            source.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, otherStack)
            val otherBefore = other.saveTag()
            val zeta = wand("zeta"); ready(zeta); zeta.spell(source)
            check("zeta-copies-exhausted-other-wand-card", VoidStatus.isPhased(source) && otherBefore == other.saveTag(), other.magics[0]?.remainingUses)
            source.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY)
            val mixed = wand("double_cast", "void_escape", "alpha")
            // Alpha复制多重施放卡再回到真实牌堆；直接卡的次数仍只提交一次。
            ready(mixed); mixed.spell(source)
            check("direct-card-spends-once-in-copy-transaction", mixed.magics[1]?.remainingUses == 24, mixed.magics[1]?.remainingUses)
            val refresh = wand("wand_refresh", "void_escape"); refresh.magics[1]!!.remainingUses = 0
            ready(refresh); refresh.spell(source)
            check("refresh-does-not-refill-card", refresh.magics[1]?.remainingUses == 0, refresh.magics[1]?.remainingUses)

            // 引用控制牌付款失败时整个子树都不能接受。
            val unpaid = wand("gamma", "void_escape"); unpaid.status.mana = 0.0
            ready(unpaid); unpaid.spell(source)
            check("unpaid-control-does-not-spend-source", unpaid.magics[1]?.remainingUses == 25 && !VoidStatus.isPhased(source), unpaid.status.failedReason)
            val canceled = wand("void_escape")
            val probe = object {
                @net.neoforged.bus.api.SubscribeEvent
                fun before(event: SpellEvents.BeforeCast) { if (event.wand === canceled) event.isCanceled = true }
            }
            NeoForge.EVENT_BUS.register(probe)
            try { ready(canceled); check("canceled-cast-keeps-uses", !canceled.spell(source) && canceled.magics[0]?.remainingUses == 25, "BeforeCast canceled") }
            finally { NeoForge.EVENT_BUS.unregister(probe) }

            val saved = wand("void_escape", "fireball", "void_escape")
            saved.innate += requireNotNull(SpellRegistry.create("void_escape")).also { it.remainingUses = 2 }
            saved.magics[0]!!.remainingUses = 7; saved.magics[2]!!.remainingUses = 0; saved.load()
            saved.drawPile.clear(); saved.drawPile += saved.magics[2]!!
            saved.hand += saved.magics[0]!!; saved.discardPile += saved.innate[0]; saved.discardPile += saved.magics[1]!!
            val tag = saved.saveTag(); val restored = Wand.fromTag(tag)
            check("nbt-keeps-per-card-and-innate-uses", restored.magics[0]?.remainingUses == 7 && restored.magics[2]?.remainingUses == 0 && restored.innate[0].remainingUses == 2, restored.magics.map { it?.remainingUses })
            check("nbt-restores-exact-duplicate-card-cursor", restored.drawPile.single() === restored.magics[2] && restored.hand.single() === restored.magics[0] && restored.discardPile.first() === restored.innate[0] && tag == restored.saveTag(), restored.drawPile.map { it.remainingUses })
            val removed = tag.copy()
            removed.put("magics", net.minecraft.nbt.ListTag().also { list ->
                listOf("void_escape", "removed_spell", "void_escape").forEach { list.add(net.minecraft.nbt.StringTag.valueOf(it)) }
            })
            removed.put("innate", net.minecraft.nbt.ListTag().also { it.add(net.minecraft.nbt.StringTag.valueOf("removed_innate")) })
            val migrated = Wand.fromTag(removed)
            check("removed-ids-do-not-shift-uses-or-cursors", migrated.magics[0]?.remainingUses == 7 && migrated.magics[1] == null &&
                migrated.magics[2]?.remainingUses == 0 && migrated.drawPile.single() === migrated.magics[2] && migrated.hand.single() === migrated.magics[0], migrated.saveTag())
            val legacy = tag.copy()
            listOf("spell_uses", "innate_uses", "draw_pile_slots", "hand_slots", "discard_pile_slots").forEach(legacy::remove)
            check("legacy-cards-start-full", Wand.fromTag(legacy).loadedMagics.filter { it.maxUses > 0 }.all { it.remainingUses == 25 }, "old ID-only NBT remains readable")

            val payload = wand("delayed_cast", "void_escape")
            ready(payload); payload.spell(source); ProjectileRuntime.tick()
            val carrier = ProjectileRuntime.projectiles.last { it.id == "delayed_cast" }
            check("payload-reserves-use-once-with-original-payment", payload.magics[1]?.remainingUses == 24, payload.magics[1]?.remainingUses)
            carrier.expire()
            check("payload-release-does-not-charge-twice", payload.magics[1]?.remainingUses == 24 && VoidStatus.isPhased(source), payload.magics[1]?.remainingUses)
            val dividedPayload = wand("divide_two", "delayed_cast", "void_escape")
            ready(dividedPayload); dividedPayload.spell(source); ProjectileRuntime.tick()
            check("copied-tree-keeps-payload-uses", dividedPayload.magics[2]?.remainingUses == 25, dividedPayload.magics[2]?.remainingUses)

            transfer(ctx, ::check)
        } finally {
            source.discard()
            ProjectileRuntime.projectiles.forEach {
                (it as? TriggerSpell)?.payload?.clear(); it.addedTriggers.forEach { trigger -> trigger.cards.clear() }; it.alive = false
            }
            ProjectileRuntime.tick()
        }
    }

    private fun transfer(ctx: CaptureDriver.SceneCtx, check: (String, Boolean, Any?) -> Unit) {
        val stack = ItemStack(ModItems.WAND.get()); val w = WandManager[WandManager.createWand(stack)]
        val item = stack.item as WandItem
        val slot = Slot(SimpleContainer(1), 0, 0, 0)
        var cursor = SpellCardData.stack("void_escape", 7)
        val access = object : SlotAccess {
            override fun get() = cursor
            override fun set(value: ItemStack): Boolean { cursor = value; return true }
        }
        fun click(container: ItemStack) { container.item.overrideOtherStackedOnMe(container, cursor, slot, ClickAction.SECONDARY, ctx.player, access) }
        click(stack)
        check("insert-keeps-uses-and-syncs-slot", w.magics[0]?.remainingUses == 7 && stack.get(ModItems.WAND_SPELL_USES)?.get(0) == 7 && cursor.isEmpty, w.magics[0]?.remainingUses)
        cursor = SpellCardData.stack("void_escape", 0); click(stack)
        check("swap-same-id-keeps-distinct-uses", w.magics[0]?.remainingUses == 0 && cursor.get(ModItems.SPELL_USES) == 7, cursor)
        val pouch = ItemStack(ModItems.SPELL_POUCH.get()); click(pouch)
        check("pouch-retains-card-uses", SpellPouchItem.getUses(pouch)[0] == 7 && cursor.isEmpty, pouch)
        click(pouch)
        check("pouch-return-keeps-uses", cursor.get(ModItems.SPELL_USES) == 7, cursor)
        click(stack)
        check("pouch-to-wand-swap-keeps-both-cards", w.magics[0]?.remainingUses == 7 && cursor.get(ModItems.SPELL_USES) == 0, cursor)
        cursor = ItemStack.EMPTY; click(stack)
        check("remove-keeps-card-count", cursor.get(ModItems.SPELL_USES) == 7 && w.magics[0] == null, cursor)
        val ops = ctx.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE)
        val encoded = ItemStack.CODEC.encodeStart(ops, cursor).getOrThrow()
        val decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow()
        check("loose-card-nbt-retains-uses", SpellCardData.read(decoded)?.remainingUses == 7, encoded)
        cursor = decoded; click(pouch)
        val pouchTag = ItemStack.CODEC.encodeStart(ops, pouch).getOrThrow()
        val pouchRestored = ItemStack.CODEC.parse(ops, pouchTag).getOrThrow()
        check("pouch-nbt-retains-per-slot-uses", SpellPouchItem.getUses(pouchRestored)[0] == 7, pouchTag)
        val fresh = ItemStack(ModItems.spellItem("void_escape")!!)
        check("legacy-loose-card-defaults-to-25", SpellCardData.read(fresh)?.remainingUses == 25, fresh)
        val buf = net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), ctx.level.registryAccess(),
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE)
        try {
            val packet = work.nekow.primalspells.network.EditWandPayload(1, 2, w.id, 0, "void_escape", 1, 7)
            work.nekow.primalspells.network.EditWandPayload.STREAM_CODEC.encode(buf, packet)
            check("creative-edit-codec-retains-uses", work.nekow.primalspells.network.EditWandPayload.STREAM_CODEC.decode(buf) == packet, packet)
        } finally { buf.release() }
        w.unload()
    }
}
