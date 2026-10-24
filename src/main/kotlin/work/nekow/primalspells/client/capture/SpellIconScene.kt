package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.projectile.Projectile
import work.nekow.primalspells.registry.ModItems

/** 用原版物品栏与快捷栏展示真实已打包图标，不使用自绘展示层。 */
object SpellIconScene {
    fun scene(): Scene {
        val ids = SpellRegistry.factories.entries.sortedBy { entry ->
            when (entry.value()) {
                is Projectile -> 0
                is Modifier -> 1
                else -> 2
            }
        }.map { it.key }
        val pages = ids.chunked(27)
        val lastPageTick = 8 + (pages.size - 1) * 36
        val closeTick = lastPageTick + 32
        val clearTick = lastPageTick + 46
        val checks = ConcurrentHashMap<Int, List<Check>>()
        var floor = 0
        return Scene("spell_icons", "完整像素法术目录 / 原版物品栏与快捷栏", 24, lastPageTick + 64,
            showHud = true, allowScreen = true,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0, 0)
                ctx.buildStudio(0, 0, floor)
                ctx.setup(false, 0.0, floor + 2.0, -7.0, 0f, 5f)
                ctx.command("gamemode survival @a")
                ctx.player.inventory.clearContent()
                ctx.player.inventory.setSelectedSlot(8)
                ctx.player.containerMenu.broadcastFullState()
            },
            camera = { _, _ -> Pose(0.0, floor + 2.0, -7.0, 0f, 5f) },
            tick = { ctx, t ->
                val pageIndex = (t - 8) / 36
                if (t >= 8 && (t - 8) % 36 == 0 && pageIndex in pages.indices) {
                    ctx.player.inventory.clearContent()
                    pages[pageIndex].forEachIndexed { i, id ->
                        val stack = ItemStack(requireNotNull(ModItems.spellItem(id)))
                        ctx.player.inventory.setItem(9 + i, stack)
                        if (i < 8) ctx.player.inventory.setItem(i, stack.copy())
                    }
                    ctx.player.containerMenu.broadcastFullState()
                }
                if (t == clearTick) {
                    ctx.player.inventory.clearContent()
                    ctx.player.containerMenu.broadcastFullState()
                }
            },
            clientTick = { mc, t ->
                // 只清理录制实例的提示；让截图集中展示原版栏位，空手也不遮挡样例。
                mc.gui.toastManager().clear()
                mc.gui.hud.chat.clearMessages(false)
                mc.player?.inventory?.setSelectedSlot(8)
                if (t == 16) mc.gui.setScreen(InventoryScreen(requireNotNull(mc.player)))
                val pageIndex = (t - 24) / 36
                if (t >= 24 && (t - 24) % 36 == 0 && pageIndex in pages.indices) {
                    val inventory = requireNotNull(mc.player).inventory
                    val page = pages[pageIndex]
                    val loaded = (0 until 27).all { i ->
                        if (i < page.size) inventory.getItem(9 + i).item == ModItems.spellItem(page[i])
                        else inventory.getItem(9 + i).isEmpty
                    }
                    checks[t] = listOf(Check("spell_icons", t, "real-inventory-page-${pageIndex + 1}",
                        loaded && mc.gui.screen() is InventoryScreen, page.toString()))
                }
                if (t == closeTick) mc.gui.setScreen(null)
                if (t == closeTick + 6) {
                    val inventory = requireNotNull(mc.player).inventory
                    val loaded = pages.last().take(8).withIndex().all { (i, id) ->
                        inventory.getItem(i).item == ModItems.spellItem(id)
                    }
                    checks[t] = listOf(Check("spell_icons", t, "real-items-in-hotbar",
                        loaded && mc.gui.screen() == null, "最后一页快捷栏"))
                }
                if (t == clearTick + 10) checks[t] = listOf(Check("spell_icons", t, "inventory-cleaned",
                    requireNotNull(mc.player).inventory.isEmpty && mc.gui.screen() == null, "原版栏位已清空"))
            },
            checksAt = { _, t -> checks[t].orEmpty() },
        )
    }
}
