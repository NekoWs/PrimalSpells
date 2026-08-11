package work.nekow.primalspells.entity

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells

/**
 * 实体注册 —— 将自定义实体类型注册到原版 registries。
 */
object ModEntities {
    val ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, PrimalSpells.MODID)

    /** 正方体实体（边长为 1） */
    val CUBE = ENTITY_TYPES.register("cube") { _ ->
        EntityType.Builder.of(::CubeEntity, MobCategory.MISC)
            .sized(1f, 1f) // 实体碰撞箱 1×1×1
            .clientTrackingRange(4) // 客户端追踪范围
            .updateInterval(20) // 更新间隔（tick）
            .build(
                ResourceKey.create(
                    Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(PrimalSpells.MODID, "cube")
                )
            )
    }
}
