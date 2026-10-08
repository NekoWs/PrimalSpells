@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.DamageNumber
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.lifecycle.CrystalReaction
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand
import kotlin.math.cos
import kotlin.math.sin

/** 四个外视角确认晶面，再验证预警频率加快和自然到期的真实爆炸伤害。 */
object CrystalLifecycleScene {
    fun scene(): Scene {
        val name="crystal_lifecycle"
        var crystal: Projectile?=null
        var reaction: CrystalReaction?=null
        var slowPeriod=0
        var mediumPeriod=0
        var fastPeriod=0
        var target: TestDummyEntity?=null
        return Scene(name,"晶面四向 / 白色加速闪烁 / 到期爆炸",24,180,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -8..8) for(z in -8..8) for(y in floor+1..floor+8)
                    ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor)
                ctx.level.getEntitiesOfClass(TestDummyEntity::class.java,AABB.ofSize(Vec3.ZERO,100.0,200.0,100.0)).forEach { it.discard() }
                ctx.level.getEntitiesOfClass(DamageNumber::class.java,AABB.ofSize(Vec3.ZERO,100.0,200.0,100.0)).forEach { it.discard() }
                ctx.setup(true,0.0,floor+2.7,-4.0,0f,12f)
            },camera={ctx,t ->
                val degrees=if(t<64) (t.coerceAtLeast(0)/16)*90.0 else 45.0
                val angle=Math.toRadians(degrees)
                Pose(sin(angle)*4.0,ctx.floorBlockY(0,0)+2.7,-cos(angle)*4.0,degrees.toFloat(),12f)
            },tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                if(t==8) {
                    val p=MagicManager.create("dormant_crystal") as Projectile
                    p.maxAge=100 // 只压缩摄影时间；到期/预警仍由正式生命周期执行。
                    p.caster=ctx.player;p.wand=Wand("crystal-lifetime")
                    p.position=Vector3f(0f,floor+1f,0f);p.velocity=Vector3f(0f,0f,1f)
                    p.spell();p.motionEnabled=false;p.collisionEnabled=false
                    reaction=p.effects.filterIsInstance<CrystalReaction>().single();crystal=p;MagicManager.add(p)
                }
                if(t==70) slowPeriod=reaction!!.warningHalfPeriod
                if(t==90) mediumPeriod=reaction!!.warningHalfPeriod
                if(t==104) fastPeriod=reaction!!.warningHalfPeriod
                if(t==96) target=ctx.spawnDummy(1.0,floor+1.0,0.0)
            },checksAt={ctx,t -> when(t) {
                2 -> listOf(Check(name,t,"sphere-orbit-unregistered",MagicManager.create("sphere_orbit")==null,"removed"))
                64 -> listOf(Check(name,t,"crystal-alive-after-four-outside-views",crystal!!.alive && !reaction!!.hasDetonated,"age=${crystal!!.status.age}"))
                105 -> listOf(Check(name,t,"white-flash-accelerates",slowPeriod>mediumPeriod && mediumPeriod>fastPeriod && fastPeriod==1,"halfPeriods=$slowPeriod/$mediumPeriod/$fastPeriod"))
                115 -> listOf(Check(name,t,"natural-expiry-detonates-once",reaction!!.hasDetonated && !crystal!!.alive && crystal!!.status.age==100,"detonated=${reaction!!.hasDetonated} age=${crystal!!.status.age}"),
                    Check(name,t,"expiry-explosion-has-real-damage",ctx.level.getEntitiesOfClass(DamageNumber::class.java,AABB.ofSize(target!!.position(),6.0,6.0,6.0)).any { it.amount>0.0 },"positive damage at neighboring target"))
                179 -> listOf(Check(name,t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}"),
                    Check(name,t,"pd-particles-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                else -> emptyList()
            }})
    }
}
