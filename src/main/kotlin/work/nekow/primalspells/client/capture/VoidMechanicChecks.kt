package work.nekow.primalspells.client.capture

import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.projectile.ShulkerBullet
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.PayloadRelease
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModEffects
import work.nekow.primalspells.wand.Wand

/** 真实施法与原版方法验证；不以替身目标选择器代替特殊生物/追踪弹。 */
internal object VoidMechanicChecks {
    fun run(ctx: CaptureDriver.SceneCtx, target: Pig, record: (String, Boolean, String) -> Unit) {
        val origin = target.position()
        target.deltaMovement = Vec3(2.0, 1.0, -1.0)
        for (type in listOf(MoverType.SELF, MoverType.PISTON, MoverType.PLAYER)) target.move(type, Vec3(1.0, -1.0, 1.0))
        record("void-stops-movement-gravity-and-momentum", target.position() == origin && target.deltaMovement == Vec3.ZERO, "${target.position()}/${target.deltaMovement}")
        record("void-not-pickable-pushable-or-attackable", !target.isPickable && !target.isPushable && !target.isAttackable, "pick=${target.isPickable}")
        val conditions = TargetingConditions.forCombat().ignoreInvisibilityTesting().ignoreLineOfSight()
        record("void-excluded-even-without-sight-invisibility-tests", !conditions.test(ctx.level, null, target), "native TargetingConditions")
        val warden = Warden(EntityTypes.WARDEN, ctx.level)
        val wither = WitherBoss(EntityTypes.WITHER, ctx.level)
        record("void-warden-cannot-smell-or-anger-target", !warden.canTargetEntity(target), "native Warden.canTargetEntity")
        wither.setAlternativeTarget(1, target.id)
        record("void-wither-side-head-cannot-retain-target", wither.getAlternativeTarget(1) == 0, "head=${wither.getAlternativeTarget(1)}")
        val bullet = ShulkerBullet(ctx.level, warden, target, Direction.Axis.Y)
        bullet.isSilent = true
        bullet.setPos(target.x, target.y + 5, target.z)
        bullet.deltaMovement = Vec3.ZERO; bullet.tick()
        record("void-shulker-bullet-loses-lock", bullet.deltaMovement.y < -.03, "velocity=${bullet.deltaMovement}")

        val caster = Pig(EntityTypes.PIG, ctx.level).also { it.setPos(15.0, target.y + 5, 15.0) }
        val sensorPos = target.blockPosition().offset(1, 0, 0)
        val sensor = net.minecraft.world.level.block.entity.SculkSensorBlockEntity(sensorPos,
            net.minecraft.world.level.block.Blocks.SCULK_SENSOR.defaultBlockState())
        val step = net.minecraft.world.level.gameevent.GameEvent.STEP
        val context = net.minecraft.world.level.gameevent.GameEvent.Context.of(target)
        record("void-sculk-cannot-locate-source", !sensor.listener.handleGameEvent(ctx.level, step, context, target.position()), "native sculk vibration listener")
        record("void-sculk-still-detects-normal-entities", sensor.listener.handleGameEvent(ctx.level, step,
            net.minecraft.world.level.gameevent.GameEvent.Context.of(caster), target.position()), "native listener accepts normal source")
        val dragon = net.minecraft.world.entity.boss.enderdragon.EnderDragon(EntityTypes.ENDER_DRAGON, ctx.level)
        val strafe = net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.STRAFE_PLAYER
        val holding = net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOLDING_PATTERN
        dragon.phaseManager.setPhase(strafe)
        val phase = dragon.phaseManager.getPhase(strafe)
        phase.setTarget(caster); VoidStatus.enter(caster); phase.doServerTick(ctx.level)
        record("void-dragon-drops-existing-strafe-lock", dragon.phaseManager.currentPhase.phase == holding, "phase=${dragon.phaseManager.currentPhase.phase}")
        caster.removeEffect(ModEffects.VOID_PHASE)
        record("void-warden-can-target-after-expiry", warden.canTargetEntity(caster), "native targetability restored")
        val cases = listOf(
            listOf("extended_lifetime", "void_escape") to 85,
            listOf("reduced_lifetime", "void_escape") to 35,
            listOf("extended_lifetime", "reduced_lifetime", "void_escape") to 60,
            listOf("reduced_lifetime", "reduced_lifetime", "reduced_lifetime", "void_escape") to 1,
            listOf("void_escape", "extended_lifetime") to 85,
            listOf("gamma", "extended_lifetime", "void_escape") to 85,
        )
        for ((index, pair) in cases.withIndex()) {
            val (ids, duration) = pair
            val wand = Wand("void-duration-$index").also {
                it.cast = if (index >= 4) 2 else 1; it.mana = 1000.0
                it.magics += ids.map { id -> SpellRegistry.create(id)!! }; it.load(); it.status.mana = 1000.0
            }
            val cast = wand.spell(caster)
            val actual = caster.getEffect(ModEffects.VOID_PHASE)?.duration
            record("void-lifetime-chain-$index", cast && actual == duration, "$ids: $actual/$duration")
            caster.removeEffect(ModEffects.VOID_PHASE)
        }
        val wand = Wand("void-payload-duration").also {
            it.mana = 1000.0; it.magics += listOf("extended_lifetime", "trigger_fireball", "reduced_lifetime", "void_escape")
                .map { id -> SpellRegistry.create(id)!! }; it.load(); it.status.mana = 1000.0
        }
        wand.spell(caster); ProjectileRuntime.tick()
        val carrier = ProjectileRuntime.projectiles.lastOrNull { it.wand === wand }
        val payload = (carrier as? work.nekow.primalspells.magic.core.TriggerSpell)?.payload
        if (carrier != null && payload != null) PayloadRelease.emit(carrier, payload, Vector3d(caster.x, caster.y, caster.z), Vector3f(0f, 0f, 1f))
        record("void-payload-lifetime-isolated-from-parent", carrier != null && caster.getEffect(ModEffects.VOID_PHASE)?.duration == 35,
            "duration=${caster.getEffect(ModEffects.VOID_PHASE)?.duration}")
        caster.removeEffect(ModEffects.VOID_PHASE)
        caster.move(MoverType.SELF, Vec3(.25, 0.0, 0.0))
        record("void-movement-restored-after-removal", caster.x > 15.2, "x=${caster.x}")
        carrier?.alive = false
        bullet.discard(); caster.discard(); warden.discard(); wither.discard(); dragon.discard()
    }
}
