package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

/**
 * 传送玩法实现 - 分散与共享
 * <p>
 * 核心机制：
 * 1. 所有玩家背包共享 - 以第一个玩家的背包为基准，定期同步给其他玩家
 * 2. 玩家受伤时所有玩家随机传送到不同位置 - 受伤触发全员传送
 * 3. 传送冷却2秒 - 防止连续受伤导致频繁传送
 * 4. 传送目标维度随机：主世界60%、地狱30%、末地10%
 * 5. 传送坐标随机：主世界±10000，地狱±300
 * 6. 复活时同步背包 - 确保新复活的玩家拥有最新背包
 * </p>
 */
public class TeleportGameplay extends BaseGameplay {

    /** 随机数生成器，用于传送坐标和维度选择 */
    private static final Random RANDOM = new Random();
    /** 传送冷却时间（tick），40tick = 2秒，防止连续受伤导致频繁传送 */
    private static final int TELEPORT_COOLDOWN = 40;
    /** 上次触发传送的服务器tick，用于冷却判断 */
    private long lastTeleportTick = 0;
    /** 背包同步冷却计数器（tick），防止背包同步过于频繁导致性能问题 */
    private int syncCooldown = 0;

    /**
     * 构造方法 - 初始化传送玩法的ID和显示名称
     */
    public TeleportGameplay() {
        super("teleport", "传送玩法");
    }

    /**
     * 玩法开始时的初始化
     * <p>将第一个玩家的背包复制给所有其他玩家，实现背包共享。
     * 初始化传送冷却计时器。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 初始化共享背包：将第一个玩家的背包复制给所有其他玩家
        if (players.size() >= 2) {
            var firstPlayer = players.get(0);
            for (int i = 1; i < players.size(); i++) {
                copyInventory(firstPlayer, players.get(i));
            }
        }
        // 初始化传送冷却，避免开始就触发传送
        lastTeleportTick = server.getTicks();
    }

    /**
     * 玩法结束时的清理
     * <p>停止时不需要特殊处理，每个玩家保留当前背包状态</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 停止时不需要特殊处理，每个玩家保留当前背包
    }

    /**
     * 每tick执行的处理逻辑
     * <p>每10tick同步一次所有玩家的背包（以第一个玩家为基准）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        if (players.size() < 2) return;

        // 每10tick同步一次背包
        syncCooldown--;
        if (syncCooldown <= 0) {
            syncCooldown = 10;
            syncInventories(server, players);
        }
    }

    /**
     * 玩家复活时的处理
     * <p>从其他玩家同步背包到复活玩家，确保背包内容一致</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        // 从其他玩家同步背包到复活玩家
        var players = server.getPlayerManager().getPlayerList();
        if (players.size() >= 2) {
            for (var other : players) {
                if (other != player) {
                    copyInventory(other, player);
                    break; // 只需要从任意一个玩家复制即可
                }
            }
        }
    }

    /**
     * 玩家受伤害时的处理
     * <p>任何玩家受伤时，如果传送冷却已过，则将所有玩家随机传送到不同位置</p>
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
        // 受伤时传送所有玩家（有冷却限制）
        long currentTick = server.getTicks();
        if (currentTick - lastTeleportTick >= TELEPORT_COOLDOWN) {
            lastTeleportTick = currentTick;
            teleportAllPlayersRandomly(server);
        }
        return true;
    }

    /**
     * 同步所有玩家的背包
     * <p>以第一个玩家的背包为基准，复制给其他所有玩家</p>
     * @param server Minecraft服务器实例
     * @param players 当前所有在线玩家列表
     */
    private void syncInventories(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (players.isEmpty()) return;
        var source = players.get(0);
        for (int i = 1; i < players.size(); i++) {
            copyInventory(source, players.get(i));
        }
    }

    /**
     * 复制源玩家的背包到目标玩家
     * <p>复制内容包括：主背包（0-35槽位）、副手（40槽位）、盔甲（36-39槽位）</p>
     * @param source 源玩家（背包来源）
     * @param target 目标玩家（背包复制目标）
     */
    private void copyInventory(ServerPlayerEntity source, ServerPlayerEntity target) {
        // 复制主背包所有槽位
        for (int i = 0; i < source.getInventory().size(); i++) {
            var sourceStack = source.getInventory().getStack(i);
            target.getInventory().setStack(i, sourceStack.copy());
        }
        // 复制副手物品（槽位40）
        var offHand = source.getOffHandStack();
        target.getInventory().setStack(40, offHand.copy());
        // 复制盔甲（槽位36-39：靴子、护腿、胸甲、头盔）
        for (int i = 0; i < 4; i++) {
            var armorStack = source.getInventory().getStack(36 + i);
            target.getInventory().setStack(36 + i, armorStack.copy());
        }
        // 刷新客户端容器界面
        target.currentScreenHandler.sendContentUpdates();
    }

