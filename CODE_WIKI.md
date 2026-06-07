# Sacrifice Mod - Code Wiki

## 1. 项目概述

**Sacrifice Mod** 是一个基于 Fabric Loader 的 Minecraft 1.21.1 模组，实现了一套"共享生命 + 献祭身体部位"的合作挑战玩法。所有在线玩家共享同一份生命值与饥饿值，玩家可以献祭自己的身体部位（头部、左手、右手、左腿、右腿）来触发随机抽奖，承受对应的负面效果，同时面临死亡惩罚和跳跃触发怪物生成的风险。

- **Mod ID**: `sacrificemod`
- **版本**: 1.0.7
- **许可证**: MIT
- **Minecraft 版本**: 1.21.1
- **Fabric Loader**: >=0.16.0
- **Java 版本**: 21

---

## 2. 项目架构

```
src/main/
├── java/com/sacrificemod/
│   ├── SacrificeMod.java          # 服务端主入口 & 核心逻辑
│   ├── SacrificeModClient.java    # 客户端入口 & HUD/按键/网络接收
│   ├── GameState.java             # 服务端持久化状态（共享生命/饥饿/部位等）
│   ├── BodyPart.java              # 身体部位枚举
│   ├── ClientData.java            # 客户端缓存数据
│   ├── ModPackets.java            # 网络包定义与注册
│   ├── mixin/
│   │   ├── LivingEntityMixin.java     # 跳跃拦截（双腿失去→禁止跳跃 + 跳跃计数）
│   │   ├── BipedEntityModelMixin.java # 隐藏失去的身体部位模型
│   │   └── PlayerEntityModelMixin.java # 隐藏失去的外层皮肤部位
│   └── screen/
│       └── SacrificeScreen.java   # 献祭/赎回 GUI 界面
└── resources/
    ├── fabric.mod.json            # Fabric 模组元数据
    ├── sacrificemod.mixins.json   # Mixin 配置
    └── assets/sacrificemod/lang/
        └── zh_cn.json             # 中文语言文件
```

### 架构分层

| 层级 | 职责 | 关键类 |
|------|------|--------|
| **服务端核心** | 游戏状态管理、伤害处理、献祭/赎回逻辑、跳跃计数、怪物生成 | `SacrificeMod`, `GameState` |
| **网络通信** | C2S/S2C 自定义 Payload 注册与序列化 | `ModPackets` |
| **客户端入口** | 按键绑定、HUD 渲染、网络包接收 | `SacrificeModClient` |
| **客户端数据** | 缓存服务端同步的状态 | `ClientData` |
| **Mixin 层** | 跳跃拦截、模型可见性控制 | `LivingEntityMixin`, `BipedEntityModelMixin`, `PlayerEntityModelMixin` |
| **GUI 层** | 献祭/赎回操作界面 | `SacrificeScreen` |
| **数据模型** | 身体部位定义 | `BodyPart` |

---

## 3. 模块详解

### 3.1 SacrificeMod — 服务端主入口

**文件**: [SacrificeMod.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/SacrificeMod.java)

`SacrificeMod` 实现 `ModInitializer`，是模组的服务端入口，承担核心游戏逻辑。

#### 初始化 (`onInitialize`)

注册以下事件与网络接收器：

| 注册项 | 说明 |
|--------|------|
| `ModPackets.register()` | 注册所有自定义 Payload 类型 |
| `ServerTickEvents.END_SERVER_TICK` | 每Tick执行健康同步、debuff应用、副手限制、客户端同步 |
| `ServerLivingEntityEvents.ALLOW_DAMAGE` | 拦截伤害，改为扣减共享生命 |
| `ServerPlayerEvents.AFTER_RESPAWN` | 死亡惩罚：最大生命-2 |
| `UseItemCallback.EVENT` | 禁止失去左手的玩家使用副手物品 |
| `SacrificeRequestPayload` 接收器 | 处理献祭请求 |
| `ReclaimRequestPayload` 接收器 | 处理赎回请求 |
| `ToggleGamePayload` 接收器 | 开始/结束玩法 |
| `SetJumpThresholdPayload` 接收器 | 设置跳跃触发阈值 |
| `SetReclaimCostPayload` 接收器 | 设置赎回钻石消耗 |

#### 核心方法

