package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.lifecycle.Explosion
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.util.PD_IMMORTAL
import work.nekow.primalspells.util.Polyhedra
import work.nekow.primalspells.util.pdParticles
import work.nekow.primalspells.wand.Wand
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 能量护盾（修饰器）：**常驻展开、平时不可见、受击才现形**的玻璃护盾。
 *
 * 定价口径全文见 [docs/design/combat-pricing.md] §3–§4；这里只记实现口径。
 *
 * ## 玩法（第 26 轮重做：耐久 → 强度 vs 抗性 + 法力）
 *
 * - 只要法杖上装着护盾卡，护盾就是**展开的**：没有耐久、没有破盾冻结、没有回复，
 *   因此也**没有持续状态**（HUD 条已随之删除）。
 * - 每次「格挡真的发生」按强度结算，**两道关**（第 39 轮起）：
 *   1. **窗口预算**：`R = 基础抗性 + 每张卡抗性`，**最近 1 秒（[BUDGET_WINDOW_TICKS] tick）内
 *      已经承担的强度 + 本次 > R × [RESISTANCE_BUDGET_RATIO]** 就直接挡不住
 *      —— 用户口径「护盾防的是 DPS 而不是单次伤害，抗性改为在一段时间内可以承担的强度大小」；
 *   2. **法力**：预算内才轮到 `强度 ≤ R` 只付「基础反弹 MP」、`强度 > R` 付
 *      `基础反弹 + (强度 - R) × 每点额外`（超出部分封顶 2 × R，第 35 轮）。
 * - **挡不住 = 破口**（预算爆掉或法力不够）：这一发照常命中持有者，破口区在 [breachTicks] 内
 *   不挡任何东西，并常驻显示淡红色轮廓。破口区**就是碎裂的那一片**：
 *   投射物是「受击面板 + 相邻一圈」（**定向破口**），爆炸则是**所有被波及到的面板**（见下）。
 * - **爆炸按影响范围选片**（第 39 轮）：面心到爆心 ≤ 爆炸半径的面板**全部**入选 ——
 *   半径 6 的核弹摆在壳外 3 格时 32 片全被波及（于是整罩碎裂）；抵扣成功时这些片**全亮一下玻璃**，
 *   挡不住时它们**全碎**（朝爆点的片白热、背对爆点的片暗一档）。
 * - **只有「从外侧穿过壳面」才算格挡**：判据是这一 tick 的位移线段与**球壳**求交、且起点在壳外。
 *   因此在壳内生成的法术（贴脸施法、壳内释放的荷载）**不会被误判成挡下**，
 *   单 tick 位移再大（`laser` 是 5 格）也不会漏过——两条都是既有缺陷，第 26 轮一并修掉。
 * - **穿透（`maxHitTargets < 0`）完全无视护盾**：不结算、不出玻璃、不出破口。
 * - **爆炸**（独立爆炸点，不走投射物）：中心在壳内侧 → 护盾不触发（壳挡不住已经发生在自己内部的事）；
 *   在壳外侧 → `强度 = 衰减后伤害 × 半径`，抵扣成功则**只替持有者吃**（伤害与击退），
 *   世界其他部分照常。入口是 [absorbExplosion]（由 `ShieldGuard` 在 `Explosion` 结算前调用）。
 * - 摔落/中毒/虚空这类**没有投射物**的伤害护盾不响应 —— 既有口径，不是缺口。
 *
 * ## 视觉（延续第 23 轮「受击处展开玻璃」，第 26 轮换掉整罩两段演出）
 *
 * - **格挡成功**：受击方向那一块面板展开一块**白玻璃**（亮边 + 极淡填充，约 0.35 秒淡出）；
 * - **打穿**：破口区那一片**碎裂**（向外飘散 + 淡出），随后是持续整个破口期的**淡红轮廓**；
 * - 旧的「整罩一闪而碎」「护盾恢复闪一下」两段演出随耐久模型一起删除。
 *
 * 玻璃**画在生成处就不再动**（第 36 轮口径：判定跟人、碎片盖章），淡出结束由 PD 自行销毁
 * （`fadeOut(removeAfter = true)`），所以本实例此后不再被 tick（法杖被收起/回收）也不留孤儿粒子。
 *
 * ## PD 分组（第 39 轮，open.md §1.9.2）
 *
 * PD 的组是 arm / 演出的单位：一块面板一个组 = 一次爆炸打 32 条 `armed` 日志。所以 [spawnGlassPanels]
 * **按飘散位移分组** —— 位移相同的面板共用一个组（格挡玻璃、破口轮廓、爆炸碎裂都只占 1 个组），
 * 只有「投射物打穿、每片沿自己法线向外」那种一片一方向，才不得不一片一组。
 */
class EnergyShield : Modifier() {
    override val id = "energy_shield"

    /** 基础抗性：强度不超过它的攻击只付基础反弹 MP */
    var baseResistance: Double = 2.0

    /** 每张护盾卡额外提供的抗性（多张卡 = 更能扛连续低强度攻击） */
    var resistancePerCard: Double = 2.0

    /** 基础反弹消耗（MP）：每一次成功格挡都要付 */
    var baseBounceCost: Double = 5.0

    /** 强度超出抗性后，每一点额外消耗的 MP */
    var extraCostPerPoint: Double = 5.0

    /** 打穿后的破口持续 tick（60 = 3 秒） */
    var breachTicks: Int = 60

