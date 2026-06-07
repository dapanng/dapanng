package com.sacrificemod;

import com.sacrificemod.gameplay.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 献祭系统模组主类
 *
 * 这是一个Fabric模组，实现了多种游戏玩法：
 * - 献祭玩法：共享生命值、献祭身体部位抽奖
 * - 纸人玩法：初始生命值1，可食用特殊物品恢复状态
 * - 残疾玩法：受伤后禁用背包格子
 * - 肉鸽玩法：进入新区块生成随机生物
 * - 加速玩法：所有生物速度加快
 * - 全随机玩法：配方和掉落物随机化
 * - 追击玩法：追击目标玩家
 * - 传送玩法：随机传送
 * - 弓箭手大作战玩法：弓箭手对战
 *
 * @author SacrificeMod Team
 * @version 1.3.0
 */
public class SacrificeMod implements ModInitializer {

    /** 模组日志记录器，用于输出调试和运行信息 */
    public static final Logger LOGGER = LoggerFactory.getLogger("sacrificemod");

    /** 同步标志，防止在同步生命值/饥饿值时触发循环伤害处理 */
    private static boolean syncing = false;

    /** 当前活跃的玩法ID（静态缓存，与持久化状态保持同步） */
    private static String activeGameplay = null;

    /** 随机数生成器，用于各种随机逻辑 */
    private static final Random RANDOM = Random.create();

    // ==================== 玩法实例 ====================

    /** 献祭玩法实例：共享生命值，献祭身体部位获取跳跃计数 */
    public static final SacrificeGameplay SACRIFICE_GAMEPLAY = new SacrificeGameplay();

    /** 纸人玩法实例：初始1血，食用纸/冰恢复，耐力系统 */
    public static final PaperPersonGameplay PAPER_PERSON_GAMEPLAY = new PaperPersonGameplay();

    /** 残疾玩法实例：受伤后随机禁用背包格子 */
    public static final DisabledGameplay DISABLED_GAMEPLAY = new DisabledGameplay();

    /** 肉鸽玩法实例：进入新区块生成随机生物，击杀后才能继续前进 */
    public static final RoguelikeGameplay ROGUELIKE_GAMEPLAY = new RoguelikeGameplay();

    /** 加速玩法实例：所有生物移动速度按倍率加快 */
    public static final SpeedGameplay SPEED_GAMEPLAY = new SpeedGameplay();

    /** 全随机玩法实例：定期随机化配方和掉落物 */
    public static final RandomGameplay RANDOM_GAMEPLAY = new RandomGameplay();

    /** 追击玩法实例：指定追击目标玩家 */
    public static final ChaseGameplay CHASE_GAMEPLAY = new ChaseGameplay();

    /** 传送玩法实例：随机传送玩家 */
    public static final TeleportGameplay TELEPORT_GAMEPLAY = new TeleportGameplay();

    /** 弓箭手大作战玩法实例：弓箭手对战，击杀获取装备 */
    public static final ArcherGameplay ARCHER_GAMEPLAY = new ArcherGameplay();

    /**
     * 玩法注册表：玩法ID → 玩法实例的映射
     * 用于根据ID快速查找对应的玩法实例
     */
    private static final Map<String, BaseGameplay> GAMEPLAY_REGISTRY = new HashMap<>();

