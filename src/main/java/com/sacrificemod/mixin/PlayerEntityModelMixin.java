package com.sacrificemod.mixin;

import com.sacrificemod.BodyPart;
import com.sacrificemod.ClientData;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 玩家实体模型Mixin：根据祭祀玩法中失去的身体部位隐藏对应的外层皮肤部件
 * <p>
 * 与BipedEntityModelMixin配合使用，BipedEntityModelMixin处理内层模型，
 * 本Mixin处理外层皮肤（袖子、裤腿），确保3D皮肤层也正确隐藏。
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> {

    /** 左袖外层皮肤部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart leftSleeve;

    /** 右袖外层皮肤部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart rightSleeve;

    /** 左裤腿外层皮肤部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart leftPants;

    /** 右裤腿外层皮肤部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart rightPants;

    /**
     * 在模型设置角度后，根据玩家失去的身体部位隐藏对应的外层皮肤部件
     *
     * @param entity 对应的实体
     * @param limbAngle 肢体摆动角度
     * @param limbDistance 肢体摆动距离
     * @param animationProgress 动画进度
     * @param headYaw 头部偏航角
     * @param headPitch 头部俯仰角
     * @param ci 回调信息
     */
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void onSetAngles(T entity, float limbAngle, float limbDistance, float animationProgress,
                             float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player) {
            // 从客户端缓存获取玩家失去的身体部位
            Set<BodyPart> lostParts = ClientData.getLostParts(player.getUuid());

            boolean lostLeftHand = lostParts.contains(BodyPart.LEFT_HAND);
            boolean lostRightHand = lostParts.contains(BodyPart.RIGHT_HAND);
            boolean lostLeftLeg = lostParts.contains(BodyPart.LEFT_LEG);
            boolean lostRightLeg = lostParts.contains(BodyPart.RIGHT_LEG);

            // 外层皮肤部位（3D Skin Layers等mod渲染的3D层）
            // 失去对应手时隐藏袖子
            leftSleeve.visible = !lostLeftHand;
            rightSleeve.visible = !lostRightHand;
            // 失去对应腿时隐藏裤腿
            leftPants.visible = !lostLeftLeg;
            rightPants.visible = !lostRightLeg;
        }
    }
}
