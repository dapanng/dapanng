package com.sacrificemod.mixin;

import com.sacrificemod.GameState;
import com.sacrificemod.SacrificeMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin用于全随机玩法中随机化实体掉落物
 * 在dropLoot执行后，查找附近刚生成的ItemEntity并替换其物品
 */
@Mixin(LivingEntity.class)
public class LootRandomizeMixin {

    /**
     * 在生物掉落物生成后，将掉落物品替换为映射表中的随机物品
     * 仅在全随机玩法激活时生效
     *
     * @param source 造成死亡的伤害来源
     * @param causedByPlayer 是否由玩家击杀
     * @param ci 回调信息
     */
    @Inject(method = "dropLoot", at = @At("TAIL"))
    private void randomizeDrops(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 客户端不处理
        if (self.getWorld().isClient()) return;

        MinecraftServer server = self.getServer();
        if (server == null) return;

        GameState state = GameState.getServerState(server);
        // 仅在全随机玩法激活时生效
        if (!state.isGameplayActive("random")) return;

        // 查找实体附近2格范围内刚掉落的ItemEntity，替换为映射的随机物品
        for (ItemEntity itemEntity : self.getWorld().getEntitiesByClass(
                ItemEntity.class, self.getBoundingBox().expand(2.0), e -> true)) {
            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            // 通过映射表获取对应的随机物品
            Item mapped = SacrificeMod.RANDOM_GAMEPLAY.getMappedItem(stack.getItem());
            if (mapped != stack.getItem()) {
                // 替换为映射物品，保持原有数量
                itemEntity.setStack(new ItemStack(mapped, stack.getCount()));
            }
        }
    }
}
