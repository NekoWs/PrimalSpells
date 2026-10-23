package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.magic.projectile.Projectile

/** 服务端投射物反应：接触水晶蓄能、冰火湮灭、黑洞吞弹；每对每tick只结算一次。 */
object SpellInteractions {
    private class Reaction(val shot: Projectile, val level: ServerLevel) {
        val point = point(shot)
        val crystal = shot.effects.firstOrNull { it is CrystalReaction } as? CrystalReaction
        val blackHole = shot.effects.any { it is BlackHole }
        val field = shot.effects.any { it is AreaFieldEffect || it is RepulsionField }
        val special = crystal != null || blackHole
    }

    /** 只枚举至少一方具有反应能力的组合；保留原先i/j顺序，避免改写吞噬/冰火先后。 */
    fun tick(shots: List<Projectile>) {
        if (shots.none { it.alive && !it.awaitingRelease &&
            (it.status.freezeTicks > 0 || it.effects.any { effect -> effect is CrystalReaction || effect is BlackHole }) }) return
        val members = shots.mapNotNull { p ->
            if (!p.alive || p.awaitingRelease) null else (p.caster.level() as? ServerLevel)?.let { Reaction(p, it) }
        }
        val special = members.indices.filter { members[it].special }
        val coldIndices = members.indices.filter { members[it].special || members[it].shot.status.freezeTicks > 0 }
        val hotIndices = members.indices.filter { members[it].special || hot(members[it].shot) }
        for (i in members.indices) {
            val a = members[i]
            if (a.special) {
                for (j in i + 1 until members.size) react(a, members[j])
            } else {
                // 冻结弹彼此、普通热弹彼此也不反应，不让冰冻机枪重新退化成全配对。
                val targets = if (a.shot.status.freezeTicks > 0) hotIndices else if (hot(a.shot)) coldIndices else special
                for (j in targets) if (j > i) react(a, members[j])
            }
        }
    }

    private fun react(a: Reaction, b: Reaction) {
        val first = a.shot; val second = b.shot
        if (!first.alive || !second.alive || a.level !== b.level) return
        a.crystal?.let { if (touches(second, a.point, 0.75)) it.charge(second) }
        b.crystal?.let { if (touches(first, b.point, 0.75)) it.charge(first) }
        if (a.point.distanceToSqr(b.point) > 0.7 * 0.7) return
        if (a.blackHole != b.blackHole) {
            val hole = if (a.blackHole) first else second
            val food = if (a.blackHole) b else a
            if (!food.field) {
                hole.storedEnergy += food.shot.status.damage.coerceAtLeast(1.0) + food.shot.status.explosionDamage * 0.25
                food.shot.alive = false
                PixelVfx.arc(ParticleManager.of(a.level), food.point, if (a.blackHole) a.point else b.point, Color.ofInt(193, 128, 255))
            }
            return
        }
        if ((first.status.freezeTicks > 0 && hot(second)) || (second.status.freezeTicks > 0 && hot(first))) {
            first.alive = false; second.alive = false
            val manager = ParticleManager.of(a.level)
            PixelVfx.burst(manager, a.point.lerp(b.point, 0.5), Color.ofInt(185, 231, 241), 144, 0.3, 18, -0.006)
            PixelVfx.ring(manager, a.point, Vec3(0.0, 1.0, 0.0), 0.25, Color.ofInt(117, 219, 255), 12, 5f)
        }
    }
    /** 光束沿实际截断线段也能给水晶充能，不能只检查束源位置。 */
    fun beam(shot: Projectile, from: Vec3, to: Vec3, shots: List<Projectile>) {
        shots.filter { it !== shot && it.alive && !it.awaitingRelease && it.caster.level() === shot.caster.level() }.forEach { other ->
            if (segmentDistance(point(other), from, to) <= 0.75)
                other.effects.filterIsInstance<CrystalReaction>().forEach { it.charge(shot) }
        }
    }
    /** 爆炸冲击也对魔法弹施力，水晶触发由Explosion的统一通知入口处理。 */
    fun shock(level: ServerLevel, center: Vec3, radius: Double, damage: Double, shots: List<Projectile>) {
        shots.filter { it.alive && !it.awaitingRelease && it.caster.level() === level }.forEach { shot ->
            val d = point(shot).subtract(center); val dist = d.length()
            if (dist in 0.05..radius && shot.motionEnabled) {
                val push = d.normalize().scale(SpellImpact.strength(damage * (1 - dist / radius), blast = true) / shot.mass)
                shot.status.velocity.add(push.x.toFloat(), push.y.toFloat(), push.z.toFloat())
            }
        }
    }
    private fun touches(shot: Projectile, center: Vec3, radius: Double): Boolean {
        // 反射后的速度不能反推旧路径；否则墙外没经过的水晶会被凭空激活。
        val path = shot.flightPath
        if (path.size < 2) return point(shot).distanceToSqr(center) <= radius * radius
        for (i in 1 until path.size) {
            val from = path[i - 1]; val to = path[i]
            // 精确距离之前先用段包围盒拒绝；不创建整条路径的Pair列表。
            if (center.x < minOf(from.x, to.x) - radius || center.x > maxOf(from.x, to.x) + radius ||
                center.y < minOf(from.y, to.y) - radius || center.y > maxOf(from.y, to.y) + radius ||
                center.z < minOf(from.z, to.z) - radius || center.z > maxOf(from.z, to.z) + radius) continue
            if (segmentDistanceSquared(center, from, to) <= radius * radius) return true
        }
        return false
    }
    private fun segmentDistance(p: Vec3, a: Vec3, b: Vec3): Double = kotlin.math.sqrt(segmentDistanceSquared(p, a, b))
    private fun segmentDistanceSquared(p: Vec3, a: Vec3, b: Vec3): Double {
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        val px = p.x - a.x; val py = p.y - a.y; val pz = p.z - a.z
        val length = dx * dx + dy * dy + dz * dz
        if (length < 1e-8) return px * px + py * py + pz * pz
        val t = ((px * dx + py * dy + pz * dz) / length).coerceIn(0.0, 1.0)
        val ex = px - dx * t; val ey = py - dy * t; val ez = pz - dz * t
        return ex * ex + ey * ey + ez * ez
    }
    private fun point(p: Projectile) = Vec3(p.status.pos.x.toDouble(), p.status.pos.y.toDouble(), p.status.pos.z.toDouble())
    private val HOT_SPELLS = setOf("fireball", "trigger_fireball", "laser", "plasma_beam")
    private fun hot(p: Projectile) = p.id in HOT_SPELLS && p.status.freezeTicks == 0
}
