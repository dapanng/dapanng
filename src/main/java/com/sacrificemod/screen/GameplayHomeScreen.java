package com.sacrificemod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 玩法选择主界面
 * <p>
 * 显示所有可用玩法的入口按钮，玩家点击后进入对应玩法的参数配置界面。
 * 包含的玩法：祭祀、纸人、残障、肉鸽、加速、全随机、追击、传送、弓箭手大作战
 */
public class GameplayHomeScreen extends Screen {

    /** 背景面板宽度 */
    private static final int BG_WIDTH = 280;
    /** 背景面板高度 */
    private static final int BG_HEIGHT = 336;

    /**
     * 构造玩法选择主界面
     */
    public GameplayHomeScreen() {
        super(Text.translatable("gui.sacrificemod.home_title"));
    }

    /**
     * 初始化界面控件：各玩法的入口按钮
     */
    @Override
    protected void init() {
        super.init();
        int startX = (this.width - BG_WIDTH) / 2;
        int startY = (this.height - BG_HEIGHT) / 2;
        int centerX = this.width / 2;
        int btnW = 200;
        int btnH = 22;
        int spacing = 26;
        int startYBtn = startY + 45;

        // 祭祀玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.sacrifice_gameplay"),
                button -> this.client.setScreen(new SacrificeScreen())
        ).dimensions(centerX - btnW / 2, startYBtn, btnW, btnH).build());

        // 纸人玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.paper_person_gameplay"),
                button -> this.client.setScreen(new PaperPersonScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing, btnW, btnH).build());

        // 残障玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.disabled_gameplay"),
                button -> this.client.setScreen(new DisabledScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 2, btnW, btnH).build());

        // 肉鸽玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.roguelike_gameplay"),
                button -> this.client.setScreen(new RoguelikeScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 3, btnW, btnH).build());

        // 加速玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.speed_gameplay"),
                button -> this.client.setScreen(new SpeedScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 4, btnW, btnH).build());

        // 全随机玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.random_gameplay"),
                button -> this.client.setScreen(new RandomScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 5, btnW, btnH).build());

        // 追击玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.chase_gameplay"),
                button -> this.client.setScreen(new ChaseScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 6, btnW, btnH).build());

        // 传送玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.teleport_gameplay"),
                button -> this.client.setScreen(new TeleportScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 7, btnW, btnH).build());

        // 弓箭手大作战玩法
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sacrificemod.archer_gameplay"),
                button -> this.client.setScreen(new ArcherScreen())
        ).dimensions(centerX - btnW / 2, startYBtn + spacing * 8, btnW, btnH).build());
    }

    /**
     * 渲染界面：背景面板、标题、副标题
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.getString(), centerX, startY + 12, 0xFF5555);

        // 标题下方分隔线
        context.drawHorizontalLine(startX + 10, startX + BG_WIDTH - 10, startY + 26, 0xFF555555);

        // 副标题提示
        context.drawCenteredTextWithShadow(this.textRenderer, "\u00a77\u8bf7\u9009\u62e9\u4e00\u4e2a\u73a9\u6cd5", centerX, startY + 32, 0xAAAAAA);

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
