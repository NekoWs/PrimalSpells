@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")

package work.nekow.primalspells.client.capture

import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModDamageTypes
import work.nekow.primalspells.util.toPosition
import work.nekow.primalspells.wand.Wand

/** 真原版生物验证伤害/抗性/自伤与暗核，避免测试木桩免伤/免击退掩盖原版固定推力。 */
object CombatImpactScene {
    fun scene(): Scene {
        val name="combat_impact"
        val checks=linkedMapOf<Int,MutableList<Check>>()
        val pigs=ArrayList<Pig>()
        var hole: Projectile?=null
        var owner: Pig?=null
        var pulled: Pig?=null
        fun record(t:Int,label:String,ok:Boolean,detail:String) { checks.getOrPut(t){arrayListOf()}.add(Check(name,t,label,ok,detail)) }
        return Scene(name,"跃迁魔弹 / 强度击退 / 自伤 / 黑洞暗核 / 爆炸火舌",24,260,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12) for(z in -12..12) for(y in floor+1..floor+10)
                    ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(true,0.0,floor+6.0,-14.0,0f,15f)
                ctx.level.getEntitiesOfClass(TestDummyEntity::class.java,AABB.ofSize(Vec3.ZERO,100.0,200.0,100.0)).forEach { it.discard() }
            },camera={ctx,_ -> Pose(0.0,ctx.floorBlockY(0,0)+6.0,-14.0,0f,15f)},
            tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                fun pig(x:Double,z:Double,y:Double=floor+2.0):Pig {
                    val p=EntityTypes.PIG.create(ctx.level,EntitySpawnReason.COMMAND)!!
                    p.setPos(x,y,z);p.setNoAi(true);p.setNoGravity(true)
                    p.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=100.0;p.health=100f
                    ctx.level.addFreshEntity(p);pigs+=p;return p
                }
                fun shot(id:String,at:Vec3,mods:List<String> = emptyList(),caster:net.minecraft.world.entity.Entity=ctx.player):Projectile {
                    val p=SpellRegistry.create(id) as Projectile
                    SpellAssembly.apply(p,mods.map { SpellRegistry.create(it) as Modifier })
                    p.caster=caster;p.wand=Wand("impact-$t-$id");p.position=at.toPosition();p.velocity=Vector3f(0f,0f,1f)
                    p.spell();return p
                }
                if(t==8) {
                    val a=pig(-4.0,0.0);val b=pig(4.0,0.0)
                    val base=shot("fireball",a.boundingBox.center)
                    val boosted=shot("fireball",b.boundingBox.center,List(5){"damage_boost"})
                    SpellDamage.hit(ctx.level,a,base,base.status.damage);SpellDamage.hit(ctx.level,b,boosted,boosted.status.damage)
                    val weak=a.deltaMovement.length();val strong=b.deltaMovement.length()
                    record(t,"native-damage-no-fixed-knockback",ModDamageTypes.projectileSourceOrNull(ctx.level,ctx.player)!!.`is`(DamageTypeTags.NO_KNOCKBACK),"native no_knockback tag")
                    record(t,"spark-tiny-strength-scaled-knockback",abs(weak-SpellImpact.strength(5.0))<1e-6 && strong>weak*8 && a.health==95f && b.health==80f,"weak=$weak strong=$strong health=${a.health}/${b.health}")
                    val resistant=pig(8.0,0.0);resistant.getAttribute(Attributes.KNOCKBACK_RESISTANCE)!!.baseValue=1.0
                    SpellDamage.hit(ctx.level,resistant,boosted,20.0)
                    record(t,"knockback-resistance-honored",resistant.deltaMovement.lengthSqr()<1e-8,"velocity=${resistant.deltaMovement}")
                    val safe=pig(-8.0,-2.0);val danger=pig(-8.0,2.0)
                    fun returning(caster:Pig,mods:List<String>):Projectile {
                        val p=shot("fireball",caster.boundingBox.center,mods,caster)
                        p.throughBlocks=true;p.status.velocity.set(0.8f,0f,0f)
                        repeat(5){p.tick();p.status.age++};p.status.velocity.set(-2f,0f,0f)
                        repeat(5){if(p.alive){p.tick();p.status.age++}}
                        return p
                    }
                    val safeShot=returning(safe,emptyList());val piercingShot=returning(danger,listOf("piercing"))
                    record(t,"returning-piercing-self-damage",safe.health==100f && danger.health<100f && piercingShot.selfDamage,"safe=${safe.health} pierced=${danger.health}")
                    val blastOwner=pig(0.0,4.0);val before=blastOwner.health
                    Explosion.customExplode(blastOwner,blastOwner.boundingBox.center.add(1.0,0.0,0.0).toVector3f(),2f,12.0)
                    record(t,"explosion-real-self-damage-and-outward-push",blastOwner.health<before && blastOwner.deltaMovement.x<0,"health=${blastOwner.health} velocity=${blastOwner.deltaMovement}")
                    record(t,"explosive-cards-expose-self-damage",listOf("small_explosion","large_explosion","dormant_crystal","black_hole").all{(SpellRegistry.create(it) as Projectile).selfDamage},"all explosive/crystal/black-hole cards")
                    listOf(base,boosted,safeShot,piercingShot).forEach { it.alive=false;it.die() }
                    pigs.forEach { it.discard() };pigs.clear()
                    val explosiveMod=shot("fireball",Vec3(10.0,floor+5.0,-5.0),listOf("bounce_explosion"))
                    record(t,"explosion-modifier-enables-self-damage",explosiveMod.selfDamage,"bounce explosion recipe")
                    explosiveMod.alive=false // 不调用die，避免夹具额外制造死亡爆炸；该弹未进管理器且无本体。
                    val air=shot("chain_bolt",Vec3(0.0,floor+5.0,-6.0));val arc=air.effects.filterIsInstance<ChainArc>().single()
                    val ages=ArrayList<Int>();var previous=0
                    repeat(15){air.tick();if(arc.jumpsMade!=previous){ages+=air.status.age;previous=arc.jumpsMade};air.status.age++}
                    record(t,"noita-four-forward-jumps-in-15-ticks",ages==listOf(4,7,10,14) && arc.hitsLanded==0 && air.status.pos.z>1.4,"ages=$ages z=${air.status.pos.z}")
                    air.alive=false;air.die()
                    val longer=shot("chain_bolt",Vec3(0.0,floor+5.0,-6.0),listOf("extended_lifetime"))
                    val longerArc=longer.effects.filterIsInstance<ChainArc>().single()
                    repeat(longer.maxAge){longer.tick();longer.status.age++}
                    val lifetime=SpellRegistry.create("extended_lifetime")!!
                    record(t,"lifetime-25-ticks-40-mana-5-delay",longer.maxAge==40 && longerArc.jumpsMade==11 && lifetime.mana==40.0 && lifetime.delay==5,"age=${longer.maxAge} jumps=${longerArc.jumpsMade} mana=${lifetime.mana} delay=${lifetime.delay}")
                    longer.alive=false;longer.die()
                    val targets=listOf(pig(-1.0,7.0,floor+5.0),pig(0.0,7.0,floor+5.0),pig(1.0,7.0,floor+5.0))
                    val chain=shot("chain_bolt",Vec3(0.0,floor+5.45,6.0));val chainFx=chain.effects.filterIsInstance<ChainArc>().single()
                    repeat(15){chain.tick();chain.status.age++}
                    record(t,"chain-avoids-last-two-then-can-return",chainFx.hitsLanded==4 && targets[0].health==92f && targets[1].health==96f && targets[2].health==96f,"hits=${chainFx.hitsLanded} health=${targets.map{it.health}}")
                    chain.alive=false;chain.die();pigs.forEach { it.discard() };pigs.clear()
                    ctx.level.setBlock(BlockPos(0,floor+5,9),Blocks.STONE.defaultBlockState(),2)
                    val behind=pig(0.0,10.0,floor+5.0)
                    val phaser=shot("chain_bolt",Vec3(0.0,floor+5.45,8.0))
                    repeat(5){phaser.tick();phaser.status.age++}
                    record(t,"chain-jumps-through-thin-wall",behind.health==96f,"target health=${behind.health}")
                    phaser.alive=false;phaser.die();behind.discard()
                    val wallShot=shot("chain_bolt",Vec3(0.0,floor+5.2,7.05))
                    repeat(5){if(wallShot.alive){wallShot.tick();wallShot.status.age++}}
                    record(t,"chain-solid-destination-stops",!wallShot.alive,"alive=${wallShot.alive}")
                    wallShot.die();ctx.level.setBlock(BlockPos(0,floor+5,9),Blocks.AIR.defaultBlockState(),2)
                    pigs.forEach { it.discard() };pigs.clear()
                    val main=pig(0.0,7.0,floor+5.0);val neighbor=pig(0.8,7.0,floor+5.0)
                    val explosiveChain=shot("chain_bolt",Vec3(0.0,floor+5.45,6.0),listOf("volatile"))
                    repeat(5){explosiveChain.tick();explosiveChain.status.age++}
                    record(t,"chain-destination-dispatches-explosion-modifier",neighbor.health<100f && main.health<96f && explosiveChain.selfDamage,"main=${main.health} neighbor=${neighbor.health}")
                    explosiveChain.alive=false;explosiveChain.die();pigs.forEach { it.discard() };pigs.clear()
                    ctx.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,AABB.ofSize(Vec3.ZERO,100.0,200.0,100.0)).forEach { it.discard() }
                }
                if(t==50) Explosion.customExplode(ctx.player,Vector3f(-3f,floor+2f,0f),2f,12.0)
                if(t==85) Explosion.customExplode(ctx.player,Vector3f(3f,floor+2f,0f),6f,24.0)
                if(t==125) {
                    owner=pig(0.0,0.0);pulled=pig(3.0,0.0)
                    hole=shot("black_hole",owner!!.boundingBox.center,caster=owner!!)
                    hole!!.maxAge=65;ProjectileRuntime.add(hole!!)
                }
                if(t==127) record(t,"black-hole-pull-fivefold",pulled!!.deltaMovement.x< -0.15,"velocity=${pulled!!.deltaMovement}")
                if(t==137) {
                    record(t,"black-hole-core-hurts-caster-repeatedly",owner!!.health<=88f,"health=${owner!!.health}")
                    record(t,"core-damage-does-not-kick-outward",owner!!.deltaMovement.length()<0.03,"velocity=${owner!!.deltaMovement}")
                }
                if(t==210){pigs.forEach{it.discard()};pigs.clear();ProjectileRuntime.projectiles.forEach{it.alive=false}}
            },checksAt={ctx,t ->
                if(t==259) {
                    record(t,"projectiles-cleaned",ProjectileRuntime.projectiles.isEmpty(),"remaining=${ProjectileRuntime.projectiles.size}")
                    record(t,"pd-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
                checks[t].orEmpty()
            })
    }
}
