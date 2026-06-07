package com.sacrificemod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;

import java.util.*;

/**
 * 网络包定义类
 *
 * 定义了客户端与服务端之间通信的所有自定义网络包（Payload）。
 * 每个网络包使用record类型定义，包含序列化/反序列化逻辑。
 *
 * 网络包方向分类：
 * - S2C（服务端→客户端）：GameStatePayload、BodyPartSyncPayload、LotteryResultPayload、
 *   DisabledSlotsSyncPayload、StaminaSyncPayload、RandomizeTimeSyncPayload、GameplaySyncPayload
 * - C2S（客户端→服务端）：SacrificeRequestPayload、ReclaimRequestPayload、ToggleGamePayload、
 *   SetJumpThresholdPayload、SetReclaimCostPayload、SetPaperPersonParamsPayload、
 *   SetDisabledParamsPayload、SetRoguelikeMobCountPayload、SetSpeedMultiplierPayload、
 *   SetRandomizeIntervalPayload、SetArcherParamsPayload
 */
public class ModPackets {

    // ==================== 网络包标识符 ====================

    /** 游戏状态同步包ID（S2C） */
    public static final net.minecraft.util.Identifier GAME_STATE_ID = net.minecraft.util.Identifier.of("sacrificemod", "game_state");
    /** 身体部位同步包ID（S2C） */
    public static final net.minecraft.util.Identifier BODY_PART_SYNC_ID = net.minecraft.util.Identifier.of("sacrificemod", "body_part_sync");
    /** 献祭请求包ID（C2S） */
    public static final net.minecraft.util.Identifier SACRIFICE_REQUEST_ID = net.minecraft.util.Identifier.of("sacrificemod", "sacrifice_request");
    /** 赎回请求包ID（C2S） */
    public static final net.minecraft.util.Identifier RECLAIM_REQUEST_ID = net.minecraft.util.Identifier.of("sacrificemod", "reclaim_request");
    /** 切换玩法包ID（C2S） */
    public static final net.minecraft.util.Identifier TOGGLE_GAME_ID = net.minecraft.util.Identifier.of("sacrificemod", "toggle_game");
    /** 抽奖结果包ID（S2C） */
    public static final net.minecraft.util.Identifier LOTTERY_RESULT_ID = net.minecraft.util.Identifier.of("sacrificemod", "lottery_result");
    /** 设置跳跃阈值包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_JUMP_THRESHOLD_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_jump_threshold");
    /** 设置赎回花费包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_RECLAIM_COST_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_reclaim_cost");
    /** 设置纸人参数包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_PAPER_PERSON_PARAMS_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_paper_person_params");
    /** 禁用格子同步包ID（S2C） */
    public static final net.minecraft.util.Identifier DISABLED_SLOTS_SYNC_ID = net.minecraft.util.Identifier.of("sacrificemod", "disabled_slots_sync");
    /** 设置残疾参数包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_DISABLED_PARAMS_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_disabled_params");
    /** 耐力同步包ID（S2C） */
    public static final net.minecraft.util.Identifier STAMINA_SYNC_ID = net.minecraft.util.Identifier.of("sacrificemod", "stamina_sync");
    /** 设置肉鸽生物数量包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_ROGUELIKE_MOB_COUNT_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_roguelike_mob_count");
    /** 设置速度倍率包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_SPEED_MULTIPLIER_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_speed_multiplier");
    /** 设置随机化间隔包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_RANDOMIZE_INTERVAL_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_randomize_interval");
    /** 玩法动态数据同步包ID（S2C） */
    public static final net.minecraft.util.Identifier GAMEPLAY_SYNC_ID = net.minecraft.util.Identifier.of("sacrificemod", "gameplay_sync");
    /** 设置弓箭手参数包ID（C2S） */
    public static final net.minecraft.util.Identifier SET_ARCHER_PARAMS_ID = net.minecraft.util.Identifier.of("sacrificemod", "set_archer_params");

    // ==================== S2C 网络包定义 ====================

