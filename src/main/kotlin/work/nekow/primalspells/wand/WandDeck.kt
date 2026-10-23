package work.nekow.primalspells.wand

import work.nekow.primalspells.magic.core.AppendedPayload
import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.projectile.Projectile

/** 抽牌树与弃牌/回洗规则；牌堆状态只存放在所属法杖上，预览也复用此解析器。 */
internal class WandDeck(private val wand: Wand) {
    /**
     * 深度优先抽取一条链：触发牌先预留完整荷载，再继续父链预算。
     * 本轮手牌不回洗；荷载不从弃牌堆绕回，避免抽到自身。
     * trace仅供只读槽位分色观察原卡和作用域，正常施放不安装观察器。
     */
    fun draw(
        budget: Int,
        wrapping: Boolean = true,
        trace: ((Magic, String) -> Unit)? = null,
        scope: String = "root",
    ): ArrayList<Magic> {
        var remaining = budget
        var allowWrap = wrapping
        val chain = arrayListOf<Magic>()
        fun take(): Magic? {
            val pile = when {
                wand.drawPile.isNotEmpty() -> wand.drawPile
                allowWrap && wand.discardPile.isNotEmpty() -> wand.discardPile
                else -> return null
            }
            return pile.removeFirst().also { wand.hand += it; trace?.invoke(it, scope) }.clone()
        }
        fun shots(cards: List<Magic>): List<Projectile> = cards.flatMap {
            when (it) {
                is Projectile -> listOf(it)
                is DrawAction -> shots(it.expanded)
                else -> emptyList()
            }
        }
        fun prepare(card: Magic): Int {
            val action = card as? DrawAction
            if (action == null) {
                if (card is TriggerSpell) card.payload += draw(card.triggerCast, false, trace, if (trace == null) scope else "$scope/hit${wand.hand.size}")
                return card.cast - 1
            }
            when (action.kind) {
                DrawAction.Kind.ALL -> {
                    // 只抽当前剩余牌；耗尽后剩余多重施放预算也不能绕回旧弃牌。
                    allowWrap = false
                    action.expanded += draw(Int.MAX_VALUE, false, trace, scope)
                    return -1
                }
                DrawAction.Kind.DIVIDE -> {
                    val next = take() ?: return -1
                    val cost = prepare(next)
                    repeat(action.copies) { action.expanded += SpellAssembly.copyCard(next) }
                    // 复制修正只复制效果，不把它的继续抽牌重复N次。
                    return cost
                }
                else -> {
                    val carrier = draw(1, false, trace, scope)
                    val targets = shots(carrier)
                    if (targets.isNotEmpty()) {
                        val payload = draw(1, false, trace, if (trace == null) scope else "$scope/${action.kind.name.lowercase()}${wand.hand.size}")
                        targets.forEach { it.addedTriggers += AppendedPayload(
                            action.kind, ArrayList(payload.map(SpellAssembly::copyCard))) }
                        // Noita追加系列：寻找载体途中经过的普通修正免费，抽牌控制仍付费。
                        fun waive(cards: List<Magic>) {
                            cards.forEach { card -> when(card) {
                                is Modifier -> card.mana = 0.0
                                is DrawAction -> waive(card.expanded)
                                else -> Unit
                            } }
                        }
                        waive(carrier)
                    }
                    action.expanded += carrier
                    return -1
                }
            }
        }
        while (remaining > 0) {
            val instance = take() ?: break
            chain += instance
            remaining += prepare(instance)
        }
        return chain
    }

    fun discard() {
        wand.discardPile += wand.hand
        wand.discardPile += wand.discarded
        wand.hand.clear()
        // 并入后必须清空暂存表：否则每次施法都会把历史上所有被丢弃的卡**重复**并进弃牌堆，
        // 弃牌堆会无界膨胀（并随牌堆一起写进存档）。
        wand.discarded.clear()
        reloadIfNeeded()
    }

    private fun reloadIfNeeded() {
        if (wand.drawPile.isEmpty() && wand.discardPile.isNotEmpty()) {
            wand.status.recharge += wand.rechargeTicks()
            wand.drawPile.clear()
            wand.discardPile.clear()
            wand.drawPile.addAll(wand.loadedMagics)
        }
    }
}
