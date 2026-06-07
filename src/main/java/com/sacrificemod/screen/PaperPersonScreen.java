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
 * 纸人玩法参数配置界面
 * <p>
 * 提供纸人玩法的启动/停止控制，以及各项参数的调整：
 * - 纸回复饱食度/饱和度
 * - 增加生命概率和数值
 * - 增加护甲概率和数值
 * - 伤害倍率
 * - 最大耐力值
 * - 冰块掉落概率
 */
public class PaperPersonScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 320;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 340;

    /** 玩法规则说明文本 */
    private static final String[] INFO_LINES = {
            "\u00a7e\u7eb8\u4eba\u73a9\u6cd5\u89c4\u5219\uff1a",
            "1. \u73a9\u5bb6\u521d\u59cb\u6700\u5927\u751f\u547d\u503c\u4e3a1",
            "2. \u53ef\u98df\u7528\u7eb8\u3001\u96ea\u5757\u3001\u51b0\u5757\u3001\u84dd\u51b0\u3001\u6d6e\u51b0",
            "3. \u98df\u7eb8\u56de\u590d\u9971\u98df\u5ea6\u548c\u9971\u548c\u5ea6\uff0c\u6709\u6982\u7387\u589e\u52a0\u751f\u547d\u548c\u62a4\u7532",
            "4. \u98df\u96ea\u5757\u7acb\u5373\u6062\u590d20\u70b9\u8010\u529b\u503c",
            "5. \u98df\u51b0\u5757\u83b7\u5f9715\u79d2\u51b7\u51bb\u6548\u679c\uff08\u8010\u529b\u4e0d\u964d\u3001\u6c34\u9762\u7ed3\u51b0\uff09",
            "6. \u98df\u84dd\u51b0\u83b7\u5f9730\u79d2\u51b7\u51bb\u6548\u679c",
            "7. \u98df\u6d6e\u51b0\u7acb\u5373\u6062\u590d25\u70b9\u8010\u529b\u503c",
            "8. \u89e6\u78b0\u6c34\u3001\u5ca9\u6d46\u65f6\u76f4\u63a5\u6b7b\u4ea1",
            "9. \u65e0\u6cd5\u62ff\u8d77\u5ca9\u6d46\u6876\uff0c\u80cc\u5305\u4e2d\u5ca9\u6d46\u6876\u4f1a\u88ab\u4e22\u5f03",
            "10. \u53ea\u80fd\u7a7f\u76ae\u9769\u5957",
            "11. \u53d7\u5230\u7684\u4f24\u5bb3\u6309\u500d\u7387\u653e\u5927",
            "12. \u6c38\u4e45\u83b7\u5f97\u7f13\u964d\u6548\u679c",
            "13. \u8010\u529b\u503c\u673a\u5236\uff1a\u9760\u8fd1\u5ca9\u6d46/\u706b\u7130/\u624b\u6301\u706b\u628a/\u5730\u72f1\u65f6\u6bcf\u79d2\u6263\u8010\u529b",
            "    \u5176\u4f59\u65f6\u5019\u6bcf\u79d2\u6062\u590d10\u70b9\u8010\u529b",
            "14. \u8010\u529b\u503c\u4e3a\u96f6\u65f6\u8fdb\u5165\u71c3\u70e7\u72b6\u6001",
            "15. \u7528\u956e\u5b50\u6316\u51b0\u5757\u6709\u6982\u7387\u76f4\u63a5\u6389\u843d\u5b8c\u6574\u51b0\u5757"
    };

    /** 是否显示规则说明页面 */
    private boolean showInfo = false;

    /** 纸回复饱食度输入框 */
    private TextFieldWidget paperFoodLevelField;
    /** 纸回复饱和度输入框 */
    private TextFieldWidget paperSaturationField;
    /** 增加生命概率(%)输入框 */
    private TextFieldWidget paperHealthChanceField;
    /** 增加生命值输入框 */
    private TextFieldWidget paperHealthAmountField;
    /** 增加护甲概率(%)输入框 */
    private TextFieldWidget paperArmorChanceField;
    /** 增加护甲值输入框 */
    private TextFieldWidget paperArmorAmountField;
    /** 伤害倍率输入框 */
    private TextFieldWidget damageMultiplierField;
    /** 最大耐力值输入框 */
    private TextFieldWidget maxStaminaField;
    /** 冰块掉落概率(%)输入框 */
    private TextFieldWidget iceDropChanceField;

    /**
     * 构造纸人玩法参数界面
     */
    public PaperPersonScreen() {
        super(Text.translatable("gui.sacrificemod.paper_person_title"));
    }

    /**
     * 初始化界面控件：启动/停止按钮、9个参数输入框、应用按钮
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
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(true, "paper_person"));
        }).dimensions(centerX - btnW - 5, toggleY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.stop"), button -> {
            ClientPlayNetworking.send(new ModPackets.ToggleGamePayload(false, "paper_person"));
        }).dimensions(centerX + 5, toggleY, btnW, btnH).build());

        // 参数输入框
        int labelX = startX + 15;
        int fieldX = startX + 140;
        int fieldW = 60;
        int rowStartY = startY + 74;
        int rowSpacing = 24;

        // 纸回复饱食度
        this.paperFoodLevelField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY, fieldW, btnH, Text.literal(""));
        this.paperFoodLevelField.setMaxLength(5);
        this.paperFoodLevelField.setText(String.valueOf(ClientData.paperFoodLevel));
        this.paperFoodLevelField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.paperFoodLevelField);

        // 纸回复饱和度
        this.paperSaturationField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing, fieldW, btnH, Text.literal(""));
        this.paperSaturationField.setMaxLength(8);
        this.paperSaturationField.setText(String.valueOf(ClientData.paperSaturation));
        this.paperSaturationField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.paperSaturationField);

        // 增加生命概率(%)
        this.paperHealthChanceField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 2, fieldW, btnH, Text.literal(""));
        this.paperHealthChanceField.setMaxLength(5);
        this.paperHealthChanceField.setText(String.valueOf(ClientData.paperHealthChance));
        this.paperHealthChanceField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.paperHealthChanceField);

        // 增加生命值
        this.paperHealthAmountField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 3, fieldW, btnH, Text.literal(""));
        this.paperHealthAmountField.setMaxLength(5);
        this.paperHealthAmountField.setText(String.valueOf(ClientData.paperHealthAmount));
        this.paperHealthAmountField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.paperHealthAmountField);

        // 增加护甲概率(%)
        this.paperArmorChanceField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 4, fieldW, btnH, Text.literal(""));
        this.paperArmorChanceField.setMaxLength(5);
        this.paperArmorChanceField.setText(String.valueOf(ClientData.paperArmorChance));
        this.paperArmorChanceField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.paperArmorChanceField);

        // 增加护甲值
        this.paperArmorAmountField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 5, fieldW, btnH, Text.literal(""));
        this.paperArmorAmountField.setMaxLength(5);
        this.paperArmorAmountField.setText(String.valueOf(ClientData.paperArmorAmount));
        this.paperArmorAmountField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.paperArmorAmountField);

        // 伤害倍率
        this.damageMultiplierField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 6, fieldW, btnH, Text.literal(""));
        this.damageMultiplierField.setMaxLength(8);
        this.damageMultiplierField.setText(String.valueOf(ClientData.damageMultiplier));
        this.damageMultiplierField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.damageMultiplierField);

        // 最大耐力值
        this.maxStaminaField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 7, fieldW, btnH, Text.literal(""));
        this.maxStaminaField.setMaxLength(5);
        this.maxStaminaField.setText(String.valueOf(ClientData.maxStamina));
        this.maxStaminaField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.maxStaminaField);

        // 冰块掉落概率(%)
        this.iceDropChanceField = new TextFieldWidget(this.textRenderer, fieldX, rowStartY + rowSpacing * 8, fieldW, btnH, Text.literal(""));
        this.iceDropChanceField.setMaxLength(5);
        this.iceDropChanceField.setText(String.valueOf(ClientData.iceDropChance));
        this.iceDropChanceField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d+\\.?\\d*"));
        this.addDrawableChild(this.iceDropChanceField);

        // 应用参数按钮
        int confirmY = rowStartY + rowSpacing * 9 + 5;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sacrificemod.apply_paper_params"), button -> {
            applyPaperParams();
        }).dimensions(centerX - 60, confirmY, 120, btnH).build());
    }

    /**
     * 应用纸人玩法所有参数，发送网络包给服务端
     * 如果输入为空则使用当前ClientData中的值作为默认值
     */
    private void applyPaperParams() {
        try {
            int foodLevel = this.paperFoodLevelField.getText().isEmpty() ? ClientData.paperFoodLevel : Integer.parseInt(this.paperFoodLevelField.getText());
            float saturation = this.paperSaturationField.getText().isEmpty() ? ClientData.paperSaturation : Float.parseFloat(this.paperSaturationField.getText());
            int healthChance = this.paperHealthChanceField.getText().isEmpty() ? ClientData.paperHealthChance : Integer.parseInt(this.paperHealthChanceField.getText());
            int healthAmount = this.paperHealthAmountField.getText().isEmpty() ? ClientData.paperHealthAmount : Integer.parseInt(this.paperHealthAmountField.getText());
            int armorChance = this.paperArmorChanceField.getText().isEmpty() ? ClientData.paperArmorChance : Integer.parseInt(this.paperArmorChanceField.getText());
            int armorAmount = this.paperArmorAmountField.getText().isEmpty() ? ClientData.paperArmorAmount : Integer.parseInt(this.paperArmorAmountField.getText());
            float dmgMultiplier = this.damageMultiplierField.getText().isEmpty() ? ClientData.damageMultiplier : Float.parseFloat(this.damageMultiplierField.getText());
            int maxStamina = this.maxStaminaField.getText().isEmpty() ? ClientData.maxStamina : Integer.parseInt(this.maxStaminaField.getText());
            int iceDropChance = this.iceDropChanceField.getText().isEmpty() ? ClientData.iceDropChance : Integer.parseInt(this.iceDropChanceField.getText());

            ClientPlayNetworking.send(new ModPackets.SetPaperPersonParamsPayload(
                    foodLevel, saturation, healthChance, healthAmount, armorChance, armorAmount, dmgMultiplier,
                    maxStamina, iceDropChance
            ));
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
        String statusText = "paper_person".equals(ClientData.activeGameplay)
                ? "\u00a7a\u5df2\u542f\u7528"
                : "\u00a7c\u672a\u542f\u7528";
        context.drawCenteredTextWithShadow(this.textRenderer, "\u73a9\u6cd5\u72b6\u6001: " + statusText, centerX, startY + 58, 0xFFFFFF);

        // 状态下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 68, 0xFF555555);

        // 绘制参数标签
        int labelX = startX + 15;
        int rowStartY = startY + 74;
        int rowSpacing = 24;

        context.drawTextWithShadow(this.textRenderer, "\u7eb8\u56de\u590d\u9971\u98df\u5ea6:", labelX, rowStartY + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u7eb8\u56de\u590d\u9971\u548c\u5ea6:", labelX, rowStartY + rowSpacing + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u589e\u52a0\u751f\u547d\u6982\u7387(%):", labelX, rowStartY + rowSpacing * 2 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u589e\u52a0\u751f\u547d\u503c:", labelX, rowStartY + rowSpacing * 3 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u589e\u52a0\u62a4\u7532\u6982\u7387(%):", labelX, rowStartY + rowSpacing * 4 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u589e\u52a0\u62a4\u7532\u503c:", labelX, rowStartY + rowSpacing * 5 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u4f24\u5bb3\u500d\u7387:", labelX, rowStartY + rowSpacing * 6 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u6700\u5927\u8010\u529b\u503c:", labelX, rowStartY + rowSpacing * 7 + 6, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "\u51b0\u5757\u6389\u843d\u6982\u7387(%):", labelX, rowStartY + rowSpacing * 8 + 6, 0xFFFFFF);

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
