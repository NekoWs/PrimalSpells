package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;
import work.nekow.primalspells.registry.ModEffects;
import java.util.Collection;
import net.minecraft.world.level.storage.ValueInput;

/** 同时覆盖普通目标选择器与Brain对生物可见性的判断。 */
@Mixin(LivingEntity.class)
public abstract class VoidLivingEntityMixin {
    @Inject(method = {"isPickable", "isPushable"}, at = @At("HEAD"), cancellable = true)
    private void primalspells$noSelectionOrPush(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }

    @Unique
    private void primalspells$syncVoidState() {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.level().isClientSide()) {
            if (entity.hasEffect(ModEffects.INSTANCE.getVOID_PHASE())) entity.setData(ModEffects.INSTANCE.getVOID_PHASED(), true);
            else if (entity.hasData(ModEffects.INSTANCE.getVOID_PHASED())) entity.removeData(ModEffects.INSTANCE.getVOID_PHASED());
        }
    }

    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void primalspells$voidAdded(MobEffectInstance effect, Entity cause, CallbackInfo ci) {
        if (effect.is(ModEffects.INSTANCE.getVOID_PHASE())) primalspells$syncVoidState();
    }

    @Inject(method = "onEffectUpdated", at = @At("TAIL"))
    private void primalspells$voidUpdated(MobEffectInstance effect, boolean refresh, Entity cause, CallbackInfo ci) {
        if (effect.is(ModEffects.INSTANCE.getVOID_PHASE())) primalspells$syncVoidState();
    }

    @Inject(method = "onEffectsRemoved", at = @At("TAIL"))
    private void primalspells$voidRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        if (effects.stream().anyMatch(effect -> effect.is(ModEffects.INSTANCE.getVOID_PHASE()))) primalspells$syncVoidState();
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void primalspells$voidLoaded(ValueInput input, CallbackInfo ci) {
        primalspells$syncVoidState();
    }
    @Inject(method = "canBeSeenByAnyone", at = @At("HEAD"), cancellable = true)
    private void primalspells$untargetable(CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }
}
