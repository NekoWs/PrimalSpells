package work.nekow.primalspells.client.capture

import work.nekow.primalspells.client.hud.HudImpactAnimations
import work.nekow.primalspells.client.hud.HudImpactFrame
import kotlin.math.*

/** 原生HUD场景调用实际队列与布局，检查按目标去重、击杀接续及准星保护区。 */
object HudImpactChecks {
    fun run(scene: String, tick: Int): List<Check> {
        val checks = arrayListOf<Check>()
        fun record(id: String, ok: Boolean, detail: String) { checks += Check(scene, tick, id, ok, detail) }
        val queue = HudImpactAnimations()
        repeat(64) { queue.add(10, false, 0.0) }
        val born = queue.sample(0.0)
        record("same-target-burst-shows-one-star", born.size == 1 && born.single().targetId == 10,
            "64 hits, frames=$born")
        record("impacts-start-transparent", born.all { it.alpha == 0.0 && it.draw == 0.0 && it.spread == 0.0 }, "born=$born")
        val old = queue.sample(4.0).single()
        repeat(64) { queue.add(10, false, 4.0) }
        record("same-target-does-not-rewind-or-extend", queue.sample(4.0).single() == old && queue.sample(6.0).isEmpty(),
            "original=$old")
        queue.clear()
        repeat(3) { queue.add(it, false, 0.0) }
        val targets = queue.sample(1.0)
        record("different-targets-have-independent-stars", targets.size == 3 && targets.map { it.slot }.distinct().size == 3,
            "targets=$targets")
        val beforeKill = queue.sample(4.0)
        queue.add(0, true, 4.0)
        val promoted = queue.sample(4.0)
        val kill = promoted.single { it.targetId == 0 }
        record("kill-promotes-target-without-adding-marker", promoted.size == 3 && kill.killed &&
            kill.id == beforeKill[0].id && kill.slot == beforeKill[0].slot && kill.spread == beforeKill[0].spread &&
            kill.previous == beforeKill[0] && kill.alpha == 0.0, "promoted=$kill")
        record("promotion-keeps-other-targets-unchanged", promoted.drop(1) == beforeKill.drop(1), "other=${promoted.drop(1)}")
        val continuing = queue.sample(4.4)
        queue.add(0, true, 4.4)
        record("duplicate-kill-does-not-restart", continuing == queue.sample(4.4), "same target duplicate")
        record("promotion-crossfades-continuously", queue.sample(4.4).single { it.killed }.let {
            it.alpha > 0.0 && it.previous != null && it.previous.alpha in 0.0..beforeKill[0].alpha
        } && queue.sample(5.0).single { it.killed }.previous == null, "transition=0.8t")
        record("impacts-no-backlog-after-hidden-hud", queue.sample(100.0).isEmpty(), "jump=100t")
        repeat(128) { queue.add(it, it % 3 == 0, 100.0) }
        val burst = queue.sample(101.0)
        record("different-target-burst-keeps-every-target", burst.size == 128 && burst.map { it.slot }.distinct().size == 128,
            "targets=${burst.size}")
        queue.clear()
        var singleTarget = true
        repeat(32) { t ->
            repeat(16) { queue.add(7, false, t.toDouble()) }
            singleTarget = singleTarget && queue.sample(t + 0.5).size == 1
        }
        record("sustained-single-target-never-stacks", singleTarget, "32t, 16 accepted hits per tick")
        queue.clear()
        record("impact-world-clear-releases-all", queue.sample(101.0).isEmpty(), "cleared")
        for (killed in listOf(false, true)) {
            queue.add(1, killed, 0.0)
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
        val markers = (0..2).map { HudImpactFrame(it.toLong(), false, it, 1.0, 1.0, 0.0).strokes(1.0) }
        record("fps-markers-share-crosshair-and-differ-by-angle", markers.distinct().size == 3 && markers.all { strokes ->
            strokes.count { it.core } == 4 && strokes.any { it.x > 0 && it.y > 0 } &&
                strokes.any { it.x < 0 && it.y < 0 } && strokes.any { it.x < 0 && it.y > 0 } &&
                strokes.any { it.x > 0 && it.y < 0 }
        }, "three targets, four diagonal arms each")
        record("fps-markers-fit-and-protect-crosshair-at-all-scales", listOf(0.6, 1.0, 2.5).all { scale ->
            (0..127).all { slot -> listOf(0.0, 1.0).all { spread -> listOf(false, true).all { killed ->
                HudImpactFrame(slot.toLong(), killed, slot, 1.0, 1.0, spread).strokes(scale).all { stroke ->
                    (stroke.x until stroke.x + stroke.width).all { x -> (stroke.y until stroke.y + stroke.height).all { y ->
                        max(abs(x), abs(y)) * scale >= 10.0 && max(abs(x), abs(y)) <= ceil(10 / scale) * 1.42 + 10
                    } }
                }
            } } }
        }, "128 markers, entry/exit, hit/kill, scales=60/100/250%")
        return checks
    }
}
