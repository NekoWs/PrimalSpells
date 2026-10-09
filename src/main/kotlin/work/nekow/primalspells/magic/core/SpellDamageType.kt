package work.nekow.primalspells.magic.core

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.world.damagesource.DamageType

/** 实际结算使用的伤害渠道；类别与数值分离，爆炸不冒充载体的直接命中类型。 */
enum class SpellDamageType(val id: String, val color: Int) {
    PROJECTILE("projectile", 0xFF6868),
    EXPLOSION("explosion", 0xFFAE42),
    FIRE("fire", 0xFF722E),
    SLICE("slice", 0xE6EBD9),
    ICE("ice", 0x8BE4FF),
    ELECTRICITY("electricity", 0xD5BCFF),
    ENERGY("energy", 0x6EF4EA),
    GRAVITY("gravity", 0xBB87E8);

    val key: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath("primalspells", id))
    val text: Component get() = Component.translatable("damage.primalspells.$id")
    companion object {
        fun fromMessageId(id: String): SpellDamageType? = entries.firstOrNull { "primalspells_${it.id}" == id }
    }
}