    static {
        // 注册所有玩法到注册表，键为玩法ID，值为玩法实例
        GAMEPLAY_REGISTRY.put("sacrifice", SACRIFICE_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("paper_person", PAPER_PERSON_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("disabled", DISABLED_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("roguelike", ROGUELIKE_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("speed", SPEED_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("random", RANDOM_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("chase", CHASE_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("teleport", TELEPORT_GAMEPLAY);
        GAMEPLAY_REGISTRY.put("archer", ARCHER_GAMEPLAY);
    }

    // ==================== 初始化 ====================

    /**
     * 模组初始化入口方法
     *
     * 在服务器启动时由Fabric框架调用，完成以下工作：
     * 1. 注册网络通信包
     * 2. 注册服务器tick事件
     * 3. 注册服务器启动事件（恢复玩法状态）
     * 4. 注册伤害、复活、玩家加入等事件
     * 5. 注册物品使用和方块破坏事件
     * 6. 注册网络包处理器
     */
    @Override
    public void onInitialize() {
        LOGGER.info("献祭系统模组正在初始化...");

        // 注册网络包，定义客户端与服务端之间的通信协议
        ModPackets.register();

        // 注册服务器tick事件，每tick执行一次玩法逻辑
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // 服务器启动时，从持久化状态恢复活跃玩法
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GameState state = GameState.getServerState(server);
            if (state.isAnyGameplayActive()) {
                activeGameplay = state.getActiveGameplay();
                LOGGER.info("服务器启动时恢复活跃玩法: {}", activeGameplay);
            }
        });

        // 注册伤害事件，用于各玩法拦截/修改伤害逻辑
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::onDamage);

        // 注册复活事件，用于各玩法处理玩家复活后的状态
        ServerPlayerEvents.AFTER_RESPAWN.register(this::onRespawn);

        // 注册玩家加入事件，同步服务端数据到新加入的客户端
        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);

        // 注册物品使用事件，主要用于纸人玩法拦截食物/特殊物品
        UseItemCallback.EVENT.register(this::onUseItem);

        // 注册网络包处理器，处理客户端发来的各种请求
        registerPacketHandlers();

        // 注册方块破坏事件，用于纸人玩法冰块掉落逻辑
        PlayerBlockBreakEvents.BEFORE.register(this::onBlockBreak);

        // 注册实体死亡事件，用于肉鸽玩法击杀检测
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity.getWorld().getServer() instanceof MinecraftServer server)) return;
            GameState state = GameState.getServerState(server);
            if (state.isGameplayActive("roguelike")) {
                ROGUELIKE_GAMEPLAY.onEntityKill(server, state, entity);
            }
        });

