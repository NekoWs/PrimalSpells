package work.nekow.primalspells.magic.core

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.util.LoreEntry
import work.nekow.primalspells.wand.Wand

abstract class Magic {
    abstract val id: String

    lateinit var caster: Entity
    lateinit var wand: Wand

    open var cast: Int = 1

    /** 消耗法术值 **/
    var mana: Double = 0.0
    /** 增加施法延迟 **/
    var delay: Int = 0
    /** 增加充能时间 **/
    var recharge: Int = 0
    /** 本张投射物成功付费时归零整次施放延迟，含触发荷载；与牌序无关。 */
    open val resetsCastDelay: Boolean = false
    var effects = arrayListOf<BaseEffect>()
    var lore = arrayListOf<LoreEntry>()

    /** 被动开关：为 true 时，法杖持于玩家手上（主手/副手）期间每 tick 触发 [onInventoryTick] */
    open var needInventory: Boolean = false

    /**
     * 被动效果钩子：仅在 [needInventory] 为 true、且该法术已编入法杖、法杖位于玩家手上时，
     * 由 [Wand.tick] 每 tick 调用一次（调用前已设置好 [caster]/[wand]）。
     */
    open fun onInventoryTick() { }

    /**
     * 被动法术「未被持有」时的每 tick 回调（默认空实现）。
     *
     * 由 [Wand.tick] 的闲置分支调用：当法杖在**这一 tick** 没有被任何实体驱动过被动
     * （即 [needInventory] 的 [onInventoryTick] 没跑）时，逐 tick 通知一次。
     *
     * 用途：需要「放下后延迟清理 / 播淡出动画」的被动法术（例如 `EnergyShield` 的球壳粒子）。
     * 约定：被重新持有后 [onInventoryTick] 会继续跑，法术应自行复位自己的倒计时。
     */
    open fun onPassiveIdle() { }

    /**
     * 被**从法杖上取下**（或所在槽位被别的法术替换掉）时调用（默认空实现）。
     *
     * 由 [Wand.load] 在重建「已装载法术」列表时，对**本次不再装载**的实例各调一次；
     * [Wand.unload]（法杖内存实例被回收时）也会调用。
     *
     * 用途：清理「只该在装载期间存在」的东西 —— 例如 `EnergyShield` 的球壳粒子组：
     * 取下之后本实例不再出现在 [Wand.loadedMagics] 里，[onInventoryTick] / [onPassiveIdle]
     * **都不会再被调用**，不在这里清理就会把粒子永远留在世界里。
     *
     * 约定：调用时 [wand] 仍指向原法杖；此后本实例不会再收到任何 tick。
     */
    open fun onUnloaded() { }

    /**
     * 【持久化钩子·写】把本法术需要跨存档保留的**运行期状态**写进给定 NBT（默认什么都不写）。
     *
     * 运行期状态 = 只活在内存里的进度/资源，例如 `EnergyShield` 的当前耐久与破盾暂停剩余 tick；
     * 法术的构造参数（伤害、半径…）不算运行期状态，它们由代码/资源决定，不必入库。
     *
     * 调用方：[Wand.saveTag] 对 [Wand.loadedMagics]（innate + magics）逐个调用，
     * 结果按法术 id 存进 [Wand.magicStates] 一并落盘。写空 NBT 的法术不会留下条目。
     *
     * ⚠️ 键是法术 id：同一 id 的重复卡共享同一份状态，见 [Wand.magicStates]。
     *
     * @param tag 调用方新建的空 NBT，往里写自己的键（建议用 snake_case 短键名）
     */
    open fun saveRuntimeState(tag: CompoundTag) { }

