package work.nekow.primalspells.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 脚步之外的事件同样不能经幽匿振动暴露虚空实体。 */
@Mixin(VibrationSystem.Listener.class)
public abstract class VoidVibrationMixin {
    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void primalspells$noVibration(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context,
                                        Vec3 position, CallbackInfoReturnable<Boolean> cir) {
        if (VoidStatus.isPhased(context.sourceEntity())) cir.setReturnValue(false);
    }
}
