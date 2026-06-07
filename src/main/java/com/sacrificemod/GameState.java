package com.sacrificemod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

/**
 * 游戏状态持久化类
 *
 * 继承自Minecraft的PersistentState，用于在服务器端持久化存储模组的所有游戏状态数据。
 * 数据保存在主世界的"sacrificemod"数据文件中，服务器重启后自动恢复。
 *
 * 存储内容包括：
 * - 当前活跃的玩法ID
 * - 献祭玩法的共享生命值、饥饿值、跳跃计数等
 * - 纸人玩法的食物参数、耐力、冻结时间等
 * - 残疾玩法的禁用格子、受伤计数等
 * - 肉鸽玩法的区块追踪、待击杀生物数等
 * - 加速玩法的速度倍率
 * - 全随机玩法的随机化间隔和上次随机化时间
 * - 弓箭手玩法的武器参数和击杀统计
 * - 每个玩家丢失的身体部位
 */
public class GameState extends PersistentState {
    // ==================== 通用字段 ====================

    /** 当前活跃的玩法ID，null表示没有活跃玩法 */
    private String activeGameplay = null;

    // ==================== 献祭玩法字段 ====================

    /** 共享生命值（所有玩家共享的生命值） */
    private float sharedHealth = 20.0f;

    /** 共享饥饿值（所有玩家共享的饥饿值） */
    private int sharedHunger = 20;

    /** 共享饱和度（所有玩家共享的食物饱和度） */
    private float sharedSaturation = 5.0f;

    /** 共享最大生命值 */
    private float sharedMaxHealth = 20.0f;

    /** 跳跃计数（累计跳跃次数） */
    private int jumpCount = 0;

    /** 跳跃阈值（达到此数值触发献祭抽奖） */
    private int jumpThreshold = 50;

    /** 赎回身体部位所需的钻石数量 */
    private int reclaimDiamondCost = 3;

    /** 每个玩家丢失的身体部位集合 */
    private final Map<UUID, Set<BodyPart>> lostParts = new HashMap<>();

    /** 每个玩家禁用的背包格子索引集合 */
    private final Map<UUID, Set<Integer>> disabledSlots = new HashMap<>();

    /** 每个玩家上次同步时的生命值（用于检测变化） */
    private final Map<UUID, Float> lastSyncedHealth = new HashMap<>();

    /** 每个玩家上次同步时的饥饿值（用于检测变化） */
    private final Map<UUID, Integer> lastSyncedHunger = new HashMap<>();

    /** 待执行的死亡惩罚次数（献祭玩法中玩家死亡后累加） */
    private int pendingDeathPenalties = 0;

    // ==================== 纸人玩法字段 ====================

    /** 纸人食物恢复的饥饿等级 */
    private int paperFoodLevel = 3;

    /** 纸人食物恢复的饱和度 */
    private float paperSaturation = 1.0f;

    /** 纸人食物恢复生命值的概率（百分比） */
    private int paperHealthChance = 10;

    /** 纸人食物恢复生命值的数量 */
    private int paperHealthAmount = 1;

    /** 纸人食物给予护甲的概率（百分比） */
    private int paperArmorChance = 5;

    /** 纸人食物给予护甲的数量 */
    private int paperArmorAmount = 2;

    /** 纸人玩法中的伤害倍率 */
    private float damageMultiplier = 2.0f;

    // ==================== 残疾玩法字段 ====================

    /** 受伤后残疾的概率（百分比，100表示每次受伤必定残疾） */
    private int disabledChance = 100;

    /** 触发残疾判定的受伤次数阈值（累计受伤多少次后判定一次） */
    private int disabledHurtThreshold = 1;

    /** 每个玩家的累计受伤次数 */
    private final Map<UUID, Integer> playerHurtCount = new HashMap<>();

    /** 每个玩家上次受伤的游戏tick */
    private final Map<UUID, Long> playerLastHurtTick = new HashMap<>();

    // ==================== 耐力系统字段（纸人玩法） ====================

    /** 最大耐力值 */
    private int maxStamina = 30;

    /** 每个玩家的当前耐力值 */
    private final Map<UUID, Float> playerStamina = new HashMap<>();

    /** 每个玩家的冻结剩余tick数（耐力耗尽后冻结） */
    private final Map<UUID, Integer> playerFrozenTicks = new HashMap<>();

    /** 破坏冰类方块掉落冰物品的概率（百分比） */
    private int iceDropChance = 5;

    /** 每个玩家累计的生命值修正量（跨死亡保留） */
    private final Map<UUID, Double> playerHealthModifier = new HashMap<>();

    /** 每个玩家累计的护甲修正量（跨死亡保留） */
    private final Map<UUID, Double> playerArmorModifier = new HashMap<>();

    // ==================== 肉鸽玩法字段 ====================

    /** 每次进入新区块时生成的生物数量 */
    private int roguelikeMobCount = 1;

    /** 每个玩家上次所在的区块位置（用于检测是否进入新区块） */
    private final Map<UUID, Long> playerLastChunkPos = new HashMap<>();

    /** 每个玩家是否被锁定在当前区块（需要击杀完生物才能移动） */
    private final Map<UUID, Boolean> playerBlockedInChunk = new HashMap<>();

    /** 每个玩家待击杀的生物数量 */
    private final Map<UUID, Integer> playerPendingMobs = new HashMap<>();

    // ==================== 加速玩法字段 ====================

    /** 速度倍率（百分比，100表示正常速度，200表示2倍速） */
    private float speedMultiplier = 100.0f;

    // ==================== 全随机玩法字段 ====================

    /** 随机化间隔时间（分钟） */
    private int randomizeIntervalMinutes = 10;

    /** 上次执行随机化的游戏tick */
    private long lastRandomizeTick = 0;

    // ==================== 弓箭手大作战玩法字段 ====================

    /** 弓的力量附魔等级 */
    private int archerBowPower = 8;

    /** 弓的冲击附魔等级 */
    private int archerBowPunch = 5;

    /** 弩的多重射击附魔等级 */
    private int archerCrossbowMultishot = 5;

