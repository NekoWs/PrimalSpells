package work.nekow.primalspells.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 龙的扫射阶段独立保存目标，失锁后回到盘旋，不继续向虚空实体蓄火球。 */
@Mixin(DragonStrafePlayerPhase.class)
public abstract class VoidDragonStrafeMixin extends AbstractDragonPhaseInstance {
    public VoidDragonStrafeMixin(EnderDragon dragon) { super(dragon); }
    @Shadow private LivingEntity attackTarget;

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void primalspells$noAcquisition(LivingEntity target, CallbackInfo ci) {
        if (VoidStatus.isPhased(target)) ci.cancel();
    }

    @Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
    private void primalspells$dropLock(ServerLevel level, CallbackInfo ci) {
        if (VoidStatus.isPhased(attackTarget)) {
            attackTarget = null;
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            ci.cancel();
        }
    }
}
