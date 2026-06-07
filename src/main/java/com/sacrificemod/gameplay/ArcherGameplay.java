package com.sacrificemod.gameplay;

import com.sacrificemod.GameState;
import com.sacrificemod.util.ArcherMobHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * 弓箭手大作战玩法实现 - 远程战斗挑战
 * <p>
 * 核心机制：
 * 1. 所有玩家获得特殊武器（霸王弓、散弹弩、火焰剑、工具斧）- 武器不可丢弃（箭除外）
 * 2. 世界生物被替换为远程敌对生物 - 骷髅、流浪者、女巫、溺尸、守卫者、烈焰人、恶魂
 * 3. 击杀积累护甲值 - 每击杀一定数量生物获得+1护甲
 * 4. 末影龙增强 - 永久抗性提升3和力量效果
 * 5. 末影水晶重生 - 被打爆的末影水晶5分钟后重生
 * 6. 删除剑/斧/盔甲合成配方 - 限制近战和防御装备获取
 * 7. 修改末影之眼配方 - 末影珍珠→恶魂之泪
 * 8. 主动生物生成 - 定期在玩家周围生成远程敌对生物
 * </p>
 */
public class ArcherGameplay extends BaseGameplay {

    /** 武器标识NBT标签名，用于识别玩法特殊武器 */
    private static final String WEAPON_TAG = "sacrificemod_archer_weapon";

    /** 武器补充检查间隔（tick），每20tick（1秒）检查一次 */
    private static final int WEAPON_CHECK_INTERVAL = 20;
    /** 末影水晶重生时间（tick），6000tick = 5分钟 */
    private static final int CRYSTAL_RESPAWN_TICKS = 6000;
    /** 末影龙效果刷新间隔（tick），每100tick（5秒）刷新一次 */
    private static final int DRAGON_EFFECT_INTERVAL = 100;
    /** 护甲属性修饰符ID，用于击杀积累的额外护甲 */
    private static final Identifier ARCHER_ARMOR_ID = Identifier.of("sacrificemod", "archer_bonus_armor");
    /** 主动生物生成间隔（tick），60tick = 3秒 */
    private static final int SPAWN_INTERVAL = 60;
    /** 地狱最大生物数量上限 */
    private static final int MAX_NETHER_MOBS = 40;
    /** 玩家周围生物生成半径（格） */
    private static final double SPAWN_RADIUS = 40.0;
    /** 守卫者最大数量上限 */
    private static final int MAX_GUARDIANS = 30;

    /** 被移除的配方列表，用于停止时恢复 */
    private List<net.minecraft.recipe.RecipeEntry<?>> removedRecipes = new ArrayList<>();
    /** 原始末影之眼配方，用于恢复 */
    private net.minecraft.recipe.RecipeEntry<?> originalEnderEyeRecipe = null;

    /** 被打爆的末影水晶位置和重生时间（BlockPos → 重生tick） */
    private final Map<BlockPos, Long> destroyedCrystals = new HashMap<>();
    /** 上次武器检查的服务器tick */
    private long lastWeaponCheckTick = 0;

    /**
     * 构造方法 - 初始化弓箭手大作战玩法的ID和显示名称
     */
    public ArcherGameplay() {
        super("archer", "弓箭手大作战");
    }

    /**
     * 玩法开始时的初始化
     * <p>发放初始武器，修改配方，替换已存在的生物，增强末影龙</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStart(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        destroyedCrystals.clear();
        lastWeaponCheckTick = server.getTicks();

        // 给所有玩家发放初始武器
        for (ServerPlayerEntity player : players) {
            giveWeapons(player, state);
        }

        // 修改配方：移除剑/斧/盔甲配方，修改末影之眼配方
        modifyRecipes(server);

        // 替换已存在的非目标生物为远程敌对生物
        replaceExistingMobs(server, state);

        // 给末影龙添加抗性提升和力量效果
        buffEnderDragon(server);
    }

    /**
     * 玩法结束时的清理
     * <p>移除玩家武器和护甲修饰符，恢复配方，清除末影龙增益，清空水晶记录</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onStop(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        try {
            for (ServerPlayerEntity player : players) {
                removeWeapons(player);
                // 移除击杀积累的护甲修饰符
                var armorAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
                if (armorAttr != null) armorAttr.removeModifier(ARCHER_ARMOR_ID);
            }
        } catch (Exception e) {
            // 忽略清理异常，确保后续清理继续执行
        }
        
        try {
            // 恢复被移除的配方
            restoreRecipes(server);
        } catch (Exception e) {
            // 忽略恢复异常
        }
        
        try {
            // 清除末影龙的增益效果
            var endWorld = server.getWorld(World.END);
            if (endWorld != null) clearDragonBuff(endWorld);
        } catch (Exception e) {
            // 忽略清除异常
        }
        
        destroyedCrystals.clear();
        state.clearArcherKillData();
    }

    /**
     * 每tick执行的处理逻辑
     * <p>定期执行：武器补充、禁止物品移除、掉落武器清理、
     * 末影水晶重生、末影龙效果刷新、生物替换、主动生物生成</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param players 当前所有在线玩家列表
     */
    @Override
    public void onTick(MinecraftServer server, GameState state, List<ServerPlayerEntity> players) {
        long currentTick = server.getTicks();

        // 每20tick检查武器补充和移除禁止物品
        if (currentTick - lastWeaponCheckTick >= WEAPON_CHECK_INTERVAL) {
            lastWeaponCheckTick = currentTick;
            for (ServerPlayerEntity player : players) {
                ensureWeapons(player, state);      // 补充缺失的武器
                removeForbiddenItems(player);       // 移除禁止的物品（非武器剑/斧/盔甲）
            }
            // 清除掉落的非箭初始武器（防止武器泛滥）
            removeDroppedWeapons(server);
        }

        // 末影水晶重生检查
        respawnCrystals(server, currentTick);

        // 末影龙效果刷新（每100tick）
        if (currentTick % DRAGON_EFFECT_INTERVAL == 0) {
            buffEnderDragon(server);
        }

        // 替换新生成的非目标生物（每20tick检查一次）
        if (currentTick % 20 == 0) {
            replaceExistingMobs(server, state);
        }

        // 主动在玩家周围生成远程敌对生物（每60tick = 3秒）
        if (currentTick % SPAWN_INTERVAL == 0) {
            spawnMobsAroundPlayers(server, state);
        }
    }

