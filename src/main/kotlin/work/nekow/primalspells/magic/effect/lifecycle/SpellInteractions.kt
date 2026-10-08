package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.magic.projectile.Projectile

/** 服务端投射物反应：接触水晶蓄能、冰火湮灭、黑洞吞弹；每对每tick只结算一次。 */
object SpellInteractions {
    /** 只遍历本tick快照；命中链只标记状态，死亡/爆炸仍由既有生命周期结算。 */
    fun tick(shots: List<Projectile>) {
        for (i in shots.indices) for (j in i + 1 until shots.size) {
            val a = shots[i]; val b = shots[j]
            if (!a.alive || !b.alive || a.caster.level() !== b.caster.level()) continue
            val level = a.caster.level() as? ServerLevel ?: continue
            val pa = point(a); val pb = point(b)
            val crystalA = a.effects.filterIsInstance<CrystalReaction>().firstOrNull()
            val crystalB = b.effects.filterIsInstance<CrystalReaction>().firstOrNull()
            if (crystalA != null && touches(b, pa, 0.75)) crystalA.charge(b)
            if (crystalB != null && touches(a, pb, 0.75)) crystalB.charge(a)
            if (pa.distanceToSqr(pb) > 0.7 * 0.7) continue
            val blackA = a.effects.any { it is BlackHole }; val blackB = b.effects.any { it is BlackHole }
            if (blackA != blackB) {
                val hole = if (blackA) a else b; val food = if (blackA) b else a
                // 场源之间互相牵引但不吞噬，避免重叠场导致无意的无限能量循环。
                if (food.effects.none { it is AreaFieldEffect || it is RepulsionField }) {
                    hole.storedEnergy += food.status.damage.coerceAtLeast(1.0) + food.status.explosionDamage * 0.25
                    food.alive = false
                    PixelVfx.arc(ParticleManager.of(level), point(food), point(hole), Color.ofInt(193, 128, 255))
                }
                continue
            }
            if ((a.status.freezeTicks > 0 && hot(b)) || (b.status.freezeTicks > 0 && hot(a))) {
                a.alive = false; b.alive = false
                val manager = ParticleManager.of(level)
                PixelVfx.burst(manager, pa.lerp(pb, 0.5), Color.ofInt(185, 231, 241), 144, 0.3, 18, -0.006)
                PixelVfx.ring(manager, pa, Vec3(0.0, 1.0, 0.0), 0.25, Color.ofInt(117, 219, 255), 12, 5f)
            }
        }
    }
    /** 光束沿实际截断线段也能给水晶充能，不能只检查束源位置。 */
    fun beam(shot: Projectile, from: Vec3, to: Vec3, shots: List<Projectile>) {
        shots.filter { it !== shot && it.alive && it.caster.level() === shot.caster.level() }.forEach { other ->
            if (segmentDistance(point(other), from, to) <= 0.75)
                other.effects.filterIsInstance<CrystalReaction>().forEach { it.charge(shot) }
        }
    }
    /** 爆炸冲击也对魔法弹施力，水晶触发由Explosion的统一通知入口处理。 */
    fun shock(level: ServerLevel, center: Vec3, radius: Double, shots: List<Projectile>) {
        shots.filter { it.alive && it.caster.level() === level }.forEach { shot ->
            val d = point(shot).subtract(center); val dist = d.length()
            if (dist in 0.05..radius && shot.motionEnabled) {
                val push = d.normalize().scale((1 - dist / radius) * 0.85)
                shot.status.velocity.add(push.x.toFloat(), push.y.toFloat(), push.z.toFloat())
            }
        }
    }
    private fun touches(shot: Projectile, center: Vec3, radius: Double): Boolean {
        val p = point(shot)
        val v = Vec3(shot.status.velocity.x.toDouble(), shot.status.velocity.y.toDouble(), shot.status.velocity.z.toDouble())
        return segmentDistance(center, p.subtract(v), p) <= radius
    }
    private fun segmentDistance(p: Vec3, a: Vec3, b: Vec3): Double {
        val d = b.subtract(a)
        if (d.lengthSqr() < 1e-8) return p.distanceTo(a)
        return p.distanceTo(a.add(d.scale(p.subtract(a).dot(d).div(d.lengthSqr()).coerceIn(0.0, 1.0))))
    }
    private fun point(p: Projectile) = Vec3(p.status.pos.x.toDouble(), p.status.pos.y.toDouble(), p.status.pos.z.toDouble())
    private fun hot(p: Projectile) = p.id in setOf("fireball", "trigger_fireball", "laser", "plasma_beam") && p.status.freezeTicks == 0
}
