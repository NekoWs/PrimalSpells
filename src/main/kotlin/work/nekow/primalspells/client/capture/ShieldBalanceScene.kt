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
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.modifier.EnergyShield
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandManager

/** 原版受伤验证破盾当次保护、卡数预算和法力，再用左右双靶记录真实碎片表现。 */
object ShieldBalanceScene {
    fun scene(): Scene {
        val name="shield_balance";val checks=linkedMapOf<Int,MutableList<Check>>()
        fun record(t:Int,id:String,ok:Boolean,info:String){checks.getOrPut(t){arrayListOf()}.add(Check(name,t,id,ok,info))}
        var left:TestDummyEntity?=null;var right:TestDummyEntity?=null
        lateinit var one:Wand;lateinit var eight:Wand
        fun shield(wand:Wand)=wand.loadedMagics.first{it is EnergyShield} as EnergyShield
        fun equip(holder:net.minecraft.world.entity.LivingEntity,count:Int):Wand {
            val stack=ItemStack(ModItems.WAND.get());val w=WandManager[WandManager.createWand(stack)]
            repeat(count){w.magics+=MagicManager.create("energy_shield")};w.load();w.mana=10000.0;w.status.mana=10000.0;w.charge=0.0
            holder.setItemInHand(InteractionHand.MAIN_HAND,stack);w.tick(holder,holder.level() as net.minecraft.server.level.ServerLevel)
            return w
        }
        return Scene(name,"单卡与八卡护盾 / 破盾当次保护 / 靶子固定",24,250,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12)for(z in -12..12)for(y in floor+1..floor+10)ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(true,0.0,floor+5.0,-11.0,0f,12f)
            },camera={ctx,_ -> Pose(0.0,ctx.floorBlockY(0,0)+5.0,-11.0,0f,12f)},tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                fun pig(x:Double):Pig {
                    val p=EntityTypes.PIG.create(ctx.level,EntitySpawnReason.COMMAND)!!
                    p.setPos(x,floor+3.0,0.0);p.setNoGravity(true);p.setNoAi(true);p.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;p.health=1000f;ctx.level.addFreshEntity(p);return p
                }
                fun incoming(holder:net.minecraft.world.entity.LivingEntity,damage:Double,mods:List<String> = emptyList()):Projectile {
                    val p=MagicManager.create("fireball") as Projectile;SpellAssembly.apply(p,mods.map{MagicManager.create(it) as Modifier})
                    p.caster=ctx.player;p.wand=Wand("shield-probe");p.status.damage=damage
                    val center=holder.boundingBox.center
                    p.position=center.add(0.0,0.0,-4.0).toVector3f();p.velocity=Vector3f(0f,0f,1f);p.spell()
                    p.status.pos=center.add(0.0,0.0,-0.5).toVector3f();p.status.velocity.set(0f,0f,4.8f)
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
                    val dummy=ctx.spawnDummy(0.0,floor+1.0,-2.0);val start=dummy.position()
                    SpellImpact.hurt(ctx.level,dummy,ctx.player,100.0,Vec3(1.0,1.0,0.0),blast=true)
                    dummy.push(2.0,3.0,4.0)
                    record(t,"dummy-rejects-direct-and-explosion-push",dummy.deltaMovement.lengthSqr()==0.0 && dummy.position()==start && dummy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)==1.0,"velocity=${dummy.deltaMovement}");dummy.discard()
                }
                if(t==60){left=ctx.spawnDummy(-3.0,floor+1.0,0.0);right=ctx.spawnDummy(3.0,floor+1.0,0.0);one=equip(left!!,1);eight=equip(right!!,8)}
                if(t in listOf(70,74,78,140)) {
                    if(t==140)eight.status.mana=0.0
                    for(holder in listOf(left!!,right!!)) {
                        val p=MagicManager.create("fireball") as Projectile;p.caster=ctx.player;p.wand=Wand("shield-runtime");p.status.damage=36.0
                        p.position=Vector3f(holder.x.toFloat(),floor+1.8f,-6f);p.velocity=Vector3f(0f,0f,1f);p.speed=3f;p.maxAge=30;p.spell();MagicManager.add(p)
                    }
                }
                if(t==84)record(t,"runtime-one-breaks-eight-remains-intact",shield(one).breached && !shield(eight).breached && eight.status.mana<10000.0,"one=${shield(one).breached} eight=${shield(eight).breached} mana=${eight.status.mana}")
                if(t==147)record(t,"runtime-eight-breaks-after-mana-depletion",shield(eight).breached,"breached=${shield(eight).breached}")
                if(t==200)MagicManager.projectiles.forEach{it.alive=false}
                if(t==220){left?.discard();right?.discard()}
            },checksAt={ctx,t ->
                if(t==249){record(t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}");record(t,"pd-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")}
                checks[t].orEmpty()
            })
    }
}
