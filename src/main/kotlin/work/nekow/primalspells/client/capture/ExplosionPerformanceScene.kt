package work.nekow.primalspells.client.capture

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import jdk.jfr.Configuration
import jdk.jfr.Recording
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.core.client.ClientParticleEngine
import work.nekow.primalspells.magic.effect.lifecycle.ExplosionFx

/** 真实爆炸表现的单发/八发压力与JFR采样；不替换粒子引擎，不把截图FPS当性能。 */
object ExplosionPerformanceScene {
    fun scene(): Scene {
        val name = "explosion_performance"
        val samples = arrayListOf("tick\tbursts\tnanoseconds\tallocatedBytes\timmediateParticles")
        val counts = arrayListOf("tick\tserverParticles")
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        var recording: Recording? = null
        var peak = 0
        val clientPeak = AtomicInteger()
        val clientRemaining = AtomicInteger()
        val clientLights = AtomicInteger()
        val shapes = ConcurrentHashMap<Int, String>()
        var emissions = 0
        var floor = 0
        return Scene(name, "爆炸单发 / 八发压力 / CPU与分配采样", 24, 212,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor, half = 32, clearInterior = true)
                ctx.setup(true, 0.5, floor + 7.0, -22.0, 0f, 10f)
                recording = Recording(Configuration.getConfiguration("profile")).also { it.start() }
            },
            camera = { _, _ -> Pose(0.5, floor + 7.0, -22.0, 0f, 10f) },
            tick = { ctx, t ->
                val manager = ParticleManager.of(ctx.level)
                if (t in listOf(8, 48, 88, 128)) {
                    val bursts = if (t == 128) 8 else 1
                    val thread = Thread.currentThread().threadId()
                    val allocated = bean?.getThreadAllocatedBytes(thread) ?: -1
                    val start = System.nanoTime()
                    repeat(bursts) { i ->
                        ExplosionFx.burst(manager, Vec3((i % 3 - 1) * 4.0, floor + 3.0, (i / 3) * 3.0), 6f)
                    }
                    val ns = System.nanoTime() - start
                    val bytes = bean?.getThreadAllocatedBytes(thread)?.minus(allocated) ?: -1
                    samples += "$t\t$bursts\t$ns\t$bytes\t${manager.getEngine().particleCount()}"
                    emissions += bursts
                }
                val count = manager.getEngine().particleCount()
                peak = maxOf(peak, count)
                counts += "$t\t$count"
            },
            clientTick = { _, t ->
                ClientParticleEngine.instance()?.let { engine ->
                    val count = engine.activeCount()
                    clientRemaining.set(count)
                    clientPeak.accumulateAndGet(count, ::maxOf)
                    if (t in listOf(14, 54, 94, 138)) {
                        var visible = 0
                        var x = 0.0
                        var y = 0.0
                        var z = 0.0
                        var lights = 0
                        engine.forEachParticleSample(1f) { sample ->
                            if (sample.lightLevel > 0) lights++
                            if (sample.alpha > 0 && sample.scaleX > 0 && sample.scaleY > 0) {
                                visible++
                                x += sample.x + 4.0
                                y += sample.y - (floor + 3.0)
                                z += sample.z
                            }
                        }
                        clientLights.accumulateAndGet(lights, ::maxOf)
                        if (t != 138) shapes[t] = "$visible:${x.toInt()}:${y.toInt()}:${z.toInt()}"
                    }
                }
            },
            checksAt = { ctx, t ->
                if (t == 211) listOf(
                    Check(name, t, "fixed-workload-completed", emissions == 11 && samples.size == 5,
                        "bursts=$emissions measured=${samples.size - 1} serverPeak=$peak clientPeak=${clientPeak.get()}"),
                    Check(name, t, "bounded-eight-burst-budget", peak in 1..1600, "serverPeak=$peak budget=1600"),
                    Check(name, t, "native-client-rendered-particles", clientPeak.get() > 0, "peak=${clientPeak.get()}"),
                    Check(name, t, "independent-burst-shapes", shapes.size == 3 && shapes.values.toSet().size == 3,
                        "actual-position-signatures=$shapes"),
                    Check(name, t, "default-explosion-has-no-light-sources", clientLights.get() == 0,
                        "peakLightSources=${clientLights.get()}"),
                    Check(name, t, "native-server-cleaned", ParticleManager.of(ctx.level).getEngine().particleCount() == 0,
                        "remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"),
                    Check(name, t, "native-client-cleaned", clientRemaining.get() == 0,
                        "remaining=${clientRemaining.get()}"),
                ) else emptyList()
            },
            finish = {
                val dir = File(System.getProperty(CaptureDriver.PROP_OUT), name)
                File(dir, "performance.tsv").writeText(samples.joinToString("\n") + "\n")
                File(dir, "particles.tsv").writeText(counts.joinToString("\n") + "\n")
                File(dir, "shapes.tsv").writeText("tick\tvisible:sumX:sumY:sumZ\n" +
                    shapes.toSortedMap().entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
                recording?.let { record ->
                    record.stop()
                    record.dump(File(dir, "explosion.jfr").toPath())
                    record.close()
                }
            },
        )
    }
}
