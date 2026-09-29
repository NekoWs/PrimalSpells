package work.nekow.primalspells.registry

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageScaling
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier
import work.nekow.primalspells.PrimalSpells

object ModDamageTypes {
    val DAMAGE_TYPES = DeferredRegister.create(Registries.DAMAGE_TYPE, PrimalSpells.MODID)

    val PROJECTILE = DAMAGE_TYPES.register("projectile", Supplier {
        DamageType("primalspells_projectile", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f)
    })

    val PROJECTILE_KEY: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "projectile"))

    /** 只警告一次，避免数据包缺失时每 tick 刷屏。 */
    private var warnedMissingType = false

    /**
     * 安全解析投射物伤害类型对应的 [DamageSource]。
     *
     * **为什么需要它**：`DamageType` 是数据包注册表内容。如果世界/数据包没有加载
     * `primalspells:projectile`，原来的 `getOrThrow` 会抛
     * `IllegalStateException: Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]`
     * 直接把服务器 tick 打崩（见 `docs/known-issues.md` 的历史崩溃记录）。
     * 现在查不到就返回 `null` 并只记一次错误日志，由调用方跳过这次伤害。
     *
     * @param level 服务端世界（提供注册表访问）
     * @param cause 伤害来源实体（通常是施法者），可为 null
     * @return 可用的伤害源；数据包缺失时返回 null
     */
    fun projectileSourceOrNull(level: ServerLevel, cause: Entity?): DamageSource? {
        val type = level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .get(PROJECTILE_KEY)
            .orElse(null)
        if (type == null) {
            if (!warnedMissingType) {
                warnedMissingType = true
                PrimalSpells.LOGGER.error(
                    "伤害类型 {} 不在数据包注册表中，本次及后续投射物伤害将被跳过（检查 data/{}/damage_type/projectile.json）",
                    PROJECTILE_KEY, PrimalSpells.MODID
                )
            }
            return null
        }
        return DamageSource(type, cause)
    }
}