    /**
     * 破口的面板下标（空 = 没有破口）。
     *
     * 只在内存里：破口是 3 秒的瞬时状态，不值得进存档（也不该跨重登保留）。
     */
    private val breachPanels = HashSet<Int>()

    /** 破口到期时间（`gameTime`）；到期后在下次结算时清空 [breachPanels] */
    private var breachUntil = 0L

    /**
     * 曾经**在壳外出现过**的投射物（`System.identityHashCode` → 最后一次在壳外出现的 gameTime）。
     *
     * 键用身份哈希：`Magic` 只有法术名（`id: String`）没有实例级 UUID，同一种法术会有多份实例。
     * 代价是对象被回收后哈希可能重复（概率极低），最坏情况是"某发生成在壳里的法术被误挡一次"。
     *
     * 第 38 轮加：生存模式实测「持盾朝攻击者走过去会被打中，且法力不掉、护盾不展示」——
     * 因为球心跟着持有者走，**壳会"迎着"投射物扩张**，弹会在某一 tick 直接落到壳内，
     * 于是 [shellEntry] 的前提「起点在壳外」不成立 → 整发不挡。
     * 有这张表就能区分两种情况：**壳追上来的弹**（曾在壳外）照挡；
     * **本来就生成在壳里的法术**（从没在壳外出现过）仍然不挡（贴脸施法漏过去是设计）。
     */
    private val seenOutside = HashMap<Int, Long>()

    /** 表项过期时长（tick）：20 秒没再在壳外出现就丢掉，避免只增不减。 */
    private val seenOutsideTtl = 400L

    /**
     * 当前是否处于破口状态。
     *
     * 破口是**瞬时**状态（[breachTicks] 后自动消失），不进存档；这个只读口子给测试/调试用
     * （录制流水线靠它断言「法力不足 → 打穿 + 破口」这一段真的发生了）。
     */
    val breached: Boolean get() = breachPanels.isNotEmpty()

    // ---------------------------------------------------------------- DPS 预算（第 39 轮）

    /** 滚动记录里的一项：某个 gameTime 承担掉的强度。 */
    private class UsedStrength(val gameTime: Long, val strength: Double)

    /**
     * 最近 [BUDGET_WINDOW_TICKS] tick 内**已经承担**的强度（只在内存：1 秒的瞬时状态，不进存档）。
     *
     * 只由**主卡**记账（只有主卡结算格挡），所以「多张卡」的影响全部走 [resistance]，不在这里。
     */
    private val recentStrength = ArrayDeque<UsedStrength>()

    /**
     * 本杖**一个窗口内能承担的强度总量** = `抗性 × [RESISTANCE_BUDGET_RATIO]`。
     *
     * 这是「抗性」的第二个身份：既是**单发阈值**（超出它才加价，见 [bounceCost]），
     * 也是**窗口预算**（超出它直接挡不住）。为什么取 2 倍见 [RESISTANCE_BUDGET_RATIO]。
     *
     * 公开只读：录制流水线靠它断言「预算而非法力」那一段（同 [breached]）。
     */
    fun strengthBudget(): Double = resistance() * RESISTANCE_BUDGET_RATIO

    /** 窗口内已承担的强度总量（顺带把到期项丢掉，所以这个表不会只增不减）。 */
    private fun usedStrength(gameTime: Long): Double {
        val cutoff = gameTime - BUDGET_WINDOW_TICKS
        while (recentStrength.isNotEmpty() && recentStrength.first().gameTime <= cutoff) {
            recentStrength.removeFirst()
        }
        var sum = 0.0
        for (used in recentStrength) sum += used.strength
        return sum
    }

    /**
     * 一次格挡的**资金结算**：先查窗口预算、再查法力 —— 两个入口（投射物 / 爆炸）共用。
     *
     * 顺序与副作用都是刻意的（第 39 轮用户口径「护盾防的是 DPS 而不是单次伤害」）：
     * 1. `已承担 + 本次 > 预算` → 挡不住，**并把窗口记账清零**（这一发连法力都不花）；
     * 2. 预算内再看法力：付不起 [bounceCost] 同样挡不住（既有口径：法力不够 = 挡不住）；
     * 3. 都过了才记账 + 扣法力。
     *
     * 破口期间不需要在这里排除：破口一开就 [breachTicks]（60 tick = 3 秒），
     * 那段时间整个格挡判定根本走不到这里。
     *
     * @return true = 这一发挡得住（已记账、已扣法力）
     */
    private fun payForBlock(gameTime: Long, strength: Double): Boolean {
        if (usedStrength(gameTime) + strength > strengthBudget()) {
            recentStrength.clear()
            return false
        }
        if (!wand.spendMana(bounceCost(strength))) return false
        recentStrength.addLast(UsedStrength(gameTime, strength))
        return true
    }

    /**
     * 面板几何（截角二十面体：12 五边形 + 20 六边形，坐标**相对球心**、长度 = [SHELL_RADIUS]）。
     *
     * `sortedBy { center.y }`（由下往上）保留着：粒子生成顺序稳定，观感一致。
     */
    private val shieldPanels: List<Polyhedra.Panel> by lazy {
        Polyhedra.truncatedIcosahedron(SHELL_RADIUS, SHELL_POINTS_PER_EDGE).sortedBy { it.center.y }
    }

