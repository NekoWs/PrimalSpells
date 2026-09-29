package work.nekow.primalspells.magic.effect.visual

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleHandle
import work.nekow.particledrawing.api.ParticleManager

/**
 * PD 粒子群：把**我们自己算出来的力**交给 ParticleDrawing 去积分与渲染。
 *
 * ## 职责边界（改动前先读 `docs/conventions.md` §9）
 *
 * - **ParticleDrawing 负责**：生成、跨端同步、可见性、寿命、每 tick 位置积分（引擎内部 `pos += velocity`）、
 *   客户端插值渲染；
 * - **本类只负责**：记住句柄、每 tick 把「本 tick 的速度」写回（[update]）、到点移除（[clear]）。
 *
 * 也就是说这里**没有任何渲染/同步/求值逻辑** —— 不是「另写一套粒子系统」，只是 PD API 的使用封装。
 *
 * 用法：每 tick 用 [spawn] 生成若干粒子（可给初速度），需要随时间改变运动时用 [update] 施加一次力；
 * 效果结束时务必 [clear]（否则粒子会按各自寿命继续存在）。
 */
class ParticleSwarm {

    private val handles = ArrayList<ParticleHandle>()

    /** 当前仍在跟踪的粒子数（不包含已自然过期的）。 */
    val size: Int get() = handles.size

    /**
     * 生成一个粒子并纳入跟踪。
     *
     * @param velocity 初速度（blocks/tick）；`null` 表示静止（后续由 [update] 施力）
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
        val handle = manager.create()
            .position(pos)
            .color(color)
            .scale(scale)
            .lifetime(lifetimeTicks)
            .spawn() ?: return null
        if (velocity != null) handle.setVelocity(velocity)
        handles.add(handle)
        return handle
    }

    /**
     * 遍历仍存活的粒子并**施力**：回调拿到 PD 引擎里的权威位置与当前速度，
     * 返回本 tick 的新速度；返回 `null` 表示立即销毁该粒子（例如已落到中心）。
     *
     * 位置/速度都从 PD 读取，本模组不自己积分，因此不会与引擎状态产生漂移。
     * 注意：[ParticleHandle.setVelocity] 会向可见玩家广播，所以只在需要改速度时调用本方法。
     */
    fun update(action: (pos: Vec3, velocity: Vec3) -> Vec3?) {
        val iter = handles.iterator()
        while (iter.hasNext()) {
            val handle = iter.next()
            val data = handle.data()
            if (data == null) {
                iter.remove() // 已过期或被销毁
                continue
            }
            val next = action(data.position(), data.velocity())
            if (next == null) {
                handle.remove()
                iter.remove()
            } else {
                handle.setVelocity(next)
            }
        }
    }

    /** 只清理句柄表里已过期的粒子（不改速度、不发包）。 */
    fun prune() {
        handles.removeIf { it.data() == null }
    }

    /**
     * 把本群粒子按调用方给出的**逐个偏移**钉到锚点周围（「锚点球体」这类跟随表现）。
     *
     * 与 [update] 一样：位置由我们算（锚点 + 偏移），PD 负责渲染与客户端插值。
     * 用 [ParticleHandle.track] 直设位置（`move` 缓动会慢一拍），因此**不要**同时给这些粒子设速度。
     * 注意 `track` 会向可见玩家广播，所以「粒子数 × 每 tick 跟踪」要有预算。
     *
     * @param anchor   锚点世界坐标（例如持有者身体中段）
     * @param offsetAt 第 index 个粒子相对锚点的偏移
     */
    fun trackAround(anchor: Vec3, offsetAt: (index: Int) -> Vec3) {
        handles.forEachIndexed { index, handle ->
            val pos = anchor.add(offsetAt(index))
            handle.track(pos.x, pos.y, pos.z)
        }
    }

    /** 立即销毁本群全部粒子并清空跟踪（效果结束时调用）。 */
    fun clear() {
        for (handle in handles) handle.remove()
        handles.clear()
    }
}
