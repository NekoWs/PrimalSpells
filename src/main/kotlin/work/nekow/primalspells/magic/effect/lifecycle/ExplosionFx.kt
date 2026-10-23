package work.nekow.primalspells.magic.effect.lifecycle

import kotlin.math.*
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.particledrawing.core.easing.EasingType
import work.nekow.primalspells.magic.effect.visual.PixelVfx
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.util.ScreenShake
import work.nekow.primalspells.util.spellParticleSpec
import work.nekow.primalspells.util.spellVisual

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
        repeat((radius * 96).toInt().coerceIn(160, 768)) {
            val d = direction()
            val life = 12 + random.nextInt(17)
            val fast = random.nextDouble() < 0.18
            sparks += spellParticleSpec().position(center.add(d.scale(0.2)))
                .color(flame[random.nextInt(flame.size)])
                .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(if (fast) 0.375f else 0.125f, 0.09375f)
                    .additive(false).also { if (fast) it.alignTo(Vec3.ZERO,d) })
                .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
            sparkVelocity += d.scale((0.18+r*0.05)*(0.6+random.nextDouble()*1.3)).add(0.0, 0.06, 0.0)
        }
        emit(sparks, sparkVelocity, 0.017)
        // 白热冲击前锋自身碎裂向外飞，逐片熄灭；没有整壳倒缩、也没有蓝色装饰圈。
        val flash = ArrayList<ParticleSpawnSpec>()
        val flashVelocity = ArrayList<Vec3>()
        repeat(320) {
            val d = direction()
            flash += spellParticleSpec().position(center.add(d.scale(r*0.08)))
                .color(Color.ofInt(255, 251, 215)).visual(spellVisual().style(ParticleStyle.SQUARE)
                    .anisoWorld(0.25f, 0.1875f).additive(false))
                .lifetime(5).sizeCurve(CurveKey.at(0,0),CurveKey.at(0.5,1,EasingType.EASE_OUT),
                    CurveKey.at(1.5,1),CurveKey.at(5,0,EasingType.EASE_IN))
            flashVelocity += d.scale(0.22+r*0.06)
        }
        emit(flash, flashVelocity)
        // 体积火舌用共享轴向的像素切片构成：亮心、厚边、分叉端点，不是均匀球形撒点。
        repeat(if (r >= 5) 7 + random.nextInt(4) else 4 + random.nextInt(3)) { lobe ->
            val axis = direction()
            val side = axis.cross(if (abs(axis.y) < 0.9) Vec3(0.0,1.0,0.0) else Vec3(1.0,0.0,0.0)).normalize()
            val up = side.cross(axis).normalize()
            val power = 0.18+r*0.035+random.nextDouble()*0.10
            val width = 0.12+r*0.025
            val phase = lobe % 3
            fun plume() {
                val specs = ArrayList<ParticleSpawnSpec>()
                val velocities = ArrayList<Vec3>()
                repeat(5) { slice ->
                    for (x in -2..2) for (y in -2..2) {
                        if (x*x+y*y > 6 || random.nextDouble() < 0.12) continue
                        val off = side.scale((x+random.nextDouble()*0.35)*width)
                            .add(up.scale((y+random.nextDouble()*0.35)*width))
                        val edge = x*x+y*y >= 4
                        val life = (if (r >= 5) 18 else 12) + slice * 2 + random.nextInt(12)
                        val burnOut = if (r >= 5) 12 else 8
                        val size = if (edge) 0.3125f else if (r >= 5) 0.5625f else 0.4375f
                        specs += spellParticleSpec().position(center.add(axis.scale((slice+phase)*0.10)).add(off.scale(0.35)))
                            .color(flame[if (edge) 2 else if(slice>=3) 0 else 1])
                            .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(size,size).additive(false))
                            .lifetime(life).sizeCurve(CurveKey.at(0,0),CurveKey.at(1,0.8,EasingType.EASE_OUT),
                                CurveKey.at(3,1.15,EasingType.EASE_OUT),CurveKey.at(life-burnOut,1),CurveKey.at(life,0,EasingType.EASE_OUT))
                        velocities += axis.scale(power*(0.65+slice*0.11)).add(off.scale(0.10+slice*0.012)).add(0.0,0.025,0.0)
                    }
                }
                emit(specs,velocities,0.004)
            }
            if(phase==0) plume() else Deferred.after(manager.level,phase) { plume() }
        }
        // 长条压力碎片冲出火舌，之后只留下零星余烬；不用封闭光环遮住内部结构。
        val shards = ArrayList<ParticleSpawnSpec>()
        val shardVelocity = ArrayList<Vec3>()
        repeat((r*12).toInt().coerceIn(20,96)) {
            val d=direction();val life=7+random.nextInt(8)
            shards += spellParticleSpec().position(center.add(d.scale(r*0.12))).color(flame[0])
                .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(0.5f,0.125f).additive(false)
                    .alignTo(Vec3.ZERO,d))
                .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
            shardVelocity += d.scale(0.5+r*0.08+random.nextDouble()*0.25)
        }
        emit(shards,shardVelocity,0.012)
        ScreenShake.shake(manager.level, center, radius = r*2+3,
            amplitude = (r*0.06).coerceAtMost(0.55).toFloat(), ticks = 8)
        for (beat in listOf(2,5,9)) Deferred.after(manager.level,beat) {
            val smoke = ArrayList<ParticleSpawnSpec>()
            val velocities = ArrayList<Vec3>()
            repeat(128) {
                val d = direction()
                val life = 12+random.nextInt(12)
                smoke += spellParticleSpec().position(center.add(d.scale(r*(0.16+random.nextDouble()*0.22))))
                    .color(if (random.nextBoolean()) Color.ofInt(147,134,116) else Color.ofInt(66,60,57))
                    .visual(spellVisual().style(ParticleStyle.SQUARE).anisoWorld(0.5f,0.5f).additive(false))
                    .lifetime(life).sizeCurve(*PixelVfx.lifeKeys(life))
                velocities += d.scale(0.07+random.nextDouble()*0.06).add(0.0,0.055,0.0)
            }
            emit(smoke,velocities)
        }
    }
}
