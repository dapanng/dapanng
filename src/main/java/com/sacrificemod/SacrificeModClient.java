package com.sacrificemod;

import com.sacrificemod.screen.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端模组入口类
 *
 * 负责客户端侧的所有初始化工作，包括：
 * - 注册快捷键（V键打开玩法界面）
 * - 注册客户端tick事件（处理快捷键输入、全随机倒计时）
 * - 注册HUD渲染回调（在屏幕上显示玩法状态信息）
 * - 注册网络包接收器（接收服务端同步的数据并更新ClientData）
 */
public class SacrificeModClient implements ClientModInitializer {

    /** 打开UI界面的快捷键绑定 */
    private static KeyBinding openUIKey;

    /**
     * 客户端模组初始化入口方法
     *
     * 由Fabric框架在客户端启动时调用，完成以下工作：
     * 1. 注册V键快捷键
     * 2. 注册客户端tick事件
     * 3. 注册HUD渲染回调
     * 4. 注册所有S2C网络包接收器
     */
    @Override
    public void onInitializeClient() {
        // 注册快捷键：V键打开玩法界面
        openUIKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sacrificemod.open_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.sacrificemod"
        ));

        // 注册客户端tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检测快捷键按下，根据当前活跃玩法打开对应的界面
            if (openUIKey.wasPressed() && client.player != null) {
                if (ClientData.activeGameplay == null || ClientData.activeGameplay.isEmpty()) {
                    // 无活跃玩法，打开玩法选择主页
                    client.setScreen(new GameplayHomeScreen());
                } else if (ClientData.activeGameplay.equals("sacrifice")) {
                    client.setScreen(new SacrificeScreen());
                } else if (ClientData.activeGameplay.equals("paper_person")) {
                    client.setScreen(new PaperPersonScreen());
                } else if (ClientData.activeGameplay.equals("disabled")) {
                    client.setScreen(new DisabledScreen());
                } else if (ClientData.activeGameplay.equals("roguelike")) {
                    client.setScreen(new RoguelikeScreen());
                } else if (ClientData.activeGameplay.equals("speed")) {
                    client.setScreen(new SpeedScreen());
                } else if (ClientData.activeGameplay.equals("random")) {
                    client.setScreen(new RandomScreen());
                } else if (ClientData.activeGameplay.equals("chase")) {
                    client.setScreen(new ChaseScreen());
                } else if (ClientData.activeGameplay.equals("teleport")) {
                    client.setScreen(new TeleportScreen());
                } else if (ClientData.activeGameplay.equals("archer")) {
                    client.setScreen(new ArcherScreen());
                } else {
                    // 未知玩法，回退到主页
                    client.setScreen(new GameplayHomeScreen());
                }
            }

            // 全随机玩法：客户端本地倒计时递减
            if (ClientData.randomizeRemainingTicks > 0) {
                ClientData.randomizeRemainingTicks--;
            }
        });

        // 注册HUD渲染回调：在屏幕左上角显示玩法状态信息
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            // 无活跃玩法时不渲染HUD
            if (ClientData.activeGameplay == null || ClientData.activeGameplay.isEmpty()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int x = 4;  // HUD起始X坐标
            int y = 4;  // HUD起始Y坐标

            // 根据当前活跃玩法渲染不同的HUD信息
            switch (ClientData.activeGameplay) {
                case "sacrifice" -> {
                    // 献祭玩法：显示跳跃计数/阈值
                    drawContext.drawTextWithShadow(client.textRenderer,
                            String.format("\u00a7e\u8df3\u8dc3\u8ba1\u6570: \u00a7f%d \u00a77/ %d", ClientData.jumpCount, ClientData.jumpThreshold),
                            x, y, 0xFFFFFF);
                }
                case "paper_person" -> {
                    // 纸人玩法：显示玩法启用状态
                    drawContext.drawTextWithShadow(client.textRenderer,
                            "\u00a7e\u7eb8\u4eba\u73a9\u6cd5 \u00a7a\u5df2\u542f\u7528",
                            x, y, 0xFFFFFF);

                    // 耐力条显示
                    float stamina = ClientData.playerStamina;
                    float maxStamina = ClientData.playerMaxStamina;
                    if (maxStamina <= 0) maxStamina = 30;

                    // 根据耐力状态选择颜色
                    String staminaColor;
                    if (ClientData.playerFrozenTicks > 0) {
                        staminaColor = "\u00a7b"; // 青色：冻结状态
                    } else if (stamina > maxStamina * 0.3) {
                        staminaColor = "\u00a79"; // 蓝色：正常状态
                    } else {
                        staminaColor = "\u00a7c"; // 红色：低耐力警告
                    }

                    String staminaText = "\u00a7e\u8010\u529b: " + staminaColor + (int) stamina + "\u00a7f/" + (int) maxStamina;
                    drawContext.drawTextWithShadow(client.textRenderer, staminaText, x, y + 12, 0xFFFFFF);

                    // 冻结指示器：显示冻结剩余秒数
                    if (ClientData.playerFrozenTicks > 0) {
                        String frozenText = "\u00a7b\u51bb\u7ed3 " + (ClientData.playerFrozenTicks / 20) + "s";
                        drawContext.drawTextWithShadow(client.textRenderer, frozenText, x, y + 24, 0xFFFFFF);
                    }
                }
                case "disabled" -> {
                    // 残疾玩法：显示玩法启用状态
                    drawContext.drawTextWithShadow(client.textRenderer,
                            "\u00a7e\u6b8b\u75be\u73a9\u6cd5 \u00a7a\u5df2\u542f\u7528",
                            x, y, 0xFFFFFF);
                }
                case "roguelike" -> {
                    // 肉鸽玩法：显示玩法启用状态和待击杀生物数
                    drawContext.drawTextWithShadow(client.textRenderer,
                            "\u00a7e\u8089\u9e3d\u73a9\u6cd5 \u00a7a\u5df2\u542f\u7528",
                            x, y, 0xFFFFFF);

                    String mobsText = String.format("\u00a7e\u5f85\u51fb\u6740\u751f\u7269: \u00a7f%d", ClientData.roguelikePendingMobs);
                    drawContext.drawTextWithShadow(client.textRenderer, mobsText, x, y + 12, 0xFFFFFF);
                }
                case "speed" -> {
                    // 加速玩法：显示玩法启用状态和当前速度倍率
                    drawContext.drawTextWithShadow(client.textRenderer,
                            "\u00a7e\u52a0\u901f\u73a9\u6cd5 \u00a7a\u5df2\u542f\u7528",
                            x, y, 0xFFFFFF);

                    String speedText = String.format("\u00a7e\u5f53\u524d\u500d\u7387: \u00a7f%.0f%%", ClientData.speedMultiplier);
                    drawContext.drawTextWithShadow(client.textRenderer, speedText, x, y + 12, 0xFFFFFF);
                }
                case "random" -> {
                    // 全随机玩法：显示玩法启用状态和下次随机化倒计时
                    drawContext.drawTextWithShadow(client.textRenderer,
                            "\u00a7e\u5168\u968f\u673a\u73a9\u6cd5 \u00a7a\u5df2\u542f\u7528",
                            x, y, 0xFFFFFF);

                    // 计算剩余时间（tick转秒再转分:秒格式）
                    long remaining = ClientData.randomizeRemainingTicks;

                    int remainingSeconds = (int) (remaining / 20);
                    int minutes = remainingSeconds / 60;
                    int seconds = remainingSeconds % 60;

                    String timeText = String.format("\u00a7e\u4e0b\u6b21\u968f\u673a: \u00a7f%d:%02d", minutes, seconds);
                    drawContext.drawTextWithShadow(client.textRenderer, timeText, x, y + 12, 0xFFFFFF);
                }
            }
        });

        // ==================== 注册S2C网络包接收器 ====================

        // 接收游戏状态同步包，更新ClientData中的所有状态数据
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.GameStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientData.updateFromGameStatePayload(payload));
        });

        // 接收身体部位同步包，更新ClientData中的丢失部位数据
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.BodyPartSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientData.updateFromBodyPartPayload(payload));
        });

        // 接收抽奖结果包，记录抽奖结果和涉及的身体部位
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.LotteryResultPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientData.lastLotteryResult = payload.result();
                // 校验身体部位序号有效性
                if (payload.bodyPartOrdinal() >= 0 && payload.bodyPartOrdinal() < BodyPart.values().length) {
                    ClientData.lastLotteryPart = BodyPart.values()[payload.bodyPartOrdinal()];
                }
            });
        });

        // 接收禁用格子同步包，更新ClientData中的禁用格子数据
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.DisabledSlotsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientData.updateFromDisabledSlotsPayload(payload));
        });

        // 接收耐力同步包，更新ClientData中的耐力和冻结数据
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.StaminaSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientData.updateFromStaminaSyncPayload(payload));
        });

        // 接收随机化时间同步包，更新ClientData中的全随机倒计时数据
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.RandomizeTimeSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientData.updateFromRandomizeTimeSyncPayload(payload));
        });

        // 接收玩法动态数据同步包，更新肉鸽/加速玩法的实时数据
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.GameplaySyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientData.updateFromGameplaySyncPayload(payload));
        });
    }
}
