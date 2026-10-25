package work.nekow.primalspells.mixin;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 客户端移动预测和服务端位置包均锁定；保留转头，迟到的位置包只纠正一次。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VoidPlayerMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void primalspells$lockPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.level());
        if (!VoidStatus.isPhased(player)) return;
        float yaw = packet.getYRot(player.getYRot());
        float pitch = packet.getXRot(player.getXRot());
        if (Float.isFinite(yaw) && Float.isFinite(pitch)) player.absSnapRotationTo(yaw, pitch);
        double dx = packet.getX(player.getX()) - player.getX();
        double dy = packet.getY(player.getY()) - player.getY();
        double dz = packet.getZ(player.getZ()) - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 1.0e-8)
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ci.cancel();
    }
}
