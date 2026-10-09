package work.nekow.primalspells.registry

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredRegister
import work.nekow.primalspells.PrimalSpells
import work.nekow.primalspells.entity.RogueMageEntity
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.entity.TestDummyEntity

/**
 * 实体注册 —— 将自定义实体类型注册到原版 registries。
 *
 * 这里只保留会真正生成的实体：`drone`（`DroneSwarm` 召唤）与 `test_dummy`（测试木桩物品放置）。
 * 原先的 cube/sphere 调试实体没有生成点，已随死代码清理移除。
 */
object ModEntities {
    val ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, PrimalSpells.MODID)

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

    val ROGUE_MAGE = ENTITY_TYPES.register("rogue_mage") { _ ->
        EntityType.Builder.of(::RogueMageEntity, MobCategory.MONSTER)
            .sized(0.6f, 1.95f).clientTrackingRange(10).updateInterval(3).build(key("rogue_mage"))
    }

    /** 便捷方法：创建实体类型的 ResourceKey */
    private fun key(name: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PrimalSpells.MODID, name))
}
