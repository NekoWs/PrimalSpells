package work.nekow.primalspells.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 公开 AbstractContainerScreen.slotClicked（protected），
 * 供客户端拖放交换（ClientContainerDragHandler）以原版协议执行拾起/放下。
 * 注意：26.2 已将 ClickType 改名为 ContainerInput。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvokerMixin {
    @Invoker("slotClicked")
    void primalspells_slotClicked(Slot slot, int slotId, int button, ContainerInput containerInput);
}
