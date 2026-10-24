package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.Wand

/** spell_chain调用；观察真正发射的独立子弹，覆盖重复穿刺、条件排除与一次性荷载。 */
object TriggerLifecycleChecks {
    fun run(ctx: CaptureDriver.SceneCtx, tick: Int, floor: Int): List<Check> {
        val checks = arrayListOf<Check>()
        fun record(id: String, ok: Boolean, detail: String) { checks += Check("spell_chain", tick, id, ok, detail) }
        fun cards(p: Projectile): ArrayList<Magic> = (p as? TriggerSpell)?.payload ?: p.addedTriggers.single().cards
        fun carrier(appended: Boolean = false, piercing: Boolean = false,
                    kind: DrawAction.Kind = DrawAction.Kind.HIT, id: String = "fireball"): Projectile {
            val p = SpellRegistry.create(if (appended) id else "trigger_fireball") as Projectile
            SpellAssembly.apply(p, (if (piercing) listOf("piercing") else emptyList()).map { SpellRegistry.create(it) as Modifier })
            p.effects.removeIf { it.phase == EffectPhase.VISUAL }
            p.speed = 0.5f // 两类载体统一4格/t标尺，分别完整穿过两个相隔5格的目标。
            p.caster = ctx.player; p.wand = Wand("trigger-hit-check")
            p.position = Vec3(0.0, floor + 7.0, -1.0).toPosition(); p.velocity = Vector3f(0f, 0f, 1f)
            val payload = arrayListOf(SpellRegistry.create("speed_boost")!!, SpellRegistry.create("fireball")!!)
            if (appended) p.addedTriggers += AppendedPayload(kind, payload) else (p as TriggerSpell).payload += payload
            p.spell()
            return p
        }
        fun discard(p: Projectile) {
            (p as? TriggerSpell)?.payload?.clear(); p.addedTriggers.forEach { it.cards.clear() }
            p.alive = false; p.die()
        }
        fun collect(retire: Boolean = true): List<Projectile> {
            // 新子弹在pendingAdd末尾入列，此时尚未移动，不会干扰载体的下一次命中。
            ProjectileRuntime.tick()
            return ProjectileRuntime.projectiles.toList().also { shots -> if (retire) shots.forEach { discard(it) } }
        }
        fun walls(solid: Boolean) {
            for (z in listOf(-4, 3)) for (y in floor + 5..floor + 9)
                ctx.level.setBlock(BlockPos(0, y, z), (if (solid) Blocks.STONE else Blocks.AIR).defaultBlockState(), 2)
        }

        record("trigger-fixture-starts-empty", ProjectileRuntime.projectiles.isEmpty(), "isolated hit checks")
        for (appended in listOf(false, true)) {
            walls(true)
            val bouncing = carrier(appended)
            bouncing.status.bounces = 8; bouncing.bounceScaler.set(1f)
            var bounces = 0
            while (bouncing.alive && bounces++ < 80) { bouncing.tick(); bouncing.status.age++ }
            record("hit-trigger-wall-bounces-never-release-$appended", !bouncing.alive && collect().isEmpty(), "age=${bouncing.status.age}")
            bouncing.expire(); bouncing.die()
            record("hit-trigger-terminal-hooks-never-release-$appended", cards(bouncing).isEmpty() && collect().isEmpty(), "walls/end are not enemy hits")
            walls(false)

            val target = ctx.spawnDummy(0.0, floor + 6.0, 0.0)
            val ordinary = carrier(appended)
            ordinary.tick()
            val single = collect()
            record("hit-trigger-on-first-enemy-contact-$appended", !ordinary.alive && single.size == 1 &&
                target.totalDamage == if (appended) 5.0 else 4.0, "children=${single.size} damage=${target.totalDamage}")
            discard(ordinary); target.discard()

            val first = ctx.spawnDummy(0.0, floor + 6.0, 0.0)
            val second = ctx.spawnDummy(0.0, floor + 6.0, 5.0)
            val p = carrier(appended, piercing = true)
            val template = cards(p).filterIsInstance<Projectile>().single()
            p.tick(); p.status.age++
            val wave1 = collect()
            record("piercing-each-contact-releases-$appended", p.alive && p.status.hitTargets == 4 && wave1.size == 4 &&
                first.totalDamage == 0.0, "hits=${p.status.hitTargets} children=${wave1.size} damage=${first.totalDamage}")
            record("hit-payload-local-modifiers-only-$appended", wave1.all { it !== template && it.status.damage == 5.0 &&
                it.modifiers == mapOf("speed_boost" to 1) && kotlin.math.abs(it.status.velocity.length() - 8f) < 1e-5 }, "mods=${wave1.map { it.modifiers }}")
            wave1.firstOrNull()?.status?.damage = 999.0
            p.tick(); p.status.age++
            val wave2 = collect()
            record("piercing-next-target-releases-fresh-wave-$appended", p.status.hitTargets == 8 && wave2.size == 4 &&
                wave2.all { it.status.damage == 5.0 } && wave1.none { a -> wave2.any { it === a } }, "hits=${p.status.hitTargets} children=${wave2.size}")
            record("hit-payload-template-stays-unmodified-$appended", cards(p).size == 2 && template.modifiers.isEmpty() &&
                template.status.age == 0 && template.status.damage == 5.0, "templateMods=${template.modifiers} age=${template.status.age}")
            discard(p); first.discard(); second.discard()
            record("piercing-cleanup-adds-no-extra-wave-$appended", collect().isEmpty(), "death does not fire hit payload")

            val expiry = carrier(appended); expiry.collisionEnabled = false; expiry.maxAge = 1
            ProjectileRuntime.add(expiry); ProjectileRuntime.tick(); ProjectileRuntime.tick()
            record("hit-trigger-expiry-is-not-a-hit-$appended", !expiry.alive && cards(expiry).isEmpty() &&
                ProjectileRuntime.projectiles.none { it !== expiry }, "age=${expiry.status.age}")
            ProjectileRuntime.tick()
            record("hit-trigger-expiry-cleanup-is-empty-$appended", ProjectileRuntime.projectiles.isEmpty(), "no fallback casts")
        }

        val timer = carrier(appended = true, kind = DrawAction.Kind.TIMER)
        timer.status.bounces = 1; timer.hitBlock(Vector3d(timer.status.pos), Vector3f(0f, 0f, -1f))
        record("appended-timer-keeps-bounce-condition", timer.alive && collect().isEmpty(), "not fired on surviving bounce")
        timer.collisionEnabled = false; timer.status.age = 19; timer.tick()
        record("appended-timer-still-fires-once-at-20", cards(timer).isEmpty() && collect().size == 1, "timer consumes one prepared payload")
        timer.tick(); discard(timer)
        record("appended-timer-does-not-repeat", collect().isEmpty(), "timer consumed")

        val expired = carrier(appended = true, kind = DrawAction.Kind.EXPIRE)
        expired.expire(); expired.die(); expired.die()
        record("appended-expiration-still-fires-once", cards(expired).isEmpty() && collect().size == 1, "expiry and cleanup share one consumption")
        val terminalTimer = carrier(appended = true, kind = DrawAction.Kind.TIMER)
        terminalTimer.hitBlock(Vector3d(terminalTimer.status.pos), Vector3f(0f, 0f, -1f))
        record("appended-timer-still-fires-on-terminal-wall", collect().size == 1, "only hit-trigger condition changed")
        discard(terminalTimer)

        val sawTarget = ctx.spawnDummy(0.0, floor + 6.0, 0.0)
        val saw = carrier(appended = true, piercing = true, id = "sawblade")
        saw.motionEnabled = false
        saw.hitEntity(sawTarget); saw.hitEntity(sawTarget)
        record("repeat-trigger-obeys-per-tick-contact-budget", collect().size == 1 && sawTarget.totalDamage == 12.0,
            "hits=${saw.status.hitTargets} damage=${sawTarget.totalDamage}")
        saw.tick(); saw.hitEntity(sawTarget)
        record("repeat-trigger-budget-resets-next-tick", collect().size == 1 && sawTarget.totalDamage == 24.0, "cut damage is unchanged")
        discard(saw); sawTarget.discard()

        val nested = carrier(piercing = true)
        val nestedTemplate = SpellRegistry.create("trigger_fireball") as Projectile
        (nestedTemplate as TriggerSpell).payload += SpellRegistry.create("fireball")!!
        SpellAssembly.apply(nestedTemplate, listOf(SpellRegistry.create("damage_boost") as Modifier))
        cards(nested).clear(); cards(nested) += nestedTemplate
        val nestedTarget = ctx.spawnDummy(0.0, floor + 6.0, 0.0)
        nested.hitEntity(nestedTarget); nested.hitEntity(nestedTarget)
        val nestedShots = collect(retire = false)
        val trees = nestedShots.filterIsInstance<TriggerSpell>()
        record("repeated-payloads-deep-copy-nested-trees", trees.size == 2 && trees[0].payload !== trees[1].payload &&
            trees[0].payload.single() !== trees[1].payload.single(), "independent recursive trees")
        record("prepared-payload-modifiers-copy-once", nestedShots.all { it.modifiers == mapOf("damage_boost" to 1) && it.status.damage == 7.0 } &&
            (nestedTemplate.modifiers["damage_boost"] == 1) &&
            nestedTemplate.status.damage == 4.0, "unfired template is unchanged")
        nestedShots.forEach(::discard)
        discard(nested); nestedTarget.discard()

        val composed = listOf(listOf("piercing") + List(5) { "damage_boost" }, List(5) { "damage_boost" } + "piercing").map { ids ->
            val shot = SpellRegistry.create("fireball") as Projectile
            SpellAssembly.apply(shot, ids.map { SpellRegistry.create(it) as Modifier })
            shot.effects.removeIf { it.phase == EffectPhase.VISUAL }
            shot.caster = ctx.player; shot.wand = Wand("piercing-addition")
            shot.position = Vec3(0.0, floor + 7.0, -1.0).toPosition(); shot.velocity = Vector3f(0f, 0f, 1f)
            shot.spell()
            shot.status.damage.also { discard(shot) }
        }
        record("piercing-penalty-adds-with-damage-boost-regardless-of-order", composed == listOf(5.0, 5.0), "damage=$composed")
        val healing = SpellRegistry.create("healing_bolt") as Projectile
        SpellAssembly.apply(healing, listOf(SpellRegistry.create("piercing") as Modifier))
        healing.effects.removeIf { it.phase == EffectPhase.VISUAL }
        healing.caster = ctx.player; healing.wand = Wand("piercing-healing")
        healing.position = Vec3(0.0, floor + 7.0, -1.0).toPosition(); healing.velocity = Vector3f(0f, 0f, 1f); healing.spell()
        record("piercing-does-not-turn-healing-into-damage", healing.status.damage == 5.0 && healing.damageType == SpellDamageType.HEALING,
            "healing=${healing.status.damage}")
        discard(healing)

        val source = ctx.spawnDummy(-8.0, floor + 2.0, -5.0)
        val wand = Wand("repeat-trigger-payment"); wand.mana = 2000.0; wand.delayTickCount = 0.0; wand.rechargeTickCount = 0.0
        wand.magics += listOf("piercing", "trigger_fireball", "damage_boost", "fireball").map { SpellRegistry.create(it)!! }
        wand.load(); wand.status.mana = 2000.0
        val price = wand.magics.filterNotNull().sumOf { it.mana }
        val preview = wand.preview(); val accepted = wand.spell(source); ProjectileRuntime.tick()
        val paid = ProjectileRuntime.projectiles.singleOrNull { it is TriggerSpell }
        val victim = ctx.spawnDummy(-7.0, floor + 2.0, -5.0)
        paid?.hitEntity(victim); paid?.hitEntity(victim)
        paid?.let(::discard)
        val children = collect()
        record("repeated-payload-charged-only-at-cast", accepted && children.size == 2 &&
            wand.status.mana == 2000.0 - price && price == 313.0 && children.all { it.status.damage == 8.0 },
            "mana=${wand.status.mana} price=$price children=${children.size}")
        record("piercing-has-no-extra-cooldowns", preview.delay == 1.0 && preview.recharge == 0.0,
            "delay=${preview.delay} recharge=${preview.recharge}")
        source.discard(); victim.discard()
        collect(); ProjectileRuntime.clear()
        record("trigger-fixture-cleans-active-and-pending", ProjectileRuntime.projectiles.isEmpty(), "next checks start empty")
        return checks
    }
}
