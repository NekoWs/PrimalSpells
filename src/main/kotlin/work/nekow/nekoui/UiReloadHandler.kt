package work.nekow.nekoui

import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent

/**
 * 资源重载（F3+T）完成后，刷新当前打开的 [UiScreen]，
 * 使修改过的 HTML 文件立即生效，无需重启游戏。
 * 由 [work.nekow.primalspells.client.PrimalSpellsClient] 注册到 NeoForge.EVENT_BUS。
 */
object UiReloadHandler {

    @SubscribeEvent
    fun onResourceReload(event: ClientResourceLoadFinishedEvent) {
        val screen = Minecraft.getInstance().gui.screen()
        if (screen is UiScreen) screen.reload()
    }
}
