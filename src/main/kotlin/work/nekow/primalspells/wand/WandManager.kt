package work.nekow.primalspells.wand

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.util.Lore
import java.util.*

/**
 * 法杖运行时实例表（[wands]）＝ 持有者记录（[holders]）＋ 内存回收 ＋ 按需懒加载。
 *
 * ## 为什么需要「持有者记录」
 *
 * 法杖的权威状态（法力/牌堆/冷却/法术运行期状态）活在服务端内存的 [Wand] 实例里，
 * 而「谁拿着它」过去只能靠 [work.nekow.primalspells.item.WandItem] 的
 * 「玩家 UUID → 最近一次施法的法杖」表 + 登录时扫背包推出来 —— 那套**只能覆盖玩家**。
 * 之后要让**敌对生物也能用法杖**，而生物不走 `inventoryTick` 的被动驱动路径，
 * 所以这里改成以**实体**为粒度的记录：`wandId → (持有实体 UUID, 最近一次确认时间)`。
 *
 * 记录入口两处（玩家与生物各覆盖一条）：
 * 1. [work.nekow.primalspells.item.WandItem.inventoryTick]：法杖在**任何实体**的背包里被 tick 时记 owner
 *    ——「玩家持有」走这条，天然覆盖背包/护甲/副手/主手全部槽位；
 * 2. [Wand.spell]：任何实体主动施法时记 caster ——**手持法杖的生物**只走这条
 *    （生物拿杖不会进入我们的被动 tick 路径，但施法一定会经过 [Wand.spell]）。
 *
 * ## 回收判定口径与已知限制
 *
 * [tick] 每 [RECYCLE_INTERVAL_TICKS] tick 做一次回收扫描（**不是**每 tick 全量扫，扫背包不便宜）：
 *
 * 1. **硬判定（在线玩家）**：遍历 `ServerPlayer.inventory`（含护甲/副手，共 41 格）与主手/副手物品，
 *    凡带 [ModItems.WAND_ID] 组件的物品栈都算「被该玩家持有」——这一路是权威。
 * 2. **宽松判定（非玩家实体）**：用 [holders] 里的时间戳——最近 [NON_PLAYER_HOLD_TTL_TICKS] tick 内
 *    被确认过（[Wand.spell] 施法，或第 1 条入口的 `inventoryTick`），就仍当作被持有。
 *
 * 为什么不给非玩家也做硬判定：服务端没有「按物品反查实体」的索引，要扫全场的实体存储，
 * 代价随实体数线性增长。**实际覆盖情况**：法杖只要位于某个实体的 `Inventory`
 * 或装备槽里（玩家背包，以及生物的主手/副手——分别由 `Inventory.tick` 与 `EntityEquipment.tick`
 * 驱动），就会经 [work.nekow.primalspells.item.WandItem.inventoryTick] 逐 tick 刷新时间戳，
 * 因此「生物手持法杖」事实上也能被判定到。
 *
 * 误判的**唯一方向**是「其实有人持有，却被判成无人持有」，触发条件是
 * ①物品既不在任何实体存储里、又②超过 [NON_PLAYER_HOLD_TTL_TICKS] 没被确认过：
 * 例如玩家把法杖拖在容器界面的光标上、或将来某种「法杖不进实体存储、只由 AI 逻辑持有」的形态。
 * 后果是可控的：内存实例被回收，下次被使用/再次进入某个实体存储时按 [loadOrNull] 懒加载重建
 * （状态从存档还原，不丢数据，只是多一次读档）；
 * 反方向（把无人持有的判成有人持有）最多让实例多留一个 TTL 窗口，不影响正确性。
 *
 * 后续可扩展点：登记「手持法杖的生物」轻量列表（在实体换手/背包 tick 事件里维护），
 * 或给带 [ModItems.WAND_ID] 的物品建索引，即可把非玩家判定也升级成硬判定。
 *
 * ## 不变量：存档永不删
 *
 * 回收**只针对内存实例**：先 [writeBack] 把 [Wand.saveTag] 写回 [WandSavedData]，再 `wands.remove`。
 * 法杖可能正躺在箱子里，存档条目必须原样保留；物品再次被持有/使用时，
 * [get]（及其两参重载）会按需从存档重建实例。因此
 * 「持有 → 使用 → 放下/丢掉 → 再捡起」之后法力/牌堆/冷却/法术运行期状态都不丢。
 */
