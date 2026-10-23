package work.nekow.primalspells.client.capture

import work.nekow.primalspells.client.hud.HudImpactAnimations
import kotlin.math.abs

/** 在原生HUD场景内调用真实队列，检查并发独立性、连续性与有限回收。 */
object HudImpactChecks {
    fun run(scene: String, tick: Int): List<Check> {
        val checks = arrayListOf<Check>()
        fun record(id: String, ok: Boolean, detail: String) { checks += Check(scene, tick, id, ok, detail) }
        val queue = HudImpactAnimations()
        repeat(3) { queue.add(false, 0.0) }
        repeat(2) { queue.add(true, 0.0) }
        val born = queue.sample(0.0)
        record("impacts-same-tick-remain-independent", born.size == 5 && born.map { it.id }.distinct().size == 5 &&
            born.filterNot { it.killed }.map { it.slot } == listOf(0, 1, 2) &&
            born.filter { it.killed }.map { it.slot } == listOf(0, 1), "born=$born")
        record("impacts-start-transparent", born.all { it.alpha == 0.0 && it.draw == 0.0 && it.spread == 0.0 }, "born=$born")
        val old = queue.sample(4.0)
        queue.add(false, 4.0); queue.add(true, 4.0)
        val after = queue.sample(4.0)
        record("new-impact-does-not-rewind-or-extend-old", after.filter { it.id < 5 } == old && after.size == 7,
            "before=$old after=$after")
        record("kill-does-not-hide-other-hits", after.any { !it.killed && it.alpha > 0.0 } &&
            after.any { it.killed && it.alpha > 0.0 }, "active=${after.size}")
        val expired = queue.sample(8.0)
        record("impacts-expire-on-original-schedule", expired.size == 2 && expired.all { it.id >= 5 }, "active=$expired")
        record("impacts-no-backlog-after-hidden-hud", queue.sample(100.0).isEmpty(), "jump=100t")
        repeat(128) { queue.add(it % 3 == 0, 100.0) }
        val burst = queue.sample(101.0)
        record("impact-burst-keeps-every-event", burst.size == 128 && burst.groupBy { it.killed }.values.all {
            group -> group.map { it.slot }.distinct().size == group.size }, "events=${burst.size}")
        queue.clear()
        record("impact-world-clear-releases-all", queue.sample(101.0).isEmpty(), "cleared")
        for (killed in listOf(false, true)) {
            queue.add(killed, 0.0)
            val duration = if (killed) 8 else 6
            val samples = (0 until duration * 4).map { queue.sample(it / 4.0).single() }
            record("impact-continuous-${if (killed) "kill" else "hit"}",
                samples.zipWithNext().all { (a, b) -> b.draw >= a.draw && b.spread >= a.spread && abs(b.alpha - a.alpha) < 0.61 } &&
                    (2 until duration).all { t ->
                        val a = samples[t * 4].alpha
                        val b = samples.getOrNull((t + 1) * 4)?.alpha ?: 0.0
                        a - b <= 0.35
                    } && samples.last().alpha < 0.02 && queue.sample(duration.toDouble()).isEmpty(),
                "duration=$duration finalAlpha=${samples.last().alpha}")
            queue.clear()
        }
        return checks
    }
}
