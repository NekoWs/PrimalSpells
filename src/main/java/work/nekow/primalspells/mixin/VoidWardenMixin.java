package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 监守者嗅探、愤怒和音波共用的目标资格，不依赖隐身或视线。 */
@Mixin(Warden.class)
public abstract class VoidWardenMixin {
    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void primalspells$noSensing(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased(target)) cir.setReturnValue(false);
    }
}
