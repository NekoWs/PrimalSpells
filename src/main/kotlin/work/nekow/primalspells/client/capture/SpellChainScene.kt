@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
package work.nekow.primalspells.client.capture

import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.*
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.PlasmaBeam
import work.nekow.primalspells.magic.projectile.*
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.*

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
            repeat(8) { w.magics += SpellRegistry.create("energy_shield") }
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
                    val p=SpellRegistry.create(id) as Projectile
                    SpellAssembly.apply(p,mods.map { SpellRegistry.create(it) as Modifier })
                    p.caster=caster;p.wand=Wand("chain-$id-$t");p.position=pos.toPosition();p.velocity=Vector3f(0f,0f,1f)
                    p.spell();return p
                }
                if(t==8) {
                    checks.getOrPut(t) { arrayListOf() }.addAll(TriggerLifecycleChecks.run(ctx, t, floor))
                    val target=ctx.spawnDummy(-6.0,floor+2.0,0.0)
                    val parent=SpellRegistry.create("trigger_fireball") as TriggerFireball
                    SpellAssembly.apply(parent,listOf(SpellRegistry.create("piercing") as Modifier))
                    parent.payload += SpellRegistry.create("fireball")!!
                    parent.caster=ctx.player;parent.wand=Wand("piercing-trigger");parent.position=target.boundingBox.center.toPosition();parent.velocity=Vector3f(0f,0f,1f);parent.spell()
                    parent.hitEntity(target)
                    parent.hitEntity(target)
                    ProjectileRuntime.tick()
                    record(t,"piercing-entity-releases-each-hit-with-zero-direct-damage",target.totalDamage==0.0 && parent.payload.size==1 && parent.alive && ProjectileRuntime.projectiles.count{it.id=="fireball"}==2,"damage=${target.totalDamage} payload=${parent.payload.size} alive=${parent.alive}")
                    ProjectileRuntime.projectiles.forEach{it.alive=false}
                    parent.hitBlock(parent.status.pos,Vector3f(0f,0f,-1f))
                    parent.die()
                    ProjectileRuntime.tick()
                    record(t,"piercing-wall-does-not-release",parent.payload.isEmpty() && ProjectileRuntime.projectiles.isEmpty(),"payload=${parent.payload.size} shots=${ProjectileRuntime.projectiles.size}")
                    parent.hitBlock(parent.status.pos,Vector3f(0f,0f,-1f))
                    record(t,"wall-payload-does-not-repeat",ProjectileRuntime.projectiles.isEmpty(),"shots=${ProjectileRuntime.projectiles.size}")
                    parent.alive=false;parent.die();target.discard()
                    ProjectileRuntime.projectiles.forEach { it.alive=false }
                    val source=ctx.spawnDummy(-8.0,floor+2.0,-5.0)
                    val triggerVictim=ctx.spawnDummy(-6.0,floor+2.0,0.0)
                    val w=Wand("machinegun");w.delayTickCount=10.0;w.rechargeTickCount=20.0;w.charge=100.0
                    w.innate += listOf("double_cast","trigger_fireball","omnidirectional_scatter","chainsaw").map { SpellRegistry.create(it)!! }
                    w.load();w.status.mana=1000.0
                    val ok=w.spell(source)
                    ProjectileRuntime.tick()
                    record(t,"paid-machinegun-recipe-ready-next-tick",ok && w.status.delay==0.0 && w.status.recharge==0.0 && ProjectileRuntime.projectiles.any { it.id=="trigger_fireball" } && ProjectileRuntime.projectiles.none { it.id=="chainsaw" },"cast=$ok delay=${w.status.delay} recharge=${w.status.recharge}")
                    val carrier=ProjectileRuntime.projectiles.filterIsInstance<TriggerFireball>().single()
                    record(t,"machinegun-parent-does-not-scatter",carrier.modifiers.keys==setOf("double_cast") && carrier.payload.map{it.id}==listOf("omnidirectional_scatter","chainsaw") && carrier.delay==0 && carrier.recharge==0,"mods=${carrier.modifiers} payload=${carrier.payload.map{it.id}}")
                    record(t,"machinegun-mana-charged-once",w.status.mana==992.0,"mana=${w.status.mana}")
                    carrier.hitEntity(triggerVictim);ProjectileRuntime.tick()
                    val saw=ProjectileRuntime.projectiles.single{it.id=="chainsaw"}
                    record(t,"machinegun-only-payload-scatters",saw.modifiers.keys==setOf("omnidirectional_scatter") && carrier.payload.isEmpty(),"mods=${saw.modifiers}")
                    // 同一链的尾部修正也要覆盖先抽到的投射物；内层修正不得穿过链边界。
                    fun castCards(ids:List<String>,budget:Int=1,mana:Double=1000.0):Pair<Wand,List<Projectile>> {
                        // 每份抽牌夹具取消未使用荷载，不能把清场误当成玩法死亡来生成下一份测试的子弹。
                        ProjectileRuntime.projectiles.forEach {
                            (it as? TriggerSpell)?.payload?.clear()
                            it.addedTriggers.forEach { trigger -> trigger.cards.clear() }
                            it.alive=false
                        };ProjectileRuntime.tick()
                        val wand=Wand("scope-${ids.joinToString("-")}")
                        wand.cast=budget;wand.delayTickCount=10.0;wand.rechargeTickCount=20.0
                        wand.magics += ids.map{SpellRegistry.create(it)!!};wand.load();wand.status.mana=mana
                        wand.spell(source);ProjectileRuntime.tick()
                        return wand to ProjectileRuntime.projectiles.toList()
                    }
                    val (nestedW,nestedShots)=castCards(listOf("damage_boost","trigger_fireball","speed_boost","delayed_cast","freeze_charge","fireball"))
                    val outer=nestedShots.single() as TriggerFireball
                    val middle=outer.payload.filterIsInstance<DelayedCastProjectile>().single()
                    val inner=middle.payload.filterIsInstance<Projectile>().single()
                    record(t,"nested-chain-boundaries",outer.modifiers.keys==setOf("damage_boost") && outer.payload.filterIsInstance<Modifier>().map{it.id}==listOf("speed_boost") && middle.payload.filterIsInstance<Modifier>().map{it.id}==listOf("freeze_charge"),"root=${outer.modifiers} middle=${outer.payload.map{it.id}} inner=${middle.payload.map{it.id}}")
                    record(t,"nested-fees-once",nestedW.status.mana==1000.0-listOf("damage_boost","trigger_fireball","speed_boost","delayed_cast","freeze_charge","fireball").sumOf{SpellRegistry.create(it)!!.mana} && outer.delay==0 && middle.delay==0,"mana=${nestedW.status.mana} delay=${nestedW.status.delay}")
                    val copied=SpellAssembly.copy(outer) as TriggerFireball
                    val copiedMiddle=copied.payload.filterIsInstance<DelayedCastProjectile>().single()
                    record(t,"copies-own-independent-payload-tree",copied.payload!==outer.payload && copiedMiddle!==middle && copiedMiddle.payload!==middle.payload && copiedMiddle.payload.zip(middle.payload).all{(a,b)->a!==b},"copy=${copied.payload.map{it.id}}")
                    outer.hitEntity(triggerVictim);ProjectileRuntime.tick()
                    val releasedMiddle=ProjectileRuntime.projectiles.filterIsInstance<DelayedCastProjectile>().single()
                    val releasedInner=releasedMiddle.payload.filterIsInstance<Projectile>().single()
                    record(t,"trigger-child-does-not-inherit-parent",releasedMiddle.modifiers.keys==setOf("speed_boost") && releasedMiddle.status.damage==0.0,"mods=${releasedMiddle.modifiers}")
                    releasedMiddle.expire();ProjectileRuntime.tick()
                    record(t,"delayed-child-does-not-inherit-parent",releasedInner.modifiers.keys==setOf("freeze_charge") && releasedInner.status.damage==3.75 && releasedMiddle.payload.isEmpty(),"mods=${releasedInner.modifiers} damage=${releasedInner.status.damage}")
                    record(t,"releasing-original-keeps-copy-payload",copiedMiddle.payload.size==2 && copied.payload.size==2,"copy=${copiedMiddle.payload.size}")
                    val (_,siblings)=castCards(listOf("double_cast","fireball","damage_boost","fireball"))
                    record(t,"late-modifier-applies-to-entire-chain",siblings.size==2 && siblings.all{it.status.damage==8.0},"damage=${siblings.map{it.status.damage}}")
                    val (_,parentAndSibling)=castCards(listOf("double_cast","trigger_fireball","omnidirectional_scatter","chainsaw","damage_boost","fireball"))
                    record(t,"payload-does-not-steal-parent-sibling-budget",parentAndSibling.size==2 && parentAndSibling.all{it.modifiers.keys==setOf("double_cast","damage_boost")} && parentAndSibling.filterIsInstance<TriggerFireball>().single().payload.map{it.id}==listOf("omnidirectional_scatter","chainsaw"),"shots=${parentAndSibling.map{it.id to it.modifiers}}")
                    val (_,quantumShots)=castCards(listOf("quantum_entanglement","damage_boost","trigger_fireball","omnidirectional_scatter","chainsaw"))
                    val quantumCarriers=quantumShots.filterIsInstance<TriggerFireball>()
                    record(t,"quantum-copies-retain-isolated-payload",quantumCarriers.size==3 && quantumCarriers.all{it.payload.map{card->card.id}==listOf("omnidirectional_scatter","chainsaw") && "omnidirectional_scatter" !in it.modifiers} && quantumCarriers.map{System.identityHashCode(it.payload[1])}.distinct().size==3,"copies=${quantumCarriers.map{it.modifiers to it.payload.map{card->card.id}}}")
                    val (_,multicastPayload)=castCards(listOf("damage_boost","trigger_fireball","double_cast","fireball","speed_boost","fireball"))
                    val multicastCarrier=multicastPayload.single() as TriggerFireball
                    multicastCarrier.hitEntity(triggerVictim);ProjectileRuntime.tick()
                    val childShots=ProjectileRuntime.projectiles.filter{it.id=="fireball"}
                    record(t,"payload-multicast-shares-only-local-modifiers",childShots.size==2 && childShots.all{it.modifiers.keys==setOf("double_cast","speed_boost") && it.status.damage==5.0 && abs(it.status.velocity.length()-8f)<1e-5},"children=${childShots.map{Triple(it.modifiers,it.status.damage,it.status.velocity.length())}}")
                    val (_,twoTriggers)=castCards(listOf("double_cast","trigger_fireball","freeze_charge","fireball","trigger_fireball","damage_boost","fireball"))
                    val carriers=twoTriggers.filterIsInstance<TriggerFireball>()
                    record(t,"sibling-payloads-are-independent",carriers.size==2 && carriers.all{it.modifiers.keys==setOf("double_cast")} && carriers.map{it.payload.filterIsInstance<Modifier>().map{card->card.id}}==listOf(listOf("freeze_charge"),listOf("damage_boost")),"carriers=${carriers.map{it.payload.map{card->card.id}}}")
                    val (refund,refundShots)=castCards(listOf("damage_boost","mana_injection","fireball"),mana=0.0)
                    record(t,"same-chain-refund-funds-unordered-modifiers",refundShots.size==1 && refundShots.single().status.damage==8.0 && refund.status.mana==22.0,"mana=${refund.status.mana} shots=${refundShots.size}")
                    val (poor,poorShots)=castCards(listOf("trigger_fireball","chainsaw"),mana=5.0)
                    record(t,"unpaid-payload-does-not-reset-delay",poor.status.delay==10.0 && (poorShots.single() as TriggerFireball).payload.isEmpty() && poor.status.mana==0.0,"delay=${poor.status.delay} mana=${poor.status.mana}")
                    val (_,emptyShots)=castCards(listOf("trigger_fireball"),budget=2)
                    record(t,"empty-payload-never-wraps-into-itself",emptyShots.size==1 && (emptyShots.single() as TriggerFireball).payload.isEmpty(),"shots=${emptyShots.size}")
                    val (resetFirst,_)=castCards(listOf("chainsaw","fireball"),budget=2)
                    val (resetLast,_)=castCards(listOf("fireball","chainsaw"),budget=2)
                    record(t,"chainsaw-reset-independent-of-order",resetFirst.status.delay==0.0 && resetLast.status.delay==0.0,"first=${resetFirst.status.delay} last=${resetLast.status.delay}")
                    val recipes=listOf(listOf("damage_boost","volatile","damage_boost"),listOf("volatile","damage_boost","damage_boost"),listOf("damage_boost","damage_boost","volatile"))
                    val values=recipes.map{mods ->
                        val p=shot("fireball",Vec3(-8.0,floor+5.0,0.0),mods)
                        val result=p.status.damage to p.status.explosionDamage
                        p.alive=false;p.die();result
                    }
                    record(t,"modifier-order-independent-composition",values.distinct().size==1 && values.first()==(11.0 to 24.0),"values=$values")
                    val reduced=Wand("scatter-delay");reduced.delayTickCount=10.0;reduced.rechargeTickCount=20.0
                    reduced.innate += listOf("omnidirectional_scatter","fireball").map { SpellRegistry.create(it)!! }
                    reduced.load();reduced.status.mana=1000.0
                    reduced.spell(source)
                    record(t,"scatter-reduces-wand-base-cooldowns",reduced.status.delay==0.0 && reduced.status.recharge==0.0,"delay=${reduced.status.delay} recharge=${reduced.status.recharge}")
                    ProjectileRuntime.tick()
                    ProjectileRuntime.projectiles.forEach { it.alive=false };source.discard();triggerVictim.discard()
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
                        record(t,"beam-clips-real-shell-$i",abs(end.distanceTo(center)-1.7)<1e-4 && end.z<0 && p.status.pos.distance(start.toPosition())<1e-5 && p.status.velocity.lengthSquared()==0f,"end=$end source=${p.status.pos}")
                        record(t,"beam-protects-holder-and-behind-$i",holder!!.totalDamage==0.0 && behind!!.totalDamage==0.0 && shield.status.mana<before,"holder=${holder!!.totalDamage} behind=${behind!!.totalDamage} mana=${shield.status.mana}")
                        if(i==0){visibleBeam=p;ProjectileRuntime.add(p)} else {p.alive=false;p.die()}
                    }
                }
                if(t==65) {visibleBeam?.alive=false;holder?.discard();behind?.discard()}
                if(t==90) {
                    val target=ctx.spawnDummy(-5.0,floor+2.0,3.0)
                    val start=target.boundingBox.center.add(0.0,0.0,-0.8)
                    val p=shot("fireball",start,listOf("homing"));p.status.velocity.set(p.status.velocity.length(),0f,0f)
                    val speed=p.status.velocity.length();p.tick()
                    record(t,"homing-turns-towards-center-without-snap",p.guidance!=null && p.status.velocity.z>0 && p.status.velocity.length()<=speed,"velocity=${p.status.velocity}")
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
                    record(t,"projectiles-cleaned",ProjectileRuntime.projectiles.isEmpty(),"remaining=${ProjectileRuntime.projectiles.size}")
                    record(t,"pd-particles-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                    record(t,"dummy-stat-displays-cleaned",ctx.level.getEntitiesOfClass(DummyStatsDisplay::class.java,AABB.ofSize(Vec3(0.0,floor.toDouble(),0.0),60.0,60.0,60.0)).isEmpty(),"no orphan boards")
                }
            },checksAt={_,t -> checks[t] ?: emptyList()})
    }
}
