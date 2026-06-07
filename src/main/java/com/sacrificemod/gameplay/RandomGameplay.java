package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import com.sacrificemod.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * 全随机玩法实现 - 混沌世界
 * <p>
 * 核心机制：
 * 1. 所有物品的掉落物被随机替换 - 挖矿和击杀获得的物品完全随机
 * 2. 每隔一定时间自动重新随机化 - 物品映射表定期刷新
 * 3. 玩家主动丢弃的物品不受影响 - 只替换自然掉落物
 * 4. UI可调节随机化间隔 - 控制混沌程度
 * 5. 客户端同步随机化倒计时 - 显示下次随机化时间
 * </p>
 */
public class RandomGameplay extends BaseGameplay {

    /** 当前服务器实例引用，供Mixin回调使用 */
    private static MinecraftServer currentServer = null;

    /** 物品随机映射表：原始物品ID → 随机替换物品 */
    private Map<Identifier, Item> itemMapping = new HashMap<>();

    /** 已被随机化替换的物品实体UUID集合，避免重复替换 */
    private final Set<UUID> randomizedEntities = new HashSet<>();

    /**
     * 构造方法 - 初始化全随机玩法的ID和显示名称
     */
    public RandomGameplay() {
        super("random", "全随机玩法");
    }

    /**
     * 获取当前服务器实例
     * <p>供Mixin回调使用，在合成配方替换时需要访问服务器实例</p>
     * @return 当前Minecraft服务器实例，可能为null
     */
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    /**
     * 玩法开始时的初始化
     * <p>保存服务器实例引用，记录当前tick为上次随机化时间，执行首次物品随机化</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        currentServer = server;
        // 记录当前tick作为随机化起始时间
        state.setLastRandomizeTick(server.getTicks());
        // 执行首次物品随机化
        randomizeItems(server);
    }

    /**
     * 玩法结束时的清理
     * <p>清空物品映射表和已替换实体记录，释放服务器实例引用</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 清空映射表和记录
        itemMapping.clear();
        randomizedEntities.clear();
        currentServer = null;
    }

    /**
     * 每tick执行的处理逻辑
     * <p>每2tick检查世界中的掉落物实体，将未替换的掉落物替换为映射表中的随机物品。
     * 到达随机化间隔时重新生成映射表。每100tick同步倒计时到客户端。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 每2tick检查一次掉落物替换，防止玩家捡到原始物品
        if (server.getTicks() % 2 == 0) {
            for (var world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof net.minecraft.entity.ItemEntity itemEntity) {
                        // 跳过已替换过的物品实体，避免重复处理
                        if (randomizedEntities.contains(itemEntity.getUuid())) continue;
                        // 跳过玩家主动丢弃的物品（有owner标记的）
                        if (itemEntity.getOwner() != null) continue;

                        ItemStack stack = itemEntity.getStack();
                        Item original = stack.getItem();
                        // 检查映射表中是否有该物品的替换
                        if (hasMapping(original)) {
                            Item mapped = getMappedItem(original);
                            if (mapped != original) {
                                // 替换为随机物品，保持数量不变
                                ItemStack newStack = new ItemStack(mapped, stack.getCount());
                                itemEntity.setStack(newStack);
                                randomizedEntities.add(itemEntity.getUuid());
                            }
                        }
                    }
                }
            }

            // 清理已不存在的实体UUID，避免内存泄漏（超过10000条时触发）
            if (randomizedEntities.size() > 10000) {
                Set<UUID> alive = new HashSet<>();
                for (var world : server.getWorlds()) {
                    for (var entity : world.iterateEntities()) {
                        if (entity instanceof net.minecraft.entity.ItemEntity) {
                            alive.add(entity.getUuid());
                        }
                    }
                }
                // 只保留仍然存在的实体UUID
                randomizedEntities.retainAll(alive);
            }
        }

        // 检查是否到达随机化间隔，触发重新随机化
        long intervalTicks = (long) state.getRandomizeIntervalMinutes() * 60 * 20;
        if (server.getTicks() - state.getLastRandomizeTick() >= intervalTicks) {
            state.setLastRandomizeTick(server.getTicks());
            randomizeItems(server);
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.randomize_triggered"), false);
        }

        // 每100tick同步随机化倒计时到客户端（用于UI显示）
        if (server.getTicks() % 100 == 0) {
            for (ServerPlayerEntity player : players) {
                long remainingTicks = intervalTicks - (server.getTicks() - state.getLastRandomizeTick());
                if (remainingTicks < 0) remainingTicks = 0;
                ServerPlayNetworking.send(player, new ModPackets.RandomizeTimeSyncPayload(remainingTicks, intervalTicks));
            }
        }
    }

    /**
     * 玩家复活时的处理
     * <p>全随机玩法不需要在复活时做特殊处理</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
    }

    /**
     * 玩家受伤害时的处理
     * <p>全随机玩法不修改伤害逻辑</p>
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
        return true;
    }

    /**
     * 重置随机化计时器
     * <p>将上次随机化时间设为当前tick，相当于重新开始倒计时</p>
     * @param server Minecraft服务器实例
     */
    public void resetTimer(MinecraftServer server) {
        GameState state = GameState.getServerState(server);
        state.setLastRandomizeTick(server.getTicks());
    }

    /**
     * 随机化物品映射表
     * <p>遍历所有注册的物品，为每个物品随机分配一个替代物品。
     * 每次调用都会重新生成映射表。</p>
     * @param server Minecraft服务器实例
     */
    private void randomizeItems(MinecraftServer server) {
        itemMapping.clear();

        // 获取所有注册的物品
        List<Item> allItems = new ArrayList<>();
        Registries.ITEM.stream().forEach(allItems::add);

        if (allItems.isEmpty()) return;

        // 为每个物品创建随机映射
        Random random = new Random();
        for (Item item : allItems) {
            Identifier id = Registries.ITEM.getId(item);
            Item randomItem = allItems.get(random.nextInt(allItems.size()));
            itemMapping.put(id, randomItem);
        }
    }

    /**
     * 获取物品的随机化替代品
     * <p>如果映射表中没有该物品的记录，返回原始物品本身</p>
     * @param original 原始物品
     * @return 随机化后的替代物品，或原始物品（无映射时）
     */
    public Item getMappedItem(Item original) {
        Identifier id = Registries.ITEM.getId(original);
        return itemMapping.getOrDefault(id, original);
    }

    /**
     * 检查物品是否有随机化映射
     * @param original 原始物品
     * @return true=有映射记录，false=无映射
     */
    public boolean hasMapping(Item original) {
        return itemMapping.containsKey(Registries.ITEM.getId(original));
    }
}
