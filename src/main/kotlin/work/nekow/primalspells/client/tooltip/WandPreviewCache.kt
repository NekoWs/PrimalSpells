package work.nekow.primalspells.client.tooltip

import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import work.nekow.primalspells.network.WandPreviewPayload
import work.nekow.primalspells.wand.WandPreview

/** 仅缓存最近悬停的一根杖；分色由服务端真实抽牌器给出，不在客户端解析卡牌。 */
object WandPreviewCache {
    private var id = ""
    private var cards = emptyList<String>()
    private var value: WandPreview? = null
    private var requested = Long.MIN_VALUE
    private val palette = intArrayOf(0xFF534073.toInt(), 0xFF285D62.toInt(), 0xFF735D32.toInt(),
        0xFF3D526F.toInt(), 0xFF72434C.toInt(), 0xFF45633D.toInt())

    /** 悬停时节流请求，装卸后清空旧配方颜色；不阻止原版tooltip或背包输入。 */
    fun slots(wandId: String, spells: List<String>): List<WandPreview.Slot> {
        val now = Minecraft.getInstance().level?.gameTime ?: return emptyList()
        if (id != wandId || cards != spells) { id = wandId; cards = spells.toList(); value = null }
        if (requested == Long.MIN_VALUE || now - requested >= 10 || now < requested) {
            requested = now
            ClientPacketDistributor.sendToServer(WandPreviewPayload(wandId))
        }
        return value?.slots.orEmpty()
    }

    /** 只接受仍匹配当前槽位的响应，编辑途中到达的旧包不能给新配方错着色。 */
    fun accept(payload: WandPreviewPayload) {
        val p = payload.preview ?: return
        if (payload.wandId == id && p.slots.map { it.id } == cards) value = p
    }

    /** 同域同色，按配置中的首次出现依次取色；空槽使用原版底色。 */
    fun color(slots: List<WandPreview.Slot>, index: Int): Int? {
        val scope = slots.getOrNull(index)?.scope?.takeIf { it.isNotEmpty() } ?: return null
        return palette[slots.map { it.scope }.filter { it.isNotEmpty() }.distinct().indexOf(scope) % palette.size]
    }

    /** 登出时清理，避免跨世界继承同名杖的预览。 */
    fun clear() { id = ""; cards = emptyList(); value = null; requested = Long.MIN_VALUE }
}
