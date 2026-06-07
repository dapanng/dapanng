package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import com.sacrificemod.ModPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.*;

/**
 * 纸人玩法实现 - 脆弱生存挑战
 * <p>
 * 核心机制：
 * 1. 玩家初始最大生命值为1 - 极度脆弱，一击即死
 * 2. 可食用纸、雪块、冰块等特殊物品恢复状态 - 纸可恢复饥饿度并有概率增加生命/护甲
 * 3. 接触水/岩浆立即死亡 - 纸人怕水的核心设定
 * 4. 只能穿皮革装备 - 其他装备会被强制卸下
 * 5. 耐力值机制 - 靠近热源消耗耐力，冻结时不消耗，耐力为零时燃烧
 * 6. 受到伤害按倍率放大 - 通过damageMultiplier增加额外伤害
 * 7. 永久缓降效果 - 纸人轻飘飘的设定
 * 8. 冰块使用后可冻结周围水面 - 提供安全的移动路径
 * </p>
 */
public class PaperPersonGameplay extends BaseGameplay {
    
    /** 纸人最大生命值属性修饰符ID，用于将生命值从20修改为1 */
    private static final Identifier PAPER_MAX_HEALTH_ID = Identifier.of("sacrificemod", "paper_max_health");
    /** 纸人护甲值属性修饰符ID，用于增加额外护甲 */
    private static final Identifier PAPER_ARMOR_ID = Identifier.of("sacrificemod", "paper_armor");
    /** 随机数生成器，用于纸的随机属性增加判定 */
    private static final Random RANDOM = Random.create();
    
    /**
     * 构造方法 - 初始化纸人玩法的ID和显示名称
     */
    public PaperPersonGameplay() {
        super("paper_person", "纸人玩法");
    }
    
