package work.nekow.primalspells.magic.projectile.immobile

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.NeoForge

class ItemPull : UtilityPulse() {
    override val id = "item_pull"
    init { mana = 8.0 }
    override fun onSpell() {
        val player = caster as? Player ?: return
        val level = caster.level() as? ServerLevel ?: return
        for (item in level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(4.0))) {
            if (item.position().distanceToSqr(player.position()) <= 16 && !item.hasPickUpDelay() &&
                (item.target == null || item.target == player.uuid) && visible(level, player.eyePosition, item.boundingBox.center) && allowed(item)) {
                // 原版拥有者/拾取延迟/NeoForge拾取事件和背包容量仍由playerTouch最终裁定。
                item.playerTouch(player)
            }
        }
    }
}
