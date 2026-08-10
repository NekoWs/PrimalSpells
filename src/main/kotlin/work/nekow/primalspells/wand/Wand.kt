package work.nekow.primalspells.wand

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.*
import kotlin.math.min

class Wand(var id: String) {
    val innate = arrayListOf<Magic>()

    val magics = arrayListOf<Magic?>()
    var mana: Double = 300.0
    var delay: Int = 0
    var recharge: Int = 0
    var charge: Double = 100.0
    var cast = 1
    var size: Int = 9

    val drawPile = arrayListOf<Magic>()
    val hand = arrayListOf<Magic>()
    val discardPile = arrayListOf<Magic>()

    data class Status(
        var mana: Double = 0.0,
        var delay: Int = 0,
        var recharge: Int = 0,
        var lastDelay: Int = 0,
        var lastRecharge: Int = 0,
        var failedReason: ArrayList<String> = arrayListOf()
    )
    var status: Status = Status()

    fun load() {
        drawPile.clear()
        hand.clear()
        discardPile.clear()
        drawPile.addAll(innate)
        magics.filterNotNull().forEach { drawPile.add(it) }
    }

    /**
     * 进行一次施法：检查 delay/recharge 是否就绪，然后抽牌并执行。
     *
     * @param caster 施法者实体
     * @return true 如果施法成功，false 如果因 delay/recharge 被阻止
     */
    fun spell(caster: Entity): Boolean {
        status.failedReason.clear()
        if (status.delay > 0) {
            status.failedReason.add("Delay ${status.delay}/${delay}")
            return false
        }
        if (status.recharge > 0) {
            status.failedReason.add("Recharge ${status.recharge}/${recharge}")
            return false
        }
        status.delay = 0
        status.recharge = 0
        draw(cast)
        castHand(caster, hand)
        return true
    }

    /**
     * 执行手牌：先将所有 [Revise] 的法术效果提取出来（消耗法力、累加 delay/recharge），
     * 再依次施放每个 [Projectile]，如果有 [TriggerSpell] 则额外抽牌组装 payload。
     *
     * @param caster 施法者实体
     * @param hand   当前手牌列表（本方法负责清空）
     */
    private fun castHand(caster: Entity, hand: ArrayList<Magic>) {
        val magics = arrayListOf<Magic>()
        magics += hand
        discard()

        val effects = magics.filterIsInstance<Revise>()
            .flatMap {
                magics.remove(it)
                status.recharge += it.recharge
                status.delay += it.delay
                if (consumeMana(it)) it.effects()
                else emptyList()
            }
        for (projectile in magics.filterIsInstance<Projectile>()) {
            if (!consumeMana(projectile)) continue
            val p = projectile.clone()
            if (p is TriggerSpell) {
//                draw(p.triggerCast, false)
//                hand.forEach {
//                    if (consumeMana(it)) {
//                        p.payload.add(it)
//                        p.recharge += it.recharge
//                        p.delay += it.delay
//                    }
//                }
//                discard()
                loadPayload(p)
            }
            p.effects.addAll(effects)
            p.caster = caster
            p.wand = this
            p.position = caster.eyePosition.toVector3f().sub(0F, 0.1F, 0F)
            p.velocity = caster.lookAngle.toVector3f().normalize()
            p.spell()
            status.recharge += p.recharge
            status.delay += p.delay

            status.lastDelay = status.delay
            status.lastRecharge = status.recharge
            MagicManager.add(p)
        }
    }

    /**
     * 以文本形式显示触发法术内容
     */
    internal fun renderTriggers(p: Magic): String {
        fun className(any: Any): String = any::class.simpleName.toString()
        if (p !is TriggerSpell) return className(p)
        return "${className(p)}(${p.payload.map { renderTriggers(it) }.joinToString(separator = " → ") { it }})"
    }

    private fun loadPayload(projectile: Projectile): Projectile {
        if (projectile !is TriggerSpell) return projectile
        val magics = draw(projectile.triggerCast, false)
        magics.forEach {
            val p = it.clone()
            if (consumeMana(p)) {
                projectile.payload += if (p is Projectile && p is TriggerSpell) {
                    loadPayload(p)
                } else { p }
                projectile.recharge += p.recharge
                projectile.delay += p.delay
            }
        }
        discard(magics)
        return projectile
    }

    /**
     * 检查并消耗法术所需法力。
     *
     * @param magic 要消耗法力的法术
     * @return true 如果法力足够并已扣除
     */
    fun consumeMana(magic: Magic): Boolean {
        if (status.mana < magic.mana) {
            status.failedReason += "Mana(${status.mana}) < ${magic.mana}"
            return false
        }
        status.mana -= magic.mana
        return true
    }

