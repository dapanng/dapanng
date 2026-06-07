package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.*;

/**
 * 肉鸽玩法实现 - 区块封锁挑战
 * <p>
 * 核心机制：
 * 1. 玩家每进入新区块时，在周围生成随机生物 - 增加探索风险
 * 2. 只有击杀所有生成的生物后才能离开该区块 - 强制战斗
 * 3. 尝试离开未清理区块时会被传送回区块中心 - 无法逃跑
 * 4. 生成的生物被限制在其出生区块内 - 不会追到其他区块
 * 5. 玩家死亡时清除所有未击杀的肉鸽生物 - 死亡重置
 * 6. UI可调节每次生成的生物数量
 * </p>
 */
public class RoguelikeGameplay extends BaseGameplay {
    
    /** 随机数生成器，用于生物类型选择和位置偏移 */
    private static final Random RANDOM = Random.create();
    
    /**
     * 肉鸽生物的出生区块记录（生物UUID → 区块坐标编码值）
     * <p>用于限制生物不离开出生区块，以及清理时识别肉鸽生物</p>
     */
    private final Map<UUID, Long> mobSpawnChunk = new HashMap<>();
    
    /**
     * 可生成的生物类型列表
     * <p>包含1.21.1版本中大部分生物类型，既有敌对的也有和平的，增加随机性</p>
     */
    private static final List<EntityType<?>> POSSIBLE_MOBS = Arrays.asList(
        EntityType.CREEPER, EntityType.SKELETON, EntityType.SPIDER, EntityType.ZOMBIE,
        EntityType.SLIME, EntityType.GHAST, EntityType.PIG, EntityType.COW,
        EntityType.SHEEP, EntityType.CHICKEN, EntityType.WOLF, EntityType.CAT,
        EntityType.VILLAGER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.BLAZE,
        EntityType.SILVERFISH, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.OCELOT,
        EntityType.PARROT, EntityType.VEX, EntityType.EVOKER, EntityType.VINDICATOR,
        EntityType.PILLAGER, EntityType.RAVAGER, EntityType.PHANTOM, EntityType.TURTLE,
        EntityType.PANDA, EntityType.WANDERING_TRADER, EntityType.LLAMA,
        EntityType.TRADER_LLAMA, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
        EntityType.MOOSHROOM, EntityType.PIGLIN, EntityType.HOGLIN,
        EntityType.STRIDER, EntityType.WARDEN, EntityType.BOGGED, EntityType.FROG,
        EntityType.TADPOLE, EntityType.GLOW_SQUID, EntityType.GOAT, EntityType.AXOLOTL,
        EntityType.CAMEL, EntityType.DOLPHIN, EntityType.TROPICAL_FISH, EntityType.PUFFERFISH,
        EntityType.SALMON, EntityType.COD, EntityType.SQUID, EntityType.BAT,
        EntityType.BEE, EntityType.ENDERMITE, EntityType.CAVE_SPIDER, EntityType.WITHER_SKELETON,
        EntityType.STRAY, EntityType.HUSK, EntityType.DROWNED, EntityType.SHULKER,
        EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.MAGMA_CUBE, EntityType.PIGLIN_BRUTE
    );
    
    /**
     * 构造方法 - 初始化肉鸽玩法的ID和显示名称
     */
    public RoguelikeGameplay() {
        super("roguelike", "肉鸽玩法");
    }
    