| 方法 | 说明 |
|------|------|
| `onDamage()` | 拦截玩家伤害，扣减共享生命值而非个体生命；共享生命归零时标记死亡 |
| `onServerTick()` | 每Tick主循环：同步生命/饥饿 → 应用debuff → 副手限制 → 同步客户端 |
| `syncHealthAndHunger()` | 计算所有玩家的生命/饥饿变化量，合并到共享值，再写回每个玩家 |
| `applyDebuffs()` | 根据失去的部位施加对应debuff（头部→失明/黑暗，右手→虚弱/挖掘疲劳，腿→缓慢） |
| `enforceOffhandRestriction()` | 失去左手的玩家自动丢弃副手物品 |
| `syncClients()` | 向所有玩家发送 GameStatePayload 和 BodyPartSyncPayload |
| `onRespawn()` | 死亡惩罚：共享最大生命-2（最低2），重置当前生命 |
| `updateMaxHealthAttribute()` | 通过属性修饰符修改玩家最大生命值 |
| `onPlayerJump()` | 跳跃计数+1，达到阈值时生成20个随机敌对生物 |
| `spawnRandomHostileMobs()` | 在玩家附近30格范围内随机生成20个敌对生物 |
| `onSacrificeRequest()` | 处理献祭：失去部位 → 随机抽奖(1-4) → 修改最大生命(-4/-2/+2/+4) |
| `onReclaimRequest()` | 处理赎回：消耗钻石 → 恢复部位 → 移除对应debuff |
| `onToggleGame()` | 开始/结束玩法，重置或恢复所有状态 |
| `onSetJumpThreshold()` | 设置跳跃触发阈值 |
| `onSetReclaimCost()` | 设置赎回钻石消耗 |
| `onUseItem()` | 失去左手的玩家禁止使用副手 |

#### 献祭抽奖结果

| 结果 | 效果 |
|------|------|
| 1 | 所有人最大生命 -2 |
| 2 | 所有人最大生命 -4 |
| 3 | 所有人最大生命 +2 |
| 4 | 所有人最大生命 +4 |

#### 失去部位的负面效果

| 部位 | 效果 |
|------|------|
| HEAD | 失明 III (20s)、黑暗 (20s) |
| RIGHT_HAND | 虚弱 V (20s)、挖掘疲劳 V (20s) |
| LEFT_HAND | 无法使用副手（自动丢弃副手物品） |
| LEFT_LEG / RIGHT_LEG | 缓慢 II (20s) |
| 双腿均失去 | 完全无法跳跃 |

#### 可生成的敌对生物列表

僵尸、骷髅、苦力怕、蜘蛛、末影人、女巫、烈焰人、恶魂、凋灵骷髅、守卫者、远古守卫者、劫掠兽、唤魔者、卫道士、掠夺者、幻翼、疣猪兽、猪灵蛮兵、洞穴蜘蛛、蠹虫、末影螨、岩浆怪、史莱姆、流浪者、尸壳、溺尸、潜影贝、监守者、沼骸、凋灵

---

### 3.2 GameState — 服务端持久化状态

**文件**: [GameState.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/GameState.java)

继承 `PersistentState`，数据存储在主世界的 `sacrificemod` NBT 文件中，服务器重启后自动恢复。

#### 字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `active` | boolean | false | 玩法是否启用 |
| `sharedHealth` | float | 20.0 | 共享生命值 |
| `sharedHunger` | int | 20 | 共享饥饿值 |
| `sharedSaturation` | float | 5.0 | 共享饱和度 |
| `sharedMaxHealth` | float | 20.0 | 共享最大生命值 |
| `jumpCount` | int | 0 | 当前跳跃计数 |
| `jumpThreshold` | int | 50 | 跳跃触发阈值 |
| `reclaimDiamondCost` | int | 3 | 赎回钻石消耗 |
| `lostParts` | Map\<UUID, Set\<BodyPart\>\> | {} | 每个玩家失去的部位 |
| `lastSyncedHealth` | Map\<UUID, Float\>\> | {} | 上次同步的生命值（用于计算增量） |
| `lastSyncedHunger` | Map\<UUID, Integer\>\> | {} | 上次同步的饥饿值 |
| `pendingDeathPenalties` | int | 0 | 待处理的死亡惩罚次数 |

#### 关键方法

| 方法 | 说明 |
|------|------|
| `getServerState(MinecraftServer)` | 获取全局唯一的 GameState 实例 |
| `createFromNbt()` | 从 NBT 反序列化 |
| `writeNbt()` | 序列化到 NBT |
| `losePart()` | 标记玩家失去某部位 |
| `reclaimPart()` | 标记玩家赎回某部位 |
| `markDeath()` / `consumeDeathPenalty()` | 死亡惩罚的标记与消费 |
| `resetForNewGame()` | 重置所有状态到初始值 |
| `clearSyncData()` | 清空同步缓存 |
| `resetLostParts()` | 清空所有玩家失去的部位 |

