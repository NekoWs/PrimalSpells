package work.nekow.primalspells.client.capture

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand

/** foundations的服务端边界回归；使用真实注册表、NBT与弹体队列，不生成替代算法。 */
internal object ArchitectureChecks {
    fun run(ctx: CaptureDriver.SceneCtx, record: (String, Boolean, String) -> Unit) {
        registry(record)
        persistence(record)
        lifecycle(ctx, record)
    }

    private fun registry(record: (String, Boolean, String) -> Unit) {
        record("registry-matches-real-spell-items", SpellRegistry.factories.keys == ModItems.MAGICS.keys,
            "factories=${SpellRegistry.factories.size}; items=${ModItems.MAGICS.size}")
        val factory = SpellRegistry.factories.getValue("fireball")
        record("duplicate-factory-rejected-without-replacement", runCatching { SpellRegistry.register(factory) }.isFailure &&
            SpellRegistry.factories["fireball"] === factory, "duplicate registration preserves original factory")
        val first = SpellRegistry.create("fireball")!!
        val second = SpellRegistry.create("fireball")!!
        first.effects.clear()
        record("registry-creates-isolated-instances", first !== second && second.effects.isNotEmpty(), "effects are not shared")
    }

    private fun persistence(record: (String, Boolean, String) -> Unit) {
        val w = Wand("architecture-roundtrip")
        w.size = 7; w.cast = 2; w.mana = 700.0; w.charge = 3.5
        w.delayTickCount = 3.0; w.rechargeTickCount = 17.0
        w.innate += SpellRegistry.create("damage_boost")!!
        w.magics += listOf("fireball", "", "trigger_fireball", "fireball", "", "fireball", "").map { SpellRegistry.create(it) }
        w.load(); w.status.mana = 123.0; w.status.delay = 2.0; w.status.recharge = 9.0
        w.drawPile.clear(); w.drawPile += w.loadedMagics.takeLast(2)
        w.hand += w.loadedMagics[0]; w.discardPile += w.loadedMagics.drop(1).dropLast(2)
        val saved = w.saveTag()
        val restored = Wand.fromTag(saved)
        record("nbt-roundtrip-preserves-slots-stats-and-pile-cursors", saved == restored.saveTag(), restored.saveTag().toString())
        val before = restored.saveTag()
        restored.preview()
        record("preview-preserves-restored-pile-state", before == restored.saveTag(), "restored partially drawn deck unchanged")
        val unknown = saved.copy()
        unknown.put("magics", ListTag().also { list ->
            listOf("fireball", "removed_spell", "fireball").forEach { list.add(StringTag.valueOf(it)) }
        })
        val slots = Wand.fromTag(unknown).magics.map { it?.id }
        record("removed-spell-keeps-original-slot", slots == listOf("fireball", null, "fireball"), slots.toString())
        for ((unit, value) in listOf("ticks" to 7.0, "sec" to 0.34, "ms" to 340.0, "" to 7.0)) {
            val legacy = CompoundTag()
            legacy.putString("id", "architecture-legacy-$unit")
            legacy.putDouble(if (unit.isEmpty()) "delay" else "delay_$unit", value)
            legacy.putDouble("charge", 100.0)
            val migrated = Wand.fromTag(legacy)
            val again = Wand.fromTag(migrated.saveTag())
            record("nbt-time-unit-$unit", migrated.delayTicks() == 7.0 && again.delayTicks() == 7.0 && again.charge == 5.0,
                "delay=${again.delayTicks()}; charge=${again.charge}")
        }
        val precedence = saved.copy()
        precedence.putDouble("delay_sec", 99.0)
        precedence.putDouble("status_mana", 9999.0)
        val clamped = Wand.fromTag(precedence)
        record("current-tick-key-wins-and-mana-is-clamped", clamped.delayTicks() == 3.0 && clamped.status.mana == 700.0,
            "delay=${clamped.delayTicks()}; mana=${clamped.status.mana}")
        val stale = Wand("architecture-stale-state")
        stale.magicStates["removed_passive"] = CompoundTag().also { it.putInt("stale", 1) }
        record("save-discards-unloaded-passive-state", stale.saveTag().getListOrEmpty("magic_states").isEmpty,
            "only currently loaded cards contribute state")
    }

    private fun lifecycle(ctx: CaptureDriver.SceneCtx, record: (String, Boolean, String) -> Unit) {
        record("runtime-fixture-starts-empty", ProjectileRuntime.projectiles.isEmpty(), "no active effects before fixture")
        val events = arrayListOf<String>()
        fun probe(label: String, age: Int) = object : Projectile() {
            override val id = "capture_runtime_$label"
            init {
                maxAge = age
                caster = ctx.player
                wand = Wand(id)
                effects += object : BaseEffect() {
                    override fun onTick() { events += "$label.tick" }
                    override fun onExpire() { events += "$label.expire" }
                    override fun onDie() { events += "$label.die" }
                }
            }
        }
        val child = probe("child", 200)
        val parent = probe("parent", 1)
        parent.effects += object : BaseEffect() {
            override fun onExpire() { ProjectileRuntime.add(child) }
        }
        ProjectileRuntime.add(parent)
        ProjectileRuntime.tick()
        record("runtime-inserts-after-iteration", parent.status.age == 0 && events.isEmpty(), events.toString())
        ProjectileRuntime.tick()
        record("runtime-expires-and-enqueues-child-same-tick", !parent.alive && child.status.age == 0 &&
            events == listOf("parent.tick", "parent.expire"), events.toString())
        ProjectileRuntime.tick()
        record("runtime-cleans-parent-before-child-ticks", events == listOf("parent.tick", "parent.expire", "parent.die", "child.tick"), events.toString())
        ProjectileRuntime.add(probe("pending", 200))
        ProjectileRuntime.clear()
        ProjectileRuntime.tick()
        record("runtime-reset-clears-active-and-pending", ProjectileRuntime.projectiles.isEmpty(), "queued projectiles cannot enter next world")
    }
}
