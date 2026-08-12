package work.nekow.primalspells.client.entity

import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState

class DroneEntityModel(root: ModelPart) : EntityModel<LivingEntityRenderState>(root) {
    val bbMain: ModelPart = root.getChild("bb_main")

    companion object {
        fun createBodyLayer(): LayerDefinition {
            val mesh = MeshDefinition()
            mesh.root.addOrReplaceChild("bb_main",
                CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-2.0f, -4.0f, -2.0f, 4.0f, 4.0f, 4.0f)
                    .texOffs(0, 8).addBox(-1.0f, -3.0f, -3.0f, 2.0f, 2.0f, 1.0f)
                    .texOffs(6, 11).addBox(-1.0f, -2.0f, 2.0f, 2.0f, 1.0f, 1.0f)
                    .texOffs(6, 8).addBox(2.0f, -2.0f, -1.0f, 1.0f, 1.0f, 2.0f)
                    .texOffs(0, 11).addBox(-3.0f, -2.0f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f)
            )
            return LayerDefinition.create(mesh, 16, 16)
        }
    }
}
