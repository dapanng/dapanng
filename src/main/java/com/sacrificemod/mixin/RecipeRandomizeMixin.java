package com.sacrificemod.mixin;

import com.sacrificemod.GameState;
import com.sacrificemod.SacrificeMod;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 全随机玩法：拦截合成结果更新，将输出替换为映射表中的随机物品
 * 通过拦截CraftingScreenHandler.updateResult，在源头修改合成结果
 * 确保显示和实际输出一致
 */
@Mixin(CraftingScreenHandler.class)
public class RecipeRandomizeMixin {

    /**
     * 在合成结果更新完成后，将合成输出物品替换为映射表中的随机物品
     * 同时同步给客户端确保显示一致
     *
     * @param handler 屏幕处理器
     * @param world 世界
     * @param player 执行合成的玩家
     * @param craftingInventory 合成输入栏
     * @param resultInventory 合成结果栏
     * @param recipe 当前匹配的合成配方（可能为null）
     * @param ci 回调信息
     */
    @Inject(method = "updateResult", at = @At("TAIL"))
    private static void randomizeUpdateResult(
            ScreenHandler handler,
            World world,
            PlayerEntity player,
            RecipeInputInventory craftingInventory,
            CraftingResultInventory resultInventory,
            @Nullable RecipeEntry<CraftingRecipe> recipe,
            CallbackInfo ci) {
        // 客户端不处理
        if (world.isClient) return;

        MinecraftServer server = world.getServer();
        if (server == null) return;

        GameState state = GameState.getServerState(server);
        // 仅在全随机玩法激活时生效
        if (!state.isGameplayActive("random")) return;

        // 获取合成结果槽位的物品
        ItemStack output = resultInventory.getStack(0);
        if (output.isEmpty()) return;

        // 通过映射表获取对应的随机物品
        Item mapped = SacrificeMod.RANDOM_GAMEPLAY.getMappedItem(output.getItem());
        if (mapped != output.getItem()) {
            // 替换为映射物品，保持原有数量
            ItemStack mappedStack = new ItemStack(mapped, output.getCount());
            resultInventory.setStack(0, mappedStack);
            handler.setPreviousTrackedSlot(0, mappedStack);
            // 同步给客户端，确保显示一致
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket(
                        handler.syncId, handler.nextRevision(), 0, mappedStack
                    )
                );
            }
        }
    }
}
