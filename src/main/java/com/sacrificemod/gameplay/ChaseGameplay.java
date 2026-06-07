package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import com.sacrificemod.mixin.MobEntityAccessor;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 追击玩法实现 - 全民公敌模式
 * <p>
 * 核心机制：
 * 1. 500格内所有生物追杀最近的玩家 - 无处可逃
 * 2. 敌对生物(HostileEntity)通过setTarget让AI自动追击和攻击 - 利用原版AI
 * 3. 非敌对生物(和平/水生/环境等)没有攻击AI，需要：
 *    a. 通过Mixin Accessor清除其goalSelector和targetSelector，防止AI覆盖我们的导航
 *    b. 使用导航让它们走向玩家
 *    c. 靠近后直接造成伤害
 * 4. 水生生物导航失败时传送到玩家附近
 * 5. 停止后清除所有生物的攻击目标，恢复非敌对生物的基本AI
 * </p>
 */
public class ChaseGameplay extends BaseGameplay {

    /** 追击范围（格），超过此距离的生物不会追击玩家 */
    private static final double CHASE_RANGE = 500.0;
    /** 非敌对生物攻击伤害值 */
    private static final float NON_HOSTILE_ATTACK_DAMAGE = 4.0f;
    /** 非敌对生物寻路速度倍率 */
    private static final double NON_HOSTILE_MOVE_SPEED = 1.5;
    /** 非敌对生物攻击冷却时间（tick），防止连续攻击 */
    private static final int NON_HOSTILE_ATTACK_COOLDOWN = 30;

    /** 已清除AI的非敌对生物UUID集合，避免重复清除 */
    private final Set<UUID> clearedAiMobs = new HashSet<>();
    /** 每个非敌对生物的上次攻击时间（tick），用于攻击冷却 */
    private final Map<UUID, Long> lastAttackTickMap = new HashMap<>();

    /**
     * 构造方法 - 初始化追击玩法的ID和显示名称
     */
    public ChaseGameplay() {
        super("chase", "追击玩法");
    }

