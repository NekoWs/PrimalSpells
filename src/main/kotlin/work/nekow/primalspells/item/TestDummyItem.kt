package work.nekow.primalspells.item

import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.entity.ModEntities
import work.nekow.primalspells.entity.TestDummyEntity

/**
 * 测试木桩物品：在点击位置放置一个测试木桩，放置逻辑与盔甲架一致
 * （点击面朝下失败、碰撞检测、角度吸附 45°、消耗物品、放置音效）。
 */
class TestDummyItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        if (context.clickedFace == Direction.DOWN) return InteractionResult.FAIL
        val level = context.level
        val blockPos = BlockPlaceContext(context).clickedPos
        val pos = Vec3.atBottomCenterOf(blockPos)
        val box = ModEntities.TEST_DUMMY.get().dimensions.makeBoundingBox(pos.x, pos.y, pos.z)
        if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) {
            return InteractionResult.FAIL
        }
        if (level is ServerLevel) {
            val dummy = TestDummyEntity(ModEntities.TEST_DUMMY.get(), level)
            val yRot = Mth.floor((Mth.wrapDegrees(context.rotation - 180.0f) + 22.5f) / 45.0f) * 45.0f
            dummy.snapTo(pos.x, pos.y, pos.z, yRot, 0.0f)
            level.addFreshEntityWithPassengers(dummy)
            level.playSound(null, dummy.x, dummy.y, dummy.z, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f)
            dummy.gameEvent(GameEvent.ENTITY_PLACE, context.player)
        }
        context.itemInHand.shrink(1)
        return InteractionResult.SUCCESS
    }
}
