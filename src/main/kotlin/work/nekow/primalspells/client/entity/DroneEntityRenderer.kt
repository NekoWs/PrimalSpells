package work.nekow.primalspells.client.entity

import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.entity.DroneEntity

class DroneEntityRenderer(context: EntityRendererProvider.Context) :
    LivingEntityRenderer<DroneEntity, LivingEntityRenderState, DroneEntityModel>(
        context, DroneEntityModel(context.bakeLayer(DRONE_LAYER)), 0.5f
    ) {

    companion object {
        val DRONE_LAYER = ModelLayerLocation(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "drone"), "main")
    }

    override fun getTextureLocation(state: LivingEntityRenderState) =
        Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "textures/entity/drone.png")

    override fun createRenderState() = LivingEntityRenderState()
}
