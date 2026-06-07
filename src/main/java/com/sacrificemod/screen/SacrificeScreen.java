package com.sacrificemod.screen;

import com.sacrificemod.BodyPart;
import com.sacrificemod.ClientData;
import com.sacrificemod.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 祭祀玩法参数配置界面
 * <p>
 * 提供祭祀玩法的启动/停止控制、参数调整，以及身体部位献祭/赎回操作：
 * - 跳跃阈值：跳跃次数达到阈值时召唤敌对生物
 * - 赎回花费：赎回身体部位所需的钻石数量
 * - 身体部位献祭/赎回按钮
 * - 实时显示共享生命、饱食度、跳跃计数、身体部位状态、抽奖结果
 */
public class SacrificeScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 380;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 280;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u732e\u796d\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u6240\u6709\u73a9\u5bb6\u5171\u4eab\u8840\u91cf\u548c\u9971\u98df\u5ea6",
            "2. \u8df3\u8dc3\u6b21\u6570\u8fbe\u5230\u9608\u503c\u65f6\u53ec\u552420\u53ea\u968f\u673a\u654c\u5bf9\u751f\u7269",
            "3. \u6bcf\u6b21\u6b7b\u4ea1\u51cf\u5c112\u70b9\u751f\u547d\u4e0a\u9650",
            "4. \u53ef\u732e\u796d\u8eab\u4f53\u90e8\u4f4d\u8fdb\u884c\u62bd\u5956",
            "5. \u5956\u6c60\uff1a-2/-4/+2/+4\u6700\u5927\u751f\u547d",
            "6. \u732e\u796d\u90e8\u4f4d\u540e\u5bf9\u5e94\u76ae\u80a4\u7eb9\u7406\u6d88\u5931\u5e76\u83b7\u5f97debuff",
            "7. \u6d88\u8017\u94bb\u77f3\u53ef\u8d4e\u56de\u90e8\u4f4d\u5e76\u6d88\u9664debuff"
    };

    /** 跳跃阈值输入框 */
    private TextFieldWidget jumpThresholdField;
    /** 赎回花费输入框 */
    private TextFieldWidget reclaimCostField;
    /** 是否显示规则说明页面 */
    private boolean showInfo = false;

    /**
     * 构造祭祀玩法参数界面
     */
    public SacrificeScreen() {
        super(Text.translatable("gui.sacrificemod.sacrifice_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮、参数输入框、身体部位献祭/赎回按钮
     */
    @Override
    protected void init() {
        super.init();

        int startX = (this.width - BG_WIDTH) / 2;
        int startY = (this.height - BG_HEIGHT) / 2;
        int centerX = this.width / 2;
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
        int row1Y = startY + 34;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.start"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "sacrifice"));
        }).dimensions(centerX - 125, row1Y, 120, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "sacrifice"));
        }).dimensions(centerX + 5, row1Y, 120, btnH).build());

        // 参数输入区域：跳跃阈值和赎回花费
        int paramStartY = row1Y + 28;
        int fieldX = centerX - 10;
        int fieldW = 60;

        // 跳跃阈值输入框
        this.jumpThresholdField = new TextFieldWidget(this.textRenderer, fieldX, paramStartY, fieldW, btnH, Text.literal(""));
        this.jumpThresholdField.setMaxLength(5);
        this.jumpThresholdField.setText(String.valueOf(ClientData.jumpThreshold));
        this.jumpThresholdField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(this.jumpThresholdField);

        // 应用跳跃阈值按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_threshold"), button -> {
            applyJumpThreshold();
        }).dimensions(fieldX + fieldW + 5, paramStartY, 50, btnH).build());

        // 赎回花费输入框
        int costY = paramStartY + 24;
        this.reclaimCostField = new TextFieldWidget(this.textRenderer, fieldX, costY, fieldW, btnH, Text.literal(""));
        this.reclaimCostField.setMaxLength(5);
        this.reclaimCostField.setText(String.valueOf(ClientData.reclaimCost));
        this.reclaimCostField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(this.reclaimCostField);

        // 应用赎回花费按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_reclaim_cost"), button -> {
            applyReclaimCost();
        }).dimensions(fieldX + fieldW + 5, costY, 50, btnH).build());

        // 左侧列：身体部位献祭/赎回按钮
        int leftX = startX + 15;
        int partStartY = startY + 112;
        int partSpacing = 22;
        BodyPart[] parts = BodyPart.values();

        // 获取当前玩家UUID用于判断部位状态
        UUID playerUuid = null;
        if (this.client != null && this.client.player != null) {
            playerUuid = this.client.player.getUuid();
        }

        // 为每个身体部位创建献祭或赎回按钮
        for (int i = 0; i < parts.length; i++) {
            BodyPart part = parts[i];
            int y = partStartY + i * partSpacing;
            boolean lost = playerUuid != null && ClientData.getLostParts(playerUuid).contains(part);

            if (!lost) {
                // 部位完好 - 显示献祭按钮
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.sacrificemod.sacrifice").append(" ").append(part.getDisplayName()),
                        button -> {
                            ClientPlayNetworking.send(new ModPackets.SacrificeRequestPayload(part.ordinal()));
                            this.close();
                        }
                ).dimensions(leftX, y, 120, btnH).build());
            } else {
                // 部位已失去 - 显示赎回按钮（含花费）
                this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.sacrificemod.reclaim").append(" ").append(part.getDisplayName()).append(" (").append(String.valueOf(ClientData.reclaimCost)).append("\ud83d\udc8e)"),
                        button -> {
                            ClientPlayNetworking.send(new ModPackets.ReclaimRequestPayload(part.ordinal()));
                            this.close();
                        }
                ).dimensions(leftX, y, 140, btnH).build());
            }
        }
    }

    /**
     * 应用跳跃阈值参数，发送网络包给服务端
     * 值必须大于等于1
     */
    private void applyJumpThreshold() {
        String text = this.jumpThresholdField.getText();
        if (text.isEmpty()) return;
        try {
            int value = Integer.parseInt(text);
            if (value >= 1) ClientPlayNetworking.send(new ModPackets.SetJumpThresholdPayload(value));
        } catch (NumberFormatException ignored) {}
    }

    /**
     * 应用赎回花费参数，发送网络包给服务端
     * 值必须大于等于1
     */
    private void applyReclaimCost() {
        String text = this.reclaimCostField.getText();
        if (text.isEmpty()) return;
        try {
            int value = Integer.parseInt(text);
            if (value >= 1) ClientPlayNetworking.send(new ModPackets.SetReclaimCostPayload(value));
        } catch (NumberFormatException ignored) {}
    }

    /**
     * 渲染界面：背景面板、标题、参数标签、状态信息、身体部位状态、抽奖结果
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

        // 绘制参数标签
        int fieldX = centerX - 10;
        int paramStartY = startY + 62;
        context.drawTextWithShadow(this.textRenderer, "\u8df3\u8dc3\u9608\u503c:", fieldX - 65, paramStartY + 5, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u8d4e\u56de\u82b1\u8d39:", fieldX - 65, paramStartY + 29, 0xFFFFFF);

        // 参数区域与身体部位区域之间的分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 102, 0xFF555555);

        // 左侧列标题
        int leftX = startX + 15;
        context.drawTextWithShadow(this.textRenderer, "\u00a7e\u732e\u796d\u90e8\u4f4d:", leftX, startY + 106, 0xFFAA00);

        // 右侧列：状态信息
        int rightX = startX + 180;
        int infoY = startY + 106;

        // 玩法状态
        String statusText = ClientData.activeGameplay != null && ClientData.activeGameplay.equals("sacrifice")
                ? "\u00a7a\u5df2\u542f\u7528" : "\u00a7c\u672a\u542f\u7528";
        context.drawTextWithShadow(this.textRenderer, "\u00a7e\u73a9\u6cd5\u72b6\u6001: " + statusText, rightX, infoY, 0xFFFFFF);
        // 共享生命值
        context.drawTextWithShadow(this.textRenderer,
                String.format("\u5171\u4eab\u751f\u547d: \u00a7c%.1f \u00a7f/ \u00a7a%.1f", ClientData.sharedHealth, ClientData.sharedMaxHealth),
                rightX, infoY + 14, 0xFFFFFF);
        // 共享饱食度
        context.drawTextWithShadow(this.textRenderer,
                String.format("\u5171\u4eab\u9971\u98df\u5ea6: \u00a7e%d \u00a7f/ 20", ClientData.sharedHunger),
                rightX, infoY + 28, 0xFFFFFF);
        // 跳跃计数
        context.drawTextWithShadow(this.textRenderer,
                String.format("\u8df3\u8dc3\u8ba1\u6570: \u00a7b%d \u00a7f/ %d", ClientData.jumpCount, ClientData.jumpThreshold),
                rightX, infoY + 42, 0xFFFFFF);

        // 状态信息下方分隔线
        context.drawHorizontalLine(rightX, rightX + 170, infoY + 56, 0xFF555555);

        // 身体部位状态显示
        UUID playerUuid = null;
        if (this.client != null && this.client.player != null) {
            playerUuid = this.client.player.getUuid();
        }

        int partInfoY = infoY + 62;
        context.drawTextWithShadow(this.textRenderer, "\u00a7e\u8eab\u4f53\u90e8\u4f4d\u72b6\u6001:", rightX, partInfoY, 0xFFAA00);
        BodyPart[] parts = BodyPart.values();
        for (int i = 0; i < parts.length; i++) {
            boolean lost = playerUuid != null && ClientData.getLostParts(playerUuid).contains(parts[i]);
            String partStatus = parts[i].getDisplayName() + ": " + (lost ? "\u00a7c\u5df2\u5931\u53bb" : "\u00a7a\u5b8c\u597d");
            context.drawTextWithShadow(this.textRenderer, partStatus, rightX, partInfoY + 14 + i * 12, 0xFFFFFF);
        }

        // 上次抽奖结果显示
        if (ClientData.lastLotteryResult > 0 && ClientData.lastLotteryPart != null) {
            int lotteryY = partInfoY + 14 + parts.length * 12 + 6;
            context.drawHorizontalLine(rightX, rightX + 170, lotteryY - 3, 0xFF555555);
            context.drawTextWithShadow(this.textRenderer, "\u00a7e\u4e0a\u6b21\u62bd\u5956\u7ed3\u679c:", rightX, lotteryY, 0xFFAA00);
            // 根据抽奖结果类型显示不同文本
            String resultText = switch (ClientData.lastLotteryResult) {
                case 1 -> "\u00a7c\u6240\u6709\u73a9\u5bb6\u6700\u5927\u751f\u547d-2";
                case 2 -> "\u00a74\u6240\u6709\u73a9\u5bb6\u6700\u5927\u751f\u547d-4";
                case 3 -> "\u00a7a\u6240\u6709\u73a9\u5bb6\u6700\u5927\u751f\u547d+2";
                case 4 -> "\u00a72\u6240\u6709\u73a9\u5bb6\u6700\u5927\u751f\u547d+4";
                default -> "?";
            };
            context.drawTextWithShadow(this.textRenderer, resultText, rightX, lotteryY + 12, 0xFFFFFF);
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
