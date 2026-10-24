package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import work.nekow.primalspells.entity.RogueMageEntity
import work.nekow.primalspells.magic.core.SpellDamageSource
import work.nekow.primalspells.magic.core.SpellDamageType
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.SpellDamage
import work.nekow.primalspells.magic.effect.lifecycle.SpellImpact
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModDamageTypes
import work.nekow.primalspells.registry.ModEntities
import work.nekow.primalspells.wand.Wand

/** 原生伤害源、战斗记录和真实玩家死亡界面；验证后通过正常重生请求恢复录制玩家。 */
object DeathAttributionScene {
    /** 原版死亡流程结束时会清战斗记录；在死亡事件中观察真正用于发送的记录。 */
    class DeathProbe {
        var player: ServerPlayer? = null
        var source: DamageSource? = null
        var message: Component? = null
        @SubscribeEvent fun onDeath(event: LivingDeathEvent) {
            val victim = event.entity as? ServerPlayer ?: return
            player = victim; source = event.source; message = victim.combatTracker.deathMessage.copy()
        }
    }
    fun scene(): Scene {
        val name = "death_attribution"
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        var floor = 0
        var mage: RogueMageEntity? = null
        var deadPlayer: ServerPlayer? = null
        val probe = DeathProbe()
        fun record(t: Int, id: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name, t, id, ok, detail))
        }
        return Scene(name, "法术死亡来源 / 施法者与自伤 / 原生死亡界面", 24, 110, allowScreen = true,
            prepare = { ctx ->
                NeoForge.EVENT_BUS.register(probe)
                floor = ctx.floorBlockY(0, 0); ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.0, floor + 2.0, -6.0, 0f, 8f)
                ctx.command("difficulty normal")
                mage = ModEntities.ROGUE_MAGE.get().create(ctx.level, EntitySpawnReason.COMMAND)!!.also {
                    it.setPos(24.0, floor + 1.0, -16.0); it.isNoAi = true
                    ctx.level.addFreshEntity(it)
                }
            }, camera = { _, _ -> Pose(0.0, floor + 2.0, -6.0, 0f, 8f) },
            clientTick = { mc, t ->
                if (t == 50) record(t, "actual-client-death-screen", mc.gui.screen() is DeathScreen,
                    "screen=${mc.gui.screen()?.javaClass?.simpleName}")
                if (t == 75) { mc.player?.respawn(); mc.gui.setScreen(null) }
                if (t == 86) record(t, "actual-client-respawn", mc.player?.isAlive == true && mc.gui.screen() == null,
                    "alive=${mc.player?.isAlive} screen=${mc.gui.screen()}")
            }, tick = { ctx, t ->
                if (t == 5) {
                    val victim = EntityTypes.COW.create(ctx.level, EntitySpawnReason.COMMAND)!!.also {
                        it.setPos(-24.0, floor + 1.0, -16.0); it.isNoAi = true
                        it.customName = Component.literal("Spell Victim")
                        it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 100.0; it.health = 100f
                        ctx.level.addFreshEntity(it)
                    }
                    val held = ctx.player.mainHandItem.copy()
                    ctx.player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
                    try {
                        for (type in SpellDamageType.entries) {
                            val key = "death.attack.primalspells_${type.id}"
                            val source = requireNotNull(ModDamageTypes.sourceOrNull(ctx.level, ctx.player, type))
                            val message = source.getLocalizedDeathMessage(victim)
                            record(t, "named-player-${type.id}", source is SpellDamageSource && source.entity === ctx.player &&
                                source.directEntity === ctx.player && source.`is`(type.key) &&
                                (message.contents as? TranslatableContents)?.key == "$key.player" &&
                                message.string.contains(ctx.player.displayName.string) && message.string.contains("Spell Victim"), message.string)
                            val npc = requireNotNull(ModDamageTypes.sourceOrNull(ctx.level, mage, type))
                            val npcMessage = npc.getLocalizedDeathMessage(victim)
                            record(t, "named-mage-${type.id}", (npcMessage.contents as? TranslatableContents)?.key == "$key.player" &&
                                npcMessage.string.contains(requireNotNull(mage).displayName.string), npcMessage.string)
                            val self = requireNotNull(ModDamageTypes.sourceOrNull(ctx.level, victim, type)).getLocalizedDeathMessage(victim)
                            record(t, "self-damage-${type.id}", (self.contents as? TranslatableContents)?.key == "$key.self" &&
                                self.string.contains("Spell Victim") && self.string.contains("own"), self.string)
                            victim.health = 100f; victim.invulnerableTime = 17
                            val amount = if (type == SpellDamageType.HEALING) -2.0 else 2.0
                            val accepted = SpellImpact.hurt(ctx.level, victim, ctx.player, amount, Vec3.ZERO, knockback = false, type = type)
                            val combat = victim.combatTracker.deathMessage
                            record(t, "real-impact-attribution-${type.id}", accepted && victim.health == 98f && victim.invulnerableTime == 17 &&
                                victim.lastDamageSource?.entity === ctx.player && combat.string.contains(ctx.player.displayName.string),
                                "health=${victim.health} message=${combat.string}")
                            val unknown = requireNotNull(ModDamageTypes.sourceOrNull(ctx.level, null, type)).getLocalizedDeathMessage(victim)
                            record(t, "unknown-not-previous-attacker-${type.id}", (unknown.contents as? TranslatableContents)?.key == key &&
                                !unknown.string.contains(ctx.player.displayName.string), unknown.string)
                        }
                        val unrelated = ItemStack(Items.STICK).also { it.set(DataComponents.CUSTOM_NAME, Component.literal("Unrelated Item")) }
                        ctx.player.setItemSlot(EquipmentSlot.MAINHAND, unrelated)
                        val message = requireNotNull(ModDamageTypes.projectileSourceOrNull(ctx.level, ctx.player)).getLocalizedDeathMessage(victim)
                        record(t, "current-hand-does-not-replace-spell-source", message.string.contains(ctx.player.displayName.string) &&
                            !message.string.contains("Unrelated Item"), message.string)
                    } finally {
                        ctx.player.setItemSlot(EquipmentSlot.MAINHAND, held); victim.discard()
                    }
                }
                if (t == 45) {
                    ctx.command("gamemode survival @a")
                    ctx.player.health = 20f; ctx.player.invulnerableTime = 0
                    val shot = SpellRegistry.create("fireball") as Projectile
                    shot.caster = requireNotNull(mage); shot.wand = Wand("death-attribution")
                    deadPlayer = ctx.player
                    val accepted = SpellDamage.hit(ctx.level, ctx.player, shot, 1000.0, knockback = false)
                    val message = probe.message
                    record(t, "real-player-killed-by-mage-spell", accepted && !ctx.player.isAlive && probe.player === deadPlayer &&
                        probe.source?.entity === mage && message?.string?.contains(requireNotNull(mage).displayName.string) == true,
                        "accepted=$accepted alive=${ctx.player.isAlive} message=${message?.string}")
                }
                if (t == 80) {
                    mage?.discard()
                    ctx.setup(true, 0.0, floor + 2.0, -6.0, 0f, 8f)
                }
                if (t == 90) record(t, "server-player-replaced-on-respawn", ctx.player.isAlive && ctx.player !== deadPlayer && ctx.player.uuid == deadPlayer?.uuid,
                    "alive=${ctx.player.isAlive} newObject=${ctx.player !== deadPlayer}")
            }, checksAt = { _, t -> checks[t].orEmpty() }, finish = { ctx -> NeoForge.EVENT_BUS.unregister(probe); mage?.discard(); ctx.command("difficulty peaceful") })
    }
}
