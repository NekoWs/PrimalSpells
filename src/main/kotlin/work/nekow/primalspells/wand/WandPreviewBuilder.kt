package work.nekow.primalspells.wand

import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.projectile.Projectile

/** 在独立副本中观察真实解析与付费，不调用装备、施法、被动或网络钩子。 */
internal object WandPreviewBuilder {
    /** 只读预览：在独立牌堆/法力副本上复用WandDeck与CastTransaction，不激活任何效果。 */
    fun build(wand: Wand): WandPreview {
        val copy = Wand(wand.id)
        copy.mana = wand.mana; copy.charge = wand.charge; copy.cast = wand.cast; copy.size = wand.size
        copy.delayTickCount = wand.delayTickCount; copy.rechargeTickCount = wand.rechargeTickCount
        copy.status.mana = wand.status.mana
        copy.drawPile += wand.drawPile.map { it.clone() }
        copy.discardPile += wand.discardPile.map { it.clone() }
        copy.loadedMagics += wand.loadedMagics.map { it.clone() }
        val chain = WandDeck(copy).draw(wand.cast)
        data class Draft(val card: Magic, val scope: String, val depth: Int, val kind: String)
        val draft = arrayListOf<Draft>()
        var scopeIndex = 0
        fun visit(cards: List<Magic>, scope: String, depth: Int) {
            cards.forEach { card ->
                val kind = when (card) {
                    is DrawAction -> card.kind.name.lowercase()
                    is Modifier -> "modifier"
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
        val accounting = CastTransaction(copy)
        val paid = accounting.pay(chain)
        accounting.finish()
        val reasons = copy.status.failedReason.toMutableList()
        if (wand.status.delay > 0) reasons += "Delay ${wand.status.delay.toInt()}t"
        if (wand.status.recharge > 0) reasons += "Recharge ${wand.status.recharge.toInt()}t"
        if (paid.none { it is Projectile }) reasons += "No payable projectile"
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
        wand.magics.forEachIndexed { index, card -> card?.clone()?.let { sources[it] = index; copy.drawPile += it } }
        val slots = MutableList(maxOf(wand.size, wand.magics.size)) { i -> WandPreview.Slot(wand.magics.getOrNull(i)?.id.orEmpty(), "") }
        var chain = 0
        while (copy.drawPile.isNotEmpty()) {
            WandDeck(copy).draw(wand.cast.coerceAtLeast(1), false, { source, scope ->
                sources[source]?.let { i -> slots[i] = WandPreview.Slot(source.id, scope) }
            }, "chain${chain++}")
            // 下一条链只看余下配置，不把此前抽牌再次排回队列。
            copy.hand.clear()
        }
        return slots
    }
}
