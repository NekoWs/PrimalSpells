package work.nekow.primalspells.network

/**
 * 公共侧 → 客户端表现层的**间接分发点**。
 *
 * 为什么需要它：S→C 网络包必须在公共入口（`PrimalSpells`）注册 codec 与处理器，
 * 但处理逻辑本身要用客户端类（HUD 渲染器）。若公共类直接 `import` 客户端类，
 * 专用服务器加载该入口时就可能触碰客户端类（`NoClassDefFoundError` 风险）。
 * 所以这里只放可空回调，由客户端入口 `client/PrimalSpellsClient` 在初始化时安装实现；
 * 在专用服务器上它们保持 `null`，处理器什么也不做。
 */
object ClientPayloadSink {
    /** 真实法术命中反馈。 */
    var hudImpact: ((HudImpactPayload) -> Unit)? = null
    /** 成功施法后的晶体模型点亮。 */
    var wandCast: ((WandCastPayload) -> Unit)? = null


    /** 法杖数值同步（`SyncWandStatsPayload`）的客户端处理；由客户端入口安装。 */
    var wandStats: ((SyncWandStatsPayload) -> Unit)? = null

    /** 镜头冲击（`ScreenShakePayload`）的客户端处理；由客户端入口安装。 */
    var screenShake: ((ScreenShakePayload) -> Unit)? = null
}
