package work.nekow.primalspells.magic.modifier

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector3f
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleGroup
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.network.SyncShieldPayload
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.util.PD_IMMORTAL
import work.nekow.primalspells.util.Polyhedra
import work.nekow.primalspells.util.pdParticles
import java.util.UUID
import kotlin.math.PI
import kotlin.math.min

/**
 * 能量护盾（修饰器）：把**敌方法术**弹开并消耗耐久；耐久耗尽进入破盾暂停，暂停结束自动回复。
 *
 * ## 玩法（数值与判定：未随第 23 轮的视觉重做改动）
 *
 * - 逐 tick 扫描场上的本模组投射物：进入 `shieldRadius` 外扩的玩家包围盒、且**正朝玩家飞来**时，
 *   以玩家中心为法线**反射速度**、并把投射物推出 0.05 格（下一 tick 必然离开判定范围，不会反复触发）；
 * - 每次弹开扣 `bounceCost` 耐久；耐久归零 → `breakPauseTicks` 冻结（期间既不回复也不弹开）；
 * - 耐久上限 = `maxDurability` × 本杖护盾卡数，回复 `regenPerSecond`/秒；
 * - **不弹自己施放的法术**（投射物出生在施法者面前，否则出生即被弹回）；
 * - 摔落/中毒/虚空这类**没有投射物**的伤害护盾不响应（既不扣耐久也不弹开）—— 既有口径，不是缺口。
 *
 * ## 视觉（第 23 轮按实机反馈重做成「受击处展开的玻璃护盾」）
 *
 * 旧版是**常驻的足球状球壳 + 自转 + 逐块构建/消散**。实机反馈两条：**太挡视野**、
 * **一直在转容易让人晕 3D**。现在改成（三轮确认后的设计）：
 *
 * - **平时完全不可见** —— 屏幕上没有任何常驻粒子，「护盾在不在」由 HUD 盾条告知；
 * - **格挡生效时**（投射物被弹开、扣了耐久）在**受击方向上最近的那一块面板**处展开一块**玻璃板**：
 *   亮边 + 极淡填充（≈15% 不透明度），约 0.35 秒淡出；每次命中各出一片、不限片数；
 * - **破盾**（耐久归零）→ 整颗玻璃罩现形**一闪而碎**（面板沿各自法线向外飘散 + 淡出）；
 * - **护盾恢复**（暂停结束重新上线）→ 全身轮廓**极淡地闪一下**；
 * - **不再自转**（晕 3D 的根源），于是也没有「分批建组要补自转相位」那个问题（第 22 轮）——
 *   整壳只在破盾/恢复两个瞬间**整体**出现，天然同相位。
 *
 * 三处玻璃都挂在持有者身上（`followEntity`，随人移动），淡出结束由 PD 自行销毁
 * （`fadeOut(removeAfter = true)`），所以**本实例此后不再被 tick（法杖被收起/回收）也不留孤儿粒子**
 * —— 因此旧版那套「放下法杖后延迟收起球壳」的处理整个不再需要。
 */
class EnergyShield : Modifier() {
    override val id = "energy_shield"

    /** 最大耐久 */
    var maxDurability: Double = 100.0

    /** 耐久回复速度（每秒） */
    var regenPerSecond: Double = 10.0

    /** 破盾后暂停恢复时长（tick） */
    var breakPauseTicks: Int = 50

    /** 每次弹开法术消耗的耐久 */
    var bounceCost: Double = 5.0

    /** 弹开检测半径（玩家包围盒外扩距离） */
    var shieldRadius: Double = 1.5

    /** 当前耐久 */
    var durability: Double = maxDurability

    /** 破盾暂停剩余 tick */
    var pauseTicks: Int = 0

    /**
     * **上一 tick 护盾是否在线**：只用来捕捉「破盾」与「恢复」两个状态翻转的瞬间
     * （翻转的那一 tick 才播整罩玻璃动画）。第一次结算时按当时的耐久补种，避免读档进游戏误播一次。
     */
    private var shieldWasUp = true

    /** [shieldWasUp] 是否已按实际耐久补种过。 */
    private var shieldStateInitialised = false