    /** 弩的穿透附魔等级 */
    private int archerCrossbowPiercing = 3;

    /** 剑的锋利附魔等级 */
    private int archerSwordSharpness = 2;

    /** 剑的火焰附加附魔等级 */
    private int archerSwordFireAspect = 20;

    /** 每击杀多少个生物获得一件护甲 */
    private int archerKillsPerArmor = 15;

    /** 骷髅射出TNT箭的概率（百分比） */
    private int archerSkeletonTntChance = 30;

    /** 流浪者射出凋零箭的概率（百分比） */
    private int archerStrayWitherChance = 40;

    /** 弓箭手生物生成上限（主世界最大生物数量） */
    private int archerMobLimit = 120;

    /** 每个玩家的击杀计数 */
    private final Map<UUID, Integer> archerKillCount = new HashMap<>();

    /** 每个玩家的额外护甲点数（通过击杀获得） */
    private final Map<UUID, Integer> archerBonusArmor = new HashMap<>();

    // ==================== 追击玩法字段 ====================

    /** 追击玩法和平生物攻击范围 */
    private double chaseAttackRange = 1.0;

    /** 默认构造函数 */
    public GameState() {
    }

    /**
     * 从NBT数据反序列化创建GameState
     *
     * 从存档文件中读取所有游戏状态数据，支持向后兼容：
     * 新增字段使用contains检查是否存在，不存在则使用默认值。
     *
     * @param nbt             NBT数据复合标签
     * @param registryLookup  注册表包装器（未使用）
     * @return 反序列化后的GameState实例
     */
    public static GameState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        GameState state = new GameState();
        // 读取活跃玩法ID，兼容旧版本的"active"布尔字段
        state.activeGameplay = nbt.contains("activeGameplay") ? nbt.getString("activeGameplay") : (nbt.getBoolean("active") ? "sacrifice" : null);
        state.sharedHealth = nbt.getFloat("sharedHealth");
        state.sharedHunger = nbt.getInt("sharedHunger");
        state.sharedSaturation = nbt.getFloat("sharedSaturation");
        state.sharedMaxHealth = nbt.getFloat("sharedMaxHealth");
        state.jumpCount = nbt.getInt("jumpCount");
        // 向后兼容：旧存档可能没有这些字段
        state.jumpThreshold = nbt.contains("jumpThreshold") ? nbt.getInt("jumpThreshold") : 50;
        state.reclaimDiamondCost = nbt.contains("reclaimDiamondCost") ? nbt.getInt("reclaimDiamondCost") : 3;

        // 纸人玩法字段（向后兼容）
        state.paperFoodLevel = nbt.contains("paperFoodLevel") ? nbt.getInt("paperFoodLevel") : 3;
        state.paperSaturation = nbt.contains("paperSaturation") ? nbt.getFloat("paperSaturation") : 1.0f;
        state.paperHealthChance = nbt.contains("paperHealthChance") ? nbt.getInt("paperHealthChance") : 10;
        state.paperHealthAmount = nbt.contains("paperHealthAmount") ? nbt.getInt("paperHealthAmount") : 1;
        state.paperArmorChance = nbt.contains("paperArmorChance") ? nbt.getInt("paperArmorChance") : 5;
        state.paperArmorAmount = nbt.contains("paperArmorAmount") ? nbt.getInt("paperArmorAmount") : 2;
        state.damageMultiplier = nbt.contains("damageMultiplier") ? nbt.getFloat("damageMultiplier") : 2.0f;

        // 残疾玩法字段（向后兼容）
        state.disabledChance = nbt.contains("disabledChance") ? nbt.getInt("disabledChance") : 100;
        state.disabledHurtThreshold = nbt.contains("disabledHurtThreshold") ? nbt.getInt("disabledHurtThreshold") : 1;

        // 耐力系统字段（向后兼容）
        state.maxStamina = nbt.contains("maxStamina") ? nbt.getInt("maxStamina") : 30;
        state.iceDropChance = nbt.contains("iceDropChance") ? nbt.getInt("iceDropChance") : 5;

        // 全随机玩法字段（向后兼容）
        if (nbt.contains("lastRandomizeTick")) {
            state.lastRandomizeTick = nbt.getLong("lastRandomizeTick");
        }

        // 弓箭手玩法字段（向后兼容）
        state.archerBowPower = nbt.contains("archerBowPower") ? nbt.getInt("archerBowPower") : 8;
        state.archerBowPunch = nbt.contains("archerBowPunch") ? nbt.getInt("archerBowPunch") : 5;
        state.archerCrossbowMultishot = nbt.contains("archerCrossbowMultishot") ? nbt.getInt("archerCrossbowMultishot") : 5;
        state.archerCrossbowPiercing = nbt.contains("archerCrossbowPiercing") ? nbt.getInt("archerCrossbowPiercing") : 3;
        state.archerSwordSharpness = nbt.contains("archerSwordSharpness") ? nbt.getInt("archerSwordSharpness") : 2;
        state.archerSwordFireAspect = nbt.contains("archerSwordFireAspect") ? nbt.getInt("archerSwordFireAspect") : 20;
        state.archerKillsPerArmor = nbt.contains("archerKillsPerArmor") ? nbt.getInt("archerKillsPerArmor") : 15;
        state.archerSkeletonTntChance = nbt.contains("archerSkeletonTntChance") ? nbt.getInt("archerSkeletonTntChance") : 30;
        state.archerStrayWitherChance = nbt.contains("archerStrayWitherChance") ? nbt.getInt("archerStrayWitherChance") : 40;
        state.archerMobLimit = nbt.contains("archerMobLimit") ? nbt.getInt("archerMobLimit") : 120;

        // 追击玩法字段（向后兼容）
        state.chaseAttackRange = nbt.contains("chaseAttackRange") ? nbt.getDouble("chaseAttackRange") : 1.0;

