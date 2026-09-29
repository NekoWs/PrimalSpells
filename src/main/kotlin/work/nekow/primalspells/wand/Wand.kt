package work.nekow.primalspells.wand

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import work.nekow.particledrawing.api.ParticleManager
import kotlin.math.min
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.projectile.Projectile

class Wand(var id: String) {
    val innate = arrayListOf<Magic>()

    val magics = arrayListOf<Magic?>()
    var mana: Double = 3000.0
    var delay: Int = 0
    var recharge: Int = 0
    var charge: Double = 1000.0
    var cast = 1
    var size: Int = 18

    val drawPile = arrayListOf<Magic>()
    val hand = arrayListOf<Magic>()
    val discardPile = arrayListOf<Magic>()

    val discarded = arrayListOf<Magic>()

    /** 已装载法术缓存（innate + magics），由 [load] 重建；被动驱动与牌堆重置共用 */
    val loadedMagics = arrayListOf<Magic>()

    /**
     * 法术**运行期状态**容器：法术 id → 该法术在这根法杖上的状态 NBT。
     *
     * 由 [saveTag] 在序列化时**先清空再重建**（逐个问 [loadedMagics] 里的法术要一份，
     * 见 [Magic.saveRuntimeState]），由 [fromTag] 读档时经 [Magic.loadRuntimeState] 逐个回灌；
     * 具体存什么由法术自己决定（例如 `EnergyShield` 存当前耐久与破盾暂停剩余 tick）。
     *
     * ⚠️ 键是**法术 id**，不是槽位：[magics] 里同一个法术 id 可以出现多次（重复卡），
     * 这些重复卡在存档层面**共享同一份**运行期状态 —— 保存时后写的覆盖先写的，
     * 读档时同一份值回灌给所有同 id 的卡。对当前唯一的用户（`EnergyShield`）这正是想要的语义：
     * 一根法杖上的多张护盾卡**共用一个耐久池**（容量 = 每张上限 × 卡数、只出一层球壳），
     * 由第一张卡负责结算、其余卡每 tick 把它的值镜像过来（见 `EnergyShield.onInventoryTick`）。
     * 若将来出现「每张卡各自计时」的法术，需要把键从法术 id 换成槽位索引。
     */
    val magicStates = hashMapOf<String, CompoundTag>()

    data class Status(
        var mana: Double = 0.0,
        var delay: Int = 0,
        var recharge: Int = 0,
        var lastDelay: Int = 0,
        var lastRecharge: Int = 0,
        var particleManager: ParticleManager? = null,
        var failedReason: ArrayList<String> = arrayListOf()
    )
    var status: Status = Status()

    fun load() {
        val previous = loadedMagics.toList()
        loadedMagics.clear()
        loadedMagics.addAll(innate)
        magics.filterNotNull().forEach { loadedMagics.add(it) }
        // 通知「本次重建后不再装载」的实例（法术被取下 / 槽位被替换）：让它们清理只该在装载期间
        // 存在的东西（如 `EnergyShield` 的球壳粒子组）—— 此后它们不会再收到任何 tick。
        previous.forEach { if (it !in loadedMagics) it.onUnloaded() }
        drawPile.clear()
        hand.clear()
        discardPile.clear()
        drawPile.addAll(loadedMagics)
    }

    /**
     * 丢弃本内存实例：通知全部已装载法术 [Magic.onUnloaded]（清理粒子组等外部资源），
     * 并清空法术/牌堆列表。由 [WandManager] 在回收「无人持有」的法杖前调用
     * （调用方负责先 [saveTag] 落盘 —— 存档才是权威副本）。
     */
    fun unload() {
        loadedMagics.forEach { it.onUnloaded() }
        loadedMagics.clear()
        magics.clear()
        drawPile.clear()
        hand.clear()
        discardPile.clear()
    }

