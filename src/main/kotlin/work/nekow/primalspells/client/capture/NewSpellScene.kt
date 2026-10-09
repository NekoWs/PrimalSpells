@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
package work.nekow.primalspells.client.capture

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.lifecycle.SpellDamage
import work.nekow.primalspells.magic.modifier.Torch
import work.nekow.primalspells.wand.Wand

/** 真实抽牌树、付费、复制、追加条件、正负治疗和逐刻切割回归。 */
object NewSpellScene {
    fun scene(): Scene {
        val name="new_spells"
        val checks=linkedMapOf<Int,MutableList<Check>>()
        var source: work.nekow.primalspells.entity.TestDummyEntity?=null
        var torch: Torch?=null
        var timer: Projectile?=null
        var expires: Projectile?=null
        var timerCount=0
        var expireCount=0
        var timerAge=-1
        var expireAge=-1
        var saw: Projectile?=null
        return Scene(name,"抽牌控制 / 锯片 / 治疗 / 环绕火种",24,180,
            prepare={ctx ->
                checks.clear();timerCount=0;expireCount=0;timerAge=-1;expireAge=-1
                val floor=ctx.floorBlockY(0,0)
                ctx.buildStudio(0,0,floor);ctx.setup(true,0.5,floor+5.0,-10.0,0f,18f)
                source=ctx.spawnDummy(-5.0,floor+1.0,-5.0)
            },camera={ctx,_ -> Pose(0.5,ctx.floorBlockY(0,0)+5.0,-10.0,0f,18f)},
            tick={ctx,t ->
                fun record(id:String,ok:Boolean,detail:String) { checks.getOrPut(t){arrayListOf()}.add(Check(name,t,id,ok,detail)) }
                fun cast(ids:List<String>,mana:Double=10000.0):Pair<Wand,List<Projectile>> {
                    MagicManager.projectiles.forEach { it.alive=false };MagicManager.tick()
                    val w=Wand("new-$t-${ids.joinToString()}")
                    w.magics+=ids.map { MagicManager.create(it)!! };w.load();w.status.mana=mana
                    w.spell(source!!);MagicManager.tick()
                    return w to MagicManager.projectiles.filter { it.alive }.toList()
                }
                if(t==8) {
                    for((id,n) in listOf("triple_cast" to 3,"quadruple_cast" to 4,"quintuple_cast" to 5)) {
                        val (_,shots)=cast(listOf(id)+List(n){"fireball"})
                        record("$id-draws-$n",shots.size==n,"shots=${shots.size}")
                    }
                    val (all,allShots)=cast(listOf("cast_all","fireball","trigger_fireball","speed_boost","chainsaw","healing_bolt"))
                    val native=allShots.filterIsInstance<work.nekow.primalspells.magic.projectile.TriggerFireball>().single()
                    record("cast-all-preserves-payload-scope",allShots.size==3 && native.payload.map{it.id}==listOf("speed_boost","chainsaw") && native.modifiers.isEmpty(),"shots=${allShots.size} payload=${native.payload.map{it.id}}")
                    record("cast-all-pays-once-no-wrap",all.status.mana==9938.0,"mana=${all.status.mana}")
                    MagicManager.projectiles.forEach{it.alive=false};MagicManager.tick()
                    val noWrap=Wand("cast-all-old-discard");noWrap.cast=2
                    noWrap.magics+=listOf("healing_bolt","cast_all","fireball").map{MagicManager.create(it)!!}
                    noWrap.load();noWrap.discardPile+=noWrap.drawPile.removeFirst();noWrap.status.mana=1000.0
                    noWrap.spell(source!!);MagicManager.tick()
                    val allOnly=MagicManager.projectiles.filter{it.alive}
                    record("cast-all-extra-budget-does-not-wrap-old-discard",allOnly.map{it.id}==listOf("fireball"),"shots=${allOnly.map{it.id}}")
                    for((id,n) in listOf("divide_two" to 2,"divide_three" to 3,"divide_five" to 5,"divide_ten" to 10)) {
                        val (_,shots)=cast(listOf(id,"concentrated_light"))
                        record("$id-copies-card",shots.size==n && shots.all{it.awaitingRelease},"shots=${shots.size}")
                    }
                    val (_,boosted)=cast(listOf("divide_ten","speed_boost","fireball"))
                    record("divide-modifier-does-not-copy-chain",boosted.size==1 && boosted.single().modifiers["speed_boost"]==10,"shots=${boosted.size} modifiers=${boosted.firstOrNull()?.modifiers}")
                    val (_,nested)=cast(listOf("divide_two","divide_five","fireball"))
                    record("nested-copy-control",nested.size==10,"shots=${nested.size}")
                    val (poor,unpaid)=cast(listOf("divide_ten","fireball"),mana=100.0)
                    record("unpaid-copy-has-no-shots",unpaid.isEmpty() && poor.status.mana==100.0,"shots=${unpaid.size} mana=${poor.status.mana}")
                    val (_,triggers)=cast(listOf("divide_two","trigger_fireball","speed_boost","chainsaw"))
                    val a=triggers[0] as TriggerSpell;val b=triggers[1] as TriggerSpell
                    record("copied-native-payload-independent",a.payload.size==2 && b.payload.size==2 && a.payload[1]!==b.payload[1],"payloads=${a.payload.size}/${b.payload.size}")
                    val (added,carriers)=cast(listOf("add_trigger","speed_boost","fireball","omnidirectional_scatter","chainsaw"))
                    val carrier=carriers.single();val payload=carrier.addedTriggers.single().cards
                    record("appended-payload-scope",carrier.modifiers.keys==setOf("speed_boost") && payload.map{it.id}==listOf("omnidirectional_scatter","chainsaw"),"parent=${carrier.modifiers} payload=${payload.map{it.id}}")
                    record("append-intermediate-modifier-free",added.status.mana==9984.0,"mana=${added.status.mana}")
                    // 回蓝放在复制卡前后，同一链均能支付复制卡。
                    for(ids in listOf(listOf("mana_injection","divide_two","fireball"),listOf("double_cast","divide_two","fireball","mana_injection"))) {
                        val (funded,shots)=cast(ids,mana=6.0)
                        record("refund-funds-control-${ids.first()}",shots.size==2 && funded.status.mana==0.0,"shots=${shots.size} mana=${funded.status.mana}")
                    }
                    val (waived,waivedShots)=cast(listOf("add_trigger","divide_two","speed_boost","fireball","chainsaw"))
                    record("append-recursively-waives-modifiers",waivedShots.size==1 && waivedShots.single().modifiers["speed_boost"]==2 && waived.status.mana==9956.0,"mana=${waived.status.mana}")
                    val (divided,dividedShots)=cast(listOf("divide_two","add_trigger","speed_boost","fireball","chainsaw"))
                    record("copied-append-preserves-waiver",dividedShots.size==2 && divided.status.mana==9942.0,"mana=${divided.status.mana}")
                    val copy=SpellAssembly.copy(carrier)
                    record("appended-copy-independent",copy.addedTriggers.single().cards[1]!==payload[1],"original/copy child identity")
                    val (_,hitShots)=cast(listOf("add_trigger","fireball","healing_bolt"))
                    val hit=hitShots.single();hit.collisionEnabled=false;hit.status.bounces=1
                    hit.hitBlock(Vector3f(hit.status.pos),Vector3f(0f,0f,-1f));MagicManager.tick()
                    record("appended-hit-releases-on-bounce",hit.alive && hit.addedTriggers.single().cards.isEmpty() && MagicManager.projectiles.count{it.id=="healing_bolt"}==1,"alive=${hit.alive}")
                    val (_,pierced)=cast(listOf("piercing","add_trigger","fireball","healing_bolt"))
                    val through=pierced.single();through.collisionEnabled=false
                    through.hitEntity(source!!)
                    val heldPayload=through.addedTriggers.single().cards.isNotEmpty()
                    through.hitBlock(Vector3f(through.status.pos),Vector3f(0f,0f,-1f));MagicManager.tick()
                    record("appended-piercing-entity-waits-for-block",heldPayload && through.addedTriggers.single().cards.isEmpty() && MagicManager.projectiles.count{it.id=="healing_bolt"}==1,"held=$heldPayload")
                    val (_,early)=cast(listOf("add_timer","fireball","healing_bolt"))
                    val earlyTimer=early.single();earlyTimer.collisionEnabled=false
                    earlyTimer.hitBlock(Vector3f(earlyTimer.status.pos),Vector3f(0f,0f,-1f));MagicManager.tick()
                    record("timer-earlier-collision-releases",earlyTimer.status.age<20 && earlyTimer.addedTriggers.single().cards.isEmpty() && MagicManager.projectiles.count{it.id=="healing_bolt"}==1,"age=${earlyTimer.status.age}")
                    val (_,death)=cast(listOf("add_expiration","fireball","healing_bolt"))
                    val deathTrigger=death.single();deathTrigger.collisionEnabled=false;deathTrigger.status.bounces=1
                    deathTrigger.hitBlock(Vector3f(deathTrigger.status.pos),Vector3f(0f,0f,-1f))
                    val waits=deathTrigger.alive && deathTrigger.addedTriggers.single().cards.isNotEmpty()
                    deathTrigger.hitBlock(Vector3f(deathTrigger.status.pos),Vector3f(0f,0f,1f));MagicManager.tick()
                    record("expiration-waits-through-bounce-until-death",waits && deathTrigger.addedTriggers.single().cards.isEmpty() && MagicManager.projectiles.count{it.id=="healing_bolt"}==1,"waits=$waits")
                    val (_,reduced)=cast(listOf("reduce_recharge","fireball"))
                    record("reduce-recharge-registered",reduced.single().modifiers["reduce_recharge"]==1,"recipe=${reduced.single().modifiers}")
                    val (_,heals)=cast(listOf("healing_bolt"))
                    val heal=heals.single()
                    val stand=net.minecraft.world.entity.EntityTypes.PIG.create(ctx.level,net.minecraft.world.entity.EntitySpawnReason.COMMAND)!!
                    stand.setPos(4.0,ctx.floorBlockY(0,0)+1.0,0.0)
                    ctx.level.addFreshEntity(stand);stand.health=2f
                    val before=stand.health
                    SpellDamage.hit(ctx.level,stand,heal,5.0)
                    record("healing-plus-five-restores-five",stand.health==before+5f,"before=$before after=${stand.health}")
                    val mid=stand.health;SpellDamage.hit(ctx.level,stand,heal,-5.0)
                    record("healing-negative-removes-five",stand.health==mid-5f,"before=$mid after=${stand.health}")
                    stand.discard();MagicManager.projectiles.forEach{it.alive=false}
                }
                if(t==35) {
                    val (_,shots)=cast(listOf("double_cast","add_timer","fireball","healing_bolt","add_expiration","fireball","healing_bolt"))
                    timer=shots[0];expires=shots[1]
                    shots.forEachIndexed { index,p ->
                        p.collisionEnabled=false;p.maxAge=if(index==1)6 else 60
                        p.addedTriggers.single().cards.filterIsInstance<Projectile>().single().effects+=object:BaseEffect(){
                            override fun onActive(){if(index==1){expireCount++;expireAge=p.status.age}else{timerCount++;timerAge=p.status.age+1}}
                        }
                    }
                }
                if(t==65) {
                    record("timer-fires-once-at-20",timerCount==1 && timerAge==20,"count=$timerCount age=$timerAge")
                    record("expiration-fires-once-at-end",expireCount==1 && expireAge==6,"count=$expireCount age=$expireAge")
                    MagicManager.projectiles.forEach{it.alive=false}
                    val p=MagicManager.create("sawblade") as Projectile
                    p.caster=source!!;p.wand=Wand("saw-frame");p.wand.status.particleManager=ParticleManager.of(ctx.level)
                    p.position=Vector3f(-2f,ctx.floorBlockY(0,0)+3f,0f);p.velocity=Vector3f(0f,0f,0.05f)
                    p.spell();MagicManager.add(p);saw=p
                    val target=ctx.spawnDummy(6.0,ctx.floorBlockY(0,0)+1.0,0.0)
                    val before=target.totalDamage
                    repeat(5){SpellDamage.hit(ctx.level,target,p,p.status.damage)}
                    val first=target.totalDamage-before;p.tick();SpellDamage.hit(ctx.level,target,p,p.status.damage)
                    record("saw-cut-once-per-tick",first==12.0 && target.totalDamage-before==24.0,"first=$first total=${target.totalDamage-before}")
                    target.discard()
                    val b=p.status.bounces;p.hitBlock(Vector3f(p.status.pos),Vector3f(0f,0f,-1f))
                    record("saw-bounces-and-self-harms",p.alive && p.status.bounces==b-1 && p.selfDamage,"bounces=${p.status.bounces} self=${p.selfDamage}")
                    val w=Wand("torch-idle");torch=MagicManager.create("torch") as Torch;w.magics+=torch!!;w.load();w.tick(source!!,ctx.level)
                }
                if(t==100){torch?.onUnloaded();saw?.alive=false;MagicManager.projectiles.forEach{it.alive=false};source?.discard()}
                if(t==179){
                    record("projectiles-cleaned",MagicManager.projectiles.isEmpty(),"shots=${MagicManager.projectiles.size}")
                    record("pd-particles-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                    source?.discard()
                }
            },checksAt={_,t -> checks[t]?:emptyList()})
    }
}
