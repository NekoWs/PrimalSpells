package work.nekow.primalspells.entity

import net.minecraft.ChatFormatting
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.item.WandItem
import kotlin.math.cos
import kotlin.math.sin

/** 流派由实际持杖配方推导，名称/准备节奏/战术与可夺取的牌一致。 */
enum class MageSchool(val windup: Int, val burst: Int, val recovery: Int, val range: Double, val color: ChatFormatting) {
    SKIRMISHER(16, 3, 38, 9.0, ChatFormatting.GREEN),
    WARDEN(24, 2, 50, 7.0, ChatFormatting.AQUA),
    ARCANIST(28, 2, 54, 11.0, ChatFormatting.LIGHT_PURPLE),
    SIEGE(32, 1, 65, 14.0, ChatFormatting.GOLD);
    companion object {
        fun of(mage: RogueMageEntity): MageSchool {
            val ids = WandItem.getWand(mage.mainHandItem)?.magics?.mapNotNull { it?.id }.orEmpty()
            return when {
                "concentrated_light" in ids || "plasma_beam" in ids -> SIEGE
                "energy_shield" in ids -> WARDEN
                ids.any { it in listOf("trigger_fireball", "add_trigger", "add_timer", "delayed_cast") } -> ARCANIST
                else -> SKIRMISHER
            }
        }
    }
}

/** 仅评估已加载、可站立且在出生活动圈内的位置；掩体由真实方块射线决定。 */
object MageTactics {
    fun destination(mage: RogueMageEntity, target: LivingEntity, cover: Boolean): Vec3? {
        val level = mage.level() as? net.minecraft.server.level.ServerLevel ?: return null
        val candidates = (0 until 12).mapNotNull { i ->
            val angle = i * Math.PI / 6 + (mage.id % 7) * 0.17
            val p = mage.position().add(cos(angle) * 4.0, 0.0, sin(angle) * 4.0)
            val block = net.minecraft.core.BlockPos.containing(p)
            if (level.chunkSource.getChunkNow(block.x shr 4, block.z shr 4) == null || mage.hasHome() && !mage.isWithinHome(block)) return@mapNotNull null
            val box = mage.boundingBox.move(p.subtract(mage.position()))
            if (!level.noCollision(mage, box) || level.getBlockState(block.below()).getCollisionShape(level, block.below()).isEmpty) return@mapNotNull null
            val eye = p.add(0.0, mage.eyeHeight.toDouble(), 0.0)
            val blocked = level.clip(ClipContext(target.eyePosition, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mage)).type != HitResult.Type.MISS
            if (cover && !blocked) return@mapNotNull null
            p
        }
        return if (cover) candidates.minByOrNull { it.distanceToSqr(mage.position()) + it.distanceToSqr(target.position()) * 0.05 }
        else candidates.maxByOrNull { it.distanceToSqr(target.position()) }
    }
}
