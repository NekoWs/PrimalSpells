package work.nekow.primalspells.wand

import kotlin.math.max
import kotlin.math.min
import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.OtherSpell
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.projectile.Projectile

/** 一次施法可在刷新边界增量结算的账本；根链与荷载共享费用，各自链内共享修正。 */
internal class CastTransaction(private val wand: Wand) {
    val accepted = arrayListOf<Magic>()
    private var resetDelay = false
    private var delay = 0.0
    private var recharge = 0.0
    private var delayReduction = 0.0
    var healthCost = 0f
        private set
    private var refreshes = 0
    private val reservedUses = java.util.IdentityHashMap<Magic, Int>()
    private val funded = java.util.IdentityHashMap<Magic, Boolean>()
    private var usesCommitted = false

    private fun fund(card: Magic): Boolean {
        funded[card]?.let { return it }
        val owner = card.useOwner?.takeIf { it.maxUses > 0 }
        if (owner != null && owner.remainingUses <= (reservedUses[owner] ?: 0)) {
            wand.status.failedReason += "Uses exhausted (${card.id})"
            funded[card] = false
            return false
        }
        if (!wand.consumeMana(card)) { funded[card] = false; return false }
        if (owner != null) reservedUses[owner] = (reservedUses[owner] ?: 0) + 1
        funded[card] = true
        account(card)
        return true
    }

    /** 刷新是抽牌时的结算边界；只支付此刻已抽出的前缀，不能借用右侧仓库的回蓝牌。 */
    fun draw(references: List<Wand> = emptyList()): ArrayList<Magic> =
        WandDeck(wand, references, kotlin.random.Random(wand.referenceSeed)).draw(wand.cast, onRefresh = { card, root, visible ->
            pay(root, visible)
            (funded[card] == true).also { accepted ->
                if (accepted) {
                    refreshes++
                    // 刷新移走当前手牌，故此前预留的使用次数不再经普通弃牌提交；不是补满次数。
                    reservedUses.clear()
                }
            }
        })

    /** 仅真实施放提交次数；荷载与MP一样预付，预览、拒绝施法和复制均不修改实体卡。 */
    fun commitUses() {
        check(!usesCommitted) { "法术次数事务不能重复提交" }
        usesCommitted = true
        reservedUses.forEach { (card, count) -> card.remainingUses -= count }
    }

    private fun account(card: Magic) {
        accepted += card
        delay += card.delay
        recharge += card.recharge
        delayReduction += min(0.0, card.delay.toDouble())
        resetDelay = resetDelay || (card.resetsCastDelay && !card.castingCostsWaived)
        healthCost += card.healthCost
    }

    /** visible指定刷新时已进入的前缀；最终结算才过滤荷载，重复访问不重复收费或扣次。 */
    fun pay(chain: List<Magic>, visible: Set<Magic>? = null): ArrayList<Magic> {
        val paid = arrayListOf<Magic>()
        fun expand(cards: List<Magic>): List<Magic> {
            val present = if (visible == null) cards else cards.filter { it in visible }
            // 同层普通回蓝修正先结算，放在控制牌前后均能支付该控制牌。
            val refunds = present.filterIsInstance<Modifier>().filter { it.mana < 0.0 }.sortedBy { it.id }
            refunds.forEach { if (fund(it)) paid += it }
            return present.filter { it !in refunds }.flatMap { card ->
                if (card is DrawAction) {
                    if (fund(card)) expand(card.expanded) else emptyList()
                } else listOf(card)
            }
        }
        val expanded = expand(chain)
        for (modifier in expanded.filterIsInstance<Modifier>().sortedWith(compareBy<Modifier> { it.mana >= 0.0 }.thenBy { it.id })) {
            if (fund(modifier)) paid += modifier
        }
        for (shot in expanded.filterIsInstance<Projectile>()) {
            if (!fund(shot)) continue
            paid += shot
            if (shot is TriggerSpell) {
                val payload = pay(shot.payload.toList(), visible)
                if (visible == null) { shot.payload.clear(); shot.payload += payload }
            }
            shot.addedTriggers.forEach { trigger ->
                val payload = pay(trigger.cards.toList(), visible)
                if (visible == null) { trigger.cards.clear(); trigger.cards += payload }
            }
        }
        for (other in expanded.filterIsInstance<OtherSpell>().filter { it !is DrawAction }) {
            if (fund(other)) paid += other
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
