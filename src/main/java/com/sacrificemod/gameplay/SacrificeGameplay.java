package com.sacrificemod.gameplay;

import com.sacrificemod.BodyPart;
import com.sacrificemod.GameState;
import com.sacrificemod.ModPackets;
import com.sacrificemod.SacrificeMod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * 献祭玩法实现 - 核心玩法之一
 * <p>
 * 核心机制：
 * 1. 所有玩家共享生命值和饥饿度 - 任何玩家的生命/饥饿变化都会同步到所有玩家
 * 2. 跳跃次数达到阈值时召唤敌对生物 - 鼓励玩家减少不必要的跳跃
 * 3. 玩家可献祭身体部位进行抽奖 - 献祭后获得随机效果（可能增加或减少最大生命值）
 * 4. 每次死亡减少最大生命值 - 死亡惩罚机制，最大生命值最低为2
 * 5. 献祭身体部位后获得对应debuff - 失去头部→失明，失去手→挖掘疲劳/缓慢，失去腿→缓慢
 * 6. 可用钻石赎回已献祭的身体部位
 * </p>
 */
public class SacrificeGameplay extends BaseGameplay {
    
    /** 最大生命值属性修饰符的标识符，用于修改玩家的最大生命值上限 */
    private static final Identifier MAX_HEALTH_MODIFIER_ID = Identifier.of("sacrificemod", "sacrifice_max_health");
    
    /**
     * 可召唤的敌对生物类型列表
     * <p>当跳跃次数达到阈值时，从此列表中随机选择生物类型进行召唤</p>
     */
    private static final List<net.minecraft.entity.EntityType<?>> HOSTILE_MOBS = List.of(
            net.minecraft.entity.EntityType.ZOMBIE, net.minecraft.entity.EntityType.SKELETON,
            net.minecraft.entity.EntityType.CREEPER, net.minecraft.entity.EntityType.SPIDER,
            net.minecraft.entity.EntityType.ENDERMAN, net.minecraft.entity.EntityType.WITCH,
            net.minecraft.entity.EntityType.BLAZE, net.minecraft.entity.EntityType.GHAST,
            net.minecraft.entity.EntityType.WITHER_SKELETON, net.minecraft.entity.EntityType.GUARDIAN,
            net.minecraft.entity.EntityType.RAVAGER, net.minecraft.entity.EntityType.EVOKER,
            net.minecraft.entity.EntityType.VINDICATOR, net.minecraft.entity.EntityType.PHANTOM,
            net.minecraft.entity.EntityType.HOGLIN, net.minecraft.entity.EntityType.PIGLIN_BRUTE,
            net.minecraft.entity.EntityType.CAVE_SPIDER, net.minecraft.entity.EntityType.WARDEN
    );
    
    /** 随机数生成器，用于抽奖和生物召唤等随机逻辑 */
    private static final Random RANDOM = new Random();
    
    /**
     * 构造方法 - 初始化献祭玩法的ID和显示名称
     */
    public SacrificeGameplay() {
        super("sacrifice", "献祭玩法");
    }
    