    /**
     * 游戏状态同步包（S2C）
     *
     * 服务端向客户端发送完整的游戏状态数据，包括：
     * 当前活跃玩法、共享生命值/饥饿值、各玩法参数等。
     * 在玩家加入服务器或玩法切换时发送。
     *
     * @param activeGameplay   当前活跃的玩法ID，null/空字符串表示无活跃玩法
     * @param sharedHealth     共享生命值
     * @param sharedMaxHealth  共享最大生命值
     * @param sharedHunger     共享饥饿值
     * @param jumpCount        跳跃计数
     * @param jumpThreshold    跳跃阈值
     * @param reclaimCost      赎回钻石花费
     * @param paperFoodLevel   纸人食物饥饿等级
     * @param paperSaturation  纸人食物饱和度
     * @param paperHealthChance  纸人食物恢复生命概率
     * @param paperHealthAmount  纸人食物恢复生命数量
     * @param paperArmorChance   纸人食物给予护甲概率
     * @param paperArmorAmount   纸人食物给予护甲数量
     * @param damageMultiplier   伤害倍率
     * @param disabledChance     残疾概率
     * @param disabledHurtThreshold 残疾受伤阈值
     * @param maxStamina       最大耐力值
     * @param iceDropChance    冰块掉落概率
     */
    public record GameStatePayload(String activeGameplay, float sharedHealth, float sharedMaxHealth,
                                   int sharedHunger, int jumpCount, int jumpThreshold,
                                   int reclaimCost, int paperFoodLevel, float paperSaturation,
                                   int paperHealthChance, int paperHealthAmount,
                                   int paperArmorChance, int paperArmorAmount,
                                   float damageMultiplier,
                                   int disabledChance, int disabledHurtThreshold,
                                   int maxStamina, int iceDropChance) implements CustomPayload {
        public static final Id<GameStatePayload> ID = new Id<>(GAME_STATE_ID);

        /**
         * 从网络缓冲区反序列化GameStatePayload
         * @param buf 网络缓冲区
         * @return 反序列化后的Payload实例
         */
        public static GameStatePayload read(PacketByteBuf buf) {
            // 空字符串表示无活跃玩法，转换为null
            String gameplay = buf.readString(32);
            return new GameStatePayload(
                    gameplay.isEmpty() ? null : gameplay,
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            );
        }

        /**
         * 将Payload序列化到网络缓冲区
         * @param buf 目标网络缓冲区
         */
        public void write(PacketByteBuf buf) {
            // null转换为空字符串传输
            buf.writeString(activeGameplay != null ? activeGameplay : "");
            buf.writeFloat(sharedHealth);
            buf.writeFloat(sharedMaxHealth);
            buf.writeInt(sharedHunger);
            buf.writeInt(jumpCount);
            buf.writeInt(jumpThreshold);
            buf.writeInt(reclaimCost);
            buf.writeInt(paperFoodLevel);
            buf.writeFloat(paperSaturation);
            buf.writeInt(paperHealthChance);
            buf.writeInt(paperHealthAmount);
            buf.writeInt(paperArmorChance);
            buf.writeInt(paperArmorAmount);
            buf.writeFloat(damageMultiplier);
            buf.writeInt(disabledChance);
            buf.writeInt(disabledHurtThreshold);
            buf.writeInt(maxStamina);
            buf.writeInt(iceDropChance);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 身体部位同步包（S2C）
     *
     * 服务端向客户端同步指定玩家丢失的身体部位数据。
     * 使用序号数组传输以节省带宽。
     *
     * @param playerUuid    玩家UUID
     * @param partOrdinals  丢失的身体部位序号数组
     */
    public record BodyPartSyncPayload(UUID playerUuid, int[] partOrdinals) implements CustomPayload {
        public static final Id<BodyPartSyncPayload> ID = new Id<>(BODY_PART_SYNC_ID);

        /** 从网络缓冲区反序列化 */
        public static BodyPartSyncPayload read(PacketByteBuf buf) {
            UUID uuid = buf.readUuid();
            int[] ordinals = buf.readIntArray();
            return new BodyPartSyncPayload(uuid, ordinals);
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeUuid(playerUuid);
            buf.writeIntArray(partOrdinals);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 献祭请求包（C2S）
     *
     * 客户端向服务端发送献祭指定身体部位的请求。
     *
     * @param bodyPartOrdinal 要献祭的身体部位序号（对应BodyPart枚举的ordinal）
     */
    public record SacrificeRequestPayload(int bodyPartOrdinal) implements CustomPayload {
        public static final Id<SacrificeRequestPayload> ID = new Id<>(SACRIFICE_REQUEST_ID);

        /** 从网络缓冲区反序列化 */
        public static SacrificeRequestPayload read(PacketByteBuf buf) {
            return new SacrificeRequestPayload(buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(bodyPartOrdinal);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 赎回请求包（C2S）
     *
     * 客户端向服务端发送用钻石赎回指定身体部位的请求。
     *
     * @param bodyPartOrdinal 要赎回的身体部位序号
     */
    public record ReclaimRequestPayload(int bodyPartOrdinal) implements CustomPayload {
        public static final Id<ReclaimRequestPayload> ID = new Id<>(RECLAIM_REQUEST_ID);

        /** 从网络缓冲区反序列化 */
        public static ReclaimRequestPayload read(PacketByteBuf buf) {
            return new ReclaimRequestPayload(buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(bodyPartOrdinal);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 切换玩法包（C2S）
     *
     * 客户端向服务端发送开始或停止某个玩法的请求。
     *
     * @param start      true表示开始玩法，false表示停止玩法
     * @param gameplayId 要切换的玩法ID
     */
    public record ToggleGamePayload(boolean start, String gameplayId) implements CustomPayload {
        public static final Id<ToggleGamePayload> ID = new Id<>(TOGGLE_GAME_ID);

        /** 从网络缓冲区反序列化 */
        public static ToggleGamePayload read(PacketByteBuf buf) {
            return new ToggleGamePayload(buf.readBoolean(), buf.readString(32));
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeBoolean(start);
            buf.writeString(gameplayId != null ? gameplayId : "");
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 抽奖结果包（S2C）
     *
     * 服务端向客户端发送献祭抽奖的结果。
     *
     * @param result           抽奖结果代码
     * @param bodyPartOrdinal  相关的身体部位序号
     */
    public record LotteryResultPayload(int result, int bodyPartOrdinal) implements CustomPayload {
        public static final Id<LotteryResultPayload> ID = new Id<>(LOTTERY_RESULT_ID);

        /** 从网络缓冲区反序列化 */
        public static LotteryResultPayload read(PacketByteBuf buf) {
            return new LotteryResultPayload(buf.readInt(), buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(result);
            buf.writeInt(bodyPartOrdinal);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置跳跃阈值包（C2S）
     *
     * 客户端向服务端发送修改献祭玩法跳跃阈值的请求。
     *
     * @param threshold 目标跳跃阈值
     */
    public record SetJumpThresholdPayload(int threshold) implements CustomPayload {
        public static final Id<SetJumpThresholdPayload> ID = new Id<>(SET_JUMP_THRESHOLD_ID);

        /** 从网络缓冲区反序列化 */
        public static SetJumpThresholdPayload read(PacketByteBuf buf) {
            return new SetJumpThresholdPayload(buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(threshold);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置赎回花费包（C2S）
     *
     * 客户端向服务端发送修改赎回钻石花费的请求。
     *
     * @param cost 目标钻石花费
     */
    public record SetReclaimCostPayload(int cost) implements CustomPayload {
        public static final Id<SetReclaimCostPayload> ID = new Id<>(SET_RECLAIM_COST_ID);

        /** 从网络缓冲区反序列化 */
        public static SetReclaimCostPayload read(PacketByteBuf buf) {
            return new SetReclaimCostPayload(buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(cost);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置纸人玩法参数包（C2S）
     *
     * 客户端向服务端发送修改纸人玩法所有参数的请求。
     *
     * @param foodLevel      食物恢复的饥饿等级
     * @param saturation     食物恢复的饱和度
     * @param healthChance   恢复生命概率
     * @param healthAmount   恢复生命数量
     * @param armorChance    给予护甲概率
     * @param armorAmount    给予护甲数量
     * @param damageMultiplier 伤害倍率
     * @param maxStamina     最大耐力值
     * @param iceDropChance  冰块掉落概率
     */
    public record SetPaperPersonParamsPayload(int foodLevel, float saturation, int healthChance,
                                               int healthAmount, int armorChance, int armorAmount,
                                               float damageMultiplier,
                                               int maxStamina, int iceDropChance) implements CustomPayload {
        public static final Id<SetPaperPersonParamsPayload> ID = new Id<>(SET_PAPER_PERSON_PARAMS_ID);

        /** 从网络缓冲区反序列化 */
        public static SetPaperPersonParamsPayload read(PacketByteBuf buf) {
            return new SetPaperPersonParamsPayload(
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt()
            );
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(foodLevel);
            buf.writeFloat(saturation);
            buf.writeInt(healthChance);
            buf.writeInt(healthAmount);
            buf.writeInt(armorChance);
            buf.writeInt(armorAmount);
            buf.writeFloat(damageMultiplier);
            buf.writeInt(maxStamina);
            buf.writeInt(iceDropChance);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 禁用格子同步包（S2C）
     *
     * 服务端向客户端同步指定玩家被禁用的背包格子索引。
     *
     * @param playerUuid  玩家UUID
     * @param slotIndices 被禁用的格子索引数组
     */
    public record DisabledSlotsSyncPayload(UUID playerUuid, int[] slotIndices) implements CustomPayload {
        public static final Id<DisabledSlotsSyncPayload> ID = new Id<>(DISABLED_SLOTS_SYNC_ID);

        /** 从网络缓冲区反序列化 */
        public static DisabledSlotsSyncPayload read(PacketByteBuf buf) {
            UUID uuid = buf.readUuid();
            int[] slots = buf.readIntArray();
            return new DisabledSlotsSyncPayload(uuid, slots);
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeUuid(playerUuid);
            buf.writeIntArray(slotIndices);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置残疾玩法参数包（C2S）
     *
     * 客户端向服务端发送修改残疾玩法参数的请求。
     *
     * @param chance    残疾概率（百分比）
     * @param threshold 受伤次数阈值
     */
    public record SetDisabledParamsPayload(int chance, int threshold) implements CustomPayload {
        public static final Id<SetDisabledParamsPayload> ID = new Id<>(SET_DISABLED_PARAMS_ID);

        /** 从网络缓冲区反序列化 */
        public static SetDisabledParamsPayload read(PacketByteBuf buf) {
            return new SetDisabledParamsPayload(buf.readInt(), buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(chance);
            buf.writeInt(threshold);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 耐力同步包（S2C）
     *
     * 服务端向客户端同步指定玩家的耐力值和冻结状态。
     *
     * @param playerUuid   玩家UUID
     * @param stamina      当前耐力值
     * @param maxStamina   最大耐力值
     * @param frozenTicks  冻结剩余tick数
     */
    public record StaminaSyncPayload(UUID playerUuid, float stamina, float maxStamina, int frozenTicks) implements CustomPayload {
        public static final Id<StaminaSyncPayload> ID = new Id<>(STAMINA_SYNC_ID);

        /** 从网络缓冲区反序列化 */
        public static StaminaSyncPayload read(PacketByteBuf buf) {
            return new StaminaSyncPayload(
                    buf.readUuid(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt()
            );
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeUuid(playerUuid);
            buf.writeFloat(stamina);
            buf.writeFloat(maxStamina);
            buf.writeInt(frozenTicks);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置肉鸽生物数量包（C2S）
     *
     * 客户端向服务端发送修改肉鸽玩法每次生成生物数量的请求。
     *
     * @param mobCount 目标生物数量
     */
    public record SetRoguelikeMobCountPayload(int mobCount) implements CustomPayload {
        public static final Id<SetRoguelikeMobCountPayload> ID = new Id<>(SET_ROGUELIKE_MOB_COUNT_ID);

        /** 从网络缓冲区反序列化 */
        public static SetRoguelikeMobCountPayload read(PacketByteBuf buf) {
            return new SetRoguelikeMobCountPayload(buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(mobCount);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置速度倍率包（C2S）
     *
     * 客户端向服务端发送修改加速玩法速度倍率的请求。
     *
     * @param multiplier 目标速度倍率（百分比）
     */
    public record SetSpeedMultiplierPayload(float multiplier) implements CustomPayload {
        public static final Id<SetSpeedMultiplierPayload> ID = new Id<>(SET_SPEED_MULTIPLIER_ID);

        /** 从网络缓冲区反序列化 */
        public static SetSpeedMultiplierPayload read(PacketByteBuf buf) {
            return new SetSpeedMultiplierPayload(buf.readFloat());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeFloat(multiplier);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置随机化间隔包（C2S）
     *
     * 客户端向服务端发送修改全随机玩法随机化间隔时间的请求。
     *
     * @param minutes 目标间隔时间（分钟）
     */
    public record SetRandomizeIntervalPayload(int minutes) implements CustomPayload {
        public static final Id<SetRandomizeIntervalPayload> ID = new Id<>(SET_RANDOMIZE_INTERVAL_ID);

        /** 从网络缓冲区反序列化 */
        public static SetRandomizeIntervalPayload read(PacketByteBuf buf) {
            return new SetRandomizeIntervalPayload(buf.readInt());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(minutes);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 随机化时间同步包（S2C）
     *
     * 服务端向客户端同步全随机玩法的倒计时信息。
     *
     * @param remainingTicks 距离下次随机化的剩余tick数
     * @param intervalTicks  随机化间隔的总tick数
     */
    public record RandomizeTimeSyncPayload(long remainingTicks, long intervalTicks) implements CustomPayload {
        public static final Id<RandomizeTimeSyncPayload> ID = new Id<>(net.minecraft.util.Identifier.of("sacrificemod", "randomize_time_sync"));

        /** 从网络缓冲区反序列化 */
        public static RandomizeTimeSyncPayload read(PacketByteBuf buf) {
            return new RandomizeTimeSyncPayload(buf.readLong(), buf.readLong());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeLong(remainingTicks);
            buf.writeLong(intervalTicks);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 玩法动态数据同步包（S2C）
     *
     * 服务端定期向客户端同步肉鸽/加速玩法的动态数据。
     * 每20tick（1秒）发送一次。
     *
     * @param roguelikePendingMobs 肉鸽玩法待击杀生物数（非肉鸽玩法为0）
     * @param speedMultiplier      加速玩法速度倍率（非加速玩法为100%）
     */
    public record GameplaySyncPayload(int roguelikePendingMobs, float speedMultiplier) implements CustomPayload {
        public static final Id<GameplaySyncPayload> ID = new Id<>(GAMEPLAY_SYNC_ID);

        /** 从网络缓冲区反序列化 */
        public static GameplaySyncPayload read(PacketByteBuf buf) {
            return new GameplaySyncPayload(
                    buf.readInt(),
                    buf.readFloat()
            );
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(roguelikePendingMobs);
            buf.writeFloat(speedMultiplier);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 设置弓箭手玩法参数包（C2S）
     *
     * 客户端向服务端发送修改弓箭手大作战玩法所有参数的请求。
     *
     * @param bowPower           弓的力量附魔等级
     * @param bowPunch           弓的冲击附魔等级
     * @param crossbowMultishot  弩的多重射击附魔等级
     * @param crossbowPiercing   弩的穿透附魔等级
     * @param swordSharpness     剑的锋利附魔等级
     * @param swordFireAspect    剑的火焰附加附魔等级
     * @param killsPerArmor      击杀换甲数
     * @param skeletonTntChance  骷髅TNT箭概率
     * @param strayWitherChance  流浪者凋零箭概率
     */
    public record SetArcherParamsPayload(int bowPower, int bowPunch, int crossbowMultishot,
                                          int crossbowPiercing, int swordSharpness,
                                          int swordFireAspect, int killsPerArmor,
                                          int skeletonTntChance, int strayWitherChance,
                                          int mobLimit) implements CustomPayload {
        public static final Id<SetArcherParamsPayload> ID = new Id<>(SET_ARCHER_PARAMS_ID);

        /** 从网络缓冲区反序列化 */
        public static SetArcherParamsPayload read(PacketByteBuf buf) {
            return new SetArcherParamsPayload(
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt()
            );
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeInt(bowPower);
            buf.writeInt(bowPunch);
            buf.writeInt(crossbowMultishot);
            buf.writeInt(crossbowPiercing);
            buf.writeInt(swordSharpness);
            buf.writeInt(swordFireAspect);
            buf.writeInt(killsPerArmor);
            buf.writeInt(skeletonTntChance);
            buf.writeInt(strayWitherChance);
            buf.writeInt(mobLimit);
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** 追击玩法参数设置包（C2S） */
    public record SetChaseParamsPayload(double attackRange) implements CustomPayload {
        public static final Id<SetChaseParamsPayload> ID = new Id<>(net.minecraft.util.Identifier.of("sacrificemod", "set_chase_params"));

        public static SetChaseParamsPayload read(PacketByteBuf buf) {
            return new SetChaseParamsPayload(buf.readDouble());
        }

        /** 序列化到网络缓冲区 */
        public void write(PacketByteBuf buf) {
            buf.writeDouble(attackRange);
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * 注册所有网络包到Fabric网络系统
     *
     * 将所有Payload类型注册到PayloadTypeRegistry，
     * 区分S2C（服务端→客户端）和C2S（客户端→服务端）方向。
     * 必须在模组初始化时调用。
     */
    public static void register() {
        // ===== S2C（服务端→客户端）注册 =====
        PayloadTypeRegistry.playS2C().register(GameStatePayload.ID, CustomPayload.codecOf(GameStatePayload::write, GameStatePayload::read));
        PayloadTypeRegistry.playS2C().register(BodyPartSyncPayload.ID, CustomPayload.codecOf(BodyPartSyncPayload::write, BodyPartSyncPayload::read));
        PayloadTypeRegistry.playS2C().register(LotteryResultPayload.ID, CustomPayload.codecOf(LotteryResultPayload::write, LotteryResultPayload::read));
        PayloadTypeRegistry.playS2C().register(DisabledSlotsSyncPayload.ID, CustomPayload.codecOf(DisabledSlotsSyncPayload::write, DisabledSlotsSyncPayload::read));
        PayloadTypeRegistry.playS2C().register(StaminaSyncPayload.ID, CustomPayload.codecOf(StaminaSyncPayload::write, StaminaSyncPayload::read));
        PayloadTypeRegistry.playS2C().register(RandomizeTimeSyncPayload.ID, CustomPayload.codecOf(RandomizeTimeSyncPayload::write, RandomizeTimeSyncPayload::read));
        PayloadTypeRegistry.playS2C().register(GameplaySyncPayload.ID, CustomPayload.codecOf(GameplaySyncPayload::write, GameplaySyncPayload::read));

        // ===== C2S（客户端→服务端）注册 =====
        PayloadTypeRegistry.playC2S().register(SacrificeRequestPayload.ID, CustomPayload.codecOf(SacrificeRequestPayload::write, SacrificeRequestPayload::read));
        PayloadTypeRegistry.playC2S().register(ReclaimRequestPayload.ID, CustomPayload.codecOf(ReclaimRequestPayload::write, ReclaimRequestPayload::read));
        PayloadTypeRegistry.playC2S().register(ToggleGamePayload.ID, CustomPayload.codecOf(ToggleGamePayload::write, ToggleGamePayload::read));
        PayloadTypeRegistry.playC2S().register(SetJumpThresholdPayload.ID, CustomPayload.codecOf(SetJumpThresholdPayload::write, SetJumpThresholdPayload::read));
        PayloadTypeRegistry.playC2S().register(SetReclaimCostPayload.ID, CustomPayload.codecOf(SetReclaimCostPayload::write, SetReclaimCostPayload::read));
        PayloadTypeRegistry.playC2S().register(SetPaperPersonParamsPayload.ID, CustomPayload.codecOf(SetPaperPersonParamsPayload::write, SetPaperPersonParamsPayload::read));
        PayloadTypeRegistry.playC2S().register(SetDisabledParamsPayload.ID, CustomPayload.codecOf(SetDisabledParamsPayload::write, SetDisabledParamsPayload::read));
        PayloadTypeRegistry.playC2S().register(SetRoguelikeMobCountPayload.ID, CustomPayload.codecOf(SetRoguelikeMobCountPayload::write, SetRoguelikeMobCountPayload::read));
        PayloadTypeRegistry.playC2S().register(SetSpeedMultiplierPayload.ID, CustomPayload.codecOf(SetSpeedMultiplierPayload::write, SetSpeedMultiplierPayload::read));
        PayloadTypeRegistry.playC2S().register(SetRandomizeIntervalPayload.ID, CustomPayload.codecOf(SetRandomizeIntervalPayload::write, SetRandomizeIntervalPayload::read));
        PayloadTypeRegistry.playC2S().register(SetArcherParamsPayload.ID, CustomPayload.codecOf(SetArcherParamsPayload::write, SetArcherParamsPayload::read));
        PayloadTypeRegistry.playC2S().register(SetChaseParamsPayload.ID, CustomPayload.codecOf(SetChaseParamsPayload::write, SetChaseParamsPayload::read));
    }
}
