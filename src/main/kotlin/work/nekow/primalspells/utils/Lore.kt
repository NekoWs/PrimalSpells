package work.nekow.primalspells.utils

enum class Lore(val key: String) {
    MANA("lore.primalspells.mana"),
    DELAY("lore.primalspells.delay"),
    RECHARGE("lore.primalspells.recharge"),
    DAMAGE("lore.primalspells.damage"),
    MAX_AGE("lore.primalspells.max_age");

    companion object {
        fun formatDouble(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
    }
}

data class LoreEntry(val type: Lore, val args: Array<out Any>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoreEntry

        if (type != other.type) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}
