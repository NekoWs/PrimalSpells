package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier

/** 法力返还与双冷却减免属于本次事务；生命代价不注入每枚投射物。 */
class BloodMagic : Modifier() {
    override val id = "blood_magic"
    override val healthCost = 1f
    init { mana = -100.0; delay = -7; recharge = -7 }
}
