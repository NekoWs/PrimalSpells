package work.nekow.primalspells.util

import net.minecraft.server.level.ServerLevel
import org.slf4j.LoggerFactory

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
 *
 * **[Handle] 可取消**：特效提前结束时，它排下的「还没到点的重音」必须跟着作废 ——
 * 否则会出现「场已经没了，地面还在被压出一道环」（第 18 轮实机反馈，重力场波环落地溅射就是这么来的）。
 */
object Deferred {

    private val LOGGER = LoggerFactory.getLogger("primalspells-deferred")

    internal class Task(val level: ServerLevel, val dueAt: Long, val action: () -> Unit) {
        var cancelled = false
    }

    /** 排下任务时拿到的句柄：特效结束/重来时 [cancel] 掉，免得延迟动作落到一个已经不存在的特效上。 */
    class Handle internal constructor(private val task: Task) {
        fun cancel() {
            task.cancelled = true
        }
    }

    private val tasks = ArrayList<Task>()

    /**
     * 在 [ticks] 个服务端 tick 之后执行 [action]。
     *
     * @param ticks 0 或负数视为「下一 tick 就执行」（不立刻执行，避免与当前 tick 的生成顺序交错）
     * @return 句柄；调用方提前收场时记得 [Handle.cancel]
     */
    fun after(level: ServerLevel, ticks: Int, action: () -> Unit): Handle {
        val task = Task(level, level.gameTime + ticks.coerceAtLeast(0) + 1, action)
        tasks += task
        return Handle(task)
    }

    /**
     * 由 `MagicManager.tick()` 每 tick 调用。
     *
     * ⚠️ **分两步走**：先把本轮到期的任务从队列里摘出来，执行放到摘完之后 ——
     * 任务体自己还会排新任务（黑洞退场就是「整壳塌缩 → 排反弹小球 → 排收尾」三拍连排），
     * 边迭代边执行会直接在 `ArrayList` 上抛 `ConcurrentModificationException` 把服务端打崩
     * （第 18 轮实测踩到，见 `docs/issues/resolved.md`）。
     */
    fun tick() {
        if (tasks.isEmpty()) return
        var due: MutableList<Task>? = null
        val iter = tasks.iterator()
        while (iter.hasNext()) {
            val task = iter.next()
            if (task.cancelled || !task.level.server.isRunning) {
                iter.remove()
                continue
            }
            if (task.level.gameTime >= task.dueAt) {
                iter.remove()
                (due ?: ArrayList<Task>().also { due = it }).add(task)
            }
        }
        due?.forEach { task ->
            runCatching { task.action() }.onFailure { LOGGER.warn("[PrimalSpells] 延迟任务执行失败", it) }
        }
    }

    /** 服务端停机时清空（`MagicManager` 的 `ServerStoppingEvent` 里调）。 */
    fun clear() {
        tasks.clear()
    }
}
