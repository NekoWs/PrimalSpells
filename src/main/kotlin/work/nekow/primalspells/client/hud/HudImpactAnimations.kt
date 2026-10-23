package work.nekow.primalspells.client.hud

import kotlin.math.*

/** 单个目标的反馈画面；击杀可接续该目标的命中残影，但不多占一个位置。 */
data class HudImpactFrame(
    val id: Long, val killed: Boolean, val slot: Int,
    val alpha: Double, val draw: Double, val spread: Double,
    val targetId: Int = -1, val previous: HudImpactFrame? = null,
) {
    /** 完整小星芒围绕准星错开，低差异角度避免排成虚线；方形边界保证缩放后仍留空。 */
    fun center(scale: Double): Pair<Int, Int> {
        val angle = -PI / 5 + slot * PI * (3 - sqrt(5.0))
        val x = cos(angle); val y = sin(angle)
        val radius = ceil(10 / scale) + 5 + slot % 3 * 0.7 + 2 * spread
        val edge = max(abs(x), abs(y))
        return (x / edge * radius).roundToInt() to (y / edge * radius).roundToInt()
    }
}

/** 客户端按目标管理短反馈；同一窗口内的重复命中不创建、延长或挪动星芒。 */
class HudImpactAnimations {
    private data class Impact(val id: Long, val targetId: Int, val killed: Boolean, val slot: Int,
                              val start: Double, val previous: HudImpactFrame? = null) {
        val duration get() = if (killed) 8.0 else 6.0

        fun sample(now: Double): HudImpactFrame {
            val age = (now - start).coerceAtLeast(0.0)
            val fadeStart = if (killed) 2.0 else 1.0
            val exit = smooth((age - fadeStart) / (duration - fadeStart))
            val origin = previous?.spread ?: 0.0
            val spread = origin + (1 - origin) * smooth((age - 2.0) / (duration - 2.0))
            return HudImpactFrame(id, killed, slot, smooth(age / 0.6) * (1.0 - exit).pow(1.25),
                smooth(age / if (killed) 1.5 else 0.8), spread, targetId,
                previous?.takeIf { age < 0.8 }?.copy(alpha = previous.alpha * (1 - smooth(age / 0.8))))
        }
    }
    private val active = arrayListOf<Impact>()
    private var nextId = 0L

    /** 使用HUD游戏时钟和服务端目标ID去重；致死时在原位置平滑接成红色斜十字。 */
    fun add(targetId: Int, killed: Boolean, now: Double) {
        prune(now)
        val index = active.indexOfFirst { it.targetId == targetId }
        if (index >= 0) {
            val existing = active[index]
            if (killed && !existing.killed) active[index] = Impact(existing.id, targetId, true, existing.slot, now, existing.sample(now))
            return
        }
        val occupied = active.map { it.slot }.toHashSet()
        var slot = 0
        while (slot in occupied) slot++
        active += Impact(nextId++, targetId, killed, slot, now)
    }

    /** 按绝对年龄求值并回收到期事件；隐藏HUD期间不暂停，也不积压过时反馈。 */
    fun sample(now: Double): List<HudImpactFrame> {
        prune(now)
        return active.map { it.sample(now) }
    }

    /** 退出世界时清空事件和序号。 */
    fun clear() { active.clear(); nextId = 0L }

    private fun prune(now: Double) { active.removeAll { now - it.start >= it.duration } }
    private companion object {
        fun smooth(value: Double): Double {
            val t = value.coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }
    }
}
