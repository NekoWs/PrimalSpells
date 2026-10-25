package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 原版箭矢与可见性也服从虚空状态，不改写实体原有共享标记。 */
@Mixin(Entity.class)
public abstract class VoidEntityMixin {
    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void primalspells$noContact(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void primalspells$invisible(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((Entity) (Object) this)) cir.setReturnValue(true);
    }
}
