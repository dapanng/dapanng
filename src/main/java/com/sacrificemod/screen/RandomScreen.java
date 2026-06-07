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
 * 全随机玩法参数配置界面
 * <p>
 * 提供全随机玩法的启动/停止控制，以及重新打乱时间间隔的调整。
 * 全随机玩法下，所有合成配方被打乱，杀怪和挖掘的掉落物被打乱，
 * 每过一定时间会重新打乱一次。
 * 界面还显示下次随机化的倒计时。
 */
public class RandomScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 300;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 220;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u5168\u968f\u673a\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u6240\u6709\u5408\u6210\u914d\u65b9\u88ab\u6253\u4e71",
            "2. \u6740\u6740\u751f\u7269\u548c\u6316\u6398\u65b9\u5757\u7684\u6389\u843d\u7269\u88ab\u6253\u4e71",
            "3. \u6bcf\u8fc7\u4e00\u5b9a\u65f6\u95f4\u4f1a\u91cd\u65b0\u6253\u4e71\u4e00\u6b21",
            "4. \u91cd\u65b0\u6253\u4e71\u7684\u65f6\u95f4\u53ef\u5728UI\u754c\u9762\u8c03\u6574"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;
    /** 重新打乱时间间隔(分钟)输入框 */
    private TextFieldWidget randomizeIntervalField;

    /**
     * 构造全随机玩法参数界面
     */
    public RandomScreen() {
        super(Text.translatable("gui.sacrificemod.random_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮、时间间隔输入框、应用按钮
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
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "random"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "random"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 重新打乱时间间隔输入框
        int labelX = startX + 15;
        int fieldX = startX + 140;
        int fieldW = 60;
        int rowStartY = startY + 74;

        this.randomizeIntervalField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY, fieldW, btnH, Text.literal(""));
        this.randomizeIntervalField.setMaxLength(3);
        this.randomizeIntervalField.setText(String.valueOf(ClientData.randomizeIntervalMinutes));
        this.randomizeIntervalField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(this.randomizeIntervalField);

        // 应用参数按钮
        int confirmY = rowStartY + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_params"), button -> {
            applyParams();
        }).dimensions(centerX - 60, confirmY, 120, btnH).build());
    }

    /**
     * 应用重新打乱时间间隔参数，发送网络包给服务端
     * 间隔必须大于等于1分钟
     */
    private void applyParams() {
        try {
            int minutes = this.randomizeIntervalField.getText().isEmpty() ? ClientData.randomizeIntervalMinutes : Integer.parseInt(this.randomizeIntervalField.getText());
            if (minutes >= 1) {
                ClientPlayNetworking.send(new ModPackets.SetRandomizeIntervalPayload(minutes));
            }
        } catch (NumberFormatException ignored) {
            // 输入格式错误时静默忽略
        }
    }

    /**
     * 渲染界面：背景面板、标题、状态、参数标签、下次随机化倒计时
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
        String statusText = "random".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 状态下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 68, 0xFF555555);

        // 绘制参数标签
        int labelX = startX + 15;
        int rowStartY = startY + 74;

        context.drawTextWithShadow(this.textRenderer, "\u91cd\u65b0\u6253\u4e71\u65f6\u95f4(\u5206\u949f):", labelX, rowStartY + 6, 0xFFFFFF);

        // 显示下次随机化倒计时
        if ("random".equals(ClientData.activeGameplay) && this.client != null && this.client.player != null) {
            long remaining = ClientData.randomizeRemainingTicks;

            // 将tick转换为分:秒格式（20tick = 1秒）
            int remainingSeconds = (int) (remaining / 20);
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;

            String timeText = String.format("\u00a7e\u4e0b\u6b21\u968f\u673a\u5316: \u00a7f%d:%02d", minutes, seconds);
            context.drawCenteredTextWithShadow(this.textRenderer, timeText, centerX, startY + 130, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
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
