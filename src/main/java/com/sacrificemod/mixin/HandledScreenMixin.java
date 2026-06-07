package com.sacrificemod.mixin;

import com.sacrificemod.ClientData;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/**
 * 容器界面Mixin：在残障玩法下阻止玩家点击被禁用的背包格子
 * <p>
 * 被禁用的格子使用BARRIER物品作为视觉指示器，
 * 本Mixin在点击时拦截操作，防止玩家与禁用格子交互。
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {

    /** 界面X偏移量 */
    @Shadow
    private int x;

    /** 界面Y偏移量 */
    @Shadow
    private int y;

    /**
     * 拦截鼠标点击事件，在残障玩法下阻止点击被禁用的背包格子
     *
     * @param slotId 被点击的槽位ID
     * @param ci 回调信息，可取消点击操作
     */
    // 阻止点击被禁用的格子 - 使用BARRIER物品作为视觉指示器而非覆盖层
    @Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true)
    private void preventDisabledSlotClick(int slotId, CallbackInfo ci) {
        // 仅在残障玩法激活时生效
        if (ClientData.activeGameplay == null || !ClientData.activeGameplay.equals("disabled")) return;
        // 无效槽位不处理
        if (slotId < 0) return;

        net.minecraft.client.gui.screen.ingame.HandledScreen<?> self = (net.minecraft.client.gui.screen.ingame.HandledScreen<?>) (Object) this;
        net.minecraft.screen.ScreenHandler handler = self.getScreenHandler();
        if (handler == null || slotId >= handler.slots.size()) return;

        Slot slot = handler.slots.get(slotId);
        // 仅限制玩家背包中的格子，不限制其他容器（如箱子）
        if (!(slot.inventory instanceof PlayerInventory)) return;

        UUID playerUuid = net.minecraft.client.MinecraftClient.getInstance().player.getUuid();
        Set<Integer> disabledSlots = ClientData.getDisabledSlots(playerUuid);

        // 如果该格子被禁用，取消点击操作
        if (disabledSlots != null && disabledSlots.contains(slot.getIndex())) {
            ci.cancel();
        }
    }
}
