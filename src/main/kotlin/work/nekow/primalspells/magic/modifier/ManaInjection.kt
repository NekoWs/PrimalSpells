package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

/**
 * 注入玛那修正 —— 施放时返还 30 法力而非消耗法力。
 *
 * 本质：将法力消耗设为负数，实现施法时恢复法力。
 */
class ManaInjection : Modifier() {

    override val id = "mana_injection"

    init {
        /** 施放返还的法力值（负数 = 恢复法力） */
        mana = -30.0
    }
}
