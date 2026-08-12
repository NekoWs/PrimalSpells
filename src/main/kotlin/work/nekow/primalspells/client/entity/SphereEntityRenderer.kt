package work.nekow.primalspells.client.entity

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.entity.SphereEntity

class SphereEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<SphereEntity, SphereEntityRenderState>(context) {
    private val model = SphereEntityModel(context.bakeLayer(SPHERE_LAYER))

    companion object {
        val SPHERE_LAYER = ModelLayerLocation(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "sphere"), "main")
    }

    override fun submit(state: SphereEntityRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
        submitNodeCollector.submitModel(
            model, state, poseStack,
            RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "textures/entity/sphere_entity.png")),
            state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null
        )
        super.submit(state, poseStack, submitNodeCollector, camera)
    }

    override fun createRenderState() = SphereEntityRenderState()

    override fun extractRenderState(entity: SphereEntity, state: SphereEntityRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
    }
}

class SphereEntityRenderState : EntityRenderState()
