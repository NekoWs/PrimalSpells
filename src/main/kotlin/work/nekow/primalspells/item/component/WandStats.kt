package work.nekow.primalspells.item.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.component.DataComponentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class WandStats(
    val mana: Double,
    val maxMana: Double,
    val delay: Int,
    val recharge: Int,
    val cast: Int
) {
    companion object {
        val CODEC: Codec<WandStats> = RecordCodecBuilder.create { it.group(
            Codec.DOUBLE.fieldOf("mana").forGetter(WandStats::mana),
            Codec.DOUBLE.fieldOf("max_mana").forGetter(WandStats::maxMana),
            Codec.INT.fieldOf("delay").forGetter(WandStats::delay),
            Codec.INT.fieldOf("recharge").forGetter(WandStats::recharge),
            Codec.INT.fieldOf("cast").forGetter(WandStats::cast)
        ).apply(it, ::WandStats) }

        val STREAM_CODEC: StreamCodec<ByteBuf, WandStats> = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, WandStats::mana,
            ByteBufCodecs.DOUBLE, WandStats::maxMana,
            ByteBufCodecs.VAR_INT, WandStats::delay,
            ByteBufCodecs.VAR_INT, WandStats::recharge,
            ByteBufCodecs.VAR_INT, WandStats::cast,
            ::WandStats
        )

        val TYPE: DataComponentType<WandStats> = DataComponentType.builder<WandStats>()
            .persistent(CODEC)
            .networkSynchronized(STREAM_CODEC)
            .cacheEncoding()
            .build()
    }
}
