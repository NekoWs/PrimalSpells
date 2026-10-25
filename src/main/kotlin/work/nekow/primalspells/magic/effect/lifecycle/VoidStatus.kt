package work.nekow.primalspells.magic.effect.lifecycle

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import work.nekow.primalspells.registry.ModEffects

/** 无界之界以原生效果计时、同步附件发布可见性；公共命中/索敌入口与原版接触统一读取。 */
object VoidStatus {
    @JvmStatic fun isPhased(entity: Entity?): Boolean = entity is LivingEntity &&
        if (entity.level().isClientSide) entity.getExistingDataOrNull(ModEffects.VOID_PHASED) == true
        else entity.hasEffect(ModEffects.VOID_PHASE)

    fun enter(entity: Entity) {
        val living = entity as? LivingEntity ?: return
        living.addEffect(MobEffectInstance(ModEffects.VOID_PHASE, 60, 0, false, false, true))
        living.stopUsingItem()
        living.level().getEntitiesOfClass(Mob::class.java, living.boundingBox.inflate(128.0)).forEach { mob ->
            if (mob.target === living) mob.target = null
            if (mob.brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && mob.brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) === living)
                mob.brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
        }
    }
}
