package work.nekow.primalspells.client.hud

import kotlin.math.*

/** 相对原版准星中心的短笔画；保留小数位置和长度，避免逐像素取整造成停顿。 */
data class HudImpactStroke(val x: Double, val y: Double, val width: Double, val height: Double, val core: Boolean)

/** 单个目标的反馈画面；击杀接续命中残影，不多占一个目标。 */
data class HudImpactFrame(
    val id: Long, val killed: Boolean,
    val alpha: Double, val draw: Double, val spread: Double,
    val targetId: Int = -1, val previous: HudImpactFrame? = null,
) {
    /** 命中固定正十字、击杀固定斜十字；不同目标允许完全重叠，不做旋转或错位。 */
    fun strokes(scale: Double): List<HudImpactStroke> {
        val inner = ceil(10 / scale) + 5 * spread
        val maxLength = if (killed) 4 else 3
        val length = maxLength * draw
        val result = arrayListOf<HudImpactStroke>()
        for (arm in 0..3) for (step in 0 until maxLength) {
            val part = (length - step).coerceIn(0.0, 1.0)
            if (part <= 0.0) continue
            val d = inner + step
            val thickness = if (step == 0) 2.0 else 1.0
            if (killed) {
                val size = thickness * part
                val sx = if (arm < 2) 1 else -1
                val sy = if (arm % 2 == 0) 1 else -1
                result += HudImpactStroke(if (sx > 0) d else -d - size, if (sy > 0) d else -d - size,
                    size, size, step == 0)
            } else {
                val sign = if (arm < 2) 1 else -1
                val start = if (sign > 0) d else -d - part
                val across = -floor(thickness / 2)
                result += if (arm % 2 == 0) HudImpactStroke(start, across, part, thickness, step == 0)
                    else HudImpactStroke(across, start, thickness, part, step == 0)
            }
        }
        return result
    }
}

/** 客户端按目标管理短反馈；同一窗口内的重复命中不创建、延长或挪动笔画。 */
class HudImpactAnimations {
    private data class Impact(val id: Long, val targetId: Int, val killed: Boolean,
                              val start: Double, val previous: Impact? = null) {
        val duration get() = if (killed) 8.0 else 6.0
        private val origin = previous?.sample(start)?.spread ?: 0.0

        fun sample(now: Double): HudImpactFrame {
            val age = (now - start).coerceAtLeast(0.0)
            val progress = (age / duration).coerceIn(0.0, 1.0)
            // 单段外移从出生持续到归零；描线、升亮和淡出同时发生，没有独立停留阶段。
            val spread = origin + progress
            return HudImpactFrame(id, killed, smooth(age / 0.6) * (1.0 - smooth(progress)).pow(1.25),
                smooth(age / 0.6), spread, targetId,
                previous?.takeIf { age < 0.8 }?.sample(now)?.let {
                    it.copy(alpha = it.alpha * (1 - smooth(age / 0.8)))
                })
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
            if (killed && !existing.killed) active[index] = Impact(existing.id, targetId, true, now, existing)
            return
        }
        active += Impact(nextId++, targetId, killed, now)
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
