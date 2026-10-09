package work.nekow.primalspells.registry

import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageScaling
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.magic.core.SpellDamageType

object ModDamageTypes {
    val DAMAGE_TYPES = DeferredRegister.create(Registries.DAMAGE_TYPE, PrimalSpells.MODID)
    private val types = SpellDamageType.entries.associateWith { channel ->
        DAMAGE_TYPES.register(channel.id, Supplier {
            DamageType("primalspells_${channel.id}", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f)
        })
    }
    val PROJECTILE = types.getValue(SpellDamageType.PROJECTILE)
    val PROJECTILE_KEY = SpellDamageType.PROJECTILE.key
    private val warnedMissing = HashSet<SpellDamageType>()

    /** 数据包缺失时按渠道只记一次错误并跳过伤害，不让服务器tick崩溃。 */
    fun sourceOrNull(level: ServerLevel, cause: Entity?, channel: SpellDamageType): DamageSource? {
        val type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(channel.key).orElse(null)
        if (type == null) {
            if (warnedMissing.add(channel)) PrimalSpells.LOGGER.error("缺少法术伤害类型 {}，跳过本渠道伤害", channel.key)
            return null
        }
        return DamageSource(type, cause)
    }

    fun projectileSourceOrNull(level: ServerLevel, cause: Entity?): DamageSource? =
        sourceOrNull(level, cause, SpellDamageType.PROJECTILE)
}
