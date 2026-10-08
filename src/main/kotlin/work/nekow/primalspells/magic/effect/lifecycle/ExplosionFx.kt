package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.util.ParticleShapes
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.util.ScreenShake
import kotlin.math.*

/** 非对称火舌向外撕裂、火星弹道和翻卷烟尘；每次爆炸独立取样，绝不整体收球。 */
object ExplosionFx {
    fun burst(manager: ParticleManager, center: Vec3, radius: Float, sourceId: String = "explosion") {
        if (radius <= 0f) return
        val r = radius.toDouble()
        val random = manager.level.random
        val crystal = sourceId == "dormant_crystal" || sourceId == "crystal_detonation"
        val flame = if (crystal) listOf(Color.ofInt(212, 239, 167), Color.ofInt(109, 216, 168), Color.ofInt(236, 173, 59))
            else listOf(Color.ofInt(255, 237, 156), Color.ofInt(255, 175, 42), Color.ofInt(236, 79, 24))
        fun direction(): Vec3 {
            val y = random.nextDouble() * 2 - 1
            val angle = random.nextDouble() * PI * 2
            val w = sqrt(1-y*y)
            return Vec3(cos(angle)*w, y, sin(angle)*w)
        }
        // spawnAll的公开创建上限为256；位置/受力更新由PD批次自行分包。
        fun emit(specs: List<ParticleSpawnSpec>, velocities: List<Vec3>, gravity: Double = 0.0) {
            val batch = ParticleBatch(manager)
            specs.chunked(256).forEach { batch.addAll(manager.spawnAll(it)) }
            batch.setVelocityAll(velocities)
            if (gravity != 0.0) batch.applyForceAll(List(specs.size) { Vec3(0.0, -gravity, 0.0) }, 24)
        }
        val sparks = ArrayList<ParticleSpawnSpec>()
        val sparkVelocity = ArrayList<Vec3>()
        repeat((radius * 80).toInt().coerceIn(160, 640)) {
            val d = direction()
            val life = 12 + random.nextInt(17)
            val fast = random.nextDouble() < 0.18
            sparks += ParticleSpawnSpec().position(center.add(d.scale(0.2)))
                .color(flame[random.nextInt(flame.size)])
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(if (fast) 0.375f else 0.125f, 0.09375f)
                    .glowing(true).additive(false).spin(random.nextDouble()*PI))
                .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
            sparkVelocity += d.scale((0.18+r*0.05)*(0.6+random.nextDouble()*1.3)).add(0.0, 0.06, 0.0)
        }
        emit(sparks, sparkVelocity, 0.017)
        // 白热冲击前锋自身碎裂向外飞，逐片熄灭；没有整壳倒缩、也没有蓝色装饰圈。
        val flash = ArrayList<ParticleSpawnSpec>()
        val flashVelocity = ArrayList<Vec3>()
        repeat(144) {
            val d = direction()
            flash += ParticleSpawnSpec().position(center.add(d.scale(r*0.08)))
                .color(Color.ofInt(255, 251, 215)).visual(ParticleVisual().style(ParticleStyle.SQUARE)
                    .anisoWorld(0.25f, 0.1875f).glowing(true).additive(false))
                .lifetime(5).sizeCurve(CurveKey.at(0,0),CurveKey.at(0.5,1,EasingType.EASE_OUT),
                    CurveKey.at(1.5,1),CurveKey.at(5,0,EasingType.EASE_IN))
            flashVelocity += d.scale(0.22+r*0.06)
        }
        emit(flash, flashVelocity)
        // 小爆炸是短促喷流，大爆炸是多瓣厚重火舌；每瓣方向、密度、寿命、速度都独立。
        repeat(if (r >= 5) 8 + random.nextInt(3) else 5 + random.nextInt(3)) {
            val axis = direction()
            val specs = ArrayList<ParticleSpawnSpec>()
            val velocities = ArrayList<Vec3>()
            val power = 0.16+r*0.035+random.nextDouble()*0.12
            repeat(72) {
                val local = direction()
                val life = 8 + random.nextInt(11)
                specs += ParticleSpawnSpec().position(center.add(axis.scale(r*0.10)).add(local.scale(r*0.08)))
                    .color(flame[random.nextInt(flame.size)])
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.3125f, 0.25f)
                        .glowing(true).additive(false).spin(random.nextDouble()*PI))
                    .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
                velocities += axis.scale(power).add(local.scale(0.05+random.nextDouble()*0.13)).add(0.0,0.03,0.0)
            }
            emit(specs, velocities, 0.006)
        }
        ScreenShake.shake(manager.level, center, radius = r*2+3,
            amplitude = (r*0.06).coerceAtMost(0.55).toFloat(), ticks = 8)
        for (beat in listOf(2,5,9)) Deferred.after(manager.level,beat) {
            val smoke = ArrayList<ParticleSpawnSpec>()
            val velocities = ArrayList<Vec3>()
            repeat(192) {
                val d = direction()
                val life = 12+random.nextInt(12)
                smoke += ParticleSpawnSpec().position(center.add(d.scale(r*(0.16+random.nextDouble()*0.22))))
                    .color(if (random.nextBoolean()) Color.ofInt(147,134,116) else Color.ofInt(66,60,57))
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(0.3125f,0.3125f).additive(false).glowing(false))
                    .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
                velocities += d.scale(0.07+random.nextDouble()*0.06).add(0.0,0.055,0.0)
            }
            emit(smoke,velocities)
        }
    }
}
