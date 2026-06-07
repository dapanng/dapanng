package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import com.sacrificemod.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

/**
 * 残疾玩法实现 - 逐渐残废
 * <p>
 * 核心机制：
 * 1. 玩家每次受伤达到阈值时，按概率禁用一个背包格子 - 受伤越多越残
 * 2. 被禁用的格子放入屏障方块作为标识 - 视觉上清晰可见
 * 3. 无法在禁用格子中放置物品 - 格子被屏障锁定
 * 4. 受伤有2秒冷却 - 防止岩浆/火焰等快速伤害源一次禁用多个格子
 * 5. 禁用概率和受伤阈值可通过UI调节
 * </p>
 */
public class DisabledGameplay extends BaseGameplay {
    
    /** 随机数生成器，用于禁用格子的概率判定和随机选择 */
    private static final Random RANDOM = new Random();
    
    /**
     * 构造方法 - 初始化残疾玩法的ID和显示名称
     */
    public DisabledGameplay() {
        super("disabled", "残疾玩法");
    }
    
    /**
     * 玩法开始时的初始化
     * <p>清除所有玩家的禁用格子记录，清空所有背包格子</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 清除所有禁用格子记录
        state.clearAllDisabledSlots();
        
        for (ServerPlayerEntity player : players) {
            // 清空所有背包格子（开始时背包为空）
            for (int i = 0; i < player.getInventory().size(); i++) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
            // 重置区块封锁状态（复用GameState中的字段）
            state.setPlayerBlockedInChunk(player.getUuid(), false);
        }
    }
    
    /**
     * 玩法结束时的清理
     * <p>移除所有玩家背包中的屏障物品，恢复被禁用的格子</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            // 移除所有屏障物品，恢复禁用格子
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack invStack = player.getInventory().getStack(i);
                if (invStack.getItem() == Items.BARRIER) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }
    
    /**
     * 每tick执行的处理逻辑
     * <p>确保禁用格子中有屏障物品（防止玩家移除），移除非禁用格子中的屏障物品。
     * 每5秒同步一次禁用格子状态到客户端。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 维护禁用格子中的屏障物品
        for (ServerPlayerEntity player : players) {
            if (player.isDead()) continue;
            
            Set<Integer> disabled = state.getDisabledSlots(player.getUuid());
            // 确保每个禁用格子都有屏障物品
            for (int slot : disabled) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isEmpty()) {
                    // 格子为空，放入屏障物品
                    ItemStack barrier = new ItemStack(Items.BARRIER);
                    barrier.set(DataComponentTypes.CUSTOM_NAME, 
                        Text.translatable("item.sacrificemod.disabled_slot"));
                    player.getInventory().setStack(slot, barrier);
                } else if (stack.getItem() != Items.BARRIER) {
                    // 格子中有非屏障物品，踢出并放入屏障
                    player.dropItem(stack.copy(), true);
                    ItemStack barrier = new ItemStack(Items.BARRIER);
                    barrier.set(DataComponentTypes.CUSTOM_NAME, 
                        Text.translatable("item.sacrificemod.disabled_slot"));
                    player.getInventory().setStack(slot, barrier);
                }
            }
            
            // 移除非禁用格子中误放的屏障物品
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (disabled.contains(i)) continue; // 跳过禁用格子
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() == Items.BARRIER) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
        }
        
        // 每5秒同步一次禁用格子状态到客户端
        if (server.getTicks() % 100 == 0) {
            syncDisabledSlotsToAll(server, state, players);
        }
    }
    
    /**
     * 玩家复活时的处理
     * <p>重新在禁用格子中放入屏障物品（复活后背包会重置）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        // 复活时重新应用禁用格子
        Set<Integer> disabled = state.getDisabledSlots(player.getUuid());
        for (int slot : disabled) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponentTypes.CUSTOM_NAME, 
                Text.translatable("item.sacrificemod.disabled_slot"));
            player.getInventory().setStack(slot, barrier);
        }
    }
    
    /**
     * 玩家受伤害时的处理
     * <p>受伤后增加受伤计数，达到阈值时按概率禁用随机格子。
     * 有2秒冷却防止快速伤害源一次禁用多个格子。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 受伤的玩家实例
     * @param source 伤害来源
     * @param amount 伤害量
     * @return 始终返回true，允许伤害正常处理
     */
    @Override
    public boolean onDamage(MinecraftServer server, GameState state, ServerPlayerEntity player,
                          DamageSource source, float amount) {
        // 2秒冷却（40tick），防止岩浆/火焰等快速伤害一次禁用多个格子
        long currentTick = server.getTicks();
        long lastHurt = state.getLastHurtTick(player.getUuid());
        if (currentTick - lastHurt < 40) {
            return true; // 冷却中，只允许伤害但不增加计数
        }
        state.setLastHurtTick(player.getUuid(), currentTick);
        
        // 增加受伤计数
        state.incrementHurtCount(player.getUuid());
        
        // 达到阈值时按概率禁用格子
        if (state.getHurtCount(player.getUuid()) >= state.getDisabledHurtThreshold()) {
            state.resetHurtCount(player.getUuid());
            // 按概率判定是否禁用
            if (RANDOM.nextInt(100) < state.getDisabledChance()) {
                disableRandomSlot(player, state);
            }
        }
        
        return true;
    }
    
    /**
     * 随机禁用一个未禁用的背包格子
     * <p>从可用格子中随机选择一个，踢出原有物品，放入屏障方块作为标识</p>
     * @param player 目标玩家
     * @param state 游戏全局状态
     */
    private void disableRandomSlot(ServerPlayerEntity player, GameState state) {
        Set<Integer> disabled = state.getDisabledSlots(player.getUuid());
        // 收集所有未禁用的格子
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (!disabled.contains(i)) {
                available.add(i);
            }
        }
        // 所有格子都已禁用，无法继续
        if (available.isEmpty()) return;
        
        // 随机选择一个格子禁用
        int slot = available.get(RANDOM.nextInt(available.size()));
        state.disableSlot(player.getUuid(), slot);
        
        // 踢出原物品并放入屏障
        ItemStack stack = player.getInventory().getStack(slot);
        if (!stack.isEmpty()) {
            player.dropItem(stack.copy(), true);
        }
        
        ItemStack barrier = new ItemStack(Items.BARRIER);
        barrier.set(DataComponentTypes.CUSTOM_NAME, 
            Text.translatable("item.sacrificemod.disabled_slot"));
        player.getInventory().setStack(slot, barrier);
        
        // 同步禁用格子状态到客户端
        syncDisabledSlots(player, state);
    }
    
    /**
     * 同步禁用格子状态到所有玩家
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    private void syncDisabledSlotsToAll(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            syncDisabledSlots(player, state);
        }
    }
    
    /**
     * 同步禁用格子状态到单个玩家
     * <p>发送DisabledSlotsSyncPayload数据包，包含禁用格子索引数组</p>
     * @param player 目标玩家
     * @param state 游戏全局状态
     */
    private void syncDisabledSlots(ServerPlayerEntity player, GameState state) {
        Set<Integer> disabled = state.getDisabledSlots(player.getUuid());
        int[] slotArray = disabled.stream().mapToInt(Integer::intValue).toArray();
        ServerPlayNetworking.send(player, new ModPackets.DisabledSlotsSyncPayload(player.getUuid(), slotArray));
    }
}
