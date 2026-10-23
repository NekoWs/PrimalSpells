package work.nekow.primalspells.magic.modifier

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.util.spellVisual

/** 远距施放：在 SETUP 后将本次投射物出生点前移；从触发荷载施放时仍相对命中点计算。 */
class LongDistanceCast : Modifier() {
    override val id = "long_distance_cast"
    init {
        mana = 6.0
        delay = 2
        effects += object : BaseEffect() {
            override fun onActive() {
                if (!isStackPrimary) return
                val level = caster.level() as? ServerLevel ?: return
                val origin = Vec3(status.pos.x.toDouble(), status.pos.y.toDouble(), status.pos.z.toDouble())
                val direction = if (status.velocity.lengthSquared() > 1e-8f)
                    Vec3(status.velocity.x.toDouble(), status.velocity.y.toDouble(), status.velocity.z.toDouble()).normalize()
                    else projectile.launchDirection
                fun insideCollider(point: Vec3): Boolean {
                    val pos = net.minecraft.core.BlockPos.containing(point)
                    val local = point.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    return level.getBlockState(pos).getCollisionShape(level, pos).toAabbs().any { it.contains(local) }
                }
                // Move 的子步碰撞点可深入表面不超过 1/16 格；触发荷载先沿来向挪到邻近空气，
                // 否则从实体方块内部开始 clip 会直接返回起点。只允许 1/8 格修正，不能借此穿墙。
                val start = if (insideCollider(origin)) {
                    val nearby = origin.add(direction.scale(0.125))
                    if (!insideCollider(nearby)) nearby else origin
                } else origin
                val end = start.add(direction.scale((6.0 * stackScale).coerceAtMost(12.0)))
                val hit = level.clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                val destination = if (hit.type == net.minecraft.world.phys.HitResult.Type.MISS) end
                    else hit.location.subtract(direction.scale(0.15))
                status.pos.set(destination.x, destination.y, destination.z)
                val manager = ParticleManager.of(level)
                val group = manager.createGroup(destination)
                Draw.circle(manager, destination, 0.45, 48, colorFn = ColorSource.of(Color.ofInt(180, 135, 255, 255)),
                    scale = 0.65f, group = group, visual = spellVisual().style(ParticleStyle.SQUARE))
                group.scale(1.6f, 4).delay(4).scale(0f, 4).destroyAfter(4)
            }
        }
    }
}
