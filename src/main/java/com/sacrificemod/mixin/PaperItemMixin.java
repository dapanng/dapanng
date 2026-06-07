package com.sacrificemod.mixin;

import com.sacrificemod.ClientData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.UseAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 纸人玩法物品Mixin：使纸、雪块、冰块等物品可食用
 * <p>
 * 在纸人玩法下，将纸、雪块、冰块、蓝冰、浮冰的使用动作改为"食用"，
 * 并设置使用时间为32tick，使玩家可以长按右键食用这些物品。
 */
@Mixin(Item.class)
public class PaperItemMixin {

    /**
     * 拦截物品使用动作查询，在纸人玩法下将特定物品的使用动作改为"食用"
     *
     * @param stack 物品栈
     * @param cir 回调信息，可修改返回值
     */
    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void onGetUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        // 仅在纸人玩法激活时生效
        if (ClientData.activeGameplay != null && ClientData.activeGameplay.equals("paper_person")) {
            // 纸、雪块、冰块、蓝冰、浮冰可食用
            if (stack.getItem() == Items.PAPER || stack.getItem() == Items.SNOW_BLOCK || stack.getItem() == Items.ICE || stack.getItem() == Items.BLUE_ICE || stack.getItem() == Items.PACKED_ICE) {
                cir.setReturnValue(UseAction.EAT);
            }
        }
    }

    /**
     * 拦截物品最大使用时间查询，在纸人玩法下将特定物品的使用时间设为32tick
     *
     * @param stack 物品栈
     * @param user 使用者
     * @param cir 回调信息，可修改返回值
     */
    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void onGetMaxUseTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        // 仅在纸人玩法激活时生效
        if (ClientData.activeGameplay != null && ClientData.activeGameplay.equals("paper_person")) {
            // 纸、雪块、冰块、蓝冰、浮冰的使用时间为32tick
            if (stack.getItem() == Items.PAPER || stack.getItem() == Items.SNOW_BLOCK || stack.getItem() == Items.ICE || stack.getItem() == Items.BLUE_ICE || stack.getItem() == Items.PACKED_ICE) {
                cir.setReturnValue(32);
            }
        }
    }
}
