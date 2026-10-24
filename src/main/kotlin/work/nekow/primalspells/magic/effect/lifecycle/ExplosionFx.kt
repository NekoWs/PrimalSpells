package work.nekow.primalspells.magic.effect.lifecycle

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.Color
import work.nekow.particledrawing.api.CurveKey
import work.nekow.particledrawing.api.ParticleBatch
import work.nekow.particledrawing.api.ParticleCurve
import work.nekow.particledrawing.api.ParticleLifeCurve
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.particledrawing.api.ParticleSpawnSpec
import work.nekow.particledrawing.api.ParticleStyle
import work.nekow.particledrawing.api.ParticleVisual
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.util.ScreenShake
import work.nekow.primalspells.util.spellParticleSpec
import work.nekow.primalspells.util.spellVisual

/** 大像素火舌、压力碎片与烟团；只声明出生参数，运动、曲线和回收完全交给PD。 */
object ExplosionFx {
    private enum class Pattern { BLOOM, SPLIT_JET, RISING_CROWN }

    private val firePalettes = listOf(
        listOf(Color.ofInt(255, 237, 156), Color.ofInt(255, 175, 42), Color.ofInt(236, 79, 24)),
        listOf(Color.ofInt(255, 224, 128), Color.ofInt(255, 142, 40), Color.ofInt(194, 53, 32)),
        listOf(Color.ofInt(255, 244, 183), Color.ofInt(242, 194, 65), Color.ofInt(224, 101, 33)),
    )
    private val crystalPalette = listOf(Color.ofInt(212, 239, 167), Color.ofInt(109, 216, 168), Color.ofInt(236, 173, 59))
    private val white = Color.ofInt(255, 251, 215)
    private val smokeColors = listOf(Color.ofInt(122, 110, 94), Color.ofInt(68, 62, 58))
    private val square = spellVisual().style(ParticleStyle.SQUARE).anisoWorld(1f, 1f).additive(false)
    private val streak = spellVisual().style(ParticleStyle.SQUARE).anisoWorld(0.5f, 0.125f).additive(false)

    // PD曲线不可变，可以共享；不再为每片重建关键帧、排序与通道列表。
    private val fireLife = (12..24).associateWith { life ->
        envelope(CurveKey.at(0, 0), CurveKey.at(0.6, 0.85, EasingType.EASE_OUT),
            CurveKey.at(2.5, 1.12, EasingType.EASE_OUT), CurveKey.at(life * 0.28, 1),
            CurveKey.at(life * 0.65, 0.72, EasingType.EASE_IN_OUT), CurveKey.at(life, 0, EasingType.EASE_IN_OUT))
    }
    private val flashLife = (6..8).associateWith { life ->
        envelope(CurveKey.at(0, 0), CurveKey.at(0.35, 1, EasingType.EASE_OUT),
            CurveKey.at(1.2, 0.9), CurveKey.at(life, 0, EasingType.EASE_IN_OUT))
    }
    private val emberLife = (8..18).associateWith { life ->
        envelope(CurveKey.at(0, 0), CurveKey.at(0.5, 1, EasingType.EASE_OUT),
            CurveKey.at(life * 0.35, 0.9), CurveKey.at(life, 0, EasingType.EASE_IN_OUT))
    }
    private val smokeLife = (16..26).associateWith { life ->
        envelope(CurveKey.at(0, 0), CurveKey.at(2, 0.8, EasingType.EASE_OUT),
            CurveKey.at(5, 1.15, EasingType.EASE_OUT), CurveKey.at(life * 0.5, 1),
            CurveKey.at(life, 0, EasingType.EASE_IN_OUT))
    }

