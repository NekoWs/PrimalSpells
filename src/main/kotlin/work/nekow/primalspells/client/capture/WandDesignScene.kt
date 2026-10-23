package work.nekow.primalspells.client.capture

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import work.nekow.particledrawing.api.ParticleManager
import work.nekow.primalspells.magic.core.SpellRegistry
import work.nekow.primalspells.magic.effect.visual.WandCastingFx
import work.nekow.primalspells.magic.runtime.ProjectileRuntime
import work.nekow.primalspells.registry.ModItems
import work.nekow.primalspells.wand.Wand
import work.nekow.primalspells.wand.WandGrip
import work.nekow.primalspells.wand.WandManager

/** 户外真实持杖：右手、副手和左惯用手，验证低位宝石、施法与有限收场。 */
object WandDesignScene {
    fun scene(): Scene {
        val name = "wand_design"
        val checks = ConcurrentHashMap<Int, MutableList<Check>>()
        var floor = 0
        val scenery = linkedMapOf<BlockPos, net.minecraft.world.level.block.state.BlockState>()
        var stack = ItemStack.EMPTY
        lateinit var wand: Wand
        fun record(t: Int, label: String, ok: Boolean, detail: String) {
            checks.getOrPut(t) { arrayListOf() } += Check(name,t,label,ok,detail)
        }
        return Scene(name, "暮紫封印 / 户外低位持握 / 左右手", 24, 160, showHands = true, fov = 70,
            prepare = { ctx ->
                floor = ctx.floorBlockY(0,0)
                ctx.setup(false,0.5,floor+1.0,-5.5,0f,0f)
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
                WandCastingFx.clear()
                // 仅布置独立run/capture世界，避免旧摄影棚墙面影响自然背景的材质判断。
                for(x in -17..17) for(z in -17..17) {
                    ctx.level.setBlock(BlockPos(x,floor,z),Blocks.GRASS_BLOCK.defaultBlockState(),2)
                    for(y in floor+1..floor+32) ctx.level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2)
                }
                fun decorate(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
                    scenery.putIfAbsent(pos,ctx.level.getBlockState(pos))
                    ctx.level.setBlock(pos,state,2)
                }
                for((x,z) in listOf(-9 to 9,9 to 12,-12 to -2,12 to -1)) {
                    for(y in 1..5) decorate(BlockPos(x,floor+y,z),Blocks.OAK_LOG.defaultBlockState())
                    for(dx in -2..2) for(dz in -2..2) for(dy in 4..6) {
                        if(kotlin.math.abs(dx)+kotlin.math.abs(dz)<4) decorate(BlockPos(x+dx,floor+dy,z+dz),Blocks.OAK_LEAVES.defaultBlockState())
                    }
                }
                // 准星后方的矮石壁同时作为实际弹体的有限距离收弹靶。
                for(x in -2..2) for(y in 1..4) decorate(BlockPos(x,floor+y,11),Blocks.STONE_BRICKS.defaultBlockState())
                stack=ItemStack(ModItems.WAND.get())
                wand=WandManager[WandManager.createWand(stack)]
                wand.magics += SpellRegistry.create("fireball")
                wand.delayTickCount=2.0; wand.rechargeTickCount=2.0
                wand.load(); wand.status.mana=wand.mana
                ctx.player.inventory.setItem(7,stack)
            }, camera = { _,_ -> Pose(0.5,floor+1.0,-5.5,0f,0f) },
            clientTick = { mc,t ->
                if(t==90) { mc.options.mainHand().set(HumanoidArm.LEFT); mc.options.broadcastOptions() }
                if(t==142) { mc.options.mainHand().set(HumanoidArm.RIGHT); mc.options.broadcastOptions() }
            }, tick = { ctx,t ->
                when(t) {
                    8 -> ctx.player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                    32,72,108 -> {
                        val delta=WandCastingFx.origin(ctx.player,wand).subtract(ctx.player.eyePosition)
                        val sign=if(t==32) -1.0 else 1.0
                        record(t,"gem-is-low-and-on-held-side-$t",kotlin.math.abs(delta.y-WandGrip.UP)<1e-5 &&
                            kotlin.math.abs(delta.x-sign*WandGrip.SIDE)<1e-5,"delta=$delta")
                        record(t,"actual-cast-$t",wand.spell(ctx.player),"MP=${wand.status.mana}")
                    }
                    50 -> {
                        ctx.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        ctx.player.setItemInHand(InteractionHand.OFF_HAND,stack)
                    }
                    95 -> {
                        ctx.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY)
                        ctx.player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                    }
                    104 -> record(t,"left-handed-preference-reaches-server",ctx.player.mainArm==HumanoidArm.LEFT,"arm=${ctx.player.mainArm}")
                    125 -> {
                        ctx.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                        ctx.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY)
                        ProjectileRuntime.projectiles.forEach { it.alive=false }
                    }
                    150 -> record(t,"model-scene-cleans-effects",ProjectileRuntime.projectiles.isEmpty() &&
                        ParticleManager.of(ctx.level).getEngine().particleCount()==0,"particles=${ParticleManager.of(ctx.level).getEngine().particleCount()}")
                }
            }, finish = { ctx ->
                ctx.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
                ctx.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY)
                ctx.player.inventory.setItem(7,ItemStack.EMPTY)
                WandCastingFx.forget(ctx.player.uuid)
                scenery.forEach { (pos,state) -> ctx.level.setBlock(pos,state,2) }
                scenery.clear()
            }, checksAt = { _,t -> checks[t].orEmpty() })
    }
}
