package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager

/**
 * PD 粒子群：把**我们自己算出来的位置**交给 ParticleDrawing 去渲染。
 *
 * ## 职责边界（改动前先读 `docs/conventions.md` §9）
 *
 * - **ParticleDrawing 负责**：生成、跨端同步、可见性、寿命、渲染与客户端插值；
 * - **本类只负责**：记住句柄、写位置（[tickSimulated] / [tickAnchored]）、按条件销毁（[clear]）。
 *
 * 也就是说这里**没有任何渲染/同步/求值逻辑** —— 不是「另写一套粒子系统」，只是 PD API 的使用封装。
 *
 * 三种用法：
 * - [spawn]：**静止**粒子（重力场的范围球壳）；
 * - [spawnSimulated] + [tickSimulated]：**运动**粒子，位置由本模组每 tick 积分后用 `track` 写回
 *   （黑洞绕转吸入、重力场下坠）—— 相比交给引擎按速度推进，这样在**服务端暂停时不会飘**，也更好控制轨迹；
 * - [tickAnchored]：**锚定**在某个点/实体上的粒子（护盾球壳），寿命错峰重建。
 */
class ParticleSwarm {

    /** 静止粒子：生成后不再改位置，由寿命/条件回收。 */
    private val handles = ArrayList<ParticleHandle>()

    /**
     * 锚定粒子：位置 = 锚点 + 固定偏移，寿命**错峰**重建。
     *
     * @property handle    当前句柄（错峰重建时会换成新句柄）
     * @property offset    相对锚点的偏移（**绑定在粒子上**，不按索引配对）
     * @property remaining 剩余寿命（tick），到 0 就重建这一个粒子
     */
    private class Anchored(var handle: ParticleHandle, val offset: Vec3, var remaining: Int)

    private val anchored = ArrayList<Anchored>()

    /**
     * **运动粒子**：位置与速度由本模组维护，每 tick 用 `track` 把算好的位置写回 PD。
     *
     * 为什么不用 `setVelocity` 交给引擎积分：引擎的「有速度粒子」在客户端也参与推进，
     * **服务端暂停时（单人按 Esc）客户端会按最后速度继续外推**，粒子会飘出原定范围；
     * 由本模组积分 + `track` 后，暂停时不再下发新位置，粒子就地定格。
     * （渲染/同步/寿命/可见性仍由 ParticleDrawing 负责。）
     *
     * @property pos 本模组维护的位置（与 PD 里的位置保持同步写入）
     * @property vel 本模组维护的速度（由 [tickSimulated] 的 `step` 更新）
     */
    private class Simulated(var handle: ParticleHandle, var pos: Vec3, var vel: Vec3)

    private val simulated = ArrayList<Simulated>()

    /** 普通（静止）粒子数：用于「只生成一次」这类判断（不含锚定/运动粒子）。 */
    val size: Int get() = handles.size

    /**
     * 生成一个**运动粒子**：位置/速度由本模组维护，见 [Simulated] 的说明。
     *
     * @return 句柄；因维度粒子上限被 PD 拒绝时返回 null（不纳入跟踪）
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
     * 1. `step(pos, vel)` 返回**本 tick 的新速度**（施力/阻尼都在这里做）；返回 `null` 表示立刻销毁
     *    （例如「已抵达黑洞中心」）；
     * 2. 位置由本模组积分（`pos += vel`）后用 [ParticleHandle.track] 写回 PD；
     * 3. `removeIf` 命中（例如「碰到了下半球表面」）同样立刻销毁，判定只读本模组自己的位置。
     */
    fun tickSimulated(
        step: (pos: Vec3, vel: Vec3) -> Vec3?,
        removeIf: ((pos: Vec3) -> Boolean)? = null,
    ) {
        val iter = simulated.iterator()
        while (iter.hasNext()) {
            val particle = iter.next()
            if (particle.handle.data() == null) {
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
            particle.handle.track(particle.pos.x, particle.pos.y, particle.pos.z)
        }
    }

    /**
     * 生成一个**静止**粒子（生成后不再改位置，靠寿命或 [clear] 回收）。
     *
     * @return 句柄；因维度粒子上限被 PD 拒绝时返回 null（不纳入跟踪）
     */
    fun spawn(
        manager: ParticleManager,
        pos: Vec3,
        color: Color,
        scale: Float,
        lifetimeTicks: Int,
    ): ParticleHandle? {
        val handle = spawnRaw(manager, pos, color, scale, lifetimeTicks) ?: return null
        handles.add(handle)
        return handle
    }

    /**
     * 维护一组**锚定粒子**：位置 = 锚点 + 各自偏移，随锚点每 tick 更新。
     *
     * 寿命**错峰**重建：每个粒子独立倒计时，到点只重建它自己（初始剩余寿命按序号错开），
     * 而不是整圈同时销毁+重建。两个原因：
     * 1. 同一 tick 集中销毁+生成会出现视觉闪断；
     * 2. 若 PD 因维度粒子上限拒绝了部分生成，按「索引 → 偏移」配对会让粒子整体错位（表现为瞬移）——
     *    这里把偏移**绑定在句柄上**，任何生成失败都只影响该粒子自己。
     *
     * @param count         目标粒子数（未达标时每 tick 补齐并从 [offsetAt] 取对应偏移）
     * @param lifetimeTicks 每个粒子的寿命；必须 ≥ count 才能完全错峰（否则会出现同一 tick 多个重建）
     * @param offsetAt      第 index 个粒子相对锚点的偏移
     */
    fun tickAnchored(
        manager: ParticleManager,
        anchor: Vec3,
        count: Int,
        color: Color,
        scale: Float,
        lifetimeTicks: Int,
        offsetAt: (index: Int) -> Vec3,
    ) {
        while (anchored.size < count) {
            val index = anchored.size
            val offset = offsetAt(index)
            val handle = spawnRaw(manager, anchor.add(offset), color, scale, lifetimeTicks) ?: break
            // 错峰：把初始剩余寿命沿 [1, lifetimeTicks] 均匀铺开
            val remaining = (lifetimeTicks - index * lifetimeTicks / count).coerceAtLeast(1)
            anchored += Anchored(handle, offset, remaining)
        }
        anchored.removeIf { it.handle.data() == null }

        for (entry in anchored) {
            entry.remaining--
            if (entry.remaining <= 0) {
                entry.handle.remove()
                val handle = spawnRaw(manager, anchor.add(entry.offset), color, scale, lifetimeTicks)
                if (handle != null) {
                    entry.handle = handle
                    entry.remaining = lifetimeTicks
                } else {
                    entry.remaining = 2 // 被维度上限拒绝：过两 tick 再试，避免每 tick 空转
                }
            }
            val pos = anchor.add(entry.offset)
            entry.handle.track(pos.x, pos.y, pos.z)
        }
    }

    /** 立即销毁本群全部粒子（普通 + 锚定 + 运动）并清空跟踪（效果结束时调用）。 */
    fun clear() {
        for (handle in handles) handle.remove()
        handles.clear()
        for (entry in anchored) entry.handle.remove()
        anchored.clear()
        for (particle in simulated) particle.handle.remove()
        simulated.clear()
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
}