    /**
     * 玩法开始时的初始化
     * <p>清除所有玩家的状态效果，将最大生命值设为1，初始化耐力值为最大值</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 清除所有耐力和冻结计时数据
        state.clearStaminaAndFrozenTicks();
        
        for (ServerPlayerEntity player : players) {
            // 清除所有状态效果
            player.clearStatusEffects();
            // 设置最大生命值为1（基础值20，修饰符-19）
            applyHealthModifier(player, -19);
            player.setHealth(1);
            
            // 初始化耐力值为最大耐力值
            state.setStamina(player.getUuid(), state.getMaxStamina());
        }
    }
    
    /**
     * 玩法结束时的清理
     * <p>移除所有玩家的生命值和护甲修饰符，清除所有状态效果</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            // 移除生命值和护甲修饰符，恢复原始属性
            removeModifiers(player);
            // 移除所有状态效果（缓降、冻结等）
            player.clearStatusEffects();
        }
    }
    
    /**
     * 每tick执行的处理逻辑
     * <p>每秒处理一次核心逻辑：
     * 1. 确保最大生命值正确
     * 2. 检测水中接触→立即死亡
     * 3. 维持缓降效果
     * 4. 处理冻结效果和水面冻结
     * 5. 强制穿皮革装备
     * 6. 移除熔岩桶
     * 7. 更新耐力值
     * 每10tick同步耐力值到客户端</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        // 每秒处理一次核心逻辑（20tick = 1秒）
        if (server.getTicks() % 20 != 0) return;
        
        for (ServerPlayerEntity player : players) {
            if (player.isDead()) continue;
            
            // 确保最大生命值属性正确（基础-19 + 额外修饰符）
            double expectedHealthMod = state.getHealthModifier(player.getUuid());
            double baseHealthMod = -19; // 基础修饰符：将最大生命值从20降到1
            double totalExpectedMod = baseHealthMod + expectedHealthMod;
            var healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (healthAttr != null && Math.abs(healthAttr.getValue() - (20 + totalExpectedMod)) > 0.01) {
                applyHealthModifier(player, totalExpectedMod);
            }
            
            // 检查是否在水中 - 纸人怕水，接触水立即死亡
            if (player.isTouchingWater()) {
                player.kill();
                continue;
            }
            
            // 维持永久缓降效果（纸人轻飘飘的设定）
            if (!player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false, true));
            }
            
            // 冻结效果处理：减少冻结计时器，冻结期间冻结周围水面
            int frozenTicks = state.getFrozenTicks(player.getUuid());
            if (frozenTicks > 0) {
                state.setFrozenTicks(player.getUuid(), frozenTicks - 1);
                freezeWaterAround(player);
            }
            
            // 强制穿皮革装备，非皮革装备会被踢出
            enforceLeatherArmor(player);
            
            // 移除背包中的熔岩桶（纸人不能用火）
            removeLavaBuckets(player);
            
            // ===== 耐力值逻辑 =====
            float stamina = state.getStamina(player.getUuid());
            boolean isNearHeat = isNearHeatSource(player);
            boolean holdingTorch = player.getMainHandStack().getItem() == Items.TORCH
                                || player.getOffHandStack().getItem() == Items.TORCH;
            boolean inNether = player.getWorld().getRegistryKey() == World.NETHER;
            
            // 靠近热源/手持火把/在地狱时消耗耐力，冻结期间不消耗
            boolean shouldDrain = (isNearHeat || holdingTorch || inNether) && frozenTicks <= 0;
            
            if (shouldDrain) {
                // 靠近热源时每秒消耗1点耐力
                stamina = Math.max(0, stamina - 1);
            } else if (frozenTicks <= 0) {
                // 正常环境下每秒恢复10点耐力
                stamina = Math.min(state.getMaxStamina(), stamina + 10);
            }
            // 冻结期间耐力不变
            
            state.setStamina(player.getUuid(), stamina);
            
            // 耐力值为零时点燃玩家（纸人怕火）
            if (stamina <= 0) {
                player.setFireTicks(100);
            }
        }
        
        // 每10tick同步耐力值和冻结状态到客户端
        if (server.getTicks() % 10 == 0) {
            for (ServerPlayerEntity player : players) {
                ServerPlayNetworking.send(player, new ModPackets.StaminaSyncPayload(
                    player.getUuid(),
                    state.getStamina(player.getUuid()),
                    state.getMaxStamina(),
                    state.getFrozenTicks(player.getUuid())
                ));
            }
        }
    }
    
    /**
     * 玩家复活时的处理
     * <p>重新应用生命值修饰符（基础-19 + 额外增加的部分）和护甲修饰符</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        // 重新应用生命值修饰符（基础-19 + 额外）
        double healthMod = state.getHealthModifier(player.getUuid());
        double totalHealthMod = -19 + healthMod;
        var healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(PAPER_MAX_HEALTH_ID);
            healthAttr.addPersistentModifier(new EntityAttributeModifier(
                PAPER_MAX_HEALTH_ID, totalHealthMod, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        
        // 重新应用护甲修饰符
        double armorMod = state.getArmorModifier(player.getUuid());
        if (armorMod != 0) {
            var armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
            if (armorAttr != null) {
                armorAttr.removeModifier(PAPER_ARMOR_ID);
                armorAttr.addPersistentModifier(new EntityAttributeModifier(
                    PAPER_ARMOR_ID, armorMod, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
    }
    
    /**
     * 玩家受伤害时的处理
     * <p>特殊伤害处理：
     * - 岩浆/火焰/热地板 → 立即死亡（纸人怕火）
     * - 其他伤害 → 应用伤害倍率，额外伤害部分手动扣除</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 受伤的玩家实例
     * @param source 伤害来源
     * @param amount 原始伤害量
     * @return true=允许原始伤害通过，false=阻止原始伤害
     */
    @Override
    public boolean onDamage(MinecraftServer server, GameState state, ServerPlayerEntity player,
                          DamageSource source, float amount) {
        // 岩浆/火焰/热地板/在岩浆中的燃烧 → 立即死亡
        if (source == player.getDamageSources().lava() ||
            source == player.getDamageSources().inFire() ||
            source == player.getDamageSources().hotFloor() ||
            (source == player.getDamageSources().onFire() && player.isInLava())) {
            player.kill();
            return false;
        }
        
        // 应用伤害倍率：额外伤害 = 原始伤害 × (倍率 - 1)
        float multipliedDamage = amount * state.getDamageMultiplier();
        float extraDamage = multipliedDamage - amount;
        if (extraDamage > 0) {
            // 手动扣除额外伤害部分（原始伤害由原版逻辑处理）
            player.setHealth(Math.max(0, player.getHealth() - extraDamage));
            if (player.getHealth() <= 0) {
                player.onDeath(source);
            }
        }
        
        return true; // 允许原始伤害继续处理
    }
    
