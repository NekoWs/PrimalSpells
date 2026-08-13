package work.nekow.primalspells.entity

import com.mojang.math.Transformation
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.TextColor
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import work.nekow.primalspells.utils.Lore

/**
 * 头顶伤害数字：基于 [Display.TextDisplay] 的文本实体。
 *
 * - 始终面向玩家（billboard = center）
 * - 跳出动画：先快后慢（ease-out），缩放从 0.35 逐渐变大到 1.0，同时向上飘动
 * - 明亮红色加粗，便于阅读
 * - 约 1.5 秒后消失
 *
 * 26.2 的 Display/TextDisplay 所有 setter 均为私有，唯一数据入口是
 * `readAdditionalSaveData(ValueInput)`（protected，子类可用），故每 tick 构造
 * CompoundTag + [TagValueInput] 重新加载（transformation 每 tick 更新，
 * 客户端经 entityData 同步 + interpolation_duration 平滑插值）。
 */
class DamageNumber(level: Level, pos: Vec3, amount: Double) :
    Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level) {

    private var life = 0

    /** 显示数据标签：text/background/billboard 固定，transformation 每 tick 更新 */
    private val data = CompoundTag().apply {
        val text = Component.literal("-" + Lore.formatDouble(amount))
            .withStyle { it.withColor(TextColor.fromRgb(BRIGHT_RED)).withBold(true) }
        ComponentSerialization.CODEC
            .encodeStart(NbtOps.INSTANCE, text)
            .result()
            .ifPresent { put("text", it) }
        putInt("background", 0)              // 透明背景
        putString("billboard", "center")     // 始终面向玩家
        putInt("interpolation_duration", 8)  // 客户端对 transformation 变化做平滑插值
        putInt("start_interpolation", 0)
    }

    init {
        setPos(pos.x, pos.y, pos.z)
        applyTransformation(0)
        if (level() is ServerLevel) {
            reloadData(level() as ServerLevel)
        }
    }

    override fun tick() {
        super.tick()
        if (++life > 30) {
            discard()
            return
        }
        applyTransformation(life)
        val serverLevel = level()
        if (serverLevel is ServerLevel) {
            reloadData(serverLevel)
        }
    }

    private fun reloadData(level: ServerLevel) {
        readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data))
    }

    /** 先快后慢：缩放 ease-out 逐渐变大 + 向上飘动（走 transformation，随 entityData 同步） */
    private fun applyTransformation(t: Int) {
        val grow = (t / 12f).coerceAtMost(1f)
        val easeOut = 1f - (1f - grow) * (1f - grow)
        val scale = 0.35f + 0.65f * easeOut
        val riseP = (t / 30f).coerceAtMost(1f)
        val rise = 0.9f * (1f - (1f - riseP) * (1f - riseP))
        val transformation = Transformation(
            Vector3f(0f, rise, 0f),
            Quaternionf(),
            Vector3f(scale, scale, scale),
            Quaternionf()
        )
        Transformation.EXTENDED_CODEC
            .encodeStart(NbtOps.INSTANCE, transformation)
            .result()
            .ifPresent { data.put("transformation", it) }
    }

    companion object {
        /** 明亮红色（默认 ChatFormatting.RED 偏暗，此处加亮便于阅读） */
        private val BRIGHT_RED: Int = 0xFFFF4545.toInt()
    }
}

/** 在 [level] 的 [pos] 处生成一个向上飘动、逐渐变大的伤害数字 */
fun spawnDamageNumber(level: ServerLevel, pos: Vec3, amount: Double) {
    level.addFreshEntity(DamageNumber(level, pos, amount))
}
