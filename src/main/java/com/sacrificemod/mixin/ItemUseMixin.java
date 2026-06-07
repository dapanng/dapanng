package com.sacrificemod.mixin;

import com.sacrificemod.SacrificeMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 物品使用Mixin：在纸人玩法下拦截纸、雪块、冰块的使用事件
 * <p>
 * 当纸人玩法激活时，使纸、雪块、冰块可以右键使用（开始食用动画），
 * 实际效果在食用完成时由服务端逻辑处理。
 */
@Mixin(Item.class)
public class ItemUseMixin {

    /**
     * 拦截物品使用事件，在纸人玩法下使纸、雪块、冰块可右键使用
     * 返回success以启动食用动画和使用流程，实际效果在食用完成时处理
     *
     * @param world 世界
     * @param user 使用物品的玩家
     * @param hand 使用物品的手
     * @param cir 回调信息，可修改返回值
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        // 客户端不处理
        if (world.isClient()) return;
        ItemStack stack = user.getStackInHand(hand);
        // 仅处理纸、雪块、冰块
        if (stack.getItem() != Items.PAPER && stack.getItem() != Items.SNOW_BLOCK && stack.getItem() != Items.ICE) return;
        if (!(user instanceof ServerPlayerEntity serverPlayer)) return;

        // 检查纸人玩法是否激活
        if (SacrificeMod.isPaperPersonActive(serverPlayer)) {
            // 返回成功以启动食用动画和使用流程
            // 实际效果将在食用完成时由服务端逻辑处理
            cir.setReturnValue(TypedActionResult.success(stack, false));
        }
    }
}
