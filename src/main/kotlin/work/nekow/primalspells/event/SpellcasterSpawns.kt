package work.nekow.primalspells.event

import net.minecraft.server.level.ServerLevel
import net.minecraft.util.random.Weighted
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.biome.MobSpawnSettings
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.LevelEvent
import work.nekow.primalspells.loot.SpellLootProfile
import work.nekow.primalspells.registry.ModEntities

/** 在结构内向原刷怪表追加单人施法者；不覆盖生物群系/结构已有敌人，不加入袭击或野外巡逻。 */
object SpellcasterSpawns {
    @SubscribeEvent
    fun potential(event: LevelEvent.PotentialSpawns) {
        val level = event.level as? ServerLevel ?: return
        if (event.mobCategory != MobCategory.MONSTER || SpellLootProfile.at(level, event.pos) == null) return
        if (event.spawnerDataList.any { it.value().type() == ModEntities.ROGUE_MAGE.get() }) return
        event.addSpawnerData(Weighted(MobSpawnSettings.SpawnerData(ModEntities.ROGUE_MAGE.get(), 1, 1), 35))
    }
}
