@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.util.toVec3
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager

/** 原版受伤验证破盾当次保护、卡数预算和法力，再用左右双靶记录真实碎片表现。 */
object ShieldBalanceScene {
    fun scene(): Scene {
        val name="shield_balance";val checks=linkedMapOf<Int,MutableList<Check>>()
        fun record(t:Int,id:String,ok:Boolean,info:String){checks.getOrPut(t){arrayListOf()}.add(Check(name,t,id,ok,info))}
        val clientResults=java.util.concurrent.ConcurrentHashMap<String,Pair<Boolean,String>>()
        var left:TestDummyEntity?=null;var right:TestDummyEntity?=null
        var grazingLight:work.nekow.primalspells.magic.projectile.ConcentratedLight?=null
        var grazingContact:Vec3?=null
        lateinit var one:Wand;lateinit var eight:Wand
        fun shield(wand:Wand)=wand.loadedMagics.first{it is EnergyShield} as EnergyShield
        fun equip(holder:net.minecraft.world.entity.LivingEntity,count:Int):Wand {
            val stack=ItemStack(ModItems.WAND.get());val w=WandManager[WandManager.createWand(stack)]
            repeat(count){w.magics+=SpellRegistry.create("energy_shield")};w.load();w.mana=10000.0;w.status.mana=10000.0;w.charge=0.0
            holder.setItemInHand(InteractionHand.MAIN_HAND,stack);w.tick(holder,holder.level() as net.minecraft.server.level.ServerLevel)
            return w
        }
        return Scene(name,"单卡与八卡护盾 / 破盾当次保护 / 靶子固定",24,250,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12)for(z in -12..12)for(y in floor+1..floor+10)ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(true,0.0,floor+4.5,-8.5,0f,16f)
            },camera={ctx,_ -> Pose(0.0,ctx.floorBlockY(0,0)+4.5,-8.5,0f,16f)},tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                fun pig(x:Double):Pig {
                    val p=EntityTypes.PIG.create(ctx.level,EntitySpawnReason.COMMAND)!!
                    p.setPos(x,floor+3.0,0.0);p.setNoGravity(true);p.setNoAi(true);p.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;p.health=1000f;ctx.level.addFreshEntity(p);return p
                }
                fun incoming(holder:net.minecraft.world.entity.LivingEntity,damage:Double,mods:List<String> = emptyList()):Projectile {
                    val p=SpellRegistry.create("fireball") as Projectile;SpellAssembly.apply(p,mods.map{SpellRegistry.create(it) as Modifier})
                    p.caster=ctx.player;p.wand=Wand("shield-probe");p.status.damage=damage
                    val center=holder.boundingBox.center
                    p.position=center.add(0.0,0.0,-4.0).toPosition();p.velocity=Vector3f(0f,0f,1f);p.spell()
                    p.status.pos=center.add(0.0,0.0,-0.5).toPosition();p.status.velocity.set(0f,0f,4.8f)
                    p.hitEntity(holder);p.die();return p
                }
                if(t==8) {
                    val a=pig(-5.0);val b=pig(5.0);val w1=equip(a,1);val w8=equip(b,8)
                    record(t,"budget-scales-with-card-count",shield(w1).strengthBudget()==6.0 && shield(w8).strengthBudget()==3072.0,"one=${shield(w1).strengthBudget()} eight=${shield(w8).strengthBudget()}")
                    incoming(a,36.0)
                    record(t,"breaking-hit-does-not-damage-or-push",shield(w1).breached && a.health==1000f && a.deltaMovement.lengthSqr()==0.0,"health=${a.health} velocity=${a.deltaMovement}")
                    incoming(a,5.0)
                    record(t,"later-hit-passes-through-breach",a.health==995f,"health=${a.health}")
                    repeat(20){incoming(b,144.0)}
                    record(t,"eight-cards-hold-heavy-full-window",b.health==1000f && !shield(w8).breached && w8.status.mana==700.0,"health=${b.health} breached=${shield(w8).breached} mana=${w8.status.mana}")
                    w8.status.mana=0.0;incoming(b,5.0)
                    record(t,"eight-cards-still-break-without-mana",shield(w8).breached && b.health==1000f,"health=${b.health} breached=${shield(w8).breached}")
                    incoming(b,5.0);record(t,"empty-mana-breach-does-not-grant-immunity",b.health==995f,"health=${b.health}")
                    a.discard();b.discard()
                    val blast=pig(0.0);val wb=equip(blast,1);val center=blast.boundingBox.center.add(3.0,0.0,0.0)
                    Explosion.customExplode(ctx.player,center.toVector3f(),6f,24.0)
                    record(t,"breaking-explosion-absorbed",blast.health==1000f && shield(wb).breached,"health=${blast.health} breached=${shield(wb).breached}")
                    Explosion.customExplode(ctx.player,center.toVector3f(),6f,24.0)
                    record(t,"later-explosion-through-breach",blast.health<1000f,"health=${blast.health}")
                    blast.discard()
                    val combo=pig(0.0);val wc=equip(combo,1);val neighbor=pig(0.8);neighbor.setPos(0.8,floor+3.0,-2.0)
                    incoming(combo,20.0,listOf("volatile"))
                    record(t,"same-impact-explosion-does-not-bypass-broken-shield",combo.health==1000f && shield(wc).breached && neighbor.health<1000f,"holder=${combo.health} neighbor=${neighbor.health}")
                    combo.discard();neighbor.discard()
                    val pierce=pig(0.0);val wp=equip(pierce,8);incoming(pierce,5.0,listOf("piercing"))
                    record(t,"piercing-still-ignores-shield",pierce.health<1000f && wp.status.mana==10000.0 && !shield(wp).breached,"health=${pierce.health} mana=${wp.status.mana}");pierce.discard()
                    val inner=pig(0.0);val wi=equip(inner,8)
                    Explosion.customExplode(ctx.player,inner.boundingBox.center.toVector3f(),2f,10.0)
                    record(t,"internal-explosion-remains-dangerous",inner.health==990f && wi.status.mana==10000.0,"health=${inner.health}");inner.discard()
                    // 实际Move单刻穿越球壳；忽略摄影棚墙面，隔离首反射与剩余路程，墙反弹另有专场。
                    for ((speed, angled) in listOf(4.8f to false, 24f to false, 64f to false, 12f to true)) {
                        val holder=pig(6.0);val ws=equip(holder,8)
                        val center=holder.boundingBox.center
                        val heading=if(angled)Vec3(2.0,0.0,6.0).normalize() else Vec3(0.0,0.0,1.0)
                        val distance=if(speed>12f)10.0 else 3.6
                        val light=SpellRegistry.create("concentrated_light") as work.nekow.primalspells.magic.projectile.ConcentratedLight
                        light.caster=ctx.player;light.wand=Wand("shield-fast-$speed-$angled")
                        light.position=center.subtract(heading.scale(distance)).toPosition();light.velocity=heading.toVector3f();light.speed=speed/Projectile.LAUNCH_SPEED_SCALE;light.throughBlocks=true
                        light.spell();light.status.age=work.nekow.primalspells.magic.projectile.ConcentratedLight.CHARGE_TICKS;light.tick();light.status.age++
                        val reflected=light.status.pos.toVec3();val velocity=Vec3(light.status.velocity)
                        record(t,"fast-light-reflects-alive-$speed-$angled",light.alive && light.status.hitTargets==0 && light.status.bounces==5 && holder.health==1000f && !shield(ws).breached && velocity.dot(heading)<-speed*0.99 && reflected.distanceTo(center)>1.7,"alive=${light.alive} hits=${light.status.hitTargets} pos=$reflected velocity=$velocity health=${holder.health}")
                        ws.tick(holder,ctx.level)
                        record(t,"fast-light-charged-once-$speed-$angled",ws.status.mana==9995.0,"mana=${ws.status.mana}")
                        val victim=pig(6.0);val back=reflected.add(velocity.normalize().scale(2.0));victim.setPos(back.x,back.y-victim.bbHeight/2.0,back.z)
                        repeat(3){if(light.alive){light.tick();light.status.age++}}
                        record(t,"reflected-light-still-hits-$speed-$angled",victim.health==964f && holder.health==1000f && !light.alive,"victim=${victim.health} holder=${holder.health} alive=${light.alive}")
                        light.die();holder.discard();victim.discard()
                    }
                    // 擦过球壳但完全错开本体：旧逻辑只会在视觉之后的被动tick反射。
                    for ((speed, ox, oy) in listOf(Triple(4.8f,1.2,0.0),Triple(4.8f,-1.2,0.0),Triple(12f,0.9,0.9),Triple(64f,1.62,0.0))) {
                        val holder=pig(6.0);val ws=equip(holder,8)
                        val center=holder.position().add(0.0,holder.bbHeight*0.55,0.0)
                        val start=center.add(ox,oy,-3.0)
                        val light=SpellRegistry.create("concentrated_light") as work.nekow.primalspells.magic.projectile.ConcentratedLight
                        light.caster=ctx.player;light.wand=Wand("shield-graze");light.position=start.toPosition();light.velocity=Vector3f(0f,0f,1f);light.speed=speed/Projectile.LAUNCH_SPEED_SCALE;light.throughBlocks=true
                        light.spell();light.status.age=work.nekow.primalspells.magic.projectile.ConcentratedLight.CHARGE_TICKS;light.tick()
                        val contact=light.shieldReflectionPoint
                        val expected=center.add(ox,oy,-kotlin.math.sqrt(1.7*1.7-ox*ox-oy*oy))
                        val normal=contact?.subtract(center)?.normalize()
                        record(t,"grazing-contact-correct-before-passive-$speed-$ox-$oy",contact!=null && contact.distanceTo(expected)<0.001 && light.alive && light.status.hitTargets==0 && holder.health==1000f && ws.status.mana==9995.0,"contact=$contact expected=$expected pos=${light.status.pos} mana=${ws.status.mana}")
                        val position=Vector3d(light.status.pos);val velocity=Vector3f(light.status.velocity)
                        ws.tick(holder,ctx.level)
                        record(t,"grazing-no-late-shift-$speed-$ox-$oy",normal!=null && Vec3(velocity).dot(normal)>0 && kotlin.math.abs(velocity.length()-speed)<0.001 && position==light.status.pos && velocity==light.status.velocity && ws.status.mana==9995.0,"velocity=$velocity pos=${light.status.pos} mana=${ws.status.mana}")
                        light.alive=false;light.die();holder.discard()
                    }
                    val chainHolder=pig(6.0);val chainWand=equip(chainHolder,8)
                    val chain=SpellRegistry.create("chain_bolt") as Projectile
                    chain.caster=ctx.player;chain.wand=Wand("shield-chain");chain.position=chainHolder.boundingBox.center.add(0.0,0.0,-2.8).toPosition();chain.velocity=Vector3f(0f,0f,1f);chain.spell()
                    chain.status.age=4;chain.tick()
                    record(t,"chain-shield-reflection-keeps-outgoing-drift",chain.alive && chain.status.hitTargets==0 && chain.status.velocity.z<0 && chainHolder.health==1000f && chainWand.status.mana==9995.0,"velocity=${chain.status.velocity} health=${chainHolder.health} mana=${chainWand.status.mana}")
                    chain.alive=false;chain.die();chainHolder.discard()
                    val dummy=ctx.spawnDummy(0.0,floor+1.0,-2.0);val start=dummy.position()
                    SpellImpact.hurt(ctx.level,dummy,ctx.player,100.0,Vec3(1.0,1.0,0.0),blast=true)
                    dummy.push(2.0,3.0,4.0)
                    record(t,"dummy-rejects-direct-and-explosion-push",dummy.deltaMovement.lengthSqr()==0.0 && dummy.position()==start && dummy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)==1.0,"velocity=${dummy.deltaMovement}");dummy.discard()
                }
                if(t==20)net.minecraft.client.Minecraft.getInstance().execute {
                    val player=net.minecraft.client.Minecraft.getInstance().player ?: return@execute
                    val oldInput=player.input;val main=player.mainHandItem;val off=player.offhandItem
                    val values=arrayListOf<Float>()
                    try {
                        player.stopUsingItem()
                        player.input=object:net.minecraft.client.player.ClientInput(){override fun getMoveVector()=net.minecraft.world.phys.Vec2(0f,1f)}
                        player.applyInput();val baseline=player.zza
                        val items=listOf(ModItems.WAND.get())+ModItems.WAND_TIERS.values.map{it.get()}
                        var sprint=true
                        for(item in items)for(hand in listOf(InteractionHand.MAIN_HAND,InteractionHand.OFF_HAND)) {
                            val stack=ItemStack(item);player.setItemInHand(hand,stack);player.startUsingItem(hand);player.applyInput();values+=player.zza
                            sprint=sprint && stack.get(net.minecraft.core.component.DataComponents.USE_EFFECTS)!!.canSprint()
                            player.stopUsingItem()
                        }
                        clientResults["native-held-wand-input-keeps-speed"]=((baseline>0.9f && values.all{kotlin.math.abs(it-baseline)<0.0001}) to "baseline=$baseline values=$values")
                        clientResults["held-wand-can-sprint"]=sprint to "all tiers and both hands=$sprint"
                        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(net.minecraft.world.item.Items.BOW));player.startUsingItem(InteractionHand.MAIN_HAND);player.applyInput()
                        clientResults["native-other-item-slowdown-unchanged"]=(kotlin.math.abs(player.zza-baseline*0.2f)<0.0001) to "bow=${player.zza} baseline=$baseline"
                    } finally {
                        player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,main);player.setItemInHand(InteractionHand.OFF_HAND,off);player.input=oldInput;player.applyInput()
                    }
                }
                if(t==60){left=ctx.spawnDummy(-3.0,floor+1.0,0.0);right=ctx.spawnDummy(3.0,floor+1.0,0.0);one=equip(left!!,1);eight=equip(right!!,8)}
                if(t in listOf(70,74,78,140)) {
                    if(t==140)eight.status.mana=0.0
                    for(holder in listOf(left!!,right!!)) {
                        val p=SpellRegistry.create("fireball") as Projectile;p.caster=ctx.player;p.wand=Wand("shield-runtime");p.status.damage=36.0
                        p.position=Vector3d((holder.x).toDouble(), (floor+1.8f).toDouble(), (-6f).toDouble());p.velocity=Vector3f(0f,0f,1f);p.speed=3f;p.maxAge=30;p.spell();ProjectileRuntime.add(p)
                    }
                }
                if(t==90) {
                    val p=SpellRegistry.create("concentrated_light") as work.nekow.primalspells.magic.projectile.ConcentratedLight
                    p.caster=ctx.player;p.wand=Wand("grazing-visual")
                    val center=right!!.position().add(0.0,right!!.bbHeight*0.55,0.0)
                    p.position=center.add(1.2,0.0,-7.0).toPosition();p.velocity=Vector3f(0f,0f,1f);p.spell();ProjectileRuntime.add(p);grazingLight=p
                }
                if(t in 100..120 && grazingContact==null && grazingLight?.shieldReflectionPoint!=null) {
                    val center=right!!.position().add(0.0,right!!.bbHeight*0.55,0.0)
                    grazingContact=grazingLight!!.flightPath.firstOrNull { kotlin.math.abs(it.distanceTo(center)-1.7)<1e-6 }
                }
                if(t==115)grazingLight?.alive=false
                if(t==130) {
                    val center=right!!.position().add(0.0,right!!.bbHeight*0.55,0.0)
                    val expected=center.add(1.2,0.0,-kotlin.math.sqrt(1.7*1.7-1.2*1.2))
                    record(t,"runtime-grazing-contact-visible-at-shell",grazingContact?.distanceTo(expected)?.let{it<0.001}==true,"contact=$grazingContact expected=$expected")
                }
                if(t==84)record(t,"runtime-one-breaks-eight-remains-intact",shield(one).breached && !shield(eight).breached && eight.status.mana<10000.0,"one=${shield(one).breached} eight=${shield(eight).breached} mana=${eight.status.mana}")
                if(t==147)record(t,"runtime-eight-breaks-after-mana-depletion",shield(eight).breached,"breached=${shield(eight).breached}")
                if(t==200)ProjectileRuntime.projectiles.forEach{it.alive=false}
                if(t==220){left?.discard();right?.discard()}
            },checksAt={ctx,t ->
                if(t==249){record(t,"projectiles-cleaned",ProjectileRuntime.projectiles.isEmpty(),"remaining=${ProjectileRuntime.projectiles.size}");record(t,"pd-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")}
                if(t==249)for(id in listOf("native-held-wand-input-keeps-speed","held-wand-can-sprint","native-other-item-slowdown-unchanged")) {
                    val result=clientResults[id] ?: (false to "client callback did not finish")
                    record(t,id,result.first,result.second)
                }
                checks[t].orEmpty()
            })
    }
}
