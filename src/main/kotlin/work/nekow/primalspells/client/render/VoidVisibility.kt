package work.nekow.primalspells.client.render

import com.google.common.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier
import net.minecraft.util.context.ContextKey
import net.minecraft.world.entity.LivingEntity
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent
import net.neoforged.neoforge.client.event.RenderLivingEvent
import net.neoforged.neoforge.client.event.RenderPlayerEvent
import net.neoforged.neoforge.client.event.RenderHandEvent
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus

/** 连同装备、姓名与轮廓隐藏；仅依据服务端已同步的虚空标记。 */
object VoidVisibility {
    val PHASED = ContextKey<Boolean>(Identifier.fromNamespaceAndPath("primalspells", "void_phase"))

    fun register(bus: IEventBus) {
        bus.addListener<RegisterRenderStateModifiersEvent> { event ->
            event.registerEntityModifier(object : TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, *>>() {}) { entity, state ->
                val hidden = VoidStatus.isPhased(entity)
                state.setRenderData(PHASED, hidden)
                if (hidden) { state.outlineColor = 0; state.nameTag = null; state.shadowPieces.clear(); state.shadowRadius = 0f }
            }
        }
        NeoForge.EVENT_BUS.addListener<RenderLivingEvent.Pre<*, *, *>> {
            if (it.renderState.getRenderData(PHASED) == true) it.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener<RenderPlayerEvent.Pre<*>> {
            if (it.renderState.getRenderData(PHASED) == true) it.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener<RenderHandEvent> {
            if (VoidStatus.isPhased(Minecraft.getInstance().player)) it.isCanceled = true
        }
    }
}
