package com.sacrificemod.mixin;

import com.sacrificemod.BodyPart;
import com.sacrificemod.ClientData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 双足生物模型Mixin：根据祭祀玩法中失去的身体部位隐藏对应的模型部件
 * <p>
 * 当玩家献祭了某个身体部位后，该部位的模型将不再渲染，
 * 实现视觉上"失去肢体"的效果。
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> {

    /** 头部模型部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart head;

    /** 帽子模型部件（头盔层） */
    @Shadow
    public net.minecraft.client.model.ModelPart hat;

    /** 右臂模型部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart rightArm;

    /** 左臂模型部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart leftArm;

    /** 右腿模型部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart rightLeg;

    /** 左腿模型部件 */
    @Shadow
    public net.minecraft.client.model.ModelPart leftLeg;

    /**
     * 在模型设置角度后，根据玩家失去的身体部位隐藏对应模型部件
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

            // 检查各部位是否失去
            boolean lostHead = lostParts.contains(BodyPart.HEAD);
            boolean lostLeftHand = lostParts.contains(BodyPart.LEFT_HAND);
            boolean lostRightHand = lostParts.contains(BodyPart.RIGHT_HAND);
            boolean lostLeftLeg = lostParts.contains(BodyPart.LEFT_LEG);
            boolean lostRightLeg = lostParts.contains(BodyPart.RIGHT_LEG);

            // 失去头部时隐藏头部和帽子
            head.visible = !lostHead;
            hat.visible = !lostHead;
            // 失去对应手时隐藏手臂
            leftArm.visible = !lostLeftHand;
            rightArm.visible = !lostRightHand;
            // 失去对应腿时隐藏腿部
            leftLeg.visible = !lostLeftLeg;
            rightLeg.visible = !lostRightLeg;
        }
    }
}
