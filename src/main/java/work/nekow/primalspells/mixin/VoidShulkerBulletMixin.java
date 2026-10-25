package work.nekow.primalspells.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import work.nekow.primalspells.magic.effect.lifecycle.VoidStatus;

/** 已发射的追踪弹永久丢弃虚空目标，而非在状态结束后重新锁回。 */
@Mixin(ShulkerBullet.class)
public abstract class VoidShulkerBulletMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityReference;getEntity(Lnet/minecraft/world/entity/EntityReference;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private Entity primalspells$loseTarget(EntityReference<Entity> reference, Level level) {
        Entity target = EntityReference.getEntity(reference, level);
        return VoidStatus.isPhased(target) ? null : target;
    }
}
