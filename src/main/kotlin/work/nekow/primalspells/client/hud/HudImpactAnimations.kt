package work.nekow.primalspells.client.hud

import kotlin.math.pow

/** 单次命中/击杀的只读画面；slot在出生时固定，后续事件不能挪动已有笔画。 */
data class HudImpactFrame(
    val id: Long, val killed: Boolean, val slot: Int,
    val alpha: Double, val draw: Double, val spread: Double,
)

/** 客户端独立反馈队列；一次伤害通知对应一次动画，致死命中只生成红色击杀反馈。 */
class HudImpactAnimations {
    private data class Impact(val id: Long, val killed: Boolean, val slot: Int, val start: Double) {
        val duration get() = if (killed) 8.0 else 6.0
    }
    private val active = arrayListOf<Impact>()
    private var nextId = 0L

    /** 使用HUD游戏时钟记录事件；同一刻的通知也各占一个位置，不合并或延长。 */
    fun add(killed: Boolean, now: Double) {
        prune(now)
        val occupied = active.asSequence().filter { it.killed == killed }.map { it.slot }.toHashSet()
        var slot = 0
        while (slot in occupied) slot++
        active += Impact(nextId++, killed, slot, now)
    }

    /** 按绝对年龄求值并回收到期事件；隐藏HUD期间不暂停，也不积压过时反馈。 */
    fun sample(now: Double): List<HudImpactFrame> {
        prune(now)
        return active.map {
            val age = (now - it.start).coerceAtLeast(0.0)
            val fadeStart = if (it.killed) 2.0 else 1.0
            val exit = smooth((age - fadeStart) / (it.duration - fadeStart))
            HudImpactFrame(it.id, it.killed, it.slot, smooth(age / 0.6) * (1.0 - exit).pow(1.25),
                smooth(age / if (it.killed) 1.5 else 0.8), smooth((age - 2.0) / (it.duration - 2.0)))
        }
    }

    /** 退出世界时清空事件和序号。 */
    fun clear() { active.clear(); nextId = 0L }

    private fun prune(now: Double) { active.removeAll { now - it.start >= it.duration } }
    private fun smooth(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}
