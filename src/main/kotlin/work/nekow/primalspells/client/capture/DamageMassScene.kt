package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3f
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.entity.DamageNumber
import work.nekow.primalspells.magic.core.*
import work.nekow.primalspells.magic.effect.lifecycle.*
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModDamageTypes
import work.nekow.primalspells.wand.Wand
import kotlin.math.abs

/** 第66轮：原版伤害源/实体受力，以及双黑洞长时互吸的真实运行回归。 */
object DamageMassScene {
    fun scene(): Scene {
        val name = "damage_mass"
        val checks = linkedMapOf<Int, MutableList<Check>>()
        val pigs = arrayListOf<Pig>()
        var holes = emptyList<Projectile>()
        fun record(t: Int, label: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() }.add(Check(name,t,label,ok,detail))
        }
        return Scene(name,"伤害类型 / 质量受力 / 每刻伤害 / 双黑洞互吸",24,220,
            prepare={ctx ->
                val floor=ctx.floorBlockY(0,0)
                for(x in -12..12) for(z in -12..12) for(y in floor+1..floor+10)
                    ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                ctx.buildStudio(0,0,floor);ctx.setup(true,0.0,floor+6.0,-14.0,0f,15f)
            },camera={ctx,_ -> Pose(0.0,ctx.floorBlockY(0,0)+6.0,-14.0,0f,15f)},
            tick={ctx,t ->
                val floor=ctx.floorBlockY(0,0)
                fun pig(x:Double,z:Double,y:Double=floor+4.5):Pig {
                    val p=EntityTypes.PIG.create(ctx.level,EntitySpawnReason.COMMAND)!!
                    p.setPos(x,y,z);p.setNoAi(true);p.setNoGravity(true)
                    p.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=100.0;p.health=100f
                    ctx.level.addFreshEntity(p);pigs+=p;return p
                }
                fun shot(id:String,at:Vec3,mods:List<String> = emptyList()):Projectile {
                    val p=MagicManager.create(id) as Projectile
                    SpellAssembly.apply(p,mods.map { MagicManager.create(it) as Modifier })
                    p.caster=ctx.player;p.wand=Wand("mass-$t-$id");p.position=Vector3d((at.x).toDouble(), (at.y).toDouble(), (at.z).toDouble())
                    p.velocity=Vector3f(0f,0f,1f);p.spell();return p
                }
                if(t==8) {
                    val target=pig(0.0,0.0)
                    for(type in SpellDamageType.entries) {
                        target.health=100f;target.invulnerableTime=17
                        val src=ModDamageTypes.sourceOrNull(ctx.level,ctx.player,type)!!
                        val accepted=SpellImpact.hurt(ctx.level,target,ctx.player,2.0,Vec3.ZERO,knockback=false,type=type)
                        val key=src.getLocalizedDeathMessage(target).toString()
                        record(t,"native-type-${type.id}",accepted && target.health==98f && target.lastDamageSource?.msgId=="primalspells_${type.id}" &&
                            src.`is`(DamageTypeTags.NO_KNOCKBACK) && target.invulnerableTime==17 && key.contains("death.attack.primalspells_${type.id}"),
                            "health=${target.health} source=${target.lastDamageSource?.msgId} death=$key invul=${target.invulnerableTime}")
                    }
                    val fire=ModDamageTypes.sourceOrNull(ctx.level,ctx.player,SpellDamageType.FIRE)!!
                    val ice=ModDamageTypes.sourceOrNull(ctx.level,ctx.player,SpellDamageType.ICE)!!
                    val blast=ModDamageTypes.sourceOrNull(ctx.level,ctx.player,SpellDamageType.EXPLOSION)!!
                    record(t,"native-fire-ice-explosion-tags",fire.`is`(DamageTypeTags.IS_FIRE) && ice.`is`(DamageTypeTags.IS_FREEZING) && blast.`is`(DamageTypeTags.IS_EXPLOSION),"native mitigation/immunity tags")
                    for((id,type) in listOf("fireball" to SpellDamageType.PROJECTILE,"chainsaw" to SpellDamageType.SLICE,
                        "chain_bolt" to SpellDamageType.ELECTRICITY,"laser" to SpellDamageType.ENERGY)) {
                        val p=shot(id,target.boundingBox.center);target.health=100f
                        SpellDamage.hit(ctx.level,target,p,p.status.damage)
                        record(t,"spell-channel-$id",target.lastDamageSource?.msgId=="primalspells_${type.id}","source=${target.lastDamageSource?.msgId}")
                        p.alive=false;p.die()
                    }
                    val cold=shot("fireball",target.boundingBox.center,listOf("freeze_charge"))
                    target.health=100f;SpellDamage.hit(ctx.level,target,cold,cold.status.damage)
                    record(t,"freeze-converts-direct-channel",cold.damageType==SpellDamageType.ICE && target.lastDamageSource?.msgId=="primalspells_ice" && FrozenStatus.isFrozen(target),"type=${cold.damageType} health=${target.health}")
                    FrozenStatus.clear();cold.alive=false;cold.die()
                    target.health=100f;Explosion.customExplode(ctx.player,Vector3f(target.boundingBox.center.x.toFloat(),target.boundingBox.center.y.toFloat(),target.boundingBox.center.z.toFloat()),2f,4.0)
                    record(t,"explosion-uses-own-channel",target.lastDamageSource?.msgId=="primalspells_explosion" && target.health==96f,"source=${target.lastDamageSource?.msgId} health=${target.health}")
                    target.discard();pigs.clear()
                    val beamTarget=pig(0.0,2.0)
                    val beam=shot("plasma_beam",beamTarget.boundingBox.center.add(0.0,0.0,-3.0))
                    val beamHealth=arrayListOf<Float>()
                    repeat(5) { beam.status.age=it+1;beam.tick();beamHealth+=beamTarget.health }
                    record(t,"plasma-hurts-every-tick",beamHealth==listOf(98.5f,97f,95.5f,94f,92.5f) && beamTarget.lastDamageSource?.msgId=="primalspells_energy","health=$beamHealth")
                    beam.alive=false;beam.die();beamTarget.discard();pigs.clear()
                    val coreTarget=pig(8.0,0.0)
                    val core=shot("black_hole",coreTarget.boundingBox.center)
                    val coreHealth=arrayListOf<Float>()
                    repeat(5) { core.status.age=it+1;core.tick();coreHealth+=coreTarget.health }
                    record(t,"black-hole-hurts-every-tick",coreHealth==listOf(96f,92f,88f,84f,80f) && coreTarget.lastDamageSource?.msgId=="primalspells_gravity","health=$coreHealth")
                    core.alive=false;core.die();coreTarget.discard();pigs.clear()
                    val light=pig(-3.0,0.0);val heavy=pig(3.0,0.0)
                    light.persistentData.putDouble(MagicMass.ENTITY_TAG,1.0);heavy.persistentData.putDouble(MagicMass.ENTITY_TAG,8.0)
                    val field=shot("black_hole",Vec3(0.0,light.boundingBox.center.y,0.0));field.status.age=1;field.tick()
                    val ratio=light.deltaMovement.length()/heavy.deltaMovement.length()
                    record(t,"entity-mass-reduces-attraction-eightfold",abs(ratio-8.0)<1e-5,"ratio=$ratio")
                    val natural=MagicMass.of(light);light.persistentData.putDouble(MagicMass.ENTITY_TAG,Double.NaN)
                    record(t,"mass-invalid-data-falls-back",MagicMass.of(light).isFinite() && MagicMass.of(light)>0.0,"mass=${MagicMass.of(light)} explicit=$natural")
                    field.alive=false;field.die();pigs.forEach{it.discard()};pigs.clear()
                    val l=shot("fireball",Vec3(0.0,floor+5.0,0.0));val h=shot("fireball",Vec3(0.0,floor+5.0,0.0))
                    l.status.velocity.zero();h.status.velocity.zero();h.mass=8.0
                    MagicMass.attract(l,Vector3f(1f,0f,0f),0.6);MagicMass.attract(h,Vector3f(1f,0f,0f),0.6)
                    record(t,"projectile-mass-reduces-attraction-eightfold",abs(l.status.velocity.x/h.status.velocity.x-8f)<1e-5,"velocity=${l.status.velocity}/${h.status.velocity}")
                    h.mass=Double.POSITIVE_INFINITY;record(t,"projectile-invalid-mass-falls-back",h.mass==1.0,"mass=${h.mass}")
                    listOf(l,h).forEach{it.alive=false;it.die()}
                }
                if(t==45) {
                    holes=listOf(shot("black_hole",Vec3(-1.0,floor+3.0,0.0)),shot("black_hole",Vec3(1.0,floor+3.0,0.0)))
                    holes.forEach { it.maxAge=120;MagicManager.add(it) }
                }
                if(t==70 || t==140) record(t,"two-heavy-holes-slow-and-finite-$t",holes.size==2 && holes.all{it.alive && it.mass==256.0 && it.status.velocity.isFinite && it.status.velocity.length()<0.15f && abs(it.status.pos.x)<2.0},"holes=${holes.map{it.status.pos to it.status.velocity}}")
                if(t==175) MagicManager.projectiles.forEach{it.alive=false}
            },checksAt={ctx,t ->
                if(t==219) {
                    record(t,"projectiles-cleaned",MagicManager.projectiles.isEmpty(),"remaining=${MagicManager.projectiles.size}")
                    record(t,"pd-zero",ParticleManager.of(ctx.level).getEngine().particleCount()==0,"remaining=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
                checks[t].orEmpty()
            })
    }
}