    /**
     * 面板几何（截角二十面体：12 五边形 + 20 六边形，坐标**相对球心**、长度 = [SHELL_RADIUS]，见 [Polyhedra]）。
     *
     * 第 23 轮起不再拿来铺常驻球壳，而是当**方向索引**用：
     * - 受击时用 `maxByOrNull { it.center.dot(受击方向) }` 找最近的那一块，只展开那一块玻璃板；
     * - 破盾 / 恢复时整体遍历一遍，铺出「整罩」的玻璃。
     * `sortedBy { center.y }` 保留着（由下往上），是为了破盾/恢复时粒子生成顺序稳定、观感一致。
     */
    private val shieldPanels: List<Polyhedra.Panel> by lazy {
        Polyhedra.truncatedIcosahedron(SHELL_RADIUS, SHELL_POINTS_PER_EDGE).sortedBy { it.center.y }
    }


    /**
     * 是否已从存档读到过耐久（`durability` 键存在）。
     *
     * 没读到（旧档 / 首次入库 / 新装）时**首次结算按当前容量补满** —— 容量随护盾卡数量变化
     * （见 [shieldCapacity]），所以「补满」不能在 [loadRuntimeState] 里做（那里拿不到 `wand`）。
     */
    private var durabilityLoaded = false

    /**
     * 本杖上的**主卡**：`loadedMagics` 里第一张护盾卡。
     *
     * 多张护盾卡**只出一层球壳、共用一个耐久池**（容量 = 每张的 [maxDurability] × 卡数）：
     * 主卡负责弹开结算、玻璃表现与 HUD 同步，其余卡只做镜像（见 [onInventoryTick]），不铺粒子也不结算。
     *
     * 顺序确定（`innate` + 槽位顺序），所以「谁是主卡」不需要额外状态：主卡被取下后，
     * 下一张自动接任（它下一 tick 就会照常结算与出玻璃板）。
     */
    private fun primaryShield(): EnergyShield? =
        wand.loadedMagics.firstOrNull { it is EnergyShield } as? EnergyShield

    /** 本杖的护盾耐久上限 = 每张卡的 [maxDurability] × 护盾卡数量（「额外护盾叠加耐久度」）。 */
    private fun shieldCapacity(): Double = maxDurability * wand.loadedMagics.count { it is EnergyShield }

    /**
     * 运行期状态持久化（写）：把「当前耐久」与「破盾暂停剩余 tick」交给法杖存档。
     *
     * 这两个字段是仅有的、无法由构造参数重算的护盾进度 —— 不入库就会出现
     * 「重登/重启后护盾回满、破盾暂停被清空」（原 `docs/known-issues.md` §1.1 缺口 1，现已解决）。
     *
     * 数值本身（[maxDurability]/[regenPerSecond]/[breakPauseTicks]/[bounceCost]/[shieldRadius]）
     * 是构造参数，由代码决定，不入库。
     *
     * @param tag 空 NBT，写 `durability`（double）与 `pause_ticks`（int）
     */
    override fun saveRuntimeState(tag: CompoundTag) {
        tag.putDouble("durability", durability)
        tag.putInt("pause_ticks", pauseTicks)
    }

    /**
     * 运行期状态持久化（读）：恢复 [durability] 与 [pauseTicks]。
     *
     * ⚠️ 本钩子由 `Wand.fromTag` 在读档期间调用，此时 `wand`/`caster`/`status` **还不能访问**
     * （见 `Magic.loadRuntimeState` 的约定），所以这里只碰自己的字段。
     * 恢复后的值不用主动同步客户端：法杖回到手上后 [onInventoryTick] 第一次 [syncClient]
     * 就会把恢复值推给 HUD（`lastSent` 初始值是空壳 payload，必然不同）。
     *
     * @param tag 存档状态；键缺失时记录「未读到」标记，由 [onInventoryTick] 首次结算补满到**当前容量**
     */
    override fun loadRuntimeState(tag: CompoundTag) {
        durabilityLoaded = tag.contains("durability")
        // 上限不在读档时钳制（此时拿不到 wand、算不出「卡数 × maxDurability」）：
        // 只保证非负，上限交给每 tick 的 [shieldCapacity] 钳制。
        durability = tag.getDoubleOr("durability", maxDurability).coerceAtLeast(0.0)
        pauseTicks = tag.getIntOr("pause_ticks", 0).coerceAtLeast(0)
    }

    override var needInventory: Boolean = true