    /** 面板邻接表（共享一条棱 = 相邻），与 [shieldPanels] 同序；破口 = 受击面板 + 它的一圈邻居。 */
    private val panelNeighbours: List<Set<Int>> by lazy {
        Polyhedra.adjacency(shieldPanels, SHELL_POINTS_PER_EDGE)
    }

    override var needInventory: Boolean = true

    /**
     * 本杖上的**主卡**：`loadedMagics` 里第一张护盾卡。
     *
     * 只有主卡结算格挡 —— 否则 N 张卡会对同一发投射物各扣一次 MP、各出一块玻璃。
     * 主卡的顺序是确定的（`innate` + 槽位顺序），所以不需要额外状态：主卡被取下后，
     * 下一张自动接任（它下一 tick 就会照常结算）。
     */
    private fun primaryShield(): EnergyShield? =
        wand.loadedMagics.firstOrNull { it is EnergyShield } as? EnergyShield

    /** 本杖当前的抗性 = [baseResistance] + [resistancePerCard] × 护盾卡数量。 */
    private fun resistance(): Double =
        baseResistance + resistancePerCard * wand.loadedMagics.count { it is EnergyShield }

    /**
     * 这次格挡要花的 MP：抗性以内只付基础反弹，超出部分每点加价。
     *
     * **第 35 轮加封顶**（用户口径：「大于阈值后不是扣 MP 了吗，改成**最多额外扣阈值的 2 倍**的 MP」，
     * 追问「阈值指什么」→「**就是抗性的两倍**」）：
     * 超出抗性的那部分**最多按 `2 × 抗性` 计**，即额外消耗 ≤ `2 × 抗性 × extraCostPerPoint`。
     *
     * 为什么加：原来按点数线性加价，一颗大爆（强度 144 / 抗性 4）要 **705 MP** ——
     * 于是手感是"有蓝就一直不会破、没蓝就一直破"（能不能付得起的悬崖）。
     * 封顶后同样的核弹只要 **45 MP**（基础 5 + 8 点 × 5），强度高低仍分档（火球 10 / 核弹 45），
     * 但 T1 的 80 MP 也扛得住一发，破不破取决于**连续挨打**而不是单发的绝对值。
     */
    private fun bounceCost(strength: Double): Double {
        val resisted = resistance()
        val over = (strength - resisted).coerceIn(0.0, resisted * MAX_EXTRA_RESISTANCE_RATIO)
        return baseBounceCost + over * extraCostPerPoint
    }

    /**
     * 每 tick 维护护盾：只做主卡的格挡结算（见 [primaryShield]）。
     *
     * 判定顺序刻意是「先算强度、再看预算与法力」（[payForBlock]）——预算爆掉或付不起都不是"少扣一点"，
     * 而是这一发**整个挡不住**（用户口径：MP 不足则无法抵挡；第 39 轮起再加一条"窗口内承担不下"）。
     */
    override fun onInventoryTick() {
        if (primaryShield() !== this) return

        val holder = caster
        if (!holder.isAlive) return
        val level = holder.level() as? ServerLevel ?: return
        expireBreach(level.gameTime)

        // 剪枝：场上没有投射物时不必做任何遍历
        if (MagicManager.projectiles.isEmpty()) return

        val shellCenter = shellCenter(holder)
        for (p in MagicManager.projectiles) {
            if (!p.alive) continue
            if (p.caster === holder) continue          // 自己施放的法术不弹（出生在面前，否则出生即被弹回）
            if (p.maxHitTargets < 0) continue          // 穿透：完全无视护盾
            tryBlock(level, holder, shellCenter, p)
        }
    }

    /**
     * **命中兜底**：投射物在同一 tick 内就穿过壳面并贴到持有者身上时，由 `Hurt` 在结算伤害前问这一句。
     *
     * 为什么需要它：投射物的「移动 + 命中结算 + 到寿死亡」全在 `MagicManager.tick`（`ServerTickEvent.Pre`）里做完，
     * 而护盾的被动扫场跑在**同一 tick 更晚**的实体 tick 里。于是高速法术（`laser` 单 tick 位移 5 格、`maxAge = 1`）
     * 在扫场看到它之前就已经打完了、而且已经被 `maxAge` 收走 —— 扫场再准也拦不到。
     * 这里的规则与 [tryBlock] **完全同一套**（同一条穿越判定、同一套强度/MP/破口），
     * 只是把评估时机挪到「真正命中的那一刻」。
     *
     * @return true = 已格挡（调用方跳过伤害），投射物已被反射
     */
    fun absorbProjectile(level: ServerLevel, wand: Wand, holder: Entity, projectile: Projectile): Boolean {
        if (wand.loadedMagics.firstOrNull { it is EnergyShield } !== this) return false
        if (!holder.isAlive) return false
        if (projectile.caster === holder) return false
        if (projectile.maxHitTargets < 0) return false   // 穿透不吃护盾这一套
        this.wand = wand
        expireBreach(level.gameTime)
        return tryBlock(level, holder, shellCenter(holder), projectile)
    }

