package work.nekow.primalspells.client.capture

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.magic.effect.BaseEffect
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.util.Deferred
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandAim
import work.nekow.primalspells.wand.WandManager

/** 使用真实主副手施法及碰撞回调，验证近远目标、方块遮挡和同步不重新装备。 */
object WandAimScenes {
    fun aiming(): Scene {
        val name = "wand_aim"
        lateinit var wand: Wand
        lateinit var dummy: TestDummyEntity
        var stack = ItemStack.EMPTY
        val results = linkedMapOf<String, Boolean>()
        fun fire(ctx: CaptureDriver.SceneCtx, label: String) {
            val aim = WandAim.target(ctx.player)
            results["$label-cast"] = wand.spell(ctx.player)
            results["$label-hit"] = false
            Deferred.after(ctx.level, 0) {
                val shot = MagicManager.projectiles.lastOrNull { it.wand === wand }
                val expected = shot?.let { aim.subtract(Vec3(it.position)).normalize() }
                results["$label-direction"] = shot != null && expected != null &&
                    Vec3(shot.velocity).normalize().distanceTo(expected) < 1.0e-5
                shot?.effects?.add(object : BaseEffect() {
                    override fun onHitEntity(target: Entity) { if (target === dummy && label != "occluded") results["$label-hit"] = true }
                    override fun onHitBlock(pos: org.joml.Vector3f) { if (label == "occluded" && pos.z in 6f..7f) results["$label-hit"] = true }
                })
            }
        }
        return Scene(name, "准星瞄准：近处 / 副手远处 / 方块遮挡", 24, 160, showHands = true,
            prepare = { ctx ->
                val floor = ctx.floorBlockY(0, 0)
                for (x in -12..12) for (z in -12..12) for (y in floor+1..floor+8)
                    ctx.level.setBlock(BlockPos(x,y,z), Blocks.AIR.defaultBlockState(),2)
                ctx.level.getEntitiesOfClass(TestDummyEntity::class.java, AABB.ofSize(Vec3.ZERO,100.0,200.0,100.0)).forEach { it.discard() }
                ctx.buildStudio(0,0,floor)
                ctx.setup(false,.5,floor+1.0,.5,0f,0f)
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY)
                WandCastingFx.clear()
                stack = ItemStack(ModItems.WAND.get())
                wand = WandManager[WandManager.createWand(stack)]
                wand.magics += MagicManager.create("fireball")
                wand.delaySeconds = .1; wand.rechargeSeconds = .1; wand.load(); wand.status.mana = wand.mana
                ctx.player.inventory.setItem(7,stack)
                dummy = ctx.spawnDummy(.5,floor+1.0,3.5)
                val copy = stack.copy()
                copy.set(ModItems.WAND_SELECTED_SLOT, 1)
                results["same-wand-sync-no-reequip"] = !(stack.item as WandItem).shouldCauseReequipAnimation(stack,copy,false)
                results["slot-switch-still-equips"] = (stack.item as WandItem).shouldCauseReequipAnimation(stack,copy,true)
            }, camera = { ctx, _ -> Pose(.5,ctx.floorBlockY(0,0)+1.0,.5,0f,0f) },
            tick = { ctx,t ->
                when(t) {
                    8 -> ctx.player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                    28 -> fire(ctx,"near")
                    50 -> {
                        dummy.setPos(.5,ctx.floorBlockY(0,0)+1.0,12.5)
                        ctx.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        ctx.player.setItemInHand(InteractionHand.OFF_HAND,stack)
                    }
                    70 -> fire(ctx,"far-offhand")
                    100 -> {
                        for (y in ctx.floorBlockY(0,0)+1..ctx.floorBlockY(0,0)+4)
                            ctx.level.setBlock(BlockPos(0,y,6),Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState(),2)
                        results["block-hides-entity"] = kotlin.math.abs(WandAim.target(ctx.player).z-6.0)<1.0e-5
                    }
                    112 -> fire(ctx,"occluded")
                    132 -> {
                        ctx.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY)
                        dummy.discard(); MagicManager.projectiles.forEach { it.alive=false }
                    }
                }
            }, checksAt = { _,t -> if(t==159) results.map { (key,ok) -> Check(name,t,key,ok,"result=$ok") } else emptyList() },
            finish = { ctx ->
                dummy.discard(); ctx.player.inventory.setItem(7,ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY)
                WandCastingFx.forget(ctx.player.uuid)
                for (y in ctx.floorBlockY(0,0)+1..ctx.floorBlockY(0,0)+4) ctx.level.setBlock(BlockPos(0,y,6),Blocks.AIR.defaultBlockState(),2)
            })
    }
}
