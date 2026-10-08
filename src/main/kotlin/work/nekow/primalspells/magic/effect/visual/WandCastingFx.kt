package work.nekow.primalspells.magic.effect.visual

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3
import work.nekow.particledrawing.api.*
import work.nekow.primalspells.item.WandItem
import work.nekow.primalspells.wand.Wand
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/** 持杖法阵、常驻符印和宝石施法；逐帧轨迹、退场及成员寿命交给PD。 */
object WandCastingFx {
    const val EQUIP_TICKS = 8
    const val EXIT_TICKS = 6
    private const val GEM_FORWARD = 0.85
    private const val GEM_UP = 0.25
    private const val GEM_SIDE = 0.38
    private val states = hashMapOf<UUID, Held>()
    // 只保留有限退场会话，便于登出/跨维度立即取消；粒子成员和销毁调度仍由PD管理。
    private val retiring = ArrayList<Held>()

    private class Held(val player: ServerPlayer, val wand: Wand) {
        val level = player.level()
        val started = level.gameTime
        val groups = ArrayList<ParticleGroup>()
        var expires = Long.MAX_VALUE
        var lastDelay = -1.0
        var lastRecharge = -1.0
        var lastEyeHeight = -1.0
        var lastBusy = ""
        var lastSide = 0.0
        val ready: Boolean get() = level.gameTime - started >= EQUIP_TICKS
        private val elapsed: Double get() = (level.gameTime - started) * 50.0

        init {
            group(72, 0.09375f, """
                lane=floor(3*i/n);q=3*i/n-lane;
                theta=PI/2+yaw+lane*2*PI/3;
                ax=cos(theta)*1.2;az=sin(theta)*1.2;
                bx=cos(theta+2*PI/3)*1.2;bz=sin(theta+2*PI/3)*1.2;
                scatter=sin(i*91.71)*0.5+0.5;
                dissolve=min(1,max(0,(t-retireAt-scatter*50)/200));
                x=fx+ax+(bx-ax)*q;y=fy+sin(t*0.004+i*0.55)*0.018+dissolve*dissolve*(0.08+scatter*0.08);
                z=fz+az+(bz-az)*q;
                sc=min(1,max(0,(t/260-q)*24))*(1-dissolve);
                r=0.26;g=0.78;b=0.86;a=1;
            """)
            // 两枚块状菱形符印持续陪伴宝石；呼吸只改变尺寸，不周期关闭可见性。
            group(16, 0.046875f, """
                lane=floor(i/8);q=(i-lane*8)/8;
                edge=floor(q*4);u=q*4-edge;
                ax=cos(edge*PI/2);ay=sin(edge*PI/2);
                bx=cos((edge+1)*PI/2);by=sin((edge+1)*PI/2);
                turn=t*0.00055+lane*PI;
                rx=(lane*2-1)*(0.25+0.015*sin(turn));ry=sin(turn)*0.05;
                vx=rx+(ax+(bx-ax)*u)*0.075;
                vy=ry+(ay+(by-ay)*u)*0.075;
                x=hx+cos(yaw)*vx+ux*vy;
                y=hy+uy*vy;z=hz+sin(yaw)*vx+uz*vy;
                sc=min(1,max(0,(t-260)/100))*(0.9+0.1*sin(t*0.003+lane*PI))*sqrt(remaining);
                r=0.38+lane*0.16;g=0.92;b=1;a=1;
            """)
            // 施法后束印张开卸能，白色夹片回位表示施放间隔恢复；从第一帧就完整可见。
            group(8, 0.046875f, """
                lane=floor(i/4);node=i-lane*4;
                done=min(1,max(0,(t-delayEnd)/160));
                vx=(lane*2-1)*(0.24+0.12*(1-dp)*(1-dp)+(node-floor(node/2)*2)*0.045);
                vy=(floor(node/2)*2-1)*0.10;
                x=hx+cos(yaw)*vx+ux*vy;y=hy+uy*vy;z=hz+sin(yaw)*vx+uz*vy;
                sc=delayOn*(1-done)*remaining;
                r=0.92;g=0.96;b=1;a=1;
            """)
            group(36, 0.09375f, """
                lane=floor(i/12);node=i-lane*12;row=floor(node/3);col=node-row*3-1;
                theta=PI/2+yaw+lane*2*PI/3;
                done=min(1,max(0,(t-rechargeEnd-row*15)/160));
                rad=1.08+done*0.08;
                x=fx+cos(theta)*rad-sin(theta)*col*0.105;
                z=fz+sin(theta)*rad+cos(theta)*col*0.105;
                y=fy+0.075+row*0.125+done*done*0.22;
                shape=1-abs(row-1.5)*0.18-abs(col)*0.12;
                sc=rechargeOn*shape*(1-done)*remaining;
                lit=min(1,max(0,floor(rp*4-row+1)));
                lit=lit*min(1,3-row)+floor(rp)*max(0,row-2);
                r=0.16+lit*0.12;g=0.32+lit*0.34;b=0.72+lit*0.28;a=1;
            """)
            group(1, 0.0625f, """
                x=hx;y=hy;z=hz;
                sc=min(1,max(0,(t-260)/100))*remaining;
                r=0.72;g=1;b=0.95;a=1;
            """)
            update()
        }

