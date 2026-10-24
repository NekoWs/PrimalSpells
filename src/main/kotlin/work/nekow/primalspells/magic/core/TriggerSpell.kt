package work.nekow.primalspells.magic.core

/** 独立作用域荷载；由具体条件决定重复复制还是一次性消费。 */
interface TriggerSpell {
    var triggerCast: Int
    var payload: ArrayList<Magic>
}
