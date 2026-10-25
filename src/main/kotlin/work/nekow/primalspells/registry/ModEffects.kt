package work.nekow.primalspells.registry

import net.minecraft.core.registries.Registries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.network.codec.ByteBufCodecs
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells

/** 原版效果计时/保存，NeoForge附件同步虚空标记，不改写原有隐身、无敌或游戏模式。 */
object ModEffects {
    val ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PrimalSpells.MODID)
    val VOID_PHASED = ATTACHMENTS.register("void_phased", java.util.function.Supplier {
        AttachmentType.builder(java.util.function.Supplier { false }).sync(ByteBufCodecs.BOOL).build()
    })
    val EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, PrimalSpells.MODID)
    val VOID_PHASE = EFFECTS.register("void_phase", java.util.function.Supplier {
        object : MobEffect(MobEffectCategory.BENEFICIAL, 0x8E69CA) {}
    })
}