    /**
     * 处理玩家使用物品的逻辑
     * <p>纸人玩法下可使用的特殊物品：
     * - 纸：恢复饥饿度，有概率增加最大生命值或护甲值
     * - 雪块：恢复20点耐力
     * - 冰块：冻结15秒（300tick）
     * - 蓝冰：冻结30秒（600tick）
     * - 浮冰：恢复25点耐力
     * 普通食物和熔岩桶被禁止使用</p>
     * @param player 使用物品的玩家
     * @param state 游戏全局状态
     * @param stack 使用的物品堆
     * @param hand 使用物品的手
     * @return TypedActionResult：success=消耗物品，fail=阻止使用，pass=不处理
     */
    public TypedActionResult<ItemStack> onUseItem(ServerPlayerEntity player, GameState state, ItemStack stack, Hand hand) {
        if (stack.getItem() == Items.PAPER) {
            // 纸 - 恢复饥饿度和饱和度
            player.getHungerManager().add(state.getPaperFoodLevel(), state.getPaperSaturation());
            
            // 概率增加最大生命值
            if (RANDOM.nextInt(100) < state.getPaperHealthChance()) {
                double newValue = state.getHealthModifier(player.getUuid()) + state.getPaperHealthAmount();
                state.addHealthModifier(player.getUuid(), state.getPaperHealthAmount());
                applyHealthModifier(player, newValue);
            }
            
            // 概率增加护甲值
            if (RANDOM.nextInt(100) < state.getPaperArmorChance()) {
                double newValue = state.getArmorModifier(player.getUuid()) + state.getPaperArmorAmount();
                state.addArmorModifier(player.getUuid(), state.getPaperArmorAmount());
                applyArmorModifier(player, newValue);
            }
            
            stack.decrementUnlessCreative(1, player);
            player.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
            return TypedActionResult.success(stack);
        }
        
        if (stack.getItem() == Items.SNOW_BLOCK) {
            // 雪块 - 恢复20点耐力
            float currentStamina = state.getStamina(player.getUuid());
            state.setStamina(player.getUuid(), Math.min(state.getMaxStamina(), currentStamina + 20));
            stack.decrementUnlessCreative(1, player);
            player.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
            return TypedActionResult.success(stack);
        }
        
        if (stack.getItem() == Items.ICE) {
            // 冰块 - 冻结15秒（300tick），冻结期间不消耗耐力且冻结周围水面
            state.setFrozenTicks(player.getUuid(), 300);
            stack.decrementUnlessCreative(1, player);
            player.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
            return TypedActionResult.success(stack);
        }
        
        if (stack.getItem() == Items.BLUE_ICE) {
            // 蓝冰 - 冻结30秒（600tick），比普通冰块持续时间更长
            state.setFrozenTicks(player.getUuid(), 600);
            stack.decrementUnlessCreative(1, player);
            player.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
            return TypedActionResult.success(stack);
        }
        
        if (stack.getItem() == Items.PACKED_ICE) {
            // 浮冰 - 恢复25点耐力
            float currentStamina = state.getStamina(player.getUuid());
            state.setStamina(player.getUuid(), Math.min(state.getMaxStamina(), currentStamina + 25));
            stack.decrementUnlessCreative(1, player);
            player.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
            return TypedActionResult.success(stack);
        }
        
        // 阻止普通食物（纸人不能吃正常食物）
        if (stack.contains(DataComponentTypes.FOOD)) {
            return TypedActionResult.fail(stack);
        }
        
        // 阻止熔岩桶（纸人怕火）
        if (stack.getItem() == Items.LAVA_BUCKET) {
            return TypedActionResult.fail(stack);
        }
        
        // 其他物品不处理，交给原版逻辑
        return TypedActionResult.pass(stack);
    }
    
