package com.sacrificemod.mixin;

import com.sacrificemod.ClientData;
import com.sacrificemod.SacrificeMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import com.sacrificemod.BodyPart;

/**
 * 生物实体Mixin：处理跳跃限制和死亡事件
 * <p>
 * 功能：
 * - 祭祀玩法：双腿均失去时禁止跳跃，跳跃时触发计数
 * - 通用：非玩家实体死亡时通知模组处理掉落等逻辑
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * 拦截跳跃事件
     * - 祭祀玩法下，双腿均失去的玩家无法跳跃
     * - 服务端玩家跳跃时触发跳跃计数（用于召唤敌对生物）
     *
     * @param ci 回调信息，可取消跳跃
     */
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof PlayerEntity player) {
            if (player.getWorld().isClient()) {
                // 客户端：检查本地缓存的失去部位数据
                Set<BodyPart> lostParts = ClientData.getLostParts(player.getUuid());
                // 双腿均失去时禁止跳跃
                if (lostParts.contains(BodyPart.LEFT_LEG) && lostParts.contains(BodyPart.RIGHT_LEG)) {
                    ci.cancel();
                }
            } else if (player instanceof ServerPlayerEntity serverPlayer) {
                // 服务端：从GameState获取失去部位数据
                Set<BodyPart> lostParts = com.sacrificemod.GameState.getServerState(serverPlayer.getServer())
                        .getLostParts(serverPlayer.getUuid());
                // 双腿均失去时禁止跳跃
                if (lostParts.contains(BodyPart.LEFT_LEG) && lostParts.contains(BodyPart.RIGHT_LEG)) {
                    ci.cancel();
                    return;
                }
                // 触发玩家跳跃事件（用于祭祀玩法的跳跃计数）
                SacrificeMod.onPlayerJump(serverPlayer);
            }
        }
    }

    /**
     * 拦截生物死亡事件
     * 仅处理非玩家实体在服务端的死亡，通知模组进行掉落处理等逻辑
     *
     * @param source 伤害来源
     * @param ci 回调信息
     */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 仅在服务端处理，且排除玩家（玩家死亡由其他逻辑处理）
        if (!self.getWorld().isClient() && !(self instanceof ServerPlayerEntity)) {
            SacrificeMod.onEntityDeath(self, source);
        }
    }
}
