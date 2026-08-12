package work.nekow.primalspells.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.nekow.primalspells.item.WandItem;
import work.nekow.nekoui.FloatingWindowManager;
import work.nekow.nekoui.pouch.PouchWindowManager;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Inject(method = "showTooltipWithItemInHand", at = @At("RETURN"), cancellable = true)
    private void primalspells_showWandTooltip(ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        if (item.getItem() instanceof WandItem) cir.setReturnValue(true);
    }

    /**
     * 悬浮窗覆盖区域内屏蔽槽位悬停：
     * 鼠标位于法杖窗或小包窗矩形内时视为无悬停槽位，使下方容器的槽位高亮与 tooltip 不再响应。
     * 不修改鼠标坐标本身，携带物品的渲染不受影响。
     */
    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void primalspells_blockHoveredSlot(double x, double y, CallbackInfoReturnable<Slot> cir) {
        if (FloatingWindowManager.isPointInsideCurrentWindow((int) x, (int) y) ||
            PouchWindowManager.isPointInsideWindow((int) x, (int) y)) {
            cir.setReturnValue(null);
        }
    }
}