        // 反序列化玩家耐力数据（NbtList格式）
        if (nbt.contains("playerStamina")) {
            NbtList staminaList = nbt.getList("playerStamina", 10);
            for (int i = 0; i < staminaList.size(); i++) {
                NbtCompound comp = staminaList.getCompound(i);
                state.playerStamina.put(comp.getUuid("uuid"), comp.getFloat("stamina"));
            }
        }

        // 反序列化玩家冻结时间数据（NbtList格式）
        if (nbt.contains("playerFrozenTicks")) {
            NbtList frozenList = nbt.getList("playerFrozenTicks", 10);
            for (int i = 0; i < frozenList.size(); i++) {
                NbtCompound comp = frozenList.getCompound(i);
                state.playerFrozenTicks.put(comp.getUuid("uuid"), comp.getInt("ticks"));
            }
        }

        // 反序列化玩家生命值修正量（跨死亡保留的累计值）
        if (nbt.contains("playerHealthModifier")) {
            NbtList list = nbt.getList("playerHealthModifier", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound comp = list.getCompound(i);
                state.playerHealthModifier.put(comp.getUuid("uuid"), comp.getDouble("modifier"));
            }
        }

        // 反序列化玩家护甲修正量（跨死亡保留的累计值）
        if (nbt.contains("playerArmorModifier")) {
            NbtList list = nbt.getList("playerArmorModifier", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound comp = list.getCompound(i);
                state.playerArmorModifier.put(comp.getUuid("uuid"), comp.getDouble("modifier"));
            }
        }

        // 反序列化玩家受伤计数（UUID字符串 → 次数）
        NbtCompound hurtCountNbt = nbt.getCompound("playerHurtCount");
        for (String uuidStr : hurtCountNbt.getKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            state.playerHurtCount.put(uuid, hurtCountNbt.getInt(uuidStr));
        }

        // 反序列化玩家丢失的身体部位（UUID字符串 → 序号数组）
        NbtCompound partsNbt = nbt.getCompound("lostParts");
        for (String uuidStr : partsNbt.getKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            Set<BodyPart> parts = new HashSet<>();
            int[] partOrdinals = partsNbt.getIntArray(uuidStr);
            for (int ord : partOrdinals) {
                // 校验序号有效性，防止越界
                if (ord >= 0 && ord < BodyPart.values().length) {
                    parts.add(BodyPart.values()[ord]);
                }
            }
            state.lostParts.put(uuid, parts);
        }

        // 反序列化玩家禁用的背包格子（UUID字符串 → 格子索引数组）
        NbtCompound slotsNbt = nbt.getCompound("disabledSlots");
        for (String uuidStr : slotsNbt.getKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            int[] slotArray = slotsNbt.getIntArray(uuidStr);
            Set<Integer> slots = new HashSet<>();
            for (int s : slotArray) {
                slots.add(s);
            }
            state.disabledSlots.put(uuid, slots);
        }

