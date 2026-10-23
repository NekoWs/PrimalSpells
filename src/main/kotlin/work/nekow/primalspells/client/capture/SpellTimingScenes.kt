@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.visual.DelayedCastFx
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.util.toPosition

/** 链锯整次清零、链内修正无牌序差异，真实移动载体的视觉爆开与荷载出生严格同刻。 */
object SpellTimingScenes {
    fun scene(): Scene {
        val name = "spell_timing"
        val checks = ArrayList<Check>()
        val carriers = ArrayList<Projectile>()
        var releases = 0
        var synchronized = true
        var gun: Wand? = null
        var consecutive = true
        var shots = 0
        var sawHit = false
        var longSaw: Projectile? = null
        var sawTarget: TestDummyEntity? = null
        return Scene(name, "链锯连射 / 延时锁印与荷载同步", 24, 150,
            prepare = { ctx ->
                val floor = ctx.floorBlockY(0, 0)
                for (x in -12..12) for (z in -12..18) for (y in floor+1..floor+8)
                    ctx.level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
                ctx.buildStudio(0, 0, floor)
                ctx.level.getEntitiesOfClass(TestDummyEntity::class.java, AABB.ofSize(Vec3.ZERO,100.0,200.0,100.0)).forEach { it.discard() }
                ctx.setup(true,0.5,floor+5.0,-12.0,0f,12f)
                carriers.clear(); checks.clear(); releases=0; synchronized=true; shots=0; consecutive=true
            }, camera = { ctx,_ -> Pose(0.5,ctx.floorBlockY(0,0)+5.0,-12.0,0f,12f) },
            tick = { ctx,t ->
                val floor = ctx.floorBlockY(0, 0)
                fun wand(ids: List<String>, delay: Double=9.0, recharge: Double=3.0, mana: Double=1000.0): Wand {
                    val w=Wand("timing-$t-${ids.joinToString()}")
                    w.delayTickCount=delay;w.rechargeTickCount=recharge
                    w.innate+=ids.map { MagicManager.create(it)!! };w.load();w.status.mana=mana
                    return w
                }
                if (t == 8) {
                    for ((index, mods) in listOf(emptyList<String>(),listOf("speed_boost")).withIndex()) {
                        val p=MagicManager.create("delayed_cast") as Projectile
                        SpellAssembly.apply(p,mods.map { MagicManager.create(it) as Modifier })
                        p.caster=ctx.player;p.wand=Wand("delay-visual-$index")
                        p.position=Vector3d((if(index==0)-3f else 3f).toDouble(), (floor+3f).toDouble(), (-2f).toDouble());p.velocity=Vector3f(0f,0f,1f)
                        val payload=MagicManager.create("laser") as Projectile
                        payload.effects+=object:BaseEffect() {
                            override fun onActive() {
                                releases++
                                val fx=p.effects.filterIsInstance<DelayedCastFx>().single()
                                synchronized=synchronized && p.status.age==p.maxAge && fx.releasedAtAge==p.maxAge && status.pos.distance(p.status.pos)<1e-5
                            }
                        }
                        (p as TriggerSpell).payload+=payload;p.spell();MagicManager.add(p);carriers+=p
                    }
                }
                if (t == 45) {
                    val last=wand(listOf("double_cast","fireball","chainsaw"))
                    last.spell(ctx.player)
                    checks+=Check(name,t,"chainsaw-clears-wand-and-shot-delay",last.status.delay==0.0 && last.status.recharge==0.0,"delay=${last.status.delay} recharge=${last.status.recharge}")
                    val first=wand(listOf("double_cast","chainsaw","fireball"))
                    first.spell(ctx.player)
                    checks+=Check(name,t,"chainsaw-reset-independent-of-card-order",first.status.delay==0.0,"delay=${first.status.delay}")
                    val poor=wand(listOf("chainsaw"),mana=0.0)
                    poor.spell(ctx.player)
                    checks+=Check(name,t,"unpaid-chainsaw-does-not-reset",poor.status.delay==9.0,"delay=${poor.status.delay}")
                    val nested=wand(listOf("trigger_fireball","chainsaw"))
                    nested.spell(ctx.player)
                    checks+=Check(name,t,"paid-payload-chainsaw-clears-wand-delay",nested.status.delay==0.0,"delay=${nested.status.delay}")
                    gun=wand(listOf("double_cast","fireball","chainsaw"))
                }
                if (t in 55..74) { consecutive=consecutive && gun!!.spell(ctx.player);shots++;gun!!.tick() }
                if (t == 100) {
                    sawTarget=ctx.spawnDummy(0.0,floor+1.0,0.0)
                    val saw=MagicManager.create("chainsaw") as Projectile
                    SpellAssembly.apply(saw,listOf(MagicManager.create("damage_boost") as Modifier))
                    saw.effects+=object:BaseEffect() {
                        override fun onHitEntity(target:net.minecraft.world.entity.Entity) { sawHit=target===sawTarget && status.damage==5.0 }
                    }
                    saw.caster=ctx.player;saw.wand=Wand("saw-hit")
                    saw.position=Vector3d((0f).toDouble(), ((sawTarget!!.boundingBox.center.y)).toDouble(), (-1.2f).toDouble());saw.velocity=Vector3f(0f,0f,1f)
                    saw.spell();MagicManager.add(saw)
                }
                if(t==76) MagicManager.projectiles.forEach { it.alive=false }
                if(t==110) MagicManager.projectiles.forEach { it.alive=false }
                if(t==115) {
                    val saw=MagicManager.create("chainsaw") as Projectile
                    SpellAssembly.apply(saw,listOf("extended_lifetime","speed_boost").map { MagicManager.create(it) as Modifier })
                    saw.caster=ctx.player;saw.wand=Wand("saw-long-visual")
                    saw.position=Vector3d((-2f).toDouble(), (floor+3f).toDouble(), (-2f).toDouble());saw.velocity=Vector3f(0f,0f,.1f)
                    saw.spell();saw.maxAge=8;saw.collisionEnabled=false
                    longSaw=saw;MagicManager.add(saw)
                }
            }, checksAt={ctx,t -> when(t) {
                22 -> listOf(Check(name,t,"no-premature-burst-or-payload",releases==0 && carriers.all { it.effects.filterIsInstance<DelayedCastFx>().single().releasedAtAge==-1 },"released=$releases"))
                32 -> listOf(Check(name,t,"burst-and-payload-same-expiry-position",releases==2 && synchronized,"released=$releases synchronized=$synchronized"),
                    Check(name,t,"moving-delay-actually-moves",carriers[1].status.pos.z>5f,"pos=${carriers[1].status.pos}"))
                47 -> checks.toList()
                76 -> listOf(Check(name,t,"chainsaw-machinegun-every-tick",consecutive && shots==20 && gun!!.status.delay==0.0 && gun!!.status.recharge==0.0,"shots=$shots delay=${gun!!.status.delay} recharge=${gun!!.status.recharge}"))
                105 -> listOf(Check(name,t,"chainsaw-real-hit-with-damage-modifier",sawHit && sawTarget!!.totalDamage==5.0,"hit=$sawHit actualDamage=${sawTarget!!.totalDamage}"))
                127 -> listOf(Check(name,t,"extended-fast-chainsaw-keeps-cutting",longSaw!!.effects.filterIsInstance<work.nekow.primalspells.magic.effect.visual.ChainsawFx>().single().cutsEmitted==4 && longSaw!!.status.pos.z>1f,"cuts=${longSaw!!.effects.filterIsInstance<work.nekow.primalspells.magic.effect.visual.ChainsawFx>().single().cutsEmitted} z=${longSaw!!.status.pos.z}"))
                149 -> listOf(Check(name,t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}"),
                    Check(name,t,"pd-particles-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"))
                else -> emptyList()
            }})
    }
    /** 连续80刻真实机枪：观察密集命中，核对粒子驻留量、切痕变化及六格弧线追踪边界。 */
    fun machinegun(): Scene {
        val name="machinegun_visual"
        lateinit var gun: Wand
        lateinit var source: TestDummyEntity
        lateinit var target: TestDummyEntity
        val checks=linkedMapOf<Int,MutableList<Check>>()
        val rolls=hashSetOf<Int>()
        var rounds=0
        var peak=0
        fun record(t:Int,id:String,ok:Boolean,detail:String) {
            checks.getOrPut(t){arrayListOf()}.add(Check(name,t,id,ok,detail))
        }
        return Scene(name,"高频触发机枪 / 随机弧刃 / 六格弧线追踪",24,170,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12) for(z in -12..12) for(y in floor+1..floor+10)
                    ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(true,3.0,floor+3.0,-6.0,28f,10f)
                source=ctx.spawnDummy(0.0,floor+1.0,-5.0)
                source.yRot=0f;source.xRot=0f
                target=ctx.spawnDummy(0.0,floor+1.0,0.0)
                gun=Wand("continuous-trigger-gun");gun.delayTickCount=10.0;gun.rechargeTickCount=20.0
                gun.magics+=listOf("double_cast","trigger_fireball","mana_injection","omnidirectional_scatter","chainsaw").map{MagicManager.create(it)!!}
                gun.load();gun.status.mana=10000.0
            },camera={ctx,_ -> Pose(3.0,ctx.floorBlockY(0,0)+3.0,-6.0,28f,10f)},
            tick={ctx,t ->
                if(t in 8..87) { if(gun.spell(source))rounds++;gun.tick() }
                for(p in MagicManager.projectiles) {
                    val fx=p.effects.filterIsInstance<work.nekow.primalspells.magic.effect.visual.ChainsawFx>().firstOrNull() ?: continue
                    val axis=fx.lastCutAxis
                    if(axis.lengthSqr()>0.1) rolls+=(kotlin.math.atan2(axis.y,axis.x)*12).toInt()
                }
                peak=maxOf(peak,ParticleManager.of(ctx.level).getEngine().particleCount())
                if(t==100) {
                    record(t,"machinegun-every-tick-and-real-damage",rounds==80 && target.totalDamage>=320.0,"rounds=$rounds damage=${target.totalDamage}")
                    record(t,"cut-orientations-vary",rolls.size>=8,"directions=${rolls.size}")
                    record(t,"sustained-particles-stay-readable",peak<900,"peak=$peak")
                    MagicManager.projectiles.forEach{it.alive=false}
                    source.discard();target.discard()
                    // 隔离范围检查，不借用命中箱范围判定锁定距离。
                    fun probe(distance:Double):Boolean {
                        val dummy=ctx.spawnDummy(-6.0,ctx.floorBlockY(0,0)+2.0,4.0)
                        val p=MagicManager.create("fireball") as Projectile
                        SpellAssembly.apply(p,listOf(MagicManager.create("homing") as Modifier))
                        p.caster=ctx.player;p.wand=Wand("radius-probe");p.position=dummy.boundingBox.center.add(-distance,0.0,0.0).toPosition()
                        p.velocity=Vector3f(0f,0f,1f);p.spell();p.collisionEnabled=false;p.tick()
                        val turned=p.status.velocity.x>0.1f
                        p.alive=false;p.die();dummy.discard();return turned
                    }
                    record(t,"homing-six-block-radius",probe(5.9) && !probe(6.1),"5.9 inside / 6.1 outside")
                }
                if(t==169) {
                    record(t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}")
                    record(t,"pd-particles-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
            },checksAt={_,t -> checks[t] ?: emptyList()})
    }

}
