package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.ParticleShapes

/** MC 像素爆发：实色闪块、外飞方形碎片、灰色尘屑；运动和寿命均由 PD 批次负责。 */
object ExplosionFx {
    /** 触发即爆；不改结算时机，不生成半透明球壳或柔光烟雾。 */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float) {
        if (radius <= 0f) return
        val specs = ArrayList<ParticleSpawnSpec>()
        val velocities = ArrayList<Vec3>()
        val colors = listOf(Color.ofInt(232, 248, 255), Color.ofInt(112, 192, 240), Color.ofInt(72, 136, 184))
        val count = (radius * 32).toInt().coerceIn(64, 160)
        ParticleShapes.fibonacciSphere(count).forEachIndexed { index, direction ->
            val life = 8 + index % 15
            val size = if (index % 4 == 0) 0.25f else 0.1875f
            specs += ParticleSpawnSpec().position(center.add(direction.scale(radius * 0.16)))
                .color(colors[index % colors.size]).visual(square(size, true)).lifetime(life)
                .shrinkTo(0.05f, life - 3, EasingType.LINEAR)
            velocities += direction.scale((radius * 0.055 + 0.10).coerceAtMost(0.45))
        }
        repeat(4) { index ->
            specs += ParticleSpawnSpec().position(center.add((index % 2 - 0.5) * 0.25, index / 2 * 0.25, 0.0))
                .color(colors.first()).visual(square(0.375f, true)).lifetime(2 + index)
            velocities += Vec3.ZERO
        }
        val dust = ParticleShapes.fibonacciSphere(20)
        repeat(20) { index ->
            val direction = dust[index]
            val life = 12 + index % 12
            specs += ParticleSpawnSpec().position(center.add(direction.scale(radius * 0.25)))
                .color(if (index % 2 == 0) Color.ofInt(112, 120, 128) else Color.ofInt(160, 168, 176))
                .visual(square(if (index % 3 == 0) 0.25f else 0.125f, false)).lifetime(life)
                .shrinkTo(0.05f, life - 4, EasingType.LINEAR)
            velocities += direction.scale(0.06).add(0.0, 0.045, 0.0)
        }
        val batch = ParticleBatch(manager)
        batch.addAll(manager.spawnAll(specs))
        batch.setVelocityAll(velocities)
    }

    private fun square(size: Float, bright: Boolean): ParticleVisual =
        ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(size, size).glowing(bright).additive(false)
}
