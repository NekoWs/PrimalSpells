package work.nekow.primalspells.client.render

import net.minecraft.client.model.HumanoidModel
import net.minecraft.world.entity.HumanoidArm
import net.neoforged.fml.common.asm.enumextension.EnumProxy
import net.neoforged.neoforge.client.IArmPoseTransformer

/** 独立枚举参数类：单手抬起前臂握住杖柄，另一只手仍可自由动作。 */
object WandArmPose {
    @JvmField
    val POSE = EnumProxy(HumanoidModel.ArmPose::class.java, false, false,
        IArmPoseTransformer { model, state, arm ->
            val right = arm == HumanoidArm.RIGHT
            val part = if (right) model.rightArm else model.leftArm
            // 同一第一人称宝石点的高度；短杖握柄在掌心，杆轴垂直于前臂。
            val pitch = state.xRot * (Math.PI / 180.0)
            val gemHeight = 1.62 - 0.85 * kotlin.math.sin(pitch) + 0.25 * kotlin.math.cos(pitch)
            val length = kotlin.math.hypot(0.625, 0.7625)
            part.xRot = -(kotlin.math.atan2(0.625, 0.7625) +
                kotlin.math.asin(((gemHeight - 1.375) / length).coerceIn(-0.98, 0.98))).toFloat()
            part.yRot = model.head.yRot * 0.5f + if (right) -0.08f else 0.08f
            part.zRot = if (right) 0.06f else -0.06f
        })
}