object WandManager {
    /** 法杖运行时实例表（wandId → 权威状态）。条目可被回收，所以它**不是**「法杖是否存在」的判据。 */
    val wands = hashMapOf<WandID, Wand>()

    /** 回收扫描周期（tick）：每 100 tick 一次。 */
    private const val RECYCLE_INTERVAL_TICKS = 100L

    /** 非玩家实体的宽松持有窗口（tick）：这段时间内施法用过就仍当作被持有。 */
    private const val NON_PLAYER_HOLD_TTL_TICKS = 200L

    /**
     * 持有者记录：wandId → 最近一次被确认持有/使用它的实体。
     *
     * 「一根法杖同一时刻只有一个持有者」是物品唯一性的假设：一个 `wandId` 对应一份物品栈，
     * 只能躺在一个背包/一只手上（创造模式复制出的副本会互相覆盖，取最后写入者）。
     */
    private val holders = hashMapOf<WandID, Holder>()

    /**
     * 一条持有者记录。
     *
     * @property entityId     最近一次被确认持有/使用该法杖的实体 UUID
     * @property lastSeenTick 那次确认发生时的 `level.gameTime`（各维度共用同一时间基准：
     *                        非主世界的 `gameTime` 由主世界存档代理）
     */
    data class Holder(var entityId: UUID, var lastSeenTick: Long)

    /** 服务端主世界引用：懒加载读档与回收写回都要经 [WandSavedData] 落盘，[load] 时取得。 */
    private var overworld: ServerLevel? = null

    /**
     * 标记「某实体持有/使用了某法杖」，是 [holders] 的唯一写入口。
     *
     * 由 [Wand.spell]（任何实体主动施法）与 [work.nekow.primalspells.item.WandItem.inventoryTick]
     * （法杖在实体背包里被 tick）调用；同一次持有会被反复刷新，只保留最近一次确认。
     *
     * @param wandId   法杖 id
     * @param holderId 持有者/施法者的实体 UUID
     * @param gameTime 本次确认的游戏时间（tick），供回收的宽松判定使用
     */
    fun markHeld(wandId: WandID, holderId: UUID, gameTime: Long) {
        val record = holders[wandId]
        if (record == null) {
            holders[wandId] = Holder(holderId, gameTime)
        } else {
            record.entityId = holderId
            record.lastSeenTick = gameTime
        }
    }

    /**
     * 查询最近一次被确认持有/使用该法杖的实体。
     *
     * 注意这是「最近一次确认」，不代表此刻一定还持有（玩家可能已经把它放进箱子）；
     * 权威的「是否仍被持有」判定在 [recycleUnheld] 里（以在线玩家背包为准）。
     *
     * @return 持有实体 UUID；从未被确认过则返回 null
     */
    fun holderOf(wandId: WandID): UUID? = holders[wandId]?.entityId

    /**
     * 查询最近一次确认持有的游戏时间（tick）。
     *
     * @return 该次确认的 `level.gameTime`；从未被确认过则返回 null
     */
    fun lastHeldTick(wandId: WandID): Long? = holders[wandId]?.lastSeenTick

    fun save(level: ServerLevel) {
        val data = WandSavedData.getOrCreate(level)
        wands.forEach { (id, wand) ->
            data.wandTags[id.wandId] = wand.saveTag()
        }
        data.isDirty = true
    }

    fun load(level: ServerLevel) {
        val data = WandSavedData.getOrCreate(level)
        // 先清空：整合服务器在同一 JVM 内换世界/重载会再次触发 ServerStartedEvent，
        // 不清空会把上一个世界的法杖残留下来。
        wands.clear()
        // 持有者记录同理：上一个世界的实体 UUID 与 gameTime 对新世界没有意义。
        holders.clear()
        overworld = level
        data.wandTags.forEach { (id, tag) ->
            wands[WandID(id)] = Wand.fromTag(tag)
        }
    }

