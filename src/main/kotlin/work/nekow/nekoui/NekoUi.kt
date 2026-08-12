package work.nekow.nekoui

import com.mojang.logging.LogUtils
import org.slf4j.Logger

/**
 * NekoUi 独立 UI 框架入口常量。
 *
 * [MODID]：宿主 mod 的资源命名空间（HTML 资源位于 `assets/primalspells/html/`）。
 * 本框架不依赖宿主逻辑，业务窗口（wand/pouch 子包）除外。
 */
object NekoUi {
    const val MODID = "primalspells"
    val LOGGER: Logger = LogUtils.getLogger()
}
