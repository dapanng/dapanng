package com.sacrificemod.mixin;

import com.sacrificemod.SacrificeMod;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弓箭手大作战：箭矢在水中不受衰减
 * 保存tick前的速度，在tick后恢复水中被衰减的部分
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class ArrowWaterMixin {

    /** 保存tick前的箭矢速度向量，用于在水中恢复被衰减的速度 */
    private Vec3d sacrificemod$prevVelocity;

    /**
     * 在箭矢tick开始时保存当前速度
     *
     * @param ci 回调信息
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void saveVelocity(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        sacrificemod$prevVelocity = self.getVelocity();
    }

    /**
     * 在箭矢tick结束后，如果箭矢在水中且弓箭手大作战玩法激活，恢复被水阻力衰减的速度
     *
     * @param ci 回调信息
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void restoreVelocityInWater(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        // 仅在弓箭手大作战玩法激活时生效
        if (!"archer".equals(SacrificeMod.getActiveGameplayId())) return;
        // 仅在箭矢在水中时恢复速度
        if (!self.isTouchingWater()) return;
        if (sacrificemod$prevVelocity == null) return;

        // 恢复被水阻力衰减的速度
        self.setVelocity(sacrificemod$prevVelocity);
    }
}