    /**
     * 将所有玩家随机传送到不同位置
     * <p>每个玩家独立随机选择维度和坐标，并广播传送提示</p>
     * @param server Minecraft服务器实例
     */
    private void teleportAllPlayersRandomly(MinecraftServer server) {
        var players = server.getPlayerManager().getPlayerList();
        for (var player : players) {
            teleportPlayerRandomly(server, player);
        }
        server.getPlayerManager().broadcast(
            Text.translatable("msg.sacrificemod.teleport_triggered"), false);
    }

    /**
     * 将单个玩家随机传送到任意维度的安全位置
     * <p>维度选择概率：主世界60%、地狱30%、末地10%
     * 坐标范围：主世界±10000，地狱±300
     * 自动寻找安全Y坐标，找不到则传送到出生点</p>
     * @param server Minecraft服务器实例
     * @param player 要传送的玩家
     */
    private void teleportPlayerRandomly(MinecraftServer server, ServerPlayerEntity player) {
        // 获取三个维度世界实例
        var overworld = server.getWorld(World.OVERWORLD);
        var nether = server.getWorld(World.NETHER);
        var end = server.getWorld(World.END);

        // 按概率选择目标维度
        ServerWorld targetWorld;
        int roll = RANDOM.nextInt(100);
        if (roll < 60) {
            targetWorld = overworld;   // 60%概率：主世界
        } else if (roll < 90) {
            targetWorld = nether;      // 30%概率：地狱
        } else {
            targetWorld = end;         // 10%概率：末地
        }

        // 安全检查：如果目标世界不存在，默认传送到主世界
        if (targetWorld == null) {
            targetWorld = overworld;
        }

        // 根据维度选择随机坐标范围
        int x, z;
        if (targetWorld == nether) {
            // 地狱范围较小，避免传送到基岩层外
            x = RANDOM.nextInt(600) - 300;
            z = RANDOM.nextInt(600) - 300;
        } else {
            // 主世界/末地范围较大
            x = RANDOM.nextInt(20000) - 10000;
            z = RANDOM.nextInt(20000) - 10000;
        }

        // 确保目标区块已加载
        targetWorld.getChunk(x >> 4, z >> 4);

        // 获取安全的Y坐标
        int y = findSafeY(targetWorld, x, z);

        // 安全检查：如果找不到安全位置，传送到出生点
        if (y < targetWorld.getBottomY() + 1) {
            var spawnPos = targetWorld.getSpawnPos();
            x = spawnPos.getX();
            y = spawnPos.getY();
            z = spawnPos.getZ();
        }

        // 执行传送（保留玩家朝向）
        player.teleport(targetWorld, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch());
    }

    /**
     * 在指定维度中寻找安全的Y坐标
     * <p>对于有天花板的维度（地狱），从下往上扫描找到第一个可站立位置。
     * 对于无天花板的维度（主世界/末地），使用MOTION_BLOCKING高度图。</p>
     * @param world 目标世界
     * @param x 方块X坐标
     * @param z 方块Z坐标
     * @return 安全的Y坐标，如果找不到返回世界底部Y
     */
    private int findSafeY(ServerWorld world, int x, int z) {
        if (world.getDimension().hasCeiling()) {
            // 地狱：从基岩层之上开始向上扫描，找到第一个可站立位置
            // 跳过底部基岩层（Y=0~4）和顶部基岩层（Y=123+）
            int startY = Math.max(world.getBottomY() + 5, 6);
            int maxY = Math.min(world.getTopY() - 5, 122);
            for (int y = startY; y < maxY; y++) {
                var below = world.getBlockState(new BlockPos(x, y - 1, z)); // 脚下方块
                var feet = world.getBlockState(new BlockPos(x, y, z));      // 脚部方块
                var head = world.getBlockState(new BlockPos(x, y + 1, z));  // 头部方块
                // 可站立条件：脚下是实心方块，脚部和头部是空气
                if (!below.isAir() && below.isSolid() && feet.isAir() && head.isAir()) {
                    return y;
                }
            }
            // 找不到安全位置，返回世界底部Y（将触发出生点回退）
            return world.getBottomY();
        } else {
            // 主世界/末地：使用MOTION_BLOCKING高度图获取地面高度
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            // 如果MOTION_BLOCKING失败，尝试WORLD_SURFACE
            if (y < world.getBottomY() + 1) {
                y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            }
            return y;
        }
    }
}