    /**
     * 进行一次施法：检查 delay/recharge 是否就绪，然后抽牌并执行。
     *
     * @param caster 施法者实体
     * @return true 如果施法成功，false 如果因 delay/recharge 被阻止
     */
    fun spell(caster: Entity): Boolean {
        // 持有者记录入口 2/2：任何实体主动施法都算一次「确认持有」。
        // 手持法杖的**生物**不走 WandItem.inventoryTick 的被动驱动路径，只能在这里补记，
        // 否则 WandManager 的回收判定会把它当成无人持有；玩家两处都会记，重复写同一张表无副作用。
        WandManager.markHeld(WandID(id), caster.uuid, caster.level().gameTime)
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
     * 执行手牌：先将所有 [Modifier] 的法术效果提取出来（消耗法力、累加 delay/recharge），
     * 再依次施放每个 [Projectile]，如果有 [TriggerSpell] 则额外抽牌组装 payload。
     *
     * @param caster 施法者实体
     * @param hand   当前手牌列表（本方法负责清空）
     */
    private fun castHand(caster: Entity, hand: ArrayList<Magic>) {
        val magics = arrayListOf<Magic>()
        magics += hand

        val revises = magics.filterIsInstance<Modifier>().toList()
        revises.forEach { magics.remove(it) }
        val validRevises = revises.filter {
            if (consumeMana(it)) {
                status.recharge += it.recharge
                status.delay += it.delay
                true
            } else {
                false
            }
        }

        for (projectile in magics.filterIsInstance<Projectile>()) {
            if (!consumeMana(projectile)) continue
            val p = projectile.clone()
            if (p is TriggerSpell) {
                loadPayload(p)
            }
            val effects = validRevises.flatMap { it.effects() }
            p.effects += effects
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

        // 手牌在 payload 抽取完成后统一丢弃：过早 discard 会触发 reload，
        // 导致触发法术（TriggerSpell）重新抽到刚施放的牌（含自身）。
        discard()
    }

    /**
     * 以文本形式显示触发法术内容
     */
    internal fun renderTriggers(p: Magic, effects: Boolean = false): String {
        fun toString(magic: Magic): String =
            "${magic::class.simpleName.toString()}[${magic.effects.joinToString(",") { it::class.simpleName.toString() }}]"
        if (p !is TriggerSpell) return toString(p)
        return "${toString(p)}(${p.payload.map { renderTriggers(it) }.joinToString(separator = " + ") { it }})"
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
            if (hand.remove(it)) discarded += it
        }
    }

    private fun discard() {
        discardPile += hand
        discardPile += discarded
        hand.clear()
        // 并入后必须清空暂存表：否则每次施法都会把历史上所有被丢弃的卡**重复**并进弃牌堆，
        // 弃牌堆会无界膨胀（并随 restorePiles 一起写进存档）。
        discarded.clear()
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
            drawPile.clear()
            discardPile.clear()
            drawPile.addAll(loadedMagics)
        }
    }

