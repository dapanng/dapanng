package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 玩法抽象基类 - 所有玩法实现的顶层抽象类
 * <p>
 * 定义了玩法的通用接口和生命周期方法，所有具体玩法都必须继承此类并实现其抽象方法。
 * 生命周期：onStart（开始）→ onTick（每tick执行）→ onStop（结束）
 * 事件回调：onRespawn（玩家复活）、onDamage（玩家受伤）
 * </p>
 */
public abstract class BaseGameplay {
    
    /** 玩法唯一标识符，用于在GameState中标识当前激活的玩法 */
    protected final String id;
    
    /** 玩法显示名称，用于在UI和消息中展示给玩家 */
    protected final String displayName;
    
    /**
     * 构造方法
     * @param id 玩法唯一标识符（如 "sacrifice"、"chase" 等）
     * @param displayName 玩法显示名称（如 "献祭玩法"、"追击玩法" 等）
     */
    protected BaseGameplay(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
    
    /**
     * 获取玩法唯一标识符
     * @return 玩法ID字符串
     */
    public String getId() { return id; }
    
    /**
     * 获取玩法显示名称
     * @return 玩法显示名称字符串
     */
    public String getDisplayName() { return displayName; }
    
    /**
     * 玩法开始时的初始化回调
     * <p>当玩法被激活时调用，用于初始化玩法所需的状态和属性</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态，用于存取玩法相关的共享数据
     * @param players 当前所有在线玩家列表
     */
    public abstract void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players);
    
    /**
     * 玩法结束时的清理回调
     * <p>当玩法被停用或切换时调用，用于清理修饰符、效果和恢复原始状态</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    public abstract void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players);
    
    /**
     * 服务器tick时的处理回调（每tick调用一次，即每秒20次）
     * <p>用于实现玩法的持续逻辑，如状态同步、效果刷新、条件检测等</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    public abstract void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players);
    
    /**
     * 玩家复活时的处理回调
     * <p>当玩家死亡后重生时调用，用于重新应用玩法相关的修饰符和效果</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    public abstract void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player);
    
    /**
     * 玩家受伤害时的处理回调
     * <p>在伤害应用到玩家之前调用，可用于修改伤害量、阻止伤害或触发额外效果</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 受伤的玩家实例
     * @param source 伤害来源（如摔落、生物攻击等）
     * @param amount 原始伤害量
     * @return 是否允许伤害继续处理（true=允许原始伤害通过，false=阻止原始伤害）
     */
    public abstract boolean onDamage(MinecraftServer server, GameState state, ServerPlayerEntity player, 
                                     net.minecraft.entity.damage.DamageSource source, float amount);
}
