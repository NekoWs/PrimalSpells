package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.util.ScreenShake

/** 闪爆、压缩火团、冲击环、放射火星与翻涌尘团；固定实色，不把爆炸做成匀速撒球。 */
object ExplosionFx {
    /** 同一次结算立即出现闪爆，后续烟团仅是演出，不延迟伤害。 */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float) {
        if (radius <= 0f) return
        val r = radius.toDouble()
        val white = Color.ofInt(232, 248, 255)
        val cyan = Color.ofInt(108, 210, 255)
        val flame = listOf(Color.ofInt(255, 241, 172), Color.ofInt(255, 182, 57), Color.ofInt(238, 90, 35))
        val specs = ArrayList<ParticleSpawnSpec>()
        val velocities = ArrayList<Vec3>()
        val count = (radius * 80).toInt().coerceAtLeast(160)
        ParticleShapes.fibonacciSphere(count).forEachIndexed { i, dir ->
            val spoke = if (i % 7 == 0) 1.8 else 0.6 + (i % 13) / 18.0
            specs += ParticleSpawnSpec().position(center.add(dir.scale(0.3)))
                .color(if (i % 5 == 0) cyan else flame[i % 3])
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(
                    if (i % 7 == 0) 0.375f else 0.125f, 0.0625f).glowing(true).spin(i * 0.71))
                .lifetime(12 + i % 17)
            velocities += dir.scale((0.25 + r * 0.07) * spoke).add(0.0, 0.06, 0.0)
        }
        val batch = ParticleBatch(manager).also { it.addAll(manager.spawnAll(specs)) }
        batch.setVelocityAll(velocities)
        batch.applyForceAll(velocities.map { it.scale(-0.025).add(0.0, -0.017, 0.0) }, 22)

        // 白热块只停两拍；实体火团不是透明光晕，分瓣喷出后断裂。
        val flash = Draw.sphere(manager, center, r * 0.18, 144, ColorSource.of(white), 1.8f,
            visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))
        flash.scale(1.8f, 2, EasingType.EASE_OUT).destroyAfter(3)
        repeat(8) { lobe ->
            val d = ParticleShapes.fibonacciSphere(8)[lobe]
            val group = Draw.sphere(manager, center.add(d.scale(r * 0.18)), r * 0.15, 72,
                ColorSource.of(flame[lobe % 3]), 1.0f,
                visual = ParticleVisual().style(ParticleStyle.SQUARE).glowing(true))
            group.move(d.scale(r * 0.5), 6, EasingType.EASE_OUT).scale(1.5f, 4).destroyAfter(5 + lobe % 4)
        }
        for (n in listOf(Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.15, 0.0), Vec3(0.0, 0.3, 1.0)))
            PixelVfx.ring(manager, center, n, r * 0.25, cyan, 11, 5f)
        ScreenShake.shake(manager.level, center, radius = r * 5 + 16, amplitude = (0.3 + r * 0.09).toFloat(), ticks = 9)

        // 分时喷出的烟团向上翻卷，内暗外亮，保持轮廓块面；粒子寿命负责断裂收场。
        for (beat in listOf(2, 5, 9)) Deferred.after(manager.level, beat) {
            val puffSpecs = ArrayList<ParticleSpawnSpec>()
            val puffVelocities = ArrayList<Vec3>()
            ParticleShapes.fibonacciSphere(12).forEachIndexed { puff, d ->
                val c = center.add(d.scale(r * (0.35 + beat * 0.035))).add(0.0, beat * 0.04, 0.0)
                ParticleShapes.fibonacciSphere(32).forEachIndexed { i, local ->
                    puffSpecs += ParticleSpawnSpec().position(c.add(local.scale(0.22 + r * 0.04)))
                        .color(if ((i + puff) % 3 == 0) Color.ofInt(172, 178, 186) else Color.ofInt(68, 73, 84))
                        .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.25f, 0.25f).glowing(false))
                        .lifetime(12 + i % 12)
                    puffVelocities += d.scale(0.06).add(local.cross(Vec3(0.0, 1.0, 0.0)).scale(0.018)).add(0.0, 0.045, 0.0)
                }
            }
            val smoke = ParticleBatch(manager).also { it.addAll(manager.spawnAll(puffSpecs)) }
            smoke.setVelocityAll(puffVelocities)
        }
    }
}