        LOGGER.info("献祭系统模组初始化完成！");
    }

    // ==================== 事件处理 ====================

    /**
     * 服务器tick事件处理
     *
     * 每个服务器tick结束时调用，执行以下操作：
     * 1. 同步静态变量与持久化状态（防止状态不一致）
     * 2. 分发tick事件到当前活跃的玩法
     * 3. 定期同步肉鸽/加速玩法的动态数据到客户端
     *
     * @param server 当前Minecraft服务器实例
     */
    private void onServerTick(MinecraftServer server) {
        GameState state = GameState.getServerState(server);

        // 安全检查：确保静态变量activeGameplay与持久化状态一致
        if (state.isAnyGameplayActive()) {
            String persistedId = state.getActiveGameplay();
            if (!persistedId.equals(activeGameplay)) {
                activeGameplay = persistedId;
                LOGGER.warn("从持久化状态同步activeGameplay: {}", persistedId);
            }
        }

        // 没有活跃玩法则跳过
        if (!state.isAnyGameplayActive()) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        // 根据活跃玩法ID查找对应实例并执行tick逻辑
        String gameplayId = state.getActiveGameplay();
        BaseGameplay gameplay = GAMEPLAY_REGISTRY.get(gameplayId);
        if (gameplay != null) {
            gameplay.onTick(server, state, players);
        }

        // 每20tick（1秒）同步肉鸽/加速玩法的动态数据到客户端
        if (server.getTicks() % 20 == 0) {
            if ("roguelike".equals(gameplayId) || "speed".equals(gameplayId)) {
                for (ServerPlayerEntity player : players) {
                    // 获取肉鸽玩法的待击杀生物数，非肉鸽玩法为0
                    int pendingMobs = "roguelike".equals(gameplayId) ? state.getPlayerPendingMobs(player.getUuid()) : 0;
                    // 获取加速玩法的速度倍率，非加速玩法为100%
                    float speedMult = "speed".equals(gameplayId) ? state.getSpeedMultiplier() : 100.0f;
                    ServerPlayNetworking.send(player, new ModPackets.GameplaySyncPayload(pendingMobs, speedMult));
                }
            }
        }
    }

    /**
     * 伤害事件处理
     *
     * 当实体受到伤害时调用，将伤害事件分发给当前活跃的玩法处理。
     * 如果正在同步中（syncing=true），则跳过处理以防止循环伤害。
     *
     * @param entity  受伤的实体
     * @param source  伤害来源
     * @param amount  伤害数值
     * @return true表示允许伤害，false表示取消伤害
     */
    private boolean onDamage(net.minecraft.entity.LivingEntity entity,
                            net.minecraft.entity.damage.DamageSource source, float amount) {
        // 同步中不拦截伤害，防止循环处理
        if (syncing) return true;
        // 只处理服务端玩家实体
        if (!(entity instanceof ServerPlayerEntity player)) return true;

        MinecraftServer server = player.getServer();
        if (server == null) return true;

        GameState state = GameState.getServerState(server);
        if (!state.isAnyGameplayActive()) return true;

        // 分发给对应玩法处理伤害逻辑
        String gameplayId = state.getActiveGameplay();
        BaseGameplay gameplay = GAMEPLAY_REGISTRY.get(gameplayId);
        if (gameplay != null) {
            return gameplay.onDamage(server, state, player, source, amount);
        }

        return true;
    }

    /**
     * 玩家复活事件处理
     *
     * 当玩家复活时调用，将复活事件分发给当前活跃的玩法处理。
     * 各玩法可在此恢复或重置玩家复活后的状态。
     *
     * @param oldPlayer  复活前的玩家实体（已死亡）
     * @param newPlayer  复活后的新玩家实体
     * @param alive      是否为活着的复活（如末地传送门），false表示死亡后复活
     */
    private void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        MinecraftServer server = newPlayer.getServer();
        if (server == null) return;

        GameState state = GameState.getServerState(server);
        if (!state.isAnyGameplayActive()) return;

        // 分发给对应玩法处理复活逻辑
        String gameplayId = state.getActiveGameplay();
        BaseGameplay gameplay = GAMEPLAY_REGISTRY.get(gameplayId);
        if (gameplay != null) {
            gameplay.onRespawn(server, state, newPlayer);
        }
    }

    /**
     * 玩家加入事件处理
     *
     * 当玩家加入服务器时调用，将服务端的游戏状态数据同步到客户端。
     * 根据当前活跃的玩法类型，同步不同的数据：
     * - 所有玩法：基础游戏状态（生命值、饥饿值、参数等）
     * - 纸人玩法：耐力值、冻结时间
     * - 残疾玩法：禁用的背包格子
     * - 全随机玩法：下次随机化的剩余时间
     * - 肉鸽/加速玩法：待击杀生物数、速度倍率
     *
     * @param handler  服务端网络连接处理器
     * @param sender   发送者（未使用）
     * @param server   Minecraft服务器实例
     */
    private void onPlayerJoin(net.minecraft.server.network.ServerPlayNetworkHandler handler,
                             Object sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.getPlayer();
        GameState state = GameState.getServerState(server);

        // 发送基础游戏状态到客户端
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

        // 纸人玩法：同步耐力值和冻结时间
        if (state.isGameplayActive("paper_person")) {
            ServerPlayNetworking.send(player, new ModPackets.StaminaSyncPayload(
                player.getUuid(), state.getStamina(player.getUuid()),
                state.getMaxStamina(), state.getFrozenTicks(player.getUuid())
            ));
        }

        // 残疾玩法：同步禁用的背包格子索引
        if (state.isGameplayActive("disabled")) {
            Set<Integer> disabled = state.getDisabledSlots(player.getUuid());
            int[] slots = disabled.stream().mapToInt(Integer::intValue).toArray();
            ServerPlayNetworking.send(player, new ModPackets.DisabledSlotsSyncPayload(player.getUuid(), slots));
        }

        // 全随机玩法：同步下次随机化的剩余时间和间隔
        if (state.isGameplayActive("random")) {
            long intervalTicks = (long) state.getRandomizeIntervalMinutes() * 60 * 20;
            long remainingTicks = intervalTicks - (server.getTicks() - state.getLastRandomizeTick());
            if (remainingTicks < 0) remainingTicks = 0;
            ServerPlayNetworking.send(player, new ModPackets.RandomizeTimeSyncPayload(remainingTicks, intervalTicks));
        }

        // 肉鸽/加速玩法：同步待击杀生物数和速度倍率
        if (state.isGameplayActive("roguelike") || state.isGameplayActive("speed")) {
            int pendingMobs = state.isGameplayActive("roguelike") ? state.getPlayerPendingMobs(player.getUuid()) : 0;
            float speedMult = state.isGameplayActive("speed") ? state.getSpeedMultiplier() : 100.0f;
            ServerPlayNetworking.send(player, new ModPackets.GameplaySyncPayload(pendingMobs, speedMult));
        }
    }

    /**
     * 物品使用事件处理
     *
     * 当玩家使用（右键）物品时调用，主要用于纸人玩法：
     * - 允许使用纸、雪块、冰类物品（纸人玩法的特殊食物）
     * - 阻止使用普通食物（纸人不能吃普通食物）
     * - 阻止使用熔岩桶（防止纸人自毁）
     *
     * @param player  使用物品的玩家
     * @param world   玩家所在世界
     * @param hand    使用物品的手（主手/副手）
     * @return 操作结果：pass表示不拦截，fail表示阻止使用
     */
    private TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        // 客户端不处理
        if (world.isClient()) return TypedActionResult.pass(player.getStackInHand(hand));

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return TypedActionResult.pass(player.getStackInHand(hand));

        GameState state = GameState.getServerState(server);

        // 纸人玩法物品使用逻辑
        if (state.isGameplayActive("paper_person")) {
            ItemStack stack = serverPlayer.getStackInHand(hand);
            // 允许使用纸人特殊食物：纸、雪块、冰、蓝冰、浮冰
            if (stack.getItem() == Items.PAPER || stack.getItem() == Items.SNOW_BLOCK
                    || stack.getItem() == Items.ICE || stack.getItem() == Items.BLUE_ICE
                    || stack.getItem() == Items.PACKED_ICE) {
                return PAPER_PERSON_GAMEPLAY.onUseItem(serverPlayer, state, stack, hand);
            }
            // 阻止普通食物（纸人不能吃普通食物）
            if (stack.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
                return TypedActionResult.fail(stack);
            }
            // 阻止熔岩桶（防止纸人利用熔岩自杀）
            if (stack.getItem() == Items.LAVA_BUCKET) {
                return TypedActionResult.fail(stack);
            }
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    /**
     * 方块破坏事件处理
     *
     * 当玩家破坏方块时调用，用于纸人玩法的冰块掉落逻辑。
     * 纸人玩法中，破坏冰类方块有概率掉落冰物品作为食物来源。
     *
     * @param world        方块所在世界
     * @param player       破坏方块的玩家
     * @param pos          方块位置
     * @param state        方块状态
     * @param blockEntity  方块实体（如有）
     * @return true允许破坏，false阻止破坏
     */
    private boolean onBlockBreak(net.minecraft.world.World world, net.minecraft.entity.player.PlayerEntity player,
                                 net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state,
                                 net.minecraft.block.entity.BlockEntity blockEntity) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return true;

        GameState gameState = GameState.getServerState(server);

        // 纸人玩法：处理冰块掉落逻辑
        if (gameState.isGameplayActive("paper_person")) {
            return PAPER_PERSON_GAMEPLAY.onBlockBreak(world, serverPlayer, pos, state, gameState);
        }

        return true;
    }

    // ==================== 网络包处理 ====================

    /**
     * 注册所有网络包处理器
     *
     * 注册客户端发来的各种请求的处理器，包括：
     * - 献祭请求：客户端请求献祭某个身体部位
     * - 赎金请求：客户端请求用钻石赎回身体部位
     * - 切换玩法：客户端请求开始/停止某个玩法
     * - 各玩法参数设置请求
     */
    private void registerPacketHandlers() {
        // 献祭请求：处理客户端发起的身体部位献祭
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SacrificeRequestPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            // 将序号转换为BodyPart枚举
            BodyPart part = BodyPart.values()[payload.bodyPartOrdinal()];
            SACRIFICE_GAMEPLAY.handleSacrifice(server, state, context.player(), part);
        });

        // 赎金请求：处理客户端发起的身体部位赎回
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.ReclaimRequestPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            BodyPart part = BodyPart.values()[payload.bodyPartOrdinal()];
            SACRIFICE_GAMEPLAY.handleReclaim(server, state, context.player(), part);
        });

        // 切换玩法：处理客户端发起的开始/停止玩法请求
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.ToggleGamePayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            handleToggleGame(server, context.player(), payload.start(), payload.gameplayId());
        });

        // 献祭玩法参数设置：跳跃阈值
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetJumpThresholdPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            // 确保阈值至少为1
            state.setJumpThreshold(Math.max(1, payload.threshold()));
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.jump_threshold_set", payload.threshold()), false);
        });

        // 献祭玩法参数设置：赎回钻石花费
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetReclaimCostPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            // 确保花费至少为1
            state.setReclaimDiamondCost(Math.max(1, payload.cost()));
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.reclaim_cost_set", payload.cost()), false);
        });

        // 纸人玩法参数设置：食物等级、饱和度、生命/护甲概率和数量、伤害倍率、耐力、冰块掉率
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetPaperPersonParamsPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            state.setPaperFoodLevel(payload.foodLevel());
            state.setPaperSaturation(payload.saturation());
            state.setPaperHealthChance(payload.healthChance());
            state.setPaperHealthAmount(payload.healthAmount());
            state.setPaperArmorChance(payload.armorChance());
            state.setPaperArmorAmount(payload.armorAmount());
            state.setDamageMultiplier(payload.damageMultiplier());
            state.setMaxStamina(payload.maxStamina());
            state.setIceDropChance(payload.iceDropChance());
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.paper_params_set"), false);
        });

        // 残疾玩法参数设置：残疾概率和受伤阈值
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetDisabledParamsPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            state.setDisabledChance(payload.chance());
            state.setDisabledHurtThreshold(payload.threshold());
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.disabled_params_set"), false);
        });

        // 肉鸽玩法参数设置：每次生成的生物数量
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetRoguelikeMobCountPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            state.setRoguelikeMobCount(payload.mobCount());
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.roguelike_mob_count_set", payload.mobCount()), false);
        });

        // 加速玩法参数设置：速度倍率
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetSpeedMultiplierPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            state.setSpeedMultiplier(payload.multiplier());
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.speed_multiplier_set", payload.multiplier()), false);
        });

        // 全随机玩法参数设置：随机化间隔时间（分钟）
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetRandomizeIntervalPayload.ID, (payload, context) -> {
            MinecraftServer server = context.player().getServer();
            if (server == null) return;
            GameState state = GameState.getServerState(server);
            state.setRandomizeIntervalMinutes(payload.minutes());
            // 重置随机化计时器
            RANDOM_GAMEPLAY.resetTimer(server);
            server.getPlayerManager().broadcast(
                Text.translatable("msg.sacrificemod.randomize_interval_set", payload.minutes()), false);
        });

        // 弓箭手玩法参数设置：弓/弩/剑附魔等级、击杀换甲数、骷髅TNT概率、流浪者凋零概率
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetArcherParamsPayload.ID, (payload, context) -> {
            var state = GameState.getServerState(context.server());
            state.setArcherBowPower(payload.bowPower());
            state.setArcherBowPunch(payload.bowPunch());
            state.setArcherCrossbowMultishot(payload.crossbowMultishot());
            state.setArcherCrossbowPiercing(payload.crossbowPiercing());
            state.setArcherSwordSharpness(payload.swordSharpness());
            state.setArcherSwordFireAspect(payload.swordFireAspect());
            state.setArcherKillsPerArmor(payload.killsPerArmor());
            state.setArcherSkeletonTntChance(payload.skeletonTntChance());
            state.setArcherStrayWitherChance(payload.strayWitherChance());
            state.setArcherMobLimit(payload.mobLimit());
            context.player().sendMessage(Text.translatable("msg.sacrificemod.archer_params_set"), false);
        });

        // 追击玩法参数设置：和平生物攻击范围
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.SetChaseParamsPayload.ID, (payload, context) -> {
            var state = GameState.getServerState(context.server());
            state.setChaseAttackRange(payload.attackRange());
        });
    }

    /**
     * 处理玩法切换
     *
     * 根据start参数决定开始或停止玩法：
     * - 开始(start=true)：先停止旧玩法，重置状态，再启动新玩法
     * - 停止(start=false)：停止当前玩法，清理所有状态
     *
     * 切换过程中会：
     * 1. 调用旧玩法的onStop进行清理
     * 2. 重置游戏状态
     * 3. 调用新玩法的onStart进行初始化
     * 4. 同步状态到所有客户端
     * 5. 广播切换消息
     *
     * @param server     Minecraft服务器实例
     * @param player     发起切换的玩家
     * @param start      true表示开始玩法，false表示停止玩法
     * @param gameplayId 要切换的玩法ID
     */
    private void handleToggleGame(MinecraftServer server, ServerPlayerEntity player, boolean start, String gameplayId) {
        GameState state = GameState.getServerState(server);
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        if (start) {
            // ===== 开始新玩法 =====
            String wasGameplay = state.getActiveGameplay();

            // 先停止旧玩法（如果存在）
            if (wasGameplay != null) {
                BaseGameplay oldGameplay = GAMEPLAY_REGISTRY.get(wasGameplay);
                if (oldGameplay != null) {
                    try {
                        oldGameplay.onStop(server, state, players);
                    } catch (Exception e) {
                        LOGGER.error("停止玩法时出错: " + wasGameplay, e);
                    }
                }

                // 发送停止状态到所有客户端（清除旧玩法的UI数据）
                for (ServerPlayerEntity p : players) {
                    ServerPlayNetworking.send(p, new ModPackets.GameStatePayload(
                        null, state.getSharedHealth(), state.getSharedMaxHealth(),
                        state.getSharedHunger(), state.getJumpCount(), state.getJumpThreshold(),
                        state.getReclaimDiamondCost(), state.getPaperFoodLevel(), state.getPaperSaturation(),
                        state.getPaperHealthChance(), state.getPaperHealthAmount(),
                        state.getPaperArmorChance(), state.getPaperArmorAmount(),
                        state.getDamageMultiplier(), state.getDisabledChance(), state.getDisabledHurtThreshold(),
                        state.getMaxStamina(), state.getIceDropChance()
                    ));

                    // 清空身体部位和禁用格子同步数据
                    ServerPlayNetworking.send(p, new ModPackets.BodyPartSyncPayload(p.getUuid(), new int[0]));
                    ServerPlayNetworking.send(p, new ModPackets.DisabledSlotsSyncPayload(p.getUuid(), new int[0]));
                }

                // 广播旧玩法停止消息
                String stopMsgKey = switch (wasGameplay) {
                    case "sacrifice" -> "msg.sacrificemod.game_stopped";
                    case "paper_person" -> "msg.sacrificemod.paper_person_stopped";
                    case "disabled" -> "msg.sacrificemod.disabled_stopped";
                    case "roguelike" -> "msg.sacrificemod.roguelike_stopped";
                    case "speed" -> "msg.sacrificemod.speed_stopped";
                    case "random" -> "msg.sacrificemod.random_stopped";
                    case "chase" -> "msg.sacrificemod.chase_stopped";
                    case "teleport" -> "msg.sacrificemod.teleport_stopped";
                    case "archer" -> "msg.sacrificemod.archer_stopped";
                    default -> "msg.sacrificemod.game_stopped";
                };
                server.getPlayerManager().broadcast(Text.translatable(stopMsgKey), false);
            }

            // 重置状态并设置新玩法
            state.resetForNewGame();
            state.setActiveGameplay(gameplayId);
            activeGameplay = gameplayId;

            // 调用新玩法的初始化逻辑
            BaseGameplay newGameplay = GAMEPLAY_REGISTRY.get(gameplayId);
            if (newGameplay != null) {
                newGameplay.onStart(server, state, players);
            }

            // 发送新玩法状态到所有客户端
            for (ServerPlayerEntity p : players) {
                ServerPlayNetworking.send(p, new ModPackets.GameStatePayload(
                    gameplayId, state.getSharedHealth(), state.getSharedMaxHealth(),
                    state.getSharedHunger(), state.getJumpCount(), state.getJumpThreshold(),
                    state.getReclaimDiamondCost(), state.getPaperFoodLevel(), state.getPaperSaturation(),
                    state.getPaperHealthChance(), state.getPaperHealthAmount(),
                    state.getPaperArmorChance(), state.getPaperArmorAmount(),
                    state.getDamageMultiplier(), state.getDisabledChance(), state.getDisabledHurtThreshold(),
                    state.getMaxStamina(), state.getIceDropChance()
                ));
            }

            // 广播新玩法开始消息
            String startMsgKey = switch (gameplayId) {
                case "sacrifice" -> "msg.sacrificemod.game_started";
                case "paper_person" -> "msg.sacrificemod.paper_person_started";
                case "disabled" -> "msg.sacrificemod.disabled_started";
                case "roguelike" -> "msg.sacrificemod.roguelike_started";
                case "speed" -> "msg.sacrificemod.speed_started";
                case "random" -> "msg.sacrificemod.random_started";
                case "chase" -> "msg.sacrificemod.chase_started";
                case "teleport" -> "msg.sacrificemod.teleport_started";
                case "archer" -> "msg.sacrificemod.archer_started";
                default -> "msg.sacrificemod.game_started";
            };
            server.getPlayerManager().broadcast(Text.translatable(startMsgKey), false);
        } else {
            // ===== 停止玩法 =====
            // 如果请求停止的玩法不是当前活跃的玩法，则忽略
            if (!gameplayId.equals(state.getActiveGameplay())) return;

            BaseGameplay gameplay = GAMEPLAY_REGISTRY.get(gameplayId);
            if (gameplay != null) {
                try {
                    gameplay.onStop(server, state, players);
                } catch (Exception e) {
                    LOGGER.error("停止玩法时出错: " + gameplayId, e);
                }
            }

            // 清理所有游戏状态
            state.resetForNewGame();
            state.clearSyncData();
            activeGameplay = null;

            // 发送停止状态到所有客户端（清空所有UI数据）
            for (ServerPlayerEntity p : players) {
                ServerPlayNetworking.send(p, new ModPackets.GameStatePayload(
                    null, state.getSharedHealth(), state.getSharedMaxHealth(),
                    state.getSharedHunger(), state.getJumpCount(), state.getJumpThreshold(),
                    state.getReclaimDiamondCost(), state.getPaperFoodLevel(), state.getPaperSaturation(),
                    state.getPaperHealthChance(), state.getPaperHealthAmount(),
                    state.getPaperArmorChance(), state.getPaperArmorAmount(),
                    state.getDamageMultiplier(), state.getDisabledChance(), state.getDisabledHurtThreshold(),
                    state.getMaxStamina(), state.getIceDropChance()
                ));
                // 清空身体部位和禁用格子数据
                ServerPlayNetworking.send(p, new ModPackets.BodyPartSyncPayload(p.getUuid(), new int[0]));
                ServerPlayNetworking.send(p, new ModPackets.DisabledSlotsSyncPayload(p.getUuid(), new int[0]));
            }

            // 移除所有玩家的状态效果（如加速玩法添加的速度效果等）
            for (ServerPlayerEntity p : players) {
                p.clearStatusEffects();
            }

            // 广播玩法停止消息
            String stopMsgKey = switch (gameplayId) {
                case "sacrifice" -> "msg.sacrificemod.game_stopped";
                case "paper_person" -> "msg.sacrificemod.paper_person_stopped";
                case "disabled" -> "msg.sacrificemod.disabled_stopped";
                case "roguelike" -> "msg.sacrificemod.roguelike_stopped";
                case "speed" -> "msg.sacrificemod.speed_stopped";
                case "random" -> "msg.sacrificemod.random_stopped";
                case "chase" -> "msg.sacrificemod.chase_stopped";
                case "teleport" -> "msg.sacrificemod.teleport_stopped";
                case "archer" -> "msg.sacrificemod.archer_stopped";
                default -> "msg.sacrificemod.game_stopped";
            };
            server.getPlayerManager().broadcast(Text.translatable(stopMsgKey), false);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 检查是否正在同步（防止循环同步）
     *
     * @return true表示正在同步中，不应处理伤害事件
     */
    public static boolean isSyncing() {
        return syncing;
    }

    /**
     * 设置同步状态
     *
     * @param value true表示开始同步，false表示同步结束
     */
    public static void setSyncing(boolean value) {
        syncing = value;
    }

    /**
     * 处理玩家跳跃（由Mixin调用）
     *
     * 当玩家跳跃时由PlayerEntityMixin调用，
     * 仅在献祭玩法活跃时处理，用于累计跳跃计数。
     *
     * @param player 跳跃的玩家
     */
    public static void onPlayerJump(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GameState state = GameState.getServerState(server);
        // 仅献祭玩法处理跳跃
        if (!state.isGameplayActive("sacrifice")) return;

        SACRIFICE_GAMEPLAY.handleJump(server, state, player);
    }

    /**
     * 检查是否是纸人玩法
     *
     * 由Mixin调用，用于判断是否应应用纸人玩法的特殊逻辑。
     *
     * @param player 要检查的玩家
     * @return true表示纸人玩法当前活跃
     */
    public static boolean isPaperPersonActive(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        GameState state = GameState.getServerState(server);
        return state.isGameplayActive("paper_person");
    }

    /**
     * 获取当前活跃的玩法ID（供Mixin使用）
     *
     * Mixin类通过此方法获取当前活跃的玩法ID，
     * 用于在无法直接访问GameState的场景下判断玩法状态。
     *
     * @return 当前活跃的玩法ID，无活跃玩法时返回null
     */
    public static String getActiveGameplayId() {
        return activeGameplay;
    }

    /**
     * 处理生物死亡事件（由LivingEntityMixin调用）
     *
     * 当生物死亡时由Mixin调用，仅在弓箭手大作战玩法活跃时处理，
     * 用于统计玩家击杀数并给予奖励。
     *
     * @param entity  死亡的生物实体
     * @param source  伤害来源（用于判断是否被玩家击杀）
     */
    public static void onEntityDeath(net.minecraft.entity.LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
        // 仅弓箭手玩法处理
        if (activeGameplay == null || !activeGameplay.equals("archer")) return;
        // 仅处理被玩家击杀的情况
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;

        var server = player.getServer();
        if (server == null) return;
        var state = GameState.getServerState(server);
        ARCHER_GAMEPLAY.onPlayerKill(server, state, player, entity);
    }
}
