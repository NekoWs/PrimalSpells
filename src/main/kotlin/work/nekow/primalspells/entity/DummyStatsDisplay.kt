package work.nekow.primalspells.entity

import com.mojang.math.Transformation
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.storage.TagValueInput
import org.joml.Quaternionf
import org.joml.Vector3f
import work.nekow.primalspells.util.Lore

/** 假人头顶的双行统计板；原版TextDisplay同步，随靶子销毁，不写入存档。 */
class DummyStatsDisplay(private val owner: TestDummyEntity, level: ServerLevel) :
    Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level) {
    private var previous = ""

    /** 每四刻同步DPS和累计伤害，避免高频命中造成大量重复文本更新。 */
    fun update(dps: Double, total: Double) {
        val signature = "${Lore.formatDouble(dps)}/${Lore.formatDouble(total)}"
        if (signature == previous) return
        previous = signature
        val data = CompoundTag().apply {
            ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE,
                Component.translatable("entity.primalspells.dummy.stats", Lore.formatDouble(dps), Lore.formatDouble(total)))
                .result().ifPresent { put("text", it) }
            putInt("background", 0x600b1420)
            putString("billboard", "center")
            putInt("line_width", 240)
            Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,
                Transformation(Vector3f(), Quaternionf(), Vector3f(0.65f), Quaternionf()))
                .result().ifPresent { put("transformation", it) }
        }
        val level = level() as ServerLevel
        readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data))
    }

    override fun tick() {
        super.tick()
        if (owner.isRemoved || !owner.isAlive || owner.level() !== level()) { discard(); return }
        setPos(owner.x, owner.y + owner.bbHeight + 1.7, owner.z)
    }

    override fun shouldBeSaved(): Boolean = false
}
