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
 * ## 视觉：五/六边形拼成的球壳 + 展开 / 收起动画（第 15 轮重做）
 *
 * 球壳不再是「均匀撒点的球」，而是**截角二十面体**（足球：12 个五边形 + 20 个六边形，共 60 个顶点、90 条棱，
 * 几何见 [Polyhedra]）：每个面板一块 [ParticleGroup]，沿自己的边界铺一圈粒子。
 * 用**一组面板各自成组**换来的能力是「一块一块地动」——组级编排只能整组平移/旋转/淡入淡出，
 * 单组做不出「各面板从球心飞向自己的位置」：
 *
 * - **展开**：每块面板先瞬时折叠到球心（`move(-面板中心偏移, 0)`），再 `move(面板中心偏移, [UNFOLD_TICKS])`
 *   用 [EasingType.EASE_OUT] 飞回球面，同时整组 `fadeIn` —— 观感是「能量从身体里炸开、凝成球壳」；
 * - **稳态**：`spin` 持续自转（客户端本地求值，**0 包/tick**）；
 * - **收起**：`move(-面板中心偏移, [FOLD_TICKS], EASE_IN)` 向心收拢 + `fadeOut(removeAfter = true)`
 *   —— 面板缩回身体、边缩边淡出，PD 在动画结束后自行销毁整组，本模组不必再回来清理。
 *
 * ⚠️ **录制顺序（展开）**：两条 `move` 必须录在 `fadeIn` **之后**。`move` 会**同步挪动组的轴心字段**，
 * 而客户端在 arm（收到「全量程序」）那一刻用**当时的轴心**反算每颗粒子的相对偏移 —— 先录 `move`
 * 会让整壳半径翻倍（详见 [updateShieldVisual] 内注释，该易踩点已登记到 `docs/issues/pd-api-requests.md`）。
 *
 * ⚠️ **退场动画的时间线要对齐到「现在」**：组的指令时间轴是**相对程序起点**的毫秒数，
 * 而收起发生在球壳建立很久之后，所以必须先 `delay(已过去 tick)` 再录指令（见 [foldShieldVisual]）。
 *
 * ⚠️ **不做径向缩放的替代实现**：`ParticleGroup.scale` 的 KDoc 承诺「粒子到轴心的距离与视觉大小同乘」，
 * 但 PD 实现只缩放视觉尺寸（位置 = 轴心 + 位移 + 相对偏移），所以「整壳胀大/缩小」这条最直觉的写法
 * 当前**不可用**；已登记 API 需求（`docs/issues/pd-api-requests.md`），本模组改用「面板平移」表达展开/收起。
 *
 * 代价：32 个组意味着建立时要发 32 份程序包 + 每个面板一圈粒子的生成包（一次性的，见 `open.md` §2），
 * 稳态仍是 **0 包/tick**；组数是常数，调粒子密度只改 [SHELL_POINTS_PER_EDGE]。
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
    private val shell = ArrayList<ParticleGroup>()

    /** 与 [shell] 一一对应的「面板中心偏移」（收起动画要按同样的方向把面板收回球心）。 */
    private val shellRadials = ArrayList<Vec3>()

    /** 球壳建立时的服务端 gameTime：收起动画要把指令时间线对齐到「现在」，见 [foldShieldVisual]。 */
    private var shellStartTick = 0L

    /**
     * 球壳面板几何（截角二十面体：12 五边形 + 20 六边形，坐标**相对球心**，见 [Polyhedra]）。
     * 结果确定，算一次缓存；[shell] 按同样的顺序建立（面板中心偏移另存于 [shellRadials]）。
     */
    private val shieldPanels: List<Polyhedra.Panel> by lazy {
        Polyhedra.truncatedIcosahedron(SHELL_RADIUS, SHELL_POINTS_PER_EDGE)
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
     * 护盾视觉（ParticleDrawing）：以持有者为**轴心**的五/六边形球壳 —— 跟随 + 自转 + 展开，
     * 前两者**零逐 tick 包**，展开只在建立时录一次指令。
     *
     * 实现要点：
     * - **一个面板一个组**（[Polyhedra] 给出的 32 块：12 五边形 + 20 六边形）。组级编排只能整组动，
     *   所以「各面板从球心飞向自己的位置」必须靠**每块面板各自的组**才能表达；
     * - **先绑轴心、再铺成员**（PD 的轴心是**程序级状态**，调用顺序即语义）：
     *   `createGroup(脚底)` → `followEntity(实体, 身体中段偏移)`（此后组的 pivot 字段 = 球心）
     *   → 再按「球心 + 面板边界点」铺成员，`offsetFromPivot` 用**相对球心**的偏移。
     *   顺序反了（或 [shellCenterOffset] 那段偏移被算两次/漏掉）都会让 arm 之后的相对偏移差一个身体中段，
     *   实机表现是「出现时位置正确、arm 之后整壳掉到脚下」（第 13 轮踩过，见 `resolved.md`）；
     * - **展开 = 两次 `move`**：先 `move(-面板中心偏移, 0)` 瞬时把面板折到球心，再
     *   `move(+面板中心偏移, [UNFOLD_TICKS], EASE_OUT)` 飞回位；⚠️ 两条 `move` 必须录在 `fadeIn`
     *   **之后**（`fadeIn` 那条指令完成 arm），原因见方法内注释；
     * - **自转**用 `spin`（客户端本地累加角度）。它写的是粒子的**相对偏移**，而展开写的是**组位移**，
     *   两者互不覆盖 —— 这是能同时使用的原因（旋转类指令之间会互相覆盖，别在同一组上叠加两次旋转）；
     * - 跨维度 / 换实体（重生）时**立即**销毁旧球壳再重建：旧面板组在旧维度/旧实体上，动画没有意义；
     * - 破盾 / 放下法杖 / 法术被取下走 [foldShieldVisual]（收起动画）。
     */
    private fun updateShieldVisual(level: ServerLevel, holder: Entity) {
        // 已建立且维度/实体都没变：跟随与自转都在客户端本地推进，这里不需要再做任何事
        if (shell.isNotEmpty() && shellLevel === level && shellHolder == holder.uuid) return
        clearShieldVisual() // 跨维度 / 换实体：先销毁旧的，避免球壳留在旧维度或挂在已消失的实体上

        val manager = wand.pdParticles(level)
        val bodyOffset = shellCenterOffset(holder)
        val feet = holder.position()
        val center = feet.add(0.0, bodyOffset, 0.0)

        for (panel in shieldPanels) {
            val group = manager.createGroup(feet)
            group.followEntity(holder.uuid, Vec3(0.0, bodyOffset, 0.0))
            for (offset in panel.ring) {
                // 成员必须相对**绑定后的轴心**铺开（= 球心），见上面的顺序说明
                manager.create()
                    .position(center.add(offset))
                    .color(SHELL_COLOR)
                    .scale(SHELL_SCALE)
                    .lifetime(PD_IMMORTAL)
                    .group(group.id)
                    .offsetFromPivot(offset)
                    .spawn()
            }
            val radial = panel.center // 面板中心相对球心的偏移（长度 = SHELL_RADIUS）
            // ⚠️⚠️ 录制顺序是硬要求：`move` 会**同步挪动组的轴心字段** `pivot`，而客户端在 arm
            //   （收到「全量程序」那一刻）用的正是**当时的轴心字段**去反算每颗粒子的相对偏移
            //   （`rel = spawn 位置 − 轴心字段`）。所以第一条录进去的指令必须**不挪轴心**：
            //   本组第一条是 fadeIn（轴心仍是球心）→ arm 时下发正确轴心 → rel = 面板边界偏移；
            //   两次 move 在其后录，只作为「追加剧组指令」下发，不再影响已算好的相对偏移。
            //   若把 move 放到最前面（最自然的「先铺形状再动」写法），arm 基准会少掉一个面板中心偏移，
            //   实机表现是整壳半径翻倍、展开从错误的位置开始。这条已登记为 PD 侧易踩点（pd-api-requests.md）。
            group.fadeIn(UNFOLD_TICKS, EasingType.EASE_OUT) // 1) 先录淡入：同时完成 arm（此时轴心 = 球心）
            group.move(radial.scale(-1.0), 0) // 2) 瞬时折到球心：展开动画的起点
            group.move(radial, UNFOLD_TICKS, EasingType.EASE_OUT) // 3) 展开：飞回球面
            group.spin(SHELL_SPIN_AXIS, SHELL_SPIN_RADIANS_PER_TICK)
            shell += group
            shellRadials += radial
        }
        shellStartTick = level.gameTime
        shellLevel = level
        shellHolder = holder.uuid
    }

    /**
     * 收起动画：每块面板向心收拢 + 淡出，动画结束后由 PD 自行销毁整组（`fadeOut(removeAfter = true)`
     * 内部会排一个延迟销毁任务），所以这里把引用丢掉即可 —— **本模组不需要再回来清理**。
     *
     * ⚠️ `delay([shellAgeTicks])` 不能省：组的时间线是「相对程序起点」的毫秒数，
     * 球壳可能已经存在几分钟；不推游标就会被客户端当成「这条指令早就该执行完」，动画瞬间跳到终点（等于没动画）。
     *
     * 面板组是「发射后不管」的：收起途中重新握杖会铺出**新**球壳，旧球壳在这 [FOLD_TICKS] tick 内
     * 自行淡出，中间会有一瞬间两层同框（可接受；要严格无缝就得等动画结束再重建，不值得为此加状态）。
     */
    private fun foldShieldVisual() {
        if (shell.isEmpty()) return
        val age = shellAgeTicks()
        for ((index, group) in shell.withIndex()) {
            val radial = shellRadials[index]
            group.delay(age)
            group.move(radial.scale(-1.0), FOLD_TICKS, EasingType.EASE_IN)
            group.fadeOut(FOLD_TICKS, removeAfter = true)
        }
        shell.clear()
        shellRadials.clear()
        shellLevel = null
        shellHolder = null
        idleTicks = 0
    }

    /** 球壳已存在多少 tick（收起动画的时间线游标要对齐到「现在」）。 */
    private fun shellAgeTicks(): Int {
        val level = shellLevel ?: return 0
        return (level.gameTime - shellStartTick).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * 被动「未被持有」时的每 tick 回调（由 `Wand.tick` 的闲置分支驱动）。
     *
     * 放下法杖后**保留 [SHELL_LINGER_TICKS] tick** 再**收起**球壳 —— 这段窗口是给「刚放下又马上拿起来」
     * 留的缓冲（重新握杖时 [onInventoryTick] 会把倒计时归零，球壳根本不会被收）。
     */
    override fun onPassiveIdle() {
        if (shell.isEmpty()) return
        idleTicks++
        if (idleTicks >= SHELL_LINGER_TICKS) foldShieldVisual()
    }

    /**
     * 从法杖上取下（或槽位被替换）时调用：**立刻收起球壳**。
     *
     * 必须做：取下之后本实例不再出现在 `Wand.loadedMagics` 里，[onInventoryTick] 与 [onPassiveIdle]
     * **都不会再被调用** —— 而球壳是 `lifetime = -1` 的永存粒子组，不在这里处理就会一直留在世界里。
     * 收起动画不依赖本实例继续 tick（销毁由 PD 的调度器负责），所以取下后动画照样能放完。
     * 若取下的只是「多张护盾卡」中的一张，接任的主卡会在下一 tick 铺出自己的球壳。
     */
    override fun onUnloaded() {
        foldShieldVisual()
    }

    /** 立即销毁整个球壳（不播动画）：跨维度/换实体/重建时用 —— 旧面板在旧维度上，动画没有意义。 */
    private fun clearShieldVisual() {
        for (group in shell) group.remove()
        shell.clear()
        shellRadials.clear()
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
         * 1 = 只放顶点（球壳是「点阵」，最省）；2 = 顶点 + 中点（边长 0.69 格被切成两段，看起来接近实线）。
         * ⚠️ 32 块面板 × 每块 5~6 条棱 × 本值 = 建立时的粒子数（当前 2 → 360 个），调大它就成比例变多。
         */
        private const val SHELL_POINTS_PER_EDGE = 2

        /** 单颗粒子尺寸（球壳边长约 0.69 格，这个尺寸下相邻点刚好相接） */
        private const val SHELL_SCALE = 0.4f

        /** 展开时长（tick）：面板从球心飞到球面 + 整壳淡入 */
        private const val UNFOLD_TICKS = 14

        /** 收起时长（tick）：面板缩回球心 + 淡出（PD 在同一时刻销毁整组） */
        private const val FOLD_TICKS = 12

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
         * 先等这么久（防止「放下又立刻拿起」闪一下），再播 [FOLD_TICKS] 的收起动画。
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
