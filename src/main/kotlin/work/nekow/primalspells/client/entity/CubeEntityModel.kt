package work.nekow.primalspells.client.entity

import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.renderer.entity.state.EntityRenderState

class CubeEntityModel(root: ModelPart) : EntityModel<EntityRenderState>(root) {
    companion object {
        fun createBodyLayer(): LayerDefinition {
            val mesh = MeshDefinition()
            mesh.root.addOrReplaceChild(
                "cube", CubeListBuilder.create().addBox(-4f, -4f, -4f, 8f, 8f, 8f),
                PartPose.ZERO
            )
            return LayerDefinition.create(mesh, 16, 16)
        }
    }
}
