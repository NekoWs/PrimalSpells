package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.registry.ModItems

/** 用原版物品栏与快捷栏展示真实已打包图标，不使用自绘展示层。 */
object SpellIconScene {
    fun scene(): Scene {
        val ids = listOf("fireball", "concentrated_light", "black_hole", "sawblade", "piercing", "double_cast")
        val checks = ConcurrentHashMap<Int, List<Check>>()
        var floor = 0
        return Scene("spell_icons", "16像素法术图标 / 原版物品栏与快捷栏", 24, 76,
            showHud = true, allowScreen = true,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.0, floor + 2.0, -7.0, 0f, 5f)
                ctx.command("gamemode survival @a")
                ctx.player.inventory.clearContent()
                ctx.player.inventory.setSelectedSlot(6)
                ctx.player.containerMenu.broadcastFullState()
            },
            camera = { _, _ -> Pose(0.0, floor + 2.0, -7.0, 0f, 5f) },
            tick = { ctx, t ->
                if (t == 8) {
                    ids.forEachIndexed { i, id ->
                        val stack = ItemStack(requireNotNull(ModItems.spellItem(id)))
                        ctx.player.inventory.setItem(i, stack)
                        ctx.player.inventory.setItem(9 + i, stack.copy())
                    }
                    ctx.player.containerMenu.broadcastFullState()
                }
                if (t == 62) {
                    ctx.player.inventory.clearContent()
                    ctx.player.containerMenu.broadcastFullState()
                }
            },
            clientTick = { mc, t ->
                // 只清理录制实例的提示；让截图集中展示原版栏位，空手也不遮挡样例。
                mc.gui.toastManager().clear()
                mc.gui.hud.chat.clearMessages(false)
                mc.player?.inventory?.setSelectedSlot(6)
                if (t == 16) mc.gui.setScreen(InventoryScreen(requireNotNull(mc.player)))
                if (t == 24 || t == 54) {
                    val inventory = requireNotNull(mc.player).inventory
                    val loaded = ids.withIndex().all { (i, id) ->
                        inventory.getItem(i).item == ModItems.spellItem(id) &&
                            inventory.getItem(9 + i).item == ModItems.spellItem(id)
                    }
                    checks[t] = listOf(Check("spell_icons", t, "real-items-in-${if (t == 24) "inventory" else "hotbar"}",
                        loaded && (mc.gui.screen() is InventoryScreen) == (t == 24), ids.toString()))
                }
                if (t == 48) mc.gui.setScreen(null)
                if (t == 72) checks[t] = listOf(Check("spell_icons", t, "inventory-cleaned",
                    requireNotNull(mc.player).inventory.isEmpty && mc.gui.screen() == null, "原版栏位已清空"))
            },
            checksAt = { _, t -> checks[t].orEmpty() },
        )
    }
}
