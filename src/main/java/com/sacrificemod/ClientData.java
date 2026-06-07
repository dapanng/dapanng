package com.sacrificemod;

import java.util.*;
import net.minecraft.client.MinecraftClient;

/**
 * 客户端数据存储类
 *
 * 在客户端侧缓存从服务端同步过来的游戏状态数据。
 * 所有字段均为静态变量，供客户端UI渲染和逻辑判断使用。
 *
 * 数据更新流程：
 * 1. 服务端通过网络包发送数据
 * 2. SacrificeModClient接收网络包
 * 3. 调用对应的updateFrom*方法更新此类的静态字段
 * 4. 客户端UI和HUD渲染从此类读取数据
 *
 * 注意：此类仅存在于客户端，服务端使用GameState进行持久化存储
 */
public class ClientData {
    // ==================== 通用状态 ====================

    /** 当前活跃的玩法ID，null表示无活跃玩法 */
    public static String activeGameplay = null;

    // ==================== 献祭玩法数据 ====================

    /** 共享生命值 */
    public static float sharedHealth = 20.0f;
    /** 共享最大生命值 */
    public static float sharedMaxHealth = 20.0f;
    /** 共享饥饿值 */
    public static int sharedHunger = 20;
    /** 跳跃计数 */
    public static int jumpCount = 0;
    /** 跳跃阈值 */
    public static int jumpThreshold = 50;
    /** 赎回钻石花费 */
    public static int reclaimCost = 3;

    // ==================== 纸人玩法数据 ====================

    /** 纸人食物恢复的饥饿等级 */
    public static int paperFoodLevel = 3;
    /** 纸人食物恢复的饱和度 */
    public static float paperSaturation = 1.0f;
    /** 纸人食物恢复生命概率 */
    public static int paperHealthChance = 10;
    /** 纸人食物恢复生命数量 */
    public static int paperHealthAmount = 1;
    /** 纸人食物给予护甲概率 */
    public static int paperArmorChance = 5;
    /** 纸人食物给予护甲数量 */
    public static int paperArmorAmount = 2;
    /** 伤害倍率 */
    public static float damageMultiplier = 2.0f;

    // ==================== 残疾玩法数据 ====================

    /** 残疾概率（百分比） */
    public static int disabledChance = 100;
    /** 残疾受伤阈值 */
    public static int disabledHurtThreshold = 1;

    // ==================== 玩家特定数据 ====================

    /** 每个玩家丢失的身体部位集合 */
    public static final Map<UUID, Set<BodyPart>> lostParts = new HashMap<>();
    /** 每个玩家禁用的背包格子索引集合 */
    public static final Map<UUID, Set<Integer>> disabledSlots = new HashMap<>();

    // ==================== 献祭抽奖数据 ====================

    /** 上次献祭抽奖结果代码 */
    public static int lastLotteryResult = 0;
    /** 上次献祭抽奖涉及的身体部位 */
    public static BodyPart lastLotteryPart = null;

    // ==================== 耐力系统数据 ====================

    /** 最大耐力值 */
    public static int maxStamina = 30;
    /** 冰块掉落概率 */
    public static int iceDropChance = 5;
    /** 当前玩家的耐力值 */
    public static float playerStamina = 30.0f;
    /** 当前玩家的最大耐力值 */
    public static float playerMaxStamina = 30.0f;
    /** 当前玩家的冻结剩余tick数 */
    public static int playerFrozenTicks = 0;

    // ==================== 肉鸽/加速玩法数据 ====================

    /** 肉鸽玩法每次生成的生物数量 */
    public static int roguelikeMobCount = 1;
    /** 加速玩法的速度倍率 */
    public static float speedMultiplier = 100.0f;
    /** 全随机玩法的随机化间隔（分钟） */
    public static int randomizeIntervalMinutes = 10;
    /** 肉鸽玩法当前待击杀的生物数量 */
    public static int roguelikePendingMobs = 0;

    // ==================== 全随机玩法时间数据 ====================

    /** 距离下次随机化的剩余tick数（客户端本地倒计时） */
    public static long randomizeRemainingTicks = 0;
    /** 随机化间隔的总tick数（默认10分钟 * 60秒 * 20tick/秒） */
    public static long randomizeIntervalTicks = 12000;

    // ==================== 弓箭手大作战数据 ====================

    /** 弓的力量附魔等级 */
    public static int archerBowPower = 8;
    /** 弓的冲击附魔等级 */
    public static int archerBowPunch = 5;
    /** 弩的多重射击附魔等级 */
    public static int archerCrossbowMultishot = 5;
    /** 弩的穿透附魔等级 */
    public static int archerCrossbowPiercing = 3;
    /** 剑的锋利附魔等级 */
    public static int archerSwordSharpness = 2;
    /** 剑的火焰附加附魔等级 */
    public static int archerSwordFireAspect = 20;
    /** 击杀换甲数 */
    public static int archerKillsPerArmor = 15;
    /** 骷髅TNT箭概率 */
    public static int archerSkeletonTntChance = 30;
    /** 流浪者凋零箭概率 */
    public static int archerStrayWitherChance = 40;
    /** 生物生成上限 */
    public static int archerMobLimit = 120;

    // ==================== 追击玩法数据 ====================

    /** 追击玩法和平生物攻击范围 */
    public static double chaseAttackRange = 1.0;