        private fun group(count: Int, size: Float, code: String) {
            val manager = ParticleManager.of(level)
            val group = manager.createGroup(player.position())
            repeat(count) {
                group.add(manager.create().position(player.position()).color(Color.ofInt(85, 218, 226))
                    .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(size, size).glowing(true).additive(false))
                    .lifetime(-1).spawn())
            }
            group.defineEntity("holder", player.uuid)
                .setVariable("seed", (player.uuid.hashCode() and 65535).toDouble())
                .setVariable("eh", player.eyeHeight.toDouble()).setVariable("side", handOffset(player, wand))
                .setVariable("dp", 0.0).setVariable("rp", 0.0)
                .setVariable("busy", 0.0).setVariable("delayOn", 0.0).setVariable("rechargeOn", 0.0)
                .setVariable("retireAt", 1.0e12).setVariable("delayEnd", 1.0e12).setVariable("rechargeEnd", 1.0e12)
            group.expression("""
                fx=get_entity_x(holder);fy=get_entity_y(holder)+0.06;fz=get_entity_z(holder);
                yaw=get_entity_yaw(holder)*PI/180;
                dx=get_entity_dirx(holder);dy=get_entity_diry(holder);dz=get_entity_dirz(holder);
                ux=sin(yaw)*dy;uy=sqrt(max(0,1-dy*dy));uz=-cos(yaw)*dy;
                hx=fx+dx*$GEM_FORWARD+cos(yaw)*side+ux*$GEM_UP;
                hy=fy-0.06+eh+dy*$GEM_FORWARD+uy*$GEM_UP;
                hz=fz+dz*$GEM_FORWARD+sin(yaw)*side+uz*$GEM_UP;
                remaining=1-min(1,max(0,(t-retireAt)/250));
                $code
            """.trimIndent())
            groups += group
        }

        private fun variable(name: String, value: Double) = groups.forEach { it.setVariableLive(name, value.toString()) }

        fun update() {
            val delay = wand.status.delay.coerceAtLeast(0.0)
            val recharge = wand.status.recharge.coerceAtLeast(0.0)
            if (lastDelay != delay) {
                if (delay > 0) {
                    variable("dp", 1 - delay / wand.status.lastDelay.coerceAtLeast(delay))
                    variable("delayOn", 1.0); variable("delayEnd", 1.0e12)
                } else if (lastDelay > 0) {
                    // 完成时保留最后进度，让PD以自己的播放时钟收尾，而不是直接把可见性切零。
                    variable("dp", 1.0); variable("delayEnd", elapsed)
                }
                lastDelay = delay
            }
            if (lastRecharge != recharge) {
                if (recharge > 0) {
                    variable("rp", 1 - recharge / wand.status.lastRecharge.coerceAtLeast(recharge))
                    variable("rechargeOn", 1.0); variable("rechargeEnd", 1.0e12)
                } else if (lastRecharge > 0) {
                    variable("rp", 1.0); variable("rechargeEnd", elapsed)
                }
                lastRecharge = recharge
            }
            val busy = if (delay > 0 || recharge > 0) "1" else "0"
            if (lastBusy != busy) { groups.forEach { it.setVariableLive("busy", busy) }; lastBusy = busy }
            val eyeHeight = player.eyeHeight.toDouble()
            val side = handOffset(player, wand)
            if (lastSide != side) { variable("side", side); lastSide = side }
            if (eyeHeight != lastEyeHeight) { variable("eh", eyeHeight); lastEyeHeight = eyeHeight }
        }

        fun retire() {
            variable("retireAt", elapsed)
            expires = level.gameTime + EXIT_TICKS
            groups.forEach { it.destroyAfter(EXIT_TICKS) }
            retiring += this
        }

