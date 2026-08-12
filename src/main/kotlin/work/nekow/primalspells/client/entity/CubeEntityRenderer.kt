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
import net.minecraft.world.entity.Entity
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.entity.CubeEntity

class CubeEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<CubeEntity, CubeEntityRenderState>(context) {
    private val model = CubeEntityModel(context.bakeLayer(CUBE_LAYER))

    companion object {
        val CUBE_LAYER = ModelLayerLocation(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "cube"), "main")
    }

    override fun submit(state: CubeEntityRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
        submitNodeCollector.submitModel(
            model, state, poseStack,
            RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "textures/entity/cube_entity.png")),
            state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null
        )
        super.submit(state, poseStack, submitNodeCollector, camera)
    }

    override fun createRenderState() = CubeEntityRenderState()

    override fun extractRenderState(entity: CubeEntity, state: CubeEntityRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
    }
}

class CubeEntityRenderState : EntityRenderState()
