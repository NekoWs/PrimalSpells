package work.nekow.primalspells.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.item.ModItems

/**
 * 测试木桩：用于测试伤害的假人靶子，外形与盔甲架一致（复用盔甲架模型/渲染）。
 *
 * - 血量为 int 上限，永不死亡
 * - 不接受任何治疗（[heal] 无效；负/零伤害视为 0）
 * - 每次受到伤害时头顶跳出本次伤害量数字（见 [DamageNumber]）
 * - 不受击退（靶子保持原地）
 * - 空手右键回收木桩（连同装备掉落，避免物品丢失）；手持物品右键仍可给木桩穿戴
 */
class TestDummyEntity(
    entityType: net.minecraft.world.entity.EntityType<out TestDummyEntity>,
    level: Level
) : ArmorStand(entityType, level) {

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        if (isRemoved || !isAlive) return false
        if (amount <= 0) return false // 会让生命值增加的"伤害"（治疗）一律归零
        // 头顶跳出本次伤害量数字
        spawnDamageNumber(level, position().add(0.0, bbHeight + 0.35, 0.0), amount.toDouble())
        return true
    }

    /** 木桩不接受任何治疗效果 */
    override fun heal(amount: Float) {}

    /** 木桩不受击退，保持原地便于连续测试（5 参数版会委托到此版） */
    override fun knockback(power: Double, xd: Double, zd: Double, source: DamageSource, damage: Float, comesFromEffect: Boolean) {}

    /** 空手右键：回收木桩（掉落木桩物品 + 全部装备）；否则保持盔甲架穿戴行为 */
    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        if (!player.getItemInHand(hand).isEmpty) {
            return super.interact(player, hand, location)
        }
        if (level().isClientSide) return InteractionResult.SUCCESS_SERVER
        val serverLevel = level() as ServerLevel
        Block.popResource(serverLevel, blockPosition(), ItemStack(ModItems.TEST_DUMMY.get()))
        for (slot in EquipmentSlot.VALUES) {
            val stack = getItemBySlot(slot)
            if (!stack.isEmpty) Block.popResource(serverLevel, blockPosition(), stack)
        }
        kill(serverLevel)
        return InteractionResult.SUCCESS_SERVER
    }

    companion object {
        /** 血量为 int 上限 */
        fun createAttributes(): AttributeSupplier.Builder =
            ArmorStand.createAttributes().add(Attributes.MAX_HEALTH, Int.MAX_VALUE.toDouble())
    }
}
