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
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.network.SyncShieldPayload
import work.nekow.primalspells.util.Lore
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.pdParticles
import work.nekow.primalspells.util.spawnParticle
import kotlin.math.min

/**
 * 能量盾（被动法术）：编入法杖后，只要法杖位于玩家手上（主手/副手）即自动生效。
 *
 * 为持有者提供一层能量护盾：进入护盾范围的敌方法术会被弹开（反射其速度），
 * 每次弹开消耗耐久；耐久归零后护盾破碎，暂停恢复一段时间（[breakPauseTicks]），
 * 之后以 [regenPerSecond] 的速度恢复耐久。
 *
 * 当前耐久与破盾暂停剩余 tick 是**运行期状态**，随法杖存档持久化
 * （见 [saveRuntimeState]/[loadRuntimeState]）：重登/重启/被 `WandManager` 回收后再懒加载，
 * 护盾都不会回满。数值与手感本身仍是待调项（`docs/known-issues.md` §1.1）。
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

    /** 护盾球壳粒子集（ParticleDrawing）：以持有者为锚点的淡蓝色球壳，见 [updateShieldVisual]。 */
    private var shell: ParticleBatch? = null

    /** [shell] 建立时所用的粒子管理器：跨维度会换实例，换了就重建球壳（见 [shieldShell]）。 */
    private var shellManager: ParticleManager? = null

    /** 球壳粒子的球面偏移（首次生成时用 Fibonacci 球算一次，之后固定 —— 球壳静态，只跟随持有者平移）。 */
    private var shellOffsets: List<Vec3> = emptyList()

    /** 「未被持有」持续的 tick 数：达到 [SHELL_LINGER_TICKS] 就清掉球壳（见 [onPassiveIdle]）。 */
    private var idleTicks = 0

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
     * @param tag 存档状态；键缺失时退回「满耐久、无暂停」（旧档/首次入库）
     */
    override fun loadRuntimeState(tag: CompoundTag) {
        durability = tag.getDoubleOr("durability", maxDurability).coerceIn(0.0, maxDurability)
        pauseTicks = tag.getIntOr("pause_ticks", 0).coerceAtLeast(0)
    }

    override var needInventory: Boolean = true

    override fun onInventoryTick() {
        val player = caster
        if (!player.isAlive) return
        val level = player.level()
        if (level !is ServerLevel) return

        // 重新被持有：复位「放下倒计时」（见 onPassiveIdle）
        idleTicks = 0

        // 耐久回复（破盾暂停期间不回复）
        if (pauseTicks > 0) {
            pauseTicks--
        } else {
            durability = min(maxDurability, durability + regenPerSecond / 20.0)
        }
        val shieldUp = durability > 0
        if (shieldUp) {
            updateShieldVisual(level, player)
        } else {
            // 破盾：球壳立即消失（暂停恢复期间不再重建）
            clearShieldVisual()
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
     * 护盾视觉（ParticleDrawing）：以持有者为锚点的**淡蓝色球壳**（刚性平移，零逐 tick 包）。
     *
     * 实现要点：
     * - 球壳用 PD 的 `attachTo(实体, 世界空间偏移)` 钉在持有者身上：**服务端只在挂载时下发一次**，
     *   之后由客户端每 tick 本地解析实体位置 —— 每 tick 零包，而且天然刚性（不受位置插值影响）；
     * - ⚠️ **挂载偏移必须包含「身体中段」那一段**：`attachTo` 的偏移是相对 `entity.position()`（脚底）的，
     *   只给球面偏移会让球壳整个贴在脚上（实机表现为「球在玩家下方、粒子像在往下掉」）；
     * - 球面用 [ParticleShapes.fibonacciSphere]**均匀**取点（不是随机采样），所以看起来是一个均匀球壳；
     * - 粒子**永存**（`lifetime = -1`）、[ParticleBatch.ensureSize] 只在缺数量时补齐：
     *   **不做周期重建** —— 重建（销毁+新建）会让新粒子从生成位置滑到挂载位置，
     *   实机表现就是「粒子每隔一会儿瞬移一下 / 从上方落下」。清理走 [onPassiveIdle] 的显式倒计时；
     * - **不自转**：挂载偏移固定。想做自转要改成「每 tick 用一次 `ParticleBatch.trackAll`
     *   下发旋转后的偏移」（1 包/tick），不要退回逐粒子 `track`；
     * - 破盾时立即 [clearShieldVisual]（球壳消失），暂停恢复期间不再重建。
     */
    private fun updateShieldVisual(level: ServerLevel, holder: Entity) {
        if (shellOffsets.isEmpty()) {
            // Fibonacci 球：点间距近似相等（均匀球壳），且结果确定、便于复现问题
            shellOffsets = ParticleShapes.fibonacciSphere(SHELL_PARTICLES)
                .map { it.scale(SHELL_RADIUS) }
        }
        val manager = wand.pdParticles(level)
        val bodyOffset = shellCenterOffset(holder)
        shieldShell(manager).ensureSize(SHELL_PARTICLES) { index ->
            // ⚠️ 生成位置 = entity.position()（脚底）+ 偏移，必须与 attachTo 的坐标系一致：
            // 多算一段就会出现「新粒子从上方滑入」的动画（见 §6 第 8 轮）
            val offset = shellOffsets[index % shellOffsets.size].add(0.0, bodyOffset, 0.0)
            manager.spawnParticle(holder.position().add(offset), SHELL_COLOR, SHELL_SCALE)
                ?.attachTo(holder, offset)
        }
    }

    /**
     * 取本护盾的球壳粒子集（同一管理器时复用）。
     *
     * [ParticleBatch] 在**构造时**绑定粒子管理器，而 `wand.pdParticles(level)` 在跨维度时会换成新维度的
     * 实例（`util/PdParticles.kt` 的约定）—— 直接缓存粒子集会让球壳留在旧维度，所以这里按管理器缓存、
     * 换了就先把旧球壳销毁再重建。护盾只用「成员增删 + 锚定」，不用位置批量下发（球壳零逐 tick 包）。
     */
    private fun shieldShell(manager: ParticleManager): ParticleBatch {
        val cached = shell
        if (cached != null && shellManager === manager) return cached
        cached?.clear()
        return ParticleBatch(manager).also { shell = it; shellManager = manager }
    }

    /**
     * 被动「未被持有」时的每 tick 回调（由 `Wand.tick` 的闲置分支驱动）。
     *
     * 放下法杖后**保留 [SHELL_LINGER_TICKS] tick** 再清除球壳 —— 这段时间是留给后续
     * 「消失动画」的窗口（现在只是延迟销毁，看不出淡出）。重新握杖时 [onInventoryTick]
     * 会把倒计时归零。
     */
    override fun onPassiveIdle() {
        val batch = shell ?: return
        if (batch.isEmpty()) return
        idleTicks++
        if (idleTicks >= SHELL_LINGER_TICKS) clearShieldVisual()
    }

    /** 破盾/失效：立即销毁球壳粒子。 */
    private fun clearShieldVisual() {
        shell?.clear()
        idleTicks = 0
    }

    /** 球壳球心相对脚底的偏移：身体中段（身高 55%），比眼睛位置更贴近「包裹全身」的观感。 */
    private fun shellCenterOffset(holder: Entity): Double = holder.bbHeight * 0.55

    /** 同步护盾状态到持有者客户端（仅状态变化时发包，避免每 tick 空包） */
    private fun syncClient() {
        val player = caster as? ServerPlayer ?: return
        val payload = SyncShieldPayload(wand.id, durability, maxDurability, pauseTicks, breakPauseTicks)
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

        // ---- 球壳粒子（ParticleDrawing）参数 ----

        /** 球壳粒子数：Fibonacci 均匀取点；`attachTo` 后无逐 tick 带宽，可以放心多一些 */
        private const val SHELL_PARTICLES = 40

        /** 球壳半径（方块）：`attachTo` 的偏移相对脚底，球心还要再加「身体中段」那一段 */
        private const val SHELL_RADIUS = 1.7

        /** 粒子尺寸 */
        private const val SHELL_SCALE = 0.4f

        /**
         * 放下法杖后球壳保留的 tick 数（= [onPassiveIdle] 的倒计时阈值）。
         * 目前只是延迟销毁；将来做「消失动画」时，这段时间就是动画窗口。
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
