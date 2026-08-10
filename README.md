# McPets

Morndream 服务器的 **Paper / Folia** 宠物插件：把世界上几乎任意生物驯成专属宠物，支持跟随、漫步、攻击、GUI 管理与轻量 YAML 存档。

- **平台**：Paper / Folia `api-version 26.2`
- **构建**：Java 25 · Maven
- **软依赖**：PlayerParticles（有则优先，无则用内置粒子预设）

---

## 功能概览

### 驯服
- `/pet tame <名字>`：准星对准生物即可驯服（主人 = 执行者）
- 支持可驯服与不可驯服生物（含怪物），黑名单可在配置中调整
- 每人宠物数量上限可配（默认 **5**）
- 同玩家内部名不可重复；显示名可在 GUI 中修改（支持颜色 / MiniMessage / Hex，可见字数默认 ≤24，不含颜色符号）

### 行为状态
| 状态 | 说明 |
|------|------|
| **FOLLOW** | 跟随主人（默认） |
| **IDLE** | 原地待命 |
| **WANDER** | 附近自由漫步 |
| **ATTACK** | 点击攻击后追击锁定目标 |

- **无敌 / 普通**：默认无敌；无敌时不受伤、不攻击且**原地站桩**（不寻路）；切普通后按跟随/漫步走动
- GUI 可单独开关 **AI**（关闭后也不移动）

### 攻击规则（均可在 config 调整）
- **点击攻击**（GUI 或 `/pet toggle <名> attack`）：锁定宠物 **10 格内最近的一名**非主人玩家
- 附近没有目标则不攻击；单次点击只打这一名，不会全服扫敌
- 目标离开范围、打满次数或仇恨超时后结束，需再次点击
- 默认伤害 **1.0**，范围与次数等可在 `attack.*` 调整

### 管理 GUI
- **Shift + 右键** 宠物，或 `/pet gui [名字]`
- 切换状态、攻击、模式、AI、体型档位、幼体、粒子、悬浮物、传送
- 界面物品不可被拿走；插件关闭时会先关闭所有 GUI
- 每个页面独立配置：`plugins/McPets/gui/*.yml`

### 外观
- **体型**：Attribute 缩放三档（默认 0.5 / 1.0 / 2.0）+ 原版 baby（支持的生物）
- **粒子**：内置预设；检测到 PlayerParticles 时走兼容通道
- **悬浮物**：头顶上方装饰 ItemDisplay（可轻微浮动）
- **叼物**：暂时关闭（后续重做）

### 持久化与存档安全
- 单文件 `pets.yml`：主人/实体 UUID、开关、外观；并保存驯服前的**原版快照**
- **不向区块写入插件 PDC / 自定义 NBT**；不写 `setTamed` / `setInvulnerable`
- 插件运行时才套用名字、体型、幼体等；**卸载（park）或取消驯服时完整还原**原版状态
- 悬浮展示体 `setPersistent(false)`
- 宠物**死亡** → 删档并提示主人；主人下线 → 实体仍留在世界
- 实体丢失 → 默认静默清档（可配置提示）
- **说明**：异常崩溃时，若区块已自动存盘，运行中外观可能短暂残留；正常 disable / `/pet delete` / 关服路径会还原

### Folia
- 已声明 `folia-supported: true`
- AI / 传送 / 实体操作使用 Region · Entity · Global · Async 调度器

---

## 指令

| 指令 | 说明 |
|------|------|
| `/pet help` | 帮助 |
| `/pet tame <名字>` | 驯服准星生物 |
| `/pet list [玩家]` | 列表（看他人需额外权限） |
| `/pet delete <名字>` | 取消驯服 |
| `/pet toggle <名字> follow` | 在 跟随 / 待命 / 漫步 间切换 |
| `/pet toggle <名字> attack` | 开关攻击 |
| `/pet tpa <名字>` | 传送到宠物 |
| `/pet tph <名字>` | 宠物传到身边 |
| `/pet gui [名字]` | 打开管理界面 |
| `/pet reload` | 重载配置（需管理权限） |

---

## 权限

| 权限 | 默认 | 说明 |
|------|------|------|
| `mcpets.use` | true | 基础指令 |
| `mcpets.tame` | op | 驯服 |
| `mcpets.list` | true | 查看自己的列表 |
| `mcpets.list.others` | op | 查看他人列表 |
| `mcpets.delete` | true | 删除宠物 |
| `mcpets.toggle` | true | 切换 follow / attack |
| `mcpets.tpa` / `mcpets.tph` | true | 传送 |
| `mcpets.gui` | true | 打开 GUI |
| `mcpets.admin` | op | 管理（含子权限） |

兼容 LuckPerms 等权限插件。

---

## 安装

1. 使用 **Paper 或 Folia 26.2+**，构建环境需 **JDK 25**
2. 将 `McPets-v1.0.jar` 放入 `plugins/`
3. 启动服务器，编辑生成的配置后执行 `/pet reload` 或重启

### 构建

```bash
mvn clean package
```

产物：`target/McPets-v1.0.jar`

---

## 配置说明

主配置：`plugins/McPets/config.yml`

常用项：

```yaml
settings:
  max-pets-per-player: 5   # 每人上限；0 = 不限制
  tame-range: 8
  follow-teleport-distance: 16.0
  notify-missing-pet: false

blacklist:
  - ENDER_DRAGON
  - WITHER
  - WARDEN
  # ...

attack:
  damage: 1.0
  max-hits-per-target: 6
  hate-duration-seconds: 60
  auto-range: 10.0

scale:
  tiers: [0.5, 1.0, 2.0]
```

GUI 布局与按钮：`plugins/McPets/gui/`

- `main.yml` — 宠物列表
- `manage.yml` — 单宠管理
- `float.yml` — 悬浮物选择

文案支持 MiniMessage 渐变、`&` 颜色码与 `#RRGGBB`。

---

## 插件卸载 / 热重载

`settings.unload-mode` 控制插件被 disable 时已有宠物的处理：

| 模式 | 行为 |
|------|------|
| **park**（默认） | 实体留在世界；清除叼物与插件 AI，并**还原驯服前原版外观/标志**；再次启用后按实体 UUID 套回宠物外观 |
| **despawn** | 移除实体并写档；再次启用时按存档坐标重新生成（**不保留**运行中原版 NBT） |

无论哪种模式都会在卸载时强制保存 `pets.yml`。

**身份识别**：只使用 `plugins/McPets/pets.yml` 中的实体 UUID。旧版本残留的插件 PDC 仅在确认存在时清除。

---

## 作者

**Morndream Server** · [morndream.top](https://morndream.top)
