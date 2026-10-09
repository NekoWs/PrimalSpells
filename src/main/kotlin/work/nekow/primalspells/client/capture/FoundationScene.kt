package work.nekow.primalspells.client.capture

import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.*
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.client.screen.WandPreviewScreen
import java.util.concurrent.ConcurrentHashMap

/** 预览与真实抽牌/费用/冷却一致性、真实网络与界面回归。 */
object FoundationScene {
    fun scene(): Scene {
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        var floor = 0
        var id = ""
        var wand: Wand? = null
        fun record(t: Int, name: String, ok: Boolean, detail: String) { checks.getOrPut(t) { arrayListOf() }.add(Check("foundations", t, name, ok, detail)) }
        return Scene("foundations", "法术链预览 / 服务端真实解析", 24, 260, showHud = true, allowScreen = true,
            prepare = { ctx -> floor = ctx.floorBlockY(0, 0); ctx.buildStudio(0, 0, floor); ctx.setup(false, 0.0, floor + 2.0, -7.0, 0f, 5f); ctx.command("gamemode creative @a") },
            camera = { _, _ -> Pose(0.0, floor + 2.0, -7.0, 0f, 5f) },
            tick = { ctx, t ->
                if (t == 8) {
                    val stack = ItemStack(ModItems.WAND.get())
                    wand = WandManager[WandManager.createWand(stack)]
                    id = requireNotNull(wand).id
                    val cards = listOf("double_cast", "trigger_fireball", "omnidirectional_scatter", "chainsaw")
                    requireNotNull(wand).magics += cards.map { MagicManager.create(it)!! }; while (requireNotNull(wand).magics.size < 18) requireNotNull(wand).magics += null; requireNotNull(wand).load(); requireNotNull(wand).status.mana = 3000.0
                    stack.set(ModItems.WAND_SPELLS, cards + List(14) { "" })
                    ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack)
                    val before = requireNotNull(wand).saveTag().toString()
                    val p = requireNotNull(wand).preview()
                    record(t, "preview-is-read-only", before == requireNotNull(wand).saveTag().toString(), before)
                    val carrier = p.rows.first { it.id == "trigger_fireball" }
                    val saw = p.rows.first { it.id == "chainsaw" }
                    record(t, "payload-scope-is-isolated", "omnidirectional_scatter" !in carrier.modifiers && "omnidirectional_scatter" in saw.modifiers && saw.scope != carrier.scope, p.toString())
                    val recipes = listOf(cards, listOf("divide_ten", "speed_boost", "fireball"), listOf("divide_three", "trigger_fireball", "fireball"), listOf("cast_all", "fireball", "healing_bolt"), listOf("add_timer", "fireball", "sawblade"))
                    for ((i, recipe) in recipes.withIndex()) {
                        val w = Wand("preview-$i"); w.magics += recipe.map { MagicManager.create(it)!! }; w.load(); w.status.mana = 3000.0
                        val beforeMana = w.status.mana; val plan = w.preview()
                        record(t, "preview-tree-$i", plan.rows.isNotEmpty() && plan.rows.all { it.accepted } && plan.manaAfter <= beforeMana && plan.delay >= 0 && plan.recharge >= 0, plan.toString())
                        if (i == 1) record(t, "divide-copies-card-not-chain", plan.rows.count { it.id == "speed_boost" } == 10 && plan.rows.count { it.id == "fireball" } == 1, plan.toString())
                    }
                    val poor = Wand("poor"); poor.mana = 1.0; poor.status.mana = 1.0; poor.magics += MagicManager.create("concentrated_light")!!; poor.load()
                    record(t, "preview-reports-insufficient-mana", poor.preview().shots == 0 && poor.preview().reasons.any { it.startsWith("Mana") }, poor.preview().toString())
                }
                if (t == 65) {
                    val w = requireNotNull(wand); val plan = w.preview()
                    record(t, "real-cast-started", w.spell(ctx.player), "mana=${w.status.mana}")
                    record(t, "preview-matches-real-fees-cooldowns", kotlin.math.abs(w.status.mana - plan.manaAfter) < 1e-6 && w.status.delay == plan.delay && w.status.recharge == plan.recharge,
                        "real=${w.status.mana}/${w.status.delay}/${w.status.recharge}; preview=$plan")
                }
                if (t == 90) record(t, "floating-remove-to-cursor", requireNotNull(wand).magics[0] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("double_cast"), "cursor=${ctx.player.containerMenu.carried}")
                if (t == 100) {
                    record(t, "floating-insert-in-empty-slot", requireNotNull(wand).magics[4]?.id == "double_cast" && ctx.player.containerMenu.carried.isEmpty, "cards=${requireNotNull(wand).magics.map { it?.id }}")

                }
                if (t == 110) record(t, "creative-invalid-stack-is-rejected", requireNotNull(wand).magics[5] == null && ctx.player.containerMenu.carried.isEmpty, "cursor=${ctx.player.containerMenu.carried}")
                if (t == 120) record(t, "floating-swap-keeps-old-card", requireNotNull(wand).magics[4]?.id == "speed_boost" && ctx.player.containerMenu.carried.item == ModItems.spellItem("double_cast"), "cursor=${ctx.player.containerMenu.carried}")
                if (t == 135) record(t, "forged-id-cannot-edit", requireNotNull(wand).magics[6] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("double_cast"), "slot6=${requireNotNull(wand).magics[6]}")
                if (t == 160) { ctx.player.containerMenu.carried = ItemStack.EMPTY; ctx.player.containerMenu.broadcastFullState(); ctx.command("gamemode survival @a") }
                if (t == 190) record(t, "survival-remove-is-server-authoritative", requireNotNull(wand).magics[4] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("speed_boost"), ctx.player.containerMenu.carried.toString())
                if (t == 205) record(t, "survival-insert-consumes-cursor", requireNotNull(wand).magics[6]?.id == "speed_boost" && ctx.player.containerMenu.carried.isEmpty, ctx.player.containerMenu.carried.toString())
                if (t == 225) { ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); MagicManager.projectiles.forEach { it.alive = false } }
            },
            clientTick = { mc, t ->
                if (t == 20) mc.gui.setScreen(WandPreviewScreen(id, null))
                if (t == 35) record(t, "real-network-preview-received", (mc.gui.screen() as? WandPreviewScreen)?.preview?.rows?.isNotEmpty() == true, "screen=${mc.gui.screen()}")
                if (t == 55) mc.gui.setScreen(null)
                if (t == 80) mc.gui.setScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player!!))
                if (t == 83) {
                    val screen = mc.gui.screen() as net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
                    val slot = screen.menu.slots.first { it.item.get(ModItems.WAND_ID)?.wandId == id }
                    work.nekow.primalspells.client.input.WandEditorOverlay.open(screen, slot)
                }
                if (t == 102) {
                    // 散卡上限1；伪造数量3必须被拒绝，不能借创造光标通道绕过物品限制。
                    mc.player!!.containerMenu.carried = ItemStack(ModItems.spellItem("speed_boost")!!, 3)
                }
                if (t == 112) mc.player!!.containerMenu.carried = ItemStack(ModItems.spellItem("speed_boost")!!)
                if (t in listOf(86, 96, 105, 115, 180, 195)) {
                    val overlay = work.nekow.primalspells.client.input.WandEditorOverlay
                    val target = when(t) { 86 -> 0; 96 -> 4; 105 -> 5; 195 -> 6; else -> 4 }
                    val screen = mc.gui.screen()!!
                    val mouse = net.minecraft.client.input.MouseButtonEvent((overlay.x + 10 + target * 18).toDouble(), (overlay.y + 26).toDouble(), net.minecraft.client.input.MouseButtonInfo(0, 0))
                    val event = net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen, mouse, false)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event)
                    record(t, "real-overlay-click-$t", event.isCanceled, "cancelled=${event.isCanceled}; cursor=${mc.player!!.containerMenu.carried}; view=${(screen as net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>).menu.carried}")
                }
                if (t == 125) {
                    val screen = mc.gui.screen() as net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
                    val slot = screen.menu.slots.first { it.item.get(ModItems.WAND_ID)?.wandId == id }
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(work.nekow.primalspells.network.EditWandPayload(screen.menu.containerId, slot.index, "forged", 6))
                }
                if (t == 145) {
                    val overlay = work.nekow.primalspells.client.input.WandEditorOverlay
                    val event = net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.gui.screen()!!,
                        net.minecraft.client.input.MouseButtonEvent((overlay.x + 20).toDouble(), (overlay.y + 67).toDouble(), net.minecraft.client.input.MouseButtonInfo(0, 0)), false)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event)
                }
                if (t == 150) {
                    val screen = mc.gui.screen() as? WandPreviewScreen
                    record(t, "editor-opens-complete-preview", screen?.preview?.rows?.isNotEmpty() == true, "screen=$screen")
                    screen?.onClose()
                }
                if (t == 155 || t == 220) mc.gui.setScreen(null)
                if (t == 170) mc.gui.setScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player!!))
                if (t == 175) {
                    val screen = mc.gui.screen() as net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
                    work.nekow.primalspells.client.input.WandEditorOverlay.open(screen, screen.menu.slots.first { it.item.get(ModItems.WAND_ID)?.wandId == id })
                }
                if (t == 210) {
                    val overlay = work.nekow.primalspells.client.input.WandEditorOverlay
                    val screen = mc.gui.screen()!!; val oldX = overlay.x; val oldY = overlay.y
                    fun mouse(x: Int, y: Int) = net.minecraft.client.input.MouseButtonEvent(x.toDouble(), y.toDouble(), net.minecraft.client.input.MouseButtonInfo(0, 0))
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen, mouse(oldX + 20, oldY + 8), false))
                    val moved = net.neoforged.neoforge.client.event.ScreenEvent.MouseDragged.Pre(screen, mouse(oldX, oldY + 16), -20.0, 8.0)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(moved)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonReleased.Pre(screen, mouse(oldX, oldY + 16)))
                    record(t, "real-title-drag-moves-window", moved.isCanceled && overlay.x == oldX - 20 && overlay.y == oldY + 8, "${overlay.x},${overlay.y}")
                }
            },
            checksAt = { _, t -> checks[t]?.toList().orEmpty() }, finish = { ctx -> ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY) })
    }
}
