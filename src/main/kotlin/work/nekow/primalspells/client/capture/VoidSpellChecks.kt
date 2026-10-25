package work.nekow.primalspells.client.capture

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.EquipmentSlot
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import work.nekow.primalspells.client.render.VoidVisibility
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus
import work.nekow.primalspells.magic.effect.lifecycle.SpellImpact
import work.nekow.primalspells.registry.ModEffects
import work.nekow.primalspells.wand.Wand

/** 连续60t真实状态同步、原版可见性/AI/伤害/交互与装备渲染回归。 */
internal class VoidSpellChecks {
    private var pig: Pig? = null
    private var enemy: Pig? = null
    @Volatile private var targetId = -1

    fun tick(ctx: CaptureDriver.SceneCtx, t: Int, record: (String, Boolean, String) -> Unit) {
        if (t == 108) {
            pig = EntityTypes.PIG.create(ctx.level, EntitySpawnReason.COMMAND)!!.also {
                it.setNoAi(true); it.setNoGravity(true); it.setPos(3.0, ctx.floorBlockY(0,0) + 2.0, 0.0)
                it.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.DIAMOND_HELMET))
                it.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.DIAMOND_SWORD))
                ctx.level.addFreshEntity(it); targetId = it.id
            }
            enemy = EntityTypes.PIG.create(ctx.level, EntitySpawnReason.COMMAND)!!.also {
                it.setNoAi(true); it.setNoGravity(true); it.setPos(6.0, ctx.floorBlockY(0,0) + 2.0, 0.0)
                ctx.level.addFreshEntity(it); it.target = pig
            }
        }
        if (t == 116) {
            val target = requireNotNull(pig)
            val w = Wand("reference-void"); w.magics += SpellRegistry.create("void_escape")!!; w.load()
            val success = w.spell(target)
            record("void-actual-cast-and-200t-cooldowns", success && VoidStatus.isPhased(target) && w.status.mana == 0.0 && w.status.delay == 200.0 && w.status.recharge == 200.0, "${w.status.delay}/${w.status.recharge}")
            record("void-native-projectile-contact-and-ai-excluded", !target.canBeHitByProjectile() && !target.canBeSeenAsEnemy() && target.isInvisible && enemy?.target == null, "projectile=${target.canBeHitByProjectile()}; target=${enemy?.target}")
            enemy?.target = target
            record("void-cannot-reacquire-target", enemy?.target == null, enemy?.target.toString())
            val health = target.health
            val accepted = target.hurtServer(ctx.level, ctx.level.damageSources().generic(), 5f)
            val magic = SpellImpact.hurt(ctx.level, target, ctx.player, 5.0, net.minecraft.world.phys.Vec3(0.0,0.0,1.0))
            val carrier = SpellRegistry.create("fireball") as work.nekow.primalspells.magic.projectile.Projectile
            carrier.caster = ctx.player; carrier.wand = Wand("void-field-probe")
            carrier.status.pos.set(target.x + 1.0, target.y, target.z)
            val field = object : work.nekow.primalspells.magic.effect.lifecycle.AreaFieldEffect(pullPerTick = 1.0) {}
            field.projectile = carrier; field.caster = ctx.player; field.wand = carrier.wand; field.status = carrier.status
            val velocity = target.deltaMovement
            field.onTick(); field.onDie()
            record("void-excludes-field-slow-and-pull", target.deltaMovement == velocity && !target.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "velocity=${target.deltaMovement}")
            record("void-vanilla-and-magic-damage-immunity", !accepted && !magic && target.health == health, "accepted=$accepted/$magic; HP=${target.health}")
            val attack = Wand("reference-void-attack"); attack.magics += SpellRegistry.create("fireball")!!; attack.load(); attack.status.mana = 100.0
            val before = attack.saveTag().toString()
            record("void-cannot-cast-even-another-wand", !attack.spell(target) && before == attack.saveTag().toString(), attack.status.mana.toString())
            VoidStatus.enter(ctx.player)
            val blocked = NeoForge.EVENT_BUS.post(AttackEntityEvent(ctx.player, target)).isCanceled
            val use = NeoForge.EVENT_BUS.post(PlayerInteractEvent.RightClickItem(ctx.player, net.minecraft.world.InteractionHand.MAIN_HAND)).isCanceled
            ctx.player.removeEffect(ModEffects.VOID_PHASE)
            record("void-blocks-player-attack-and-item-use", blocked && use, "attack=$blocked; item=$use")
        }
        if (t == 150) record("void-stays-active-through-middle", VoidStatus.isPhased(pig), pig?.getEffect(ModEffects.VOID_PHASE)?.duration.toString())
        if (t == 177) {
            val target = requireNotNull(pig)
            record("void-restores-contact-and-visibility-after-60t", !VoidStatus.isPhased(target) && target.canBeHitByProjectile() && target.canBeSeenAsEnemy() && !target.isInvisible, "effect=${target.getEffect(ModEffects.VOID_PHASE)}")
            val before = target.health
            record("void-restores-real-damage", target.hurtServer(ctx.level, ctx.level.damageSources().generic(), 1f) && target.health == before - 1f, target.health.toString())
        }
        if (t == 179) { pig?.discard(); enemy?.discard() }
    }

    fun clientTick(mc: Minecraft, t: Int, record: (String, Boolean, String) -> Unit) {
        if (t !in listOf(112, 130, 178)) return
        val target = mc.level?.getEntity(targetId) as? Pig
        if (target == null) { record("void-client-entity-$t", false, "missing tracked entity"); return }
        val state = mc.entityRenderDispatcher.getRenderer(target).createRenderState(target, 1f)
        val expected = t == 130
        record("void-client-render-state-$t", (state.getRenderData(VoidVisibility.PHASED) == true) == expected &&
            (!expected || (state.shadowPieces.isEmpty() && state.outlineColor == 0 && state.nameTag == null)), "hidden=${state.getRenderData(VoidVisibility.PHASED)}")
    }
}
