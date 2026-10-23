package work.nekow.primalspells.wand

import kotlin.math.min
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.magic.core.Magic

/** 法杖权威状态与装卸/每刻维护；解析、事务、发射、预览和NBT各有独立实现。 */
class Wand(var id: String) {
    val innate = arrayListOf<Magic>()

    val magics = arrayListOf<Magic?>()

    // ---- 数值（这里的默认值 = 调试杖 3000/1000/0/18，见 [WandTier.DEBUG]）----
    // 正常获取的法杖由 [WandTier] 的等级表在**创建时**掷出（[WandStats.roll]），之后随 NBT 存档。

    /** 法力上限 */
    var mana: Double = 3000.0

    /** **施放延迟**（刻）：每发之间的最小间隔，与卡牌自己累加的 delay 取大值，见 [spell] */
    var delayTickCount: Double = 0.0
        set(value) { field = kotlin.math.ceil(value.coerceAtLeast(0.0)) }

    /** **充能时间**（刻）：抽牌堆抽空之后的重装时间，见 [WandDeck] */
    var rechargeTickCount: Double = 0.0
        set(value) { field = kotlin.math.ceil(value.coerceAtLeast(0.0)) }

    /** 每刻充能（回蓝）。 */
    var charge: Double = 50.0

    /** 每回施放几个法术（创建时按等级权重掷出，1 的权重最高） */
    var cast = 1

    /** 法术槽位数 */
    var size: Int = 18

    val drawPile = arrayListOf<Magic>()
    val hand = arrayListOf<Magic>()
    val discardPile = arrayListOf<Magic>()

    val discarded = arrayListOf<Magic>()

    /** 已装载法术缓存（innate + magics），由 [load] 重建；被动驱动与牌堆重置共用 */
    val loadedMagics = arrayListOf<Magic>()

    /**
     * 法术ID到运行状态NBT；保存时由已装载卡的saveRuntimeState重建，读档后回灌。
     * 重复ID共享一份状态，后写覆盖先写；独立槽位计时不能使用此键模型。
     * 当前护盾破口是短命状态，不写入存档。
     */
    val magicStates = hashMapOf<String, CompoundTag>()

    data class Status(
        var mana: Double = 0.0,
        /** 冷却按完整刻计数，到零就绪，不保留负余数。 */
        var delay: Double = 0.0,
        var recharge: Double = 0.0,
        var lastDelay: Double = 0.0,
        var lastRecharge: Double = 0.0,
        var particleManager: ParticleManager? = null,
        var failedReason: ArrayList<String> = arrayListOf()
    )
    var status: Status = Status()

    /** 本杖施放延迟（完整刻）。 */
    fun delayTicks(): Double = kotlin.math.ceil(delayTickCount.coerceAtLeast(0.0))

    /** 本杖充能时间（完整刻）。 */
    fun rechargeTicks(): Double = kotlin.math.ceil(rechargeTickCount.coerceAtLeast(0.0))

    /**
     * 按掷出的数值初始化一根**新**法杖（等级 + 变体 + 抽牌），见 [WandTier]。
     *
     * ⚠️ 只应在**创建时**调用一次：创建后数值随 NBT 存档，再调一次就会覆盖玩家手里那根杖的数值。
     * 正常的创建入口是 `WandManager.createWand(stack)`（它从物品的等级组件读）。
     */
    fun applyStats(stats: WandStats) {
        mana = stats.maxMana
        charge = stats.manaPerTick
        delayTickCount = stats.delayTickCount
        rechargeTickCount = stats.rechargeTickCount
        size = stats.size
        cast = stats.cast
    }

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
        discarded.clear()
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

    /** 服务端真实施法，预览与此入口共用抽牌、付费和冷却规则。 */
    fun spell(caster: Entity): Boolean = WandCasting.cast(this, caster)

    /** 独立副本上的只读预览，不执行任何玩法钩子。 */
    fun preview(): WandPreview = WandPreviewBuilder.build(this)

    /**
     * 检查并消耗法术所需法力。
     *
     * @param magic 要消耗法力的法术
     * @return true 如果法力足够并已扣除
     */
    fun consumeMana(magic: Magic): Boolean {
        clampMana()
        if (status.mana < magic.mana) {
            status.failedReason += "Mana(${status.mana}) < ${magic.mana}"
            return false
        }
        status.mana = (status.mana - magic.mana).coerceIn(0.0, mana.coerceAtLeast(0.0))
        return true
    }

    /** 恢复旧版本超额法力，并使付费/回蓝遵守同一法力池上限。 */
    internal fun clampMana() {
        status.mana = (status.mana.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0, mana.coerceAtLeast(0.0))
    }

    /**
     * 直接扣一笔法力，**不绑定具体 [Magic]**——供被动法术结算使用（目前只有能量护盾的格挡消耗）。
     *
     * 与 [consumeMana] 的区别：这里不写 `failedReason`（被动没有"这次施法失败"的语境，
     * 而且 `failedReason` 每次施法开头都会被清空），返回值只表示「够不够扣」。
     *
     * @return true = 已扣除；false = 法力不足，**什么都没扣**
     */
    fun spendMana(amount: Double): Boolean {
        clampMana()
        if (amount <= 0.0) return true
        if (status.mana < amount) return false
        status.mana -= amount
        return true
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
        clampMana()
        if (caster != null) {
            // 显式被动驱动也是持有证据：盔甲架等实体不一定触发物品inventoryTick。
            WandManager.markHeld(WandID(id), caster.uuid, caster.level().gameTime)
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
        // 完整刻倒计时到零，避免延迟和充能出现负余数。
        status.delay = (status.delay - 1.0).coerceAtLeast(0.0)
        status.recharge = (status.recharge - 1.0).coerceAtLeast(0.0)
        if (status.mana < mana) status.mana = min(mana, status.mana + charge)

        // 被动「未被持有」通知：闲置分支跑在 ServerTickEvent.Pre（实体 tick 之前），
        // 因此「本 tick 被持有」时 lastPassiveTick 恰为 gameTime - 1；大于 1 说明持有者已离开。
        if (level != null && level.gameTime - lastPassiveTick > 1) {
            loadedMagics.forEach { magic ->
                if (magic.needInventory) magic.onPassiveIdle()
            }
        }
    }

    private var lastPassiveTick: Long = -1

    /** 保存法杖配置、运行状态和牌堆游标；不改变现有NBT格式。 */
    fun saveTag(): CompoundTag = WandPersistence.save(this)

    companion object {
        /** 服务端固定20刻/秒；仅供旧存档边界换算。 */
        const val TICKS_PER_SECOND = 20.0

        /** 兼容旧单位与空槽位置，恢复顺序统一由持久化边界维护。 */
        fun fromTag(tag: CompoundTag): Wand = WandPersistence.load(tag)
    }
}
