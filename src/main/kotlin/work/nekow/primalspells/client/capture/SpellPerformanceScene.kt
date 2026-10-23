package work.nekow.primalspells.client.capture

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.geometry.Move
import work.nekow.primalspells.magic.effect.lifecycle.SpellInteractions
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.Wand

/** 固定原生世界负载；物理与联动分别计时/计分配，不把帧率当服务器耗时。 */
object SpellPerformanceScene {
    fun scene(): Scene {
        val name = "spell_performance"
        var floor = 0
        val moving = arrayListOf<Projectile>()
        val inert = arrayListOf<Projectile>()
        val samples = arrayListOf<String>()
        val measured = linkedMapOf<String, MutableList<Double>>()
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        fun measure(t: Int, task: String, action: () -> Unit) {
            val thread = Thread.currentThread().threadId()
            val allocated = bean?.getThreadAllocatedBytes(thread) ?: -1
            val start = System.nanoTime()
            action()
            val ns = System.nanoTime() - start
            val bytes = bean?.getThreadAllocatedBytes(thread)?.minus(allocated) ?: -1
            if (t >= 14) {
                samples += "$task\t$t\t$ns\t$bytes"
                measured.getOrPut(task) { arrayListOf() } += ns / 1e6
            }
        }
        fun shot(ctx: CaptureDriver.SceneCtx, index: Int): Projectile {
            val p = SpellRegistry.create("fireball") as Projectile
            p.effects.removeIf { it.phase == EffectPhase.VISUAL }
            p.caster = ctx.player; p.wand = Wand("performance-$index")
            p.position = Vec3(0.125, floor + 8.25, -10.125).toPosition()
            p.velocity = Vector3f(1f, 0f, 0.37f).normalize()
            p.speed = 8f; p.dragCoeff = 0f; p.maxBounces = 1000; p.bounceScaler.set(1f)
            p.spell()
            return p
        }
        return Scene(name, "原生物理与联动 CPU / 分配基准", 24, 140,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                for (x in -15..15) for (z in -15..15) for (y in floor + 1..floor + 20)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(true, 8.0, floor + 12.0, -12.0, 35f, 30f)
                repeat(32) { moving += shot(ctx, it) }
                repeat(512) { inert += shot(ctx, it + 32) }
            }, camera = { _, _ -> Pose(8.0, floor + 12.0, -12.0, 35f, 30f) },
            tick = { ctx, t ->
                if (t in 8..30) {
                    for (guided in listOf(false, true)) {
                        for (p in moving) {
                            p.status.pos.set(0.125, floor + 8.25, -10.125)
                            p.status.velocity.set(1f, 0f, 0.37f).normalize().mul(64f)
                            p.status.bounces = 1000; p.flightPath.clear()
                            p.guidance = if (guided) Projectile.Guidance(Vec3(4.0, floor + 8.25, -2.0), 0.6f,
                                AABB(3.7, floor + 8.0, -2.3, 4.3, floor + 8.5, -1.7)) else null
                        }
                        measure(t, if (guided) "guided_move_32" else "straight_move_32") {
                            moving.forEach { p -> p.effects.filterIsInstance<Move>().single().onTick() }
                        }
                    }
                    measure(t, "inert_interactions_512") { SpellInteractions.tick(inert) }
                    inert.forEach { it.status.freezeTicks = 30 }
                    measure(t, "frozen_interactions_512") { SpellInteractions.tick(inert) }
                    inert.forEach { it.status.freezeTicks = 0 }
                }
                if (t in listOf(52, 80)) {
                    val p = SpellRegistry.create("concentrated_light") as ConcentratedLight
                    p.caster = ctx.player; p.wand = Wand("performance-display")
                    p.position = Vec3(-6.0, floor + 5.0, -5.0).toPosition()
                    p.velocity = Vector3f(1f, 0f, 0.4f).normalize()
                    p.spell(); ProjectileRuntime.add(p)
                }
                if (t == 115) ProjectileRuntime.projectiles.forEach { it.alive = false }
            }, checksAt = { _, t ->
                if (t == 139) measured.map { (task, values) ->
                    Check(name, t, "$task-samples", values.size == 17 && values.all { it.isFinite() && it > 0 },
                        "n=${values.size} medianMs=${values.sorted()[values.size / 2]}")
                } else emptyList()
            }, finish = {
                File(System.getProperty(CaptureDriver.PROP_OUT), "$name/performance.tsv").writeText(
                    "task\ttick\tnanoseconds\tallocatedBytes\n" + samples.joinToString("\n") + "\n")
                moving.clear(); inert.clear()
            })
    }
}
