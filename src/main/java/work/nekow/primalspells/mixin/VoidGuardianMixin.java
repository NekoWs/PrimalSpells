package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 守卫者客户端缓存的光束目标也随虚空同步隐藏。 */
@Mixin(Guardian.class)
public abstract class VoidGuardianMixin {
    @Inject(method = "getActiveAttackTarget", at = @At("RETURN"), cancellable = true)
    private void primalspells$noBeamLock(CallbackInfoReturnable<LivingEntity> cir) {
        if (VoidStatus.isPhased(cir.getReturnValue())) cir.setReturnValue(null);
    }
}
