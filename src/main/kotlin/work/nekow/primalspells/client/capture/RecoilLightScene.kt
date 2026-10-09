@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.projectile.*
import work.nekow.primalspells.wand.Wand
import kotlin.math.abs

object RecoilLightScene {
    fun scene():Scene {
        val name="recoil_light";val checks=linkedMapOf<Int,MutableList<Check>>()
        fun record(t:Int,id:String,ok:Boolean,info:String){checks.getOrPut(t){arrayListOf()}.add(Check(name,t,id,ok,info))}
        var beam:ConcentratedLight?=null
        return Scene(name,"后坐力 / 创造飞行豁免 / 汇聚蓄能与反弹",24,220,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12) for(z in -12..12) for(y in floor+1..floor+10)ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(true,-12.0,floor+6.0,-14.0,-40f,15f)
            },camera={ctx,_ -> Pose(-12.0,ctx.floorBlockY(0,0)+6.0,-14.0,-40f,15f)},tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                fun shot(id:String,mods:List<String> = emptyList(),z:Float=-5f):Projectile {
                    val p=MagicManager.create(id) as Projectile;SpellAssembly.apply(p,mods.map{MagicManager.create(it) as Modifier})
                    p.caster=ctx.player;p.wand=Wand("recoil-$t-$id");p.position=Vector3f(0f,floor+4f,z);p.velocity=Vector3f(0f,0f,1f);p.spell();return p
                }
                if(t==8) {
                    val pig=EntityTypes.PIG.create(ctx.level,EntitySpawnReason.COMMAND)!!
                    pig.setPos(8.0,floor+4.0,0.0);pig.setNoAi(true);pig.setNoGravity(true);pig.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=100.0;pig.health=100f
                    pig.persistentData.putDouble(MagicMass.ENTITY_TAG,1.0);ctx.level.addFreshEntity(pig)
                    fun fire(ids:List<String>):Double {
                        pig.deltaMovement=Vec3.ZERO
                        val w=Wand("recoil-${ids.joinToString()}");w.innate+=ids.map{MagicManager.create(it)!!};w.load();w.status.mana=1000.0
                        check(w.spell(pig));return pig.deltaMovement.length()
                    }
                    val base=fire(listOf("fireball"));val damp=fire(listOf("recoil_damper","fireball"));val boost=fire(listOf("recoil_boost","fireball"))
                    record(t,"paid-cast-recoil-and-modifiers",abs(base-0.025)<1e-6 && abs(damp/base-0.25)<1e-6 && abs(boost-0.375)<1e-6,"base=$base damp=$damp boost=$boost")
                    val a=fire(listOf("recoil_damper","recoil_boost","fireball"));val b=fire(listOf("recoil_boost","recoil_damper","fireball"))
                    record(t,"recoil-modifier-order-independent",abs(a-b)<1e-6,"$a/$b")
                    val stacked=fire(listOf("recoil_damper","recoil_damper","fireball"))
                    record(t,"recoil-damper-stacks",abs(stacked/base-0.0625)<1e-6,"ratio=${stacked/base}")
                    val focused=fire(listOf("concentrated_light"))
                    record(t,"focused-light-paid-cast-small-recoil",abs(focused-0.06)<1e-6,"kick=$focused")
                    // 付费检查只验证出手推力，立即结束其蓄能，避免污染后续独立发射镜头。
                    MagicManager.projectiles.filterIsInstance<ConcentratedLight>().filter{it.caster===pig}.forEach{it.alive=false}
                    pig.deltaMovement=Vec3.ZERO
                    val child=MagicManager.create("fireball") as Projectile;child.caster=pig;child.wand=Wand("child");child.position=Vector3f(8f,floor+4f,0f);child.velocity=Vector3f(0f,0f,1f);child.spell()
                    record(t,"unarmed-child-does-not-kick",pig.deltaMovement.lengthSqr()==0.0,"velocity=${pig.deltaMovement}")
                    child.alive=false;child.die()
                    val charged=MagicManager.create("concentrated_light") as ConcentratedLight
                    charged.caster=pig;charged.wand=Wand("charged-recoil");charged.position=Vector3f(8f,floor+4f,0f);charged.velocity=Vector3f(0f,0f,1f);charged.spell();charged.queueCastRecoil()
                    record(t,"focused-light-small-recoil-on-cast",abs(pig.deltaMovement.length()-0.06)<1e-6,"velocity=${pig.deltaMovement}")
                    charged.status.age=20;charged.tick();val kick=pig.deltaMovement.length()
                    record(t,"focused-light-release-does-not-repeat-recoil",abs(kick-0.06)<1e-6 && abs(pig.deltaMovement.length()-kick)<1e-6,"kick=$kick")
                    charged.alive=false;charged.die();pig.discard()
                    val target=EntityTypes.PIG.create(ctx.level,EntitySpawnReason.COMMAND)!!
                    target.setPos(0.0,floor+3.55,2.0);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=100.0;target.health=100f;ctx.level.addFreshEntity(target)
                    val impact=shot("concentrated_light") as ConcentratedLight;impact.status.age=20
                    repeat(4){if(impact.alive){impact.tick();impact.status.age++}}
                    record(t,"focused-light-real-swept-hit",target.health==64f && target.lastDamageSource?.msgId=="primalspells_energy","health=${target.health} source=${target.lastDamageSource?.msgId}")
                    impact.alive=false;impact.die();target.discard()
                    val h=shot("black_hole");h.mass=256.0;h.status.velocity.set(1f,0f,0f);MagicMass.attract(h,Vector3f(1f,0f,0f),0.0)
                    record(t,"mass-radial-damping-reduced",abs(h.status.velocity.x-0.96015625)<1e-6,"radial=${h.status.velocity.x}")
                    h.alive=false;h.die()
                    val player=ctx.player;val mode=player.gameMode.gameModeForPlayer;val flying=player.abilities.flying;val velocity=player.deltaMovement
                    player.setGameMode(GameType.CREATIVE);player.abilities.flying=true;player.deltaMovement=Vec3(0.05,0.02,0.0)
                    val field=shot("black_hole");val center=player.boundingBox.center.add(2.0,0.0,0.0);field.status.pos.set(center.x.toFloat(),center.y.toFloat(),center.z.toFloat());field.tick()
                    record(t,"creative-flight-not-attracted",player.deltaMovement==Vec3(0.05,0.02,0.0) && !player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),"velocity=${player.deltaMovement}")
                    player.abilities.flying=false;field.tick()
                    record(t,"creative-ground-still-attracted",player.deltaMovement.x>0.05,"velocity=${player.deltaMovement}")
                    field.alive=false;field.die();player.setGameMode(mode);player.abilities.flying=flying;player.deltaMovement=velocity
                    // 独立测量飞行距离；场景木桩/墙面的命中另由上面的扫掠和下面的反弹检查覆盖。
                    val light=shot("concentrated_light") as ConcentratedLight;light.collisionEnabled=false
                    val start=Vector3f(light.status.pos)
                    repeat(20){light.status.age=it;light.tick()}
                    record(t,"twenty-ticks-no-premature-shot",light.awaitingRelease && light.status.pos==start && light.status.velocity.length()==0f,"pos=${light.status.pos} awaiting=${light.awaitingRelease}")
                    light.status.age=20;light.tick()
                    record(t,"release-at-twenty-with-fast-flight",light.releasedAtAge==20 && !light.awaitingRelease && abs(light.status.velocity.length()-4.8f)<1e-5,"age=${light.releasedAtAge} velocity=${light.status.velocity}")
                    repeat(30){light.status.age++;light.tick()}
                    record(t,"long-range-over-140-blocks",light.status.pos.distance(start)>140f && light.alive,"distance=${light.status.pos.distance(start)}")
                    light.alive=false;light.die()
                    // 两个真实墙面验证五次反弹后终止；采用无修正本体，不绕过Move碰撞。
                    for(z in listOf(-10,6))for(x in -1..1)for(y in floor+3..floor+5)ctx.level.setBlock(BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2)
                    val rebound=shot("concentrated_light") as ConcentratedLight;rebound.status.age=20
                    var bounced=0
                    repeat(40){if(rebound.alive){val before=rebound.status.bounces;rebound.tick();rebound.status.age++;if(rebound.status.bounces<before)bounced++}}
                    record(t,"five-real-wall-bounces-then-stop",bounced==5 && !rebound.alive,"bounced=$bounced alive=${rebound.alive}")
                    rebound.die()
                    for(z in listOf(-10,6))for(x in -1..1)for(y in floor+3..floor+5)ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                }
                if(t==50) {
                    for(z in listOf(-8,6))for(x in -1..1)for(y in floor+3..floor+5)ctx.level.setBlock(BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2)
                    beam=shot("concentrated_light") as ConcentratedLight;beam!!.maxAge=70;MagicManager.add(beam!!)
                }
                if(t==65)record(t,"runtime-charge-stays-visible-at-muzzle",beam!!.awaitingRelease && abs(beam!!.status.pos.z+5)<0.001,"age=${beam!!.status.age} pos=${beam!!.status.pos}")
                if(t==77)record(t,"runtime-beam-reflects",!beam!!.awaitingRelease && beam!!.status.bounces in 0..4 && abs(beam!!.status.velocity.length()-4.8f)<1e-5,"age=${beam!!.status.age} remainingBounces=${beam!!.status.bounces} pos=${beam!!.status.pos} velocity=${beam!!.status.velocity}")
                if(t==180)MagicManager.projectiles.forEach{it.alive=false}
            },checksAt={ctx,t ->
                if(t==219){record(t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}");record(t,"pd-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")}
                checks[t].orEmpty()
            })
    }

    fun firstPerson(): Scene {
        val name="concentrated_light_first_person"
        lateinit var wand: Wand
        var stack=net.minecraft.world.item.ItemStack.EMPTY
        var cast=false
        var anchor:Vector3f?=null
        var direction:Vec3?=null
        return Scene(name,"第一人称汇聚之光 / 原地聚光阵与锁定方向",24,180,
            thirdPerson=false,showHands=true,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -4..4)for(z in -4..12)for(y in floor+1..floor+7)ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(false,0.5,floor+1.0,0.5,0f,0f)
                ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY)
                ctx.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY)
                work.nekow.primalspells.magic.effect.visual.WandCastingFx.clear()
                stack=net.minecraft.world.item.ItemStack(work.nekow.primalspells.registry.ModItems.WAND.get())
                wand=work.nekow.primalspells.wand.WandManager[work.nekow.primalspells.wand.WandManager.createWand(stack)]
                wand.magics+=MagicManager.create("concentrated_light");wand.load();wand.status.mana=1000.0
                ctx.player.inventory.setItem(7,stack)
            },camera={ctx,t -> Pose(if(t in 30..48)0.5+(t-30)*0.04 else if(t>48)1.22 else 0.5,ctx.floorBlockY(0,0)+1.0,0.5,if(t in 30..48)(t-30)*3f else if(t>48)54f else 0f,0f)},
            tick={ctx,t ->
                if(t==8)ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack)
                if(t==28)cast=wand.spell(ctx.player)
                if(t==30){val p=MagicManager.projectiles.filterIsInstance<ConcentratedLight>().lastOrNull{it.wand===wand};anchor=p?.status?.pos?.let{Vector3f(it)};direction=p?.launchDirection}
                if(t==100){ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);ctx.player.inventory.setItem(7,net.minecraft.world.item.ItemStack.EMPTY);MagicManager.projectiles.forEach{it.alive=false}}
            },finish={ctx ->
                ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);ctx.player.inventory.setItem(7,net.minecraft.world.item.ItemStack.EMPTY)
                work.nekow.primalspells.magic.effect.visual.WandCastingFx.forget(ctx.player.uuid)
            },checksAt={ctx,t ->
                val p=MagicManager.projectiles.filterIsInstance<ConcentratedLight>().lastOrNull{it.wand===wand}
                when(t){
                    45 -> listOf(Check(name,t,"real-wand-charges-at-fixed-world-point",cast && p?.awaitingRelease==true && anchor!=null && p.status.pos.distance(anchor!!)<0.001f && p.status.pos.distance(work.nekow.primalspells.magic.effect.visual.WandCastingFx.origin(ctx.player,wand).toVector3f())>0.15f,"cast=$cast pos=${p?.status?.pos} anchor=$anchor"))
                    50 -> listOf(Check(name,t,"real-wand-releases-after-charge-with-locked-direction",p!=null && !p.awaitingRelease && p.releasedAtAge==20 && direction!=null && Vec3(p.status.velocity).normalize().dot(direction!!)>0.999 && ctx.player.lookAngle.dot(direction!!)<0.8,"released=${p?.releasedAtAge} velocity=${p?.status?.velocity} locked=$direction"))
                    179 -> listOf(Check(name,t,"pd-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}"),Check(name,t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}"))
                    else -> emptyList()
                }
            })
    }
}
