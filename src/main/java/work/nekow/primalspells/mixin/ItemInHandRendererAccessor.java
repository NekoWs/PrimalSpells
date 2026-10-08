package work.nekow.primalspells.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 实机回归检查读取原版高度，验证成功使用的回调没有重新放下法杖。 */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {
    @Accessor("mainHandHeight") float primalspells_mainHandHeight();
    @Accessor("offHandHeight") float primalspells_offHandHeight();
}