    /**
     * 每 tick 维护法杖状态：递减 delay/recharge，按充电速率回复法力。
     *
     * 传入 [caster]（法杖持有者，由 WandItem.inventoryTick 在法杖处于主手/副手时提供）时，
     * 额外驱动被动法术：对 [loadedMagics] 中 [Magic.needInventory] 为 true 的法术，
     * 设置 caster/wand 后调用其 [Magic.onInventoryTick]。
     * 法力/delay 等维护仍只由 [WandManager.tick]（caster 为 null）执行，避免双倍结算。
     *
     * 闲置分支（`caster == null`，由 [WandManager.tick] 每 tick 调用）额外负责
     * 「被动法术未被持有」的通知：若上一 tick 之后没有持有者驱动过被动，就对
     * [Magic.needInventory] 的法术各调一次 [Magic.onPassiveIdle]（用于放下后的延迟清理/淡出）。
     * 需要 [level] 才能判断时间差；不传则跳过该通知。
     *
     * 注：原版对主手持物品每 tick 会触发两次 inventoryTick（Inventory.tick 与
     * EntityEquipment.tick），因此按游戏 tick 去重，保证被动每 tick 只驱动一次。
     */
    fun tick(caster: Entity? = null, level: ServerLevel? = null) {
        if (caster != null) {
            val pm = status.particleManager
            if (pm?.level != caster.level()) {
                status.particleManager = ParticleManager.of(caster.level())
            }
            val gameTime = caster.level().gameTime
            if (lastPassiveTick == gameTime) return
            lastPassiveTick = gameTime
            loadedMagics.forEach { magic ->
                if (magic.needInventory) {
                    magic.caster = caster
                    magic.wand = this
                    magic.onInventoryTick()
                }
            }
            return
        }
        if (status.delay > 0) status.delay--
        if (status.recharge > 0) status.recharge--
        if (status.mana < mana) status.mana = min(mana, status.mana + charge / 20)

        // 被动「未被持有」通知：闲置分支跑在 ServerTickEvent.Pre（实体 tick 之前），
        // 因此「本 tick 被持有」时 lastPassiveTick 恰为 gameTime - 1；大于 1 说明持有者已离开。
        if (level != null && level.gameTime - lastPassiveTick > 1) {
            loadedMagics.forEach { magic ->
                if (magic.needInventory) magic.onPassiveIdle()
            }
        }
    }

    private var lastPassiveTick: Long = -1

    /**
     * 将法杖完整状态序列化为 NBT。
     *
     * 除属性与法术槽（`innate`/`magics`，空槽写空串保序）外，还写出：
     * - `magic_states`：法术运行期状态（法术 id → NBT，见 [magicStates]）；
     * - `draw_pile` / `hand` / `discard_pile`：三条牌堆的法术 id 序列。
     *
     * 后两者是「内存实例可被回收 + 懒加载」不丢状态的前提，见 [WandManager] 的 KDoc。
     *
     * @return 包含所有属性、法术列表、法术运行期状态与牌堆的 CompoundTag
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
        // 固定写满到 max(size, 已用槽数)：空槽写成空串占位，
        // 否则读档时后面的法术会整体前移，装到错误的槽位上。
        val slotCount = maxOf(size, magics.size, 1)
        for (i in 0 until slotCount) {
            magicsList.add(StringTag.valueOf(magics.getOrNull(i)?.id ?: ""))
        }
        tag.put("magics", magicsList)
        // 法术运行期状态：逐个问 loadedMagics（innate + magics）要一份，按法术 id 落盘。
        // 先清空再重建 —— 容器始终只反映「当前装在这根杖上」的法术：法术被取下、或法术自己不写内容时，
        // 都不会留下上一轮的陈旧条目（否则那些状态会跟着存档一直漂）。
        // 重复卡（同一 id 出现多次）在容器里天然合并成一份，所以下面按容器写列表、而不是按卡写。
        magicStates.clear()
        loadedMagics.forEach { magic ->
            val state = CompoundTag()
            magic.saveRuntimeState(state)
            if (!state.isEmpty) magicStates[magic.id] = state
        }
        val stateList = ListTag()
        // 按 id 排序：落盘结果不依赖哈希顺序，便于比对存档差异
        magicStates.entries.sortedBy { it.key }.forEach { (magicId, state) ->
            val entry = CompoundTag()
            entry.putString("id", magicId)
            entry.put("state", state)
            stateList.add(entry)
        }
        tag.put("magic_states", stateList)
        // 牌堆：只存法术 id 序列（法术实例本身是一次性的，不入库）。
        // 必须存：WandManager 会把无人持有的法杖内存实例回收掉、之后按需从存档懒加载，
        // 不存牌堆就会变成「放下再捡起 = 重新洗牌」。
        tag.put("draw_pile", pileTag(drawPile))
        tag.put("hand", pileTag(hand))
        tag.put("discard_pile", pileTag(discardPile))
        return tag
    }

    /**
     * 把一张牌堆序列化成法术 id 列表。
     *
     * @param pile 牌堆（[drawPile] / [hand] / [discardPile]），顺序即抽牌顺序
     */
    private fun pileTag(pile: List<Magic>): ListTag {
        val list = ListTag()
        pile.forEach { list.add(StringTag.valueOf(it.id)) }
        return list
    }

