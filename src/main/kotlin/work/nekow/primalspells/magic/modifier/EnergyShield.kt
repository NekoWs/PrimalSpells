package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.entity.projectile.LlamaSpit
import net.minecraft.world.entity.projectile.Projectile as VanillaProjectile
import net.minecraft.world.entity.projectile.ShulkerBullet
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.lifecycle.Explosion
import work.nekow.primalspells.magic.effect.lifecycle.DirectDamage
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.util.PD_IMMORTAL
import work.nekow.primalspells.util.Polyhedra
import work.nekow.primalspells.util.pdParticles
import work.nekow.primalspells.wand.Wand
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 手持法杖的常驻玻璃护盾：平时不可见，受击面板显形/碎裂，破口跟随持有者。
 *
 * 只由第一张卡结算；抗性=4+6×张数，20刻滚动承伤预算=6×张数³。
 * 预算内按基础MP及超抗性部分扣蓝；超预算/付不起蓝时结构破裂，仍抵消当次攻击，
 * 之后该方向在20～60刻内失防。8张卡预算3072，通常先耗尽法力。
 * 壳内爆炸、壳内生成的投射物和穿刺仍绕过护盾。
 * 原版投射物与法术共用进入判定，成功格挡（含破盾当次）均沿壳面反射。
 * 表现仅有面板晶格、裂纹、独立外飞碎片及破口轮廓，没有额外圆环。
 * 规则见docs/design/combat-pricing.md第68轮。
 */
class EnergyShield : Modifier() {
    override val id = "energy_shield"

    /** 基础抗性：强度不超过它的攻击只付基础反弹 MP */
    var baseResistance: Double = 4.0

    /** 每张护盾卡额外提供的抗性（多张卡 = 更能扛连续低强度攻击） */
    var resistancePerCard: Double = 6.0

    /** 基础反弹消耗（MP）：每一次成功格挡都要付 */
    var baseBounceCost: Double = 5.0

    /** 强度超出抗性后，每一点额外消耗的 MP */
    var extraCostPerPoint: Double = 5.0

    /** 破口的基础时长（tick）：20 = 1 秒。第 40 轮起按超出抗性的程度加长，见 [breachTicksFor] */
    var baseBreachTicks: Int = 20

    /** 破口时长：强度每超出抗性 1 点加这么多 tick（第 40 轮） */
    var breachTicksPerPoint: Double = 6.0

    /** 破口时长的上限（tick）：60 = 3 秒（原来的固定值，现在是最坏情况） */
    var maxBreachTicks: Int = 60

    /**
     * 破口的面板下标（空 = 没有破口）。
     *
     * 只在内存里：破口是几秒的瞬时状态，不值得进存档（也不该跨重登保留）。
     */
    private val breachPanels = HashSet<Int>()

    /** 破口到期时间（`gameTime`）；到期后在下次结算时清空 [breachPanels] */
    private var breachUntil = 0L

    /**
     * 曾经**在壳外出现过**的投射物（身份键 → 最后一次在壳外出现的 gameTime）。
     *
     * 键：自家法术用 `System.identityHashCode`（`Magic` 只有法术名 `id: String` 没有实例级 UUID），
     * 原版投射物用实体网络 id。代价是对象被回收后哈希可能重复（概率极低），
     * 最坏情况是"某发生成在壳里的法术被误挡一次"。
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
     * 破口是**瞬时**状态（[breachTicksFor] 决定的时长后自动消失），不进存档；这个只读口子给测试/调试用
     * （录制流水线靠它断言「打穿 + 破口」那几段真的发生了）。
     */
    val breached: Boolean get() = breachPanels.isEmpty().not()

    /**
     * 破口覆盖了**几块面板**（0 = 没破口）。
     *
     * 同样是给测试/调试用的只读口子：第 39/40 轮的表现口径是"爆炸**波及到的片全部**显示"，
     * 而**像素判据量不出这件事** —— 32 片的点阵在共享顶点/棱处大量重叠（1056 个点只落在 ~420 个位置上），
     * 所以"整罩"的粉色像素只有 7 片时的 ~1.7 倍，而不是 4 倍。判定直接问面板数才靠谱。
     */
    val breachPanelCount: Int get() = breachPanels.size

