package work.nekow.primalspells.magic.projectile.immobile

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.util.toVec3

class SurveyPulse : UtilityPulse() {
    override val id = "survey_pulse"
    init { mana = 12.0 }
    override fun onSpell() {
        val level = caster.level() as? ServerLevel ?: return
        val origin = status.pos.toVec3()
        for (target in level.getEntitiesOfClass(Monster::class.java, caster.boundingBox.inflate(12.0))) {
            if (target.isAlive && !caster.isAlliedTo(target) && target.boundingBox.center.distanceToSqr(origin) <= 144 && visible(level, origin, target.eyePosition) && allowed(target)) {
                target.addEffect(MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false))
            }
        }
    }
}