        fun remove() = groups.forEach { it.remove() }
    }

    /** 使用中的手优先，否则主手优先；双持只显示一座法阵。 */
    private fun selected(player: ServerPlayer): Wand? {
        val stacks = if (player.isUsingItem) listOf(player.useItem, player.mainHandItem, player.offhandItem)
            else listOf(player.mainHandItem, player.offhandItem)
        return stacks.firstOrNull { it.item is WandItem }?.let { WandItem.getWand(it) }
    }

    private fun handOffset(player: ServerPlayer, wand: Wand): Double {
        val main = player.mainHandItem.item is WandItem && WandItem.getWand(player.mainHandItem) === wand
        val usingOffhand = player.isUsingItem && player.usedItemHand == net.minecraft.world.InteractionHand.OFF_HAND
        val side = if (player.mainArm == HumanoidArm.RIGHT) -GEM_SIDE else GEM_SIDE
        return if (main && !usingOffhand) side else -side
    }

    private fun state(player: ServerPlayer, wand: Wand): Held {
        val current = states[player.uuid]
        if (current != null && current.wand === wand && current.player === player && current.level === player.level()) return current
        if (current?.player === player && current.level === player.level()) current.retire() else current?.remove()
        return Held(player, wand).also { states[player.uuid] = it }
    }

    /** 结算完成立即发布真实冷却状态，避免等待下一次会话tick。 */
    fun cooldownsChanged(caster: Entity, wand: Wand) {
        val player = caster as? ServerPlayer ?: return
        if (selected(player) === wand) state(player, wand).update()
    }

    /** 切杖/放下播放有限退场；死亡、维度变化、登出立即清理，避免追踪失效实体。 */
    fun tick(server: MinecraftServer) {
        retiring.removeAll {
            val invalid = !it.player.isAlive || it.player.level() !== it.level || server.playerList.getPlayer(it.player.uuid) !== it.player
            if (invalid) it.remove()
            invalid || it.level.gameTime >= it.expires
        }
        val held = hashSetOf<UUID>()
        for (player in server.playerList.players) {
            val wand = if (player.isAlive) selected(player) else null
            if (wand == null) continue
            held += player.uuid
            state(player, wand).update()
        }
        val iterator = states.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in held) {
                if (entry.value.player.isAlive && entry.value.player.level() === entry.value.level && server.playerList.getPlayer(entry.key) === entry.value.player)
                    entry.value.retire() else entry.value.remove()
                iterator.remove()
            }
        }
    }

    /** 只限制真正拿在手里的玩家法杖；AI与直接测试配方不受装备动画阻塞。 */
    fun ready(caster: Entity, wand: Wand): Boolean {
        val player = caster as? ServerPlayer ?: return true
        if (selected(player) !== wand) return true
        return state(player, wand).ready
    }

    /** 模型宝石位于视线右上方，施法和符印共用同一相机正交基；副手/左惯用手镜像。 */
    fun origin(caster: Entity, wand: Wand): Vec3 {
        val player = caster as? ServerPlayer
        if (player == null || selected(player) !== wand) return caster.eyePosition.add(0.0, -0.1, 0.0)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forward = caster.lookAngle
        val up = Vec3(sin(yaw) * forward.y, kotlin.math.sqrt((1-forward.y*forward.y).coerceAtLeast(0.0)), -cos(yaw)*forward.y)
        val side = handOffset(player, wand)
        return caster.eyePosition.add(forward.scale(GEM_FORWARD)).add(up.scale(GEM_UP))
            .add(cos(yaw)*side, 0.0, sin(yaw)*side)
    }

    /** 成功释放投射物时的一次宝石迸发；多重施法只播放一次，不额外增加施法延迟。 */
    fun cast(caster: Entity, wand: Wand) {
        val player = caster as? ServerPlayer ?: return
        if (selected(player) !== wand) return
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, work.nekow.primalspells.network.WandCastPayload(wand.id))
        val center = origin(caster, wand)
        val forward = work.nekow.primalspells.wand.WandAim.direction(caster, center)
        val manager = ParticleManager.of(caster.level())
        val group = manager.createGroup(center)
        repeat(24) { i ->
            group.add(manager.create().position(center).color(Color.ofInt(195, 253, 240))
                .visual(ParticleVisual().style(ParticleStyle.SQUARE).anisoWorld(if(i<6) 0.1875f else 0.125f, 0.125f)
                    .glowing(true).additive(false)).lifetime(6).spawn())
        }
        group.setVariable("cx", center.x).setVariable("cy", center.y).setVariable("cz", center.z)
            .setVariable("dx", forward.x).setVariable("dy", forward.y).setVariable("dz", forward.z)
            .expression("""
                p=min(1,t/200);h=sin(i*127.1)*43758.5453;h=h-floor(h);
                angle=i*2.399963;spread=sin(PI*p)*(0.08+h*0.16);
                x=cx+dx*p*(0.25+h*0.45)+cos(angle)*spread;
                y=cy+dy*p*(0.25+h*0.45)+sin(angle)*spread;
                z=cz+dz*p*(0.25+h*0.45)+sin(angle*1.71)*spread;
                sc=(1-p)*(1-p);r=0.73;g=1;b=0.92;a=1;
            """.trimIndent()).destroyAfter(6)
    }

    /** 登出清理活动法阵；有限退场组的销毁已由PD调度。 */
    fun forget(playerId: UUID) {
        states.remove(playerId)?.remove()
        retiring.removeAll { if (it.player.uuid == playerId) { it.remove(); true } else false }
    }
    /** 世界加载/服务器停止时清除活动会话。 */
    fun clear() {
        states.values.forEach { it.remove() }; states.clear()
        retiring.forEach { it.remove() }; retiring.clear()
    }
}