    /**
     * 一次「挡不住」开出的破口时长（第 40 轮用户口径：「破盾的冷却改为**根据超出抗性的部分**决定」）。
     *
     * `超出 = max(0, 强度 − 抗性)` → `基础 [baseBreachTicks] + 每点 [breachTicksPerPoint]`，
     * 封顶 [maxBreachTicks]。抗性 6 时的刻度：箭矢/火球（强度 5~6，超出 0）→ **1 秒**；
     * 强化火球（8，超出 2）→ **1.6 秒**；爆炸类（强度 60+，超出 50+）→ 吃满 **3 秒**。
     *
     * 注意用的是**这一发自己的强度**：窗口预算爆掉时也是这么算（那一发可能很弱，
     * 破口就短）—— 用户口径说的是"超出抗性的部分"，不是"超出预算的部分"。
     */
    private fun breachTicksFor(strength: Double): Int {
        val over = (strength - resistance()).coerceAtLeast(0.0)
        return (baseBreachTicks + over * breachTicksPerPoint).toInt().coerceIn(baseBreachTicks, maxBreachTicks)
    }

    // ---------------------------------------------------------------- DPS 预算（第 39 轮）

    /** 滚动记录里的一项：某个 gameTime 承担掉的强度。 */
    private class UsedStrength(val gameTime: Long, val strength: Double)

    /**
     * 最近 [BUDGET_WINDOW_TICKS] tick 内**已经承担**的强度（只在内存：1 秒的瞬时状态，不进存档）。
     *
     * 只由**主卡**记账（只有主卡结算格挡），多张卡同时提高 [resistance] 和 [strengthBudget]。
     */
    private val recentStrength = ArrayDeque<UsedStrength>()

    /** 每20刻滚动预算随卡数立方增长，消除旧固定6点预算对多卡抗性的硬性截断。 */
    fun strengthBudget(): Double {
        val cards = wand.loadedMagics.count { it is EnergyShield }.coerceAtLeast(1).toDouble()
        return SKELETON_DPS * SKELETONS_HELD * cards * cards * cards
    }

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

    /** 先查滚动预算，再扣MP；失败清除过载记账，调用方开破口并抵消当次攻击。 */
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

