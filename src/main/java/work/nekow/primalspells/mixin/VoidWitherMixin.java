package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 凋灵侧头保存独立目标ID，不能只清除Mob主目标。 */
@Mixin(WitherBoss.class)
public abstract class VoidWitherMixin {
    @Inject(method = "getAlternativeTarget", at = @At("RETURN"), cancellable = true)
    private void primalspells$noCachedTarget(int head, CallbackInfoReturnable<Integer> cir) {
        WitherBoss wither = (WitherBoss) (Object) this;
        if (VoidStatus.isPhased(wither.level().getEntity(cir.getReturnValue()))) {
            if (!wither.level().isClientSide()) wither.setAlternativeTarget(head, 0);
            cir.setReturnValue(0);
        }
    }
}
