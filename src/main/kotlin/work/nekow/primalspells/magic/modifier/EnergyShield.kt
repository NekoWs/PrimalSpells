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
 * 能量盾（被动法术）：编入法杖后，只要法杖位于玩家手上（主手/副手）即自动生效。
 *
 * 为持有者提供一层能量护盾：进入护盾范围的敌方法术会被弹开（反射其速度），
 * 每次弹开消耗耐久；耐久归零后护盾破碎，暂停恢复一段时间（[breakPauseTicks]），
 * 之后以 [regenPerSecond] 的速度恢复耐久。
 *
 * ## 多张护盾卡：一层球壳 + 叠加耐久
 *
 * 同一根法杖上装几张 `energy_shield` 都**只出一层球壳**（否则球壳会重叠得很难看），
 * 但**耐久上限叠加**：容量 = 每张的 [maxDurability] × 卡数，共用一个耐久池。
 * 由 `loadedMagics` 里的**第一张**（[primaryShield]）负责球壳、弹开结算与 HUD，其余卡只做镜像；
 * 主卡被取下后，下一张自动接任（它下一 tick 就会铺出自己的球壳）。
 * ⚠️ 容量变大**不会白送耐久**（池子原样保留、只在超过新容量时才被钳下去）——
 * 否则「反复拔插第二张卡」就能刷满耐久。
 *
 * ## 持久化
 *
 * 当前耐久与破盾暂停剩余 tick 是**运行期状态**，随法杖存档持久化
 * （见 [saveRuntimeState]/[loadRuntimeState]）：重登/重启/被 `WandManager` 回收后再懒加载，
 * 护盾都不会回满（没读到耐久时按**当前容量**补满，见 [durabilityLoaded]）。
 * 数值与手感本身仍是待调项（`docs/issues/open.md` 1.1 —— 含 [regenPerSecond] 是否随卡数放大）。
 *
 * ## 视觉：五/六边形球体 + **逐块构建 / 逐块消散**（第 16 轮按实机反馈重做）
 *
 * 球壳是**截角二十面体**（足球：12 个五边形 + 20 个六边形，共 60 个顶点、90 条棱，几何见 [Polyhedra]），
 * **每块面板一个 [ParticleGroup]**，沿自己的边界铺一圈粒子（每条棱 [SHELL_POINTS_PER_EDGE] 个点）。
 *
 * 第 15 轮的展开/收起是「每块面板从球心飞出去 / 缩回球心」，实机反馈是**不好看**（整颗球在位移）。
 * 第 16 轮改成**球体位置固定、一块一块地出现与消失**：
 *
 * - **构建**：`updateShieldVisual` 每 tick 建 [BUILD_PANELS_PER_TICK] 块面板（各自的组 + 一圈粒子 +
 *   `fadeIn([BUILD_FADE_TICKS])`）；[shieldPanels] 已按**面板中心高度由低到高**排序，于是整颗球像被
 *   **从脚下往上搭起来**，共约 16 tick；
 * - **消散**：反向（最后搭起来的最先拆）把球壳**原地重建成一组新组**，每块各带
 *   `delay(阶梯) + fadeOut([FOLD_FADE_TICKS], removeAfter = true)`；
 *   新组的程序起点就是「现在」，所以阶梯与销毁时刻都精确（细节与 PD 侧的坑见 [foldShieldVisual]），
 *   且**即使本实例此后不再被 tick（法术被取下 / 法杖被回收）动画照样放完**，不留孤儿粒子；
 * - **不再使用 `move`**：面板不位移，也就不碰 PD 的「第一条指令不能是 `move`」那条坑
 *   （`move` 会改组的轴心字段，而客户端 arm 时拿它反算相对偏移，见 `docs/issues/pd-api-requests.md`）。
 *
 * 代价：32 个组意味着建立时要发 32 份程序包 + 每块面板一圈粒子的生成包；构建被摊到 16 tick 上
 * （每 tick 约 2 块面板），所以瞬时突发比第 15 轮小得多。稳态仍是 **0 包/tick**
 * （跟随 / 自转 / 淡入淡出都在客户端本地求值）。
 *
 * 不弹开持有者自己施放的法术（投射物出生点在施法者面前，会被瞬间弹回）。
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
     * 护盾球壳（ParticleDrawing）：**每个五/六边形面板一个粒子组**（见类注释「视觉」一节）。
     *
     * 用 `ParticleGroup`（**编排式动画**）而不是 `ParticleBatch`：面板成员固定、变换是整组统一的
     * 「跟随 + 自转 + 展开/收起」，这些在组里都由**客户端本地求值**（每 tick 零网络包），
     * 服务端只在建立与录制新指令时下发。
     */
    private val shell = ArrayList<ShellPanel>()

    /**
     * 球壳的一块面板：粒子组 + 它的几何（几何要留到消散时用，见 [foldShieldVisual]）。
     */
    private class ShellPanel(val group: ParticleGroup, val panel: Polyhedra.Panel)

    /** 已经建到第几块面板（0 = 还没开始）：构建被摊到多 tick 上，见 [buildShieldPanels]。 */
    private var buildCursor = 0

    /**
     * 球壳面板几何（截角二十面体：12 五边形 + 20 六边形，坐标**相对球心**，见 [Polyhedra]），
     * **按面板中心高度从低到高排序** —— 这个顺序就是构建波（由下往上搭）与消散波（由上往下拆）的顺序。
     */
    private val shieldPanels: List<Polyhedra.Panel> by lazy {
        Polyhedra.truncatedIcosahedron(SHELL_RADIUS, SHELL_POINTS_PER_EDGE).sortedBy { it.center.y }
    }

    /** [shell] 建立时所在的维度：跨维度后旧粒子组留在旧维度，需要重建（见 [updateShieldVisual]）。 */
    private var shellLevel: ServerLevel? = null

    /** [shell] 建立时锚定的实体：换实体（重生等）后 uuid 对不上，同样重建。 */
    private var shellHolder: UUID? = null

    /** 「未被持有」持续的 tick 数：达到 [SHELL_LINGER_TICKS] 就收起球壳（见 [onPassiveIdle]）。 */
    private var idleTicks = 0

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
     * 主卡负责球壳、弹开结算与 HUD 同步，其余卡只做镜像（见 [onInventoryTick]），不铺球壳也不结算。
     *
     * 顺序确定（`innate` + 槽位顺序），所以「谁是主卡」不需要额外状态：主卡被取下后，
     * 下一张自动接任（它下一 tick 就会铺出自己的球壳）。
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

        // 重新被持有：复位「放下倒计时」（见 onPassiveIdle）
        idleTicks = 0

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
        val shieldUp = durability > 0
        if (shieldUp) {
            updateShieldVisual(level, player)
        } else {
            // 破盾：球壳**收起**（面板向心收拢 + 淡出，PD 动画结束后自行销毁）；暂停恢复期间不再重建
            foldShieldVisual()
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
                durability = 0.0
                pauseTicks = breakPauseTicks
                break
            }
        }
        syncClient()
    }

    /**
     * 护盾视觉（ParticleDrawing）：以持有者为**轴心**的五/六边形球壳，**分多 tick 逐块构建**。
     *
     * 每 tick 调 [buildShieldPanels] 建一小批面板，直到 32 块建满（之后这个函数就是空转）。
     * 跨维度 / 换实体（重生）时**立即**销毁旧球壳再从头构建：旧面板组挂在旧实体上，淡出没有意义。
     */
    private fun updateShieldVisual(level: ServerLevel, holder: Entity) {
        if (shellLevel === level && shellHolder == holder.uuid) {
            buildShieldPanels(level, holder) // 还在构建中：继续搭下一批（已建满则直接返回）
            return
        }
        clearShieldVisual()
        shellLevel = level
        shellHolder = holder.uuid
        buildCursor = 0
        buildShieldPanels(level, holder)
    }

    /**
     * 建下一批面板（[BUILD_PANELS_PER_TICK] 块）：每块一个组 —— 先绑轴心、再铺成员，最后录
     * `fadeIn` + `spin` 两条指令（两条都不挪组的轴心字段，满足 PD 的 arm 基准约束，见类注释）。
     *
     * 成员坐标 = 「绑定后的轴心（= 球心）+ 面板边界偏移」，`offsetFromPivot` 用**相对球心**的偏移：
     * 客户端 arm 时按「成员 spawn 位置 − 当时的组 `pivot` 字段」重算相对偏移，而 `followEntity(offset)`
     * 会顺手改那个字段 —— 所以**先 `followEntity` 再铺成员**是硬要求（第 13 轮踩过，见 `resolved.md`）。
     * 面板位置固定不动（不做位移动画），所以不会再碰到 `move` 相关的坑。
     */
    private fun buildShieldPanels(level: ServerLevel, holder: Entity) {
        if (buildCursor >= shieldPanels.size) return
        val manager = wand.pdParticles(level)
        val bodyOffset = shellCenterOffset(holder)
        val feet = holder.position()
        val center = feet.add(0.0, bodyOffset, 0.0)
        val end = min(shieldPanels.size, buildCursor + BUILD_PANELS_PER_TICK)

        for (index in buildCursor until end) {
            val group = manager.createGroup(feet)
            group.followEntity(holder.uuid, Vec3(0.0, bodyOffset, 0.0))
            for (offset in shieldPanels[index].ring) {
                manager.create()
                    .position(center.add(offset))
                    .color(SHELL_COLOR)
                    .scale(SHELL_SCALE)
                    .lifetime(PD_IMMORTAL)
                    .group(group.id)
                    .offsetFromPivot(offset)
                    .spawn()
            }
            // 第一条指令必须是「不挪轴心」的：fadeIn 只做透明度编排，arm 时轴心仍是球心
            group.fadeIn(BUILD_FADE_TICKS, EasingType.EASE_OUT)
            group.spin(SHELL_SPIN_AXIS, SHELL_SPIN_RADIANS_PER_TICK)
            shell += ShellPanel(group, shieldPanels[index])
        }
        buildCursor = end
    }

    /**
     * 消散动画：**反向**（最后搭起来的最先拆）逐块 `fadeOut`，每块之间用 `delay` 错开一个阶梯。
     *
     * `fadeOut(removeAfter = true)` 会在淡出结束后由 PD 排任务销毁整组，所以这里把引用丢掉即可 ——
     * **即使本实例之后不再被 tick（法术被取下 / 法杖被回收）动画也能放完**，且不留孤儿粒子。
     *
     * ⚠️ 用 `delay` 做阶梯在这里是安全的：`fadeOut` 在指令生效前默认「还没开始淡出」（因子 0，不影响 alpha），
     * 所以后面的面板在被轮到之前保持完全可见；而 `fadeIn` 的默认值相反（1），用它做阶梯会「先闪一下」。
     */
    private fun foldShieldVisual() {
        if (shell.isEmpty()) return
        val level = shellLevel
        val holderUuid = shellHolder
        val holder = level?.let { holderUuid?.let(it::getEntity) }
        // 拿不到「当初那块地方」时只能硬销毁（例如持有者已下线）：宁可瞬间消失，也不留孤儿粒子
        if (level == null || holderUuid == null || holder == null) {
            for (p in shell) p.group.remove()
            shell.clear()
            buildCursor = 0
            shellLevel = null
            shellHolder = null
            idleTicks = 0
            return
        }

        // ⚠️ 为什么是「原地重建」而不是直接给旧组 `delay + fadeOut`：
        // 组的指令时间游标（cursorMs）是**从建组时刻起累加的绝对时间**，
        // 而 `fadeOut` 的 `removeAfter` 又用这个绝对游标去排**相对 now** 的销毁任务（PD 侧两端口径不一致）。
        // 于是「建组很久之后再追加淡出」这件事：客户端把它当作早已过去的时刻 → 瞬间淡完；
        // 若把游标推到 now 让客户端正确，服务端销毁又会被推后整段已运行时长（留下看不见的粒子）。
        // 见 `docs/issues/pd-api-requests.md`。这里的绕法是用**刚建的新组**下发淡出：
        // 新组的程序起点就是 now，`delay` 与 `fadeOut(removeAfter)` 两个口径同时成立。
        // 代价是消散时一次性重铺一遍球壳（与构建时同量级的一次性突发），换来精确的逐块淡出 + 自清理。
        val manager = wand.pdParticles(level)
        val bodyOffset = shellCenterOffset(holder)
        val feet = holder.position()
        val center = feet.add(0.0, bodyOffset, 0.0)
        for ((order, entry) in shell.reversed().withIndex()) {
            entry.group.remove()
            val fading = manager.createGroup(feet)
            fading.followEntity(holderUuid, Vec3(0.0, bodyOffset, 0.0))
            for (offset in entry.panel.ring) {
                manager.create()
                    .position(center.add(offset))
                    .color(SHELL_COLOR)
                    .scale(SHELL_SCALE)
                    .lifetime(PD_IMMORTAL)
                    .group(fading.id)
                    .offsetFromPivot(offset)
                    .spawn()
            }
            fading.delay((order / FOLD_PANELS_PER_STEP) * FOLD_STAGGER_TICKS)
            fading.fadeOut(FOLD_FADE_TICKS, removeAfter = true)
        }
        shell.clear()
        buildCursor = 0
        shellLevel = null
        shellHolder = null
        idleTicks = 0
    }

    /**
     * 被动「未被持有」时的每 tick 回调（由 `Wand.tick` 的闲置分支驱动）。
     *
     * 放下法杖后**保留 [SHELL_LINGER_TICKS] tick** 再开始消散球壳 —— 这段窗口是给「刚放下又马上拿起来」
     * 留的缓冲（重新握杖时 [onInventoryTick] 会把倒计时归零，还没拆完的面板会连同构建进度一起重建）。
     */
    override fun onPassiveIdle() {
        if (shell.isEmpty()) return
        idleTicks++
        if (idleTicks >= SHELL_LINGER_TICKS) foldShieldVisual()
    }

    /**
     * 从法杖上取下（或槽位被替换）时调用：**立刻开始消散球壳**。
     *
     * 必须做：取下之后本实例不再出现在 `Wand.loadedMagics` 里，[onInventoryTick] 与 [onPassiveIdle]
     * **都不会再被调用** —— 而面板是 `lifetime = -1` 的永存粒子组，不在这里处理就会一直留在世界里。
     * 消散动画不依赖本实例继续 tick（销毁由 PD 的调度器负责），所以取下后动画照样能放完。
     * 若取下的只是「多张护盾卡」中的一张，接任的主卡会重新构建自己的球壳。
     */
    override fun onUnloaded() {
        foldShieldVisual()
    }

    /** 立即销毁整个球壳（不播动画）：跨维度/换实体/重建时用 —— 旧面板挂在旧实体上，动画没有意义。 */
    private fun clearShieldVisual() {
        for (p in shell) p.group.remove()
        shell.clear()
        buildCursor = 0
        shellLevel = null
        shellHolder = null
        idleTicks = 0
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

        // ---- 球壳（ParticleDrawing）：五/六边形面板 ----

        /**
         * 球壳半径（方块）：`followEntity` 的偏移相对脚底，球心还要再加「身体中段」那一段。
         */
        private const val SHELL_RADIUS = 1.7

        /**
         * 每条棱上放几个点（含起点，见 [Polyhedra.truncatedIcosahedron]）：
         * 棱长 0.686 格 ÷ 本值 = 点间距，点间距要 ≲ [SHELL_SCALE] 才连得成实线。
         * 3 → 点距 0.229、共 540 颗（第 16 轮从 2 提到 3：太稀时看不出五/六边形）；4 → 点距 0.171、720 颗。
         */
        private const val SHELL_POINTS_PER_EDGE = 3

        /**
         * 单颗粒子尺寸：点距 0.229 时，**0.3 左右刚好连成一条细线**（离线投影对比过：
         * 0.4 → 棱变成粗管、0.25 → 略稀但最清晰、0.3 → 兼顾实线与细度）。
         */
        private const val SHELL_SCALE = 0.3f

        /** 每 tick 构建几块面板：32 ÷ 本值 = 构建波的总时长（2 → 16 tick ≈ 0.8 秒） */
        private const val BUILD_PANELS_PER_TICK = 2

        /** 单块面板的淡入时长（tick）：与构建波叠加，最后一块在 16+6 tick 后完全出现 */
        private const val BUILD_FADE_TICKS = 6

        /** 消散时每几块面板共用同一个起始时刻：32 ÷ 本值 × [FOLD_STAGGER_TICKS] ≈ 消散波总时长 */
        private const val FOLD_PANELS_PER_STEP = 2

        /** 消散波相邻两级的间隔（tick） */
        private const val FOLD_STAGGER_TICKS = 1

        /** 单块面板的淡出时长（tick）：PD 在淡出结束后销毁该组 */
        private const val FOLD_FADE_TICKS = 5

        /**
         * 自转轴：略微倾斜（不是纯 Y 轴）。
         *
         * 纯 Y 轴下粒子只沿纬线滑动，倾斜一点会让它们走出大圆、更像「能量场在流动」。
         * 想改成「只绕竖直轴转」就把这里换成 `Vec3(0.0, 1.0, 0.0)`。
         */
        private val SHELL_SPIN_AXIS: Vec3 = Vec3(0.25, 1.0, 0.12).normalize()

        /**
         * 自转速度（弧度/tick）：`PI / 60 ≈ 3°/tick`，约 **6 秒一圈**。
         *
         * 调大 = 转得更快（`PI / 30` 约 3 秒一圈）；调小 = 更慢。组级 spin 是客户端本地累加，
         * 改这个值只影响观感，不增加带宽。
         */
        private const val SHELL_SPIN_RADIANS_PER_TICK = PI / 60.0

        /**
         * 放下法杖后球壳保留的 tick 数（= [onPassiveIdle] 的倒计时阈值）：
         * 先等这么久（防止「放下又立刻拿起」闪一下），再开始消散动画。
         */
        private const val SHELL_LINGER_TICKS = 4

        /** 淡蓝色 */
        private val SHELL_COLOR: Color = Color.ofInt(120, 200, 255)
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
