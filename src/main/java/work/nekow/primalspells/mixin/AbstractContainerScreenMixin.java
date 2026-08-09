package work.nekow.primalspells.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.item.WandItem;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Inject(method = "showTooltipWithItemInHand", at = @At("RETURN"), cancellable = true)
    private void primalspells_showWandTooltip(ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        if (item.getItem() instanceof WandItem) cir.setReturnValue(true);
    }
}