        return state;
    }

    /**
     * 将游戏状态序列化到NBT数据
     *
     * 将所有游戏状态数据写入NBT复合标签以便持久化保存。
     *
     * @param nbt             目标NBT复合标签
     * @param registryLookup  注册表包装器（未使用）
     * @return 写入数据后的NBT复合标签
     */
    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        // 写入活跃玩法ID
        if (activeGameplay != null) {
            nbt.putString("activeGameplay", activeGameplay);
        }
        // 写入献祭玩法基础数据
        nbt.putFloat("sharedHealth", sharedHealth);
        nbt.putInt("sharedHunger", sharedHunger);
        nbt.putFloat("sharedSaturation", sharedSaturation);
        nbt.putFloat("sharedMaxHealth", sharedMaxHealth);
        nbt.putInt("jumpCount", jumpCount);
        nbt.putInt("jumpThreshold", jumpThreshold);
        nbt.putInt("reclaimDiamondCost", reclaimDiamondCost);

        // 写入纸人玩法参数
        nbt.putInt("paperFoodLevel", paperFoodLevel);
        nbt.putFloat("paperSaturation", paperSaturation);
        nbt.putInt("paperHealthChance", paperHealthChance);
        nbt.putInt("paperHealthAmount", paperHealthAmount);
        nbt.putInt("paperArmorChance", paperArmorChance);
        nbt.putInt("paperArmorAmount", paperArmorAmount);
        nbt.putFloat("damageMultiplier", damageMultiplier);

        // 写入残疾玩法参数
        nbt.putInt("disabledChance", disabledChance);
        nbt.putInt("disabledHurtThreshold", disabledHurtThreshold);

        // 写入耐力系统参数
        nbt.putInt("maxStamina", maxStamina);
        nbt.putInt("iceDropChance", iceDropChance);

        // 写入全随机玩法参数
        nbt.putLong("lastRandomizeTick", lastRandomizeTick);

        // 写入弓箭手玩法参数
        nbt.putInt("archerBowPower", archerBowPower);
        nbt.putInt("archerBowPunch", archerBowPunch);
        nbt.putInt("archerCrossbowMultishot", archerCrossbowMultishot);
        nbt.putInt("archerCrossbowPiercing", archerCrossbowPiercing);
        nbt.putInt("archerSwordSharpness", archerSwordSharpness);
        nbt.putInt("archerSwordFireAspect", archerSwordFireAspect);
        nbt.putInt("archerKillsPerArmor", archerKillsPerArmor);
        nbt.putInt("archerSkeletonTntChance", archerSkeletonTntChance);
        nbt.putInt("archerStrayWitherChance", archerStrayWitherChance);
        nbt.putInt("archerMobLimit", archerMobLimit);

        // 写入追击玩法参数
        nbt.putDouble("chaseAttackRange", chaseAttackRange);

        // 序列化玩家耐力数据为NbtList
        NbtList staminaList = new NbtList();
        for (Map.Entry<UUID, Float> entry : playerStamina.entrySet()) {
            NbtCompound comp = new NbtCompound();
            comp.putUuid("uuid", entry.getKey());
            comp.putFloat("stamina", entry.getValue());
            staminaList.add(comp);
        }
        nbt.put("playerStamina", staminaList);

        // 序列化玩家冻结时间数据为NbtList
        NbtList frozenList = new NbtList();
        for (Map.Entry<UUID, Integer> entry : playerFrozenTicks.entrySet()) {
            NbtCompound comp = new NbtCompound();
            comp.putUuid("uuid", entry.getKey());
            comp.putInt("ticks", entry.getValue());
            frozenList.add(comp);
        }
        nbt.put("playerFrozenTicks", frozenList);

        // 序列化玩家生命值修正量
        NbtList healthModList = new NbtList();
        for (Map.Entry<UUID, Double> entry : playerHealthModifier.entrySet()) {
            NbtCompound comp = new NbtCompound();
            comp.putUuid("uuid", entry.getKey());
            comp.putDouble("modifier", entry.getValue());
            healthModList.add(comp);
        }
        nbt.put("playerHealthModifier", healthModList);

        // 序列化玩家护甲修正量
        NbtList armorModList = new NbtList();
        for (Map.Entry<UUID, Double> entry : playerArmorModifier.entrySet()) {
            NbtCompound comp = new NbtCompound();
            comp.putUuid("uuid", entry.getKey());
            comp.putDouble("modifier", entry.getValue());
            armorModList.add(comp);
        }
        nbt.put("playerArmorModifier", armorModList);

        // 序列化玩家受伤计数
        NbtCompound hurtCountNbt = new NbtCompound();
        for (Map.Entry<UUID, Integer> entry : playerHurtCount.entrySet()) {
            hurtCountNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("playerHurtCount", hurtCountNbt);

        // 序列化玩家丢失的身体部位（转换为序号数组）
        NbtCompound partsNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<BodyPart>> entry : lostParts.entrySet()) {
            int[] ordinals = entry.getValue().stream().mapToInt(Enum::ordinal).toArray();
            partsNbt.putIntArray(entry.getKey().toString(), ordinals);
        }
        nbt.put("lostParts", partsNbt);

        // 序列化玩家禁用的背包格子
        NbtCompound slotsNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<Integer>> entry : disabledSlots.entrySet()) {
            int[] slotArray = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            slotsNbt.putIntArray(entry.getKey().toString(), slotArray);
        }
        nbt.put("disabledSlots", slotsNbt);

        return nbt;
    }

    /** PersistentState的类型定义，用于getOrCreate方法 */
    private static final Type<GameState> TYPE = new Type<>(
            GameState::new,
            GameState::createFromNbt,
            null
    );

    /**
     * 获取服务器端的游戏状态实例
     *
     * 通过主世界的PersistentStateManager获取或创建GameState实例。
     * 数据保存在主世界（OVERWORLD）中，确保所有维度共享同一状态。
     *
     * @param server Minecraft服务器实例
     * @return 服务器端的GameState实例
     */
    public static GameState getServerState(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, "sacrificemod");
    }

    // ==================== 通用状态方法 ====================

    /**
     * 获取当前活跃的玩法ID
     * @return 玩法ID字符串，无活跃玩法时返回null
     */
    public String getActiveGameplay() {
        return activeGameplay;
    }

    /**
     * 设置当前活跃的玩法ID
     * @param activeGameplay 玩法ID，null表示无活跃玩法
     */
    public void setActiveGameplay(String activeGameplay) {
        this.activeGameplay = activeGameplay;
        markDirty();
    }

    /**
     * 检查指定玩法是否活跃
     * @param id 要检查的玩法ID
     * @return true表示该玩法当前活跃
     */
    public boolean isGameplayActive(String id) {
        return activeGameplay != null && activeGameplay.equals(id);
    }

    /**
     * 检查是否有任何玩法活跃
     * @return true表示有活跃玩法
     */
    public boolean isAnyGameplayActive() {
        return activeGameplay != null;
    }

    // ==================== 献祭玩法方法 ====================

    /**
     * 获取共享生命值
     * @return 当前共享生命值
     */
    public float getSharedHealth() {
        return sharedHealth;
    }

    /**
     * 设置共享生命值，自动限制在[0, sharedMaxHealth]范围内
     * @param health 目标生命值
     */
    public void setSharedHealth(float health) {
        this.sharedHealth = Math.max(0, Math.min(sharedMaxHealth, health));
        markDirty();
    }

    /**
     * 获取共享饥饿值
     * @return 当前共享饥饿值
     */
    public int getSharedHunger() {
        return sharedHunger;
    }

    /**
     * 设置共享饥饿值，自动限制在[0, 20]范围内
     * @param hunger 目标饥饿值
     */
    public void setSharedHunger(int hunger) {
        this.sharedHunger = Math.max(0, Math.min(20, hunger));
        markDirty();
    }

    /**
     * 获取共享饱和度
     * @return 当前共享饱和度
     */
    public float getSharedSaturation() {
        return sharedSaturation;
    }

    /**
     * 设置共享饱和度，自动限制在[0, sharedHunger]范围内
     * @param saturation 目标饱和度
     */
    public void setSharedSaturation(float saturation) {
        this.sharedSaturation = Math.max(0, Math.min(sharedHunger, saturation));
        markDirty();
    }

    /**
     * 获取共享最大生命值
     * @return 当前共享最大生命值
     */
    public float getSharedMaxHealth() {
        return sharedMaxHealth;
    }

    /**
     * 设置共享最大生命值，最小为2，如果当前生命超过最大值则自动调整
     * @param maxHealth 目标最大生命值
     */
    public void setSharedMaxHealth(float maxHealth) {
        this.sharedMaxHealth = Math.max(2, maxHealth);
        // 如果当前生命超过新的最大值，则裁剪
        if (sharedHealth > sharedMaxHealth) {
            sharedHealth = sharedMaxHealth;
        }
        markDirty();
    }

    /**
     * 获取跳跃计数
     * @return 当前跳跃次数
     */
    public int getJumpCount() {
        return jumpCount;
    }

    /**
     * 跳跃计数加1
     */
    public void incrementJumpCount() {
        this.jumpCount++;
        markDirty();
    }

    /**
     * 重置跳跃计数为0
     */
    public void resetJumpCount() {
        this.jumpCount = 0;
        markDirty();
    }

    /**
     * 获取跳跃阈值
     * @return 触发献祭抽奖所需的跳跃次数
     */
    public int getJumpThreshold() {
        return jumpThreshold;
    }

    /**
     * 设置跳跃阈值，最小为1
     * @param threshold 目标阈值
     */
    public void setJumpThreshold(int threshold) {
        this.jumpThreshold = Math.max(1, threshold);
        markDirty();
    }

    /**
     * 获取赎回身体部位所需的钻石数量
     * @return 钻石数量
     */
    public int getReclaimDiamondCost() {
        return reclaimDiamondCost;
    }

    /**
     * 设置赎回钻石花费，最小为1
     * @param cost 目标花费
     */
    public void setReclaimDiamondCost(int cost) {
        this.reclaimDiamondCost = Math.max(1, cost);
        markDirty();
    }

    // ==================== 纸人玩法方法 ====================

    /**
     * 获取纸人食物恢复的饥饿等级
     * @return 饥饿等级
     */
    public int getPaperFoodLevel() {
        return paperFoodLevel;
    }

    /**
     * 设置纸人食物恢复的饥饿等级
     * @param foodLevel 目标饥饿等级
     */
    public void setPaperFoodLevel(int foodLevel) {
        this.paperFoodLevel = foodLevel;
        markDirty();
    }

    /**
     * 获取纸人食物恢复的饱和度
     * @return 饱和度
     */
    public float getPaperSaturation() {
        return paperSaturation;
    }

    /**
     * 设置纸人食物恢复的饱和度
     * @param saturation 目标饱和度
     */
    public void setPaperSaturation(float saturation) {
        this.paperSaturation = saturation;
        markDirty();
    }

    /**
     * 获取纸人食物恢复生命值的概率
     * @return 概率（百分比）
     */
    public int getPaperHealthChance() {
        return paperHealthChance;
    }

    /**
     * 设置纸人食物恢复生命值的概率
     * @param chance 目标概率（百分比）
     */
    public void setPaperHealthChance(int chance) {
        this.paperHealthChance = chance;
        markDirty();
    }

    /**
     * 获取纸人食物恢复生命值的数量
     * @return 生命值数量
     */
    public int getPaperHealthAmount() {
        return paperHealthAmount;
    }

    /**
     * 设置纸人食物恢复生命值的数量
     * @param amount 目标数量
     */
    public void setPaperHealthAmount(int amount) {
        this.paperHealthAmount = amount;
        markDirty();
    }

    /**
     * 获取纸人食物给予护甲的概率
     * @return 概率（百分比）
     */
    public int getPaperArmorChance() {
        return paperArmorChance;
    }

    /**
     * 设置纸人食物给予护甲的概率
     * @param chance 目标概率（百分比）
     */
    public void setPaperArmorChance(int chance) {
        this.paperArmorChance = chance;
        markDirty();
    }

    /**
     * 获取纸人食物给予护甲的数量
     * @return 护甲数量
     */
    public int getPaperArmorAmount() {
        return paperArmorAmount;
    }

    /**
     * 设置纸人食物给予护甲的数量
     * @param amount 目标数量
     */
    public void setPaperArmorAmount(int amount) {
        this.paperArmorAmount = amount;
        markDirty();
    }

    /**
     * 获取纸人玩法中的伤害倍率
     * @return 伤害倍率
     */
    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    /**
     * 设置纸人玩法中的伤害倍率
     * @param multiplier 目标伤害倍率
     */
    public void setDamageMultiplier(float multiplier) {
        this.damageMultiplier = multiplier;
        markDirty();
    }

    // ==================== 残疾玩法方法 ====================

    /**
     * 获取受伤后残疾的概率
     * @return 概率（百分比）
     */
    public int getDisabledChance() {
        return disabledChance;
    }

    /**
     * 设置受伤后残疾的概率，限制在[0, 100]范围内
     * @param chance 目标概率（百分比）
     */
    public void setDisabledChance(int chance) {
        this.disabledChance = Math.max(0, Math.min(100, chance));
        markDirty();
    }

    /**
     * 获取触发残疾判定的受伤次数阈值
     * @return 受伤次数阈值
     */
    public int getDisabledHurtThreshold() {
        return disabledHurtThreshold;
    }

    /**
     * 设置触发残疾判定的受伤次数阈值，最小为1
     * @param threshold 目标阈值
     */
    public void setDisabledHurtThreshold(int threshold) {
        this.disabledHurtThreshold = Math.max(1, threshold);
        markDirty();
    }

    // ==================== 耐力系统方法 ====================

    /**
     * 获取最大耐力值
     * @return 最大耐力值
     */
    public int getMaxStamina() {
        return maxStamina;
    }

    /**
     * 设置最大耐力值，最小为1
     * @param maxStamina 目标最大耐力值
     */
    public void setMaxStamina(int maxStamina) {
        this.maxStamina = Math.max(1, maxStamina);
        markDirty();
    }

    /**
     * 获取指定玩家的当前耐力值
     * @param uuid 玩家UUID
     * @return 耐力值，未记录时返回最大耐力值
     */
    public float getStamina(UUID uuid) {
        return playerStamina.getOrDefault(uuid, (float) maxStamina);
    }

    /**
     * 设置指定玩家的耐力值，自动限制在[0, maxStamina]范围内
     * @param uuid    玩家UUID
     * @param stamina 目标耐力值
     */
    public void setStamina(UUID uuid, float stamina) {
        playerStamina.put(uuid, Math.max(0, Math.min(maxStamina, stamina)));
        markDirty();
    }

    /**
     * 获取指定玩家的冻结剩余tick数
     * @param uuid 玩家UUID
     * @return 冻结tick数，0表示未冻结
     */
    public int getFrozenTicks(UUID uuid) {
        return playerFrozenTicks.getOrDefault(uuid, 0);
    }

    /**
     * 设置指定玩家的冻结剩余tick数
     * @param uuid  玩家UUID
     * @param ticks 冻结tick数
     */
    public void setFrozenTicks(UUID uuid, int ticks) {
        playerFrozenTicks.put(uuid, ticks);
        markDirty();
    }

    /**
     * 获取破坏冰类方块掉落冰物品的概率
     * @return 概率（百分比）
     */
    public int getIceDropChance() {
        return iceDropChance;
    }

    /**
     * 设置冰块掉落概率，限制在[0, 100]范围内
     * @param chance 目标概率（百分比）
     */
    public void setIceDropChance(int chance) {
        this.iceDropChance = Math.max(0, Math.min(100, chance));
        markDirty();
    }

    /**
     * 清空所有玩家的耐力、冻结时间、生命/护甲修正数据
     * 在切换玩法时调用
     */
    public void clearStaminaAndFrozenTicks() {
        playerStamina.clear();
        playerFrozenTicks.clear();
        playerHealthModifier.clear();
        playerArmorModifier.clear();
        markDirty();
    }

    /**
     * 获取指定玩家的生命值修正量
     * @param uuid 玩家UUID
     * @return 生命值修正量，0表示无修正
     */
    public double getHealthModifier(UUID uuid) {
        return playerHealthModifier.getOrDefault(uuid, 0.0);
    }

    /**
     * 累加指定玩家的生命值修正量
     * @param uuid    玩家UUID
     * @param amount  要增加的修正量
     */
    public void addHealthModifier(UUID uuid, double amount) {
        playerHealthModifier.merge(uuid, amount, Double::sum);
        markDirty();
    }

    /**
     * 获取指定玩家的护甲修正量
     * @param uuid 玩家UUID
     * @return 护甲修正量，0表示无修正
     */
    public double getArmorModifier(UUID uuid) {
        return playerArmorModifier.getOrDefault(uuid, 0.0);
    }

    /**
     * 累加指定玩家的护甲修正量
     * @param uuid    玩家UUID
     * @param amount  要增加的修正量
     */
    public void addArmorModifier(UUID uuid, double amount) {
        playerArmorModifier.merge(uuid, amount, Double::sum);
        markDirty();
    }

    // ==================== 残疾玩法 - 受伤计数方法 ====================

    /**
     * 获取指定玩家的累计受伤次数
     * @param uuid 玩家UUID
     * @return 受伤次数
     */
    public int getHurtCount(UUID uuid) {
        return playerHurtCount.getOrDefault(uuid, 0);
    }

    /**
     * 指定玩家受伤次数加1
     * @param uuid 玩家UUID
     */
    public void incrementHurtCount(UUID uuid) {
        playerHurtCount.merge(uuid, 1, Integer::sum);
        markDirty();
    }

    /**
     * 重置指定玩家的受伤计数
     * @param uuid 玩家UUID
     */
    public void resetHurtCount(UUID uuid) {
        playerHurtCount.remove(uuid);
        markDirty();
    }

    /**
     * 获取指定玩家上次受伤的游戏tick
     * @param uuid 玩家UUID
     * @return 游戏tick，0表示从未受伤
     */
    public long getLastHurtTick(UUID uuid) {
        return playerLastHurtTick.getOrDefault(uuid, 0L);
    }

    /**
     * 设置指定玩家上次受伤的游戏tick
     * @param uuid 玩家UUID
     * @param tick 游戏tick
     */
    public void setLastHurtTick(UUID uuid, long tick) {
        playerLastHurtTick.put(uuid, tick);
        markDirty();
    }

    // ==================== 身体部位方法 ====================

    /**
     * 获取指定玩家丢失的身体部位集合（不可修改）
     * @param uuid 玩家UUID
     * @return 丢失的身体部位集合
     */
    public Set<BodyPart> getLostParts(UUID uuid) {
        return Collections.unmodifiableSet(lostParts.getOrDefault(uuid, Collections.emptySet()));
    }

    /**
     * 记录指定玩家丢失一个身体部位
     * @param uuid 玩家UUID
     * @param part 丢失的身体部位
     */
    public void losePart(UUID uuid, BodyPart part) {
        lostParts.computeIfAbsent(uuid, k -> new HashSet<>()).add(part);
        markDirty();
    }

    /**
     * 赎回指定玩家的一个身体部位
     * @param uuid 玩家UUID
     * @param part 要赎回的身体部位
     */
    public void reclaimPart(UUID uuid, BodyPart part) {
        Set<BodyPart> parts = lostParts.get(uuid);
        if (parts != null) {
            parts.remove(part);
            // 如果该玩家已无丢失部位，移除整个条目
            if (parts.isEmpty()) lostParts.remove(uuid);
        }
        markDirty();
    }

    /**
     * 获取所有玩家丢失的身体部位映射（不可修改）
     * @return UUID → 身体部位集合的映射
     */
    public Map<UUID, Set<BodyPart>> getAllLostParts() {
        return Collections.unmodifiableMap(lostParts);
    }

    // ==================== 同步数据方法 ====================

    /**
     * 获取指定玩家上次同步时的生命值
     * @param uuid 玩家UUID
     * @return 上次同步的生命值
     */
    public float getLastSyncedHealth(UUID uuid) {
        return lastSyncedHealth.getOrDefault(uuid, sharedHealth);
    }

    /**
     * 记录指定玩家上次同步时的生命值
     * @param uuid    玩家UUID
     * @param health  生命值
     */
    public void setLastSyncedHealth(UUID uuid, float health) {
        lastSyncedHealth.put(uuid, health);
    }

    /**
     * 获取指定玩家上次同步时的饥饿值
     * @param uuid 玩家UUID
     * @return 上次同步的饥饿值
     */
    public int getLastSyncedHunger(UUID uuid) {
        return lastSyncedHunger.getOrDefault(uuid, sharedHunger);
    }

    /**
     * 记录指定玩家上次同步时的饥饿值
     * @param uuid   玩家UUID
     * @param hunger 饥饿值
     */
    public void setLastSyncedHunger(UUID uuid, int hunger) {
        lastSyncedHunger.put(uuid, hunger);
    }

    /**
     * 清空同步数据（生命值和饥饿值的上次同步记录）
     */
    public void clearSyncData() {
        lastSyncedHealth.clear();
        lastSyncedHunger.clear();
    }

    /**
     * 清空所有玩家丢失的身体部位数据
     */
    public void resetLostParts() {
        lostParts.clear();
        markDirty();
    }

    // ==================== 禁用格子方法 ====================

    /**
     * 获取指定玩家禁用的背包格子集合（不可修改）
     * @param uuid 玩家UUID
     * @return 禁用的格子索引集合
     */
    public Set<Integer> getDisabledSlots(UUID uuid) {
        return Collections.unmodifiableSet(disabledSlots.getOrDefault(uuid, Collections.emptySet()));
    }

    /**
     * 禁用指定玩家的一个背包格子
     * @param uuid 玩家UUID
     * @param slot 要禁用的格子索引
     */
    public void disableSlot(UUID uuid, int slot) {
        disabledSlots.computeIfAbsent(uuid, k -> new HashSet<>()).add(slot);
        markDirty();
    }

    /**
     * 清空指定玩家的所有禁用格子
     * @param uuid 玩家UUID
     */
    public void clearDisabledSlots(UUID uuid) {
        disabledSlots.remove(uuid);
        markDirty();
    }

    /**
     * 清空所有玩家的禁用格子数据
     */
    public void clearAllDisabledSlots() {
        disabledSlots.clear();
        markDirty();
    }

    /**
     * 获取所有玩家的禁用格子映射（不可修改）
     * @return UUID → 禁用格子集合的映射
     */
    public Map<UUID, Set<Integer>> getAllDisabledSlots() {
        return Collections.unmodifiableMap(disabledSlots);
    }

    // ==================== 死亡惩罚方法 ====================

    /**
     * 标记一次死亡惩罚（累加待处理惩罚计数）
     */
    public void markDeath() {
        pendingDeathPenalties++;
    }

    /**
     * 消费一次死亡惩罚
     * @return true表示有待处理的惩罚被消费，false表示没有待处理惩罚
     */
    public boolean consumeDeathPenalty() {
        if (pendingDeathPenalties > 0) {
            pendingDeathPenalties--;
            return true;
        }
        return false;
    }

    /**
     * 重置所有游戏状态为默认值
     *
     * 在切换玩法时调用，将所有字段恢复到初始状态。
     * 清空所有玩家相关的Map数据，重置共享属性。
     */
    public void resetForNewGame() {
        activeGameplay = null;
        sharedHealth = 20.0f;
        sharedHunger = 20;
        sharedSaturation = 5.0f;
        sharedMaxHealth = 20.0f;
        jumpCount = 0;
        lostParts.clear();
        disabledSlots.clear();
        lastSyncedHealth.clear();
        lastSyncedHunger.clear();
        playerHurtCount.clear();
        playerStamina.clear();
        playerFrozenTicks.clear();
        playerLastChunkPos.clear();
        playerBlockedInChunk.clear();
        playerPendingMobs.clear();
        pendingDeathPenalties = 0;
        markDirty();
    }

    // ==================== 肉鸽玩法方法 ====================

    /**
     * 获取肉鸽玩法每次生成的生物数量
     * @return 生物数量
     */
    public int getRoguelikeMobCount() {
        return roguelikeMobCount;
    }

    /**
     * 设置肉鸽玩法每次生成的生物数量，最小为1
     * @param count 目标数量
     */
    public void setRoguelikeMobCount(int count) {
        this.roguelikeMobCount = Math.max(1, count);
        markDirty();
    }

    /**
     * 获取指定玩家上次所在的区块位置
     * 区块位置编码为长整型（chunkX << 32 | chunkZ）
     * @param uuid 玩家UUID
     * @return 编码后的区块位置，0表示未记录
     */
    public long getPlayerLastChunkPos(UUID uuid) {
        return playerLastChunkPos.getOrDefault(uuid, 0L);
    }

    /**
     * 设置指定玩家上次所在的区块位置
     * @param uuid 玩家UUID
     * @param pos  编码后的区块位置
     */
    public void setPlayerLastChunkPos(UUID uuid, long pos) {
        playerLastChunkPos.put(uuid, pos);
        markDirty();
    }

    /**
     * 检查指定玩家是否被锁定在当前区块
     * @param uuid 玩家UUID
     * @return true表示被锁定（需要击杀完生物才能移动）
     */
    public boolean isPlayerBlockedInChunk(UUID uuid) {
        return playerBlockedInChunk.getOrDefault(uuid, false);
    }

    /**
     * 设置指定玩家是否被锁定在当前区块
     * @param uuid    玩家UUID
     * @param blocked true表示锁定
     */
    public void setPlayerBlockedInChunk(UUID uuid, boolean blocked) {
        playerBlockedInChunk.put(uuid, blocked);
        markDirty();
    }

    /**
     * 获取指定玩家待击杀的生物数量
     * @param uuid 玩家UUID
     * @return 待击杀数量
     */
    public int getPlayerPendingMobs(UUID uuid) {
        return playerPendingMobs.getOrDefault(uuid, 0);
    }

    /**
     * 指定玩家的待击杀生物数量加1
     * @param uuid 玩家UUID
     */
    public void incrementPlayerPendingMobs(UUID uuid) {
        playerPendingMobs.merge(uuid, 1, Integer::sum);
        markDirty();
    }

    /**
     * 指定玩家的待击杀生物数量减1（最小为0）
     * @param uuid 玩家UUID
     */
    public void decrementPlayerPendingMobs(UUID uuid) {
        playerPendingMobs.computeIfPresent(uuid, (k, v) -> Math.max(0, v - 1));
        markDirty();
    }

    /**
     * 清空指定玩家的待击杀生物数据
     * @param uuid 玩家UUID
     */
    public void clearPlayerPendingMobs(UUID uuid) {
        playerPendingMobs.remove(uuid);
        markDirty();
    }

    // ==================== 加速玩法方法 ====================

    /**
     * 获取加速玩法的速度倍率
     * @return 速度倍率（百分比，100=正常速度）
     */
    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    /**
     * 设置加速玩法的速度倍率，最小为1
     * @param multiplier 目标速度倍率
     */
    public void setSpeedMultiplier(float multiplier) {
        this.speedMultiplier = Math.max(1, multiplier);
        markDirty();
    }

    // ==================== 全随机玩法方法 ====================

    /**
     * 获取全随机玩法的随机化间隔时间
     * @return 间隔时间（分钟）
     */
    public int getRandomizeIntervalMinutes() {
        return randomizeIntervalMinutes;
    }

    /**
     * 设置全随机玩法的随机化间隔时间，最小为1分钟
     * @param minutes 目标间隔时间（分钟）
     */
    public void setRandomizeIntervalMinutes(int minutes) {
        this.randomizeIntervalMinutes = Math.max(1, minutes);
        markDirty();
    }

    /**
     * 获取上次执行随机化的游戏tick
     * @return 游戏tick
     */
    public long getLastRandomizeTick() {
        return lastRandomizeTick;
    }

    /**
     * 设置上次执行随机化的游戏tick
     * @param tick 游戏tick
     */
    public void setLastRandomizeTick(long tick) {
        this.lastRandomizeTick = tick;
        markDirty();
    }

    // ==================== 弓箭手大作战玩法方法 ====================

    /** 获取弓的力量附魔等级 */
    public int getArcherBowPower() { return archerBowPower; }
    /** 设置弓的力量附魔等级，最小为1 */
    public void setArcherBowPower(int v) { this.archerBowPower = Math.max(1, v); markDirty(); }

    /** 获取弓的冲击附魔等级 */
    public int getArcherBowPunch() { return archerBowPunch; }
    /** 设置弓的冲击附魔等级，最小为0 */
    public void setArcherBowPunch(int v) { this.archerBowPunch = Math.max(0, v); markDirty(); }

    /** 获取弩的多重射击附魔等级 */
    public int getArcherCrossbowMultishot() { return archerCrossbowMultishot; }
    /** 设置弩的多重射击附魔等级，最小为1 */
    public void setArcherCrossbowMultishot(int v) { this.archerCrossbowMultishot = Math.max(1, v); markDirty(); }

    /** 获取弩的穿透附魔等级 */
    public int getArcherCrossbowPiercing() { return archerCrossbowPiercing; }
    /** 设置弩的穿透附魔等级，最小为0 */
    public void setArcherCrossbowPiercing(int v) { this.archerCrossbowPiercing = Math.max(0, v); markDirty(); }

    /** 获取剑的锋利附魔等级 */
    public int getArcherSwordSharpness() { return archerSwordSharpness; }
    /** 设置剑的锋利附魔等级，最小为0 */
    public void setArcherSwordSharpness(int v) { this.archerSwordSharpness = Math.max(0, v); markDirty(); }

    /** 获取剑的火焰附加附魔等级 */
    public int getArcherSwordFireAspect() { return archerSwordFireAspect; }
    /** 设置剑的火焰附加附魔等级，最小为0 */
    public void setArcherSwordFireAspect(int v) { this.archerSwordFireAspect = Math.max(0, v); markDirty(); }

    /** 获取每击杀多少生物获得一件护甲 */
    public int getArcherKillsPerArmor() { return archerKillsPerArmor; }
    /** 设置击杀换甲数，最小为1 */
    public void setArcherKillsPerArmor(int v) { this.archerKillsPerArmor = Math.max(1, v); markDirty(); }

    /** 获取骷髅射出TNT箭的概率（百分比） */
    public int getArcherSkeletonTntChance() { return archerSkeletonTntChance; }
    /** 设置骷髅TNT箭概率，限制在[0, 100] */
    public void setArcherSkeletonTntChance(int v) { this.archerSkeletonTntChance = Math.max(0, Math.min(100, v)); markDirty(); }

    /** 获取流浪者射出凋零箭的概率（百分比） */
    public int getArcherStrayWitherChance() { return archerStrayWitherChance; }
    /** 设置流浪者凋零箭概率，限制在[0, 100] */
    public void setArcherStrayWitherChance(int v) { this.archerStrayWitherChance = Math.max(0, Math.min(100, v)); markDirty(); }

    /** 获取弓箭手生物生成上限 */
    public int getArcherMobLimit() { return archerMobLimit; }
    /** 设置弓箭手生物生成上限，限制在[10, 500] */
    public void setArcherMobLimit(int v) { this.archerMobLimit = Math.max(10, Math.min(500, v)); markDirty(); }

    /**
     * 获取指定玩家的击杀计数
     * @param uuid 玩家UUID
     * @return 击杀数
     */
    public int getArcherKillCount(UUID uuid) { return archerKillCount.getOrDefault(uuid, 0); }

    /**
     * 设置指定玩家的击杀计数
     * @param uuid  玩家UUID
     * @param count 击杀数
     */
    public void setArcherKillCount(UUID uuid, int count) { archerKillCount.put(uuid, count); markDirty(); }

    /**
     * 获取指定玩家的额外护甲点数
     * @param uuid 玩家UUID
     * @return 额外护甲点数
     */
    public int getArcherBonusArmor(UUID uuid) { return archerBonusArmor.getOrDefault(uuid, 0); }

    /**
     * 设置指定玩家的额外护甲点数
     * @param uuid   玩家UUID
     * @param armor  额外护甲点数
     */
    public void setArcherBonusArmor(UUID uuid, int armor) { archerBonusArmor.put(uuid, armor); markDirty(); }

    /**
     * 清空所有玩家的弓箭手击杀数据
     */
    public void clearArcherKillData() { archerKillCount.clear(); markDirty(); }

    // ==================== 追击玩法方法 ====================

    /** 获取追击玩法和平生物攻击范围 */
    public double getChaseAttackRange() { return chaseAttackRange; }
    /** 设置追击玩法和平生物攻击范围，限制在[0.5, 32.0] */
    public void setChaseAttackRange(double v) { this.chaseAttackRange = Math.max(0.5, Math.min(32.0, v)); markDirty(); }
}
