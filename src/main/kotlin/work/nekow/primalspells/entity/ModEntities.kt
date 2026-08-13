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

    /** 正方体实体（边长 0.5） */
    val CUBE = ENTITY_TYPES.register("cube") { _ ->
        EntityType.Builder.of(::CubeEntity, MobCategory.MISC)
            .sized(0.5f, 0.5f) // 碰撞箱 0.5×0.5×0.5
            .clientTrackingRange(4)
            .updateInterval(20)
            .build(key("cube"))
    }

    /** 球形实体（直径 1.0） */
    val SPHERE = ENTITY_TYPES.register("sphere") { _ ->
        EntityType.Builder.of(::SphereEntity, MobCategory.MISC)
            .sized(1f, 1f) // 碰撞箱 1×1×1
            .clientTrackingRange(4)
            .updateInterval(20)
            .build(key("sphere"))
    }

    /** 无人机动态实体（模型约 5×4×5 像素 ≈ 0.31×0.25 格） */
    val DRONE = ENTITY_TYPES.register("drone") { _ ->
        EntityType.Builder.of(::DroneEntity, MobCategory.MISC)
            .sized(0.35f, 0.35f)
            .clientTrackingRange(8)
            .updateInterval(3)
            .setShouldReceiveVelocityUpdates(true)
            .build(key("drone"))
    }

    /** 测试木桩（伤害测试靶子，外形与盔甲架一致，尺寸同盔甲架） */
    val TEST_DUMMY = ENTITY_TYPES.register("test_dummy") { _ ->
        EntityType.Builder.of(::TestDummyEntity, MobCategory.MISC)
            .sized(0.5f, 1.975f)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build(key("test_dummy"))
    }

    /** 便捷方法：创建实体类型的 ResourceKey */
    private fun key(name: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PrimalSpells.MODID, name))
}