    /**
     * 每 tick 维护护盾。
     *
     * **多张护盾卡**：只有[主卡][primaryShield]在这里干活（球壳 / 弹开 / 回复 / HUD），
     * 其余卡把主卡的进度镜像过来后直接返回 —— 这样「同一法术 id 只有一份存档状态」
     * （`Wand.magicStates` 按 id 存，保存时同 id 的卡互相覆盖）在任何一张卡上写的值都一致。
     */
    override fun onInventoryTick() {
        val owner = primaryShield()
        if (owner != null && owner !== this) {
            durability = owner.durability
            pauseTicks = owner.pauseTicks
            durabilityLoaded = owner.durabilityLoaded
            return
        }

        val player = caster
        if (!player.isAlive) return
        val level = player.level()
        if (level !is ServerLevel) return

        // 耐久上限随「本杖有几张护盾卡」变化；首次结算（旧档/新装）直接补满到当前容量
        val capacity = shieldCapacity()
        if (!durabilityLoaded) {
            durability = capacity
            durabilityLoaded = true
        } else {
            durability = durability.coerceIn(0.0, capacity)
        }

        // 耐久回复（破盾暂停期间不回复）
        if (pauseTicks > 0) {
            pauseTicks--
        } else {
            durability = min(capacity, durability + regenPerSecond / 20.0)
        }
        // 护盾平时**不可见**：只在「破盾」与「恢复」两个状态翻转的瞬间播一次整罩玻璃动画
        val shieldUp = durability > 0
        if (!shieldStateInitialised) {
            shieldWasUp = shieldUp
            shieldStateInitialised = true
        } else if (shieldUp != shieldWasUp) {
            if (shieldUp) spawnRecoveryFlash(level, player) else spawnBreakFlash(level, player)
            shieldWasUp = shieldUp
        }

        if (!shieldUp) {
            syncClient()
            return // 护盾破碎：不弹开
        }

        // 剪枝 1：场上没有投射物时不必做任何遍历
        if (MagicManager.projectiles.isEmpty()) {
            syncClient()
            return
        }

        // 检测并弹开进入护盾范围的敌方法术（自身法术不弹，避免出生即被弹回）。
        // 注：MagicManager 中的投射物均由 castHand/Trigger 设置 caster 后才加入，故可直接访问。
        val center = player.position().toVector3f()
        val aabb = player.getBoundingBox().inflate(shieldRadius)
        for (p in MagicManager.projectiles) {
            if (!p.alive) continue
            if (p.hitRadius <= 0.0) continue            // 不碰撞实体的法术（爆炸点等）不弹
            if (p.caster === player) continue
            val pos = p.status.pos
            // 剪枝 2：先用「到玩家中心的距离」粗筛，把全场法术过滤成极少数候选；
            // 半径取 护盾半径 + 玩家包围盒最远角(1.0) + 该投射物自身碰撞半径，
            // 保证粗筛永远不比下面的精确 AABB 判定更严格（不会漏判）。
            val pruneRadius = shieldRadius + PLAYER_BOX_MAX_HALF_EXTENT + p.hitRadius
            if (pos.distanceSquared(center) > (pruneRadius * pruneRadius).toFloat()) continue
            if (!aabb.inflate(p.hitRadius).contains(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())) continue

            // 弹开：以玩家中心为法线反射投射物速度；仅弹开正在靠近玩家的法术
            val normal = Vector3f(pos).sub(center)
            if (normal.lengthSquared() < 1e-4) continue
            normal.normalize()
            if (p.status.velocity.dot(normal) >= 0) continue
            val speed = p.status.velocity.length()
            if (speed < 1e-4) continue
            p.status.velocity.normalize().reflect(normal).mul(speed)
            p.status.pos = Vector3f(normal).mul(0.05f).add(pos)

            // 弹开命中粒子（密集爆闪，便于观察弹开效果）
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                14, 0.3, 0.3, 0.3, 0.12
            )
            level.sendParticles(
                ParticleTypes.END_ROD,
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                5, 0.15, 0.15, 0.15, 0.02
            )

            durability -= bounceCost
            if (durability <= 0) {
                // 破盾的那一发不再出受击玻璃：整罩「一闪而碎」接手（由上面的状态翻转触发）
                durability = 0.0
                pauseTicks = breakPauseTicks
                break
            }
            // 格挡真的生效（扣了耐久）才在受击处展开玻璃板 —— 不放「表演性」的反应
            spawnHitPanel(level, player, Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()))
        }
        syncClient()
    }

    // ---------------------------------------------------------------- 玻璃护盾（第 23 轮）

    /**
     * **格挡生效**：在受击方向上**最近的那块面板**处展开一块玻璃板。
     *
     * @param hitPos 受击点（投射物的世界坐标 —— 弹开判定已经算过，直接用，不额外换算）
     */
    private fun spawnHitPanel(level: ServerLevel, holder: Entity, hitPos: Vec3) {
        val center = holder.position().add(0.0, shellCenterOffset(holder), 0.0)
        val dir = hitPos.subtract(center)
        if (dir.lengthSqr() < 1e-6) return
        // 面板的 `center` 就是「相对球心、长度 = 半径」的方向向量（见 [Polyhedra] 的 KDoc），
        // 所以「给定方向找最近面板」= 点积取最大。护盾不再自转，因此不需要反旋相位（第 22 轮那套已删）。
        val unit = dir.normalize()
        val panel = shieldPanels.maxByOrNull { it.center.dot(unit) } ?: return
        spawnGlassPanel(
            level, holder, panel,
            edgeColor = GLASS_EDGE_COLOR, edgeScale = GLASS_EDGE_SCALE,
            withFill = true, holdTicks = HIT_HOLD_TICKS, fadeTicks = HIT_FADE_TICKS, drift = null,
        )
    }

    /** **破盾**瞬间：整颗玻璃罩现形一闪，随即面板沿各自法线向外飘散、淡出。 */
    private fun spawnBreakFlash(level: ServerLevel, holder: Entity) {
        for (panel in shieldPanels) {
            spawnGlassPanel(
                level, holder, panel,
                edgeColor = GLASS_BREAK_COLOR, edgeScale = GLASS_EDGE_SCALE,
                withFill = true, holdTicks = BREAK_HOLD_TICKS, fadeTicks = BREAK_FADE_TICKS,
                drift = panel.center.normalize().scale(BREAK_DRIFT),
            )
        }
    }

    /** **护盾恢复**：整壳轮廓极淡地闪一下（不带填充 —— 否则又变成一层看得见的罩子）。 */
    private fun spawnRecoveryFlash(level: ServerLevel, holder: Entity) {
        for (panel in shieldPanels) {
            spawnGlassPanel(
                level, holder, panel,
                edgeColor = GLASS_RECOVERY_COLOR, edgeScale = GLASS_RECOVERY_SCALE,
                withFill = false, holdTicks = RECOVERY_HOLD_TICKS, fadeTicks = RECOVERY_FADE_TICKS,
                drift = null,
            )
        }
    }

    /**
     * 铺一块玻璃面板：**亮边**（面板轮廓上的点）+ 可选**极淡填充**（面板内部的稀疏点），
     * 挂在持有者身上，[holdTicks] 后开始淡出、[fadeTicks] 内淡完并由 PD 销毁整组。
     *
     * ⚠️ 顺序：**先 `followEntity` 再铺成员** —— 客户端 arm 时按「成员 spawn 位置 − 组 `pivot` 字段」
     * 反算相对偏移，而 `followEntity` 会顺手改那个字段（第 13 轮踩过）；成员用**相对球心**的偏移
     * （`offsetFromPivot`），于是面板随人移动、形状刚性。
     * ⚠️ 指令顺序：`delay` 必须录在 `fadeOut` 之前（游标语义），而本组是**新建**的 ——
     * 游标从 0 起算 = 从现在起（PD 1.0.12 的增量语义），所以这里不需要任何时钟补偿。
     */
    private fun spawnGlassPanel(
        level: ServerLevel,
        holder: Entity,
        panel: Polyhedra.Panel,
        edgeColor: Color,
        edgeScale: Float,
        withFill: Boolean,
        holdTicks: Int,
        fadeTicks: Int,
        drift: Vec3?,
    ) {
        val manager = wand.pdParticles(level)
        val bodyOffset = shellCenterOffset(holder)
        val feet = holder.position()
        val center = feet.add(0.0, bodyOffset, 0.0)
        val group = manager.createGroup(feet)
        group.followEntity(holder.uuid, Vec3(0.0, bodyOffset, 0.0))
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
        if (drift != null) group.move(drift, fadeTicks, EasingType.EASE_OUT)
        group.delay(holdTicks)
        // EASE_OUT：透明度先快后慢 —— 长尾才像「碎开后飘散」，EASE_IN 会在最后一帧啪一下消失
        group.fadeOut(fadeTicks, removeAfter = true, easing = EasingType.EASE_OUT)
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

    /** 球壳球心相对脚底的偏移：身体中段（身高 55%），比眼睛位置更贴近「包裹全身」的观感。 */
    private fun shellCenterOffset(holder: Entity): Double = holder.bbHeight * 0.55

    /**
     * 同步护盾状态到持有者客户端（仅状态变化时发包，避免每 tick 空包）。
     *
     * `maxDurability` 字段传的是**本杖的护盾容量**（= 每张 [maxDurability] × 卡数），
     * HUD 按 `durability / maxDurability` 画条，所以多张护盾卡时条会自动按新容量显示。
     */
    private fun syncClient() {
        val player = caster as? ServerPlayer ?: return
        val payload = SyncShieldPayload(wand.id, durability, shieldCapacity(), pauseTicks, breakPauseTicks)
        if (payload == lastSent) return
        lastSent = payload
        PacketDistributor.sendToPlayer(player, payload)
    }

    private var lastSent = SyncShieldPayload("", -1.0, -1.0, -1, -1)

    companion object {
        /**
         * 玩家碰撞箱相对其位置中心的最大半边长（宽 0.6 / 高 1.8 → 最远角 ≈ 1.0）。
         * 用于把「点到中心距离」的球面粗筛放宽到绝不比精确 AABB 判定更严格。
         */
        private const val PLAYER_BOX_MAX_HALF_EXTENT = 1.0

        // ---- 面板几何（玻璃护盾与旧的常驻球壳共用同一套截角二十面体） ----

        /** 球壳半径（方块）：面板方向向量的长度，也就是玻璃板离球心的距离 */
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

        // ---- 玻璃板（受击 / 破盾 / 恢复三种时机共用一套铺法） ----

        /** 亮边的粒子尺寸：边缘细一点才像玻璃的棱 */
        private const val GLASS_EDGE_SCALE = 0.26f

        /**
         * 填充的粒子尺寸：比边缘大、**极淡**（alpha ≈15%），靠重叠叠出一层均匀薄雾。
         * 0.55 → 约 0.12 格，与填充点距同量级，刚好连成一片。
         */
        private const val GLASS_FILL_SCALE = 0.55f

        /** 填充采样：隔几个边界点采一条射线 / 每条射线上取哪几个比例 */
        private const val FILL_STRIDE = 2
        private val FILL_FRACTIONS = doubleArrayOf(0.62, 0.88)

        /** **受击**玻璃：停留 + 淡出 ≈ 0.35 秒 */
        private const val HIT_HOLD_TICKS = 2
        private const val HIT_FADE_TICKS = 5

        /** **破盾**：一闪之后面板向外飘散 + 淡出 ≈ 0.5 秒；[BREAK_DRIFT] 是飘散距离（方块） */
        private const val BREAK_HOLD_TICKS = 2
        private const val BREAK_FADE_TICKS = 12
        private const val BREAK_DRIFT = 0.35

        /** **恢复**：极淡轮廓闪一下 ≈ 0.35 秒（不带填充） */
        private const val RECOVERY_HOLD_TICKS = 1
        private const val RECOVERY_FADE_TICKS = 6
        private const val GLASS_RECOVERY_SCALE = 0.22f

        /** 亮边颜色：淡蓝白（比旧的常驻球壳更亮 —— 它只在受击那一瞬出现） */
        private val GLASS_EDGE_COLOR: Color = Color.ofInt(190, 232, 255, 235)

        /** 破盾那一下的亮边：更白 */
        private val GLASS_BREAK_COLOR: Color = Color.ofInt(226, 246, 255, 245)

        /** 恢复时的亮边：很淡（只是「闪一下」） */
        private val GLASS_RECOVERY_COLOR: Color = Color.ofInt(140, 200, 255, 95)

        /** 填充：极淡的淡蓝（≈15% 不透明度） */
        private val GLASS_FILL_COLOR: Color = Color.ofInt(150, 205, 255, 38)
    }

    /**
     * 能量盾的 tooltip：基础信息用 `super`（法术名/法力/延迟/充能），再补护盾专属数值。
     *
     * 这里列的是护盾的**数值设定**（上限耐久/回复/破盾暂停/弹开消耗），不含当前耐久
     * ——当前耐久是每根法杖各自的运行期状态，只由服务端掌握，客户端 tooltip 拿不到权威值，
     * 它显示在 HUD 上（`SyncShieldPayload` → `client/hud/ShieldHudRenderer`）。
     *
     * 耐久持久化已完成：「重登/重启后护盾回满」这条缺口（原 `docs/known-issues.md` §1.1 缺口 1）
     * 由 [saveRuntimeState]/[loadRuntimeState] 解决，见那两个钩子的 KDoc。
     * 剩下的缺口只有**数值与手感**（反射强度/破盾暂停/耐久曲线/粒子量）尚未调平（同节缺口 2、3）。
     */
    override fun initLore() {
        super.initLore()
        lore(Lore.DURABILITY, Lore.formatDouble(maxDurability))
        lore(Lore.REGEN, Lore.formatDouble(regenPerSecond))
        lore(Lore.BREAK_PAUSE, breakPauseTicks)
        lore(Lore.BOUNCE_COST, Lore.formatDouble(bounceCost))
    }
}
