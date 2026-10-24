package work.nekow.primalspells.client.capture

import java.util.UUID
import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import org.joml.Vector3f
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.integration.SpellEvents
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.DirectDamage
import work.nekow.primalspells.magic.effect.lifecycle.PlasmaBeam
import work.nekow.primalspells.magic.projectile.ConcentratedLight
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.Wand

/** piercing_contact调用；观察正式AfterImpact事件与真实木桩总伤，逐渠道检查结算次数。 */
object DamageFrequencyChecks {
    class ImpactProbe {
        var targets = emptySet<UUID>()
        val impacts = arrayListOf<SpellEvents.AfterImpact>()
        fun watch(vararg entities: TestDummyEntity) { targets = entities.map { it.uuid }.toSet(); impacts.clear() }
        fun count(target: TestDummyEntity, type: SpellDamageType) = impacts.count { it.target === target && it.type == type }
        @SubscribeEvent fun after(event: SpellEvents.AfterImpact) {
            if (event.target.uuid in targets) impacts += event
        }
    }

    fun run(ctx: CaptureDriver.SceneCtx, tick: Int, floor: Int): List<Check> {
        val checks = arrayListOf<Check>()
        fun record(id: String, ok: Boolean, detail: String) { checks += Check("piercing_contact", tick, id, ok, detail) }
        val probe = ImpactProbe()
        val y = floor + 6.0
        fun target(z: Double = 0.0, height: Double = y): TestDummyEntity = ctx.spawnDummy(0.0, height, z)
        fun shot(id: String, start: Vec3, speed: Float? = null, mods: List<String> = emptyList()): Projectile {
            val p = SpellRegistry.create(id) as Projectile
            SpellAssembly.apply(p, mods.map { SpellRegistry.create(it) as Modifier })
            // 束/场同时承担玩法结算；不能因其VISUAL阶段而在夹具中移除。
            p.effects.removeIf { it.phase == EffectPhase.VISUAL && it !is DirectDamage }
            if (speed != null) p.speed = speed / Projectile.LAUNCH_SPEED_SCALE
            p.caster = ctx.player; p.wand = Wand("frequency-$id")
            p.position = start.toPosition(); p.velocity = Vector3f(0f, 0f, 1f); p.spell()
            if (p is ConcentratedLight) p.status.age = ConcentratedLight.CHARGE_TICKS
            return p
        }
        fun dispose(p: Projectile, vararg targets: TestDummyEntity) {
            p.alive = false; p.die(); targets.forEach { it.discard() }
        }
        fun wall(z: Int, solid: Boolean) {
            for (height in floor + 5..floor + 9) ctx.level.setBlock(BlockPos(0, height, z),
                (if (solid) Blocks.STONE else Blocks.AIR).defaultBlockState(), 2)
        }

        NeoForge.EVENT_BUS.register(probe)
        try {
            for (id in listOf("fireball", "trigger_fireball", "drone_bolt", "laser", "chainsaw", "concentrated_light")) {
                // 在摄影棚墙顶上方检查单次穿过，真实反弹由独立围墙夹具覆盖。
                val dummy = target(height = floor + 40.0); probe.watch(dummy)
                val p = shot(id, Vec3(0.0, dummy.boundingBox.center.y, -1.0))
                p.tick()
                record("single-impact-$id", probe.impacts.size == 1 && dummy.totalDamage == p.status.damage && p.status.hitTargets == 1,
                    "events=${probe.impacts.size} hits=${p.status.hitTargets} damage=${dummy.totalDamage}")
                dispose(p, dummy)
            }
            // 聚光穿刺遵循接触时间；高速一次穿过只命中一次，减速确实停留更久才会多次命中。
            for ((speed, expected) in listOf(4f to 4, 8f to 2, 32f to 1, 64f to 1, 128f to 1)) {
                val dummy = target(height = floor + 40.0); probe.watch(dummy)
                val p = shot("concentrated_light", Vec3(0.0, dummy.boundingBox.center.y, -1.0), speed, listOf("piercing"))
                p.tick()
                record("piercing-light-frequency-$speed", probe.impacts.size == expected && dummy.totalDamage == expected * 36.0,
                    "events=${probe.impacts.size} damage=${dummy.totalDamage} expected=$expected")
                dispose(p, dummy)
            }
            wall(-4, true); wall(3, true)
            val bounced = target(); probe.watch(bounced)
            val light = shot("concentrated_light", Vec3(0.1, bounced.boundingBox.center.y, -1.0), mods = listOf("piercing"))
            light.tick()
            record("light-real-bounce-reentry-is-not-global-immunity", !light.alive && light.status.bounces == 0 && probe.impacts.size == 6 && bounced.totalDamage == 216.0,
                "events=${probe.impacts.size} damage=${bounced.totalDamage} wallsLeft=${light.status.bounces}")
            dispose(light, bounced)

            val sawBounceTarget = target(); probe.watch(sawBounceTarget)
            val sawBounce = shot("sawblade", Vec3(0.1, sawBounceTarget.boundingBox.center.y, -1.0), 64f)
            sawBounce.tick()
            record("saw-many-wall-bounces-one-damage-per-tick", probe.impacts.size == 1 && sawBounceTarget.totalDamage == 12.0,
                "events=${probe.impacts.size} damage=${sawBounceTarget.totalDamage} bounces=${sawBounce.status.bounces}")
            dispose(sawBounce, sawBounceTarget)
            wall(-4, false); wall(3, false)

            val sawTarget = target(); probe.watch(sawTarget)
            val saw = shot("sawblade", sawTarget.boundingBox.center, 0.001f, listOf("volatile"))
            saw.tick(); saw.status.age++
            record("saw-contact-modifiers-share-frame-budget", probe.count(sawTarget, SpellDamageType.SLICE) == 1 &&
                probe.count(sawTarget, SpellDamageType.EXPLOSION) == 1 && saw.status.hitTargets == 1,
                "slice=${probe.count(sawTarget, SpellDamageType.SLICE)} blasts=${probe.count(sawTarget, SpellDamageType.EXPLOSION)} callbacks=${saw.status.hitTargets}")
            repeat(4) { saw.hitEntity(sawTarget) }
            record("saw-same-tick-contact-replay-does-not-repeat-modifiers", probe.impacts.size == 2,
                "events=${probe.impacts.size} callbacks=${saw.status.hitTargets}")
            saw.tick(); saw.status.age++
            record("saw-next-tick-resets-entire-contact-budget", probe.count(sawTarget, SpellDamageType.SLICE) == 2 &&
                probe.count(sawTarget, SpellDamageType.EXPLOSION) == 2 && saw.status.hitTargets == 2,
                "slice=${probe.count(sawTarget, SpellDamageType.SLICE)} blasts=${probe.count(sawTarget, SpellDamageType.EXPLOSION)} callbacks=${saw.status.hitTargets}")
            dispose(saw, sawTarget)

            val sawA = target(); val sawB = target(); probe.watch(sawA, sawB)
            val multiSaw = shot("sawblade", sawA.boundingBox.center, 0.001f)
            multiSaw.tick(); multiSaw.status.age++; multiSaw.tick()
            record("saw-frame-budget-is-per-target", sawA.totalDamage == 24.0 && sawB.totalDamage == 24.0,
                "a=${sawA.totalDamage} b=${sawB.totalDamage}")
            dispose(multiSaw, sawA, sawB)

            for (speed in listOf(0f, 2f, 16f)) {
                val a = target(speed + 2.0, floor + 40.0); val b = target(speed + 4.0, floor + 40.0); probe.watch(a, b)
                val origin = Vec3(0.0, a.boundingBox.center.y, 0.0)
                val plasma = shot("plasma_beam", origin, speed, listOf("piercing", "diffusion"))
                plasma.tick(); plasma.status.age++
                record("plasma-one-hit-per-target-at-speed-$speed", a.totalDamage == 1.5 && b.totalDamage == 1.5,
                    "a=${a.totalDamage} b=${b.totalDamage}")
                val beam = plasma.effects.filterIsInstance<PlasmaBeam>().single()
                repeat(3) { beam.onTick() }
                record("plasma-same-tick-beam-replay-$speed", a.totalDamage == 1.5 && b.totalDamage == 1.5,
                    "a=${a.totalDamage} b=${b.totalDamage}")
                plasma.status.pos.set(origin.toPosition())
                plasma.tick()
                record("plasma-next-tick-is-not-permanent-immunity-$speed", a.totalDamage == 3.0 && b.totalDamage == 3.0,
                    "a=${a.totalDamage} b=${b.totalDamage}")
                dispose(plasma, a, b)
            }

            for (id in listOf("sawblade", "plasma_beam")) {
                val dummy = target(); probe.watch(dummy)
                val start = if (id == "sawblade") dummy.boundingBox.center else dummy.boundingBox.center.add(0.0, 0.0, -1.0)
                val original = shot(id, start, if (id == "sawblade") 0.001f else 0f)
                val copy = SpellAssembly.copy(original)
                copy.effects.removeIf { it.phase == EffectPhase.VISUAL && it !is DirectDamage }
                copy.caster = ctx.player; copy.wand = original.wand; copy.position = start.toPosition()
                copy.speed = original.speed; copy.velocity = Vector3f(0f, 0f, 1f); copy.spell()
                original.tick(); copy.tick()
                val expected = if (id == "sawblade") 24.0 else 3.0
                record("independent-frame-budgets-$id", dummy.totalDamage == expected && probe.impacts.size == 2,
                    "events=${probe.impacts.size} damage=${dummy.totalDamage}")
                dispose(original); dispose(copy, dummy)
            }

            val holeTarget = target(); probe.watch(holeTarget)
            val hole = shot("black_hole", holeTarget.boundingBox.center)
            repeat(3) { hole.tick(); hole.status.age++ }
            record("black-hole-one-core-hit-per-tick", holeTarget.totalDamage == 12.0 && probe.impacts.size == 3,
                "events=${probe.impacts.size} damage=${holeTarget.totalDamage}")
            hole.effects.filterIsInstance<work.nekow.primalspells.magic.effect.lifecycle.BlackHole>().single().onTick()
            record("black-hole-same-tick-core-replay", holeTarget.totalDamage == 12.0 && probe.impacts.size == 3,
                "events=${probe.impacts.size} damage=${holeTarget.totalDamage}")
            dispose(hole, holeTarget)

            val arcTargets = listOf(target(0.5), target(1.0), target(1.5))
            probe.watch(*arcTargets.toTypedArray())
            val arc = shot("chain_bolt", Vec3(0.0, arcTargets.first().boundingBox.center.y, 0.0))
            repeat(15) { arc.tick(); arc.status.age++ }
            record("chain-bolt-only-four-jump-impacts", probe.impacts.size == 4 && arcTargets.sumOf { it.totalDamage } == 16.0,
                "events=${probe.impacts.size} total=${arcTargets.sumOf { it.totalDamage }}")
            dispose(arc, *arcTargets.toTypedArray())

            val blastTarget = target(); probe.watch(blastTarget)
            val blast = shot("fireball", Vec3(0.0, blastTarget.boundingBox.center.y, -1.0), mods = listOf("volatile"))
            blast.tick(); blast.die()
            record("terminal-hit-and-death-do-not-double-explode", probe.count(blastTarget, SpellDamageType.PROJECTILE) == 1 &&
                probe.count(blastTarget, SpellDamageType.EXPLOSION) == 1 && abs(blastTarget.totalDamage - probe.impacts.sumOf { it.amount }) < 0.001,
                "events=${probe.impacts.size} damage=${blastTarget.totalDamage}")
            blastTarget.discard()
        } finally {
            NeoForge.EVENT_BUS.unregister(probe)
        }
        return checks
    }
}
