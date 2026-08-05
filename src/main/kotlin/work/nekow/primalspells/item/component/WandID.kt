package work.nekow.primalspells.item.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.component.DataComponentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class WandID(val wandId: String) {
    companion object {
        val CODEC: Codec<WandID> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("wand_id").forGetter(WandID::wandId)
            ).apply(it, ::WandID)
        }
        val STREAM_CODEC: StreamCodec<ByteBuf, WandID> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WandID::wandId,
            ::WandID
        )
        val TYPE: DataComponentType<WandID> = DataComponentType.builder<WandID>()
            .persistent(CODEC)
            .networkSynchronized(STREAM_CODEC)
            .cacheEncoding()
            .build()
    }
}