    /**
     * 对**一枚投射物**做一次格挡判定 —— 两个入口共用：tick 末尾的全场扫（[onInventoryTick]）
     * 与命中兜底（[absorbProjectile]）。
     *
     * @return true = 这一发被格挡（已扣 MP、已反射、已出玻璃板）；false = 不归护盾管，或挡不住（已开破口）
     */
    private fun tryBlock(level: ServerLevel, holder: Entity, shellCenter: Vec3, p: Projectile): Boolean {
        if (p.hitRadius <= 0.0) return false          // 不碰撞实体的法术（爆炸点等）走爆炸那条路
        val pos = p.status.pos
        // 本引擎的 `velocity` 就是「每 tick 位移」（`Move` 用它做位置积分），所以上一 tick 的位置
        // 恰好是 `pos - velocity`。用它拼出这一 tick 的位移线段 —— 不做扫掠就会漏掉单 tick
        // 位移超过壳厚度的法术（laser 5 格/tick）。
        val prev = Vector3f(pos).sub(p.status.velocity)

        // 先登记「这一 tick 它在壳外」——下一步就可能被壳"追上"而落到壳内
        val ex = pos.x - shellCenter.x
        val ey = pos.y - shellCenter.y
        val ez = pos.z - shellCenter.z
        if (ex * ex + ey * ey + ez * ez > SHELL_RADIUS * SHELL_RADIUS) {
            seenOutside[System.identityHashCode(p)] = level.gameTime
        }
        val stale = seenOutside.entries.iterator()
        while (stale.hasNext()) {
            if (level.gameTime - stale.next().value > seenOutsideTtl) stale.remove()
        }

        val seen = System.identityHashCode(p) in seenOutside
        val hitPoint = shellEntry(prev, pos, shellCenter, allowInsideStart = seen) ?: return false
        val normal = Vector3f(hitPoint).sub(Vector3f(shellCenter.x.toFloat(), shellCenter.y.toFloat(), shellCenter.z.toFloat()))
        if (normal.lengthSquared() < 1e-6f) return false
        normal.normalize()
        if (p.status.velocity.dot(normal) >= 0) return false   // 正在远离壳面：不是「打进来」

        val panelIndex = nearestPanelIndex(Vec3(normal.x.toDouble(), normal.y.toDouble(), normal.z.toDouble())) ?: return false
        if (panelIndex in breachPanels) return false           // 破口方向：直接放行（连 MP 都不花）

        val strength = strengthOf(p)
        if (!payForBlock(level.gameTime, strength)) {
            // 挡不住（窗口预算爆掉 / 法力不够）：投射物照常飞（一行都不改），护盾这边付出破口
            openBreach(level, holder, breachArea(panelIndex))
            return false
        }

        // 挡下了：以壳面法线反射，并把投射物推到壳外 0.05 格（下一 tick 必然离开判定面）
        val speed = p.status.velocity.length()
        p.status.velocity.normalize().reflect(normal).mul(speed)
        p.status.pos = Vector3f(normal).mul(0.05f).add(hitPoint)
        spawnGlassPanels(
            level, holder, listOf(panelIndex),
            edgeColor = GLASS_EDGE_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = HIT_HOLD_TICKS, fadeTicks = HIT_FADE_TICKS,
        )
        return true
    }

    /**
     * **替持有者吃掉一次爆炸**（`ShieldGuard` 在 `Explosion` 结算伤害前调用）。
     *
     * @param falloffDamage 这颗爆炸**在持有者处**的衰减后伤害（`Explosion` 已算好）
     * @return true = 已抵扣（调用方跳过伤害与击退）；false = 不触发 / 挡不住
     */
    fun absorbExplosion(
        level: ServerLevel,
        wand: Wand,
        holder: Entity,
        center: Vec3,
        radius: Float,
        falloffDamage: Double,
    ): Boolean {
        if (wand.loadedMagics.firstOrNull { it is EnergyShield } !== this) return false
        if (!holder.isAlive) return false

        val shellCenter = shellCenter(holder)
        // 中心在壳内侧 → 护盾不触发：壳挡不住已经发生在自己内部的事（贴脸爆炸照样吃满）
        if (center.distanceTo(shellCenter) < SHELL_RADIUS) return false

        expireBreach(level.gameTime)
        // 这件事发生在**这把法杖**上：粒子管理器与抗性都取它的
        // （爆炸路径不保证 `this.wand` 已被 tick 写过）
        this.wand = wand
        val toBlast = center.subtract(shellCenter)
        val axisPanel = nearestPanelIndex(toBlast) ?: return false
        if (axisPanel in breachPanels) return false    // 破口方向：直接放行（连 MP 都不花）

        // 强度 = 衰减后伤害 × 半径（见 design/combat-pricing.md §3）
        val strength = falloffDamage * radius
        val panels = blastPanels(toBlast, radius)
        if (!payForBlock(level.gameTime, strength)) {
            openBlastBreach(level, holder, panels, toBlast)
            return false
        }
        // 挡下了：波及到的片**全部出玻璃**（这就是「护盾遇到爆炸」看得见的那一下）
        spawnGlassPanels(
            level, holder, panels.all,
            edgeColor = GLASS_EDGE_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = HIT_HOLD_TICKS, fadeTicks = HIT_FADE_TICKS,
        )
        return true
    }

    // ---------------------------------------------------------------- 强度与几何

