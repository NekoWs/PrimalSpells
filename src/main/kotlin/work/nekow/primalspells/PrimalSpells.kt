package work.nekow.primalspells

import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.nekow.primalspells.client.WandHudRenderer
import work.nekow.primalspells.block.ModBlocks
import work.nekow.primalspells.entity.DroneEntity
import work.nekow.primalspells.entity.ModEntities
import work.nekow.primalspells.event.WandEventHandler
import work.nekow.primalspells.event.PouchEventHandler
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.ModMenus
import work.nekow.primalspells.magic.MagicManager
import work.nekow.primalspells.network.EditPouchPayload
import work.nekow.primalspells.network.EditWandPayload
import work.nekow.primalspells.network.SyncWandStatsPayload
import work.nekow.primalspells.item.component.WandID
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
        }

        bus.addListener<RegisterPayloadHandlersEvent> { event ->
            val registrar = event.registrar("1.0.0")
            registrar.playToClient(
                SyncWandStatsPayload.ID,
                SyncWandStatsPayload.STREAM_CODEC
            ) { payload, _ ->
                WandHudRenderer.putStats(payload)
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
                            val oldItem = work.nekow.primalspells.item.ModItems.MAGICS[old]?.get()
                            if (oldItem != null) {
                                player.containerMenu.setCarried(ItemStack(oldItem))
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
                            // 跨浮窗移动：不发放物品
                            if (payload.silent) return@playToServer
                            val item = work.nekow.primalspells.item.ModItems.MAGICS[id]?.get()
                            if (item != null) {
                                val menu = player.containerMenu
                                val targetStack = if (payload.slotB in 0 until menu.slots.size)
                                    menu.getSlot(payload.slotB).item else ItemStack.EMPTY
                                when {
                                    // 目标槽是小包等非空容器物品：回退放入玩家背包
                                    !targetStack.isEmpty &&
                                        targetStack.item is work.nekow.primalspells.item.SpellPouchItem ->
                                        giveOrCarry(player, ItemStack(item))
                                    // 目标槽为空：放置
                                    targetStack.isEmpty && payload.slotB in 0 until menu.slots.size ->
                                        menu.getSlot(payload.slotB).set(ItemStack(item))
                                    // 目标槽有物品：交换（原物品给玩家）
                                    payload.slotB in 0 until menu.slots.size -> {
                                        val slot = menu.getSlot(payload.slotB)
                                        giveOrCarry(player, slot.item)
                                        slot.set(ItemStack(item))
                                    }
                                    else -> giveOrCarry(player, ItemStack(item))
                                }
                            }
                        }
                    }
                }
            }
            registrar.playToServer(
                EditWandPayload.ID,
                EditWandPayload.STREAM_CODEC
            ) { payload, context ->
                val player = context.player() as? ServerPlayer ?: return@playToServer
                val wand = WandManager.get(WandID(payload.wandId), null) ?: return@playToServer
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
                            val oldItem = work.nekow.primalspells.item.ModItems.MAGICS[old!!.id]?.get()
                            wand.magics[index] = MagicManager.create(payload.spellId)!!
                            wand.load()
                            clearCarriedSpell(player, payload.spellId)
                            if (oldItem != null) {
                                player.containerMenu.setCarried(ItemStack(oldItem))
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
                                // 跨浮窗移动：不发放物品
                                if (payload.silent) return@playToServer
                                val item = work.nekow.primalspells.item.ModItems.MAGICS[magic.id]?.get()
                                if (item != null) {
                                    val menu = player.containerMenu
                                    val target = payload.slotB
                                    val targetStack = if (target in 0 until menu.slots.size)
                                        menu.getSlot(target).item else ItemStack.EMPTY
                                    when {
                                        // 目标槽是小包等容器物品：回退放入玩家背包，避免覆盖
                                        !targetStack.isEmpty &&
                                            targetStack.item is work.nekow.primalspells.item.SpellPouchItem ->
                                            giveOrCarry(player, ItemStack(item))
                                        // 目标槽为空：放置
                                        targetStack.isEmpty && target in 0 until menu.slots.size ->
                                            menu.getSlot(target).set(ItemStack(item))
                                        // 目标槽有物品：交换（原物品给玩家）
                                        target in 0 until menu.slots.size -> {
                                            val slot = menu.getSlot(target)
                                            giveOrCarry(player, slot.item)
                                            slot.set(ItemStack(item))
                                        }
                                        else -> giveOrCarry(player, ItemStack(item))
                                    }
                                }
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
        val item = work.nekow.primalspells.item.ModItems.MAGICS[spellId]?.get() ?: return
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
            if (st.isEmpty || st.item !is work.nekow.primalspells.item.SpellPouchItem) continue
            val id = work.nekow.primalspells.item.SpellPouchItem.getPouchId(st)
            if (id == pouchId) return st
            if (id == null && fallback == null) fallback = st
        }
        if (pouchId.isEmpty() && fallback != null) {
            work.nekow.primalspells.item.SpellPouchItem.ensurePouchId(fallback)
            return fallback
        }
        return null
    }

    /** 优先放入玩家背包；背包满时回退到光标，确保物品不丢失 */
    private fun giveOrCarry(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        if (!player.addItem(stack)) {
            player.containerMenu.setCarried(stack)
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
