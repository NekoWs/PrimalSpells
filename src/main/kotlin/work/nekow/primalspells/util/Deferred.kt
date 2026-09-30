package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel

/**
 * 服务端**延迟一次性动作**队列：把「N tick 之后要发生的那一下」排进去，由 `MagicManager` 每 tick 推。
 *
 * ## 为什么需要它
 *
 * 特效的**重音时刻**经常晚于特效自己的生命周期：
 * - 黑洞的退场是「悬一下、再猛地塌进中心」，**塌到中心那一拍**才是重音（爆闪 + 镜头被撞），
 *   而那时投射物已经 `die()` 了，`AreaEffect.onTick` 不会再被调用；
 * - 重力场的波环是**客户端本地**在落，服务端只知道「它会在 [N] tick 后落地」，
 *   落地那一拍的溅射与冲击同样要等；
 * - 护盾的「搭完了」也一样。
 *
 * 这些动作只有一件事要做：到点生成一批一次性粒子 + 发一条镜头冲击。PD 没有「延时生成一颗粒子」的入口
 * （`ParticleHandle` 的指令都是「从现在起」，见 `docs/issues/pd-api-requests.md` 同一条时间轴问题），
 * 所以延时由本模组自己排 —— 这是**我们自己的时序簿记**，不涉及任何粒子同步/求值（那仍然全归 PD）。
 *
 * ## 约定
 *
 * - 到期判定用**所在维度的 `gameTime`**（不是进程内计数器）：与存档时间同源，世界卸载/重载不会错位；
 * - 维度已经不可用（服务器停机、世界被卸载）时**直接丢弃**任务，宁可不放这一拍也不抛异常；
 * - 动作体一律 `runCatching` 包住：延迟任务炸了不能影响服务端主循环；
 * - 数量级很小（同一时刻最多几个），线性扫描足够。
 */
object Deferred {

    private class Task(val level: ServerLevel, val dueAt: Long, val action: () -> Unit)

    private val tasks = ArrayList<Task>()

    /**
     * 在 [ticks] 个服务端 tick 之后执行 [action]。
     *
     * @param ticks 0 或负数视为「下一 tick 就执行」（不立刻执行，避免与当前 tick 的生成顺序交错）
     */
    fun after(level: ServerLevel, ticks: Int, action: () -> Unit) {
        tasks += Task(level, level.gameTime + ticks.coerceAtLeast(0) + 1, action)
    }

    /** 由 `MagicManager.tick()` 每 tick 调用。 */
    fun tick() {
        if (tasks.isEmpty()) return
        val iter = tasks.iterator()
        while (iter.hasNext()) {
            val task = iter.next()
            if (!task.level.server.isRunning) {
                iter.remove()
                continue
            }
            if (task.level.gameTime >= task.dueAt) {
                iter.remove()
                runCatching { task.action() }
            }
        }
    }

    /** 服务端停机时清空（`MagicManager` 的 `ServerStoppingEvent` 里调）。 */
    fun clear() {
        tasks.clear()
    }
}
