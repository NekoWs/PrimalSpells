package work.nekow.primalspells.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import kotlin.math.tan

/** 只校准第一人称模型：原版手持FOV为70，世界FOV可变，补偿宝石投影的位置差。 */
object WandHandPose : IClientItemExtensions {
    override fun applyForgeHandTransform(poseStack: PoseStack, player: LocalPlayer, arm: HumanoidArm,
                                         itemInHand: ItemStack, partialTick: Float,
                                         equipProcess: Float, swingProcess: Float): Boolean {
        val worldFov = Minecraft.getInstance().gameRenderer.mainCamera().fov.toDouble().coerceIn(30.0, 150.0)
        val ratio = tan(Math.toRadians(35.0)) / tan(Math.toRadians(worldFov / 2))
        val side = if (arm == HumanoidArm.RIGHT) 1.0 else -1.0
        poseStack.translate(side * 0.38 * (ratio - 1), 0.25 * (ratio - 1), 0.0)
        // 保留原版装备/挥击变换；本类不改粒子或服务端的出生点。
        return false
    }
}
