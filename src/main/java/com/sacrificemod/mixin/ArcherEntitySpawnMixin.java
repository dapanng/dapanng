package com.sacrificemod.mixin;

import com.sacrificemod.SacrificeMod;
import com.sacrificemod.util.ArcherMobHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 弓箭手大作战：拦截实体生成到世界，替换为对应敌对生物
 * 使用ServerWorld.spawnEntity作为注入点，比Entity构造函数更可靠
 */
@Mixin(ServerWorld.class)
public abstract class ArcherEntitySpawnMixin {

    /**
     * 拦截实体生成事件，在弓箭手大作战玩法下将生成的生物替换为对应的敌对生物
     *
     * @param entity 即将生成到世界中的实体
     * @param cir 回调信息，可用于取消原始生成并设置返回值
     */
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerWorld world = (ServerWorld) (Object) this;
        // 客户端不处理
        if (world.isClient()) return;
        // 仅在弓箭手大作战玩法激活时生效
        if (!"archer".equals(SacrificeMod.getActiveGameplayId())) return;

        // 玩家和末影龙不替换
        if (entity instanceof PlayerEntity || entity instanceof EnderDragonEntity) return;

        // 末地：阻止末影人生成
        if (world.getRegistryKey() == World.END && entity instanceof EndermanEntity) {
            cir.setReturnValue(false);
            return;
        }

        // 主世界：替换非骷髅/女巫/守卫者的生物
        if (world.getRegistryKey() == World.OVERWORLD) {
            // 检查主世界生物上限，超过上限则直接取消生成（不替换）
            int mobLimit = com.sacrificemod.GameState.getServerState(world.getServer()).getArcherMobLimit();
            int currentMobs = 0;
            for (var e : world.iterateEntities()) {
                if (e instanceof SkeletonEntity || e instanceof WitchEntity
                    || e instanceof StrayEntity || e instanceof DrownedEntity
                    || e instanceof GuardianEntity) currentMobs++;
            }
            if (currentMobs >= mobLimit) {
                // 超过上限，取消非目标生物的生成
                if ((entity instanceof WaterCreatureEntity && !(entity instanceof GuardianEntity) && !(entity instanceof DrownedEntity))
                    || (entity instanceof LivingEntity && !(entity instanceof PlayerEntity)
                        && !(entity instanceof SkeletonEntity) && !(entity instanceof WitchEntity)
                        && !(entity instanceof GuardianEntity) && !(entity instanceof StrayEntity)
                        && !(entity instanceof DrownedEntity))) {
                    cir.setReturnValue(false);
                    return;
                }
                return;
            }

            // 水中生物 → 守卫者或溺尸
            if (entity instanceof WaterCreatureEntity && !(entity instanceof GuardianEntity) && !(entity instanceof DrownedEntity)) {
                spawnWaterReplacementAndCancel(world, entity, cir);
                return;
            }
            // 陆地非骷髅/流浪者/溺尸/女巫生物 → 骷髅或女巫
            if (entity instanceof LivingEntity && !(entity instanceof PlayerEntity)
                && !(entity instanceof SkeletonEntity) && !(entity instanceof WitchEntity)
                && !(entity instanceof GuardianEntity) && !(entity instanceof StrayEntity)
                && !(entity instanceof DrownedEntity)) {
                spawnOverworldReplacementAndCancel(world, entity, cir);
                return;
            }
            // 自然生成的骷髅/流浪者/溺尸/女巫也需要装备（不取消生成，仅添加装备和效果）
            if (entity instanceof SkeletonEntity skeleton) {
                ArcherMobHelper.equipSkeleton(skeleton, world);
                return;
            }
            if (entity instanceof StrayEntity stray) {
                ArcherMobHelper.equipStray(stray, world);
                return;
            }
            if (entity instanceof DrownedEntity drowned) {
                ArcherMobHelper.equipDrowned(drowned, world);
                return;
            }
            if (entity instanceof WitchEntity witch) {
                ArcherMobHelper.applyWitchEffects(witch);
                return;
            }
        }

