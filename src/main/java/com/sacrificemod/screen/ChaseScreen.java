package com.sacrificemod.screen;

import com.sacrificemod.ClientData;
import com.sacrificemod.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 追击玩法参数配置界面
 * <p>
 * 提供追击玩法的启动/停止控制。
 * 追击玩法下，500格内所有生物会追杀玩家，和平生物也会获得攻击伤害并追击玩家。
 * 停止玩法后生物恢复正常。
 */
public class ChaseScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 300;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 240;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u8ffd\u51fb\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. 500\u683c\u5185\u6240\u6709\u751f\u7269\u4f1a\u8ffd\u6740\u73a9\u5bb6",
            "2. \u548c\u5e73\u751f\u7269\u4e5f\u4f1a\u83b7\u5f97\u653b\u51fb\u4f24\u5bb3\u5e76\u8ffd\u51fb\u73a9\u5bb6",
            "3. \u505c\u6b62\u73a9\u6cd5\u540e\u751f\u7269\u6062\u590d\u6b63\u5e38"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;

    /** 攻击范围输入框 */
    private TextFieldWidget attackRangeField;

    /**
     * 构造追击玩法参数界面
     */
    public ChaseScreen() {
        super(Text.translatable("gui.sacrificemod.chase_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮
     */
    @Override
    protected void init() {
        super.init();

        int startX = (this.width - BG_WIDTH) / 2;
        int startY = (this.height - BG_HEIGHT) / 2;
        int centerX = this.width / 2;
        int btnW = 120;
        int btnH = 20;

        // 右上角按钮：规则说明 + 返回
        int topBtnY = startY + 8;
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.info"),
                button -> { showInfo = !showInfo; this.clearAndInit(); }
        ).dimensions(startX + BG_WIDTH - 130, topBtnY, 60, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.back"),
                button -> this.client.setScreen(new GameplayHomeScreen())
        ).dimensions(startX + BG_WIDTH - 65, topBtnY, 55, btnH).build());

        // 显示规则说明时隐藏其他控件
        if (showInfo) return;

        // 启动 / 停止按钮
        int toggleY = startY + 34;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.start"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "chase"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "chase"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 攻击范围输入框
        int fieldX = centerX - 50;
        int fieldY = toggleY + 35;
        this.attackRangeField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, 100, 20, Text.literal(""));
        this.attackRangeField.setMaxLength(5);
        this.attackRangeField.setText(String.valueOf(ClientData.chaseAttackRange));
        this.addDrawableChild(this.attackRangeField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("应用"), button -> {
            applyChaseParams();
        }).dimensions(centerX + 60, fieldY, 60, 20).build());
    }

    /**
     * 渲染界面：背景面板、标题、状态
     *
     * @param context 绘图上下文
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param delta 帧间隔时间
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int startX = (this.width - BG_WIDTH) / 2;
        int startY = (this.height - BG_HEIGHT) / 2;
        int centerX = this.width / 2;

        // 绘制半透明黑色背景面板
        context.fill(startX, startY, startX + BG_WIDTH, startY + BG_HEIGHT, 0xC0000000);
        context.drawBorder(startX, startY, BG_WIDTH, BG_HEIGHT, 0xFF555555);

        // 绘制标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.getString(), centerX, startY + 10, 0xFF5555);

        // 标题下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 26, 0xFF555555);

        // 显示规则说明页面
        if (showInfo) {
            for (int i = 0; i < INFO_LINES.length; i++) {
                context.drawTextWithShadow(this.textRenderer, INFO_LINES[i], startX + 20, startY + 35 + i * 16, 0xFFFFFF);
            }
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // 显示玩法状态（已启用/未启用）
        String statusText = "chase".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 攻击范围标签
        int fieldX = centerX - 50;
        int fieldY = startY + 34 + 35;
        context.drawTextWithShadow(this.textRenderer, "\u653b\u51fb\u8303\u56f4:", fieldX - 65, fieldY + 3, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 应用追击玩法参数到服务端
     */
    private void applyChaseParams() {
        try {
            double attackRange = Double.parseDouble(attackRangeField.getText().trim());
            ClientData.chaseAttackRange = Math.max(0.5, Math.min(32.0, attackRange));
            ClientPlayNetworking.send(new ModPackets.SetChaseParamsPayload(ClientData.chaseAttackRange));
        } catch (NumberFormatException ignored) {}
    }

    /**
     * 该界面不暂停游戏
     *
     * @return false，不暂停游戏
     */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
