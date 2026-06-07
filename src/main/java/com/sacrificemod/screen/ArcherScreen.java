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
 * 弓箭手大作战参数配置界面
 * <p>
 * 提供弓箭手大作战玩法的启动/停止控制，以及各项参数的调整：
 * - 霸王弓力量/冲击等级
 * - 散弹弩多重射击/穿透等级
 * - 火焰剑锋利/火焰附加等级
 * - 增加护甲所需击杀数
 * - 骷髅箭TNT概率、流浪者箭凋零概率
 */
public class ArcherScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 340;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 385;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u5f13\u7bad\u624b\u5927\u4f5c\u6218\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u6240\u6709\u73a9\u5bb6\u83b7\u5f97\u9738\u738b\u5f13\u3001\u6563\u5f39\u5f29\u3001\u706b\u7130\u5251\u3001\u5de5\u5177\u65a7",
            "2. \u4e3b\u4e16\u754c\u751f\u7269\u88ab\u66ff\u6362\u4e3a\u9ab8\u9ac5/\u5973\u5deb/\u5b88\u536b\u8005",
            "3. \u5730\u72f1\u751f\u7269\u88ab\u66ff\u6362\u4e3a\u70c8\u7130\u4eba/\u6076\u9b42",
            "4. \u672b\u5730\u589e\u5f3a\u672b\u5f71\u9f99\uff0c\u672b\u5f71\u6c34\u6676\u4f1a\u91cd\u751f",
            "5. \u7981\u6b62\u5408\u6210\u5251/\u65a7/\u76d4\u7532",
            "6. \u6bcf\u6740\u6b7bN\u53ea\u751f\u7269\u6c38\u4e45+1\u62a4\u7532"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;
    /** 霸王弓力量等级输入框 */
    private TextFieldWidget bowPowerField;
    /** 霸王弓冲击等级输入框 */
    private TextFieldWidget bowPunchField;
    /** 散弹弩多重射击等级输入框 */
    private TextFieldWidget crossbowMultishotField;
    /** 散弹弩穿透等级输入框 */
    private TextFieldWidget crossbowPiercingField;
    /** 火焰剑锋利等级输入框 */
    private TextFieldWidget swordSharpnessField;
    /** 火焰剑火焰附加等级输入框 */
    private TextFieldWidget swordFireAspectField;
    /** 增加护甲所需击杀数输入框 */
    private TextFieldWidget killsPerArmorField;
    /** 骷髅箭TNT概率(%)输入框 */
    private TextFieldWidget skeletonTntChanceField;
    /** 流浪者箭凋零概率(%)输入框 */
    private TextFieldWidget strayWitherChanceField;
    /** 生物生成上限输入框 */
    private TextFieldWidget mobLimitField;

    /**
     * 构造弓箭手大作战参数界面
     */
    public ArcherScreen() {
        super(Text.translatable("gui.sacrificemod.archer_title"));
    }

    /**
     * 初始化界面控件：按钮、输入框等
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
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "archer"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "archer"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 参数输入框
        int labelX = startX + 15;
        int fieldX = startX + 180;
        int fieldW = 60;
        int rowStartY = startY + 74;
        int rowSpacing = 24;

        this.bowPowerField = createField(fieldX, rowStartY, fieldW, String.valueOf(ClientData.archerBowPower));
        this.bowPunchField = createField(fieldX, rowStartY + rowSpacing, fieldW, String.valueOf(ClientData.archerBowPunch));
        this.crossbowMultishotField = createField(fieldX, rowStartY + rowSpacing * 2, fieldW, String.valueOf(ClientData.archerCrossbowMultishot));
        this.crossbowPiercingField = createField(fieldX, rowStartY + rowSpacing * 3, fieldW, String.valueOf(ClientData.archerCrossbowPiercing));
        this.swordSharpnessField = createField(fieldX, rowStartY + rowSpacing * 4, fieldW, String.valueOf(ClientData.archerSwordSharpness));
        this.swordFireAspectField = createField(fieldX, rowStartY + rowSpacing * 5, fieldW, String.valueOf(ClientData.archerSwordFireAspect));
        this.killsPerArmorField = createField(fieldX, rowStartY + rowSpacing * 6, fieldW, String.valueOf(ClientData.archerKillsPerArmor));
        this.skeletonTntChanceField = createField(fieldX, rowStartY + rowSpacing * 7, fieldW, String.valueOf(ClientData.archerSkeletonTntChance));
        this.strayWitherChanceField = createField(fieldX, rowStartY + rowSpacing * 8, fieldW, String.valueOf(ClientData.archerStrayWitherChance));
        this.mobLimitField = createField(fieldX, rowStartY + rowSpacing * 9, fieldW, String.valueOf(ClientData.archerMobLimit));

        // 应用参数按钮
        int confirmY = rowStartY + rowSpacing * 10 + 5;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_params"), button -> {
            applyParams();
        }).dimensions(centerX - 60, confirmY, 120, btnH).build());
    }

    /**
     * 创建数字输入框，仅允许输入正整数
     *
     * @param x X坐标
     * @param y Y坐标
     * @param w 宽度
     * @param defaultVal 默认值
     * @return 创建的输入框组件
     */
    private TextFieldWidget createField(int x, int y, int w, String defaultVal) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal(""));
        field.setMaxLength(6);
        field.setText(defaultVal);
        // 仅允许输入空字符串或纯数字
        field.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+"));
        this.addDrawableChild(field);
        return field;
    }

    /**
     * 读取所有输入框的值并通过网络包发送给服务端
     * 如果输入为空则使用当前ClientData中的值作为默认值
     */
    private void applyParams() {
        try {
            int bowPower = parseIntSafe(bowPowerField, ClientData.archerBowPower);
            int bowPunch = parseIntSafe(bowPunchField, ClientData.archerBowPunch);
            int crossbowMultishot = parseIntSafe(crossbowMultishotField, ClientData.archerCrossbowMultishot);
            int crossbowPiercing = parseIntSafe(crossbowPiercingField, ClientData.archerCrossbowPiercing);
            int swordSharpness = parseIntSafe(swordSharpnessField, ClientData.archerSwordSharpness);
            int swordFireAspect = parseIntSafe(swordFireAspectField, ClientData.archerSwordFireAspect);
            int killsPerArmor = parseIntSafe(killsPerArmorField, ClientData.archerKillsPerArmor);
            int skeletonTntChance = parseIntSafe(skeletonTntChanceField, ClientData.archerSkeletonTntChance);
            int strayWitherChance = parseIntSafe(strayWitherChanceField, ClientData.archerStrayWitherChance);
            int mobLimit = parseIntSafe(mobLimitField, ClientData.archerMobLimit);

            ClientPlayNetworking.send(new ModPackets.SetArcherParamsPayload(
                    bowPower, bowPunch, crossbowMultishot, crossbowPiercing,
                    swordSharpness, swordFireAspect, killsPerArmor,
                    skeletonTntChance, strayWitherChance, mobLimit));
        } catch (NumberFormatException ignored) {
            // 输入格式错误时静默忽略
        }
    }

    /**
     * 安全解析输入框中的整数值
     *
     * @param field 输入框
     * @param defaultVal 输入为空时的默认值
     * @return 解析后的整数值
     */
    private int parseIntSafe(TextFieldWidget field, int defaultVal) {
        try {
            return field.getText().isEmpty() ? defaultVal : Integer.parseInt(field.getText());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 渲染界面：背景面板、标题、状态、参数标签等
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
        String statusText = "archer".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 状态下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 68, 0xFF555555);

        // 绘制参数标签
        int labelX = startX + 15;
        int rowStartY = startY + 74;
        int rowSpacing = 24;

        context.drawTextWithShadow(this.textRenderer, "\u9738\u738b\u5f13\u529b\u91cf\u7b49\u7ea7:", labelX, rowStartY + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u9738\u738b\u5f13\u51b2\u51fb\u7b49\u7ea7:", labelX, rowStartY + rowSpacing + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u6563\u5f39\u5f29\u591a\u91cd\u5c04\u51fb:", labelX, rowStartY + rowSpacing * 2 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u6563\u5f39\u5f29\u7a7f\u900f:", labelX, rowStartY + rowSpacing * 3 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u706b\u7130\u5251\u950b\u5229\u7b49\u7ea7:", labelX, rowStartY + rowSpacing * 4 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u706b\u7130\u5251\u706b\u7130\u9644\u52a0:", labelX, rowStartY + rowSpacing * 5 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u589e\u52a0\u62a4\u7532\u6240\u9700\u51fb\u6740:", labelX, rowStartY + rowSpacing * 6 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u9ab8\u9ac5\u7badTNT\u6982\u7387(%):", labelX, rowStartY + rowSpacing * 7 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u6d41\u6d6a\u8005\u7bad\u51cb\u96f6\u6982\u7387(%):", labelX, rowStartY + rowSpacing * 8 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u751f\u7269\u751f\u6210\u4e0a\u9650:", labelX, rowStartY + rowSpacing * 9 + 6, 0xFFFFFF);

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
