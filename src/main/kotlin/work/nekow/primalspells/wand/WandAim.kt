package work.nekow.primalspells.wand

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** 眼睛射线选择准星目标，再从宝石朝该点发射；实体不会穿过遮挡方块被选中。 */
object WandAim {
    private const val RANGE = 128.0
    data class Sight(val point: Vec3, val entity: Entity?)

    fun target(caster: Entity): Vec3 {
        val eye = caster.eyePosition
        val end = eye.add(caster.lookAngle.scale(RANGE))
        return trace(caster, eye, end, true).point
    }

    /** 从真实施法位置追踪中央直线；方块先截断，只返回最近的实体接点。 */
    fun raycast(caster: Entity, origin: Vec3, direction: Vec3, range: Double): Sight =
        trace(caster, origin, origin.add(direction.normalize().scale(range)), false)

    private fun trace(caster: Entity, eye: Vec3, end: Vec3, selection: Boolean): Sight {
        val block = caster.level().clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
        var target = if (block.type == HitResult.Type.MISS) end else block.location
        var selected: Entity? = null
        var distance = eye.distanceToSqr(target)
        val bounds = net.minecraft.world.phys.AABB(eye, end).inflate(1.0)
        for (entity in caster.level().getEntities(caster, bounds) { it.isAlive && it.isPickable && !it.isSpectator }) {
            val box = entity.boundingBox.inflate(if (selection) entity.pickRadius.toDouble() else 0.0)
            // 不把自己的坐骑或眼睛所在实体选成零距离目标。
            if (entity.rootVehicle === caster.rootVehicle || selection && box.contains(eye)) continue
            val hit = if (box.contains(eye)) eye else box.clip(eye, target).orElse(null) ?: continue
            val candidate = eye.distanceToSqr(hit)
            if (candidate < distance) { target = hit; distance = candidate; selected = entity }
        }
        return Sight(target, selected)
    }

    fun direction(caster: Entity, origin: Vec3): Vec3 {
        val delta = target(caster).subtract(origin)
        return if (delta.lengthSqr() < 1.0e-10) caster.lookAngle else delta.normalize()
    }
}
