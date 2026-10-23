package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.visual.SpellLightFx

/** 为本链弹体添加一颗动态光源；重复卡不重复创建光源。 */
class Light : Modifier() {
    override val id = "light"

    init {
        mana = 3.0
        effects += SpellLightFx()
    }
}
