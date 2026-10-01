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
 * - 每次「格挡真的发生」按强度结算：
 *   `R = 基础抗性 + 每张卡抗性`；`强度 ≤ R` 只付「基础反弹 MP」，
 *   `强度 > R` 付 `基础反弹 + (强度 - R) × 每点额外`。
 * - **法力不够 = 挡不住**：这一发照常命中持有者，并在受击方向「一块 + 相邻一圈」上出**碎裂**，
 *   该区域 [breachTicks] 内不挡任何东西（**定向破口**），破口期间那一圈常驻显示淡红色轮廓。
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
 * - **打穿**：那一块 + 相邻一圈**碎裂**（向外飘散 + 淡出），随后是持续整个破口期的**淡红轮廓**；
 * - 旧的「整罩一闪而碎」「护盾恢复闪一下」两段演出随耐久模型一起删除。
 *
 * 所有玻璃都挂在持有者身上（`followEntity`，随人移动），淡出结束由 PD 自行销毁
 * （`fadeOut(removeAfter = true)`），所以本实例此后不再被 tick（法杖被收起/回收）也不留孤儿粒子。
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
     * 当前是否处于破口状态。
     *
     * 破口是**瞬时**状态（[breachTicks] 后自动消失），不进存档；这个只读口子给测试/调试用
     * （录制流水线靠它断言「法力不足 → 打穿 + 破口」这一段真的发生了）。
     */
    val breached: Boolean get() = breachPanels.isNotEmpty()

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

    /** 这次格挡要花的 MP：抗性以内只付基础反弹，超出部分每点加价。 */
    private fun bounceCost(strength: Double): Double {
        val resisted = resistance()
        val over = (strength - resisted).coerceAtLeast(0.0)
        return baseBounceCost + over * extraCostPerPoint
    }

    /**
     * 每 tick 维护护盾：只做主卡的格挡结算（见 [primaryShield]）。
     *
     * 判定顺序刻意是「先算强度与价钱、再看付不付得起」——付不起不是"少扣一点"，
     * 而是这一发**整个挡不住**（用户口径：MP 不足则无法抵挡这次攻击）。
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

        val hitPoint = shellEntry(prev, pos, shellCenter) ?: return false
        val normal = Vector3f(hitPoint).sub(Vector3f(shellCenter.x.toFloat(), shellCenter.y.toFloat(), shellCenter.z.toFloat()))
        if (normal.lengthSquared() < 1e-6f) return false
        normal.normalize()
        if (p.status.velocity.dot(normal) >= 0) return false   // 正在远离壳面：不是「打进来」

        val panelIndex = nearestPanelIndex(Vec3(normal.x.toDouble(), normal.y.toDouble(), normal.z.toDouble())) ?: return false
        if (panelIndex in breachPanels) return false           // 破口方向：直接放行（连 MP 都不花）

        val strength = strengthOf(p)
        if (!wand.spendMana(bounceCost(strength))) {
            // 挡不住：投射物照常飞（一行都不改），护盾这边付出破口
            openBreach(level, holder, panelIndex)
            return false
        }

        // 挡下了：以壳面法线反射，并把投射物推到壳外 0.05 格（下一 tick 必然离开判定面）
        val speed = p.status.velocity.length()
        p.status.velocity.normalize().reflect(normal).mul(speed)
        p.status.pos = Vector3f(normal).mul(0.05f).add(hitPoint)
        spawnGlassPanels(
            level, holder, listOf(panelIndex),
            edgeColor = GLASS_EDGE_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = HIT_HOLD_TICKS, fadeTicks = HIT_FADE_TICKS, drift = 0.0,
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
        val panelIndex = nearestPanelIndex(center.subtract(shellCenter)) ?: return false
        if (panelIndex in breachPanels) return false

        // 强度 = 衰减后伤害 × 半径（见 design/combat-pricing.md §3）
        val strength = falloffDamage * radius
        if (!wand.spendMana(bounceCost(strength))) {
            openBreach(level, holder, panelIndex)
            return false
        }
        // 这件事发生在**这把法杖**上：粒子管理器取它的（爆炸路径不保证 `this.wand` 已被 tick 写过）
        this.wand = wand
        spawnGlassPanels(
            level, holder, listOf(panelIndex),
            edgeColor = GLASS_EDGE_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = HIT_HOLD_TICKS, fadeTicks = HIT_FADE_TICKS, drift = 0.0,
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
     * **第 33 轮起静态锚定**：第一次求值时把它冻住，此后**不再跟随持有者** ——
     * 用户口径「本来就是玩家附近出现护盾片，让这个出现的护盾片**不再跟随玩家**就行了，
     * 因为现在如果玩家移动，这个护盾片会和玩家一起动，很奇怪」。
     * 目前没有收回路径，所以冻住后一直有效（要挪位置 = 重新展开时把 anchor 置空）。
     *
     * 保护判定（在壳内/壳外、爆炸中心到壳心距离）全部读这个值 → 护盾留在原地，
     * 继续保护**落在它半径内**的人；数值规则不变（固定 5 + 每点 5 MP、破口 60 tick）。
     */
    private fun shellCenter(holder: Entity): Vec3 =
        anchor ?: holder.position().add(0.0, shellCenterOffset(holder), 0.0).also { anchor = it }

    /** 冻结的球壳球心；`null` = 还没展开（下次求值时按当时的持有者位置重新锚定）。 */
    private var anchor: Vec3? = null

    // 注：护盾目前是「只要持有就展开」，没有收回/重开生命周期，所以锚点冻结后一直有效；
    // 将来若做「重新施法挪位置」，在那条路径上把 `anchor` 置空即可重新锚定。

    /** 球壳球心相对脚底的偏移：身体中段（身高 55%）。 */
    private fun shellCenterOffset(holder: Entity): Double = holder.bbHeight * 0.55

    /**
     * 位移线段 `from → to` 与球壳的**进入点**，`null` = 这一 tick 没有从外侧穿进壳里。
     *
     * 起点已在壳内时直接返回 `null` —— 那属于「法术本来就生成在壳里」，
     * 挡不住也不该播格挡表现（贴脸施法就是这么漏过去的）。
     */
    private fun shellEntry(from: Vector3f, to: Vector3f, center: Vec3): Vector3f? {
        val fx = from.x - center.x
        val fy = from.y - center.y
        val fz = from.z - center.z
        if (fx * fx + fy * fy + fz * fz < SHELL_RADIUS * SHELL_RADIUS) return null

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

    /** 破口到期就清空（下次受击重新判定）。 */
    private fun expireBreach(gameTime: Long) {
        if (breachPanels.isNotEmpty() && gameTime >= breachUntil) breachPanels.clear()
    }

    // ---------------------------------------------------------------- 玻璃表现（第 23 轮，第 26 轮改口径）

    /** **打穿**：受击那一块 + 相邻一圈碎裂（向外飘散 + 淡出），并留下持续整段破口期的淡红轮廓。 */
    private fun openBreach(level: ServerLevel, holder: Entity, panelIndex: Int) {
        breachPanels.clear()
        breachPanels += panelIndex
        breachPanels += panelNeighbours.getOrElse(panelIndex) { emptySet() }
        breachUntil = level.gameTime + breachTicks

        spawnGlassPanels(
            level, holder, breachPanels.toList(),
            edgeColor = GLASS_SHATTER_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = SHATTER_HOLD_TICKS, fadeTicks = SHATTER_FADE_TICKS,
            drift = SHATTER_DRIFT,
        )
        spawnGlassPanels(
            level, holder, breachPanels.toList(),
            edgeColor = BREACH_OUTLINE_COLOR, edgeScale = BREACH_OUTLINE_SCALE,
            withFill = false, holdTicks = max(1, breachTicks - BREACH_OUTLINE_FADE_TICKS),
            fadeTicks = BREACH_OUTLINE_FADE_TICKS, drift = 0.0,
        )
    }

    /**
     * 铺一组玻璃面板：**亮边**（面板轮廓上的点）+ 可选**极淡填充**（面板内部的稀疏点），
     * 每块面板一个组，挂在持有者身上，[holdTicks] 后开始淡出、[fadeTicks] 内淡完并由 PD 销毁整组。
     *
     * @param drift 沿面板法线向外飘散的距离（方块）；`0.0` = 原地淡出
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
        drift: Double,
    ) {
        val manager = wand.pdParticles(level)
        // 第 33 轮：面板挂在**冻结的锚点**上（不再随人移动）
        val center = shellCenter(holder)
        for (index in panelIndices) {
            val panel = shieldPanels.getOrNull(index) ?: continue
            // ⚠️ 顺序：**先 `followEntity` 再铺成员** —— 客户端 arm 时按「成员 spawn 位置 − 组 `pivot` 字段」
            // 反算相对偏移，而 `followEntity` 会顺手改那个字段（第 13 轮踩过）；成员用**相对球心**的偏移
            // （`offsetFromPivot`），于是面板随人移动、形状刚性。
            val group = manager.createGroup(center)
            // 静态锚定：不调 followEntity，成员按「相对球心」的偏移铺开，形状刚性、位置固定
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
                        .color(GLASS_FILL_COLOR)
                        .scale(GLASS_FILL_SCALE)
                        .lifetime(PD_IMMORTAL)
                        .group(group.id)
                        .offsetFromPivot(offset)
                        .spawn()
                }
            }
            if (drift > 0.0) {
                group.move(panel.center.normalize().scale(drift), fadeTicks, EasingType.EASE_OUT)
            }
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
        lore(Lore.BOUNCE_COST, Lore.formatDouble(baseBounceCost))
        lore(Lore.EXTRA_COST, Lore.formatDouble(extraCostPerPoint))
        lore(Lore.BREACH, (breachTicks / 20).toString())
    }

    companion object {
        // ---- 面板几何（与第 23 轮的玻璃护盾共用同一套截角二十面体） ----

        /** 球壳半径（方块）：既是表现面，也是**格挡判定面**与「壳内侧」的界线 */
        private const val SHELL_RADIUS = 1.7

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

        /** 破口轮廓：淡红（提示攻击者「这里能打」），比玻璃暗、比背景亮 */
        private val BREACH_OUTLINE_COLOR: Color = Color.ofInt(255, 118, 118, 150)

        /** 填充：极淡的冷白（≈18% 不透明度）：整块玻璃在受击瞬间读起来是「白了一下」 */
        private val GLASS_FILL_COLOR: Color = Color.ofInt(206, 232, 255, 46)
    }
}
