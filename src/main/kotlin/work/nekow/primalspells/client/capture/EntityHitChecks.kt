package work.nekow.primalspells.client.capture

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.EffectPhase
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.projectile.TriggerFireball
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.Wand

/** collision_precision中的真实实体夹具，检查可命中契约、扫掠与荷载消费。 */
object EntityHitChecks {
    fun run(ctx: CaptureDriver.SceneCtx, tick: Int, floor: Int): List<Check> {
        val checks = arrayListOf<Check>()
        fun record(id: String, ok: Boolean, detail: String) {
            checks += Check("collision_precision", tick, id, ok, detail)
        }
        val y = floor + 8.0
        val ignored = listOf(
            ItemEntity(ctx.level, -2.0, y, 0.5, ItemStack(Items.DIAMOND)),
            ExperienceOrb(ctx.level, -2.0, y, 0.5, 3),
            checkNotNull(EntityTypes.AREA_EFFECT_CLOUD.create(ctx.level, EntitySpawnReason.COMMAND)),
            checkNotNull(EntityTypes.INTERACTION.create(ctx.level, EntitySpawnReason.COMMAND)),
            checkNotNull(EntityTypes.TEXT_DISPLAY.create(ctx.level, EntitySpawnReason.COMMAND))
        ).onEach { it.setPos(-2.0, y, 0.5); it.setNoGravity(true); ctx.level.addFreshEntity(it) }
        val shots = arrayListOf<Projectile>()
        fun shot(id: String): Projectile = (SpellRegistry.create(id) as Projectile).also { p ->
            p.effects.removeIf { it.phase == EffectPhase.VISUAL }
            p.caster = ctx.player; p.wand = Wand("target-filter")
            p.position = Vec3(-4.0, y + 0.1, 0.5).toPosition(); p.velocity = Vector3f(1f, 0f, 0f)
            p.spell(); shots += p
        }
        try {
            val bolt = shot("fireball")
            ignored.forEach { target ->
                record("vanilla-projectile-ineligible-${target.type}", !target.canBeHitByProjectile() && !bolt.canHitEntity(target),
                    "type=${target.type} pickable=${target.isPickable} vanilla=${target.canBeHitByProjectile()}")
            }
            // Interaction可用鼠标选中，但原版明确不允许投射物击中，不能只用isPickable判断。
            val interaction = ignored[3]
            record("pickable-interaction-is-still-ignored", interaction.isPickable && !bolt.canHitEntity(interaction), "pickable=${interaction.isPickable}")
            val dummy = ctx.spawnDummy(1.0, y - 0.8, 0.5)
            try {
                val contacts = arrayListOf<Entity>()
                bolt.effects += object : BaseEffect() {
                    override fun onHitEntity(target: Entity) { contacts += target }
                }
                repeat(3) { if (bolt.alive) { bolt.tick(); bolt.status.age++ } }
                record("swept-bolt-passes-loot-and-hits-enemy", contacts == listOf(dummy) && bolt.status.hitTargets == 1 && dummy.totalDamage == 5.0,
                    "contacts=${contacts.map { it.type }} hits=${bolt.status.hitTargets} damage=${dummy.totalDamage}")
            } finally { dummy.discard() }

            val trigger = shot("trigger_fireball") as TriggerFireball
            trigger.payload += checkNotNull(SpellRegistry.create("fireball"))
            ignored.forEach { trigger.hitEntity(it) }
            record("ignored-direct-contact-keeps-trigger-and-hit-budget", trigger.alive && trigger.status.hitTargets == 0 && trigger.payload.size == 1,
                "alive=${trigger.alive} hits=${trigger.status.hitTargets} payload=${trigger.payload.size}")
            trigger.tick()
            record("swept-trigger-passes-ignored-entities", trigger.alive && trigger.status.hitTargets == 0 && trigger.payload.size == 1 && trigger.status.pos.x > -2.0,
                "pos=${trigger.status.pos} hits=${trigger.status.hitTargets} payload=${trigger.payload.size}")

            val frame = checkNotNull(EntityTypes.ITEM_FRAME.create(ctx.level, EntitySpawnReason.COMMAND))
            frame.setPos(-2.0, y, 0.5); ctx.level.addFreshEntity(frame)
            try {
                val shootable = frame.canBeHitByProjectile()
                val p = shot("fireball")
                p.status.pos.set(frame.boundingBox.center.add(-2.0, 0.0, 0.0).toPosition())
                var hit: Entity? = null
                p.effects += object : BaseEffect() { override fun onHitEntity(target: Entity) { hit = target } }
                p.tick()
                record("vanilla-shootable-nonliving-entity-still-collides", shootable && hit === frame && p.status.hitTargets == 1,
                    "vanilla=$shootable hit=${hit?.type} hits=${p.status.hitTargets}")
            } finally { frame.discard() }
            record("ignored-loot-and-xp-remain-alive", ignored.all { it.isAlive }, "alive=${ignored.map { it.isAlive }}")
        } finally {
            shots.forEach { it.alive = false; it.die() }
            ignored.forEach { it.discard() }
        }
        return checks
    }
}
