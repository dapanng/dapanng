package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * 加速玩法实现 - 疯狂世界
 * <p>
 * 核心机制：
 * 1. 所有非玩家生物获得速度和急迫效果 - 生物移动和攻击速度大幅提升
 * 2. 使用StatusEffectInstance而非属性修饰符 - 确保所有生物（包括新刷新的）都受影响
 * 3. 效果持续时间短，需要定期刷新 - 避免效果在玩法停止后残留
 * 4. 效果等级由speedMultiplier配置决定 - 可调节加速程度
 * </p>
 */
public class SpeedGameplay extends BaseGameplay {

    /** 效果刷新间隔（tick），每40tick（2秒）刷新一次效果 */
    private static final int EFFECT_REFRESH_INTERVAL = 40;
    /** 速度效果持续时间（tick），略长于刷新间隔，确保效果不中断 */
    private static final int SPEED_DURATION = 60;
    /** 急迫效果持续时间（tick），略长于刷新间隔，确保效果不中断 */
    private static final int HASTE_DURATION = 60;

    /**
     * 构造方法 - 初始化加速玩法的ID和显示名称
     */
    public SpeedGameplay() {
        super("speed", "加速玩法");
    }

    /**
     * 玩法开始时的初始化
     * <p>立即为所有非玩家生物应用速度和急迫效果</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        applyEffectsToAllEntities(server, state);
    }

    /**
     * 玩法结束时的清理
     * <p>移除所有非玩家生物的速度和急迫效果，恢复正常速度</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 移除所有非玩家生物的速度和急迫效果
        removeAllEffects(server);
    }

    /**
     * 每tick执行的处理逻辑
     * <p>定期刷新效果（每40tick），确保新刷新的生物也能获得效果，
     * 且效果不会因过期而消失</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 定期刷新效果，覆盖新刷新的生物
        if (server.getTicks() % EFFECT_REFRESH_INTERVAL == 0) {
            applyEffectsToAllEntities(server, state);
        }
    }

    /**
     * 玩家复活时的处理
     * <p>加速玩法不需要在复活时做特殊处理</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
    }

    /**
     * 玩家受伤害时的处理
     * <p>加速玩法不修改伤害逻辑</p>
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
     * 为所有非玩家生物应用速度和急迫效果
     * <p>效果等级 = speedMultiplier - 1（因为等级0已经是1倍速）。
     * 效果不显示粒子（ambient=true），不显示HUD图标（showParticles=false）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     */
    private void applyEffectsToAllEntities(MinecraftServer server, GameState state) {
        // 效果等级：speedMultiplier为1时等级0（正常速度），2时等级1（速度II），以此类推
        int speedLevel = Math.max(0, (int) state.getSpeedMultiplier() - 1);
        int hasteLevel = Math.max(0, (int) state.getSpeedMultiplier() - 1);

        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                // 只对非玩家的活体生物生效
                if (entity instanceof LivingEntity living && !(entity instanceof PlayerEntity)) {
                    // 速度效果 - 增加移动速度
                    living.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED, SPEED_DURATION, speedLevel, false, false));
                    // 急迫效果 - 增加攻击速度和挖掘速度
                    living.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.HASTE, HASTE_DURATION, hasteLevel, false, false));
                }
            }
        }
    }

    /**
     * 移除所有非玩家生物的速度和急迫效果
     * <p>玩法结束时调用，恢复所有生物的正常速度</p>
     * @param server Minecraft服务器实例
     */
    private void removeAllEffects(MinecraftServer server) {
        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                if (entity instanceof LivingEntity living && !(entity instanceof PlayerEntity)) {
                    living.removeStatusEffect(StatusEffects.SPEED);
                    living.removeStatusEffect(StatusEffects.HASTE);
                }
            }
        }
    }
}
