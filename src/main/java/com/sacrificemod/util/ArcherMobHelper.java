package com.sacrificemod.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 弓箭玩法怪物装备与属性工具类
 * <p>
 * 提取了弓箭玩法中重复的怪物装备配置、附魔、药水效果及属性修改逻辑，
 * 统一管理溺尸、骷髅、流浪者、女巫等怪物的装备与属性设置。
 */
public class ArcherMobHelper {

    /**
     * 装备溺尸：三叉戟（引雷3+穿刺5）、海龟壳（保护4）、永久抗火效果、弓箭怪物属性
     *
     * @param drowned 溺尸实体
     * @param world   服务端世界
     */
    public static void equipDrowned(DrownedEntity drowned, ServerWorld world) {
        var server = world.getServer();

        // 主手装备：附魔三叉戟（引雷3 + 穿刺5）
        drowned.equipStack(EquipmentSlot.MAINHAND, createTrident(server));

        // 头部装备：附魔海龟壳（保护4）
        drowned.equipStack(EquipmentSlot.HEAD, createTurtleShell(server));

        // 永久抗火效果
        drowned.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // 应用弓箭怪物属性（攻击速度 + 跟随范围）
        applyArcherMobAttributes(drowned);
    }

    /**
     * 装备骷髅：钻石头盔、力量5弓、永久力量5效果、弓箭怪物属性
     *
     * @param skeleton 骷髅实体
     * @param world    服务端世界
     */
    public static void equipSkeleton(SkeletonEntity skeleton, ServerWorld world) {
        var server = world.getServer();

        // 头部装备：钻石头盔
        skeleton.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

        // 主手装备：附魔弓（力量5）
        skeleton.equipStack(EquipmentSlot.MAINHAND, createPower5Bow(server));

        // 永久力量5效果（放大器4 = 等级5）
        skeleton.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 4, false, false));

        // 应用弓箭怪物属性
        applyArcherMobAttributes(skeleton);
    }

    /**
     * 装备流浪者：钻石头盔、力量5弓、永久力量5效果、弓箭怪物属性
     *
     * @param stray 流浪者实体
     * @param world 服务端世界
     */
    public static void equipStray(StrayEntity stray, ServerWorld world) {
        var server = world.getServer();

        // 头部装备：钻石头盔
        stray.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

        // 主手装备：附魔弓（力量5）
        stray.equipStack(EquipmentSlot.MAINHAND, createPower5Bow(server));

        // 永久力量5效果（放大器4 = 等级5）
        stray.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 4, false, false));

        // 应用弓箭怪物属性
        applyArcherMobAttributes(stray);
    }

    /**
     * 为女巫应用永久抗性提升3效果（放大器2 = 等级3）
     *
     * @param witch 女巫实体
     */
    public static void applyWitchEffects(WitchEntity witch) {
        witch.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 2, false, false));
    }

    /**
     * 应用弓箭怪物通用属性：
     * - 攻击速度 +2.0（持久修饰符，ID: sacrificemod:archer_attack_speed）
     * - 跟随范围 +48.0（持久修饰符，ID: sacrificemod:archer_follow_range）
     *
     * @param mob 目标生物实体
     */
    public static void applyArcherMobAttributes(LivingEntity mob) {
        // 攻击速度 +2.0（先移除再添加，防止重复应用导致崩溃）
        var attackSpeedAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.removeModifier(Identifier.of("sacrificemod", "archer_attack_speed"));
            attackSpeedAttr.addPersistentModifier(new EntityAttributeModifier(
                    Identifier.of("sacrificemod", "archer_attack_speed"), 2.0, EntityAttributeModifier.Operation.ADD_VALUE));
        }

        // 跟随范围 +48.0（先移除再添加，防止重复应用导致崩溃）
        var followRangeAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followRangeAttr != null) {
            followRangeAttr.removeModifier(Identifier.of("sacrificemod", "archer_follow_range"));
            followRangeAttr.addPersistentModifier(new EntityAttributeModifier(
                    Identifier.of("sacrificemod", "archer_follow_range"), 48.0, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * 创建力量5附魔弓
     *
     * @param server Minecraft服务器实例
     * @return 附魔后的弓物品栈
     */
    public static ItemStack createPower5Bow(MinecraftServer server) {
        var itemStack = new ItemStack(Items.BOW);
        var enchantmentRegistry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);

        var builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        var enchantment = enchantmentRegistry.getEntry(Enchantments.POWER).orElse(null);
        if (enchantment != null) builder.add(enchantment, 5);
        itemStack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        return itemStack;
    }

    /**
     * 创建引雷3+穿刺5附魔三叉戟
     *
     * @param server Minecraft服务器实例
     * @return 附魔后的三叉戟物品栈
     */
    public static ItemStack createTrident(MinecraftServer server) {
        var itemStack = new ItemStack(Items.TRIDENT);
        var enchantmentRegistry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);

        var builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        var channeling = enchantmentRegistry.getEntry(Enchantments.CHANNELING).orElse(null);
        if (channeling != null) builder.add(channeling, 3);
        var impaling = enchantmentRegistry.getEntry(Enchantments.IMPALING).orElse(null);
        if (impaling != null) builder.add(impaling, 5);
        itemStack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        return itemStack;
    }

    /**
     * 创建保护4附魔海龟壳（龟壳头盔）
     *
     * @param server Minecraft服务器实例
     * @return 附魔后的海龟壳物品栈
     */
    public static ItemStack createTurtleShell(MinecraftServer server) {
        var itemStack = new ItemStack(Items.TURTLE_HELMET);
        var enchantmentRegistry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);

        var builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        var enchantment = enchantmentRegistry.getEntry(Enchantments.PROTECTION).orElse(null);
        if (enchantment != null) builder.add(enchantment, 4);
        itemStack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        return itemStack;
    }

    /**
     * 查找指定XZ坐标的地表Y坐标
     * <p>从世界顶部向下搜索，找到第一个实心方块（跳过空气和树叶），
     * 返回其Y坐标。用于确保生物生成在地表而非地下矿洞中。</p>
     *
     * @param world 世界实例
     * @param x     方块X坐标
     * @param z     方块Z坐标
     * @return 地表方块的Y坐标，如果找不到则返回世界底部Y坐标
     */
    public static int findSurfaceY(ServerWorld world, int x, int z) {
        // 从世界顶部向下搜索地表
        for (int y = world.getTopY() - 1; y >= world.getBottomY(); y--) {
            var blockPos = new BlockPos(x, y, z);
            var blockState = world.getBlockState(blockPos);
            // 找到第一个实心方块即为地表（树叶、花草等非实心方块会被跳过）
            if (!blockState.isAir() && blockState.isSolid()) {
                return y;
            }
        }
        return world.getBottomY();
    }
}
