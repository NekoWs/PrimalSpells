package work.nekow.primalspells.wand

import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.OtherSpell
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.projectile.Projectile

/** 在独立副本中观察真实解析与付费，不调用装备、施法、被动或网络钩子。 */
internal object WandPreviewBuilder {
    /** 只读预览：在独立牌堆/法力副本上复用WandDeck与CastTransaction，不激活任何效果。 */
    fun build(wand: Wand, references: List<Wand> = emptyList()): WandPreview {
        val copy = Wand(wand.id)
        copy.mana = wand.mana; copy.charge = wand.charge; copy.cast = wand.cast; copy.size = wand.size
        copy.delayTickCount = wand.delayTickCount; copy.rechargeTickCount = wand.rechargeTickCount
        copy.status.mana = wand.status.mana
        copy.referenceSeed = wand.referenceSeed
        val clones = java.util.IdentityHashMap<Magic, Magic>()
        fun cloned(card: Magic) = clones.getOrPut(card) { card.clone().also { it.remainingUses = card.remainingUses } }
        copy.drawPile += wand.drawPile.map(::cloned)
        copy.discardPile += wand.discardPile.map(::cloned)
        copy.loadedMagics += wand.loadedMagics.map(::cloned)
        val accounting = CastTransaction(copy)
        val chain = accounting.draw(references)
        data class Draft(val card: Magic, val scope: String, val depth: Int, val kind: String)
        val draft = arrayListOf<Draft>()
        var scopeIndex = 0
        fun visit(cards: List<Magic>, scope: String, depth: Int) {
            cards.forEach { card ->
                val kind = when (card) {
                    is DrawAction -> card.kind.name.lowercase()
                    is Modifier -> "modifier"
                    is OtherSpell -> "other"
                    else -> "projectile"
                }
                draft += Draft(card, scope, depth, kind)
                if (card is DrawAction) visit(card.expanded, scope, depth + 1)
                if (card is TriggerSpell) visit(card.payload, "$scope/hit${scopeIndex++}", depth + 1)
                if (card is Projectile) card.addedTriggers.forEach {
                    visit(it.cards, "$scope/${it.kind.name.lowercase()}${scopeIndex++}", depth + 1)
                }
            }
        }
        visit(chain, "root", 0)
        val paid = accounting.pay(chain)
        accounting.finish()
        val reasons = copy.status.failedReason.toMutableList()
        if (wand.status.delay > 0) reasons += "Delay ${wand.status.delay.toInt()}t"
        if (wand.status.recharge > 0) reasons += "Recharge ${wand.status.recharge.toInt()}t"
        if (paid.none { it is Projectile || it is OtherSpell } &&
            accounting.accepted.none { it is DrawAction && it.kind == DrawAction.Kind.REFRESH }) reasons += "No payable projectile"
        val accepted = accounting.accepted.toSet()
        val modifiersByScope = draft.filter { it.card is Modifier && it.card in accepted }
            .groupBy({ it.scope }, { it.card.id })
        return WandPreview(draft.take(4096).map {
            WandPreview.Row(it.card.id, it.scope, it.depth, it.kind, it.card.mana,
                it.card in accepted, modifiersByScope[it.scope].orEmpty())
        }, copy.status.mana, copy.status.delay, copy.status.recharge.coerceAtLeast(0.0), reasons,
            paid.count { it is Projectile }, slots(wand))
    }

    /** 整副配置分色复用同一抽牌器，仅观察原卡被抽入哪个作用域，不付费、不回洗、不执行效果。 */
    private fun slots(wand: Wand): List<WandPreview.Slot> {
        val copy = Wand(wand.id)
        val sources = java.util.IdentityHashMap<Magic, Int>()
        val clones = java.util.IdentityHashMap<Magic, Magic>()
        wand.magics.forEachIndexed { index, card -> card?.let {
            val cloned = it.clone().also { cloned -> cloned.remainingUses = it.remainingUses }
            clones[it] = cloned; sources[cloned] = index; copy.drawPile += cloned
        } }
        copy.loadedMagics += wand.loadedMagics.map { clones[it] ?: it.clone() }
        val slots = MutableList(maxOf(wand.size, wand.magics.size)) { i ->
            val card = wand.magics.getOrNull(i)
            WandPreview.Slot(card?.id.orEmpty(), "", card?.takeIf { it.maxUses > 0 }?.remainingUses ?: -1)
        }
        var chain = 0
        while (copy.drawPile.isNotEmpty()) {
            val deck = WandDeck(copy)
            deck.draw(wand.cast.coerceAtLeast(1), false, { source, scope ->
                sources[source]?.let { i -> if (slots[i].scope.isEmpty()) slots[i] = slots[i].copy(scope = scope) }
            }, "chain${chain++}")
            // 下一条链只看余下配置，不把此前抽牌再次排回队列。
            copy.hand.clear()
            if (deck.didRefresh) break
        }
        return slots
    }
}
