package work.nekow.primalspells.magic.core

import java.util.Collections

/** 法术ID到独立实例工厂的目录；不负责内容选择、世界状态或事件。 */
object SpellRegistry {
    private val entries = linkedMapOf<String, () -> Magic>()

    /** 只读工厂视图；物品、数据校验和命令补全共用注册顺序。 */
    val factories: Map<String, () -> Magic> = Collections.unmodifiableMap(entries)

    /** 公共初始化阶段注册内容；拒绝覆盖已有ID，避免物品目录和克隆行为分叉。 */
    fun register(factory: () -> Magic) {
        val id = factory().id
        require(id.isNotBlank()) { "法术ID不能为空" }
        require(id !in entries) { "法术ID重复注册：$id" }
        entries[id] = factory
    }

    /** 每次创建完整的新实例与提示，不复用可变模板。 */
    fun create(id: String): Magic? = entries[id]?.invoke()?.also { it.initLore() }
}