    /**
     * 玩法开始时的初始化
     * <p>记录每个玩家的当前区块位置，初始化封锁状态和待击杀生物计数</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            // 记录玩家当前区块位置（编码为long：低32位=X，高32位=Z）
            BlockPos pos = player.getBlockPos();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            long chunkPos = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
            state.setPlayerLastChunkPos(player.getUuid(), chunkPos);
            // 初始状态：未被封锁
            state.setPlayerBlockedInChunk(player.getUuid(), false);
            // 清空待击杀生物计数
            state.clearPlayerPendingMobs(player.getUuid());
        }
    }
    
    /**
     * 玩法结束时的清理
     * <p>清除所有玩家的待击杀生物计数，清空出生区块记录</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            state.clearPlayerPendingMobs(player.getUuid());
        }
        mobSpawnChunk.clear();
    }
    
    /**
     * 每tick执行的处理逻辑
     * <p>每tick执行两个核心任务：
     * 1. 限制肉鸽生物不离开出生区块
     * 2. 检测玩家是否进入新区块，触发生物生成和封锁逻辑</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 限制肉鸽生物不离开出生区块
        restrictMobsToChunk(server);

        for (ServerPlayerEntity player : players) {
            if (player.isDead()) continue;
            
            // 计算当前区块坐标（编码为long）
            BlockPos pos = player.getBlockPos();
            int curChunkX = pos.getX() >> 4;
            int curChunkZ = pos.getZ() >> 4;
            long currentChunk = (long) curChunkX & 0xFFFFFFFFL | ((long) curChunkZ & 0xFFFFFFFFL) << 32;
            long lastChunk = state.getPlayerLastChunkPos(player.getUuid());
            
            // 检查是否尝试离开未清理区块 → 传送回区块中心
            if (state.isPlayerBlockedInChunk(player.getUuid()) && currentChunk != lastChunk) {
                // 解码区块坐标，计算区块中心方块坐标
                int chunkX = (int)(lastChunk & 0xFFFFFFFFL);
                int chunkZ = (int)(lastChunk >> 32);
                int blockX = chunkX * 16 + 8; // 区块中心X
                int blockZ = chunkZ * 16 + 8; // 区块中心Z
                // 找到安全的Y坐标（最高非空气方块）
                int safeY = player.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, blockX, blockZ);
                player.teleport((double) blockX, (double) safeY, (double) blockZ, false);
                player.sendMessage(Text.translatable("msg.sacrificemod.roguelike_cannot_leave"), false);
                continue; // 不更新lastChunk，保持封锁状态
            }

            // 检测是否进入新区块
            if (currentChunk != lastChunk) {
                // 更新区块位置并触发封锁
                state.setPlayerLastChunkPos(player.getUuid(), currentChunk);
                state.setPlayerBlockedInChunk(player.getUuid(), true);
                state.clearPlayerPendingMobs(player.getUuid());
                
                // 生成随机数量的生物
                int mobCount = state.getRoguelikeMobCount();
                for (int i = 0; i < mobCount; i++) {
                    spawnRandomMob(player);
                    state.incrementPlayerPendingMobs(player.getUuid());
                }
                
                player.sendMessage(
                    Text.translatable("msg.sacrificemod.roguelike_mobs_spawned", mobCount), false);
            }
        }
    }
    
    /**
     * 玩家复活时的处理
     * <p>清除所有未击杀的肉鸽生物，重新初始化玩家的区块位置和封锁状态</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        // 清除所有肉鸽生成的生物（给玩家重新开始的机会）
        removeRoguelikeMobs(server);
        
        // 重新初始化玩家的区块状态
        BlockPos pos = player.getBlockPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long chunkPos = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
        state.setPlayerLastChunkPos(player.getUuid(), chunkPos);
        state.setPlayerBlockedInChunk(player.getUuid(), false);
    }
    
    /**
     * 玩家受伤害时的处理
     * <p>肉鸽玩法不修改伤害逻辑</p>
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
     * 处理实体被击杀事件
     * <p>当肉鸽生成的生物被击杀时，减少对应玩家的待击杀计数。
     * 当所有生物被击杀后，解除区块封锁。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param entity 被击杀的实体
     */
    public void onEntityKill(MinecraftServer server, GameState state, LivingEntity entity) {
        // 验证肉鸽玩法是否激活
        if (!state.isGameplayActive("roguelike")) return;
        // 只处理带有肉鸽标签的生物
        if (!entity.getCommandTags().contains("sacrificemod_roguelike")) return;
        
        // 清除出生区块记录
        mobSpawnChunk.remove(entity.getUuid());
        
        // 找到64格范围内最近的玩家并减少其待击杀计数
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() == entity.getWorld() && 
                player.getBlockPos().isWithinDistance(entity.getBlockPos(), 64)) {
                state.decrementPlayerPendingMobs(player.getUuid());
                
                int remaining = state.getPlayerPendingMobs(player.getUuid());
                if (remaining <= 0) {
                    // 所有生物已击杀，解除区块封锁
                    state.setPlayerBlockedInChunk(player.getUuid(), false);
                    player.sendMessage(Text.translatable("msg.sacrificemod.roguelike_cleared"), false);
                } else {
                    // 提示剩余生物数量
                    player.sendMessage(Text.translatable("msg.sacrificemod.roguelike_remaining", remaining), false);
                }
                break; // 只减少最近的一个玩家的计数
            }
        }
    }

    /**
     * 在玩家所在区块内生成一个随机生物
     * <p>生物生成在玩家区块内距玩家不超过8格的位置，带有"sacrificemod_roguelike"标签和高亮效果</p>
     * @param player 目标玩家，生物在其区块内生成
     */
    private void spawnRandomMob(ServerPlayerEntity player) {
        World world = player.getWorld();
        BlockPos pos = player.getBlockPos();
        
        // 计算玩家所在区块的边界范围，确保生物生成在玩家区块内
        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;
        int chunkMinX = playerChunkX * 16;
        int chunkMinZ = playerChunkZ * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        
        // 在区块内随机选一个位置，距玩家不超过8格（使用clamp确保不超出区块边界）
        double x = MathHelper.clamp(pos.getX() + (RANDOM.nextDouble() - 0.5) * 16, chunkMinX, chunkMaxX);
        double z = MathHelper.clamp(pos.getZ() + (RANDOM.nextDouble() - 0.5) * 16, chunkMinZ, chunkMaxZ);
        // Y坐标取玩家Y和地面最高点的较大值
        int safeY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) x, (int) z);
        double y = Math.max(pos.getY(), safeY);
        
        // 随机选择生物类型
        EntityType<?> type = POSSIBLE_MOBS.get(RANDOM.nextInt(POSSIBLE_MOBS.size()));
        var entity = type.create(world);
        if (entity != null) {
            entity.setPosition(x, y, z);
            // 初始化生物装备（武器、盔甲等），使其具有适当的战斗力
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                mob.initialize((net.minecraft.server.world.ServerWorld) world,
                    world.getLocalDifficulty(new BlockPos((int) x, (int) y, (int) z)),
                    net.minecraft.entity.SpawnReason.COMMAND, null);
            }
            // 确保生物满血
            if (entity instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }
            entity.addCommandTag("sacrificemod_roguelike"); // 添加肉鸽标签，用于识别
            entity.setGlowing(true); // 高亮显示，让玩家能找到所有需要击杀的生物
            world.spawnEntity(entity);
            
            // 记录生物出生区块（用于限制其不离开出生区块）
            int chunkX = (int) x >> 4;
            int chunkZ = (int) z >> 4;
            long chunkPos = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
            mobSpawnChunk.put(entity.getUuid(), chunkPos);
        }
    }
    
    /**
     * 清除所有肉鸽生成的生物
     * <p>玩家死亡时调用，遍历所有世界查找带有肉鸽标签的实体并移除</p>
     * @param server Minecraft服务器实例
     */
    private void removeRoguelikeMobs(MinecraftServer server) {
        for (var world : server.getWorlds()) {
            List<net.minecraft.entity.Entity> toRemove = new ArrayList<>();
            for (var entity : world.iterateEntities()) {
                if (entity.getCommandTags().contains("sacrificemod_roguelike") && !entity.isRemoved()) {
                    toRemove.add(entity);
                }
            }
            for (var entity : toRemove) {
                mobSpawnChunk.remove(entity.getUuid());
                entity.discard(); // 直接移除，不播放死亡动画
            }
        }
    }
    
    /**
     * 限制肉鸽生成的生物不离开其出生区块
     * <p>每tick检查所有肉鸽生物的位置，如果离开了出生区块则传送回区块中心。
     * 同时清理已消失的生物记录，防止内存泄漏。</p>
     * @param server Minecraft服务器实例
     */
    private void restrictMobsToChunk(MinecraftServer server) {
        if (mobSpawnChunk.isEmpty()) return;
        
        Set<UUID> deadMobs = new HashSet<>();
        
        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                // 只处理肉鸽生物
                if (!entity.getCommandTags().contains("sacrificemod_roguelike")) continue;
                
                UUID uuid = entity.getUuid();
                Long spawnChunk = mobSpawnChunk.get(uuid);
                if (spawnChunk == null) continue;
                
                // 检查生物是否已死亡或消失
                if (entity.isRemoved()) {
                    deadMobs.add(uuid);
                    continue;
                }
                
                // 获取当前区块坐标
                int curChunkX = entity.getBlockPos().getX() >> 4;
                int curChunkZ = entity.getBlockPos().getZ() >> 4;
                long currentChunk = (long) curChunkX & 0xFFFFFFFFL | ((long) curChunkZ & 0xFFFFFFFFL) << 32;
                
                // 如果离开了出生区块，传送回区块中心
                if (currentChunk != spawnChunk) {
                    int spawnCX = (int)(spawnChunk & 0xFFFFFFFFL);
                    int spawnCZ = (int)(spawnChunk >> 32);
                    int blockX = spawnCX * 16 + 8;
                    int blockZ = spawnCZ * 16 + 8;
                    int safeY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, blockX, blockZ);
                    entity.requestTeleport((double) blockX, (double) safeY, (double) blockZ);
                }
            }
        }
        
        // 清理已消失的生物记录，防止内存泄漏
        deadMobs.forEach(mobSpawnChunk::remove);
    }
}
