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
        val renewed = queue.sample(4.0).single()
        record("same-target-renews-with-moving-tail", renewed.id == old.id && renewed.alpha == 0.0 &&
            renewed.previous == old && queue.sample(4.4).single().previous!!.spread > old.spread,
            "original=$old renewed=$renewed")
        val after = queue.sample(4.4)
        repeat(64) { queue.add(10, false, 4.4) }
        record("same-tick-hits-do-not-restart-handoff", queue.sample(4.4) == after, "64 hits within 1t")
        record("renewed-hit-finishes-after-last-contact", queue.sample(6.0).isNotEmpty() && queue.sample(10.0).isEmpty(),
            "last contact=4t, finite lifetime=6t")
        queue.clear()
        repeat(3) { queue.add(it, false, 0.0) }
        val targets = queue.sample(1.0)
        record("different-targets-have-independent-stars", targets.size == 3 && targets.map { it.targetId }.distinct().size == 3,
            "targets=$targets")
        val beforeKill = queue.sample(4.0)
        queue.add(0, true, 4.0)
        val promoted = queue.sample(4.0)
        val kill = promoted.single { it.targetId == 0 }
        record("kill-promotes-target-without-adding-marker", promoted.size == 3 && kill.killed &&
            kill.id == beforeKill[0].id && kill.spread == beforeKill[0].spread &&
            kill.previous == beforeKill[0] && kill.alpha == 0.0, "promoted=$kill")
        record("promotion-keeps-other-targets-unchanged", promoted.drop(1) == beforeKill.drop(1), "other=${promoted.drop(1)}")
        val continuing = queue.sample(4.4)
        queue.add(0, true, 4.4)
        record("duplicate-kill-does-not-restart", continuing == queue.sample(4.4), "same target duplicate")
        record("promotion-crossfades-continuously", queue.sample(4.4).single { it.killed }.let {
            it.alpha > 0.0 && it.previous != null && it.previous.alpha in 0.0..beforeKill[0].alpha && it.previous.spread > beforeKill[0].spread
        } && queue.sample(5.0).single { it.killed }.previous == null, "transition=0.8t")
        record("impacts-no-backlog-after-hidden-hud", queue.sample(100.0).isEmpty(), "jump=100t")
        repeat(128) { queue.add(it, it % 3 == 0, 100.0) }
        val burst = queue.sample(101.0)
        record("different-target-burst-keeps-every-target", burst.size == 128 && burst.map { it.id }.distinct().size == 128,
            "targets=${burst.size}")
        queue.clear()
        var singleTarget = true
        var continuous = true
        var bounded = true
        fun energy(frame: HudImpactFrame): Double = frame.alpha * frame.draw + (frame.previous?.let { energy(it) } ?: 0.0)
        repeat(64) { t ->
            repeat(16) { queue.add(7, false, t.toDouble()) }
            singleTarget = singleTarget && queue.sample(t + 0.5).size == 1
            if (t > 0) for (step in 0 until 16) {
                val frame = queue.sample(t + step / 16.0).single()
                continuous = continuous && energy(frame) > 0.25
                bounded = bounded && frame.spread < 1 && frame.previous?.previous == null
            }
        }
        record("sustained-single-target-never-stacks", singleTarget, "64t, 16 accepted hits per tick")
        record("sustained-hits-never-go-dark", continuous, "320Hz samples throughout repeated contacts")
        record("renewals-do-not-drift-or-retain-history", bounded, "one outgoing hit layer, bounded crosshair position")
        queue.clear()
        queue.add(3, false, 0.0)
        queue.add(3, false, 1.0)
        val overlap = queue.sample(1.2).single()
        queue.add(3, true, 1.2)
        val interrupted = queue.sample(1.2).single()
        record("kill-during-hit-renewal-preserves-all-tails", interrupted.killed && interrupted.previous == overlap &&
            interrupted.previous.previous != null && energy(interrupted) == energy(overlap), "nested=$interrupted")
        val dead = queue.sample(1.5)
        repeat(32) { queue.add(3, false, 1.5); queue.add(3, true, 1.5) }
        record("late-hit-cannot-downgrade-kill", queue.sample(1.5) == dead, "duplicate/late packets ignored after kill")
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
        for (killed in listOf(false, true)) {
            val markers = (0..2).map { HudImpactFrame(it.toLong(), killed, 1.0, 1.0, 0.0).strokes(1.0) }
            record("fixed-${if (killed) "diagonal-kill" else "cardinal-hit"}-overlaps", markers.distinct().size == 1 && markers.all { strokes ->
                strokes.count { it.core } == 4 && strokes.all { stroke ->
                    if (killed) abs(abs(stroke.x + stroke.width / 2) - abs(stroke.y + stroke.height / 2)) < 1e-8
                    else abs(stroke.x + stroke.width / 2) < 1 || abs(stroke.y + stroke.height / 2) < 1
                }
            }, "three target animations, identical fixed geometry")
            queue.clear(); queue.add(1, killed, 0.0)
            val duration = if (killed) 8 else 6
            val movement = (0 until duration * 16).map { queue.sample(it / 16.0).single().spread }
            record("no-hold-in-${if (killed) "kill" else "hit"}-motion", movement.zipWithNext().all { (a, b) -> b > a },
                "strict outward motion at 320Hz from birth to end")
        }
        record("fractional-stroke-entry-and-motion", HudImpactFrame(0, false, 1.0, 0.12, 0.01).strokes(1.0).first().let {
            it.x != floor(it.x) && it.width in 0.0..1.0 && it.width != floor(it.width)
        }, "subpixel travel and fractional stroke length")
        record("fixed-markers-protect-crosshair-at-all-scales", listOf(0.6, 1.0, 2.5).all { scale ->
            listOf(0.0, 0.5, 1.0, 2.0).all { spread -> listOf(false, true).all { killed ->
                HudImpactFrame(0, killed, 1.0, 1.0, spread).strokes(scale).all { stroke ->
                    val nearX = min(abs(stroke.x), abs(stroke.x + stroke.width))
                    val nearY = min(abs(stroke.y), abs(stroke.y + stroke.height))
                    max(nearX, nearY) * scale >= 10.0 && stroke.width <= 2.0 && stroke.height <= 2.0
                }
            } }
        }, "full entry/exit and kill handoff, scales=60/100/250%")
        return checks
    }
}
