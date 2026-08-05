package work.nekow.primalspells.magic

/** 触发法术：命中后施放 [payload] 中保存的下一个法术链。 */
interface TriggerSpell {
    var triggerCast: Int
    var payload: ArrayList<Magic>
}
