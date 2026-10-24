package work.nekow.primalspells.magic.core

import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/** 保留原版伤害归属/标签，只选择法术死亡文本；服务器原样发送到死亡界面和聊天栏。 */
class SpellDamageSource(type: Holder<DamageType>, caster: Entity?) : DamageSource(type, caster) {
    override fun getLocalizedDeathMessage(victim: LivingEntity): Component {
        val key = "death.attack.$msgId"
        val caster = entity ?: directEntity ?: return Component.translatable(key, victim.displayName)
        return if (caster.uuid == victim.uuid) Component.translatable("$key.self", victim.displayName)
            else Component.translatable("$key.player", victim.displayName, caster.displayName)
    }
}
