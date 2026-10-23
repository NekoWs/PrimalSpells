package work.nekow.primalspells.client.hud

import kotlin.math.*

/** 相对原版准星中心的一小段像素笔画；几何同时供HUD绘制与原生回归检查使用。 */
data class HudImpactStroke(val x: Int, val y: Int, val width: Int, val height: Int, val core: Boolean)

/** 单个目标的反馈画面；击杀可接续该目标的命中残影，但不多占一个位置。 */
data class HudImpactFrame(
    val id: Long, val killed: Boolean, val slot: Int,
    val alpha: Double, val draw: Double, val spread: Double,
    val targetId: Int = -1, val previous: HudImpactFrame? = null,
) {
    /** 四段FPS命中短线共用原版准星中心；不同目标只改变少量角度，不创建旁置小准星。 */
    fun strokes(scale: Double): List<HudImpactStroke> {
        // 二进制反序给出0、-18、+18…的固定角度；再多目标也不向外堆叠半径。
        var bits = slot + 1
        var fraction = 0.0
        var weight = 0.5
        while (bits > 0) {
            fraction += (bits and 1) * weight
            bits = bits ushr 1; weight *= 0.5
        }
        val angle = Math.toRadians(72.0) * (fraction - 0.5)
        val cosine = cos(angle); val sine = sin(angle)
        val inner = ceil(10 / scale).toInt() + (2 * spread).roundToInt()
        val length = ceil((if (killed) 4 else 3) * draw).toInt()
        if (length <= 0) return emptyList()
        val result = arrayListOf<HudImpactStroke>()
        fun rotate(x: Int, y: Int) = (x * cosine - y * sine).roundToInt() to (x * sine + y * cosine).roundToInt()
        for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
            var (x, y) = rotate(sx * inner, sy * inner)
            val (endX, endY) = rotate(sx * (inner + length - 1), sy * (inner + length - 1))
            val dx = abs(endX - x); val dy = -abs(endY - y)
            val stepX = if (x < endX) 1 else -1; val stepY = if (y < endY) 1 else -1
            var error = dx + dy
            var head = true
            // 像素连线保证斜向也连续；不能只旋转离散点，留下断开的碎点。
            while (true) {
                val thickness = if (head) 2 else 1
                result += HudImpactStroke(x - if (x < 0) thickness - 1 else 0,
                    y - if (y < 0) thickness - 1 else 0, thickness, thickness, head)
                if (x == endX && y == endY) break
                val twice = 2 * error
                if (twice >= dy) { error += dy; x += stepX }
                if (twice <= dx) { error += dx; y += stepY }
                head = false
            }
        }
        return result
    }

}

/** 客户端按目标管理短反馈；同一窗口内的重复命中不创建、延长或挪动笔画。 */
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
