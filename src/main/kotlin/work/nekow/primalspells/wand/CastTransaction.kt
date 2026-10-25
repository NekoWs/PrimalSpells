package work.nekow.primalspells.wand

import kotlin.math.max
import kotlin.math.min
import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.OtherSpell
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.projectile.Projectile

/** 一次施法的付费账本；根链与独立荷载共享费用，只有各自链内共享修正。 */
internal class CastTransaction(private val wand: Wand) {
    val accepted = arrayListOf<Magic>()
    private var resetDelay = false
    private var delay = 0.0
    private var recharge = 0.0
    private var delayReduction = 0.0
    var healthCost = 0f
        private set
    private var refreshes = 0

    private fun account(card: Magic) {
        accepted += card
        delay += card.delay
        recharge += card.recharge
        delayReduction += min(0.0, card.delay.toDouble())
        resetDelay = resetDelay || (card.resetsCastDelay && !card.castingCostsWaived)
        healthCost += card.healthCost
        if (card is DrawAction && card.kind == DrawAction.Kind.REFRESH) refreshes++
    }

    /** 同一链先收集修正，荷载递归独立付费；每张被接受的牌只结算一次费用。 */
    fun pay(chain: List<Magic>): ArrayList<Magic> {
        val paid = arrayListOf<Magic>()
        fun expand(cards: List<Magic>): List<Magic> {
            // 同层普通回蓝修正先结算，放在控制牌前后均能支付该控制牌。
            val refunds = cards.filterIsInstance<Modifier>().filter { it.mana < 0.0 }.sortedBy { it.id }
            refunds.forEach { if (wand.consumeMana(it)) { paid += it; account(it) } }
            return cards.filter { it !in refunds }.flatMap { card ->
                if (card is DrawAction) {
                    if (wand.consumeMana(card)) { account(card); expand(card.expanded) } else emptyList()
                } else listOf(card)
            }
        }
        val expanded = expand(chain)
        for (modifier in expanded.filterIsInstance<Modifier>().sortedWith(compareBy<Modifier> { it.mana >= 0.0 }.thenBy { it.id })) {
            if (wand.consumeMana(modifier)) { paid += modifier; account(modifier) }
        }
        for (shot in expanded.filterIsInstance<Projectile>()) {
            if (!wand.consumeMana(shot)) continue
            paid += shot
            account(shot)
            if (shot is TriggerSpell) {
                val payload = pay(shot.payload.toList())
                shot.payload.clear()
                shot.payload += payload
            }
            shot.addedTriggers.forEach { trigger ->
                val payload = pay(trigger.cards.toList())
                trigger.cards.clear(); trigger.cards += payload
            }
        }
        for (other in expanded.filterIsInstance<OtherSpell>().filter { it !is DrawAction }) {
            if (wand.consumeMana(other)) { paid += other; account(other) }
        }
        return paid
    }

    /** 发射或只读模拟完成后统一结算，回洗费用与链锯归零规则只在这里定义。 */
    fun finish() {
        wand.status.delay = delay
        wand.status.recharge = recharge
        WandDeck(wand).discard(refreshes)
        wand.status.delay = (if (resetDelay) 0.0 else max(wand.status.delay,
            (wand.delayTicks() + delayReduction).coerceAtLeast(0.0))).coerceAtLeast(0.0)
        wand.status.recharge = wand.status.recharge.coerceAtLeast(0.0)
    }
}
