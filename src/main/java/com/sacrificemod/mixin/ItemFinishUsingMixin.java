package com.sacrificemod.mixin;

import com.sacrificemod.SacrificeMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 物品使用完成Mixin
 * <p>
 * 原用于拦截物品使用完成事件，现已废弃。
 * 物品使用逻辑已迁移至onUseItem中直接处理。
 */
@Mixin(Item.class)
public class ItemFinishUsingMixin {

    /**
     * 拦截物品使用完成事件（已废弃，逻辑已迁移）
     *
     * @param stack 使用中的物品栈
     * @param world 所在世界
     * @param user 使用者
     * @param cir 回调信息，可修改返回值
     */
    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void onFinishUsing(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        // 已废弃 - 物品使用完成逻辑现在由onUseItem直接处理
    }
}