    /**
     * 每 tick 驱动法杖运行时状态，并按周期回收无人持有的实例。
     *
     * @param level 服务端主世界（提供 `gameTime` 与 [WandSavedData]）
     */
    fun tick(level: ServerLevel) {
        // 顺带把 level 传给闲置分支：被动法术需要它来判断「本 tick 有没有被持有」（见 Wand.tick）
        wands.forEach { (_, wand) -> wand.tick(level = level) }
        // 回收按周期做：扫全部在线玩家背包不便宜，100 tick 一次足够。
        if (level.gameTime % RECYCLE_INTERVAL_TICKS == 0L) recycleUnheld(level)
    }

    /**
     * 回收「没有任何实体持有」的法杖运行时实例。
     *
     * 判定顺序：先看在线玩家背包（硬判定），再看 [holders] 里非玩家的宽松时间窗；
     * 两者都不成立才回收。回收前一律 [writeBack]，因此存档永远保留完整状态。
     */
    private fun recycleUnheld(level: ServerLevel) {
        val now = level.gameTime
        val heldByPlayers = collectPlayerHeldWands(level)
        val iterator = wands.entries.iterator()
        while (iterator.hasNext()) {
            val (id, wand) = iterator.next()
            val playerId = heldByPlayers[id]
            if (playerId != null) {
                // 扫描是权威判定：顺手把持有者记录刷新成当前持有者。
                markHeld(id, playerId, now)
                continue
            }
            val record = holders[id]
            if (record != null && now - record.lastSeenTick <= NON_PLAYER_HOLD_TTL_TICKS) continue
            // 先落盘再丢实例：存档是唯一权威副本，回收只影响内存表。
            writeBack(level, id, wand)
            wand.unload() // 通知法术清理外部资源（如护盾球壳粒子组），避免留下孤儿粒子
            holders.remove(id)
            iterator.remove()
        }
        // 兜底：清掉指向「内存里已不存在」的法杖的陈旧记录，避免这张表只增不减。
        holders.keys.retainAll(wands.keys)
    }

    /**
     * 扫描所有在线玩家的背包（含护甲/副手槽）与主手/副手物品，收集「谁拿着哪根法杖」。
     *
     * 判据是物品栈上的 [ModItems.WAND_ID] 组件（与 [work.nekow.primalspells.item.WandItem] 一致），
     * 因此这里不依赖 `WandItem` 类型本身。
     *
     * @return wandId → 持有者 UUID（同一根法杖被多人持有只可能来自创造模式复制品，取遍历到的最后一个）
     */
    private fun collectPlayerHeldWands(level: ServerLevel): Map<WandID, UUID> {
        val result = hashMapOf<WandID, UUID>()
        for (player in level.server.playerList.players) {
            recordIfWand(result, player.mainHandItem, player.uuid)
            recordIfWand(result, player.offhandItem, player.uuid)
            val inventory = player.inventory
            for (i in 0 until inventory.containerSize) {
                recordIfWand(result, inventory.getItem(i), player.uuid)
            }
        }
        return result
    }

    /** 物品栈带 [ModItems.WAND_ID] 组件时登记为「该玩家持有」。 */
    private fun recordIfWand(result: MutableMap<WandID, UUID>, stack: ItemStack, playerId: UUID) {
        val wandId = stack.get(ModItems.WAND_ID) ?: return
        result[wandId] = playerId
    }

    /**
     * 把内存实例的当前状态写回世界存档（回收前调用）。
     *
     * 存档条目是「这根法杖还存在」的唯一权威副本，[recycleUnheld] 只删内存实例、不删条目。
     */
    private fun writeBack(level: ServerLevel, id: WandID, wand: Wand) {
        val data = WandSavedData.getOrCreate(level)
        data.wandTags[id.wandId] = wand.saveTag()
        data.isDirty = true
    }