    /**
     * 这一发投射物的**强度** = 它实际能打出的东西（不是把 `MagicStatus` 的字段盲加）。
     *
     * - 直接伤害：**带 `Hurt` 效果**时才计 `status.damage`。像 `cluster_bomb` 那种碎片是
     *   `clone()` 出来的（保留宿主法术的 `status.damage`）但效果被换成 `Explosion + 加速度`，
     *   不判 `Hurt` 就会给它们记一笔根本没打出来的伤害；
     * - 爆炸项：**带 `Explosion` 效果**时才计 `爆炸伤害 × 半径`。`MagicStatus` 的爆炸参数
     *   每个投射物身上都有默认值，盲加会给每一发火球凭空记 +10。
     */
    private fun strengthOf(p: Projectile): Double {
        var strength = 0.0
        if (p.effects.any { it is Hurt }) strength += p.status.damage.coerceAtLeast(0.0)
        if (p.effects.any { it is Explosion }) {
            strength += p.status.explosionDamage.coerceAtLeast(0.0) *
                p.status.explosionRadius.coerceAtLeast(0f)
        }
        return strength
    }

    /**
     * 球壳球心（世界坐标）：相对脚底抬高到身体中段（[shellCenterOffset]），比眼睛位置更贴近「包裹全身」。
     *
     * **判定跟着玩家**（第 36 轮回退）：用户澄清「真正的护盾**依然跟随玩家**，但是每次展示的**碎片**不会跟着动，
     * 每次都新画一个碎片」—— 所以保护判定与球心都用持有者**实时位置**；
     * 不跟人的只是**画出来的面板**（见 [spawnPanels] 里不再 `followEntity` 的说明）。
     */
    private fun shellCenter(holder: Entity): Vec3 =
        holder.position().add(0.0, shellCenterOffset(holder), 0.0)

    /** 球壳球心相对脚底的偏移：身体中段（身高 55%）。 */
    private fun shellCenterOffset(holder: Entity): Double = holder.bbHeight * 0.55

    /**
     * 位移线段 `from → to` 与球壳的**进入点**，`null` = 这一 tick 没有从外侧穿进壳里。
     *
     * 起点已在壳内时**默认**返回 `null` —— 那属于「法术本来就生成在壳里」，
     * 挡不住也不该播格挡表现（贴脸施法就是这么漏过去的）。
     *
     * [allowInsideStart]（第 38 轮）放开这个限制，用于「**壳迎上来**」的情况：
     * 弹本来在壳外（[seenOutside] 记过），持有者朝它走过去时壳先把它罩住，
     * 这一 tick 起点已经在壳内 —— 那就把当前位置当作进入点，照挡。
     */
    private fun shellEntry(
        from: Vector3f,
        to: Vector3f,
        center: Vec3,
        allowInsideStart: Boolean = false,
    ): Vector3f? {
        val fx = from.x - center.x
        val fy = from.y - center.y
        val fz = from.z - center.z
        if (fx * fx + fy * fy + fz * fz < SHELL_RADIUS * SHELL_RADIUS) {
            return if (allowInsideStart) Vector3f(from) else null
        }

        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val a = dx * dx + dy * dy + dz * dz
        if (a < 1e-9) return null
        val b = 2.0 * (fx * dx + fy * dy + fz * dz)
        val c = fx * fx + fy * fy + fz * fz - SHELL_RADIUS * SHELL_RADIUS
        val disc = b * b - 4.0 * a * c
        if (disc < 0.0) return null
        val sq = sqrt(disc)
        val t = (-b - sq) / (2.0 * a)
        if (t < 0.0 || t > 1.0) return null
        return Vector3f(
            (from.x + dx * t).toFloat(),
            (from.y + dy * t).toFloat(),
            (from.z + dz * t).toFloat(),
        )
    }

    /** 给定方向（相对球心）落在哪块面板上：`center` 就是该面板的方向向量，所以取点积最大。 */
    private fun nearestPanelIndex(dir: Vec3): Int? {
        if (dir.lengthSqr() < 1e-9) return null
        val unit = dir.normalize()
        var best = -1
        var bestDot = -2.0
        for ((i, panel) in shieldPanels.withIndex()) {
            val d = panel.center.dot(unit)
            if (d > bestDot) {
                bestDot = d
                best = i
            }
        }
        return if (best >= 0) best else null
    }

    /**
     * 投射物打穿的破口区：**受击面板 + 相邻一圈**（共享一条棱 = 相邻，见 [panelNeighbours]）。
     *
     * 破口区既是玩法（这段时间不格挡），也是表现（碎裂 + 淡红轮廓的那一片）。
     */
    private fun breachArea(panelIndex: Int): List<Int> =
        listOf(panelIndex) + panelNeighbours.getOrElse(panelIndex) { emptySet() }

    /**
     * 一次爆炸**波及到**的面板（第 39 轮用户口径：「能够被爆炸波及的片全部显示」）。
     *
     * 判据就是字面：**面板中心（球面上的点）到爆心的距离 ≤ 爆炸半径**。
     * 所以爆炸越大、点亮的片越多 —— 半径 6 的核弹摆在壳外 3 格时，最远的背面片也只有 5.2 格，
     * 于是 **32 片全被波及**（整罩碎裂）；反过来小半径的爆炸只会点亮朝着它的那几片。
     *
     * ⚠️ 这里算的是**相对球心**的向量：`panel.center` 与 `toBlast` 都以球心为原点，直接相减即可。
     *
     * @param toBlast 爆心相对球心的向量（调用方已保证它在壳外）
     */
    private fun blastPanels(toBlast: Vec3, radius: Float): BlastPanels {
        val facing = ArrayList<Int>()
        val back = ArrayList<Int>()
        val unit = if (toBlast.lengthSqr() > 1e-9) toBlast.normalize() else Vec3(0.0, 1.0, 0.0)
        for ((index, panel) in shieldPanels.withIndex()) {
            if (panel.center.distanceTo(toBlast) > radius) continue
            // 朝爆点（法线与「球心 → 爆心」同侧）= 正面被吹到；背对的那几片同一发波及、暗一档显示
            if (panel.center.dot(unit) > 0.0) facing += index else back += index
        }
        // 一片都没够着（半径比"爆心到最近那片"还小）：退回离爆点最近的一块，保证总有东西可看
        if (facing.isEmpty() && back.isEmpty()) {
            nearestPanelIndex(toBlast)?.let { facing += it }
        }
        return BlastPanels(facing, back)
    }

