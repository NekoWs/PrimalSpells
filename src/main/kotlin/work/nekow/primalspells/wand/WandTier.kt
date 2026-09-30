package work.nekow.primalspells.wand

import net.minecraft.util.RandomSource

/**
 * ≤T3 的抽牌表：1~3，1 的权重压倒性（60%）。
 *
 * 放在文件级而不是 companion 里：枚举项构造时 companion 还没初始化，直接引用会编译不过。
 */
private val CAST_LOW = listOf(1 to 60, 2 to 25, 3 to 15)

/** >T3 的抽牌表：1~6，仍然是 1 最重（40%）。 */
private val CAST_HIGH = listOf(1 to 40, 2 to 20, 3 to 15, 4 to 10, 5 to 8, 6 to 7)

/**
 * **法杖等级** —— 六级成长曲线 + 一根调试杖。数值口径见 [docs/design/wand-tiers.md]。
 *
 * ## 为什么要有它
 *
 * 在这之前 `Wand` 的数值是写死的（3000 法力 / 1000 每秒充能 / 0/0 延迟）——那是**创造模式专用**的
 * 测试杖，不是正常获取曲线上的东西。定攻击法术 DPS 时拿它当基准，会让"法力不够"这类分支永远不可达。
 * 现在等级表是唯一事实源：法力上限、充能速度、施放延迟、充能时间、槽位、施放数都从这里来。
 *
 * ## 两个时间值（沿用 `Wand` 里既有的两个概念，别混成一个）
 *
 * | 概念 | `Wand` 字段 | 含义 |
 * |---|---|---|
 * | **施放延迟** | [delayMs] → `Wand.delayMs` | 每发之间的**最小间隔**（与卡牌累加出来的值取大）——“手速上限” |
 * | **充能时间** | [rechargeMs] → `Wand.rechargeMs` | **抽牌堆抽空之后的重装时间**——“一梭子打完要喘多久” |
 *
 * 两者都随等级下降，但**不是同一个数**：充能时间约为施放延迟的 3 倍。
 * 低等级杖槽位少、抽空得频繁，再加上重装慢 —— 早期手感是"打几发就得喘一口"。
 *
 * ## 曲线（锚点来自用户口径）
 *
 * - 初期：`100 法力 / <80 充能速度 / ≥10 tick` 的两个时间值
 * - 中后期：`300 法力 / 100 充能速度 / ~5 tick`
 * - 顶级（[DEBUG]）：`3000 / 1000 / 0` —— 只作调试，不在六级曲线里
 *
 * 六级都落在「约 2.7–3.1 秒回满」附近：等级涨的是**池子深度**与**节奏**，不是"回蓝快多少"。
 * 时间值都用**毫秒**表达：内部按 `ms / 50` 折成小数 tick，配合冷却计数器的负余数累加
 * （见 `Wand.spell`），长期平均节奏能精确到毫秒；单次间隔仍是整数 tick（客户端每 tick 只能施法一次）。
 *
 * @param itemName 物品注册名（`item.primalspells.<itemName>` 就是显示名）
 * @param maxMana 法力上限
 * @param manaPerSecond 每秒充能（回蓝）
 * @param delayMs **施放延迟**：每发之间的最小间隔（毫秒）
 * @param rechargeMs **充能时间**：抽牌堆抽空后的重装时间（毫秒）
 * @param size 法术槽位数
 * @param castRolls 施放数的加权掷点表（`施放数 to 权重`）：施放 > 1 会让某些法术链难以使用
 *   （例如无法回绕），所以 **1 的权重最高**，高等级才允许掷出大数字
 */