---

### 3.3 BodyPart — 身体部位枚举

**文件**: [BodyPart.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/BodyPart.java)

| 枚举值 | ID | 中文名 |
|--------|-----|--------|
| `HEAD` | head | 头部 |
| `LEFT_HAND` | left_hand | 左手 |
| `RIGHT_HAND` | right_hand | 右手 |
| `LEFT_LEG` | left_leg | 左腿 |
| `RIGHT_LEG` | right_leg | 右腿 |

方法：
- `getId()` — 获取字符串ID
- `fromId(String)` — 根据ID查找枚举
- `getDisplayName()` — 获取中文显示名

---

### 3.4 ClientData — 客户端缓存

**文件**: [ClientData.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/ClientData.java)

客户端侧的单例数据缓存，由网络包接收器更新，供 GUI 和 Mixin 读取。

| 字段 | 类型 | 说明 |
|------|------|------|
| `active` | boolean | 玩法是否启用 |
| `sharedHealth` | float | 共享生命 |
| `sharedMaxHealth` | float | 共享最大生命 |
| `sharedHunger` | int | 共享饥饿 |
| `jumpCount` | int | 跳跃计数 |
| `jumpThreshold` | int | 跳跃阈值 |
| `reclaimCost` | int | 赎回钻石消耗 |
| `lostParts` | Map\<UUID, Set\<BodyPart\>\> | 所有玩家失去的部位 |
| `lastLotteryResult` | int | 上次抽奖结果(1-4) |
| `lastLotteryPart` | BodyPart | 上次抽奖对应的部位 |

方法：
- `updateFromGameStatePayload()` — 从 GameStatePayload 更新
- `updateFromBodyPartPayload()` — 从 BodyPartSyncPayload 更新
- `reset()` — 重置所有数据

---

### 3.5 ModPackets — 网络包定义

**文件**: [ModPackets.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/ModPackets.java)

定义了 8 个自定义 Payload record，用于客户端与服务端之间的通信。

#### S2C（服务端→客户端）

| Payload | ID | 字段 | 说明 |
|---------|-----|------|------|
| `GameStatePayload` | `sacrificemod:game_state` | active, sharedHealth, sharedMaxHealth, sharedHunger, jumpCount, jumpThreshold, reclaimCost | 同步游戏状态 |
| `BodyPartSyncPayload` | `sacrificemod:body_part_sync` | playerUuid, partOrdinals | 同步玩家失去的部位 |
| `LotteryResultPayload` | `sacrificemod:lottery_result` | result, bodyPartOrdinal | 通知抽奖结果 |

#### C2S（客户端→服务端）

| Payload | ID | 字段 | 说明 |
|---------|-----|------|------|
| `SacrificeRequestPayload` | `sacrificemod:sacrifice_request` | bodyPartOrdinal | 请求献祭某部位 |
| `ReclaimRequestPayload` | `sacrificemod:reclaim_request` | bodyPartOrdinal | 请求赎回某部位 |
| `ToggleGamePayload` | `sacrificemod:toggle_game` | start | 开始/结束玩法 |
| `SetJumpThresholdPayload` | `sacrificemod:set_jump_threshold` | threshold | 设置跳跃阈值 |
| `SetReclaimCostPayload` | `sacrificemod:set_reclaim_cost` | cost | 设置赎回钻石消耗 |

所有 Payload 均实现 `CustomPayload` 接口，使用 `CustomPayload.codecOf()` 进行序列化/反序列化。

---

### 3.6 SacrificeModClient — 客户端入口

**文件**: [SacrificeModClient.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/SacrificeModClient.java)

实现 `ClientModInitializer`，负责：

1. **按键绑定** — 注册 `V` 键打开献祭界面
2. **HUD 渲染** — 在屏幕左上角显示跳跃计数（仅玩法启用时）
3. **网络接收** — 接收 `GameStatePayload`、`BodyPartSyncPayload`、`LotteryResultPayload`，更新 `ClientData`

---

### 3.7 SacrificeScreen — GUI 界面

**文件**: [SacrificeScreen.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/screen/SacrificeScreen.java)

继承 `Screen`，按 `V` 键打开，提供以下功能：

