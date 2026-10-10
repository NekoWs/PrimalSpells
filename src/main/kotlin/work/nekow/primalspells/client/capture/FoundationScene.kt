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
        var chest: net.minecraft.world.SimpleContainer? = null
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
                    record(t, "layout-isolates-payload-and-empty-slots", p.slots[0].scope == p.slots[1].scope && p.slots[1].scope != p.slots[2].scope && p.slots[2].scope == p.slots[3].scope && p.slots.drop(4).all { it.scope.isEmpty() }, p.slots.toString())
                    val all = Wand("layout-all"); all.magics += listOf("fireball", "fireball", "trigger_fireball", "fireball", "fireball").map { MagicManager.create(it)!! }; all.load(); all.status.mana = 0.0
                    val scopes = all.preview().slots.take(5).map { it.scope }
                    record(t, "layout-covers-all-casts-with-duplicate-cards-and-zero-mana", scopes.toSet().size == 5 && scopes[3].startsWith(scopes[2] + "/"), scopes.toString())
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
                if (t == 53) record(t, "creative-right-click-removes-card", requireNotNull(wand).magics[0] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("double_cast"), ctx.player.containerMenu.carried.toString())
                if (t == 60) record(t, "creative-right-click-inserts-card", requireNotNull(wand).magics[0]?.id == "double_cast" && ctx.player.containerMenu.carried.isEmpty, ctx.player.containerMenu.carried.toString())
                if (t == 65) {
                    val w = requireNotNull(wand); val plan = w.preview()
                    record(t, "real-cast-started", w.spell(ctx.player), "mana=${w.status.mana}")
                    record(t, "preview-matches-real-fees-cooldowns", kotlin.math.abs(w.status.mana - plan.manaAfter) < 1e-6 && w.status.delay == plan.delay && w.status.recharge == plan.recharge,
                        "real=${w.status.mana}/${w.status.delay}/${w.status.recharge}; preview=$plan")
                }
                if (t == 80) {
                    ctx.command("gamemode survival @a")
                    ctx.player.inventory.setItem(9, ItemStack(ModItems.spellItem("speed_boost")!!))
                    ctx.player.containerMenu.broadcastFullState()
                }
                if (t == 95) record(t, "wheel-wraps-to-last-slot", ctx.player.mainHandItem.get(ModItems.WAND_SELECTED_SLOT) == 17, ctx.player.mainHandItem.toString())
                if (t == 102) record(t, "wheel-selection-reaches-server", ctx.player.mainHandItem.get(ModItems.WAND_SELECTED_SLOT) == 0, ctx.player.mainHandItem.toString())
                if (t == 110) record(t, "vanilla-right-click-removes-card", requireNotNull(wand).magics[0] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("double_cast"), ctx.player.containerMenu.carried.toString())
                if (t == 120) record(t, "wheel-works-with-carried-card", ctx.player.mainHandItem.get(ModItems.WAND_SELECTED_SLOT) == 4, ctx.player.mainHandItem.toString())
                if (t == 130) record(t, "vanilla-right-click-inserts-card", requireNotNull(wand).magics[4]?.id == "double_cast" && ctx.player.containerMenu.carried.isEmpty, ctx.player.containerMenu.carried.toString())
                if (t == 150) record(t, "vanilla-right-click-swap-keeps-old-card", requireNotNull(wand).magics[4]?.id == "speed_boost" && ctx.player.containerMenu.carried.item == ModItems.spellItem("double_cast"), ctx.player.containerMenu.carried.toString())
                if (t == 160) record(t, "vanilla-inventory-receives-returned-card", ctx.player.inventory.getItem(9).item == ModItems.spellItem("double_cast") && ctx.player.containerMenu.carried.isEmpty, ctx.player.inventory.getItem(9).toString())
                if (t == 195) record(t, "editing-still-works-after-preview", requireNotNull(wand).magics[4] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("speed_boost"), ctx.player.containerMenu.carried.toString())
                if (t == 210) record(t, "roundtrip-preserves-card-count", requireNotNull(wand).magics[4]?.id == "speed_boost" && ctx.player.containerMenu.carried.isEmpty && ctx.player.inventory.getItem(9).item == ModItems.spellItem("double_cast"), requireNotNull(wand).magics.map { it?.id }.toString())
                if (t == 213) {
                    val box = net.minecraft.world.SimpleContainer(27); chest = box
                    box.setItem(0, ctx.player.mainHandItem); ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                    ctx.player.openMenu(net.minecraft.world.SimpleMenuProvider({ menuId, inventory, _ -> net.minecraft.world.inventory.ChestMenu.threeRows(menuId, inventory, box) }, net.minecraft.network.chat.Component.literal("Wand editing test")))
                }
                if (t == 220) record(t, "chest-wheel-selection-reaches-server", chest!!.getItem(0).get(ModItems.WAND_SELECTED_SLOT) == 5, chest.getItem(0).toString())
                if (t == 230) record(t, "chest-vanilla-right-click-removes-card", requireNotNull(wand).magics[4] == null && ctx.player.containerMenu.carried.item == ModItems.spellItem("speed_boost"), ctx.player.containerMenu.carried.toString())
                if (t == 240) record(t, "chest-vanilla-right-click-inserts-card", requireNotNull(wand).magics[4]?.id == "speed_boost" && ctx.player.containerMenu.carried.isEmpty, ctx.player.containerMenu.carried.toString())
                if (t == 250) { chest?.setItem(0, ItemStack.EMPTY); ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); ctx.player.inventory.setItem(9, ItemStack.EMPTY); MagicManager.projectiles.forEach { it.alive = false } }
            },
            clientTick = { mc, t ->
                fun inventory() = mc.gui.screen() as net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
                fun wandSlot() = inventory().menu.slots.first { it.item.get(ModItems.WAND_ID)?.wandId == id }
                fun click(slot: net.minecraft.world.inventory.Slot, button: Int) {
                    val screen = inventory()
                    val mouse = net.minecraft.client.input.MouseButtonEvent((screen.getLeftPos() + slot.x + 8).toDouble(),
                        (screen.getTopPos() + slot.y + 8).toDouble(), net.minecraft.client.input.MouseButtonInfo(button, 0))
                    val event = net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen, mouse, false)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event)
                    val creative = screen is net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen && button == 1
                    record(t, "right-click-route-$t", event.isCanceled == creative, "creative=$creative; cancelled=${event.isCanceled}")
                    if (!event.isCanceled) { screen.mouseClicked(mouse, false); screen.mouseReleased(mouse) }
                }
                fun wheel(delta: Double) {
                    val screen = inventory(); val slot = wandSlot()
                    net.minecraft.client.gui.screens.inventory.AbstractContainerScreen::class.java.getDeclaredField("hoveredSlot").apply { isAccessible = true }.set(screen, slot)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre(screen,
                        (screen.getLeftPos() + slot.x + 8).toDouble(), (screen.getTopPos() + slot.y + 8).toDouble(), 0.0, delta))
                }
                if (t == 20) net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_V))
                if (t == 35) record(t, "world-v-opens-real-network-preview", (mc.gui.screen() as? WandPreviewScreen)?.preview?.rows?.isNotEmpty() == true, "screen=${mc.gui.screen()}")
                if (t == 40 || t == 61) mc.gui.setScreen(null)
                if (t == 45) mc.gui.setScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player!!))
                if (t == 85) mc.gui.setScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player!!))
                if (t == 90) wheel(1.0)
                if (t == 97) wheel(-1.0)
                if (t in listOf(48, 55, 105, 125, 145, 185, 200, 225, 235)) click(wandSlot(), 1)
                if (t == 115) repeat(4) { wheel(-1.0) }
                if (t == 135 || t == 155) click(inventory().menu.slots.first { it.container === mc.player!!.inventory && it.slotIndex == 9 }, 0)
                if (t == 165) {
                    val screen = inventory(); net.minecraft.client.gui.screens.inventory.AbstractContainerScreen::class.java.getDeclaredField("hoveredSlot").apply { isAccessible = true }.set(screen, wandSlot())
                    val key = net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(screen, net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0))
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(key)
                    record(t, "inventory-v-opens-preview", key.isCanceled && mc.gui.screen() is WandPreviewScreen, "screen=${mc.gui.screen()}")
                }
                if (t == 175) {
                    val screen = mc.gui.screen() as? WandPreviewScreen
                    record(t, "inventory-preview-uses-edited-recipe", screen?.preview?.slots?.getOrNull(4)?.id == "speed_boost", screen?.preview.toString())
                }
                if (t == 180) (mc.gui.screen() as? WandPreviewScreen)?.onClose()
                if (t == 201) {
                    val stack = wandSlot().item
                    work.nekow.primalspells.client.tooltip.WandPreviewCache.slots(id, stack.get(ModItems.WAND_SPELLS).orEmpty())
                }
                if (t == 215) {
                    val slots = work.nekow.primalspells.client.tooltip.WandPreviewCache.slots(id, wandSlot().item.get(ModItems.WAND_SPELLS).orEmpty())
                    record(t, "tooltip-receives-complete-slot-colors", slots.size == 18 && slots[1].scope != slots[2].scope && slots[2].scope == slots[3].scope && slots[5].scope.isEmpty(), slots.toString())
                }
                if (t == 216) wheel(-1.0)
                if (t == 221) wheel(1.0)
                if (t == 245) mc.player!!.closeContainer()
            },
            checksAt = { _, t -> checks[t]?.toList().orEmpty() }, finish = { ctx -> ctx.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY) })
    }
}