    /**
     * 【持久化钩子·读】从给定 NBT 读回运行期状态（默认什么都不做），与 [saveRuntimeState] 成对。
     *
     * ⚠️ **调用时机**：`Wand.fromTag` 读档期间、[Wand.load] 之后立刻执行。
     * 此时 [wand] / [caster] 这些 `lateinit` 字段**还没有被设置**（[status] 也还没有意义），
     * 因此钩子里**只能**读写自己的字段，不得访问 [wand]/[caster]/[status]。
     *
     * @param tag 存档里该法术 id 对应的状态 NBT（缺失时为空 NBT，实现需给默认值兜底）
     */
    open fun loadRuntimeState(tag: CompoundTag) { }

    /**
     * 快捷添加提示
     *
     * @param lore 提示类型
     * @param args 参数
     */
    fun lore(lore: Lore, vararg args: Any) {
        this.lore += LoreEntry(lore, args)
    }

    /**
     * 施法瞬间：按 [EffectPhase.activeOrder] 依次触发每个效果的 `onActive`，最后调用 [onSpell]。
     *
     * 顺序契约（见 [EffectPhase]）：[EffectPhase.SETUP] 先把 `status.pos`/`status.velocity` 初始化，
     * [EffectPhase.MODIFIER] 注入的玩法效果随后在有效状态上改数值。同阶段内按注入顺序执行
     * （`sortedBy` 稳定排序），也就是「抽牌顺序」。
     */
    open fun spell() {
        effects.sortedBy { it.phase.activeOrder }.forEach {
            it.caster = caster
            it.wand = wand
            it.onActive()
        }
        onSpell()
    }

    /**
     * 每 tick：按 [EffectPhase.tickOrder] 依次触发 `onTick`。
     *
     * 顺序契约：玩法效果改速度/伤害 → 轨道导向 → `Move` 扫掠积分 → 位置覆盖 → 粒子表现。
     */
    open fun tick() {
        effects.sortedBy { it.phase.tickOrder }.forEach { it.onTick() }
    }

    /** 死亡结算：按注入顺序触发 `onDie`（顺序对清理无影响，保持插入顺序）。 */
    fun die() {
        effects.forEach { it.onDie() }
        onDie()
    }

    /** 寿命耗尽的当刻通知，不与提前碰撞死亡混淆。 */
    fun expire() { effects.forEach { it.onExpire() } }

    open fun onSpell() { }
    open fun onDie() { }

    open fun initLore() {
        val base = arrayListOf<LoreEntry>()
        base += LoreEntry(Lore.DESCRIPTION, emptyArray())
        base += LoreEntry(Lore.CATEGORY, arrayOf(net.minecraft.network.chat.Component.translatable("category.primalspells." + when {
            needInventory -> "passive"
            this is DrawAction -> "other"
            this is Modifier -> "modifier"
            else -> "projectile"
        })))
        base += LoreEntry(Lore.BR, emptyArray())
        base += LoreEntry(Lore.MANA, arrayOf(Lore.formatDouble(mana)))
        if (delay != 0) base += LoreEntry(Lore.DELAY, arrayOf(delay.toString()))
        if (recharge != 0) base += LoreEntry(Lore.RECHARGE, arrayOf(recharge.toString()))
        lore.addAll(0, base)
    }

    /**
     * 新建一枚「本次施法专用」的法术实例 —— **这是新建，不是字段拷贝**。
     *
     * 为什么必须新建：[Wand.spell] 会把修饰器的效果注入到**实例自身**的 [effects]，
     * 复用同一实例会让上一发注入的效果残留、并随施法次数不断叠加。
     * 因此这里从注册表工厂重建一个默认状态实例；运行期状态（当前速度、被修饰器改过的伤害等）
     * **不会**被继承——任何依赖「克隆时继承父投射物状态」的逻辑都是错的。
     *
     * @throws IllegalStateException [id] 未注册时抛出（比 `!!` 的 NPE 更容易定位）
     */
    open fun clone(): Magic = SpellRegistry.create(id)
        ?: throw IllegalStateException("法术 id 未注册，无法创建施法实例：$id")
}
