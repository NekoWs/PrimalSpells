package work.nekow.primalspells.client.render

import work.nekow.particledrawing.api.ParticleManager

/**
 * 本模组的**自定义粒子贴图**登记处（ParticleDrawing `registerTexture`）。
 *
 * 为什么需要：PD 1.0.13 内置形状只有 `SQUARE`（硬边方块）/`SOFT_DOT`（柔边圆点）/`LINE`（线段）。
 * 用户口径（第 45 轮）要的是「**清晰的轮廓 + MC 魔法感**」——柔边圆点读不出轮廓、方块又没有魔法感，
 * 所以这里注册一张自绘的 **16×16 符文菱形**（`textures/particle/rune_spark.png`）：
 * 方形菱形轮廓 + 白热内芯 + 一圈很窄的柔化边（只为消锯齿，不是光晕）。
 *
 * ## 尺寸口径
 *
 * 贴图是 16×16 ⇒ `texScale = 1`（PD 以 16px 为基准）⇒ `anisoWorld(w, h)` 的 `w/h`
 * 就是**世界格整宽**；菱形本体只占贴图的 14/16，所以「看着的直径 ≈ 0.85 × 传入值」。
 *
 * ## 时机与归属
 *
 * 与内置贴图同理：`registerTexture` 在本机是客户端时会**就地解码**（碰 `RenderSystem`），
 * 所以由 [work.nekow.primalspells.client.PrimalSpellsClient] 在**首个客户端 tick** 里调一次
 * （不能放 `FMLClientSetupEvent`，第 29 轮踩过 `Rendersystem called from wrong thread`）。
 *
 * ⚠️ **同名第一次生效**（PD 幂等语义）：改图请换名字，否则旧图会被缓存住。
 */
object ParticleTextures {

    private val LOG = org.slf4j.LoggerFactory.getLogger("primalspells-particles")

    /** 符文菱形贴图名（引用处用这个名字，不要写字面量） */
    const val RUNE_SPARK = "primalspells:rune_spark"

    /** 贴图在 jar 内的路径 */
    private const val RUNE_SPARK_PATH = "/assets/primalspells/textures/particle/rune_spark.png"

    /**
     * 登记本模组的全部自定义贴图。幂等：重复调用无副作用（PD 按「同名第一次」处理）。
     *
     * 读不到资源时**只记日志不抛异常** —— 缺一张贴图不该让玩家进不了世界
     * （PD 对未登记的名字会回落纯白方块，粒子照常出现，只是形状退化）。
     */
    fun register() {
        registerAsset(RUNE_SPARK, RUNE_SPARK_PATH)
    }

    private fun registerAsset(name: String, path: String) {
        val bytes = ParticleTextures::class.java.getResourceAsStream(path)?.use { it.readBytes() }
        if (bytes == null) {
            LOG.warn("[particle] 贴图缺失：{}（{}）—— 该形状会回落纯白方块", name, path)
            return
        }
        ParticleManager.registerTexture(name, bytes)
    }
}
