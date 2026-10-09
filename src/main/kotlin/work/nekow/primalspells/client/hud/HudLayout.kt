package work.nekow.primalspells.client.hud

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.logging.LogUtils
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.min

/** 三块客户端HUD的原生GUI尺寸；位置/缩放独立保存。 */
enum class HudElement(val width: Int, val height: Int, val labelKey: String) {
    MANA(126, 25, "hud.primalspells.mana"),
    COOLDOWN(100, 28, "hud.primalspells.cooldown"),
    FOCUS(90, 78, "hud.primalspells.focus"),
}

/** null位置使用随窗口变化的默认锚点；拖动后保存归一化中心坐标。 */
data class HudPlacement(var x: Double? = null, var y: Double? = null, var scale: Double = 1.0)

/** 仅客户端的布局草稿；编辑器复制后修改，保存前不影响正式配置。 */
data class HudLayout(
    val mana: HudPlacement = HudPlacement(),
    val cooldown: HudPlacement = HudPlacement(),
    val focus: HudPlacement = HudPlacement(),
    var hitFeedback: Boolean = true,
    var damageDirection: Boolean = true,
) {
    fun placement(element: HudElement) = when (element) {
        HudElement.MANA -> mana; HudElement.COOLDOWN -> cooldown; HudElement.FOCUS -> focus
    }
    fun duplicate() = HudLayout(mana.copy(), cooldown.copy(), focus.copy(), hitFeedback, damageDirection)

    /** 每次按当前窗口钳制实际边界，分辨率/GUI倍率变化不把组件丢在屏幕外。 */
    fun box(element: HudElement, width: Int, height: Int): HudBox {
        val p = placement(element)
        val requested = p.scale.takeIf { it.isFinite() }?.coerceIn(0.6, 2.5) ?: 1.0
        val scale = min(requested, min((width - 8.0).coerceAtLeast(1.0) / element.width,
            (height - 8.0).coerceAtLeast(1.0) / element.height))
        val cx = p.x?.takeIf { it.isFinite() }?.times(width) ?: when (element) {
            HudElement.MANA -> width / 2.0 - 46; HudElement.COOLDOWN -> width / 2.0 + 70; HudElement.FOCUS -> width / 2.0
        }
        val cy = p.y?.takeIf { it.isFinite() }?.times(height) ?: when (element) {
            HudElement.MANA -> height - 66.0; HudElement.COOLDOWN -> height - 65.0; HudElement.FOCUS -> height / 2.0 + 10
        }
        val w = element.width * scale; val h = element.height * scale
        return HudBox((cx - w / 2).coerceIn(4.0, (width - w - 4).coerceAtLeast(4.0)),
            (cy - h / 2).coerceIn(4.0, (height - h - 4).coerceAtLeast(4.0)), w, h, scale)
    }

    /** 编辑器拖动入口，以组件中心保存而非依赖某次窗口的像素坐标。 */
    fun move(element: HudElement, cx: Double, cy: Double, width: Int, height: Int) {
        val p = placement(element)
        p.x = (cx / width.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        p.y = (cy / height.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val b = box(element, width, height)
        p.x = (b.x + b.width / 2) / width.coerceAtLeast(1)
        p.y = (b.y + b.height / 2) / height.coerceAtLeast(1)
    }
}

/** 绘制与鼠标命中共用的屏幕边界，避免缩放后选框错位。 */
data class HudBox(val x: Double, val y: Double, val width: Double, val height: Double, val scale: Double) {
    fun contains(mx: Double, my: Double) = mx >= x && mx <= x + width && my >= y && my <= y + height
}

/** 本机客户端JSON配置；原子保存，读取坏文件时保留原文件并采用默认布局。 */
object HudSettings {
    private val logger = LogUtils.getLogger()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    val path: Path get() = FMLPaths.CONFIGDIR.get().resolve("primalspells-hud.json")
    var current: HudLayout = load(path)
        private set

    /** 从指定配置文件读取；也供开发录制使用隔离文件验证读写。 */
    fun load(file: Path): HudLayout = runCatching {
        if (!Files.exists(file)) return@runCatching HudLayout()
        val root = JsonParser.parseString(Files.readString(file)).asJsonObject
        fun placement(key: String): HudPlacement {
            val p = root.getAsJsonObject(key) ?: return HudPlacement()
            fun number(name: String) = p.get(name)?.takeUnless { it.isJsonNull }?.asDouble?.takeIf { it.isFinite() }
            return HudPlacement(number("x")?.coerceIn(0.0, 1.0), number("y")?.coerceIn(0.0, 1.0),
                (number("scale") ?: 1.0).coerceIn(0.6, 2.5))
        }
        HudLayout(placement("mana"), placement("cooldown"), placement("focus"),
            root.get("hitFeedback")?.asBoolean ?: true, root.get("damageDirection")?.asBoolean ?: true)
    }.getOrElse { logger.warn("无法读取HUD布局，采用默认布局并保留原文件：{}", file, it); HudLayout() }

    /** 成功落盘才替换当前配置；失败由编辑器显示，不静默丢掉用户布局。 */
    fun save(layout: HudLayout, file: Path = path): Boolean = runCatching {
        Files.createDirectories(file.parent)
        val root = JsonObject()
        HudElement.entries.forEach { element ->
            val p = layout.placement(element); val node = JsonObject()
            p.x?.takeIf { it.isFinite() }?.let { node.addProperty("x", it.coerceIn(0.0, 1.0)) }
            p.y?.takeIf { it.isFinite() }?.let { node.addProperty("y", it.coerceIn(0.0, 1.0)) }
            node.addProperty("scale", if (p.scale.isFinite()) p.scale.coerceIn(0.6, 2.5) else 1.0)
            root.add(element.name.lowercase(), node)
        }
        root.addProperty("version", 1); root.addProperty("hitFeedback", layout.hitFeedback)
        root.addProperty("damageDirection", layout.damageDirection)
        val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temporary, gson.toJson(root))
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING) }
        if (file == path) current = load(file)
        true
    }.getOrElse { logger.error("无法保存HUD布局：{}", file, it); false }
}