    /** 一次爆炸波及到的面板，按「朝着爆点」与「背对爆点」分成两批（表现上后者暗一档）。 */
    private class BlastPanels(val facing: List<Int>, val back: List<Int>) {
        /** 全部被波及的面板：破口区（玩法）用的就是它 */
        val all: List<Int> get() = facing + back
    }

    /**
     * 爆炸碎裂的飘散方向：**整批沿爆点方向被吹开**。
     *
     * 返回的是**同一个 [Vec3] 实例** —— 同一个方向的面板会合并成一个 PD 组（见 [spawnGlassPanels]），
     * 所以一次整罩碎裂只占 1 个组、1 条 arm 日志，而不是 32 条。
     */
    private fun blastDrift(toBlast: Vec3): (Polyhedra.Panel) -> Vec3? {
        val blow = if (toBlast.lengthSqr() > 1e-9) toBlast.normalize().scale(SHATTER_DRIFT) else null
        return { blow }
    }

    /**
     * 投射物打穿的飘散方向：**每块面板沿自己的法线向外**（像被顶出去的一层壳）。
     *
     * 方向各不相同 ⇒ 每块面板各占一个组（≤7 块，见 [spawnGlassPanels] 的分组说明）。
     */
    private val panelNormalDrift: (Polyhedra.Panel) -> Vec3? =
        { it.center.normalize().scale(SHATTER_DRIFT) }

    /** 破口到期就清空（下次受击重新判定）。 */
    private fun expireBreach(gameTime: Long) {
        if (breachPanels.isNotEmpty() && gameTime >= breachUntil) breachPanels.clear()
    }

    // ---------------------------------------------------------------- 玻璃表现（第 23 轮，第 26 轮改口径）

    /**
     * **打穿**（投射物路径）：把 [panels] 定为破口，碎裂时每片沿自己法线向外飘。
     */
    private fun openBreach(level: ServerLevel, holder: Entity, panels: List<Int>) {
        markBreach(level, panels)
        spawnGlassPanels(
            level, holder, panels,
            edgeColor = GLASS_SHATTER_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = SHATTER_HOLD_TICKS, fadeTicks = SHATTER_FADE_TICKS,
            driftOf = panelNormalDrift,
        )
        spawnBreachOutline(level, holder, panels)
    }

    /**
     * **打穿**（爆炸路径，第 39 轮）：破口区 = 这一发**波及到的全部面板**（可能整整 32 片），
     * 碎裂时整批沿爆点方向被吹开；朝爆点的片是白热的、**背对爆点的片暗一档**（用户口径「背面的片可以暗一档」）。
     *
     * 两批碎裂共用同一个方向向量 ⇒ 各占 1 个 PD 组，加上轮廓一共 3 个组（见 [spawnGlassPanels]）。
     */
    private fun openBlastBreach(level: ServerLevel, holder: Entity, panels: BlastPanels, toBlast: Vec3) {
        markBreach(level, panels.all)
        val drift = blastDrift(toBlast)
        spawnGlassPanels(
            level, holder, panels.facing,
            edgeColor = GLASS_SHATTER_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = SHATTER_HOLD_TICKS, fadeTicks = SHATTER_FADE_TICKS,
            driftOf = drift,
        )
        spawnGlassPanels(
            level, holder, panels.back,
            edgeColor = GLASS_SHATTER_BACK_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = SHATTER_HOLD_TICKS, fadeTicks = SHATTER_FADE_TICKS,
            driftOf = drift, fillColor = GLASS_BACK_FILL_COLOR,
        )
        spawnBreachOutline(level, holder, panels.all)
    }

    /** 破口（玩法）：[panels] 这 [breachTicks] 内置为「不格挡」，并记下到期时间。 */
    private fun markBreach(level: ServerLevel, panels: List<Int>) {
        breachPanels.clear()
        breachPanels += panels
        breachUntil = level.gameTime + breachTicks
    }

    /** 破口轮廓：常驻整段破口期（最后 [BREACH_OUTLINE_FADE_TICKS] tick 淡出）的淡红提示线。 */
    private fun spawnBreachOutline(level: ServerLevel, holder: Entity, panels: List<Int>) {
        spawnGlassPanels(
            level, holder, panels,
            edgeColor = BREACH_OUTLINE_COLOR, edgeScale = BREACH_OUTLINE_SCALE,
            withFill = false, holdTicks = max(1, breachTicks - BREACH_OUTLINE_FADE_TICKS),
            fadeTicks = BREACH_OUTLINE_FADE_TICKS,
        )
    }

