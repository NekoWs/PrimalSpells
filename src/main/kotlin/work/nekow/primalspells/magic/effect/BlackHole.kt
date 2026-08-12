package work.nekow.primalspells.magic.effect

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 黑洞效果 —— 每 5 tick 将半径 2.5 格内实体拉向中心并施加减速 I（10 tick）。
 * 粒子从球面边界向中心运动并消失。
 */
class BlackHole : BaseEffect() {

    private var cooldown = 0

    override fun onActive() {
        cooldown = 0
    }

    override fun onTick() {
        val level = caster.level() as? ServerLevel ?: return
        cooldown--
        val cx = status.pos.x.toDouble()
        val cy = status.pos.y.toDouble()
        val cz = status.pos.z.toDouble()
        val radius = 5.0
        val center = net.minecraft.world.phys.Vec3(cx, cy, cz)

        spawnParticles(level, center, radius)

        if (cooldown > 0) return
        cooldown = 5

        val aabb = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0)

        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, aabb) { it.isAlive }) {
            entity.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 10, 0, false, false), caster)
            val dir = center.subtract(entity.position()).normalize()
            val dist = entity.position().distanceTo(center)
            val strength = 0.6 / (dist + 0.5) // 距离越近吸力越强（最大 1.2）
            entity.addDeltaMovement(dir.scale(strength))
        }
    }

    /** 球面生成静止粒子 */
    private fun spawnParticles(level: ServerLevel, center: net.minecraft.world.phys.Vec3, r: Double) {
        val count = 256
        for (i in 0 until count) {
            val phi = acos(2.0 * level.random.nextDouble() - 1.0)
            val theta = level.random.nextDouble() * Math.PI * 2.0
            val px = center.x + sin(phi) * cos(theta) * r
            val py = center.y + sin(phi) * sin(theta) * r
            val pz = center.z + cos(phi) * r
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
