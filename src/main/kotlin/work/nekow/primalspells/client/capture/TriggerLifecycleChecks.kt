package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.AppendedPayload
import work.nekow.primalspells.magic.core.DrawAction
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellAssembly
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3
import work.nekow.primalspells.wand.Wand

/** spell_chain调用；真实Move反弹与运行时终结的荷载时机，不用人工碰撞替代连续反弹。 */
object TriggerLifecycleChecks {
    fun run(ctx: CaptureDriver.SceneCtx, tick: Int, floor: Int): List<Check> {
        val checks = arrayListOf<Check>()
        fun record(id: String, ok: Boolean, detail: String) { checks += Check("spell_chain", tick, id, ok, detail) }
        class Carrier(val shot: Projectile, val child: Projectile) {
            var releases = 0
            var bornAlive = false
            var bornAt = Vec3.ZERO
            var heading = Vec3.ZERO
            fun hasPayload(): Boolean = (shot as? TriggerSpell)?.payload?.isNotEmpty()
                ?: shot.addedTriggers.single().cards.isNotEmpty()
        }
        fun carrier(appended: Boolean = false, piercing: Boolean = false,
                    kind: DrawAction.Kind = DrawAction.Kind.HIT, z: Double = 0.0): Carrier {
            val p = SpellRegistry.create(if (appended) "fireball" else "trigger_fireball") as Projectile
            SpellAssembly.apply(p, (listOf("bounce") + if (piercing) listOf("piercing") else emptyList()).map {
                SpellRegistry.create(it) as Modifier
            })
            p.effects.removeIf { it.phase == EffectPhase.VISUAL }
            p.caster = ctx.player; p.wand = Wand("trigger-lifecycle")
            p.position = Vec3(0.1, floor + 7.0, z).toPosition(); p.velocity = Vector3f(0f, 0f, 1f)
            val child = SpellRegistry.create("fireball") as Projectile
            child.effects.removeIf { it.phase == EffectPhase.VISUAL }
            child.collisionEnabled = false
            val fixture = Carrier(p, child)
            child.effects += object : BaseEffect() {
                override fun onActive() {
                    fixture.releases++; fixture.bornAlive = p.alive
                    fixture.bornAt = status.pos.toVec3(); fixture.heading = child.launchDirection
                }
            }
            val cards = arrayListOf(SpellRegistry.create("speed_boost")!!, child)
            if (appended) p.addedTriggers += AppendedPayload(kind, cards)
            else (p as TriggerSpell).payload += cards
            p.spell()
            return fixture
        }
        fun cancel(fixture: Carrier) {
            // 验收结束的取消不模拟玩法死亡；先丢弃未用荷载，再回收本夹具的粒子/运行时队列。
            (fixture.shot as? TriggerSpell)?.payload?.clear()
            fixture.shot.addedTriggers.forEach { it.cards.clear() }
            fixture.shot.alive = false; fixture.shot.die()
            fixture.child.alive = false; ProjectileRuntime.clear()
        }
        fun walls(solid: Boolean) {
            for (z in listOf(-4, 3)) for (y in floor + 5..floor + 9)
                ctx.level.setBlock(BlockPos(0, y, z), (if (solid) Blocks.STONE else Blocks.AIR).defaultBlockState(), 2)
        }

        record("trigger-fixture-starts-empty", ProjectileRuntime.projectiles.isEmpty(), "isolated lifecycle checks")
        walls(true)
        for ((label, appended, piercing) in listOf(Triple("native", false, false), Triple("piercing", false, true), Triple("appended", true, false))) {
            val f = carrier(appended, piercing)
            var walls = 0
            f.shot.effects += object : BaseEffect() { override fun onHitBlock(pos: Vector3d) { walls++ } }
            val victim = if (piercing) ctx.spawnDummy(0.1, floor + 6.0, 1.0) else null
            var waiting = true
            var elapsed = 0
            while (f.shot.alive && elapsed++ < 80) {
                f.shot.tick(); f.shot.status.age++
                if (f.shot.alive) waiting = waiting && f.hasPayload() && f.releases == 0
            }
            record("trigger-$label-keeps-payload-through-eight-bounces", waiting && walls == 9 && !f.shot.alive && f.releases == 1 && !f.bornAlive,
                "waiting=$waiting walls=$walls releases=${f.releases} bornAlive=${f.bornAlive}")
            record("trigger-$label-uses-final-contact-and-outgoing-direction", f.bornAt.distanceTo(f.shot.status.pos.toVec3()) <= 1.1e-7 &&
                f.heading.dot(Vec3(f.shot.status.velocity).normalize()) < -0.999,
                "birth=${f.bornAt} final=${f.shot.status.pos} heading=${f.heading}")
            record("trigger-$label-payload-remains-independent", f.child.modifiers.keys == setOf("speed_boost") && f.child.status.bounces == 0,
                "mods=${f.child.modifiers} childBounces=${f.child.status.bounces}")
            if (victim != null) {
                record("trigger-piercing-entity-contact-only-deals-carrier-damage", victim.totalDamage > 0.0 && f.shot.status.hitTargets > 0,
                    "damage=${victim.totalDamage} hits=${f.shot.status.hitTargets}")
                victim.discard()
            }
            f.shot.expire(); f.shot.die(); f.shot.die()
            record("trigger-$label-terminal-cleanup-never-repeats-payload", f.releases == 1 && !f.hasPayload(), "releases=${f.releases}")
            cancel(f)
        }

        val onEntity = carrier()
        val entity = ctx.spawnDummy(0.1, floor + 6.0, -2.0)
        var entityWalls = 0
        onEntity.shot.effects += object : BaseEffect() { override fun onHitBlock(pos: Vector3d) { entityWalls++ } }
        repeat(5) { if (onEntity.shot.alive) { onEntity.shot.tick(); onEntity.shot.status.age++ } }
        record("trigger-bounce-then-terminal-entity-hit-releases", entityWalls == 1 && entity.totalDamage == 4.0 &&
            !onEntity.shot.alive && onEntity.releases == 1 && onEntity.shot.status.bounces == 7,
            "walls=$entityWalls damage=${entity.totalDamage} releases=${onEntity.releases}")
        entity.discard(); cancel(onEntity)
        walls(false)

        for (appended in listOf(false, true)) {
            val f = carrier(appended)
            f.shot.maxAge = 2; f.shot.collisionEnabled = false
            ProjectileRuntime.add(f.shot); ProjectileRuntime.tick(); ProjectileRuntime.tick()
            record("trigger-expiry-waits-for-life-end-$appended", f.shot.alive && f.shot.status.age == 1 && f.releases == 0 && f.hasPayload(),
                "age=${f.shot.status.age} releases=${f.releases}")
            ProjectileRuntime.tick()
            record("trigger-expiry-releases-in-expiry-tick-$appended", !f.shot.alive && f.releases == 1 && !f.hasPayload() &&
                f.bornAt.distanceTo(f.shot.status.pos.toVec3()) < 1e-8 && f.child.status.age == 0,
                "age=${f.shot.status.age} releases=${f.releases} childAge=${f.child.status.age}")
            ProjectileRuntime.tick()
            record("trigger-expiry-following-cleanup-does-not-repeat-$appended", f.releases == 1 && f.child.status.age == 1, "releases=${f.releases}")
            cancel(f)

            val ended = carrier(appended)
            ProjectileRuntime.add(ended.shot); ProjectileRuntime.tick()
            ended.shot.alive = false; ProjectileRuntime.tick()
            record("trigger-external-end-releases-once-$appended", ended.releases == 1 && !ended.bornAlive && !ended.hasPayload(),
                "releases=${ended.releases} bornAlive=${ended.bornAlive}")
            ended.shot.die(); ended.shot.expire()
            record("trigger-external-end-hooks-are-idempotent-$appended", ended.releases == 1, "releases=${ended.releases}")
            cancel(ended)
        }
        val timer = carrier(appended = true, kind = DrawAction.Kind.TIMER)
        timer.shot.hitBlock(Vector3d(timer.shot.status.pos), Vector3f(0f, 0f, -1f))
        record("appended-timer-surviving-bounce-does-not-fire-early", timer.shot.alive && timer.hasPayload() && timer.releases == 0, "releases=${timer.releases}")
        timer.shot.collisionEnabled = false; timer.shot.status.age = 19; timer.shot.tick()
        record("appended-timer-still-fires-at-twenty-ticks", timer.shot.alive && timer.releases == 1 && !timer.hasPayload(), "releases=${timer.releases}")
        cancel(timer)
        record("trigger-fixture-cleans-active-and-pending", ProjectileRuntime.projectiles.isEmpty(), "next checks start empty")
        return checks
    }
}