| 区域 | 功能 |
|------|------|
| 右上 | "开始玩法" / "结束玩法" 按钮 |
| 右中 | 跳跃阈值输入框 + 确认按钮 |
| 右中 | 赎回钻石消耗输入框 + 确认按钮 |
| 右侧信息 | 玩法状态、共享生命、共享饥饿度、跳跃计数、身体部位状态、上次抽奖结果 |
| 左侧 | 5个身体部位的献祭/赎回按钮（已失去的部位显示赎回按钮，未失去的显示献祭按钮） |

界面不会暂停游戏（`shouldPause()` 返回 false）。

---

### 3.8 Mixin 类

#### LivingEntityMixin

**文件**: [LivingEntityMixin.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/mixin/LivingEntityMixin.java)

- **目标**: `LivingEntity.jump()`
- **注入点**: `@At("HEAD")`，可取消
- **逻辑**:
  - 客户端侧：双腿均失去时取消跳跃
  - 服务端侧：双腿均失去时取消跳跃；否则调用 `SacrificeMod.onPlayerJump()` 进行跳跃计数

#### BipedEntityModelMixin

**文件**: [BipedEntityModelMixin.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/mixin/BipedEntityModelMixin.java)

- **目标**: `BipedEntityModel.setAngles()`
- **注入点**: `@At("TAIL")`
- **逻辑**: 根据玩家失去的部位，将对应的模型部件（head, hat, leftArm, rightArm, leftLeg, rightLeg）设为不可见

#### PlayerEntityModelMixin

