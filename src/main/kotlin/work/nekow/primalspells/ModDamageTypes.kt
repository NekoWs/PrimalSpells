package work.nekow.primalspells

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.damagesource.DamageScaling
import net.minecraft.world.damagesource.DamageType
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModDamageTypes {
    val DAMAGE_TYPES = DeferredRegister.create(Registries.DAMAGE_TYPE, PrimalSpells.MODID)

    val PROJECTILE = DAMAGE_TYPES.register("projectile", Supplier {
        DamageType("primalspells_projectile", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f)
    })

    val PROJECTILE_KEY: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "projectile"))
}
