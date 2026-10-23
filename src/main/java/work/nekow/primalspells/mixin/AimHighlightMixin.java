package work.nekow.primalspells.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.client.render.AimHighlights;

/** 将瞄准租约并入原版轮廓判断，保留原生发光/队伍颜色与渲染管线。 */
@Mixin(Minecraft.class)
public class AimHighlightMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void primalspells_aimOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && AimHighlights.highlights(entity)) cir.setReturnValue(true);
    }
}