    /**
     * 铺一组玻璃面板：**亮边**（面板轮廓上的点）+ 可选**极淡填充**（面板内部的稀疏点），
     * 成员挂在**以球心为 pivot** 的组上，[holdTicks] 后开始淡出、[fadeTicks] 内淡完并由 PD 销毁整组。
     *
     * **面板按飘散位移分组**（第 39 轮，open.md §1.9.2）：PD 的组是 arm / 演出的单位，
     * 一块面板一个组会让一次爆炸打出 32 条 `armed` 日志。位移**相同**的面板于是共用一个组 ——
     * 格挡玻璃、破口轮廓、爆炸碎裂（整批沿爆点方向）都只占 **1 个组**；
     * 只有「投射物打穿、每片沿自己法线向外」那种才不得不一片一组（≤7 组）。
     *
     * @param driftOf 每块面板的飘散位移（`null` = 原地淡出）
     * @param fillColor 填充点的颜色（背对爆点那一批用更淡的）
     */
    private fun spawnGlassPanels(
        level: ServerLevel,
        holder: Entity,
        panelIndices: List<Int>,
        edgeColor: Color,
        edgeScale: Float,
        withFill: Boolean,
        holdTicks: Int,
        fadeTicks: Int,
        driftOf: (Polyhedra.Panel) -> Vec3? = { null },
        fillColor: Color = GLASS_FILL_COLOR,
    ) {
        val manager = wand.pdParticles(level)
        val center = shellCenter(holder)
        // ⚠️ 顺序：**先建组再铺成员** —— 客户端 arm 时按「成员 spawn 位置 − 组 `pivot` 字段」
        // 反算相对偏移（第 13 轮踩过）；成员用**相对球心**的偏移（`offsetFromPivot`），于是形状刚性。
        // 第 36 轮口径：**面板画在生成处就不再动**（不调 `followEntity`）—— 判定跟着玩家，
        // 所以下一次受击会在玩家**当时的位置**新画一批碎片，旧的那批留在原地看着「掉队」。
        for ((drift, batch) in panelIndices.mapNotNull { shieldPanels.getOrNull(it) }.groupBy(driftOf)) {
            val group = manager.createGroup(center)
            for (panel in batch) {
                for (offset in panel.ring) {
                    manager.create()
                        .position(center.add(offset))
                        .color(edgeColor)
                        .scale(edgeScale)
                        .lifetime(PD_IMMORTAL)
                        .group(group.id)
                        .offsetFromPivot(offset)
                        .spawn()
                }
                if (withFill) {
                    for (offset in fillOffsets(panel)) {
                        manager.create()
                            .position(center.add(offset))
                            .color(fillColor)
                            .scale(GLASS_FILL_SCALE)
                            .lifetime(PD_IMMORTAL)
                            .group(group.id)
                            .offsetFromPivot(offset)
                            .spawn()
                    }
                }
            }
            if (drift != null) group.move(drift, fadeTicks, EasingType.EASE_OUT)
            group.delay(holdTicks)
            // EASE_OUT：透明度先快后慢 —— 长尾才像「碎开后飘散」，EASE_IN 会在最后一帧啪一下消失
            group.fadeOut(fadeTicks, removeAfter = true, easing = EasingType.EASE_OUT)
        }
    }

    /**
     * 面板内部的**填充点**：从面板中心向边界点连线采样（扇形），再把结果归一化回球面
     * （与 [Polyhedra.truncatedIcosahedron] 的取点口径一致，所以填充点与边缘点贴在同一个球面上）。
     *
     * 隔 [FILL_STRIDE] 个边界点采一条射线、每条射线取 [FILL_FRACTIONS] 里的几个比例：
     * 点距与「粒子实际尺寸 ≈ scale × 0.213 格」配平后，叠出来正好是一层均匀的极淡薄雾。
     */
    private fun fillOffsets(panel: Polyhedra.Panel): List<Vec3> {
        val c = panel.center
        val radius = c.length()
        val out = ArrayList<Vec3>(panel.ring.size / FILL_STRIDE * FILL_FRACTIONS.size)
        var i = 0
        while (i < panel.ring.size) {
            val v = panel.ring[i]
            for (t in FILL_FRACTIONS) {
                out += c.add(v.subtract(c).scale(t)).normalize().scale(radius)
            }
            i += FILL_STRIDE
        }
        return out
    }

    override fun initLore() {
        super.initLore()
        lore(Lore.RESISTANCE, Lore.formatDouble(baseResistance))
        lore(Lore.RESISTANCE_PER_CARD, Lore.formatDouble(resistancePerCard))
        // 单张卡时的窗口预算（= (基础 + 每张卡) × 系数）：护盾真正的"防 DPS"刻度
        lore(Lore.RESISTANCE_BUDGET, Lore.formatDouble((baseResistance + resistancePerCard) * RESISTANCE_BUDGET_RATIO))
        lore(Lore.BOUNCE_COST, Lore.formatDouble(baseBounceCost))
        lore(Lore.EXTRA_COST, Lore.formatDouble(extraCostPerPoint))
        lore(Lore.BREACH, (breachTicks / 20).toString())
    }

