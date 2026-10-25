package work.nekow.primalspells.client.capture

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.core.client.ClientParticleEngine

/** 只读观察同批原生片元的可见面积/光强；不把不同时间、不同位置的效果峰值拼作一次退场。 */
internal class SpellExitChecks {
    @Volatile var sawPosition: Vec3? = null
    @Volatile var sawEnded = false
    @Volatile var torchEnded = false
    private val saw = Exit("saw-fragments")
    private val torch = Exit("torch-light")
    private var sawIds = emptySet<UUID>()
    private var torchId: UUID? = null

    fun sample(tick: Int) {
        if (tick !in 70..125) return
        val center = sawPosition ?: return
        val ids = hashSetOf<UUID>()
        val areas = hashMapOf<UUID, Double>()
        var light = 0.0
        ClientParticleEngine.instance()?.forEachParticleSample(0f) { p ->
            if (!sawEnded && center.distanceToSqr(p.x, p.y, p.z) < 0.9 * 0.9) ids += p.id
            areas[p.id] = kotlin.math.abs(p.scaleX.toDouble() * p.scaleY) * p.alpha
            if (!torchEnded && p.lightLevel > 0) torchId = p.id
            if (p.id == torchId) light = p.lightIntensity.toDouble()
        }
        if (!sawEnded && ids.size >= 96) sawIds = ids
        saw.sample(tick, sawIds.sumOf { areas[it] ?: 0.0 }, sawEnded)
        torch.sample(tick, light, torchEnded)
    }

    fun checks(record: (String, Boolean, String) -> Unit) {
        record("saw-exit-identifies-original-body", sawIds.size >= 96, "same-particle IDs=${sawIds.size}")
        saw.check(record)
        torch.check(record)
    }

    private class Exit(private val name: String) {
        private data class Sample(val tick: Int, val value: Double)
        private val samples = CopyOnWriteArrayList<Sample>()
        private var before: Sample? = null
        fun sample(tick: Int, value: Double, ended: Boolean) {
            if (!ended) before = Sample(tick, value)
            else {
                if (samples.isEmpty()) before?.let(samples::add)
                samples += Sample(tick, value)
            }
        }
        fun check(record: (String, Boolean, String) -> Unit) {
            val peak = samples.maxOfOrNull { it.value } ?: 0.0
            val start = samples.indexOfFirst { it.value <= peak * .95 }
            val end = samples.indexOfFirst { it.value <= peak * .05 }
            val zero = samples.indexOfFirst { it.value <= 1e-8 }
            val span = if (start >= 0 && end >= start) samples[end].tick - samples[start].tick else -1
            val drop = samples.zipWithNext().maxOfOrNull { (a, b) -> (a.value - b.value).coerceAtLeast(0.0) / peak } ?: 1.0
            val last = if (zero > 0) samples[zero - 1].value / peak else 1.0
            val detail = "95→5=${span}t; maxDrop=$drop; beforeZero=$last; samples=$samples"
            record("$name-exits-continuously", peak > 0 && span in 3..10 && drop <= .35 && last <= .10 && zero >= 0, detail)
        }
    }
}
