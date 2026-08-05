package work.nekow.primalspells.wand

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import work.nekow.primalspells.item.ModItems
import work.nekow.primalspells.item.component.WandID
import java.util.*

object WandManager {
    val wands = hashMapOf<WandID, Wand>()

    fun save(level: ServerLevel) {
        val data = WandSavedData.getOrCreate(level)
        wands.forEach { (id, wand) ->
            data.wandTags[id.wandId] = wand.saveTag()
        }
        data.isDirty = true
    }

    fun load(level: ServerLevel) {
        val data = WandSavedData.getOrCreate(level)
        data.wandTags.forEach { (id, tag) ->
            wands[WandID(id)] = Wand.fromTag(tag)
        }
    }

    fun tick() {
        wands.forEach { (_, wand) -> wand.tick() }
    }

    fun createWand(stack: ItemStack): WandID {
        val existing = stack.get(ModItems.WAND_ID)
        if (existing != null) {
            if (existing !in wands) wands[existing] = Wand(existing.wandId)
            return existing
        }
        val wandId = WandID(UUID.randomUUID().toString())
        stack.set(ModItems.WAND_ID, wandId)
        wands[wandId] = Wand(wandId.wandId)
        return wandId
    }

    fun remove(key: WandID) = wands.remove(key)

    operator fun get(key: WandID) = wands[key] ?: throw NullPointerException("Key ${key.wandId} is not exists!")
    operator fun get(key: WandID, default: Wand? = null) = wands[key] ?: default

    operator fun set(key: WandID, wand: Wand) = wands.set(key, wand)

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        load(event.server.overworld())
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        save(event.server.overworld())
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Pre) {
        tick()
    }
}