    /**
     * 丢弃指定列表中的法术
     *
     * @param magics 法术列表
     */
    private fun discard(magics: List<Magic>) {
        magics.forEach {
            if (hand.remove(it)) discardPile += it
        }
        reloadIfNeeded()
    }

    private fun discard() {
        discardPile += hand
        hand.clear()
        reloadIfNeeded()
    }

    /**
     * 从指定牌堆中抽牌，直到预算耗尽或牌堆为空。
     * 每张法术的 [Magic.cast] 会影响剩余预算（正数增加可抽范围，负数或 0 减少）。
     *
     * @param budget 可抽牌的预算值
     * @param pile   要从中抽取的牌堆
     * @return Pair(剩余预算, 抽到的法术列表)
     */
    fun drawFrom(budget: Int, pile: ArrayList<Magic>): Pair<Int, ArrayList<Magic>> {
        var remaining = budget
        val result = arrayListOf<Magic>()
        while (remaining > 0 && pile.isNotEmpty()) {
            val magic = pile.removeFirst()
            result += magic
            remaining += magic.cast - 1
        }
        return Pair(remaining, result)
    }

    /**
     * 从抽牌堆抽牌，若预算仍有剩余且允许循环，则从弃牌堆继续抽取。
     *
     * @param budget   可抽牌的预算值
     * @param wrapping 是否在抽牌堆耗尽后循环弃牌堆
     */
    fun draw(budget: Int, wrapping: Boolean = true): ArrayList<Magic> {
        val res = arrayListOf<Magic>()
        val (remaining, result) = drawFrom(budget, drawPile)
        res += result
        if (wrapping && remaining > 0) {
            val pile = drawFrom(remaining, discardPile)
            res += pile.second
        }
        hand += res
        return res
    }

    private fun reloadIfNeeded() {
        if (drawPile.isEmpty() && discardPile.isNotEmpty()) {
            status.recharge += recharge
            load()
        }
    }

    /**
     * 每 tick 维护法杖状态：递减 delay/recharge，按充电速率回复法力。
     */
    fun tick() {
        if (status.delay > 0) status.delay--
        if (status.recharge > 0) status.recharge--
        if (status.mana < mana) status.mana = min(mana, status.mana + charge / 20)
    }

    /**
     * 将法杖完整状态序列化为 NBT。
     *
     * @return 包含所有属性和法术列表的 CompoundTag
     */
    fun saveTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("id", id)
        tag.putDouble("mana", mana)
        tag.putInt("delay", delay)
        tag.putInt("recharge", recharge)
        tag.putDouble("charge", charge)
        tag.putInt("cast", cast)
        tag.putInt("size", size)
        tag.putDouble("status_mana", status.mana)
        tag.putInt("status_delay", status.delay)
        tag.putInt("status_recharge", status.recharge)
        val innateList = ListTag()
        innate.forEach { innateList.add(StringTag.valueOf(it.id)) }
        tag.put("innate", innateList)
        val magicsList = ListTag()
        magics.forEach { if (it != null) magicsList.add(StringTag.valueOf(it.id)) }
        tag.put("magics", magicsList)
        return tag
    }

    companion object {
        /**
         * 从 NBT 反序列化重建法杖对象。
         *
         * @param tag 包含法杖序列化数据的 CompoundTag
         * @return 已初始化并 load() 过的 Wand 实例
         */
        fun fromTag(tag: CompoundTag): Wand {
            val wand = Wand(tag.getStringOr("id", ""))
            wand.mana = tag.getDoubleOr("mana", 0.0)
            wand.delay = tag.getIntOr("delay", 0)
            wand.recharge = tag.getIntOr("recharge", 0)
            wand.charge = tag.getDoubleOr("charge", 0.0)
            wand.cast = tag.getIntOr("cast", 1)
            wand.size = tag.getIntOr("size", 9)
            wand.status.mana = tag.getDoubleOr("status_mana", 0.0)
            wand.status.delay = tag.getIntOr("status_delay", 0)
            wand.status.recharge = tag.getIntOr("status_recharge", 0)
            tag.getListOrEmpty("innate").forEach {
                MagicManager.create((it as StringTag).asString().get())?.let { m -> wand.innate += m }
            }
            tag.getListOrEmpty("magics").forEach {
                MagicManager.create((it as StringTag).asString().get())?.let { m -> wand.magics += m }
            }
            return wand.also { wand.load() }
        }
    }
}
