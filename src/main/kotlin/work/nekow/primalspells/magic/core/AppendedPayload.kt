package work.nekow.primalspells.magic.core

/** 追加条件和独立荷载；不能共享已发射的投射物实例。 */
class AppendedPayload(val kind: DrawAction.Kind, val cards: ArrayList<Magic>)
