package work.nekow.primalspells.entity

import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * 自定义正方体实体 —— 边长为 1 格（1×1×1）。
 * 该实体无 AI、无持久化额外数据，仅作为无人机群的召唤物存在。
 */
class CubeEntity(
    entityType: EntityType<out CubeEntity>,
    level: Level
) : Entity(entityType, level) {

    /** 注册同步数据（本实体无需同步额外数据） */
    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    /** 从 ValueInput 读取额外持久化数据（无额外数据） */
    override fun readAdditionalSaveData(input: ValueInput) {}

    /** 将额外持久化数据写入 ValueOutput（无额外数据） */
    override fun addAdditionalSaveData(output: ValueOutput) {}

    /** 服务端伤害处理：接收伤害后即移除实体 */
    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        this.kill(level) // 受到任意伤害即移除
        return true
    }

    /** 使实体可被碰撞检测（参数为碰撞发起方实体，可空） */
    override fun canBeCollidedWith(entity: Entity?): Boolean = true

    /** 使实体可被选取（光线追踪） */
    override fun isPickable(): Boolean = true
}
