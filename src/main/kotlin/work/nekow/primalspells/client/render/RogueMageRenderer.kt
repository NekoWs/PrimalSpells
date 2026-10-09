package work.nekow.primalspells.client.render

import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.monster.illager.IllagerModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState
import net.minecraft.client.renderer.entity.state.IllagerRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.monster.illager.AbstractIllager
import work.nekow.primalspells.entity.RogueMageEntity

/** 原版低模唤魔者骨架与材质，始终显示真实持杖；准备施法时抬臂。 */
class RogueMageRenderer(context: EntityRendererProvider.Context) : MobRenderer<RogueMageEntity, IllagerRenderState, IllagerModel<IllagerRenderState>>(
    context, IllagerModel(context.bakeLayer(ModelLayers.EVOKER)), 0.5f,
) {
    init { addLayer(ItemInHandLayer(this)) }
    override fun createRenderState() = IllagerRenderState()
    override fun getTextureLocation(state: IllagerRenderState) = Identifier.withDefaultNamespace("textures/entity/illager/evoker.png")
    override fun extractRenderState(entity: RogueMageEntity, state: IllagerRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, itemModelResolver, partialTick)
        state.mainArm = entity.mainArm
        state.armPose = if (entity.charging) AbstractIllager.IllagerArmPose.SPELLCASTING else AbstractIllager.IllagerArmPose.CROSSBOW_HOLD
    }
}
