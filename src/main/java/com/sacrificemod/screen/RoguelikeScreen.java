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
 * 肉鸽玩法参数配置界面
 * <p>
 * 提供肉鸽玩法的启动/停止控制，以及每次生成生物数量的调整。
 * 肉鸽玩法下，玩家每进入一个新区块时会随机生成特定数量的生物，
 * 只有杀死所有生成的生物后才能离开该区块。
 */
public class RoguelikeScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 300;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 200;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u8089\u9f8d\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u73a9\u5bb6\u6bcf\u8fdb\u5165\u4e00\u4e2a\u65b0\u533a\u5757\u65f6\uff0c\u4f1a\u968f\u673a\u751f\u6210\u7279\u5b9a\u6570\u91cf\u7684\u751f\u7269",
            "2. \u53ea\u6709\u6740\u6b7b\u6240\u6709\u751f\u6210\u7684\u751f\u7269\u540e\uff0c\u624d\u80fd\u79bb\u5f00\u8be5\u533a\u5757",
            "3. \u751f\u6210\u7684\u751f\u7269\u6765\u81ea\u81ea\u7136\u8fd9\u79cd1.21.1\u7248\u672c\u7684\u6240\u6709\u539f\u7248\u751f\u7269"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;
    /** 每次生成生物数量输入框 */
    private TextFieldWidget mobCountField;

    /**
     * 构造肉鸽玩法参数界面
     */
    public RoguelikeScreen() {
        super(Text.translatable("gui.sacrificemod.roguelike_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮、生物数量输入框、应用按钮
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
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "roguelike"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "roguelike"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 生物数量输入框
        int labelX = startX + 15;
        int fieldX = startX + 140;
        int fieldW = 60;
        int rowStartY = startY + 74;

        this.mobCountField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY, fieldW, btnH, Text.literal(""));
        this.mobCountField.setMaxLength(3);
        this.mobCountField.setText(String.valueOf(ClientData.roguelikeMobCount));
        this.mobCountField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(this.mobCountField);

        // 应用参数按钮
        int confirmY = rowStartY + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_params"), button -> {
            applyParams();
        }).dimensions(centerX - 60, confirmY, 120, btnH).build());
    }

    /**
     * 应用生物数量参数，发送网络包给服务端
     * 数量必须大于等于1
     */
    private void applyParams() {
        try {
            int mobCount = this.mobCountField.getText().isEmpty() ? ClientData.roguelikeMobCount : Integer.parseInt(this.mobCountField.getText());
            if (mobCount >= 1) {
                ClientPlayNetworking.send(new ModPackets.SetRoguelikeMobCountPayload(mobCount));
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
        String statusText = "roguelike".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 状态下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 68, 0xFF555555);

        // 绘制参数标签
        int labelX = startX + 15;
        int rowStartY = startY + 74;

        context.drawTextWithShadow(this.textRenderer, "\u6bcf\u6b21\u751f\u6210\u751f\u7269\u6570\u91cf:", labelX, rowStartY + 6, 0xFFFFFF);

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