    /**
     * 玩法开始时的初始化
     * <p>重置共享生命值/饥饿度/跳跃计数/已丢失部位等状态，并为所有玩家应用初始最大生命值修饰符</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 初始化共享数据：生命值20，饥饿度20，饱和度5
        state.setSharedHealth(20.0f);
        state.setSharedMaxHealth(20.0f);
        state.setSharedHunger(20);
        state.setSharedSaturation(5.0f);
        // 重置跳跃计数和已丢失身体部位
        state.resetJumpCount();
        state.resetLostParts();
        // 清空同步数据缓存
        state.clearSyncData();
        
        // 为所有玩家应用初始最大生命值修饰符（20点）
        for (ServerPlayerEntity player : players) {
            applyMaxHealthModifier(player, 20.0f);
        }
    }
    
    /**
     * 玩法结束时的清理
     * <p>移除所有玩家的最大生命值修饰符和献祭相关的状态效果（失明、黑暗、虚弱、挖掘疲劳、缓慢）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 移除所有玩家的修饰符和献祭debuff
        for (ServerPlayerEntity player : players) {
            removeMaxHealthModifier(player);
            // 逐个移除献祭可能添加的状态效果
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        }
    }
    
    /**
     * 每tick执行的处理逻辑
     * <p>每tick同步所有玩家的生命值和饥饿度，应用献祭debuff，并将状态同步到客户端</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        if (players.isEmpty()) return;
        
        // 同步所有玩家的生命值和饥饿度到共享值
        syncHealthAndHunger(server, state, players);
        
        // 根据已丢失的身体部位应用对应的debuff
        applyDebuffs(server, state, players);
        
        // 将游戏状态同步到所有客户端（用于UI显示）
        syncToClients(server, state, players);
    }
    
    /**
     * 玩家复活时的处理
     * <p>消耗一次死亡惩罚机会，减少共享最大生命值2点（最低2点），并将所有玩家生命值设为新的最大值</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        // 检查是否还有死亡惩罚可用
        if (!state.consumeDeathPenalty()) return;
        
        // 减少最大生命值，最低保留2点（1颗心）
        float oldMax = state.getSharedMaxHealth();
        float newMax = Math.max(2, oldMax - 2);
        state.setSharedMaxHealth(newMax);
        // 复活后生命值恢复满
        state.setSharedHealth(newMax);
        
        // 更新所有玩家的最大生命值属性和当前生命值
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            applyMaxHealthModifier(p, newMax);
            if (!p.isDead()) {
                p.setHealth(newMax);
            }
            state.setLastSyncedHealth(p.getUuid(), newMax);
        }
        
        // 广播死亡惩罚提示
        server.getPlayerManager().broadcast(Text.translatable("msg.sacrificemod.death_penalty"), false);
    }
    
    /**
     * 玩家受伤害时的处理
     * <p>将伤害转移到共享生命值池，阻止原始伤害，同步所有玩家的生命值显示。
     * 当共享生命值归零时标记死亡事件。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 受伤的玩家实例
     * @param source 伤害来源
     * @param amount 伤害量
     * @return 始终返回false，阻止原始伤害处理（由共享生命值机制接管）
     */
    @Override
    public boolean onDamage(MinecraftServer server, GameState state, ServerPlayerEntity player,
                          DamageSource source, float amount) {
        // 从共享生命值中扣除伤害
        float newHealth = Math.max(0, state.getSharedHealth() - amount);
        state.setSharedHealth(newHealth);
        
        // 共享生命值归零时标记死亡
        if (newHealth <= 0) {
            state.markDeath();
        }
        
        // 同步生命值到所有存活的玩家
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isDead()) {
                p.setHealth(newHealth);
                state.setLastSyncedHealth(p.getUuid(), newHealth);
            }
        }
        
        return false; // 阻止原始伤害，由共享生命值机制统一处理
    }
    
    /**
     * 处理玩家献祭身体部位的请求
     * <p>献祭后进行抽奖，随机获得以下效果之一：
     * - 10%概率：最大生命值-2
     * - 10%概率：最大生命值-4
     * - 40%概率：最大生命值+2
     * - 40%概率：最大生命值+4
     * 献祭后还会获得对应身体部位的debuff</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 发起献祭的玩家
     * @param part 要献祭的身体部位
     */
    public void handleSacrifice(MinecraftServer server, GameState state, ServerPlayerEntity player, BodyPart part) {
        // 检查该部位是否已经献祭过
        if (state.getLostParts(player.getUuid()).contains(part)) {
            player.sendMessage(Text.translatable("msg.sacrificemod.part_already_lost"), false);
            return;
        }
        
        // 记录献祭的部位
        state.losePart(player.getUuid(), part);
        player.sendMessage(Text.translatable("msg.sacrificemod.sacrifice", player.getName().getString(), part.getDisplayName()), false);
        
        // 抽奖逻辑：0-100的随机数决定结果
        int result = RANDOM.nextInt(100);
        int healthChange = 0;
        
        if (result < 10) {      // 10%概率：-2最大生命
            healthChange = -2;
            state.setSharedMaxHealth(Math.max(2, state.getSharedMaxHealth() - 2));
        } else if (result < 20) { // 10%概率：-4最大生命
            healthChange = -4;
            state.setSharedMaxHealth(Math.max(2, state.getSharedMaxHealth() - 4));
        } else if (result < 60) { // 40%概率：+2最大生命
            healthChange = 2;
            state.setSharedMaxHealth(state.getSharedMaxHealth() + 2);
        } else {                // 40%概率：+4最大生命
            healthChange = 4;
            state.setSharedMaxHealth(state.getSharedMaxHealth() + 4);
        }
        
        // 根据抽奖结果选择对应的翻译键
        String resultKey = switch (healthChange) {
            case -2 -> "gui.sacrificemod.lottery_1";
            case -4 -> "gui.sacrificemod.lottery_2";
            case 2 -> "gui.sacrificemod.lottery_3";
            default -> "gui.sacrificemod.lottery_4";
        };
        
        // 广播抽奖结果给所有玩家
        server.getPlayerManager().broadcast(Text.translatable("msg.sacrificemod.lottery_result", Text.translatable(resultKey)), false);
        
        // 根据献祭的部位应用对应的debuff
        applyDebuffToPlayer(player, part);
        
        // 同步身体部位状态到所有客户端
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            int[] lostOrdinals = state.getLostParts(p.getUuid()).stream().mapToInt(Enum::ordinal).toArray();
            ServerPlayNetworking.send(p, new ModPackets.BodyPartSyncPayload(player.getUuid(), lostOrdinals));
        }
    }
    
    /**
     * 处理玩家赎回身体部位的请求
     * <p>使用钻石赎回已献祭的身体部位，赎回后移除对应的debuff</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 发起赎回的玩家
     * @param part 要赎回的身体部位
     */
    public void handleReclaim(MinecraftServer server, GameState state, ServerPlayerEntity player, BodyPart part) {
        // 检查该部位是否已献祭
        if (!state.getLostParts(player.getUuid()).contains(part)) {
            player.sendMessage(Text.translatable("msg.sacrificemod.part_not_lost"), false);
            return;
        }
        
        // 统计玩家背包中的钻石数量
        int diamondCount = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == net.minecraft.item.Items.DIAMOND) {
                diamondCount++;
            }
        }
        
        // 钻石不足时提示
        if (diamondCount < state.getReclaimDiamondCost()) {
            player.sendMessage(Text.translatable("msg.sacrificemod.no_diamonds", state.getReclaimDiamondCost()), false);
            return;
        }
        
        // 从背包中扣除所需数量的钻石
        int remaining = state.getReclaimDiamondCost();
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (remaining <= 0) break;
            var stack = player.getInventory().getStack(i);
            if (stack.getItem() == net.minecraft.item.Items.DIAMOND) {
                int remove = Math.min(remaining, stack.getCount());
                stack.decrement(remove);
                remaining -= remove;
            }
        }
        
        // 赎回身体部位
        state.reclaimPart(player.getUuid(), part);
        player.sendMessage(Text.translatable("msg.sacrificemod.reclaim", player.getName().getString(), part.getDisplayName()), false);
        
        // 移除该部位对应的debuff
        removeDebuffFromPlayer(player, part);
        
        // 同步身体部位状态到所有客户端
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            int[] lostOrdinals = state.getLostParts(p.getUuid()).stream().mapToInt(Enum::ordinal).toArray();
            ServerPlayNetworking.send(p, new ModPackets.BodyPartSyncPayload(player.getUuid(), lostOrdinals));
        }
    }
    
    /**
     * 处理玩家跳跃事件
     * <p>每次跳跃增加跳跃计数，达到阈值时重置计数并为所有玩家身边生成随机敌对生物</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 跳跃的玩家
     */
    public void handleJump(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        state.incrementJumpCount();
        
        // 跳跃次数达到阈值时触发生物召唤
        if (state.getJumpCount() >= state.getJumpThreshold()) {
            state.resetJumpCount();
            
            // 为每个玩家身边生成随机敌对生物
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                spawnRandomHostileMobs(p);
            }
            
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.mobs_spawned", state.getJumpThreshold()), false);
        }
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 同步所有玩家的生命值和饥饿度
     * <p>计算所有玩家生命值/饥饿度的变化量，更新共享值，然后同步到所有玩家。
     * 使用同步锁防止递归调用（setHealth会触发事件）。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    private void syncHealthAndHunger(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 防止递归同步：setHealth可能触发新的事件
        if (SacrificeMod.isSyncing()) return;
        SacrificeMod.setSyncing(true);
        
        // 第一步：确保所有存活玩家的最大生命值属性正确
        float maxHealth = state.getSharedMaxHealth();
        for (ServerPlayerEntity player : players) {
            if (player.isDead()) continue;
            // 检测偏差，超过0.01则重新应用修饰符
            if (Math.abs(player.getMaxHealth() - maxHealth) > 0.01f) {
                applyMaxHealthModifier(player, maxHealth);
            }
        }
        
        // 第二步：计算所有存活玩家的生命值和饥饿度变化量
        float healthDelta = 0;
        int hungerDelta = 0;
        int activePlayers = 0;
        
        for (ServerPlayerEntity player : players) {
            if (player.isDead()) continue;
            activePlayers++;
            
            // 生命值变化 = 当前值 - 上次同步值
            float currentHealth = player.getHealth();
            float lastHealth = state.getLastSyncedHealth(player.getUuid());
            healthDelta += (currentHealth - lastHealth);
            
            // 饥饿度变化 = 当前值 - 上次同步值
            int currentHunger = player.getHungerManager().getFoodLevel();
            int lastHunger = state.getLastSyncedHunger(player.getUuid());
            hungerDelta += (currentHunger - lastHunger);
        }
        
        // 第三步：更新共享生命值（限制在0~最大生命值范围内）
        if (healthDelta != 0 && activePlayers > 0) {
            float newHealth = Math.max(0, Math.min(state.getSharedMaxHealth(), state.getSharedHealth() + healthDelta));
            state.setSharedHealth(newHealth);
        }
        
        // 更新共享饥饿度（限制在0~20范围内）
        if (hungerDelta != 0 && activePlayers > 0) {
            int newHunger = Math.max(0, Math.min(20, state.getSharedHunger() + hungerDelta));
            state.setSharedHunger(newHunger);
        }
        
        // 第四步：将共享值同步到所有玩家
        for (ServerPlayerEntity player : players) {
            if (player.isDead()) {
                // 死亡玩家只更新缓存，不设置生命值
                state.setLastSyncedHealth(player.getUuid(), state.getSharedHealth());
                state.setLastSyncedHunger(player.getUuid(), state.getSharedHunger());
                continue;
            }
            // 生命值取共享值和玩家最大生命值中的较小值
            float targetHealth = Math.min(state.getSharedHealth(), player.getMaxHealth());
            player.setHealth(targetHealth);
            player.getHungerManager().setFoodLevel(state.getSharedHunger());
            state.setLastSyncedHealth(player.getUuid(), targetHealth);
            state.setLastSyncedHunger(player.getUuid(), state.getSharedHunger());
        }
        
        SacrificeMod.setSyncing(false);
    }
    
    /**
     * 根据已丢失的身体部位为所有玩家应用对应的debuff
     * <p>debuff对应关系：
     * - 头部：失明 + 黑暗
     * - 左手：挖掘疲劳
     * - 右手：缓慢（等级0）
     * - 左腿/右腿：缓慢（等级1，比右手更强）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    private void applyDebuffs(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            if (player.isDead()) continue;
            
            Set<BodyPart> lost = state.getLostParts(player.getUuid());
            
            // 头部丢失 → 失明 + 黑暗效果
            if (lost.contains(BodyPart.HEAD)) {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.BLINDNESS, 200, 0, false, false, true));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.DARKNESS, 200, 0, false, false, true));
            }
            
            // 身体 - 虚弱（当前版本无身体部位，使用其他部位代替）
            // 注：BodyPart枚举中没有BODY，此处保留注释作为参考
            
            // 左手丢失 → 挖掘疲劳
            if (lost.contains(BodyPart.LEFT_HAND)) {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE, 200, 0, false, false, true));
            }
            
            // 右手丢失 → 缓慢（等级0）
            if (lost.contains(BodyPart.RIGHT_HAND)) {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 200, 0, false, false, true));
            }
            
            // 左腿或右腿丢失 → 缓慢（等级1，效果更强）
            if (lost.contains(BodyPart.LEFT_LEG) || lost.contains(BodyPart.RIGHT_LEG)) {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 200, 1, false, false, true));
            }
        }
    }
    
    /**
     * 为单个玩家应用指定身体部位的debuff（献祭时立即应用）
     * <p>使用Integer.MAX_VALUE持续时间，确保效果持续到赎回为止</p>
     * @param player 目标玩家
     * @param part 献祭的身体部位
     */
    private void applyDebuffToPlayer(ServerPlayerEntity player, BodyPart part) {
        switch (part) {
            case HEAD -> {
                // 头部：永久失明 + 黑暗
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.BLINDNESS, Integer.MAX_VALUE, 0, false, false, true));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.DARKNESS, Integer.MAX_VALUE, 0, false, false, true));
            }
            case LEFT_HAND -> {
                // 左手：永久挖掘疲劳
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE, Integer.MAX_VALUE, 0, false, false, true));
            }
            case RIGHT_HAND, LEFT_LEG, RIGHT_LEG -> {
                // 右手/左腿/右腿：永久缓慢
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, Integer.MAX_VALUE, 0, false, false, true));
            }
        }
    }
    
    /**
     * 移除玩家指定身体部位的debuff（赎回时调用）
     * @param player 目标玩家
     * @param part 赎回的身体部位
     */
    private void removeDebuffFromPlayer(ServerPlayerEntity player, BodyPart part) {
        switch (part) {
            case HEAD -> {
                // 头部赎回：移除失明和黑暗
                player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
                player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS);
            }
            case LEFT_HAND -> {
                // 左手赎回：移除挖掘疲劳
                player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE);
            }
            case RIGHT_HAND, LEFT_LEG, RIGHT_LEG -> {
                // 右手/腿赎回：移除缓慢
                player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
            }
        }
    }
    
    /**
     * 将游戏状态同步到所有客户端
     * <p>发送GameStatePayload（包含共享生命值、饥饿度、跳跃计数等）和BodyPartSyncPayload（已丢失的身体部位）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    private void syncToClients(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            // 发送游戏状态数据包（包含所有共享数据用于客户端UI显示）
            ServerPlayNetworking.send(player, new ModPackets.GameStatePayload(
                state.getActiveGameplay() != null ? state.getActiveGameplay() : "",
                state.getSharedHealth(), state.getSharedMaxHealth(),
                state.getSharedHunger(), state.getJumpCount(), state.getJumpThreshold(),
                state.getReclaimDiamondCost(), state.getPaperFoodLevel(), state.getPaperSaturation(),
                state.getPaperHealthChance(), state.getPaperHealthAmount(),
                state.getPaperArmorChance(), state.getPaperArmorAmount(),
                state.getDamageMultiplier(), state.getDisabledChance(), state.getDisabledHurtThreshold(),
                state.getMaxStamina(), state.getIceDropChance()
            ));
            
            // 发送身体部位同步数据包
            int[] lostOrdinals = state.getLostParts(player.getUuid()).stream().mapToInt(Enum::ordinal).toArray();
            ServerPlayNetworking.send(player, new ModPackets.BodyPartSyncPayload(player.getUuid(), lostOrdinals));
        }
    }
    
    /**
     * 为玩家应用最大生命值修饰符
     * <p>先移除旧修饰符，再添加新修饰符。修饰符使用ADD_VALUE操作，
     * 值为 maxHealth - 基础值（20），即实际增加或减少的量</p>
     * @param player 目标玩家
     * @param maxHealth 目标最大生命值
     */
    public static void applyMaxHealthModifier(ServerPlayerEntity player, float maxHealth) {
        var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            // 先移除旧修饰符
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
            // 计算修饰值：目标值 - 基础值
            double base = attribute.getBaseValue();
            double modifier = maxHealth - base;
            if (modifier != 0) {
                attribute.addPersistentModifier(new EntityAttributeModifier(
                    MAX_HEALTH_MODIFIER_ID, modifier, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
    }
    
    /**
     * 移除玩家的最大生命值修饰符
     * <p>玩法结束时调用，恢复玩家原始的最大生命值</p>
     * @param player 目标玩家
     */
    private void removeMaxHealthModifier(ServerPlayerEntity player) {
        var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
    }
    
    /**
     * 在玩家周围随机生成20个敌对生物
     * <p>生物在玩家周围30格水平范围、10格垂直范围内随机生成，
     * 从HOSTILE_MOBS列表中随机选择生物类型</p>
     * @param player 目标玩家，生物将在其周围生成
     */
    private void spawnRandomHostileMobs(ServerPlayerEntity player) {
        var world = player.getWorld();
        var pos = player.getBlockPos();
        
        // 生成20个随机敌对生物
        for (int i = 0; i < 20; i++) {
            var type = HOSTILE_MOBS.get(RANDOM.nextInt(HOSTILE_MOBS.size()));
            // 水平方向在±15格内，垂直方向在0~9格内随机偏移
            double x = pos.getX() + (RANDOM.nextDouble() - 0.5) * 30;
            double y = pos.getY() + RANDOM.nextInt(10);
            double z = pos.getZ() + (RANDOM.nextDouble() - 0.5) * 30;
            
            var entity = type.create(world);
            if (entity != null) {
                entity.setPosition(x, y, z);
                // 确保生物满血生成
                if (entity instanceof net.minecraft.entity.LivingEntity living) {
                    living.setHealth(living.getMaxHealth());
                }
                world.spawnEntity(entity);
            }
        }
    }
}
