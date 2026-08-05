package work.nekow.primalspells.magic.effect

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class HitEntity(
    private val radius: Double,
    private val filter: (Entity) -> Boolean = { true },
    private val pierce: Boolean = false
) : BaseEffect() {
    override fun onTick() {
        val m = magic ?: return
        if (!m.alive) return

        val level = caster.level() as? ServerLevel ?: return

        val center = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
        val aabb = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0)
        level.getEntities(null, aabb) { it != caster && filter(it) }.forEach { entity ->
            if (!pierce && entity in status.hitEntities) return@forEach
            status.hitEntities.add(entity)
            m.hitEntity(entity)
        }
    }
}
