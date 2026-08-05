package work.nekow.primalspells.wand

import work.nekow.primalspells.magic.Magic
import work.nekow.primalspells.magic.Projectile

/** 一个法术链：[修正器..., 终端投射物]。*/
class SpellChain(
    val modifiers: List<Magic>,
    val projectile: Projectile
) {
    val drawCost: Int get() = projectile.cast
}