    /**
     * 玩法开始时的初始化
     * <p>清空已清除AI生物记录和攻击冷却记录</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        clearedAiMobs.clear();
        lastAttackTickMap.clear();
    }

    /**
     * 玩法结束时的清理
     * <p>恢复非敌对生物的基本AI（添加游荡和看向玩家目标），
     * 清除敌对生物的攻击目标，清空内部记录</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 恢复非敌对生物的基本AI（添加游荡和看向玩家目标）
        restoreNonHostileAI(server);
        // 清除敌对生物的攻击目标
        clearHostileTargets(server);
        // 清空内部记录
        clearedAiMobs.clear();
        lastAttackTickMap.clear();
    }

    /**
     * 每tick执行的处理逻辑
     * <p>遍历所有世界中的生物：
     * - 敌对生物：每20tick设置攻击目标为最近的玩家
     * - 非敌对生物：清除AI后手动控制寻路和攻击</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        if (players.isEmpty()) return;

        long currentTick = server.getTicks();

        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                // 跳过玩家实体
                if (entity instanceof ServerPlayerEntity) continue;
                // 只处理MobEntity（有AI的生物）
                if (!(entity instanceof MobEntity mob)) continue;
                if (!mob.isAlive()) continue;

                // 在追击范围内寻找最近的玩家
                ServerPlayerEntity nearest = null;
                double nearestDist = CHASE_RANGE * CHASE_RANGE; // 使用距离平方比较，避免开方运算
                for (ServerPlayerEntity player : players) {
                    // 必须在同一维度
                    if (player.getWorld() == entity.getWorld()) {
                        double dist = entity.squaredDistanceTo(player);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = player;
                        }
                    }
                }
                // 范围内没有玩家则跳过
                if (nearest == null) continue;

                if (mob instanceof HostileEntity) {
                    // 敌对生物：有攻击AI，只需设置目标即可自动追击和攻击
                    if (currentTick % 20 == 0) { // 每秒更新一次目标
                        mob.setTarget(nearest);
                    }
                } else {
                    // 非敌对生物：需要清除AI后手动控制寻路和攻击
                    UUID mobUuid = mob.getUuid();

                    // 首次遇到该生物时，清除其AI目标（防止原版AI覆盖我们的控制）
                    if (!clearedAiMobs.contains(mobUuid)) {
                        clearedAiMobs.add(mobUuid);
                        var accessor = (MobEntityAccessor) mob;
                        accessor.sacrificemod$getGoalSelector().clear(goal -> true);
                        accessor.sacrificemod$getTargetSelector().clear(goal -> true);
                        mob.setTarget(null);
                        mob.getNavigation().stop();
                    }

                    // 设置攻击目标
                    mob.setTarget(nearest);

                    // 寻路：让生物走向玩家（每10tick更新一次路径）
                    if (currentTick % 10 == 0) {
                        boolean navSuccess = mob.getNavigation().startMovingTo(nearest, NON_HOSTILE_MOVE_SPEED);
                        // 导航失败时不传送，仅依赖近距离的直接伤害机制
                    }

                    // 伤害检测：非敌对生物在攻击范围内直接对玩家造成伤害
                    double attackRange = state.getChaseAttackRange();
                    double attackRangeSq = attackRange * attackRange;
                    if (nearestDist <= attackRangeSq) {
                        long lastAttack = lastAttackTickMap.getOrDefault(mobUuid, 0L);
                        // 检查攻击冷却
                        if (currentTick - lastAttack >= NON_HOSTILE_ATTACK_COOLDOWN) {
                            lastAttackTickMap.put(mobUuid, currentTick);
                            var damageSource = world.getDamageSources().mobAttack(mob);
                            nearest.damage(damageSource, NON_HOSTILE_ATTACK_DAMAGE);
                            // 让生物面朝玩家（提供视觉反馈）
                            mob.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, nearest.getPos());
                        }
                    }
                }
            }
        }
    }

    /**
     * 玩家复活时的处理
     * <p>追击玩法不需要在复活时做特殊处理</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
    }

    /**
     * 玩家受伤害时的处理
     * <p>追击玩法不修改伤害逻辑，允许所有伤害正常处理</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 受伤的玩家实例
     * @param source 伤害来源
     * @param amount 伤害量
     * @return 始终返回true，允许伤害正常处理
     */
    @Override
    public boolean onDamage(MinecraftServer server, GameState state, ServerPlayerEntity player,
                          net.minecraft.entity.damage.DamageSource source, float amount) {
        return true;
    }

    /**
     * 恢复非敌对生物的基本AI
     * <p>为已清除AI的生物重新添加WanderAroundGoal（游荡）和LookAtEntityGoal（看向玩家），
     * 清除攻击目标并停止导航</p>
     * @param server Minecraft服务器实例
     */
    private void restoreNonHostileAI(MinecraftServer server) {
        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                if (!(entity instanceof MobEntity mob)) continue;
                // 跳过敌对生物（它们的AI未被修改）
                if (mob instanceof HostileEntity) continue;

                UUID mobUuid = mob.getUuid();
                // 只恢复被我们清除过AI的生物
                if (clearedAiMobs.contains(mobUuid)) {
                    var accessor = (MobEntityAccessor) mob;
                    // 先清除所有残留的目标
                    accessor.sacrificemod$getGoalSelector().clear(goal -> true);
                    accessor.sacrificemod$getTargetSelector().clear(goal -> true);

                    // PathAwareEntity（大多数非敌对生物）可以添加基本AI
                    if (mob instanceof PathAwareEntity pathAware) {
                        // 优先级0：游荡目标
                        accessor.sacrificemod$getGoalSelector().add(0, new WanderAroundGoal(pathAware, 1.0));
                        // 优先级1：看向玩家目标
                        accessor.sacrificemod$getGoalSelector().add(1, new LookAtEntityGoal(mob, PlayerEntity.class, 8.0f));
                    }

                    // 清除攻击目标并停止导航
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
            }
        }
    }

    /**
     * 清除所有敌对生物的攻击目标
     * <p>玩法结束时调用，让敌对生物不再追击玩家</p>
     * @param server Minecraft服务器实例
     */
    private void clearHostileTargets(MinecraftServer server) {
        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                if (entity instanceof HostileEntity hostile) {
                    hostile.setTarget(null);
                }
            }
        }
    }
}
