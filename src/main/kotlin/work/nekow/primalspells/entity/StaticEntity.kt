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
 * 静态实体基类 —— 无 AI、无自主移动的实体。
 * 正方体实体与球形实体均继承此类。
 */
abstract class StaticEntity(
    entityType: EntityType<out StaticEntity>,
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
