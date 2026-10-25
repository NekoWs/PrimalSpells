package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.registry.ModEffects

/** 无界之界以原生效果计时、同步附件发布可见性；公共命中/索敌入口与原版接触统一读取。 */
object VoidStatus {
    @JvmStatic fun isPhased(entity: Entity?): Boolean = entity is LivingEntity &&
        if (entity.level().isClientSide) entity.getExistingDataOrNull(ModEffects.VOID_PHASED) == true
        else entity.hasEffect(ModEffects.VOID_PHASE)

    fun enter(entity: Entity, duration: Int = 60) {
        val living = entity as? LivingEntity ?: return
        living.stopRiding()
        living.addEffect(MobEffectInstance(ModEffects.VOID_PHASE, duration.coerceAtLeast(1), 0, false, false, true))
        living.deltaMovement = Vec3.ZERO
        living.fallDistance = 0.0
        living.stopUsingItem()
        living.level().getEntitiesOfClass(Mob::class.java, living.boundingBox.inflate(128.0)).forEach { mob ->
            if (mob.target === living) mob.target = null
            val brain = mob.brain
            listOf(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ROAR_TARGET, MemoryModuleType.HURT_BY_ENTITY).forEach { memory ->
                if (brain.hasMemoryValue(memory) && brain.getMemory(memory).orElse(null) === living) brain.eraseMemory(memory)
            }
            if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT) && brain.getMemory(MemoryModuleType.ANGRY_AT).orElse(null) == living.uuid)
                brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            if (mob is Warden) mob.clearAnger(living)
            if (mob is WitherBoss) for (head in 0..2) if (mob.getAlternativeTarget(head) == living.id) mob.setAlternativeTarget(head, 0)
        }
    }
}
