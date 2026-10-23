package work.nekow.primalspells.util

import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc

/** 世界坐标禁止先转float；仅局部方向/速度使用Vector3f。 */
fun Vec3.toPosition(): Vector3d = Vector3d(x, y, z)
/** 传回原版/PD时保持世界坐标精度。 */
fun Vector3dc.toVec3(): Vec3 = Vec3(x(), y(), z())
