package com.sacrificemod.screen;

import com.sacrificemod.ClientData;
import com.sacrificemod.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 残障玩法参数配置界面
 * <p>
 * 提供残障玩法的启动/停止控制，以及禁用概率和受伤阈值的调整。
 * 残障玩法下，玩家受伤达到阈值时按概率禁用背包格子，被禁用格子上的物品自动丢出。
 * 界面还显示当前已禁用的格子数量和索引列表。
 */
public class DisabledScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 300;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 280;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u6b8b\u75be\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u6bcf\u6b21\u53d7\u4f24\u8fbe\u5230\u9608\u503c\u65f6\u6309\u6982\u7387\u7981\u7528\u4e00\u4e2a\u80cc\u5305\u683c\u5b50",
            "2. \u88ab\u7981\u7528\u683c\u5b50\u4e0a\u7684\u7269\u54c1\u81ea\u52a8\u4e22\u51fa",
            "3. \u88ab\u7981\u7528\u7684\u683c\u5b50\u65e0\u6cd5\u653e\u7f6e\u7269\u54c1"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;

    /** 禁用概率(%)输入框 */
    private TextFieldWidget disabledChanceField;
    /** 受伤阈值输入框 */
    private TextFieldWidget disabledHurtThresholdField;

    /**
     * 构造残障玩法参数界面
     */
    public DisabledScreen() {
        super(Text.translatable("gui.sacrificemod.disabled_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮、禁用概率和受伤阈值输入框、应用按钮
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
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "disabled"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "disabled"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 参数输入框
        int labelX = startX + 15;
        int fieldX = startX + 140;
        int fieldW = 60;
        int rowStartY = startY + 74;
        int rowSpacing = 24;

        // 禁用概率(%)输入框
        this.disabledChanceField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY, fieldW, btnH, Text.literal(""));
        this.disabledChanceField.setMaxLength(3);
        this.disabledChanceField.setText(String.valueOf(ClientData.disabledChance));
        this.disabledChanceField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(this.disabledChanceField);

        // 受伤阈值输入框
        this.disabledHurtThresholdField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing, fieldW, btnH, Text.literal(""));
        this.disabledHurtThresholdField.setMaxLength(5);
        this.disabledHurtThresholdField.setText(String.valueOf(ClientData.disabledHurtThreshold));
        this.disabledHurtThresholdField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(this.disabledHurtThresholdField);

        // 应用参数按钮
        int confirmY = rowStartY + rowSpacing * 2 + 5;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_disabled_params"), button -> {
            applyDisabledParams();
        }).dimensions(centerX - 60, confirmY, 120, btnH).build());
    }

    /**
     * 应用残障玩法参数，发送网络包给服务端
     * 包括禁用概率和受伤阈值
     */
    private void applyDisabledParams() {
        try {
            int chance = this.disabledChanceField.getText().isEmpty() ? ClientData.disabledChance : Integer.parseInt(this.disabledChanceField.getText());
            int threshold = this.disabledHurtThresholdField.getText().isEmpty() ? ClientData.disabledHurtThreshold : Integer.parseInt(this.disabledHurtThresholdField.getText());

            ClientPlayNetworking.send(new ModPackets.SetDisabledParamsPayload(chance, threshold));
        } catch (NumberFormatException ignored) {
            // 输入格式错误时静默忽略
        }
    }

    /**
     * 渲染界面：背景面板、标题、状态、参数标签、已禁用格子信息
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
        String statusText = "disabled".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 状态下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 68, 0xFF555555);

        // 绘制参数标签
        int labelX = startX + 15;
        int rowStartY = startY + 74;
        int rowSpacing = 24;

        context.drawTextWithShadow(this.textRenderer, "\u7981\u7528\u6982\u7387(%):", labelX, rowStartY + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u53d7\u4f24\u9608\u503c:", labelX, rowStartY + rowSpacing + 6, 0xFFFFFF);

        // 禁用格子信息区域分隔线
        int slotInfoSepY = startY + 155;
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, slotInfoSepY, 0xFF555555);

        // 获取当前玩家已禁用的格子信息
        UUID playerUuid = null;
        if (this.client != null && this.client.player != null) {
            playerUuid = this.client.player.getUuid();
        }

        Set<Integer> myDisabledSlots = playerUuid != null ? ClientData.getDisabledSlots(playerUuid) : Set.of();
        int slotCount = myDisabledSlots.size();

        // 显示已禁用格子数量
        int infoY = slotInfoSepY + 6;
        context.drawTextWithShadow(this.textRenderer, "\u00a7e\u5df2\u7981\u7528\u683c\u5b50\u6570: \u00a7c" + slotCount, startX + 20, infoY, 0xFFFFFF);

        // 列出已禁用格子的索引
        if (!myDisabledSlots.isEmpty()) {
            List<Integer> sortedSlots = new ArrayList<>(myDisabledSlots);
            sortedSlots.sort(Integer::compareTo);

            int lineY = infoY + 18;
            int slotsPerLine = 12; // 每行最多显示12个格子索引
            int lineCount = (sortedSlots.size() + slotsPerLine - 1) / slotsPerLine;

            for (int line = 0; line < lineCount; line++) {
                StringBuilder sb = new StringBuilder();
                int start = line * slotsPerLine;
                int end = Math.min(start + slotsPerLine, sortedSlots.size());
                for (int i = start; i < end; i++) {
                    if (i > start) sb.append(", ");
                    sb.append(sortedSlots.get(i));
                }
                context.drawTextWithShadow(this.textRenderer, sb.toString(), startX + 20, lineY + line * 14, 0xFFAA00);
            }
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