**文件**: [PlayerEntityModelMixin.java](file:///d:/pcl.2/模组/play4/src/main/java/com/sacrificemod/mixin/PlayerEntityModelMixin.java)

- **目标**: `PlayerEntityModel.setAngles()`
- **注入点**: `@At("TAIL")`
- **逻辑**: 根据玩家失去的部位，将外层皮肤部件（leftSleeve, rightSleeve, leftPants, rightPants）设为不可见，确保与 3D Skin Layers 等模组兼容

---

## 4. 数据流

### 4.1 伤害处理流程

```
玩家受到伤害
  → ALLOW_DAMAGE 事件触发
  → 取消原版伤害，改为扣减 sharedHealth
  → sharedHealth <= 0 时标记死亡
  → 所有玩家生命同步为 sharedHealth
```

### 4.2 献祭流程

```
客户端按V键 → 打开 SacrificeScreen
  → 点击献祭按钮
  → 发送 SacrificeRequestPayload (C2S)
  → 服务端验证部位未失去
  → 标记部位失去
  → 随机抽奖 (1-4)
  → 修改 sharedMaxHealth
  → 广播献祭消息与抽奖结果
  → 发送 LotteryResultPayload (S2C)
  → 下一个 Tick 自动同步 GameStatePayload + BodyPartSyncPayload
```

### 4.3 赎回流程

```
客户端点击赎回按钮
  → 发送 ReclaimRequestPayload (C2S)
  → 服务端验证部位已失去 + 钻石足够
  → 扣除钻石
  → 恢复部位
  → 移除对应 debuff
  → 广播赎回消息
```

### 4.4 跳跃触发流程

```
玩家跳跃
  → LivingEntityMixin 拦截
  → 双腿均失去 → 取消跳跃
  → 否则 → SacrificeMod.onPlayerJump()
  → jumpCount++
  → jumpCount >= jumpThreshold → 生成20个随机敌对生物 + 广播警告
```

### 4.5 每Tick同步流程

```
onServerTick
  → syncHealthAndHunger: 计算增量 → 合并到共享值 → 写回所有玩家
  → applyDebuffs: 根据失去部位持续施加debuff
  → enforceOffhandRestriction: 失去左手的玩家丢弃副手物品
  → syncClients: 发送 GameStatePayload + BodyPartSyncPayload 给所有玩家
```

---

## 5. 依赖关系

### 5.1 运行时依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| Minecraft | 1.21.1 | 目标游戏版本 |
| Fabric Loader | >=0.16.0 | 模组加载器 |
| Fabric API | 0.115.0+1.21.1 | Fabric API 库 |
| Java | >=21 | 运行时版本 |

### 5.2 使用的 Fabric API 模块

| API 模块 | 用途 |
|----------|------|
| `fabric-networking-api-v1` | 自定义网络包注册与收发 |
| `fabric-entity-events-v1` | `ServerLivingEntityEvents.ALLOW_DAMAGE`、`ServerPlayerEvents.AFTER_RESPAWN` |
| `fabric-lifecycle-events-v1` | `ServerTickEvents.END_SERVER_TICK`、`ClientTickEvents.END_CLIENT_TICK` |
| `fabric-events-interaction-v0` | `UseItemCallback.EVENT` |
| `fabric-key-binding-api-v1` | `KeyBindingHelper.registerKeyBinding()` |
| `fabric-screen-api-v1` | `HudRenderCallback.EVENT` |

### 5.3 构建依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| fabric-loom | 1.7-SNAPSHOT | Gradle 构建插件 |
| Yarn Mappings | 1.21.1+build.3:v2 | 方法名映射 |

### 5.4 类间依赖关系

```
SacrificeMod ──→ GameState (读写游戏状态)
             ──→ ModPackets (注册/发送网络包)
             ──→ BodyPart (部位枚举)

SacrificeModClient ──→ ClientData (读写客户端缓存)
                   ──→ ModPackets (接收网络包)
                   ──→ SacrificeScreen (打开GUI)
                   ──→ BodyPart (部位枚举)

SacrificeScreen ──→ ClientData (读取显示数据)
               ──→ ModPackets (发送请求包)
               ──→ BodyPart (部位枚举)

LivingEntityMixin ──→ GameState (服务端读取失去部位)
                  ──→ ClientData (客户端读取失去部位)
                  ──→ SacrificeMod (调用跳跃计数)
                  ──→ BodyPart

BipedEntityModelMixin ──→ ClientData (读取失去部位)
                      ──→ BodyPart

PlayerEntityModelMixin ──→ ClientData (读取失去部位)
                       ──→ BodyPart

ClientData ──→ ModPackets (从Payload解析数据)
           ──→ BodyPart

GameState ──→ BodyPart (存储失去部位)
```

---

## 6. Mixin 配置

**文件**: [sacrificemod.mixins.json](file:///d:/pcl.2/模组/play4/src/main/resources/sacrificemod.mixins.json)

| 配置项 | 值 |
|--------|-----|
| `required` | true |
| `minVersion` | 0.8 |
| `package` | com.sacrificemod.mixin |
| `compatibilityLevel` | JAVA_21 |

**通用 Mixin**（客户端+服务端）:
- `LivingEntityMixin`

**客户端专用 Mixin**:
- `BipedEntityModelMixin`
- `PlayerEntityModelMixin`

---

## 7. 国际化

**文件**: [zh_cn.json](file:///d:/pcl.2/模组/play4/src/main/resources/assets/sacrificemod/lang/zh_cn.json)

当前仅提供中文（简体）翻译，覆盖所有 GUI 文本、按键名称、聊天消息等。使用 Minecraft 颜色代码（`§` / `\u00a7`）进行格式化。

---

## 8. 项目构建与运行

### 8.1 环境要求

- JDK 21+
- Gradle 8.10（项目自带 wrapper）

### 8.2 构建命令

```bash
# 编译并生成模组 JAR
./gradlew build

# 输出路径: build/libs/sacrifice-mod-1.0.7.jar
```

### 8.3 开发运行

```bash
# 启动客户端
./gradlew runClient

# 启动服务端
./gradlew runServer
```

### 8.4 安装

将构建生成的 `sacrifice-mod-1.0.7.jar` 放入 Minecraft 的 `mods/` 目录，确保已安装 Fabric Loader 0.16.0+ 和 Fabric API 0.115.0+1.21.1。

---

## 9. 玩法机制总结

1. **共享生命**: 所有玩家共享同一份生命值和饥饿值，任何人的伤害/恢复/进食都影响全体
2. **献祭系统**: 玩家可献祭5个身体部位，每次献祭触发随机抽奖，可能增加或减少全体最大生命
3. **部位效果**: 失去不同部位会获得不同的永久debuff（失明、虚弱、缓慢等），失去左手无法使用副手，双腿均失去无法跳跃
4. **视觉反馈**: 失去的身体部位在玩家模型上不可见（包括外层皮肤）
5. **赎回系统**: 消耗钻石可赎回已失去的部位，移除对应debuff
6. **跳跃惩罚**: 全体玩家跳跃累计达到阈值后，在跳跃者附近生成20个随机敌对生物
7. **死亡惩罚**: 任何玩家死亡后，全体最大生命永久-2（最低2）
8. **可配置**: 跳跃阈值和赎回钻石消耗可在 GUI 中调整
