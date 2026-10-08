package work.nekow.primalspells.magic.effect.lifecycle

/** 可直接命中实体的效果向护盾声明单次伤害强度；不把未使用的 status 字段当成攻击。 */
interface DirectDamage {
    /** 当前一次命中的基础伤害，供护盾抗性/窗口预算读取。 */
    val damageStrength: Double
}
