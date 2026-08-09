package work.nekow.primalspells.wand

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.*
import kotlin.math.min

class Wand(var id: String) {
    /** 始终施放 **/
    val innate = arrayListOf<Magic>()

    val magics = arrayListOf<Magic?>()
    /** 法力最大值 **/
    var mana: Double = 100.0
    /** 施放延迟 **/
    var delay: Int = 0
    /** 充能时间 **/
    var recharge: Int = 0
    /** 充能速度 (charge/s) **/
    var charge: Double = 10.0
    /** 施放数 **/
    var cast = 1
    /** 容量 **/
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

    private fun castHand(caster: Entity, hand: ArrayList<Magic>) {
        val magics = arrayListOf<Magic>()
        magics += hand
        discard()
        val effects = magics.filterIsInstance<Revise>()
            .flatMap {
                magics.remove(it)
                status.recharge += it.recharge
                status.delay += it.delay
                if (consumeMana(it)) it.effects
                else emptyList()
            }
        for (projectile in magics.filterIsInstance<Projectile>()) {
            if (!consumeMana(projectile)) continue
            val p = projectile.clone()
            if (p is TriggerSpell) {
                draw(p.triggerCast, false)
                hand.forEach {
                    if (consumeMana(it)) {
                        p.payload.add(it)
                        p.recharge += it.recharge
                        p.delay += it.delay
                    }
                }
                discard()
            }
            p.effects.addAll(effects)
            p.caster = caster
            p.wand = this
            p.position = caster.eyePosition.toVector3f().sub(0F, 0.2F, 0F)
            p.velocity = caster.lookAngle.toVector3f()
            p.spell()
            status.recharge += p.recharge
            status.delay += p.delay

            status.lastDelay = status.delay
            status.lastRecharge = status.recharge
            MagicManager.add(p)
        }
    }

    fun consumeMana(magic: Magic): Boolean {
        if (status.mana < magic.mana) {
            status.failedReason += "Mana(${status.mana}) < ${magic.mana}"
            return false
        }
        status.mana -= magic.mana
        return true
    }

    private fun discard() {
        discardPile += hand
        hand.clear()
        reloadIfNeeded()
    }

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

    fun draw(budget: Int, wrapping: Boolean = true) {
        val (remaining, result) = drawFrom(budget, drawPile)
        hand += result
        if (wrapping && remaining > 0) {
            val pile = drawFrom(remaining, discardPile)
            hand += pile.second
        }
    }

    private fun reloadIfNeeded() {
        if (drawPile.isEmpty() && discardPile.isNotEmpty()) {
            status.recharge += recharge
            load()
        }
    }

    fun tick() {
        if (status.delay > 0) status.delay--
        if (status.recharge > 0) status.recharge--
        if (status.mana < mana) status.mana = min(mana, status.mana + charge / 20)
    }

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
