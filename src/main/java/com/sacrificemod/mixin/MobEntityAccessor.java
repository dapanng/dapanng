package com.sacrificemod.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 生物实体访问器接口：通过Accessor暴露MobEntity的私有字段
 * <p>
 * 用于获取生物的目标选择器和行为选择器，以便在玩法中动态修改生物AI
 * （例如追击玩法中为和平生物添加追击玩家的目标）
 */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

    /**
     * 获取生物的行为目标选择器（goalSelector）
     * 包含生物的基础行为，如游荡、看向玩家等
     *
     * @return 行为目标选择器
     */
    @Accessor("goalSelector")
    GoalSelector sacrificemod$getGoalSelector();

    /**
     * 获取生物的攻击目标选择器（targetSelector）
     * 包含生物的目标选择逻辑，如攻击最近玩家等
     *
     * @return 攻击目标选择器
     */
    @Accessor("targetSelector")
    GoalSelector sacrificemod$getTargetSelector();
}
