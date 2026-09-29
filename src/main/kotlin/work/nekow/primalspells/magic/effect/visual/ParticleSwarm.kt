package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager
import java.util.UUID

/**
 * PD 粒子群：把**我们自己算出来的位置/速度**交给 ParticleDrawing 去同步与渲染。
 *
 * ## 职责边界（改动前先读 `docs/conventions.md` §9）
 *
 * - **ParticleDrawing 负责**：生成、跨端同步、可见性、寿命、位置积分（速度/加速度驱动时）、客户端插值渲染；
 * - **本类只负责**：算位置或速度、记句柄、按条件销毁。
 *
 * 这里**没有任何渲染/同步逻辑** —— 不是「另写一套粒子系统」，只是 PD API 的使用封装。
 *
 * ## 三种模式（对应 PD 的三种低带宽写法）
 *
 * | 模式 | PD 用法 | 每 tick 带宽 | 本项目用途 |
 * |---|---|---|---|
 * | [spawn] 静止 / 匀速 | `create()…spawn()`（可选一次 `setVelocity`） | 0（速度由 PD 逐 tick 积分） | 重力场范围球壳、下落粒子 |
 * | [ensureAttached] | `attachTo(实体, 世界空间偏移)` | **0**（只在挂载时下发一次） | 护盾球壳 |
 * | [spawnSimulated] + [tickSimulated] | 本模组积分 + 一次 [ParticleManager.trackAll] | **1 包/tick，与粒子数无关** | 黑洞绕转吸入 |
 *
 * 另：PD 的 `applyForce(accel, ticks)`（服务端权威加速度，两端同规则积分）适合「力恒定或可分段」的场景；
 * 黑洞的吸力**随距离变化**，用位置驱动（[tickSimulated]）比每几 tick 逐粒子重发一次力更省、
 * 也保证「抵达中心才消失」这一步完全可控。
 *
 * ## 自转怎么办（供以后需要时使用）
 *
 * [ensureAttached] 的偏移在挂载时固定（世界空间），所以球壳是**刚性平移**、不会自转 —— 这也正是它零带宽的原因。
 * 要做「每 tick 旋转过的偏移」，改用 [ParticleManager.trackAll] 一次包发全部粒子：
 * `manager.trackAll(ids, offsets.map { anchor.add(rotateY(it, angle)) })`（1 包/tick），
 * 不要再退回「逐粒子 `track`」（那会变成 N 包/tick，而且历史上曾因按索引配对出过错位）。
 */
class ParticleSwarm {

    /** 静止 / 匀速粒子（速度由 PD 积分；不设速度就是完全静止）。 */
    private val handles = ArrayList<ParticleHandle>()

    /**
     * 锚定粒子：位置 = 实体位置 + 固定世界空间偏移（客户端本地解析，服务端零逐 tick 包）。
     * 粒子**永存**，由调用方在明确时机 [clear]。
     *
     * @property offset 挂载时的偏移（**世界空间**、含身体中段偏移，不随实体朝向旋转）
     */
    private class Attached(var handle: ParticleHandle, val offset: Vec3)

    private val attached = ArrayList<Attached>()

    /**
     * 运动粒子：位置与速度由本模组维护，每 tick 用**一次** [ParticleManager.trackAll] 批量写回。
     *
     * @property pos 本模组维护的位置（权威）
     * @property vel 本模组维护的速度（由 [tickSimulated] 的 `step` 更新）
     */
    private class Simulated(var handle: ParticleHandle, var pos: Vec3, var vel: Vec3)

    private val simulated = ArrayList<Simulated>()

    /** 静止/匀速粒子数（用于「只生成一次」这类判断）。 */
    val size: Int get() = handles.size

    /** 三张表都空（用于「放下后延迟清理」这类判断）。 */
    val isEmpty: Boolean get() = handles.isEmpty() && attached.isEmpty() && simulated.isEmpty()

    // ---------- 静止 / 匀速 ----------

    /**
     * 生成一个粒子。
     *
     * @param velocity 初速度（blocks/tick）；`null` = 完全静止（之后也不再改位置）
     * @return 句柄；因维度粒子上限被 PD 拒绝时返回 null（不纳入跟踪）
     */
    fun spawn(
        manager: ParticleManager,
        pos: Vec3,
        color: Color,
        scale: Float,
        lifetimeTicks: Int,
        velocity: Vec3? = null,
    ): ParticleHandle? {
        val handle = spawnRaw(manager, pos, color, scale, lifetimeTicks, velocity) ?: return null
        handles.add(handle)
        return handle
    }

    /** 只读位置地销毁满足条件的粒子（位置读的是 PD 的权威值；不产生速度/位置包）。 */
    fun removeWhere(predicate: (pos: Vec3) -> Boolean) {
        val iter = handles.iterator()
        while (iter.hasNext()) {
            val handle = iter.next()
            val pos = handle.position()
            if (pos == null) {
                iter.remove() // 已过期或被销毁
                continue
            }
            if (predicate(pos)) {
                handle.remove()
                iter.remove()
            }
        }
    }

    // ---------- 锚定（零逐 tick 包） ----------

