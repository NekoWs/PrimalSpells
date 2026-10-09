package work.nekow.primalspells.item

import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.CompassItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.LodestoneTracker
import net.minecraft.world.level.Level
import work.nekow.primalspells.loot.SpellLootProfile
import java.util.Optional

/** 真结构定位，沿用原版磁石罗盘指针；不生成虚构坐标或以书页类别代替位置。 */
class SpellCompassItem(properties: Properties) : CompassItem(properties) {
    companion object {
        private const val KEY = "primalspells:search_profile"
        fun selected(stack: ItemStack) = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr(KEY, "starter") ?: "starter"
    }
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level !is ServerLevel || player !is net.minecraft.server.level.ServerPlayer) return InteractionResult.SUCCESS
        val stack = player.getItemInHand(hand)
        val profiles = SpellLootProfile.entries.filter { it.allows(level) }
        if (profiles.isEmpty()) return InteractionResult.FAIL
        var profile = profiles.firstOrNull { it.key == selected(stack) } ?: profiles.first()
        if (player.isShiftKeyDown) {
            profile = profiles[(profiles.indexOf(profile) + 1) % profiles.size]
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putString(KEY, profile.key) }
            stack.remove(DataComponents.LODESTONE_TRACKER)
            player.sendSystemMessage(Component.translatable("compass.primalspells.category", category(profile), Component.translatable("item.primalspells.${profile.cards.first()}")), true)
        } else {
            // 原版定位可能同步读取结构起点；限定64区块搜索并设置200t冷却，不能每刻刷查询。
            if (player.cooldowns.isOnCooldown(stack)) return InteractionResult.FAIL
            player.cooldowns.addCooldown(stack, 200)
            val target = level.findNearestMapStructure(profile.structureTag, player.blockPosition(), 64, false)
            if (target == null) {
                stack.remove(DataComponents.LODESTONE_TRACKER)
                player.sendSystemMessage(Component.translatable("compass.primalspells.missing", category(profile)), true)
            } else {
                stack.set(DataComponents.LODESTONE_TRACKER, LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), target)), false))
                player.sendSystemMessage(Component.translatable("compass.primalspells.found", category(profile), target.x, target.z, kotlin.math.sqrt(target.distSqr(player.blockPosition())).toInt()), false)
            }
        }
        return InteractionResult.SUCCESS
    }
    private fun category(profile: SpellLootProfile): Component {
        val key = "compass.primalspells.${profile.key}"
        return Component.translatableWithFallback(key, profile.key)
    }
}
