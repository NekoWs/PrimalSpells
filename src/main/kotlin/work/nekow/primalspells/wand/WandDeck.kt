package work.nekow.primalspells.wand

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.projectile.Projectile

/** 抽牌树与弃牌/回洗；引用副本使用独立游标，原卡只在真实抽取时进入手牌。 */
internal class WandDeck(private val wand: Wand, private val references: List<Wand> = emptyList(),
                        private val random: Random = Random(0)) {
    private data class Source(val card: Magic, val free: Boolean = false, val physical: Boolean = false)
    private inner class Cursor(val copies: ArrayDeque<Source>? = null, val fallback: Cursor? = null) {
        fun peek(): List<Source> = copies?.toList()?.takeIf { it.isNotEmpty() }
            ?: if (copies == null) wand.drawPile.map(::Source) else fallback?.peek().orEmpty()
        fun take(wrap: Boolean, useFallback: Boolean): Source? {
            if (copies != null) return copies.removeFirstOrNull() ?: if (useFallback) fallback?.take(false, true) else null
            val pile = when {
                wand.drawPile.isNotEmpty() -> wand.drawPile
                wrap && wand.discardPile.isNotEmpty() -> wand.discardPile
                else -> return null
            }
            return Source(pile.removeFirst().also { wand.hand += it }, physical = true)
        }
    }
    private val active = Collections.newSetFromMap(IdentityHashMap<Magic, Boolean>())
    private var nodes = 0
    private var depth = 0
    private var trace: ((Magic, String) -> Unit)? = null

    /** 同一解析器服务真实施放与只读预览；循环引用跳过活动原卡，过量展开有限终止。 */
    fun draw(budget: Int, wrapping: Boolean = true, trace: ((Magic, String) -> Unit)? = null,
             scope: String = "root"): ArrayList<Magic> {
        this.trace = trace
        return drawFrom(budget, Cursor(), wrapping, scope)
    }

    private fun limit(): Boolean {
        if (nodes < 4096 && depth < 64) return false
        if ("Expansion limit" !in wand.status.failedReason) wand.status.failedReason += "Expansion limit"
        return true
    }

    private fun drawFrom(budget: Int, cursor: Cursor, wrapping: Boolean, scope: String,
                         fallback: Boolean = true): ArrayList<Magic> {
        var remaining = budget.toLong()
        var allowWrap = wrapping
        val chain = arrayListOf<Magic>()
        while (remaining > 0 && !limit()) {
            val source = cursor.take(allowWrap, fallback) ?: break
            // 复制不消费实体槽；跟踪只记录真实抽取，防止预览反复改写原槽作用域。
            if (source.physical) trace?.invoke(source.card, scope)
            val firstVisit = active.add(source.card)
            if (!firstVisit && source.card is DrawAction) continue
            nodes++
            val card = source.card.clone()
            card.useOwner = if (source.physical) source.card else null
            if (source.free) { card.mana = 0.0; card.delay = 0; card.recharge = 0; card.castingCostsWaived = true }
            chain += card
            depth++
            val delta = prepare(card, source.card, cursor, scope)
            if (!source.physical) SpellAssembly.waiveUses(card)
            depth--
            if (firstVisit) active.remove(source.card)
            remaining += delta
            if (card is DrawAction && card.kind in setOf(DrawAction.Kind.ALL, DrawAction.Kind.REFRESH)) allowWrap = false
        }
        return chain
    }

    private fun prepare(card: Magic, original: Magic, cursor: Cursor, scope: String): Int {
        if (card !is DrawAction) {
            if (card is TriggerSpell) card.payload += drawFrom(card.triggerCast, cursor, false, childScope(scope, "hit"))
            return card.cast - 1
        }
        when (card.kind) {
            DrawAction.Kind.ALL -> card.expanded += drawFrom(Int.MAX_VALUE, cursor, false, scope)
            DrawAction.Kind.REFRESH -> Unit
            DrawAction.Kind.DIVIDE -> {
                // 准备一张卡（包括它的荷载）后再深拷贝；继续抽牌预算只算一次。
                val source = cursor.take(false, true) ?: return -1
                if (source.physical) trace?.invoke(source.card, scope)
                if (limit()) return -1
                val firstVisit = active.add(source.card)
                if (!firstVisit && source.card is DrawAction) return -1
                nodes++
                val next = source.card.clone()
                next.useOwner = if (source.physical) source.card else null
                if (source.free) { next.mana = 0.0; next.delay = 0; next.recharge = 0; next.castingCostsWaived = true }
                depth++
                val delta = prepare(next, source.card, cursor, scope)
                depth--
                if (firstVisit) active.remove(source.card)
                val count = treeSize(next)
                repeat(card.copies) {
                    if (nodes + count <= 4096) {
                        card.expanded += SpellAssembly.copyCard(next).also(SpellAssembly::waiveUses); nodes += count
                    }
                    else { nodes = 4096; limit() }
                }
                return delta
            }
            DrawAction.Kind.HIT, DrawAction.Kind.TIMER, DrawAction.Kind.EXPIRE -> {
                val carrier = drawFrom(1, cursor, false, scope)
                val targets = shots(carrier)
                if (targets.isNotEmpty()) {
                    val payload = drawFrom(1, cursor, false, childScope(scope, card.kind.name.lowercase()))
                    val payloadSize = payload.sumOf(::treeSize)
                    targets.forEachIndexed { index, target ->
                        // 第一枚载体直接接收已准备模板；其余独立副本同样计入展开预算。
                        if (index == 0) target.addedTriggers += AppendedPayload(card.kind, ArrayList(payload))
                        else if (nodes + payloadSize <= 4096) {
                            target.addedTriggers += AppendedPayload(card.kind, ArrayList(payload.map(SpellAssembly::copyCard)))
                            nodes += payloadSize
                        } else { nodes = 4096; limit() }
                    }
                    fun waive(cards: List<Magic>) {
                        cards.forEach { when (it) {
                            is Modifier -> it.mana = 0.0
                            is DrawAction -> waive(it.expanded)
                            else -> Unit
                        } }
                    }
                    waive(carrier)
                }
                card.expanded += carrier
            }
            else -> {
                val all = wand.loadedMagics
                val selected = when (card.kind) {
                    DrawAction.Kind.FIRST -> all.take(1).map(::Source)
                    DrawAction.Kind.LAST -> all.takeLast(1).map(::Source)
                    DrawAction.Kind.NEXT_TWO -> cursor.peek().take(2)
                    DrawAction.Kind.ALL_COPIES -> all.filter { it !== original &&
                        !(it is DrawAction && it.kind == DrawAction.Kind.REFRESH) }.map(::Source)
                    DrawAction.Kind.MODIFIERS -> all.filterIsInstance<Modifier>().map { Source(it, true) }
                    DrawAction.Kind.PROJECTILES -> all.filterIsInstance<Projectile>().filter { it.speed != 0f }.map { Source(it, true) }
                    DrawAction.Kind.STATIC_PROJECTILES -> all.filterIsInstance<Projectile>().filter { it.speed == 0f }.map { Source(it, true) }
                    DrawAction.Kind.OTHER_WAND -> references.filter { it.id != wand.id && it.loadedMagics.isNotEmpty() }
                        .randomOrNull(random)?.loadedMagics?.randomOrNull(random)?.let { listOf(Source(it)) }.orEmpty()
                    else -> emptyList()
                }
                val virtual = Cursor(ArrayDeque(selected), cursor)
                card.expanded += drawFrom(Int.MAX_VALUE, virtual, false, scope, fallback = false)
                // Miu明确继续抽牌；其它引用若只得到修正，也让该修正作用于后续实际法术。
                val expanded = flatten(card.expanded)
                val extra = expanded.filterIsInstance<Modifier>().sumOf { it.cast - 1 }
                if (card.kind == DrawAction.Kind.MODIFIERS) {
                    card.expanded += drawFrom(1 + extra, cursor, false, scope)
                } else if (expanded.isEmpty()) return -1
                else if (expanded.none { it is Projectile || (it is OtherSpell && it !is DrawAction) ||
                            (it is DrawAction && it.kind == DrawAction.Kind.REFRESH) }) return extra
            }
        }
        return -1
    }

    private fun childScope(scope: String, kind: String) = if (trace == null) scope else "$scope/$kind${wand.hand.size}"
    private fun flatten(cards: List<Magic>): List<Magic> = cards.flatMap {
        if (it is DrawAction) listOf(it) + flatten(it.expanded) else listOf(it)
    }
    private fun shots(cards: List<Magic>) = flatten(cards).filterIsInstance<Projectile>()
    private fun treeSize(card: Magic): Int = 1 +
        (if (card is DrawAction) card.expanded.sumOf(::treeSize) else 0) +
        (if (card is TriggerSpell) card.payload.sumOf(::treeSize) else 0) +
        (if (card is Projectile) card.addedTriggers.sumOf { it.cards.sumOf(::treeSize) } else 0)

    /** 刷新只在成功付费后执行；单张跳过充能，多张按正常充能结算。 */
    fun discard(refreshes: Int = 0) {
        wand.discardPile += wand.hand
        wand.discardPile += wand.discarded
        wand.hand.clear(); wand.discarded.clear()
        if (refreshes > 0 || (wand.drawPile.isEmpty() && wand.discardPile.isNotEmpty())) {
            if (refreshes == 1) wand.status.recharge = 0.0 else wand.status.recharge += wand.rechargeTicks()
            wand.drawPile.clear(); wand.discardPile.clear(); wand.drawPile += wand.loadedMagics
        }
    }
}