    /** 即时爆发；随机宏观轮廓与材质色阶，大小只影响表现，不改伤害/半径/结算时机。 */
    fun burst(manager: ParticleManager, center: Vec3, radius: Float, sourceId: String = "explosion") {
        if (radius <= 0f || !radius.isFinite()) return
        // 每次独立取种子，延后出生的烟尘不会借用别次爆炸的随机序列。
        val random = RandomSource.create(manager.level.random.nextLong())
        val r = radius.toDouble()
        val extent = sqrt(r / 3.0).coerceIn(0.65, 1.8)
        val large = r >= 5
        val pattern = Pattern.entries[random.nextInt(Pattern.entries.size)]
        val dominant = direction(random)
        val flame = if (sourceId == "dormant_crystal" || sourceId == "crystal_detonation") crystalPalette
            else firePalettes[random.nextInt(firePalettes.size)]
        val lobeCount = if (large) 5 + random.nextInt(3) else 3 + random.nextInt(2)
        val fire = Emission(manager)
        val fragments = Emission(manager)
        val axes = ArrayList<Vec3>(lobeCount)

        // 有缺口的白热前锋：少量较大片元已能交代冲击，避免320片球壳互相覆盖。
        repeat((if (large) 14 else 8) + random.nextInt(5)) {
            val d = direction(random)
            val life = 6 + random.nextInt(3)
            fire.add(center.add(d.scale(0.12)), white, (0.25 + random.nextDouble() * 0.2) * extent,
                life, flashLife.getValue(life), d.scale((0.22 + random.nextDouble() * 0.15) * extent))
        }

        repeat(lobeCount) { lobe ->
            val raw = direction(random)
            val axis = when (pattern) {
                Pattern.BLOOM -> raw.add(dominant.scale(0.3)).normalize()
                Pattern.SPLIT_JET -> dominant.scale(if (lobe % 2 == 0) 1.0 else -0.8).add(raw.scale(0.45)).normalize()
                Pattern.RISING_CROWN -> Vec3(raw.x, abs(raw.y) * 0.8 + 0.25, raw.z).normalize()
            }
            axes += axis
            val side = axis.cross(if (abs(axis.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)).normalize()
            val up = side.cross(axis).normalize()
            val power = (0.18 + random.nextDouble() * 0.13) * extent * if (pattern == Pattern.SPLIT_JET) 1.3 else 1.0
            val width = (0.22 + random.nextDouble() * 0.14) * extent
            val layers = 2 + random.nextInt(2)
            val turn = random.nextDouble() * PI * 2
            repeat(layers) { slice ->
                // 每截面是一个亮心与3～4个厚边；长短、分叉、宽度整束相关，不是球形撒点。
                val sides = 3 + random.nextInt(2)
                repeat(sides + 1) { part ->
                    val angle = turn + (part - 1) * PI * 2 / sides + slice * 0.35
                    val offset = if (part == 0) Vec3.ZERO else
                        side.scale(cos(angle) * width).add(up.scale(sin(angle) * width))
                    val life = 12 + random.nextInt(9) + slice * 2
                    val color = flame[if (part == 0 && slice == layers - 1) 0 else if (part == 0) 1 else 2]
                    val size = (if (part == 0) 0.7 else 0.48) * extent * (0.85 + random.nextDouble() * 0.3)
                    fire.add(center.add(axis.scale(slice * 0.12)).add(offset.scale(0.5)), color, size,
                        life, fireLife.getValue(life), axis.scale(power * (0.7 + slice * 0.25))
                            .add(offset.scale(0.07)).add(0.0, 0.015, 0.0))
                }
            }
        }

        // 余烬和条状压力碎片数量有限；初速一次下发，后续弹道由PD两端积分。
        repeat((if (large) 16 else 8) + random.nextInt(5)) {
            val d = direction(random)
            val life = 9 + random.nextInt(8)
            fragments.add(center.add(d.scale(0.15)), flame[random.nextInt(flame.size)],
                (0.08 + random.nextDouble() * 0.08) * extent, life, emberLife.getValue(life),
                d.scale((0.25 + random.nextDouble() * 0.3) * extent).add(0.0, 0.08, 0.0))
        }
        repeat((if (pattern == Pattern.SPLIT_JET) 10 else 6) + random.nextInt(4)) {
            val d = direction(random)
            val life = 8 + random.nextInt(5)
            fragments.add(center.add(d.scale(0.2)), flame[0], extent, life, emberLife.getValue(life),
                d.scale((0.4 + random.nextDouble() * 0.25) * extent), streak.copy().alignTo(Vec3.ZERO, d))
        }
        fire.spawn(Vec3(0.0, 0.002, 0.0))
        fragments.spawn(Vec3(0.0, -0.018, 0.0))

        // 一次延后出生的翻卷烟团，沿此次火舌翻起；不再追加三批128片均匀烟球。
        Deferred.after(manager.level, 3) {
            val smoke = Emission(manager)
            for (axis in axes) repeat(3) {
                val scatter = direction(random).scale(0.25 * extent)
                val life = 16 + random.nextInt(11)
                smoke.add(center.add(axis.scale(0.4 * extent)).add(scatter), smokeColors[random.nextInt(smokeColors.size)],
                    (0.55 + random.nextDouble() * 0.35) * extent, life, smokeLife.getValue(life),
                    axis.scale(0.055 * extent).add(scatter.scale(0.025)).add(0.0, 0.045, 0.0))
            }
            smoke.spawn(Vec3(0.0, 0.0015, 0.0))
        }
        ScreenShake.shake(manager.level, center, radius = r * 2 + 3,
            amplitude = (r * 0.06).coerceAtMost(0.55).toFloat(), ticks = 8)
    }

    private fun direction(random: RandomSource): Vec3 {
        val y = random.nextDouble() * 2 - 1
        val angle = random.nextDouble() * PI * 2
        val w = sqrt(1 - y * y)
        return Vec3(cos(angle) * w, y, sin(angle) * w)
    }

    private fun envelope(vararg keys: CurveKey): ParticleLifeCurve = ParticleLifeCurve.of(ParticleCurve.scale(*keys))

    /** 只保留本次出生参数；生成后成员、运动与回收归PD，不维护自有粒子列表。 */
    private class Emission(private val manager: ParticleManager) {
        private val specs = ArrayList<ParticleSpawnSpec>(128)
        private val velocities = ArrayList<Vec3>(128)

        fun add(pos: Vec3, color: Color, size: Double, life: Int, curve: ParticleLifeCurve, velocity: Vec3,
                visual: ParticleVisual = square) {
            specs += spellParticleSpec().position(pos).color(color).scale(size.toFloat()).visual(visual)
                .lifetime(life).lifeCurve(curve)
            velocities += velocity
        }

        fun spawn(acceleration: Vec3) {
            // 每批最多123片，低于PD公开spawnAll上限；容量拒绝时按原索引对齐速度。
            val handles = manager.spawnAll(specs)
            val batch = ParticleBatch(manager)
            val acceptedVelocity = ArrayList<Vec3>(handles.size)
            handles.forEachIndexed { i, handle ->
                if (handle != null) {
                    batch.add(handle)
                    acceptedVelocity += velocities[i]
                }
            }
            batch.setVelocityAll(acceptedVelocity)
            batch.applyForceAll(List(acceptedVelocity.size) { acceleration }, -1)
        }
    }
}