    companion object {
        // ---- 面板几何（与第 23 轮的玻璃护盾共用同一套截角二十面体） ----

        /** 球壳半径（方块）：既是表现面，也是**格挡判定面**与「壳内侧」的界线 */
        private const val SHELL_RADIUS = 1.7

        /** 超出抗性的部分最多按「抗性的这个倍数」计费（第 35 轮封顶，用户口径 = 2） */
        private const val MAX_EXTRA_RESISTANCE_RATIO = 2.0

        /** 窗口预算的时间窗（tick）：1 秒。用户口径「在一段时间内可以承担的强度」，追问定为 20 tick */
        private const val BUDGET_WINDOW_TICKS = 20

        /**
         * 窗口预算 = 抗性 × 本系数（第 39 轮）。
         *
         * 为什么不是 1（= 把抗性直接当预算，open.md 登记时的默认口径）：
         * 强度 5 的火球 > 抗性 4 ⇒ **单发火球就会破口**，连「超出抗性加价」那条路都够不到
         * （能走到加价的只剩 `4 < 强度 ≤ 4` 这个空集），整套法力定价等于被架空。
         * 取 2 之后的三个刻度（抗性 4）：**单发火球 5 挡得住、一秒内第二发（累计 10）挡不住、
         * 大爆 72 / 核弹 144 必破**。推演见 docs/design/combat-pricing.md §4.95。
         *
         * 公开：这是与 [baseResistance] / [baseBounceCost] 同级的**手感旋钮**，
         * 录制流水线也拿它算"单张卡时的窗口预算"（护盾卡片在 `prepare` 阶段还没有 `wand` 引用，
         * 所以不能直接调 [strengthBudget]）。
         */
        const val RESISTANCE_BUDGET_RATIO = 2.0

        /**
         * 每条棱上放几个点（含起点，见 [Polyhedra.truncatedIcosahedron]）：
         * **棱长 1.166 格（半径 1.7）/ 本值 = 点间距 0.194 格**。
         *
         * ⚠️ 粒子「实际尺寸」**不是 `scale` 本身**：PD 的 `quadSize = scale × 0.1 × texScale`
         * （`BridgeParticle.EDITOR_TO_MC_SCALE`，录制实测 `texScale ≈ 2.1`）→ **实际格数 ≈ scale × 0.213**。
         * 6 点/棱 → 点距是粒径的 3.7 倍，这是**刻意的点阵观感**；不要再往「连续实线」调
         * （球内第一人称近看会糊成一条粗带，第 20/21 轮踩过）。
         */
        private const val SHELL_POINTS_PER_EDGE = 6

        // ---- 面板填色（格挡 / 碎裂 / 破口轮廓三种时机共用一套铺法） ----

        /** 亮边的粒子尺寸：边缘细一点才像玻璃的棱 */
        private const val GLASS_EDGE_SCALE = 0.26f

        /**
         * 填充的粒子尺寸：比边缘大、**极淡**（alpha ≈18%），靠重叠叠出一层均匀薄雾。
         * 0.55 → 约 0.12 格，与填充点距同量级，刚好连成一片。
         */
        private const val GLASS_FILL_SCALE = 0.55f

        /** 破口轮廓的粒子尺寸：比亮边再细一点，读起来是「一条提示线」而不是「一块玻璃」 */
        private const val BREACH_OUTLINE_SCALE = 0.22f

        /** 填充采样：隔几个边界点采一条射线 / 每条射线上取哪几个比例 */
        private const val FILL_STRIDE = 2
        private val FILL_FRACTIONS = doubleArrayOf(0.62, 0.88)

        /** **格挡成功**的玻璃：停留 + 淡出 ≈ 0.35 秒 */
        private const val HIT_HOLD_TICKS = 2
        private const val HIT_FADE_TICKS = 5

        /** **打穿碎裂**：一闪之后面板向外飘散 + 淡出 ≈ 0.7 秒；[SHATTER_DRIFT] 是飘散距离（方块） */
        private const val SHATTER_HOLD_TICKS = 2
        private const val SHATTER_FADE_TICKS = 12
        private const val SHATTER_DRIFT = 0.35

        /** **破口轮廓**：常驻整段破口期，最后这点时间用来淡出（时长由 [breachTicks] 决定） */
        private const val BREACH_OUTLINE_FADE_TICKS = 6

        /** 格挡那块玻璃的亮边：**接近白**（冷白）—— 实机反馈「击中要变白」 */
        private val GLASS_EDGE_COLOR: Color = Color.ofInt(226, 242, 255, 245)

        /** 碎裂那一下的亮边：更白 */
        private val GLASS_SHATTER_COLOR: Color = Color.ofInt(226, 246, 255, 245)

        /** 爆炸碎裂里**背对爆点**那一批的亮边：同一块玻璃暗一档（不抢正面的镜） */
        private val GLASS_SHATTER_BACK_COLOR: Color = Color.ofInt(198, 224, 248, 130)

        /** 破口轮廓：淡红（提示攻击者「这里能打」），比玻璃暗、比背景亮 */
        private val BREACH_OUTLINE_COLOR: Color = Color.ofInt(255, 118, 118, 150)

        /** 填充：极淡的冷白（≈18% 不透明度）：整块玻璃在受击瞬间读起来是「白了一下」 */
        private val GLASS_FILL_COLOR: Color = Color.ofInt(206, 232, 255, 46)

        /** 背对爆点那一批的填充：比正面更淡 */
        private val GLASS_BACK_FILL_COLOR: Color = Color.ofInt(206, 232, 255, 24)
    }
}