enum class WandTier(
    val level: Int,
    val itemName: String,
    val maxMana: Double,
    val manaPerSecond: Double,
    val delayMs: Double,
    val rechargeMs: Double,
    val size: Int,
    private val castRolls: List<Pair<Int, Int>>,
) {
    /** 朽木杖：刚上手的那根，池子浅、节奏慢、槽位少、打完要喘很久 */
    T1(1, "wand_t1", 80.0, 30.0, 700.0, 2100.0, 6, CAST_LOW),

    /** 见习杖 */
    T2(2, "wand_t2", 120.0, 45.0, 580.0, 1800.0, 8, CAST_LOW),

    /** 学徒杖：用户口径的"初期"（约 100 法力 / <80 充能 / ≥10 tick 延迟）就落在这里 */
    T3(3, "wand_t3", 180.0, 60.0, 460.0, 1500.0, 10, CAST_LOW),

    /** 法师杖 */
    T4(4, "wand_t4", 240.0, 80.0, 340.0, 1200.0, 12, CAST_HIGH),

    /** 大师杖：用户口径的"中后期"（300 / 100 / ~5 tick） */
    T5(5, "wand_t5", 300.0, 100.0, 240.0, 900.0, 14, CAST_HIGH),

    /** 大法师杖 */
    T6(6, "wand_t6", 450.0, 140.0, 150.0, 600.0, 16, CAST_HIGH),

    /**
     * **调试杖**（`item.primalspells.wand`）：3000 / 1000 / 0 / 0 / 18 槽全抽 1。
     *
     * 刻意留在六级曲线之外：它是开发期用来隔离变量的（法力管够、零延迟），
     * 录制场景与测试指令都依赖它。正常玩法里拿不到（只有创造模式物品栏与 `/primalspells wand debug`）。
     */
    DEBUG(0, "wand", 3000.0, 1000.0, 0.0, 0.0, 18, listOf(1 to 1));

    /** 显示名（物品名）：`item.primalspells.<itemName>` */
    val displayKey: String get() = "item.primalspells.$itemName"

    /** 按权重掷一个施放数。 */
    fun rollCast(random: RandomSource): Int {
        var pick = random.nextInt(castRolls.sumOf { it.second })
        for ((value, weight) in castRolls) {
            pick -= weight
            if (pick < 0) return value
        }
        return castRolls.last().first
    }

    companion object {
        /** 六级成长曲线（不含 [DEBUG]），按等级升序。 */
        val LADDER: List<WandTier> = entries.filter { it != DEBUG }.sortedBy { it.level }

        /** 按等级号取（1..6）；越界返回 null。 */
        fun byLevel(level: Int): WandTier? = LADDER.firstOrNull { it.level == level }

        /** 按物品注册名取（大小写不敏感；`t1`/`1`/`wand_t1`/`debug`/`test` 都能认）；认不出返回 null。 */
        fun byName(name: String): WandTier? {
            val key = name.lowercase().removePrefix("wand_")
            if (key == "debug" || key == "test") return DEBUG
            // ⚠️ 先剥掉等级前缀 `t`：`itemName` 是 `wand_t5`，而玩家/指令里写的是 `t5`
            key.removePrefix("t").toIntOrNull()?.let { return byLevel(it) }
            return entries.firstOrNull { it.itemName.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * **法杖变体** —— 目前只有一种：[CHARGED]「蓄能型」。
 *
 * 用户口径：**高蓝低充能**的法杖也要能自然生成。它的定位是"一次爆发很多发、然后等很久"，
 * 所以是**乘在等级数值上**的变体（任意等级都能出），而不是单独一级。
 *
 * 数值分配：法力 ×3、充能速度 ÷3、两个时间值 ×1.5
 * （例：T5 大师杖 300 / 100 / 240 / 900 → 蓄能 900 / 33 / 360 / 1350）。
 */
enum class WandVariant(val manaMul: Double, val chargeDiv: Double, val delayMul: Double) {
    NORMAL(1.0, 1.0, 1.0),

    /** 蓄能型：蓝量三倍、充能三分之一、两个时间值一倍半 */
    CHARGED(3.0, 3.0, 1.5);

    /** 显示名后缀的语言键（`NORMAL` 为空 = 不加后缀） */
    val suffixKey: String? get() = if (this == NORMAL) null else "wand.primalspells.variant.charged"
}

/**
 * 一根法杖**建好时**掷出的数值：等级 + 变体 + 施放数。
 *
 * ⚠️ 掷点只在**创建**时发生一次，之后数值随法杖 NBT 一起存档（`Wand.saveTag`）——
 * 同一根杖不会每次加载都变一次数值。天然生成（掉落）时将来也走这条路。
 */
data class WandStats(
    val tier: WandTier,
    val variant: WandVariant,
    val maxMana: Double,
    val manaPerSecond: Double,
    val delayMs: Double,
    val rechargeMs: Double,
    val size: Int,
    val cast: Int,
) {
    companion object {
        /** 按等级 + 变体掷出一组数值（只有施放数是随机的）。 */
        fun roll(tier: WandTier, variant: WandVariant, random: RandomSource): WandStats = WandStats(
            tier = tier,
            variant = variant,
            maxMana = tier.maxMana * variant.manaMul,
            manaPerSecond = tier.manaPerSecond / variant.chargeDiv,
            delayMs = tier.delayMs * variant.delayMul,
            rechargeMs = tier.rechargeMs * variant.delayMul,
            size = tier.size,
            cast = tier.rollCast(random),
        )
    }
}
