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
 * 加速玩法参数配置界面
 * <p>
 * 提供加速玩法的启动/停止控制，以及加速倍率的调整。
 * 加速玩法下，除玩家以外的所有生物行动速度加快，攻击间隔也相应缩短。
 */
public class SpeedScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 300;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 200;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u52a0\u901f\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u9664\u73a9\u5bb6\u4ee5\u5916\u7684\u6240\u6709\u751f\u7269\u884c\u52a8\u901f\u5ea6\u52a0\u5feb",
            "2. \u751f\u7269\u7684\u653b\u51fb\u95f4\u9694\u4e5f\u4f1a\u76f8\u5e94\u7f29\u77ed",
            "3. \u52a0\u901f\u500d\u7387\u53ef\u5728UI\u754c\u9762\u8fdb\u884c\u8c03\u6574"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;
    /** 加速倍率输入框 */
    private TextFieldWidget speedMultiplierField;

    /**
     * 构造加速玩法参数界面
     */
    public SpeedScreen() {
        super(Text.translatable("gui.sacrificemod.speed_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮、加速倍率输入框、应用按钮
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
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "speed"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "speed"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 加速倍率输入框
        int labelX = startX + 15;
        int fieldX = startX + 140;
        int fieldW = 60;
        int rowStartY = startY + 74;

        this.speedMultiplierField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY, fieldW, btnH, Text.literal(""));
        this.speedMultiplierField.setMaxLength(6);
        this.speedMultiplierField.setText(String.valueOf(ClientData.speedMultiplier));
        // 允许输入小数
        this.speedMultiplierField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.speedMultiplierField);

        // 应用参数按钮
        int confirmY = rowStartY + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_params"), button -> {
            applyParams();
        }).dimensions(centerX - 60, confirmY, 120, btnH).build());
    }

    /**
     * 应用加速倍率参数，发送网络包给服务端
     * 倍率必须大于等于1
     */
    private void applyParams() {
        try {
            float multiplier = this.speedMultiplierField.getText().isEmpty() ? ClientData.speedMultiplier : Float.parseFloat(this.speedMultiplierField.getText());
            if (multiplier >= 1) {
                ClientPlayNetworking.send(new ModPackets.SetSpeedMultiplierPayload(multiplier));
            }
        } catch (NumberFormatException ignored) {
            // 输入格式错误时静默忽略
        }
    }

    /**
     * 渲染界面：背景面板、标题、状态、参数标签
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
        String statusText = "speed".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 状态下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 68, 0xFF555555);

        // 绘制参数标签
        int labelX = startX + 15;
        int rowStartY = startY + 74;

        context.drawTextWithShadow(this.textRenderer, "\u52a0\u901f\u500d\u7387:", labelX, rowStartY + 6, 0xFFFFFF);

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
