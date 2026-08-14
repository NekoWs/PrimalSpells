package work.nekow.primalspells

import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import work.nekow.primalspells.block.ModBlocks
import work.nekow.primalspells.client.ShieldHudRenderer
import work.nekow.primalspells.client.WandHudRenderer
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.entity.ModEntities
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.event.PouchEventHandler
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.ModMenus
import work.nekow.primalspells.item.SpellPouchItem
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.item.component.WandID
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.network.*
import work.nekow.primalspells.wand.WandManager

@Mod(PrimalSpells.MODID)
class PrimalSpells(bus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.register(WandEventHandler)
        NeoForge.EVENT_BUS.register(MagicManager)
        NeoForge.EVENT_BUS.register(WandManager)
        NeoForge.EVENT_BUS.register(PouchEventHandler)
        ModItems.COMPONENTS.register(bus)
        ModItems.ITEMS.register(bus)
        ModItems.TABS.register(bus)
        ModBlocks.BLOCKS.register(bus)
        ModMenus.MENU_TYPES.register(bus)
        ModDamageTypes.DAMAGE_TYPES.register(bus)
        ModEntities.ENTITY_TYPES.register(bus)

        // 注册无人机实体属性
        bus.addListener<EntityAttributeCreationEvent> { event ->
            event.put(ModEntities.DRONE.get(), DroneEntity.createAttributes().build())
            event.put(ModEntities.TEST_DUMMY.get(), TestDummyEntity.createAttributes().build())
        }

        bus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar("1.0.0")
            registrar.playToClient(
                SyncWandStatsPayload.ID,
                SyncWandStatsPayload.STREAM_CODEC
            ) { payload, _ ->
                WandHudRenderer.putStats(payload)
            }
            registrar.playToClient(
                SyncShieldPayload.ID,
                SyncShieldPayload.STREAM_CODEC
            ) { payload, _ ->
                ShieldHudRenderer.putShield(payload)
            }
            registrar.playToServer(
                SelectWandSlotPayload.ID,
                SelectWandSlotPayload.STREAM_CODEC
            ) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@playToServer
                val inv = player.inventory
                for (i in 0 until inv.containerSize) {
                    val st = inv.getItem(i)
                    if (st.item is WandItem && st.get(ModItems.WAND_ID)?.wandId == payload.wandId) {
                        st.set(ModItems.WAND_SELECTED_SLOT.get(), payload.slot)
                        break
                    }
                }
            }
            registrar.playToServer(
                EditPouchPayload.ID,
                EditPouchPayload.STREAM_CODEC
            ) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@playToServer
                val pouch = findPouch(player, payload.pouchId) ?: return@playToServer

                fun spells() = pouch.get(ModItems.SPELL_POUCH_CONTENTS.get())?.toMutableList() ?: mutableListOf()
                fun save(list: MutableList<String>) {
                    pouch.set(ModItems.SPELL_POUCH_CONTENTS.get(), list)
                }

                when (payload.action) {
                    EditPouchPayload.ACTION_ADD -> {
                        val list = spells()
                        while (list.size <= payload.slotA) list.add("")
                        if (list[payload.slotA].isEmpty()) {
                            list[payload.slotA] = payload.spellId
                            save(list)
                            // 消费玩家光标中对应的法术物品，避免复制
                            clearCarriedSpell(player, payload.spellId)
                    } else {
                        // 目标槽已有法术：交换（原法术物品进入玩家光标）
                        val old = list[payload.slotA]
                        list[payload.slotA] = payload.spellId
                        save(list)
                        clearCarriedSpell(player, payload.spellId)
                        ModItems.spellItem(old)?.let {
                            player.containerMenu.carried = ItemStack(it)
                        }
                    }
                    }
                    EditPouchPayload.ACTION_SWAP -> {
                        val list = spells()
                        val maxIdx = maxOf(payload.slotA, payload.slotB)
                        while (list.size <= maxIdx) list.add("")
                        val t = list[payload.slotA]
                        list[payload.slotA] = list[payload.slotB]
                        list[payload.slotB] = t
                        save(list)
                    }
                    EditPouchPayload.ACTION_REMOVE -> {
                        val list = spells()
                        if (payload.slotA in list.indices && list[payload.slotA].isNotEmpty()) {
                            val id = list[payload.slotA]
                            list[payload.slotA] = ""
                            save(list)
                            // 跨浮窗移动 / 创造模式删除：不发放物品
                            if (payload.silent) return@playToServer
                            ModItems.spellItem(id)?.let { placeOrGive(player, payload.slotB, ItemStack(it)) }                        }
                    }
                }
            }
            registrar.playToServer(
                EditWandPayload.ID,
                EditWandPayload.STREAM_CODEC
            ) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@playToServer
                val wand = WandManager[WandID(payload.wandId), null] ?: return@playToServer
                when (payload.action) {
                    EditWandPayload.ACTION_ADD -> {
                        val index = payload.slotA
                        while (wand.magics.size <= index) wand.magics.add(null)
                        if (wand.magics[index] == null) {
                            val magic = MagicManager.create(payload.spellId)
                            if (magic != null) {
                                wand.magics[index] = magic
                                wand.load()
                                // 消费玩家光标中对应的法术物品，避免复制
                                clearCarriedSpell(player, payload.spellId)
                            }
                        } else {
                            // 目标槽已有法术：交换（原法术物品进入玩家光标）
                            val old = wand.magics[index]
                            val oldItem = ModItems.spellItem(old!!.id)
                            wand.magics[index] = MagicManager.create(payload.spellId)!!
                            wand.load()
                            clearCarriedSpell(player, payload.spellId)
                            oldItem?.let {
                                player.containerMenu.carried = ItemStack(it)
                            }
                        }
                    }
                    EditWandPayload.ACTION_SWAP -> {
                        val a = payload.slotA
                        val b = payload.slotB
                        // 确保目标索引在列表内（空槽也可能从未被填充，需先扩展）
                        val maxIdx = maxOf(a, b)
                        while (wand.magics.size <= maxIdx) wand.magics.add(null)
                        val t = wand.magics[a]
                        wand.magics[a] = wand.magics[b]
                        wand.magics[b] = t
                        wand.load()
                    }
                    EditWandPayload.ACTION_REMOVE -> {
                        val index = payload.slotA
                        if (index in wand.magics.indices) {
                            val magic = wand.magics[index]
                            if (magic != null) {
                                wand.magics[index] = null
                                wand.load()
                                // 跨浮窗移动 / 创造模式删除：不发放物品
                                if (payload.silent) return@playToServer
                                ModItems.spellItem(magic.id)?.let { placeOrGive(player, payload.slotB, ItemStack(it)) }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 消费玩家光标中匹配的法术物品（防止 ADD 复制） */
    private fun clearCarriedSpell(player: ServerPlayer, spellId: String) {
        val carried = player.containerMenu.carried
        if (carried.isEmpty) return
        val item = ModItems.spellItem(spellId) ?: return
        if (carried.item == item) {
            player.containerMenu.setCarried(ItemStack.EMPTY)
        }
    }

    /**
     * 按 pouchId 在玩家背包/手持中定位小包（不依赖当前容器菜单，菜单切换不影响）。
     * 兜底：客户端对未初始化（无 ID）的小包发送空 pouchId 时，
     * 匹配唯一无 ID 的小包并补写 ID（保证后续操作按 ID 定位）。
     */
    private fun findPouch(player: ServerPlayer, pouchId: String): ItemStack? {
        val inv = player.inventory
        var fallback: ItemStack? = null
        for (i in 0 until inv.containerSize) {
            val st = inv.getItem(i)
            if (st.isEmpty || st.item !is SpellPouchItem) continue
            val id = SpellPouchItem.getPouchId(st)
            if (id == pouchId) return st
            if (id == null && fallback == null) fallback = st
        }
        if (pouchId.isEmpty() && fallback != null) {
            SpellPouchItem.ensurePouchId(fallback)
            return fallback
        }
        return null
    }

    /** 将物品放入目标容器槽：空槽放置 / 有物品槽交换 / 小包槽或越界回退背包 */
    private fun placeOrGive(player: ServerPlayer, targetSlot: Int, item: ItemStack) {
        // 创造模式兜底：直接丢弃（客户端已发 silent，此处防客户端能力状态延迟导致的回退）
        if (player.abilities.instabuild) return
        val menu = player.containerMenu
        val inRange = targetSlot in menu.slots.indices
        val targetStack = if (inRange) menu.getSlot(targetSlot).item else ItemStack.EMPTY
        when {
            // 目标槽是小包等容器物品：回退放入玩家背包，避免覆盖
            !targetStack.isEmpty && targetStack.item is SpellPouchItem ->
                giveOrCarry(player, item)
            // 目标槽为空：放置
            targetStack.isEmpty && inRange ->
                menu.getSlot(targetSlot).set(item)
            // 目标槽有物品：交换（原物品给玩家）
            inRange -> {
                val slot = menu.getSlot(targetSlot)
                giveOrCarry(player, slot.item)
                slot.set(item)
            }
            // 越界：回退背包
            else -> giveOrCarry(player, item)
        }
    }

    /** 优先放入玩家背包；背包满时回退到光标，确保物品不丢失 */
    private fun giveOrCarry(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        if (!player.addItem(stack)) {
            player.containerMenu.carried = stack
        }
    }

    companion object {
        const val MODID = "primalspells"
        val LOGGER: Logger = LogUtils.getLogger()

        fun Entity.debug(string: String, vararg args: Any) {
            if (this !is Player) return
            this.sendSystemMessage(Component.literal(string.format(*args)))
        }
        fun Entity.overlay(string: String, vararg args: Any) {
            if (this !is Player) return
            this.sendOverlayMessage(Component.literal(string.format(*args)))
        }
    }
}
