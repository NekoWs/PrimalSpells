package work.nekow.primalspells.entity

import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

/**
 * 球形实体 —— 直径为 1 格，静态无 AI。
 */
class SphereEntity(
    entityType: EntityType<out SphereEntity>,
    level: Level
) : StaticEntity(entityType, level)
