package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 原版箭矢与可见性也服从虚空状态，不改写实体原有共享标记。 */
@Mixin(Entity.class)
public abstract class VoidEntityMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void primalspells$immobile(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (VoidStatus.isPhased((Entity) (Object) this)) ci.cancel();
    }

    @ModifyVariable(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
    private Vec3 primalspells$noMomentum(Vec3 velocity) {
        return VoidStatus.isPhased((Entity) (Object) this) ? Vec3.ZERO : velocity;
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void primalspells$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void primalspells$noContact(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void primalspells$invisible(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((Entity) (Object) this)) cir.setReturnValue(true);
    }
}