    /**
     * 处理方块挖掘事件 - 冰块有概率直接掉落
     * <p>使用镐挖掘冰块时，有概率直接掉落冰块物品而不是融化成水</p>
     * @param world 世界实例
     * @param player 挖掘方块的玩家
     * @param pos 方块位置
     * @param state 方块状态
     * @param gameState 游戏全局状态
     * @return true=允许原版破坏逻辑，false=阻止原版破坏（已自定义处理）
     */
    public boolean onBlockBreak(World world, ServerPlayerEntity player, BlockPos pos, 
                                net.minecraft.block.BlockState state, GameState gameState) {
        if (state.isOf(Blocks.ICE)) {
            ItemStack tool = player.getMainHandStack();
            // 只有使用镐挖掘冰块才有概率掉落
            if (tool.getItem() instanceof PickaxeItem) {
                if (RANDOM.nextInt(100) < gameState.getIceDropChance()) {
                    // 掉落冰块物品
                    Block.dropStack(world, pos, new ItemStack(Blocks.ICE));
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    return false; // 阻止原版破坏逻辑
                }
            }
        }
        return true; // 允许原版破坏逻辑
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 应用生命值修饰符到玩家
     * <p>先移除旧修饰符，再添加新修饰符。值为负数时减少最大生命值</p>
     * @param player 目标玩家
     * @param value 修饰值（-19=将20点生命降到1点）
     */
    private void applyHealthModifier(ServerPlayerEntity player, double value) {
        var healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(PAPER_MAX_HEALTH_ID);
            healthAttr.addPersistentModifier(new EntityAttributeModifier(
                PAPER_MAX_HEALTH_ID, value, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
    
    /**
     * 应用护甲值修饰符到玩家
     * <p>通过吃纸有概率增加的护甲值</p>
     * @param player 目标玩家
     * @param value 护甲修饰值
     */
    private void applyArmorModifier(ServerPlayerEntity player, double value) {
        var armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(PAPER_ARMOR_ID);
            armorAttr.addPersistentModifier(new EntityAttributeModifier(
                PAPER_ARMOR_ID, value, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
    
    /**
     * 移除玩家的所有纸人修饰符（生命值和护甲）
     * @param player 目标玩家
     */
    private void removeModifiers(ServerPlayerEntity player) {
        var healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) healthAttr.removeModifier(PAPER_MAX_HEALTH_ID);
        
        var armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr != null) armorAttr.removeModifier(PAPER_ARMOR_ID);
    }
    
    /**
     * 检查玩家是否靠近热源
     * <p>在玩家周围5×5×5的范围内检测岩浆、岩浆块、火焰、灵魂火</p>
     * @param player 目标玩家
     * @return true=靠近热源，false=不靠近
     */
    private boolean isNearHeatSource(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        World world = player.getWorld();
        // 扫描玩家周围±2格范围
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos target = pos.add(dx, dy, dz);
                    Block block = world.getBlockState(target).getBlock();
                    // 检测热源方块：岩浆、岩浆块、火焰、灵魂火
                    if (block == Blocks.LAVA || block == Blocks.MAGMA_BLOCK || 
                        block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 冻结玩家周围的水面
     * <p>将玩家脚下5×5范围内的静止水面变为冰块，为纸人提供安全的移动路径</p>
     * @param player 目标玩家
     */
    private void freezeWaterAround(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        World world = player.getWorld();
        // 冻结玩家脚下±2格范围内的静止水面
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos target = pos.add(dx, -1, dz);
                // 只冻结静止的水（流动的水不冻结）
                if (world.getBlockState(target).isOf(Blocks.WATER) && 
                    world.getBlockState(target).getFluidState().isStill()) {
                    world.setBlockState(target, Blocks.ICE.getDefaultState());
                }
            }
        }
    }
    
    /**
     * 强制玩家只能穿皮革装备
     * <p>检测所有装备栏，非皮革装备会被掉落在地上</p>
     * @param player 目标玩家
     */
    private void enforceLeatherArmor(ServerPlayerEntity player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armorStack = player.getEquippedStack(slot);
                // 非空且非皮革装备 → 掉落并清空
                if (!armorStack.isEmpty() && !isLeatherArmor(armorStack)) {
                    player.dropItem(armorStack.copy(), true);
                    player.equipStack(slot, ItemStack.EMPTY);
                }
            }
        }
    }
    
    /**
     * 移除玩家背包中的熔岩桶
     * <p>纸人不能持有熔岩桶，发现后直接掉落在地上</p>
     * @param player 目标玩家
     */
    private void removeLavaBuckets(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack invStack = player.getInventory().getStack(i);
            if (invStack.getItem() == Items.LAVA_BUCKET) {
                player.dropItem(invStack.copy(), true);
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }
    
    /**
     * 检查物品是否是皮革装备
     * @param stack 物品堆
     * @return true=是皮革装备，false=不是
     */
    private boolean isLeatherArmor(ItemStack stack) {
        return stack.getItem() == Items.LEATHER_HELMET ||
               stack.getItem() == Items.LEATHER_CHESTPLATE ||
               stack.getItem() == Items.LEATHER_LEGGINGS ||
               stack.getItem() == Items.LEATHER_BOOTS;
    }
}