    /**
     * 获取指定玩家丢失的身体部位集合
     * @param uuid 玩家UUID
     * @return 不可修改的身体部位集合
     */
    public static Set<BodyPart> getLostParts(UUID uuid) {
        return lostParts.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * 获取指定玩家禁用的背包格子集合
     * @param uuid 玩家UUID
     * @return 不可修改的格子索引集合
     */
    public static Set<Integer> getDisabledSlots(UUID uuid) {
        return disabledSlots.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * 设置指定玩家丢失的身体部位
     * @param uuid  玩家UUID
     * @param parts 身体部位集合，空集合时移除该条目
     */
    public static void setLostParts(UUID uuid, Set<BodyPart> parts) {
        if (parts.isEmpty()) {
            lostParts.remove(uuid);
        } else {
            lostParts.put(uuid, new HashSet<>(parts));
        }
    }

    /**
     * 从GameStatePayload更新所有游戏状态数据
     *
     * 当收到服务端的游戏状态同步包时调用，
     * 将Payload中的数据写入对应的静态字段。
     * 如果活跃玩法为空，则重置所有数据。
     *
     * @param payload 服务端发来的游戏状态Payload
     */
    public static void updateFromGameStatePayload(ModPackets.GameStatePayload payload) {
        activeGameplay = payload.activeGameplay();
        sharedHealth = payload.sharedHealth();
        sharedMaxHealth = payload.sharedMaxHealth();
        sharedHunger = payload.sharedHunger();
        jumpCount = payload.jumpCount();
        jumpThreshold = payload.jumpThreshold();
        reclaimCost = payload.reclaimCost();
        paperFoodLevel = payload.paperFoodLevel();
        paperSaturation = payload.paperSaturation();
        paperHealthChance = payload.paperHealthChance();
        paperHealthAmount = payload.paperHealthAmount();
        paperArmorChance = payload.paperArmorChance();
        paperArmorAmount = payload.paperArmorAmount();
        damageMultiplier = payload.damageMultiplier();
        disabledChance = payload.disabledChance();
        disabledHurtThreshold = payload.disabledHurtThreshold();
        maxStamina = payload.maxStamina();
        iceDropChance = payload.iceDropChance();
        // 如果没有活跃玩法，重置所有客户端数据
        if (activeGameplay == null || activeGameplay.isEmpty()) {
            reset();
        }
    }

    /**
     * 重置所有客户端数据为默认值
     *
     * 在玩法停止或收到空活跃玩法时调用。
     */
    public static void reset() {
        activeGameplay = null;
        sharedHealth = 20.0f;
        sharedMaxHealth = 20.0f;
        sharedHunger = 20;
        jumpCount = 0;
        paperFoodLevel = 3;
        paperSaturation = 1.0f;
        paperHealthChance = 10;
        paperHealthAmount = 1;
        paperArmorChance = 5;
        paperArmorAmount = 2;
        damageMultiplier = 2.0f;
        disabledChance = 100;
        disabledHurtThreshold = 1;
        maxStamina = 30;
        iceDropChance = 5;
        playerStamina = 30.0f;
        playerMaxStamina = 30.0f;
        playerFrozenTicks = 0;
        roguelikePendingMobs = 0;
        lostParts.clear();
        disabledSlots.clear();
        lastLotteryResult = 0;
        lastLotteryPart = null;
    }

    /**
     * 从BodyPartSyncPayload更新身体部位数据
     *
     * 将序号数组转换为BodyPart枚举集合，并更新到lostParts映射中。
     *
     * @param payload 服务端发来的身体部位同步Payload
     */
    public static void updateFromBodyPartPayload(ModPackets.BodyPartSyncPayload payload) {
        Set<BodyPart> parts = new HashSet<>();
        for (int ord : payload.partOrdinals()) {
            // 校验序号有效性，防止越界
            if (ord >= 0 && ord < BodyPart.values().length) {
                parts.add(BodyPart.values()[ord]);
            }
        }
        setLostParts(payload.playerUuid(), parts);
    }

    /**
     * 从DisabledSlotsSyncPayload更新禁用格子数据
     *
     * @param payload 服务端发来的禁用格子同步Payload
     */
    public static void updateFromDisabledSlotsPayload(ModPackets.DisabledSlotsSyncPayload payload) {
        Set<Integer> slots = new HashSet<>();
        for (int s : payload.slotIndices()) {
            slots.add(s);
        }
        if (slots.isEmpty()) {
            disabledSlots.remove(payload.playerUuid());
        } else {
            disabledSlots.put(payload.playerUuid(), slots);
        }
    }

    /**
     * 从StaminaSyncPayload更新耐力数据
     *
     * 仅当Payload中的UUID与当前客户端玩家匹配时才更新，
     * 因为耐力数据是每个玩家独立的。
     *
     * @param payload 服务端发来的耐力同步Payload
     */
    public static void updateFromStaminaSyncPayload(ModPackets.StaminaSyncPayload payload) {
        // 仅更新本地玩家的耐力数据
        if (MinecraftClient.getInstance().player != null &&
            payload.playerUuid().equals(MinecraftClient.getInstance().player.getUuid())) {
            playerStamina = payload.stamina();
            playerMaxStamina = payload.maxStamina();
            playerFrozenTicks = payload.frozenTicks();
        }
    }

    /**
     * 从RandomizeTimeSyncPayload更新全随机玩法倒计时数据
     *
     * @param payload 服务端发来的随机化时间同步Payload
     */
    public static void updateFromRandomizeTimeSyncPayload(ModPackets.RandomizeTimeSyncPayload payload) {
        randomizeRemainingTicks = payload.remainingTicks();
        randomizeIntervalTicks = payload.intervalTicks();
    }

    /**
     * 从GameplaySyncPayload更新肉鸽/加速玩法的动态数据
     *
     * @param payload 服务端发来的玩法动态数据同步Payload
     */
    public static void updateFromGameplaySyncPayload(ModPackets.GameplaySyncPayload payload) {
        roguelikePendingMobs = payload.roguelikePendingMobs();
        speedMultiplier = payload.speedMultiplier();
    }
}