    /**
     * 把法术 id 列表还原成法术实例。
     *
     * id 已不在注册表里的牌直接跳过：法术被移除后旧存档仍可读，等价于那张牌消失。
     */
    private fun magicsFrom(list: ListTag): List<Magic> =
        list.mapNotNull { MagicManager.create((it as StringTag).asString().get()) }

    /**
     * 读档：把 [magicStates] 里的状态回灌给 [loadedMagics] 里对应 id 的实例。
     *
     * 必须在 [load] 之后调用（要先有实例），且此时 `wand`/`caster`/`status` 都还没被设置 ——
     * 这正是 [Magic.loadRuntimeState] 里禁止访问它们的约定所对应的时机。
     */
    private fun applyMagicStates(tag: CompoundTag) {
        magicStates.clear()
        tag.getListOrEmpty("magic_states").forEach { element ->
            val entry = element as? CompoundTag ?: return@forEach
            val id = entry.getStringOr("id", "")
            if (id.isNotEmpty()) magicStates[id] = entry.getCompoundOrEmpty("state")
        }
        loadedMagics.forEach { magic ->
            magicStates[magic.id]?.let { magic.loadRuntimeState(it) }
        }
    }

    /**
     * 读档：按 id 还原三条牌堆。
     *
     * 存档里没有这些键时（旧存档、或新建的空法杖）保持 [load] 重建出来的初始牌堆；
     * 这样既兼容旧档，也不会让「编辑法术槽」后的重建逻辑失效。
     *
     * [discarded]（一次施法内部的暂存表）不入库：它的内容在每次施法末尾已经并进 [discardPile]，
     * 存了反而会在恢复后再并一次。
     */
    private fun restorePiles(tag: CompoundTag) {
        if (!tag.contains("draw_pile")) return
        val draw = magicsFrom(tag.getListOrEmpty("draw_pile"))
        val inHand = magicsFrom(tag.getListOrEmpty("hand"))
        val discard = magicsFrom(tag.getListOrEmpty("discard_pile"))
        // 兜底：三条牌堆都空、但法杖明明装着法术 —— 这种状态恢复出来会让法杖永远抽不到牌，
        // 按存档异常处理，退回 load() 重建的初始牌堆（宁可重新洗牌，也不要一把锁死的杖）。
        if (draw.isEmpty() && inHand.isEmpty() && discard.isEmpty() && loadedMagics.isNotEmpty()) return
        drawPile.clear()
        drawPile += draw
        hand.clear()
        hand += inHand
        discardPile.clear()
        discardPile += discard
    }

    companion object {
        /**
         * 从 NBT 反序列化重建法杖对象。
         *
         * 顺序有讲究：先按 `innate`/`magics` 建实例并 [load]（含牌堆初始重建），
         * 再回灌法术运行期状态（[applyMagicStates]），最后按存档覆盖牌堆（[restorePiles]）。
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
                // 空串 = 空槽占位，必须原样保留，保证槽位索引不变
                val id = (it as StringTag).asString().get()
                wand.magics += if (id.isEmpty()) null else MagicManager.create(id)
            }
            return wand.also {
                it.load()
                // 运行期状态要在 load() 之后（要先有 loadedMagics 里的实例可灌），
                // 且在任何施法/被动驱动之前：此刻 wand/caster 还是未初始化的 lateinit，
                // 所以钩子里不得访问它们（见 Magic.loadRuntimeState 的约定）。
                it.applyMagicStates(tag)
                it.restorePiles(tag)
            }
        }
    }
}
