package work.nekow.primalspells.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.nekow.primalspells.item.WandItem;

/** 原版成功使用会直接清零手持高度；法杖保留当前高度，切物品仍正常装备。 */
@Mixin(ItemInHandRenderer.class)
public class WandUseAnimationMixin {
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void primalspells_keepWandRaised(InteractionHand hand, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getItemInHand(hand).getItem() instanceof WandItem) ci.cancel();
    }
}