        // 地狱：替换非烈焰人/恶魂的生物
        if (world.getRegistryKey() == World.NETHER) {
            if (entity instanceof LivingEntity && !(entity instanceof PlayerEntity)
                && !(entity instanceof BlazeEntity) && !(entity instanceof GhastEntity)) {
                spawnNetherReplacementAndCancel(world, entity, cir);
                return;
            }
        }
    }

    /**
     * 主世界陆地生物替换逻辑：根据环境条件替换为溺尸/流浪者/骷髅/女巫
     * - 在水中 → 溺尸
     * - 在寒冷生物群系 → 50%概率流浪者
     * - 其他 → 50%概率骷髅，50%概率女巫
     *
     * @param world 服务端世界
     * @param original 原始被替换的实体
     * @param cir 回调信息，用于取消原始生成
     */
    private void spawnOverworldReplacementAndCancel(ServerWorld world, Entity original, CallbackInfoReturnable<Boolean> cir) {
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
                int surfaceY = ArcherMobHelper.findSurfaceY(world, blockPos.getX(), blockPos.getZ());
                if (surfaceY > blockPos.getY()) {
                    spawnY = surfaceY + 1;
                }
            }
        }

        // 检测是否在寒冷生物群系（温度低于0.15），使用生成位置判断
        var spawnBlockPos = BlockPos.ofFloored(spawnX, spawnY, spawnZ);
        boolean isSnowy = world.getBiome(spawnBlockPos).value().getTemperature() < 0.15f;

        if (isInWater) {
            // 水中生成溺尸
            DrownedEntity drowned = new DrownedEntity(EntityType.DROWNED, world);
            drowned.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            drowned.initialize(world, world.getLocalDifficulty(drowned.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipDrowned(drowned, world);
            world.spawnEntity(drowned);
        } else if (isSnowy && random.nextBoolean()) {
            // 寒冷生物群系50%概率生成流浪者
            StrayEntity stray = new StrayEntity(EntityType.STRAY, world);
            stray.refreshPositionAndAngles(spawnX, spawnY, spawnZ, original.getYaw(), original.getPitch());
            stray.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(spawnX, spawnY, spawnZ)),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipStray(stray, world);
            world.spawnEntity(stray);
        } else if (random.nextBoolean()) {
            // 50%概率生成骷髅
            SkeletonEntity skeleton = new SkeletonEntity(EntityType.SKELETON, world);
            skeleton.refreshPositionAndAngles(spawnX, spawnY, spawnZ, original.getYaw(), original.getPitch());
            skeleton.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(spawnX, spawnY, spawnZ)),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipSkeleton(skeleton, world);
            world.spawnEntity(skeleton);
        } else {
            // 50%概率生成女巫
            WitchEntity witch = new WitchEntity(EntityType.WITCH, world);
            witch.refreshPositionAndAngles(spawnX, spawnY, spawnZ, original.getYaw(), original.getPitch());
            witch.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(spawnX, spawnY, spawnZ)),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.applyWitchEffects(witch);
            world.spawnEntity(witch);
        }

        // 取消原始实体的生成
        cir.setReturnValue(false);
    }

    /**
     * 主世界水中生物替换逻辑：50%守卫者 / 50%溺尸
     * 守卫者有数量上限（30只），超过上限则直接取消生成
     *
     * @param world 服务端世界
     * @param original 原始被替换的实体
     * @param cir 回调信息，用于取消原始生成
     */
    private void spawnWaterReplacementAndCancel(ServerWorld world, Entity original, CallbackInfoReturnable<Boolean> cir) {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        var pos = original.getPos();

        // 50%守卫者 / 50%溺尸
        if (random.nextBoolean()) {
            // 守卫者（受数量上限约束）
            int guardianCount = 0;
            for (var entity : world.iterateEntities()) {
                if (entity instanceof GuardianEntity) guardianCount++;
            }
            // 守卫者数量达到上限时直接取消生成，不生成替代品
            if (guardianCount >= 30) {
                cir.setReturnValue(false);
                return;
            }
            GuardianEntity guardian = new GuardianEntity(EntityType.GUARDIAN, world);
            guardian.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            guardian.initialize(world, world.getLocalDifficulty(guardian.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            world.spawnEntity(guardian);
        } else {
            // 溺尸
            DrownedEntity drowned = new DrownedEntity(EntityType.DROWNED, world);
            drowned.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            drowned.initialize(world, world.getLocalDifficulty(drowned.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            ArcherMobHelper.equipDrowned(drowned, world);
            world.spawnEntity(drowned);
        }
        cir.setReturnValue(false);
    }

    /**
     * 地狱生物替换逻辑：50%烈焰人 / 50%恶魂
     *
     * @param world 服务端世界
     * @param original 原始被替换的实体
     * @param cir 回调信息，用于取消原始生成
     */
    private void spawnNetherReplacementAndCancel(ServerWorld world, Entity original, CallbackInfoReturnable<Boolean> cir) {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        var pos = original.getPos();

        if (random.nextBoolean()) {
            // 50%概率生成烈焰人
            BlazeEntity blaze = new BlazeEntity(EntityType.BLAZE, world);
            blaze.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            blaze.initialize(world, world.getLocalDifficulty(blaze.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            world.spawnEntity(blaze);
        } else {
            // 50%概率生成恶魂
            GhastEntity ghast = new GhastEntity(EntityType.GHAST, world);
            ghast.refreshPositionAndAngles(pos.x, pos.y, pos.z, original.getYaw(), original.getPitch());
            ghast.initialize(world, world.getLocalDifficulty(ghast.getBlockPos()),
                net.minecraft.entity.SpawnReason.COMMAND, null);
            world.spawnEntity(ghast);
        }

        cir.setReturnValue(false);
    }
}