    /**
     * 维护 [count] 个**锚定粒子**（位置 = 实体 + 各自偏移），粒子**永存**（`lifetime = -1`）。
     *
     * - 位置由 `attachTo` 交给客户端本地解析：本方法**不写位置**、也不重建健康粒子，
     *   只在数量不足时补齐、并清掉已被 PD 销毁的句柄；
     * - **为什么不做「错峰重建」**：重建 = 销毁 + 新建，而新建那一刻客户端会从**生成位置**
     *   插值到挂载位置 —— 表现为「粒子每隔一会儿就瞬移一下、或从上方滑落」（实机现象）。
     *   清理改由调用方在明确时机 `clear()`（例如 `EnergyShield` 用 `Magic.onPassiveIdle` 的
     *   放下倒计时），不依赖寿命自然过期；
     * - ⚠️ [entityPos] 必须与 `attachTo` 的坐标系一致（即 `Entity.position()`，脚底）：
     *   生成位置 = [entityPos] + 偏移，若这里多/少算一段，新粒子就会「从错误位置滑入」。
     *
     * @param offsetAt 第 index 个粒子相对实体位置的偏移（含任何身体中段偏移）；
     *                 **必须是 index 的确定性函数**（补齐时会按 index 反复调用）
     */
    fun ensureAttached(
        manager: ParticleManager,
        entityId: Int,
        entityPos: Vec3,
        count: Int,
        color: Color,
        scale: Float,
        offsetAt: (index: Int) -> Vec3,
    ) {
        attached.removeIf { it.handle.position() == null }
        // 每 tick 最多补这么多：即使 PD 侧异常地把锚定粒子都报成「不存在」，也不会瞬间刷出上百个生成包
        var budget = MAX_TOP_UP_PER_TICK
        while (attached.size < count && budget-- > 0) {
            val entry = createAttached(manager, entityId, entityPos, offsetAt(attached.size), color, scale, IMMORTAL)
                ?: break // 被维度上限拒绝：下一 tick 再补
            attached.add(entry)
        }
    }

    // ---------- 运动（本模组积分 + 一次 trackAll） ----------

    /**
     * 生成一个**运动粒子**：位置/速度由本模组维护，位置每 tick 由 [tickSimulated] 批量写回。
     *
     * @return 句柄；被维度上限拒绝时返回 null（不纳入跟踪）
     */
    fun spawnSimulated(
        manager: ParticleManager,
        pos: Vec3,
        velocity: Vec3,
        color: Color,
        scale: Float,
        lifetimeTicks: Int,
    ): ParticleHandle? {
        val handle = spawnRaw(manager, pos, color, scale, lifetimeTicks) ?: return null
        simulated += Simulated(handle, pos, velocity)
        return handle
    }

    /**
     * 推进全部运动粒子：
     * 1. `step(pos, vel)` 返回**本 tick 的新速度**（施力/阻尼都在这里做）；返回 `null` = 立刻销毁；
     * 2. 位置由本模组积分（`pos += vel`）；
     * 3. `removeIf` 命中（例如「碰到下半球表面」）同样立刻销毁；
     * 4. 存活的粒子位置用**一次** [ParticleManager.trackAll] 批量下发。
     *
     * 注意：`track` 系列是位置指令，PD 会停掉这些粒子上的速度/力（本模式的粒子不设速度，互不干扰）。
     */
    fun tickSimulated(
        manager: ParticleManager,
        step: (pos: Vec3, vel: Vec3) -> Vec3?,
        removeIf: ((pos: Vec3) -> Boolean)? = null,
    ) {
        if (simulated.isEmpty()) return
        val ids = ArrayList<UUID>(simulated.size)
        val positions = ArrayList<Vec3>(simulated.size)
        val iter = simulated.iterator()
        while (iter.hasNext()) {
            val particle = iter.next()
            if (particle.handle.position() == null) {
                iter.remove() // 寿命到期或已被销毁
                continue
            }
            val nextVel = step(particle.pos, particle.vel)
            if (nextVel == null) {
                particle.handle.remove()
                iter.remove()
                continue
            }
            particle.vel = nextVel
            particle.pos = particle.pos.add(nextVel)
            if (removeIf != null && removeIf(particle.pos)) {
                particle.handle.remove()
                iter.remove()
                continue
            }
            ids += particle.handle.id
            positions += particle.pos
        }
        if (ids.isNotEmpty()) manager.trackAll(ids, positions)
    }

    /** 立即销毁本群全部粒子并清空跟踪（效果结束时调用）。 */
    fun clear() {
        for (handle in handles) handle.remove()
        handles.clear()
        for (entry in attached) entry.handle.remove()
        attached.clear()
        for (particle in simulated) particle.handle.remove()
        simulated.clear()
    }

    // ---------- 内部 ----------

    /** 创建一个已挂载的锚定条目（不加入跟踪表），供 [ensureAttached] 复用。 */
    private fun createAttached(
        manager: ParticleManager,
        entityId: Int,
        entityPos: Vec3,
        offset: Vec3,
        color: Color,
        scale: Float,
        lifetimeTicks: Int,
    ): Attached? {
        val handle = spawnRaw(manager, entityPos.add(offset), color, scale, lifetimeTicks) ?: return null
        handle.attachTo(entityId, offset)
        return Attached(handle, offset)
    }

    /** 内部生成：只创建、不纳入任何跟踪表。 */
    private fun spawnRaw(
        manager: ParticleManager,
        pos: Vec3,
        color: Color,
        scale: Float,
        lifetimeTicks: Int,
        velocity: Vec3? = null,
    ): ParticleHandle? {
        val handle = manager.create()
            .position(pos)
            .color(color)
            .scale(scale)
            .lifetime(lifetimeTicks)
            .spawn() ?: return null
        if (velocity != null) handle.setVelocity(velocity)
        return handle
    }

    private companion object {
        /** `lifetime = -1`：永存粒子（由调用方显式 [clear]）。 */
        const val IMMORTAL = -1

        /** 每 tick 最多补齐的锚定粒子数（防异常情况下瞬间刷包）。 */
        const val MAX_TOP_UP_PER_TICK = 4
    }
}
