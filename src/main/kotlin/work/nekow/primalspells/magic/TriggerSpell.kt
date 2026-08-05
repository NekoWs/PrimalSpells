package work.nekow.primalspells.magic

import work.nekow.primalspells.wand.SpellChain

/** 触发法术：命中后施放 [payload] 中保存的下一个法术链。 */
interface TriggerSpell {
    /** 触发后施放的法术链。加载时由 Wand 链接。 */
    var payload: SpellChain?
}
