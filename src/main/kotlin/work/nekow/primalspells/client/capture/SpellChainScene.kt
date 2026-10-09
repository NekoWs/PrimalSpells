@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.*
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.lifecycle.PlasmaBeam
import work.nekow.primalspells.magic.projectile.*
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.*
import kotlin.math.abs

/** 真实世界回归束壳截点、穿刺触发、强追踪、付费机枪配方与十秒统计重置。 */
object SpellChainScene {
    /** CaptureScenes调用；所有战斗结算与统计断言均在服务端场景tick执行。 */
    fun scene(): Scene {
        val name = "spell_chain"
        val checks = linkedMapOf<Int, MutableList<Check>>()
        fun record(t: Int, id: String, ok: Boolean, info: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name,t,id,ok,info))
        }
        var holder: TestDummyEntity? = null
        var behind: TestDummyEntity? = null
        var stats: TestDummyEntity? = null
        var visibleBeam: Projectile? = null
        lateinit var shield: Wand
        fun equip(entity: LivingEntity): Wand {
            val stack = ItemStack(ModItems.WAND.get())
            val w = WandManager[WandManager.createWand(stack)]
            repeat(8) { w.magics += MagicManager.create("energy_shield") }
            w.load(); w.mana=100000.0; w.status.mana=100000.0; w.charge=0.0
            entity.setItemInHand(InteractionHand.MAIN_HAND,stack)
            w.tick(entity,entity.level() as net.minecraft.server.level.ServerLevel)
            return w
        }
        return Scene(name,"束源与护盾截束 / 强追踪 / 穿刺荷载 / 机枪与假人统计",24,400,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12) for(z in -12..12) for(y in floor+1..floor+10)
                    ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor)
                ctx.setup(true,7.0,floor+5.0,-10.0,33f,12f)
            },camera={ctx,_ -> Pose(7.0,ctx.floorBlockY(0,0)+5.0,-10.0,33f,12f)},
            tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                fun shot(id:String, pos:Vec3, mods:List<String> = emptyList(), caster:LivingEntity = ctx.player):Projectile {
                    val p=MagicManager.create(id) as Projectile
                    SpellAssembly.apply(p,mods.map { MagicManager.create(it) as Modifier })
                    p.caster=caster;p.wand=Wand("chain-$id-$t");p.position=pos.toVector3f();p.velocity=Vector3f(0f,0f,1f)
                    p.spell();return p
                }
                if(t==8) {
                    val target=ctx.spawnDummy(-6.0,floor+2.0,0.0)
                    val parent=MagicManager.create("trigger_fireball") as TriggerFireball
                    SpellAssembly.apply(parent,listOf(MagicManager.create("piercing") as Modifier))
                    parent.payload += MagicManager.create("fireball")!!
                    parent.caster=ctx.player;parent.wand=Wand("piercing-trigger");parent.position=target.boundingBox.center.toVector3f();parent.velocity=Vector3f(0f,0f,1f);parent.spell()
                    parent.hitEntity(target)
                    record(t,"piercing-entity-damages-and-keeps-payload",target.totalDamage==4.0 && parent.payload.size==1 && parent.alive,"damage=${target.totalDamage} payload=${parent.payload.size} alive=${parent.alive}")
                    val before=MagicManager.projectiles.size
                    parent.hitBlock(parent.status.pos,Vector3f(0f,0f,-1f))
                    MagicManager.tick()
                    record(t,"piercing-wall-releases-once",parent.payload.isEmpty() && MagicManager.projectiles.size==before+1,"payload=${parent.payload.size} shots=${MagicManager.projectiles.size}")
                    parent.hitBlock(parent.status.pos,Vector3f(0f,0f,-1f))
                    record(t,"wall-payload-does-not-repeat",MagicManager.projectiles.size==before+1,"shots=${MagicManager.projectiles.size}")
                    parent.alive=false;parent.die();target.discard()
                    MagicManager.projectiles.forEach { it.alive=false }
                    val source=ctx.spawnDummy(-8.0,floor+2.0,-5.0)
                    val w=Wand("machinegun");w.delayTickCount=10.0;w.rechargeTickCount=20.0;w.charge=100.0
                    w.innate += listOf("double_cast","trigger_fireball","omnidirectional_scatter","chainsaw").map { MagicManager.create(it)!! }
                    w.load();w.status.mana=1000.0
                    val ok=w.spell(source)
                    MagicManager.tick()
                    record(t,"paid-machinegun-recipe-ready-next-tick",ok && w.status.delay==0.0 && w.status.recharge==0.0 && MagicManager.projectiles.any { it.id=="trigger_fireball" } && MagicManager.projectiles.any { it.id=="chainsaw" },"cast=$ok delay=${w.status.delay} recharge=${w.status.recharge}")
                    val reduced=Wand("scatter-delay");reduced.delayTickCount=10.0;reduced.rechargeTickCount=20.0
                    reduced.innate += listOf("omnidirectional_scatter","fireball").map { MagicManager.create(it)!! }
                    reduced.load();reduced.status.mana=1000.0
                    reduced.spell(source)
                    record(t,"scatter-reduces-wand-base-cooldowns",reduced.status.delay==0.0 && reduced.status.recharge==0.0,"delay=${reduced.status.delay} recharge=${reduced.status.recharge}")
                    MagicManager.tick()
                    MagicManager.projectiles.forEach { it.alive=false };source.discard()
                    val signs=mutableSetOf<Int>()
                    repeat(32) {
                        val p=shot("fireball",Vec3(-8.0,floor+5.0,0.0),listOf("omnidirectional_scatter"))
                        val v=p.status.velocity
                        signs += (if(v.x>0)1 else 0)+(if(v.y>0)2 else 0)+(if(v.z>0)4 else 0)
                        record(t,"scatter-preserves-speed-$it",abs(v.length()-p.speed*Projectile.LAUNCH_SPEED_SCALE)<1e-5,"speed=${v.length()}")
                        p.alive=false;p.die()
                    }
                    record(t,"scatter-covers-all-octants",signs.size>=6,"octants=$signs")
                }
                if(t==30) {
                    holder=ctx.spawnDummy(0.0,floor+1.0,0.0);shield=equip(holder!!)
                    behind=ctx.spawnDummy(0.0,floor+1.0,2.0)
                    val center=holder!!.position().add(0.0,holder!!.bbHeight*0.55,0.0)
                    for((i,offset) in listOf(Vec3.ZERO,Vec3(1.2,0.0,0.0),Vec3(0.9,0.9,0.0),Vec3(-1.2,0.0,0.0)).withIndex()) {
                        val start=center.add(offset).add(0.0,0.0,-3.0)
                        val p=shot("plasma_beam",start)
                        val before=shield.status.mana
                        p.tick()
                        val end=p.effects.filterIsInstance<PlasmaBeam>().single().beamEnd
                        record(t,"beam-clips-real-shell-$i",abs(end.distanceTo(center)-1.7)<1e-4 && end.z<0 && p.status.pos.distance(start.toVector3f())<1e-5 && p.status.velocity.lengthSquared()==0f,"end=$end source=${p.status.pos}")
                        record(t,"beam-protects-holder-and-behind-$i",holder!!.totalDamage==0.0 && behind!!.totalDamage==0.0 && shield.status.mana<before,"holder=${holder!!.totalDamage} behind=${behind!!.totalDamage} mana=${shield.status.mana}")
                        if(i==0){visibleBeam=p;MagicManager.add(p)} else {p.alive=false;p.die()}
                    }
                }
                if(t==65) {visibleBeam?.alive=false;holder?.discard();behind?.discard()}
                if(t==90) {
                    val target=ctx.spawnDummy(-5.0,floor+2.0,3.0)
                    val start=target.boundingBox.center.add(0.0,0.0,-3.0)
                    val p=shot("fireball",start,listOf("homing"));p.status.velocity.set(p.status.velocity.length(),0f,0f)
                    val speed=p.status.velocity.length();p.tick()
                    record(t,"short-homing-locks-center-immediately",abs(p.status.velocity.x)<1e-5 && p.status.velocity.z>0 && p.status.velocity.length()<=speed,"velocity=${p.status.velocity}")
                    repeat(4){if(p.alive)p.tick()}
                    record(t,"short-homing-actually-hits",target.totalDamage>=5.0,"damage=${target.totalDamage}")
                    p.alive=false;p.die();target.discard()
                    val source=ctx.spawnDummy(4.0,floor+2.0,0.0)
                    val p2=shot("plasma_beam",source.boundingBox.center, caster=source)
                    p2.tick();record(t,"plasma-can-hurt-caster",p2.selfDamage && source.totalDamage==1.5,"damage=${source.totalDamage}")
                    p2.alive=false;p2.die();source.discard()
                }
                if(t==120) stats=ctx.spawnDummy(0.0,floor+1.0,0.0)
                if(t in 120..139) repeat(3){stats!!.hurtServer(ctx.level,ctx.level.damageSources().generic(),1f)}
                if(t==145) {
                    val numbers=ctx.level.getEntitiesOfClass(DamageNumber::class.java,AABB.ofSize(stats!!.position(),8.0,8.0,8.0))
                    record(t,"dummy-dps-and-total-count-every-hit",stats!!.totalDamage==60.0,"dps=${stats!!.dps} total=${stats!!.totalDamage}")
                    record(t,"rapid-popups-merge-and-retain-hit-count",numbers.isNotEmpty() && numbers.size<=6 && numbers.any{it.hitCount>=6},"numbers=${numbers.map{it.amount to it.hitCount}}")
                    record(t,"popups-use-separate-lanes",numbers.map{it.column to it.y}.distinct().size==numbers.size,"billboardColumns=${numbers.map{it.column to it.y}}")
                }
                if(t==340) record(t,"dummy-ten-second-idle-reset",stats!!.totalDamage==0.0 && stats!!.dps==0.0,"total=${stats!!.totalDamage} dps=${stats!!.dps}")
                if(t==345) {
                    stats!!.hurtServer(ctx.level,ctx.level.damageSources().generic(),2f)
                    record(t,"dummy-new-session-starts-at-zero",stats!!.totalDamage==2.0,"total=${stats!!.totalDamage}")
                }
                if(t==365) stats?.discard()
                if(t==399) {
                    record(t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}")
                    record(t,"pd-particles-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                    record(t,"dummy-stat-displays-cleaned",ctx.level.getEntitiesOfClass(DummyStatsDisplay::class.java,AABB.ofSize(Vec3(0.0,floor.toDouble(),0.0),60.0,60.0,60.0)).isEmpty(),"no orphan boards")
                }
            },checksAt={_,t -> checks[t] ?: emptyList()})
    }
}
