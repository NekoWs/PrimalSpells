package work.nekow.primalspells.wand

import com.mojang.serialization.Codec
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

class WandSavedData : SavedData() {
    val wandTags = mutableMapOf<String, CompoundTag>()

    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        val list = ListTag()
        wandTags.forEach { (id, wandTag) ->
            val entry = CompoundTag()
            entry.putString("id", id)
            entry.put("wand", wandTag)
            list.add(entry)
        }
        tag.put("wands", list)
        return tag
    }

    companion object {
        private val ID = Identifier.fromNamespaceAndPath("primalspells", "wands")

        val CODEC: Codec<WandSavedData> = CompoundTag.CODEC.xmap(
            { tag -> fromTag(tag) },
            { data -> data.toTag() }
        )

        val TYPE: SavedDataType<WandSavedData> = SavedDataType(ID, ::WandSavedData, CODEC)

        fun fromTag(tag: CompoundTag): WandSavedData {
            val data = WandSavedData()
            tag.getListOrEmpty("wands").forEach { entry ->
                val c = entry as CompoundTag
                data.wandTags[c.getStringOr("id", "")] = c.getCompoundOrEmpty("wand")
            }
            return data
        }

        fun getOrCreate(level: ServerLevel): WandSavedData =
            level.dataStorage.computeIfAbsent(TYPE)
    }
}