    /**
     * 取运行时实例：内存里有就直接返回；没有（从未加载，或已被回收）就按需从 [WandSavedData] 重建。
     *
     * 这就是**懒加载**：被回收的法杖在物品再次被持有/使用时自动复活；
     * 存档里也没有该 id 时返回 null，由调用方决定兜底行为。
     */
    private fun loadOrNull(key: WandID): Wand? {
        wands[key]?.let { return it }
        val level = overworld ?: return null
        val tag = WandSavedData.getOrCreate(level).wandTags[key.wandId] ?: return null
        return Wand.fromTag(tag).also { wands[key] = it }
    }

    fun createWand(stack: ItemStack, random: RandomSource = RandomSource.create()): WandID {
        val existing = stack.get(ModItems.WAND_ID)
        if (existing != null) {
            // 已被回收的法杖在这里也走懒加载：直接造空白实例会把存档里的牌堆/法力盖掉。
            if (existing !in wands) wands[existing] = loadOrNull(existing) ?: Wand(existing.wandId)
            return existing
        }
        val wandId = WandID(UUID.randomUUID().toString())
        stack.set(ModItems.WAND_ID, wandId)
        val wand = Wand(wandId.wandId)
        // 等级 + 变体 → 掷出一组数值（只有抽牌数是随机的，见 [WandStats.roll]）。
        // 认不出等级的物品（例如代码里手搓的 `Wand("...")`）保持调试杖默认值。
        val tier = ModItems.wandTierOf(stack.item)
        if (tier != null) {
            val variant = if (stack.getOrDefault(ModItems.WAND_CHARGED, false)) {
                WandVariant.CHARGED
            } else {
                WandVariant.NORMAL
            }
            val stats = WandStats.roll(tier, variant, random)
            wand.applyStats(stats)
            writeStatsLore(stack, stats)
        }
        wands[wandId] = wand
        return wandId
    }

    /**
     * 把**这一根杖掷出的数值**写进物品 LORE（法力上限/回蓝/双延迟/槽位/抽牌）。
     *
     * 为什么要写在物品上：数值里有**掷点**（抽牌数），不能只靠等级名表达；
     * 而物品栏/tooltip 是最直接的展示位。实时值（当前法力/冷却）仍由 HUD 走
     * `SyncWandStatsPayload` 显示，两者不冲突。
     */
    private fun writeStatsLore(stack: ItemStack, stats: WandStats) {
        stack.set(
            DataComponents.LORE,
            ItemLore(
                listOf(
                    Component.translatable("wand.primalspells.stat.mana", Lore.formatDouble(stats.maxMana)),
                    Component.translatable("wand.primalspells.stat.regen", Lore.formatDouble(stats.manaPerSecond)),
                    Component.translatable("wand.primalspells.stat.delay", Lore.formatDouble(stats.delayMs)),
                    Component.translatable("wand.primalspells.stat.size", stats.size.toString()),
                    Component.translatable("wand.primalspells.stat.cast", stats.cast.toString()),
                )
            )
        )
    }

    fun remove(key: WandID) = wands.remove(key)

    /**
     * 取法杖运行时实例（内存没有时从存档懒加载）。
     *
     * @throws NullPointerException 内存与存档里都找不到该 id 时抛出
     */
    operator fun get(key: WandID) = loadOrNull(key) ?: throw NullPointerException("Key ${key.wandId} is not exists!")

    /**
     * 取法杖运行时实例（内存没有时从存档懒加载）。
     *
     * @param default 内存与存档里都找不到时返回的对象（默认 null）
     */
    operator fun get(key: WandID, default: Wand? = null) = loadOrNull(key) ?: default

    operator fun set(key: WandID, wand: Wand) = wands.set(key, wand)

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        load(event.server.overworld())
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        save(event.server.overworld())
        // 世界要关了：主世界引用随之失效（服务器正在停机，此后的懒加载只能返回 default）。
        overworld = null
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) {
        tick(event.server.overworld())
    }
}
