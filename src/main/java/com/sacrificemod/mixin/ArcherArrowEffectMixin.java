package com.sacrificemod.mixin;

import com.sacrificemod.GameState;
import com.sacrificemod.SacrificeMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 弓箭手大作战：骷髅箭有概率带TNT跟随，流浪者箭有概率带凋零效果
 * <p>
 * 核心机制：
 * - 骷髅射出的箭矢有概率携带TNT，TNT会跟随箭矢飞行，箭矢落地后TNT爆炸
 * - 流浪者射出的箭矢有概率携带凋零效果，箭矢落地后对附近生物施加凋零
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class ArcherArrowEffectMixin {

    /** 跟踪需要TNT跟随的箭矢 (箭矢UUID -> TNT实体ID, -1表示待生成) */
    private static final Map<UUID, Integer> tntArrowMap = new ConcurrentHashMap<>();
    /** 跟踪需要凋零效果的箭矢集合 */
    private static final Set<UUID> witherArrowSet = ConcurrentHashMap.newKeySet();
    /** 已处理的箭矢集合，防止重复处理 */
    private static final Set<UUID> processedArrows = ConcurrentHashMap.newKeySet();

    /**
     * 箭矢tick头部注入：检测新射出的骷髅/流浪者箭矢，按概率标记TNT或凋零效果
     *
     * @param ci 回调信息
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        // 仅在服务端处理
        if (!(self.getWorld() instanceof ServerWorld serverWorld)) return;
        // 仅在弓箭手大作战玩法激活时生效
        if (!"archer".equals(SacrificeMod.getActiveGameplayId())) return;

        UUID arrowUuid = self.getUuid();

        // 每支箭矢只处理一次
        if (processedArrows.contains(arrowUuid)) return;

        Entity owner = self.getOwner();
        if (owner == null) return;

        // 仅处理刚射出（未落地且年龄<=5tick）的骷髅/流浪者箭矢
        if (!self.isOnGround() && self.age <= 5) {
            var server = serverWorld.getServer();
            var state = GameState.getServerState(server);

            if (owner instanceof SkeletonEntity && !(owner instanceof StrayEntity)) {
                // 骷髅箭矢 - 按概率标记TNT跟随
                int chance = state.getArcherSkeletonTntChance();
                if (chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance) {
                    tntArrowMap.put(arrowUuid, -1); // -1表示TNT尚未生成
                }
                processedArrows.add(arrowUuid);
            } else if (owner instanceof StrayEntity) {
                // 流浪者箭矢 - 按概率标记凋零效果
                int chance = state.getArcherStrayWitherChance();
                if (chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance) {
                    witherArrowSet.add(arrowUuid);
                }
                processedArrows.add(arrowUuid);
            }
        }
    }

    /**
     * 箭矢tick尾部注入：处理TNT跟随和凋零效果逻辑
     * - TNT箭矢：飞行中TNT跟随箭矢移动，落地后TNT瞬爆
     * - 凋零箭矢：落地后对附近非骷髅/流浪者生物施加凋零效果
     *
     * @param ci 回调信息
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        // 仅在弓箭手大作战玩法激活时生效
        if (!"archer".equals(SacrificeMod.getActiveGameplayId())) return;

        UUID arrowUuid = self.getUuid();

        // 处理TNT跟随逻辑
        if (tntArrowMap.containsKey(arrowUuid)) {
            if (self.isOnGround() || self.isRemoved()) {
                // 箭矢落地或被移除 - 触发TNT爆炸
                Integer tntId = tntArrowMap.remove(arrowUuid);
                if (tntId != null && tntId > 0) {
                    var tnt = self.getWorld().getEntityById(tntId);
                    if (tnt instanceof TntEntity tntEntity) {
                        // 将TNT移动到箭矢最终位置并立即引爆
                        tntEntity.setPosition(self.getX(), self.getY(), self.getZ());
                        tntEntity.setFuse(0);
                    }
                }
                // 清理已处理集合，防止内存泄漏
                processedArrows.remove(arrowUuid);
            } else {
                // 箭矢仍在飞行中 - 管理TNT跟随
                Integer tntId = tntArrowMap.get(arrowUuid);
                if (tntId == null || tntId == -1) {
                    // 首次检测到TNT箭矢，生成新的TNT实体
                    TntEntity tnt = new TntEntity(EntityType.TNT, self.getWorld());
                    tnt.refreshPositionAndAngles(self.getX(), self.getY(), self.getZ(), 0, 0);
                    tnt.setFuse(80); // 4秒引信 - 足够箭矢飞行
                    tnt.setNoGravity(true); // 取消重力，使TNT能跟随箭矢
                    self.getWorld().spawnEntity(tnt);
                    tntArrowMap.put(arrowUuid, tnt.getId());
                } else {
                    // TNT已存在，移动TNT跟随箭矢位置
                    var tnt = self.getWorld().getEntityById(tntId);
                    if (tnt != null) {
                        tnt.setPosition(self.getX(), self.getY(), self.getZ());
                    } else {
                        // TNT已被移除（可能已爆炸）- 清理追踪数据
                        tntArrowMap.remove(arrowUuid);
                        processedArrows.remove(arrowUuid);
                    }
                }
            }
        }

        // 处理凋零效果 - 箭矢落地或被移除时触发
        if (witherArrowSet.contains(arrowUuid)) {
            if (self.isOnGround() || self.isRemoved()) {
                // 对箭矢附近3格范围内的非骷髅/流浪者生物施加凋零效果
                var nearbyEntities = self.getWorld().getEntitiesByClass(
                    LivingEntity.class,
                    net.minecraft.util.math.Box.of(self.getPos(), 3, 3, 3),
                    e -> !(e instanceof SkeletonEntity) && !(e instanceof StrayEntity)
                );
                for (var entity : nearbyEntities) {
                    // 施加凋零效果：持续3秒（60tick），等级I，无环境粒子，有图标
                    entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0, false, true));
                }
                witherArrowSet.remove(arrowUuid);
                processedArrows.remove(arrowUuid);
            }
        }
    }
}
