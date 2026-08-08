package work.nekow.primalspells.magic

interface TriggerSpell {
    var triggerCast: Int
    var payload: ArrayList<Magic>
}