    /**
     * 玩家复活时的处理
     * <p>重新补充武器，重新应用击杀积累的护甲修饰符</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 复活的玩家实例
     */
    @Override
    public void onRespawn(MinecraftServer server, GameState state, ServerPlayerEntity player) {
        // 重新补充武器
        ensureWeapons(player, state);
        // 重新应用击杀积累的护甲修饰符
        int bonusArmor = state.getArcherBonusArmor(player.getUuid());
        if (bonusArmor > 0) {
            var attr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
            if (attr != null) {
                attr.removeModifier(ARCHER_ARMOR_ID);
                attr.addPersistentModifier(new EntityAttributeModifier(
                    ARCHER_ARMOR_ID, bonusArmor, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /**
     * 玩家受伤害时的处理
     * <p>弓箭手大作战不修改伤害逻辑</p>
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

    // ==================== 配方管理 ====================

    /**
     * 修改合成配方
     * <p>移除所有剑/斧/盔甲的合成配方，修改末影之眼配方（末影珍珠→恶魂之泪+烈焰粉）</p>
     * @param server Minecraft服务器实例
     */
    private void modifyRecipes(MinecraftServer server) {
        var recipeManager = server.getRecipeManager();
        var allRecipes = new ArrayList<>(recipeManager.values());

        // 查找并收集需要移除的配方
        List<net.minecraft.recipe.RecipeEntry<?>> toRemove = new ArrayList<>();
        for (var recipeEntry : allRecipes) {
            var output = recipeEntry.value().getResult(server.getRegistryManager());
            var item = output.getItem();
            // 移除剑、斧、盔甲配方
            if (item instanceof net.minecraft.item.SwordItem
                || item instanceof net.minecraft.item.AxeItem
                || item instanceof net.minecraft.item.ArmorItem) {
                toRemove.add(recipeEntry);
            }
            // 记录原始末影之眼配方
            if (item == Items.ENDER_EYE && originalEnderEyeRecipe == null) {
                originalEnderEyeRecipe = recipeEntry;
                toRemove.add(recipeEntry);
            }
        }

        // 保存被移除的配方，用于恢复
        removedRecipes = toRemove;

        // 构建剩余配方列表
        var remainingRecipes = new ArrayList<>(allRecipes.stream()
            .filter(r -> !toRemove.contains(r))
            .toList());

        // 添加修改后的末影之眼配方：恶魂之泪 + 烈焰粉 → 末影之眼
        if (originalEnderEyeRecipe != null) {
            var ingredients = net.minecraft.util.collection.DefaultedList.<net.minecraft.recipe.Ingredient>of();
            ingredients.add(net.minecraft.recipe.Ingredient.ofItems(Items.GHAST_TEAR));
            ingredients.add(net.minecraft.recipe.Ingredient.ofItems(Items.BLAZE_POWDER));
            var newRecipe = new net.minecraft.recipe.ShapelessRecipe(
                "",
                net.minecraft.recipe.book.CraftingRecipeCategory.MISC,
                new ItemStack(Items.ENDER_EYE),
                ingredients
            );
            remainingRecipes.add(new net.minecraft.recipe.RecipeEntry<>(
                Identifier.of("sacrificemod", "ghast_tear_ender_eye"),
                newRecipe
            ));
        }

        recipeManager.setRecipes(remainingRecipes);
    }

    /**
     * 恢复被移除的配方
     * <p>将之前移除的配方重新添加回来，移除修改后的末影之眼配方，恢复原始配方</p>
     * @param server Minecraft服务器实例
     */
    private void restoreRecipes(MinecraftServer server) {
        if (removedRecipes.isEmpty() && originalEnderEyeRecipe == null) return;
        var recipeManager = server.getRecipeManager();
        var allRecipes = new ArrayList<>(recipeManager.values());
        // 恢复被移除的配方
        allRecipes.addAll(removedRecipes);

        // 移除修改后的末影之眼配方，恢复原始配方
        if (originalEnderEyeRecipe != null) {
            allRecipes.removeIf(r -> r.id().equals(Identifier.of("sacrificemod", "ghast_tear_ender_eye")));
            allRecipes.add(originalEnderEyeRecipe);
            originalEnderEyeRecipe = null;
        }

        recipeManager.setRecipes(allRecipes);
        removedRecipes.clear();
    }

    // ==================== 武器系统 ====================

    /**
     * 给玩家发放所有初始武器
     * <p>发放：霸王弓、箭、散弹弩、火焰剑、工具斧</p>
     * @param player 目标玩家
     * @param state 游戏全局状态（用于读取武器附魔等级配置）
     */
    private void giveWeapons(ServerPlayerEntity player, GameState state) {
        MinecraftServer server = player.getServer();
        player.giveItemStack(createOverlordBow(server, state));
        player.giveItemStack(createArrow());
        player.giveItemStack(createScatterCrossbow(server, state));
        player.giveItemStack(createFlameSword(server, state));
        player.giveItemStack(createToolAxe());
    }

    /**
     * 确保玩家拥有所有武器，缺失则补充
     * <p>通过检查物品的CUSTOM_DATA中的WEAPON_TAG来识别武器类型</p>
     * @param player 目标玩家
     * @param state 游戏全局状态
     */
    private void ensureWeapons(ServerPlayerEntity player, GameState state) {
        boolean hasBow = false, hasArrow = false, hasCrossbow = false, hasSword = false, hasAxe = false;

        // 遍历背包检查每种武器是否存在
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
                var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
                if (customData != null && customData.contains(WEAPON_TAG)) {
                    String weaponType = customData.copyNbt().getString(WEAPON_TAG);
                    switch (weaponType) {
                        case "bow" -> hasBow = true;
                        case "arrow" -> hasArrow = true;
                        case "crossbow" -> hasCrossbow = true;
                        case "sword" -> hasSword = true;
                        case "axe" -> hasAxe = true;
                    }
                }
            }
        }

        // 补充缺失的武器
        if (!hasBow) player.giveItemStack(createOverlordBow(player.getServer(), state));
        if (!hasArrow) player.giveItemStack(createArrow());
        if (!hasCrossbow) player.giveItemStack(createScatterCrossbow(player.getServer(), state));
        if (!hasSword) player.giveItemStack(createFlameSword(player.getServer(), state));
        if (!hasAxe) player.giveItemStack(createToolAxe());
    }

    /**
     * 移除玩家的所有初始武器
     * <p>通过CUSTOM_DATA中的WEAPON_TAG识别并移除所有玩法武器</p>
     * @param player 目标玩家
     */
    private void removeWeapons(ServerPlayerEntity player) {
        // 从后往前遍历，避免移除时索引偏移
        for (int i = player.getInventory().size() - 1; i >= 0; i--) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
                var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
                if (customData != null && customData.contains(WEAPON_TAG)) {
                    player.getInventory().removeStack(i);
                }
            }
        }
    }

    /**
     * 移除非初始武器的剑、斧和盔甲
     * <p>装备栏中的盔甲和背包中的非武器剑/斧/盔甲会被掉落在地上</p>
     * @param player 目标玩家
     */
    private void removeForbiddenItems(ServerPlayerEntity player) {
        // 移除装备栏中的盔甲并掉落在地上
        for (var slot : new net.minecraft.entity.EquipmentSlot[]{
            net.minecraft.entity.EquipmentSlot.HEAD,
            net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS,
            net.minecraft.entity.EquipmentSlot.FEET
        }) {
            ItemStack equipped = player.getEquippedStack(slot);
            if (equipped.isEmpty()) continue;
            if (equipped.getItem() instanceof net.minecraft.item.ArmorItem) {
                player.dropItem(equipped, false);
                player.equipStack(slot, ItemStack.EMPTY);
            }
        }

        // 移除背包中的非武器剑/斧/盔甲并掉落在地上
        for (int i = player.getInventory().size() - 1; i >= 0; i--) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // 检查是否是玩法武器（有WEAPON_TAG标记的不移除）
            boolean isWeapon = false;
            if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
                var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
                isWeapon = customData != null && customData.contains(WEAPON_TAG);
            }

            // 非武器的剑/斧/盔甲需要移除
            if (!isWeapon) {
                var item = stack.getItem();
                if (item instanceof net.minecraft.item.SwordItem || item instanceof net.minecraft.item.AxeItem
                    || item instanceof net.minecraft.item.ArmorItem) {
                    player.dropItem(stack, false);
                    player.getInventory().removeStack(i);
                }
            }
        }
    }

    // ==================== 武器创建 ====================

    /**
     * 创建霸王弓
     * <p>附魔：力量（等级可配置）、火矢I、冲击（等级可配置），不可破坏</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态（读取附魔等级配置）
     * @return 霸王弓物品堆
     */
    private ItemStack createOverlordBow(MinecraftServer server, GameState state) {
        ItemStack bow = new ItemStack(Items.BOW);
        var nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString(WEAPON_TAG, "bow");
        bow.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
        bow.set(DataComponentTypes.ITEM_NAME, Text.literal("霸王弓"));
        bow.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(false));

        // 添加附魔
        var enchantments = net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT;
        var builder = new net.minecraft.component.type.ItemEnchantmentsComponent.Builder(enchantments);
        var registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        var power = registry.getEntry(Enchantments.POWER).orElse(null);
        var flame = registry.getEntry(Enchantments.FLAME).orElse(null);
        var punch = registry.getEntry(Enchantments.PUNCH).orElse(null);
        if (power != null) builder.add(power, state.getArcherBowPower());
        if (flame != null) builder.add(flame, 1);
        if (punch != null) builder.add(punch, state.getArcherBowPunch());
        bow.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        return bow;
    }

    /**
     * 创建箭矢
     * <p>普通箭，带有WEAPON_TAG标记</p>
     * @return 箭矢物品堆
     */
    private ItemStack createArrow() {
        ItemStack arrow = new ItemStack(Items.ARROW);
        var nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString(WEAPON_TAG, "arrow");
        arrow.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
        return arrow;
    }

    /**
     * 创建散弹弩
     * <p>附魔：多重射击（等级可配置）、穿透（等级可配置），不可破坏</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态（读取附魔等级配置）
     * @return 散弹弩物品堆
     */
    private ItemStack createScatterCrossbow(MinecraftServer server, GameState state) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        var nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString(WEAPON_TAG, "crossbow");
        crossbow.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
        crossbow.set(DataComponentTypes.ITEM_NAME, Text.literal("散弹弩"));
        crossbow.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(false));

        // 添加附魔
        var enchantments = net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT;
        var builder = new net.minecraft.component.type.ItemEnchantmentsComponent.Builder(enchantments);
        var registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        var multishot = registry.getEntry(Enchantments.MULTISHOT).orElse(null);
        var piercing = registry.getEntry(Enchantments.PIERCING).orElse(null);
        if (multishot != null) builder.add(multishot, state.getArcherCrossbowMultishot());
        if (piercing != null) builder.add(piercing, state.getArcherCrossbowPiercing());
        crossbow.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        return crossbow;
    }

    /**
     * 创建火焰剑
     * <p>木质剑基础，附魔：锋利（等级可配置）、火焰附加（等级可配置），不可破坏</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态（读取附魔等级配置）
     * @return 火焰剑物品堆
     */
    private ItemStack createFlameSword(MinecraftServer server, GameState state) {
        ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
        var nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString(WEAPON_TAG, "sword");
        sword.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
        sword.set(DataComponentTypes.ITEM_NAME, Text.literal("火焰剑"));
        sword.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(false));

        // 添加附魔
        var enchantments = net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT;
        var builder = new net.minecraft.component.type.ItemEnchantmentsComponent.Builder(enchantments);
        var registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        var sharpness = registry.getEntry(Enchantments.SHARPNESS).orElse(null);
        var fireAspect = registry.getEntry(Enchantments.FIRE_ASPECT).orElse(null);
        if (sharpness != null) builder.add(sharpness, state.getArcherSwordSharpness());
        if (fireAspect != null) builder.add(fireAspect, state.getArcherSwordFireAspect());
        sword.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        return sword;
    }

    /**
     * 创建工具斧
     * <p>钻石斧基础，攻击力设为1（通过属性修饰符-6覆盖），不可破坏，用于挖掘而非战斗</p>
     * @return 工具斧物品堆
     */
    private ItemStack createToolAxe() {
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        var nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString(WEAPON_TAG, "axe");
        axe.set(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
        axe.set(DataComponentTypes.ITEM_NAME, Text.literal("工具斧"));
        axe.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(false));

        // 攻击力设为1 - 通过属性修饰符覆盖钻石斧的默认攻击力（7→1，修饰值-6）
        axe.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, net.minecraft.component.type.AttributeModifiersComponent.builder()
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(
                Identifier.of("sacrificemod", "tool_axe_damage"),
                -6.0, EntityAttributeModifier.Operation.ADD_VALUE),
                AttributeModifierSlot.MAINHAND)
            .build());

        return axe;
    }

    // ==================== 生物替换 ====================

    /**
     * 替换已存在的非目标生物
     * <p>主世界：水生生物→守卫者/溺尸，陆地非远程生物→骷髅/流浪者/女巫/溺尸
     * 地狱：非烈焰人/恶魂→烈焰人/恶魂
     * 替换前会检查生物上限，超过上限则直接移除原生物而不生成替代品</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态（用于读取生物上限参数）
     */
    private void replaceExistingMobs(MinecraftServer server, GameState state) {
        int mobLimit = state.getArcherMobLimit();

        for (var world : server.getWorlds()) {
            // 先统计当前各维度的弓箭手生物数量
            int overworldMobs = 0;
            int netherMobs = 0;
            if (world.getRegistryKey() == World.OVERWORLD) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof SkeletonEntity || entity instanceof WitchEntity
                        || entity instanceof StrayEntity || entity instanceof DrownedEntity
                        || entity instanceof GuardianEntity) overworldMobs++;
                }
            } else if (world.getRegistryKey() == World.NETHER) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof BlazeEntity || entity instanceof GhastEntity) netherMobs++;
                }
            }

            List<net.minecraft.entity.Entity> entities = new ArrayList<>();
            for (var entity : world.iterateEntities()) {
                entities.add(entity);
            }
            for (var entity : entities) {
                // 跳过玩家和末影龙
                if (entity instanceof PlayerEntity) continue;
                if (entity instanceof EnderDragonEntity) continue;

                if (world.getRegistryKey() == World.OVERWORLD) {
                    // 检查主世界生物上限
                    if (overworldMobs >= mobLimit) {
                        // 超过上限，直接移除非目标生物而不生成替代品
                        if ((entity instanceof WaterCreatureEntity && !(entity instanceof GuardianEntity) && !(entity instanceof DrownedEntity))
                            || (entity instanceof LivingEntity && !(entity instanceof SkeletonEntity)
                                && !(entity instanceof StrayEntity) && !(entity instanceof DrownedEntity)
                                && !(entity instanceof WitchEntity) && !(entity instanceof GuardianEntity)
                                && !(entity instanceof PlayerEntity))) {
                            entity.discard();
                        }
                        continue;
                    }
                    // 水中生物 → 守卫者或溺尸
                    if (entity instanceof WaterCreatureEntity && !(entity instanceof GuardianEntity) && !(entity instanceof DrownedEntity)) {
                        spawnWaterReplacementAndRemove(world, entity);
                        overworldMobs++;
                    }
                    // 陆地非骷髅/流浪者/溺尸/女巫/守卫者的生物 → 骷髅/流浪者/女巫/溺尸
                    else if (entity instanceof LivingEntity && !(entity instanceof SkeletonEntity)
                        && !(entity instanceof StrayEntity) && !(entity instanceof DrownedEntity)
                        && !(entity instanceof WitchEntity) && !(entity instanceof GuardianEntity)) {
                        spawnReplacementAndRemove(world, entity, true);
                        overworldMobs++;
                    }
                    // 已有的骷髅/流浪者/溺尸/女巫需要补充装备（如果尚未装备）
                    else if (entity instanceof SkeletonEntity skeleton && !skeleton.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET)) {
                        ArcherMobHelper.equipSkeleton(skeleton, world);
                    }
                    else if (entity instanceof StrayEntity stray && !stray.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET)) {
                        ArcherMobHelper.equipStray(stray, world);
                    }
                    else if (entity instanceof DrownedEntity drowned && drowned.getMainHandStack().isEmpty()) {
                        ArcherMobHelper.equipDrowned(drowned, world);
                    }
                    else if (entity instanceof WitchEntity witch && !witch.hasStatusEffect(StatusEffects.RESISTANCE)) {
                        ArcherMobHelper.applyWitchEffects(witch);
                    }
                } else if (world.getRegistryKey() == World.NETHER) {
                    // 检查地狱生物上限
                    if (netherMobs >= MAX_NETHER_MOBS) {
                        if (!(entity instanceof BlazeEntity) && !(entity instanceof GhastEntity)
                            && entity instanceof LivingEntity && !(entity instanceof PlayerEntity)) {
                            entity.discard();
                        }
                        continue;
                    }
                    // 地狱非烈焰人/恶魂的生物 → 烈焰人或恶魂
                    if (!(entity instanceof BlazeEntity) && !(entity instanceof GhastEntity)
                        && entity instanceof LivingEntity && !(entity instanceof PlayerEntity)) {
                        spawnNetherReplacementAndRemove(world, entity);
                        netherMobs++;
                    }
                }
            }
        }
    }

    /**
     * 在主世界陆地替换非目标生物
     * <p>根据环境条件选择替换类型：
     * - 水中 → 溺尸（引雷3+穿刺5三叉戟，保护4海龟壳，抗火）
     * - 雪地 → 流浪者（50%概率，钻石头盔+力量5弓+力量5）
     * - 其他 → 骷髅（50%概率，钻石头帽+力量5弓）或女巫（永久抗性3）
     * 地下矿洞中的生物替换后会被传送到地表，避免地表怪物过少</p>
     * @param world 世界实例
     * @param original 原始生物实体
     * @param isLand 是否在陆地上
     */
    private void spawnReplacementAndRemove(ServerWorld world, net.minecraft.entity.Entity original, boolean isLand) {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        var pos = original.getPos();
        var blockPos = BlockPos.ofFloored(pos);

        // 检测是否在水中
        boolean isInWater = world.getBlockState(blockPos).isOf(net.minecraft.block.Blocks.WATER)
            || world.getBlockState(blockPos.up()).isOf(net.minecraft.block.Blocks.WATER);

        // 如果不在水中，检测是否在地下（没有天空光照），50%概率传送到地表
        double spawnX = pos.x;
        double spawnY = pos.y;
        double spawnZ = pos.z;
        if (!isInWater) {
            int skyLight = world.getLightLevel(net.minecraft.world.LightType.SKY, blockPos);
            if (skyLight == 0 && random.nextBoolean()) {
                // 生物在地下，50%概率传送到地表（保留部分矿洞怪物）
                int surfaceY = findSurfaceY(world, blockPos.getX(), blockPos.getZ());
                if (surfaceY > blockPos.getY()) {
                    spawnY = surfaceY + 1;
                }
            }
        }

        // 检测是否在雪地生物群系（温度<0.15），使用生成位置判断
        var spawnBlockPos = BlockPos.ofFloored(spawnX, spawnY, spawnZ);
        boolean isSnowy = world.getBiome(spawnBlockPos).value().getTemperature() < 0.15f;

        if (isInWater) {
            // 溺尸：引雷3+穿刺5三叉戟，保护4海龟壳，抗火
            DrownedEntity drowned = new DrownedEntity(EntityType.DROWNED, world);
            drowned.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            drowned.initialize(world, world.getLocalDifficulty(drowned.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipDrowned(drowned, world);
            world.spawnEntity(drowned);
        } else if (isSnowy && random.nextBoolean()) {
            // 流浪者：钻石头盔 + 力量5弓 + 力量5
            StrayEntity stray = new StrayEntity(EntityType.STRAY, world);
            stray.refreshPositionAndAngles(spawnX, spawnY, spawnZ, original.getYaw(), original.getPitch());
            stray.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(spawnX, spawnY, spawnZ)),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipStray(stray, world);
            world.spawnEntity(stray);
        } else if (random.nextBoolean()) {
            // 骷髅：钻石头帽 + 力量5弓
            SkeletonEntity skeleton = new SkeletonEntity(EntityType.SKELETON, world);
            skeleton.refreshPositionAndAngles(spawnX, spawnY, spawnZ, original.getYaw(), original.getPitch());
            skeleton.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(spawnX, spawnY, spawnZ)),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipSkeleton(skeleton, world);
            world.spawnEntity(skeleton);
        } else {
            // 女巫：永久抗性提升3
            WitchEntity witch = new WitchEntity(EntityType.WITCH, world);
            witch.refreshPositionAndAngles(spawnX, spawnY, spawnZ, original.getYaw(), original.getPitch());
            witch.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(spawnX, spawnY, spawnZ)),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.applyWitchEffects(witch);
            world.spawnEntity(witch);
        }

        // 移除原始生物
        original.discard();
    }

    /**
     * 在主世界水中替换非目标水生生物
     * <p>50%概率生成守卫者（受数量上限约束），50%概率生成溺尸</p>
     * @param world 世界实例
     * @param original 原始水生生物实体
     */
    private void spawnWaterReplacementAndRemove(ServerWorld world, net.minecraft.entity.Entity original) {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        var pos = original.getPos();

        if (random.nextBoolean()) {
            // 守卫者（受数量上限约束，防止过多导致卡顿）
            int guardianCount = 0;
            for (var entity : world.iterateEntities()) {
                if (entity instanceof GuardianEntity) guardianCount++;
            }
            if (guardianCount >= MAX_GUARDIANS) {
                // 超过上限直接移除原始生物，不生成替代品
                original.discard();
                return;
            }
            GuardianEntity guardian = new GuardianEntity(EntityType.GUARDIAN, world);
            guardian.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            guardian.initialize(world, world.getLocalDifficulty(guardian.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            world.spawnEntity(guardian);
        } else {
            // 溺尸：引雷3+穿刺5三叉戟，保护4海龟壳，抗火
            DrownedEntity drowned = new DrownedEntity(EntityType.DROWNED, world);
            drowned.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            drowned.initialize(world, world.getLocalDifficulty(drowned.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipDrowned(drowned, world);
            world.spawnEntity(drowned);
        }
        original.discard();
    }

    /**
     * 在地狱替换非目标生物
     * <p>50%概率生成烈焰人，50%概率生成恶魂</p>
     * @param world 世界实例
     * @param original 原始生物实体
     */
    private void spawnNetherReplacementAndRemove(ServerWorld world, net.minecraft.entity.Entity original) {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        var pos = original.getPos();

        if (random.nextBoolean()) {
            // 烈焰人
            BlazeEntity blaze = new BlazeEntity(EntityType.BLAZE, world);
            blaze.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            blaze.initialize(world, world.getLocalDifficulty(blaze.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            world.spawnEntity(blaze);
        } else {
            // 恶魂
            GhastEntity ghast = new GhastEntity(EntityType.GHAST, world);
            ghast.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            ghast.initialize(world, world.getLocalDifficulty(ghast.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            world.spawnEntity(ghast);
        }

        original.discard();
    }

    /**
     * 清除掉落的非箭初始武器
     * <p>弓、弩、剑、斧掉落后直接消失，箭矢不掉落消失（可以捡）</p>
     * @param server Minecraft服务器实例
     */
    private void removeDroppedWeapons(MinecraftServer server) {
        for (var world : server.getWorlds()) {
            for (var entity : world.iterateEntities()) {
                if (entity instanceof net.minecraft.entity.ItemEntity itemEntity) {
                    ItemStack stack = itemEntity.getStack();
                    if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
                        var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
                        if (customData != null && customData.contains(WEAPON_TAG)) {
                            String weaponType = customData.copyNbt().getString(WEAPON_TAG);
                            // 箭不掉落消失，其他武器掉落后消失
                            if (!"arrow".equals(weaponType)) {
                                itemEntity.discard();
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== 主动生物生成 ====================

    /**
     * 主动在玩家周围生成远程敌对生物
     * <p>增加世界中的远程敌对生物密度，确保白天也有足够的敌人。
     * 主世界：每个玩家周围生成2-3个骷髅/流浪者/女巫/溺尸（上限由mobLimit参数控制）
     * 地狱：每个玩家周围生成1个烈焰人/恶魂（上限40个）</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态（用于读取生成上限参数）
     */
    private void spawnMobsAroundPlayers(MinecraftServer server, GameState state) {
        var overworld = server.getWorld(World.OVERWORLD);
        var nether = server.getWorld(World.NETHER);

        // 统计当前各维度的远程敌对生物数量
        int overworldMobs = 0;
        int netherMobs = 0;
        int guardianCount = 0;

        if (overworld != null) {
            for (var entity : overworld.iterateEntities()) {
                if (entity instanceof SkeletonEntity || entity instanceof WitchEntity
                    || entity instanceof StrayEntity || entity instanceof DrownedEntity) overworldMobs++;
                if (entity instanceof GuardianEntity) guardianCount++;
            }
        }
        if (nether != null) {
            for (var entity : nether.iterateEntities()) {
                if (entity instanceof BlazeEntity || entity instanceof GhastEntity) netherMobs++;
            }
        }

        var players = server.getPlayerManager().getPlayerList();
        var random = java.util.concurrent.ThreadLocalRandom.current();

        for (var player : players) {
            var world = player.getWorld();

            if (world.getRegistryKey() == World.OVERWORLD && overworldMobs < state.getArcherMobLimit()) {
                // 主世界：每个玩家周围生成2-3个生物
                int count = random.nextInt(2, 4);
                for (int i = 0; i < count; i++) {
                    // 在玩家周围20~60格的环形区域随机选点
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 20 + random.nextDouble() * SPAWN_RADIUS;
                    double x = player.getX() + Math.cos(angle) * dist;
                    double z = player.getZ() + Math.sin(angle) * dist;

                    // 70%概率在地表生成，30%概率在玩家附近Y偏移生成（保证矿洞也有怪物）
                    int spawnY;
                    if (random.nextDouble() < 0.7) {
                        // 地表生成：查找地表Y坐标
                        int surfaceY = findSurfaceY((ServerWorld)world, (int)x, (int)z);
                        spawnY = surfaceY + 1;
                    } else {
                        // 玩家附近生成：可能在矿洞中
                        spawnY = (int)player.getY() + random.nextInt(-10, 10);
                        // 从该位置向下寻找实心方块
                        var searchPos = new BlockPos((int)x, spawnY, (int)z);
                        while (searchPos.getY() > world.getBottomY() && !world.getBlockState(searchPos).isSolid()) {
                            searchPos = searchPos.down();
                        }
                        spawnY = searchPos.getY() + 1;
                    }

                    var blockPos = new BlockPos((int)x, spawnY, (int)z);

                    // 确保脚部和头部没有实心方块
                    if (spawnY > world.getBottomY() && !world.getBlockState(blockPos).isSolid() && !world.getBlockState(blockPos.up()).isSolid()) {
                        // 检测环境条件选择生物类型
                        boolean isInWater = world.getBlockState(blockPos).isOf(net.minecraft.block.Blocks.WATER)
                            || world.getBlockState(blockPos.up()).isOf(net.minecraft.block.Blocks.WATER);
                        boolean isSnowy = world.getBiome(blockPos).value().getTemperature() < 0.15f;

                        if (isInWater) {
                            // 水中生成溺尸
                            DrownedEntity drowned = new DrownedEntity(EntityType.DROWNED, (ServerWorld)world);
                            drowned.refreshPositionAndAngles(x, blockPos.getY(), z, random.nextFloat() * 360, 0);
                            drowned.initialize((ServerWorld)world, world.getLocalDifficulty(blockPos),
                                net.minecraft.entity.SpawnReason.COMMAND, null);
                            ArcherMobHelper.equipDrowned(drowned, (ServerWorld)world);
                            ((ServerWorld)world).spawnEntity(drowned);
                            overworldMobs++;
                        } else if (isSnowy && random.nextBoolean()) {
                            // 雪地生成流浪者
                            StrayEntity stray = new StrayEntity(EntityType.STRAY, (ServerWorld)world);
                            stray.refreshPositionAndAngles(x, blockPos.getY(), z, random.nextFloat() * 360, 0);
                            stray.initialize((ServerWorld)world, world.getLocalDifficulty(blockPos),
                                net.minecraft.entity.SpawnReason.COMMAND, null);
                            ArcherMobHelper.equipStray(stray, (ServerWorld)world);
                            ((ServerWorld)world).spawnEntity(stray);
                            overworldMobs++;
                        } else if (random.nextBoolean()) {
                            // 生成骷髅
                            SkeletonEntity skeleton = new SkeletonEntity(EntityType.SKELETON, (ServerWorld)world);
                            skeleton.refreshPositionAndAngles(x, blockPos.getY(), z, random.nextFloat() * 360, 0);
                            skeleton.initialize((ServerWorld)world, world.getLocalDifficulty(blockPos),
                                net.minecraft.entity.SpawnReason.COMMAND, null);
                            ArcherMobHelper.equipSkeleton(skeleton, (ServerWorld)world);
                            ((ServerWorld)world).spawnEntity(skeleton);
                            overworldMobs++;
                        } else {
                            // 生成女巫
                            WitchEntity witch = new WitchEntity(EntityType.WITCH, (ServerWorld)world);
                            witch.refreshPositionAndAngles(x, blockPos.getY(), z, random.nextFloat() * 360, 0);
                            witch.initialize((ServerWorld)world, world.getLocalDifficulty(blockPos),
                                net.minecraft.entity.SpawnReason.COMMAND, null);
                            ArcherMobHelper.applyWitchEffects(witch);
                            ((ServerWorld)world).spawnEntity(witch);
                            overworldMobs++;
                        }
                    }
                }
            }

            if (world.getRegistryKey() == World.NETHER && netherMobs < MAX_NETHER_MOBS) {
                // 地狱：每个玩家周围生成1个生物
                int count = random.nextInt(1, 2);
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 20 + random.nextDouble() * SPAWN_RADIUS;
                    double x = player.getX() + Math.cos(angle) * dist;
                    double z = player.getZ() + Math.sin(angle) * dist;

                    // 地狱：在玩家附近Y偏移寻找安全位置，各高度均可生成
                    int spawnY = (int)player.getY() + random.nextInt(-10, 15);
                    var blockPos = new BlockPos((int)x, spawnY, (int)z);
                    // 从该位置向下寻找实心方块，再上移一格
                    while (blockPos.getY() > world.getBottomY() && !world.getBlockState(blockPos).isSolid()) {
                        blockPos = blockPos.down();
                    }
                    blockPos = blockPos.up();

                    // 排除基岩层（地狱底部Y=0~4为基岩层，顶部Y=123~127为基岩层）
                    boolean isOnBedrock = blockPos.getY() <= 5 || blockPos.getY() >= 123;

                    if (!isOnBedrock && !world.getBlockState(blockPos).isSolid() && !world.getBlockState(blockPos.up()).isSolid()) {
                        if (random.nextBoolean()) {
                            // 生成烈焰人
                            BlazeEntity blaze = new BlazeEntity(EntityType.BLAZE, (ServerWorld)world);
                            blaze.refreshPositionAndAngles(x, blockPos.getY(), z, random.nextFloat() * 360, 0);
                            blaze.initialize((ServerWorld)world, world.getLocalDifficulty(blockPos),
                                net.minecraft.entity.SpawnReason.COMMAND, null);
                            ((ServerWorld)world).spawnEntity(blaze);
                            netherMobs++;
                        } else {
                            // 生成恶魂
                            GhastEntity ghast = new GhastEntity(EntityType.GHAST, (ServerWorld)world);
                            ghast.refreshPositionAndAngles(x, blockPos.getY(), z, random.nextFloat() * 360, 0);
                            ghast.initialize((ServerWorld)world, world.getLocalDifficulty(blockPos),
                                net.minecraft.entity.SpawnReason.COMMAND, null);
                            ((ServerWorld)world).spawnEntity(ghast);
                            netherMobs++;
                        }
                    }
                }
            }
        }
    }

    // ==================== 地表查找 ====================

    /**
     * 查找指定XZ坐标的地表Y坐标
     * <p>委托给 ArcherMobHelper.findSurfaceY 实现。
     * 从世界顶部向下搜索，找到第一个实心方块，返回其Y坐标。</p>
     * @param world 世界实例
     * @param x 方块X坐标
     * @param z 方块Z坐标
     * @return 地表方块的Y坐标
     */
    private int findSurfaceY(ServerWorld world, int x, int z) {
        return ArcherMobHelper.findSurfaceY(world, x, z);
    }

    // ==================== 末影龙增强 ====================

    /**
     * 为末影龙添加增益效果
     * <p>抗性提升3（减少60%伤害）+ 力量2（增加攻击伤害）</p>
     * @param server Minecraft服务器实例
     */
    private void buffEnderDragon(MinecraftServer server) {
        var end = server.getWorld(World.END);
        if (end == null) return;

        for (var entity : end.iterateEntities()) {
            if (entity instanceof EnderDragonEntity dragon) {
                // 抗性提升3 - 减少60%受到的伤害
                dragon.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 2, false, false));
                // 力量2 - 增加攻击伤害
                dragon.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 1, false, false));
            }
        }
    }

    /**
     * 清除末影龙的增益效果
     * <p>玩法结束时调用</p>
     * @param world 末地世界实例
     */
    private void clearDragonBuff(ServerWorld world) {
        for (var entity : world.iterateEntities()) {
            if (entity instanceof EnderDragonEntity dragon) {
                dragon.removeStatusEffect(StatusEffects.RESISTANCE);
                dragon.removeStatusEffect(StatusEffects.STRENGTH);
            }
        }
    }

    // ==================== 末影水晶重生 ====================

    /**
     * 记录末影水晶被打爆的位置
     * <p>在水晶被破坏时调用，记录位置和重生时间</p>
     * @param pos 水晶位置
     * @param currentTick 当前服务器tick
     */
    public void onCrystalDestroyed(BlockPos pos, long currentTick) {
        destroyedCrystals.put(pos.toImmutable(), currentTick + CRYSTAL_RESPAWN_TICKS);
    }

    /**
     * 检查并重生已到时间的末影水晶
     * <p>每tick检查被打爆的水晶是否到达重生时间，到达后在原位置重新生成</p>
     * @param server Minecraft服务器实例
     * @param currentTick 当前服务器tick
     */
    private void respawnCrystals(MinecraftServer server, long currentTick) {
        var end = server.getWorld(World.END);
        if (end == null) return;

        // 收集已到重生时间的水晶位置
        var toRespawn = new ArrayList<BlockPos>();
        for (var entry : destroyedCrystals.entrySet()) {
            if (currentTick >= entry.getValue()) {
                toRespawn.add(entry.getKey());
            }
        }

        // 重生水晶
        for (BlockPos pos : toRespawn) {
            destroyedCrystals.remove(pos);
            var crystal = new net.minecraft.entity.decoration.EndCrystalEntity(
                net.minecraft.entity.EntityType.END_CRYSTAL, end);
            crystal.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            end.spawnEntity(crystal);
        }
    }

    // ==================== 击杀计数和护甲 ====================

    /**
     * 处理玩家击杀生物事件
     * <p>击杀非玩家生物时增加击杀计数，达到阈值时获得+1护甲值。
     * 护甲通过持久属性修饰符实现，死亡不消失。</p>
     * @param server Minecraft服务器实例
     * @param state 游戏全局状态
     * @param player 击杀生物的玩家
     * @param killed 被击杀的生物
     */
    public void onPlayerKill(MinecraftServer server, GameState state, ServerPlayerEntity player, LivingEntity killed) {
        // 不计算玩家击杀
        if (killed instanceof PlayerEntity) return;

        UUID uuid = player.getUuid();
        int killCount = state.getArcherKillCount(uuid) + 1;
        state.setArcherKillCount(uuid, killCount);

        // 达到击杀阈值时获得护甲
        int killsPerArmor = state.getArcherKillsPerArmor();
        if (killCount >= killsPerArmor) {
            // 重置击杀计数（保留余数）
            state.setArcherKillCount(uuid, killCount - killsPerArmor);
            int bonusArmor = state.getArcherBonusArmor(uuid) + 1;
            state.setArcherBonusArmor(uuid, bonusArmor);

            // 更新护甲属性修饰符（使用持久修饰符，死亡不消失）
            var attr = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
            if (attr != null) {
                attr.removeModifier(ARCHER_ARMOR_ID);
                attr.addPersistentModifier(new EntityAttributeModifier(
                    ARCHER_ARMOR_ID, bonusArmor, EntityAttributeModifier.Operation.ADD_VALUE));
            }

            player.sendMessage(Text.literal("§a击杀积累！获得 +1 护甲值（当前额外护甲: " + bonusArmor + "）"), true);
        }
    }
}