    /** 每刻只由主卡维护进入判定、短期记账与破口到期。 */
    override fun onInventoryTick() {
        if (primaryShield() !== this) return

        val holder = caster
        if (!holder.isAlive) return
        val level = holder.level() as? ServerLevel ?: return
        expireBreach(level.gameTime)

        val shellCenter = shellCenter(holder)

        // 自家法术：场上没有投射物就跳过这一段（剪枝）
        if (MagicManager.projectiles.isNotEmpty()) {
            for (p in MagicManager.projectiles) {
                if (!p.alive) continue
                if (p.caster === holder) continue      // 自己施放的法术不弹（出生在面前，否则出生即被弹回）
                if (p.maxHitTargets < 0) continue      // 穿透：完全无视护盾
                // 有实体碰撞的运动弹已在Move按真实子步检测；不能在视觉之后重建虚假入射线。
                if (p.motionEnabled && p.collisionEnabled && p.hitRadius > 0.0) continue
                tryBlock(level, holder, shellCenter, p)
            }
        }

        // 原版投射物（第 40 轮）：箭矢/三叉戟/火球/雪球/药水… 走同一套穿越判定与强度结算。
        // 只在壳体周围一圈内查询 —— 场上没有东西时这一句几乎不花钱（宽相位 AABB 查询）。
        val reach = SHELL_RADIUS + VANILLA_SWEEP_RANGE
        val box = AABB.ofSize(shellCenter, reach * 2.0, reach * 2.0, reach * 2.0)
        for (p in level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile::class.java, box)) {
            tryBlockVanilla(level, holder, shellCenter, p)
        }
    }

    /**
     * 移动子步拦截与直接命中兜底共用入口。Move传入真实相邻子步[from]/[to]，
     * 在本体命中及视觉之前完成反射；其它直接伤害入口未提供线段时仍保留原有兜底。
     * 强度、MP和破口统一交给[tryBlock]。
     *
     * @return true = 已格挡（调用方跳过伤害），投射物已被反射
     */
    fun absorbProjectile(level: ServerLevel, wand: Wand, holder: Entity, projectile: Projectile,
                         from: Vector3d? = null, to: Vector3d = projectile.status.pos): Boolean {
        if (wand.loadedMagics.firstOrNull { it is EnergyShield } !== this) return false
        if (!holder.isAlive) return false
        if (projectile.caster === holder) return false
        if (projectile.maxHitTargets < 0) return false   // 穿透不吃护盾这一套
        this.wand = wand
        expireBreach(level.gameTime)
        return tryBlock(level, holder, shellCenter(holder), projectile, from, to)
    }

    /**
     * 对一枚投射物做格挡判定：移动子步/命中入口[absorbProjectile]，
     * 以及不走实体碰撞的载体被动入口[onInventoryTick]共用。
     *
     * @return true = 这一发被格挡（已扣 MP、已反射、已出玻璃板）；false = 不归护盾管或已从破口穿过
     */
    private fun tryBlock(level: ServerLevel, holder: Entity, shellCenter: Vec3, p: Projectile,
                         from: Vector3d? = null, to: Vector3d = p.status.pos): Boolean {
        // 光束的束源不碰撞实体，但射线命中仍声明 DirectDamage，并传入真实命中线段。
        // 无直接伤害的爆炸点等仍走爆炸入口。
        if (p.hitRadius <= 0.0 && p.effects.none { it is DirectDamage }) return false
        // Move提供实际走过的子步；仅未提供from的兼容入口才用pos-velocity估算。
        // 不可在反射完成后用新速度重建旧入射路径。
        val velocity = p.status.velocity
        val hit = resolveIncoming(
            level, holder, shellCenter, to, velocity,
            System.identityHashCode(p), strengthOf(p), from,
        ) ?: return false

        // 以壳面法线反射，只分离数值误差；Move继续消耗本刻余时。
        p.shieldReflectionPoint = Vec3(hit.point.x, hit.point.y, hit.point.z)
        val speed = velocity.length()
        velocity.normalize().reflect(hit.normal).mul(speed)
        p.status.pos.set(hit.point).add(hit.normal.x * 1e-7, hit.normal.y * 1e-7, hit.normal.z * 1e-7)
        return true
    }

    /**
     * 对**一枚原版投射物**做一次格挡判定（第 40 轮用户口径：「护盾能够防御/反弹原版的投射物」）。
     *
     * 规则与自家法术**完全同一套**（同一条穿越判定、同一套强度/预算/法力/破口），差别只有两点：
     * 强度取自 [vanillaStrength] 的档位表，反弹要用原版的 `deltaMovement` + 归属（[reflectVanilla]）。
     * 所以「打上来的箭会被弹回去、还可能打回射手」是同一个口径的自然结果。
     *
     * @return true = 已格挡（调用方应取消这次命中）；false = 不归护盾管 / 已从破口穿过
     */
    private fun tryBlockVanilla(level: ServerLevel, holder: Entity, shellCenter: Vec3, p: VanillaProjectile): Boolean {
        val strength = vanillaStrength(p) ?: return false           // 钓钩、末影之眼…护盾不管
        val owner = p.owner
        if (owner === holder) return false                          // 自己的弹不弹（弓、药水）
        if (owner != null && holder.isAlliedTo(owner)) return false // 队友 / 自己宠物的不弹

        val velocity = p.deltaMovement
        val hit = resolveIncoming(
            level, holder, shellCenter,
            Vector3d(p.x, p.y, p.z),
            Vector3f(velocity.x.toFloat(), velocity.y.toFloat(), velocity.z.toFloat()),
            p.id, strength,
        ) ?: return false
        reflectVanilla(p, hit, holder)
        return true
    }

    /**
     * 把原版投射物**反弹**回去：速度按壳面法线镜像、大小不变，位置推到壳外一点，
     * 并把归属改成持有者 —— 于是它再也打不到持有者，却能打回原来的射手。
     *
     * `needsSync = true` 是必须的：速度是服务端权威字段，不置这一位客户端看不到反弹。
     */
    private fun reflectVanilla(p: VanillaProjectile, hit: ShellHit, holder: Entity) {
        val velocity = p.deltaMovement
        val speed = velocity.length()
        if (speed > 1e-6) {
            // MC 的 `Vec3` 没有 reflect：自己按「v − 2(v·n)n」做镜像（与自家法术的 JOML reflect 同义）
            val n = hit.normal.toVec3()
            val unit = velocity.normalize()
            p.deltaMovement = unit.subtract(n.scale(2.0 * unit.dot(n))).scale(speed)
        }
        p.setPos(hit.point.x + hit.normal.x * 0.1, hit.point.y + hit.normal.y * 0.1, hit.point.z + hit.normal.z * 0.1)
        p.owner = holder
        p.needsSync = true
    }

    /**
     * **原版投射物的命中兜底**（`ProjectileImpactEvent` → `ShieldGuard.absorbVanillaProjectile`）。
     *
     * 为什么需要它：原版箭矢 3 格/tick、火球更快，而扫场跑在持有者的实体 tick 里 ——
     * 弹可能在同一 tick 内就贴到身上（与自家 `laser` 是同一个理由，见 [absorbProjectile]）。
     *
     * @return true = 已格挡（调用方取消这次命中；投射物已被反弹）
     */
    fun absorbVanillaProjectile(level: ServerLevel, wand: Wand, holder: Entity, projectile: VanillaProjectile): Boolean {
        if (wand.loadedMagics.firstOrNull { it is EnergyShield } !== this) return false
        if (!holder.isAlive) return false
        this.wand = wand
        expireBreach(level.gameTime)
        return tryBlockVanilla(level, holder, shellCenter(holder), projectile)
    }

    /** 挡下时的壳面进入点与法线（调用方据此做自己的那套反射）。 */
    private class ShellHit(val point: Vector3d, val normal: Vector3f)

    /**
     * 一次格挡判定的**公共部分** —— 自家法术（[tryBlock]）与原版投射物（[tryBlockVanilla]）共用。
     *
     * 只做与"是什么弹"无关的事：穿越判定 → 破口方向检查 → 强度结算 → 破口或玻璃。
     * 反射交给调用方（自家的弹是 `status.pos/velocity`，原版是 `deltaMovement` + 归属）。
     *
     * @param identity [seenOutside] 的身份键：自家法术没有实例 UUID 用 `identityHashCode`，
     *   原版投射物直接用实体网络 id（两套 id 的取值域几乎不重叠，撞了就最坏误挡一次）
     * @return 挡下时的壳面进入点与法线；null = 不归护盾管或已从破口穿过
     */
    private fun resolveIncoming(
        level: ServerLevel,
        holder: Entity,
        shellCenter: Vec3,
        pos: Vector3d,
        velocity: Vector3f,
        identity: Int,
        strength: Double,
        from: Vector3d? = null,
    ): ShellHit? {
        val prev = from ?: Vector3d(pos).sub(velocity)

        // 先登记「这一 tick 它在壳外」——下一步就可能被壳"追上"而落到壳内
        val ex = pos.x - shellCenter.x
        val ey = pos.y - shellCenter.y
        val ez = pos.z - shellCenter.z
        if (ex * ex + ey * ey + ez * ez > SHELL_RADIUS * SHELL_RADIUS) {
            seenOutside[identity] = level.gameTime
        }
        val stale = seenOutside.entries.iterator()
        while (stale.hasNext()) {
            if (level.gameTime - stale.next().value > seenOutsideTtl) stale.remove()
        }

        val seen = identity in seenOutside
        val hitPoint = shellEntry(prev, pos, shellCenter, allowInsideStart = seen) ?: return null
        val normal = Vec3(hitPoint.x - shellCenter.x, hitPoint.y - shellCenter.y, hitPoint.z - shellCenter.z).toVector3f()
        if (normal.lengthSquared() < 1e-6f) return null
        normal.normalize()
        if (velocity.dot(normal) >= 0) return null             // 正在远离壳面：不是「打进来」

        val panelIndex = nearestPanelIndex(normal.toVec3()) ?: return null
        if (panelIndex in breachPanels) return null            // 破口方向：直接放行（连 MP 都不花）

        if (!payForBlock(level.gameTime, strength)) {
            // 护盾耗尽自身结构抵消破盾这一发；后续同方向攻击才从破口穿过。
            openBreach(level, holder, breachArea(panelIndex), strength)
            return ShellHit(hitPoint, normal)
        }

        // 挡下了：受击那块面板亮一下（玻璃是"盖章"式的，不跟人 —— 见第 36 轮口径）
        spawnGlassPanels(
            level, holder, listOf(panelIndex),
            edgeColor = GLASS_EDGE_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = HIT_HOLD_TICKS, fadeTicks = HIT_FADE_TICKS,
        )
        return ShellHit(hitPoint, normal)
    }

    /** JOML → MC 的向量转换（壳面法线两种库都要用：反射用 JOML，原版的 `move`/`reflect` 用 `Vec3`）。 */
    private fun Vector3f.toVec3(): Vec3 = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

    /**
     * **替持有者吃掉一次爆炸**（`ShieldGuard` 在 `Explosion` 结算伤害前调用）。
     *
     * @param falloffDamage 这颗爆炸**在持有者处**的衰减后伤害（`Explosion` 已算好）
     * @return true = 已抵扣（调用方跳过伤害与击退）；false = 不触发 / 已从破口穿过
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
            openBlastBreach(level, holder, panels, toBlast, strength)
            return true
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
     * - 直接伤害：只计实现 `DirectDamage` 的效果声明的强度。像 `bounce_duplicate` 那种碎片是
     *   `clone()` 出来的（保留宿主法术的 `status.damage`）但效果被换成 `Explosion + 加速度`，
     *   不判真实伤害效果就会给它们记一笔根本没打出来的伤害；
     * - 爆炸项：**带 `Explosion` 效果**时才计 `爆炸伤害 × 半径`。`MagicStatus` 的爆炸参数
     *   每个投射物身上都有默认值，盲加会给每一发火球凭空记 +10。
     */
    private fun strengthOf(p: Projectile): Double {
        var strength = 0.0
        strength += p.effects.filterIsInstance<DirectDamage>().sumOf { it.damageStrength.coerceAtLeast(0.0) }
        if (p.effects.any { it is Explosion }) {
            strength += p.status.explosionDamage.coerceAtLeast(0.0) *
                p.status.explosionRadius.coerceAtLeast(0f)
        }
        return strength
    }

    /**
     * **原版投射物**的强度（第 40 轮）：只认"真会打人"的那些，其余（钓钩、末影之眼…）返回 `null` = 护盾不管。
     *
     * - **箭矢 / 光谱箭**：`速度 × 2` —— 原版箭伤就是 `ceil(速度 × baseDamage)`，而 `baseDamage` 默认 **2.0**
     *   （`AbstractArrow.baseDamage` 是私有的、只有 `setBaseDamage`，所以这里按默认值估）；
     * - **三叉戟**：同一条速度公式，但原版基准是 8 → 用 [TRIDENT_DAMAGE_PER_SPEED]；
     * - **火球 / 骷髅头 / 风弹…**：直接取原版代码里的直击伤害（`SmallFireball` 5、`WitherSkull` 8…），
     *   大火的爆炸威力按一档估（`LargeFireball` 12）；
     * - **雪球 / 鸡蛋 / 药水**这类投掷物：**0** —— 挡下来只花基础 5 MP，但**值得挡**（比如伤害药水）；
     * - 没列出来的原版投射物一律不管（`else -> null`），免得"一个钓钩飞过来也要扣蓝"。
     */
    private fun vanillaStrength(p: VanillaProjectile): Double? = when (p) {
        is ThrownTrident -> p.deltaMovement.length() * TRIDENT_DAMAGE_PER_SPEED
        is AbstractArrow -> p.deltaMovement.length() * ARROW_DAMAGE_PER_SPEED
        is SmallFireball -> 5.0
        is WitherSkull -> 8.0
        is LargeFireball -> 12.0
        is DragonFireball -> 6.0
        is AbstractWindCharge -> 2.0
        is ShulkerBullet -> 4.0
        is LlamaSpit -> 1.0
        is FireworkRocketEntity -> 4.0
        is ThrowableItemProjectile -> 0.0
        else -> null
    }

    /** 返回真实球壳的入射点，仅供持续束按近远排序；格挡仍走正式结算。 */
    fun segmentEntry(holder: Entity, from: Vector3d, to: Vector3d): Vec3? =
        shellEntry(from, to, shellCenter(holder), allowInsideStart = false)?.let { Vec3(it.x, it.y, it.z) }

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
        from: Vector3d,
        to: Vector3d,
        center: Vec3,
        allowInsideStart: Boolean = false,
    ): Vector3d? {
        val fx = from.x - center.x
        val fy = from.y - center.y
        val fz = from.z - center.z
        if (fx * fx + fy * fy + fz * fz < SHELL_RADIUS * SHELL_RADIUS) {
            return if (allowInsideStart) Vector3d(from) else null
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
        return Vector3d(
            from.x + dx * t,
            from.y + dy * t,
            from.z + dz * t,
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
     * **打穿**（投射物 / 原版投射物路径）：把 [panels] 定为破口，碎裂时每片沿自己法线向外飘。
     *
     * @param strength 这一发的强度 —— 破口时长由它超出抗性的程度决定（[breachTicksFor]）
     */
    private fun openBreach(level: ServerLevel, holder: Entity, panels: List<Int>, strength: Double) {
        val ticks = markBreach(level, panels, strength)
        spawnGlassPanels(
            level, holder, panels,
            edgeColor = GLASS_SHATTER_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = SHATTER_HOLD_TICKS, fadeTicks = SHATTER_FADE_TICKS,
            driftOf = panelNormalDrift,
        )
        spawnBreachOutline(level, holder, panels, ticks)
    }

    /**
     * **打穿**（爆炸路径，第 39 轮）：破口区 = 这一发**波及到的全部面板**（可能整整 32 片），
     * 碎裂时整批沿爆点方向被吹开；朝爆点的片是白热的、**背对爆点的片暗一档**（用户口径「背面的片可以暗一档」）。
     *
     * 两批碎裂共用同一个方向向量 ⇒ 各占 1 个 PD 组，加上轮廓一共 3 个组（见 [spawnGlassPanels]）。
     */
    private fun openBlastBreach(level: ServerLevel, holder: Entity, panels: BlastPanels, toBlast: Vec3, strength: Double) {
        val ticks = markBreach(level, panels.all, strength)
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
        spawnBreachOutline(level, holder, panels.all, ticks)
    }

    /**
     * 破口（玩法）：[panels] 在 [breachTicksFor] 算出的时长内置为「不格挡」，并记下到期时间。
     *
     * @return 这次破口的时长（tick），破口轮廓的演出要跟它对齐
     */
    private fun markBreach(level: ServerLevel, panels: List<Int>, strength: Double): Int {
        val ticks = breachTicksFor(strength)
        breachPanels.clear()
        breachPanels += panels
        breachUntil = level.gameTime + ticks
        return ticks
    }

    /**
     * 破口轮廓：常驻整段破口期（最后 [BREACH_OUTLINE_FADE_TICKS] tick 淡出）的淡红提示线。
     *
     * **它跟随持有者移动**（第 40 轮用户口径：「修改红色的破盾视觉，跟随着被破盾的护盾持有者移动」）：
     * 判定跟着玩家走，破口的方向也是相对玩家算的 —— 红色轮廓留在原地就会与真正的破口错位。
     * 白色的碎片与格挡玻璃仍然是"盖章"式的（第 36 轮口径不变）。
     */
    private fun spawnBreachOutline(level: ServerLevel, holder: Entity, panels: List<Int>, ticks: Int) {
        spawnGlassPanels(
            level, holder, panels,
            edgeColor = BREACH_OUTLINE_COLOR, edgeScale = BREACH_OUTLINE_SCALE,
            withFill = false, holdTicks = max(1, ticks - BREACH_OUTLINE_FADE_TICKS),
            fadeTicks = BREACH_OUTLINE_FADE_TICKS, follow = true, glow = false,
        )
    }

    /** 实色晶格保持壳面尺寸；裂纹沿面心延伸，破裂片元独立外飞。
     * 破口轮廓随持有者移动，格挡残像和碎片留在受击位置。 */
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
        follow: Boolean = false,
        glow: Boolean = true,
    ) {
        val manager = wand.pdParticles(level)
        val center = shellCenter(holder)
        // ⚠️⚠️ PD 的 `ParticleGroup.followEntity(uuid, offset)` 不是"设成实体 + 偏移"，而是
        // **把 offset 加到当时的 pivot 上**（`pivot = offset + pivot`）。而客户端 arm 时算的是
        // `rel = 成员 spawn 位置 − pivot`（`ClientAnimationProgramManager.arm`），逐帧渲染成
        // `实体位置 + pivotEntityOffset + rel` —— offset 在 rel 里被减掉、又在渲染时加回来，
        // **净效果是零**。所以"跟随 + 抬到球心"只能靠 **pivot 取脚底**来实现：
        // `rel = (脚底 + 抬升 + 面板偏移) − 脚底 = 抬升 + 面板偏移` ⇒ 渲染位置正好是「球心 + 面板偏移」。
        //
        // 第 40 轮的初版正是在这里错的：pivot 用了球心、offset 传了抬升 ⇒ rel 少了抬升，
        // 跟着人走的红色破口轮廓整片**下沉约 1.1 格**（实机截图一眼可见）。
        // 顺序也仍是硬约束：**先建组（含 followEntity）再铺成员**（第 13 轮踩过）。
        val pivot = if (follow) holder.position() else center
        for ((drift, batch) in panelIndices.mapNotNull { shieldPanels.getOrNull(it) }.groupBy(driftOf)) {
            val group = if (drift == null) manager.createGroup(pivot) else null
            if (follow) group?.followEntity(holder.uuid)
            for (panel in batch) {
                val offsets = panel.ring + if (withFill) fillOffsets(panel) else emptyList()
                val normal = panel.center.normalize()
                offsets.forEachIndexed { i, offset ->
                    // 碎屑仅保留八分之一，格挡填充减至四分之一；完整轮廓保留。
                    if (drift != null && i % 8 != 0) return@forEachIndexed
                    if (drift == null && withFill && i >= panel.ring.size && i % 4 != 0) return@forEachIndexed
                    if (drift == null && withFill && i < panel.ring.size && i % 3 != 0) return@forEachIndexed
                    val edge = i < panel.ring.size
                    val handle = manager.create().position(center.add(offset))
                        .color(if (edge) edgeColor else fillColor)
                        .visual(work.nekow.particledrawing.api.ParticleVisual().style(work.nekow.particledrawing.api.ParticleStyle.SQUARE)
                            .anisoWorld(if (edge) edgeScale * 0.2f else 0.09375f, if (edge) edgeScale * 0.2f else 0.046875f)
                            .glowing(glow).spin(i * 0.47))
                        .lifetime(holdTicks + fadeTicks + if (drift != null) i % 13 else if (follow) -(i % 5) else i % 5)
                        .sizeCurve(*work.nekow.primalspells.magic.effect.visual.PixelVfx.lifeKeys(
                            holdTicks + fadeTicks + if (drift != null) i % 13 else if (follow) -(i % 5) else i % 5))
                        .spawn()
                    if (drift != null) {
                        // 保持片元尺寸，沿法线剥落并受重力；不会整块护盾缩到球心。
                        handle?.setVelocity(drift.scale(0.08).add(normal.scale(0.09 + i % 7 * 0.015))
                            .add(offset.subtract(panel.center).scale(0.12)).add(0.0, 0.05, 0.0))
                        handle?.applyForce(Vec3(0.0, -0.012, 0.0), -1)
                    } else group?.add(handle)
                }

            }
            group?.destroyAfter(holdTicks + fadeTicks + 13)
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
        // 物品提示显示单卡锚点；多卡运行时由strengthBudget按张数立方计算。
        lore(Lore.RESISTANCE_BUDGET, Lore.formatDouble(SKELETON_DPS * SKELETONS_HELD))
        lore(Lore.BOUNCE_COST, Lore.formatDouble(baseBounceCost))
        lore(Lore.EXTRA_COST, Lore.formatDouble(extraCostPerPoint))
        // 破口时长现在是「随超出抗性的程度」在 1 秒 ~ 3 秒之间（第 40 轮）
        lore(Lore.BREACH, "${baseBreachTicks}~${maxBreachTicks}")
    }

    companion object {
        // ---- 面板几何（与第 23 轮的玻璃护盾共用同一套截角二十面体） ----

        /** 球壳半径（方块）：既是表现面，也是**格挡判定面**与「壳内侧」的界线 */
        private const val SHELL_RADIUS = 1.7

        /** 原版投射物的搜索范围（方块）：以球心为中心、`SHELL_RADIUS + 本值` 的立方体内查实体 */
        private const val VANILLA_SWEEP_RANGE = 4.0

        /** 原版箭矢的伤害系数：`AbstractArrow.baseDamage` 默认 2.0（私有字段，只有 setter，故按默认值估） */
        private const val ARROW_DAMAGE_PER_SPEED = 2.0

        /** 原版三叉戟的伤害系数：`ThrownTrident` 的基准伤害是 8（同一条 `速度 × 系数` 公式） */
        private const val TRIDENT_DAMAGE_PER_SPEED = 8.0

        /** 超出抗性的部分最多按「抗性的这个倍数」计费（第 35 轮封顶，用户口径 = 2） */
        private const val MAX_EXTRA_RESISTANCE_RATIO = 2.0

        /** 窗口预算的时间窗（tick）：1 秒。用户口径「在一段时间内可以承担的强度」，追问定为 20 tick */
        private const val BUDGET_WINDOW_TICKS = 20

        /**
         * 数值锚：**一个原版骷髅（"小白"）的持续输出 ≈ 3.0 伤害/秒**（第 41 轮，用户口径
         * 「根据小白的 DPS 作为基础设计所有魔法数值」）。
         *
         * 推算全部来自原版代码：`RangedBowAttackGoal(this, 1.0, **20**, 15.0)` 是 20 tick 的攻击间隔，
         * 拉弓还要 20 tick（`pullTime >= 20` 才放）⇒ **2 秒一箭**；`BowItem.getPowerForTime(20) = 1.0`
         * ⇒ 箭初速 `power × 3.0 = 3.0`；`AbstractArrow.setBaseDamageFromMob(1.0)` ⇒ `baseDamage = 2 + 三角形抖动`
         * ⇒ 箭伤 `ceil(3.0 × ~2.2) ≈ 6`。⇒ **6 / 2 秒 = 3.0 DPS**。
         *
         * 全项目的数字都以它为基准折算（见 docs/design/combat-pricing.md §1）：玩家基础弹 ≈ 3 小白、
         * 护盾 ≈ 2 小白，等等。
         */
        const val SKELETON_DPS = 3.0

        /**
         * 护盾要扛住**几个小白**的持续输出：2 个（= 预算 6）。第 3 个就超预算 ⇒ 破口 ——
         * 这就是"护盾对小白不再无敌"的那条线。调它就等于调护盾的整体耐久。
         */
        const val SKELETONS_HELD = 2.0

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

        /** **破口轮廓**：常驻整段破口期，最后这点时间用来淡出（时长由 [breachTicksFor] 决定） */
        private const val BREACH_OUTLINE_FADE_TICKS = 6

        /** 实色蓝色点阵亮边，魔法全亮但不使用加法混合。 */
        private val GLASS_EDGE_COLOR: Color = Color.ofInt(120, 200, 255, 255)

        /** 碎裂那一下的亮边：更亮的蓝白（同一色系，只是更冲） */
        private val GLASS_SHATTER_COLOR: Color = Color.ofInt(190, 230, 255, 255)

        /** 爆炸碎裂里**背对爆点**那一批的亮边：同一块玻璃暗一档（不抢正面的镜） */
        private val GLASS_SHATTER_BACK_COLOR: Color = Color.ofInt(120, 180, 240, 255)

        /** 破口轮廓：淡红（提示攻击者「这里能打」），比玻璃暗、比背景亮 */
        private val BREACH_OUTLINE_COLOR: Color = Color.ofInt(255, 118, 118, 255)

        /** 实色蓝色方屑，采样只保留1/5，避免整片遮住视野。 */
        private val GLASS_FILL_COLOR: Color = Color.ofInt(60, 140, 255, 255)

        /** 背对爆点那一批的填充：比正面更淡 */
        private val GLASS_BACK_FILL_COLOR: Color = Color.ofInt(50, 120, 230, 255)
    }
}
