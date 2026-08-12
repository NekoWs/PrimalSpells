package work.nekow.primalspells.magic.effect

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * 重力场效果 —— 每 50 tick 对半径 2.5 格内实体施加迟缓 IV（持续 100 tick）。
 * 球体边界上生成粒子环，对施法者自身也生效。
 */
class GravityField : BaseEffect() {

    private var cooldown = 0

    override fun onActive() {
        cooldown = 0
    }

    override fun onTick() {
        val level = caster.level() as? ServerLevel ?: return
        cooldown--
        if (cooldown > 0) {
            spawnParticles(level) // 非施法 tick 也生成粒子，保持视觉效果
            return
        }
        cooldown = 5

        val cx = status.pos.x.toDouble()
        val cy = status.pos.y.toDouble()
        val cz = status.pos.z.toDouble()
        val radius = 5.0
        val aabb = AABB.ofSize(net.minecraft.world.phys.Vec3(cx, cy, cz), radius * 2.0, radius * 2.0, radius * 2.0)

        // 迟缓 IV（amplifier=3），包含施法者自身
        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, aabb) { it.isAlive }) {
            entity.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 10, 3, false, false), caster)
        }
    }

    /** 在球体边界上生成粒子环 */
    private fun spawnParticles(level: ServerLevel) {
        val cx = status.pos.x.toDouble()
        val cy = status.pos.y.toDouble()
        val cz = status.pos.z.toDouble()
        val r = 2.5
        val count = 12
        for (i in 0 until count) {
            val phi = Math.acos(2.0 * level.random.nextDouble() - 1.0) // 球面均匀采样
            val theta = level.random.nextDouble() * Math.PI * 2.0
            val px = cx + sin(phi) * cos(theta) * r
            val py = cy + sin(phi) * sin(theta) * r
            val pz = cz + cos(phi) * r
            level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}
