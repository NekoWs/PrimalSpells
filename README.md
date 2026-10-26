# Primal Spells

[![Build](https://github.com/NekoWs/PrimalSpells/actions/workflows/build.yml/badge.svg)](https://github.com/NekoWs/PrimalSpells/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)
![NeoForge](https://img.shields.io/badge/Loader-NeoForge-F17829)
![Java](https://img.shields.io/badge/Java-25-437291)

一个以法杖与法术链构筑为核心的 Minecraft 魔法模组，受 Noita 的法术组合玩法启发。

探索结构、击败持杖术士，带回法杖与法术卡，再将它们拆解、重组为自己的施法配方。Primal Spells 同时提供数据包配置与服务端接口，供整合包和附属模组扩展。

[功能](#功能) · [安装](#安装) · [快速上手](#快速上手) · [源码构建](#源码构建) · [整合包与扩展](#整合包与扩展) · [反馈与贡献](#反馈与贡献)

## 功能

- **自由编杖**：组合投射物、静态法术、修正与抽牌控制，权衡法力、施放延迟和充能时间。
- **独立触发荷载**：命中、定时和失效触发可承载另一条法术链，构建多阶段攻击。
- **复制与抽牌**：多重施放、一分为多、希腊字母引用和刷新法杖提供更深入的组合空间。
- **探索获取**：原版结构提供分级战利品；游荡术士使用真实法杖，击败后可夺取并拆改。
- **像素魔法表现**：法术粒子、动态光照、命中反馈，以及支持拖动和缩放的 HUD。
- **非破坏性交互**：照明、显踪、收纳与缓降；战斗法术默认不破坏方块。

## 安装

当前构建基线：

| 组件 | 版本 |
|---|---|
| Minecraft | 26.2 |
| NeoForge | 26.2.0.59 |
| Java | 25 |
| ParticleDrawing | 1.0.21-ALPHA |

1. 准备对应版本的 Minecraft 与 NeoForge 环境。
2. 按下方说明构建本项目，将 `build/libs/primalspells-1.0.0.jar` 放入游戏的 `mods/` 目录。
3. 将仓库 `lib/` 中的 `particledrawing-1.0.21-ALPHA.jar` 一并放入 `mods/`。`-sources.jar` 仅供开发阅读源码。
4. 启动游戏。联机时，服务端和客户端均需安装 Primal Spells 与 ParticleDrawing，并保持版本一致。

版本配置以 [gradle.properties](gradle.properties) 为准。ParticleDrawing 是必需前置，负责粒子动画与动态光照。

## 快速上手

### 获取法杖

寻找村庄神殿箱子，取得入门法杖、法术卡、《寻法手记》和寻法罗盘。并非每座村庄都有神殿。

后续可探索矿井、神庙、前哨站、府邸、下界要塞和末地城等地点，获取不同方向的法术与更高等级的法杖。部分结构内会出现游荡术士，击败后可获得它正在使用的法杖。

寻法罗盘也可由 **罗盘＋紫水晶碎片＋纸** 无序合成；右键搜索地点，潜行右键切换类别。

### 编辑与施放

在背包或箱子中悬停法杖，滚轮选择法术槽位。拿起法术卡后右键法杖装入或替换；空光标右键取出所选卡牌。编好后，手持法杖右键施放。

| 操作 | 默认输入 |
|---|---|
| 选择法术槽位 | 悬停法杖并滚动滚轮 |
| 装入、替换或取出卡牌 | 右键法杖物品 |
| 查看详细数值 | 悬停法杖并按住 `Alt` |
| 查看法术链与下次施法结算 | 悬停或手持法杖时按 `V` |
| 调整 HUD 位置与大小 | 游戏中按 `H` |

`V` 与 `H` 可在游戏按键设置中重新绑定。法术槽位底色用于区分法术链及其独立荷载；预览会显示下一次施法的法力与双冷却结算。

## 源码构建

需要 JDK 25。仓库使用 Gradle Wrapper，并已包含当前构建所需的 ParticleDrawing 主 JAR 与源码 JAR。

```sh
git clone https://github.com/NekoWs/PrimalSpells.git
cd PrimalSpells
```

**Windows / PowerShell**

```powershell
.\gradlew.bat build
```

**Linux / macOS**

```sh
chmod +x gradlew
./gradlew build
```

构建产物位于 `build/libs/`。开发客户端使用 `runClient`，开发服务器使用 `runServer`，例如：

```powershell
.\gradlew.bat runClient
```

`build` 包含编译、资源处理、打包、架构依赖检查与内容静音检查。修改玩法或表现时，还需针对相关功能进行游戏内验证。

## 整合包与扩展

通过数据包调整法术获取、法杖等级、成品配方和术士生成，也可通过服务端 API 与事件接入其他模组。

| 扩展内容 | 入口 |
|---|---|
| 战利品池、成品法杖配方与术士生成参数 | [loot_profiles](src/main/resources/data/primalspells/primalspells/loot_profiles/) |
| 法杖等级数值 | [wand_tiers](src/main/resources/data/primalspells/primalspells/wand_tiers/) |
| 结构箱子追加奖励 | [loot_modifiers](src/main/resources/data/primalspells/loot_modifiers/) |
| 服务端施法接口 | [SpellApi](src/main/kotlin/work/nekow/primalspells/integration/SpellApi.kt) |
| 施法、伤害与生活法术事件 | [SpellEvents](src/main/kotlin/work/nekow/primalspells/integration/SpellEvents.kt) |

物品贴图、模型与语言文本可通过资源包覆盖，资源位于 [assets/primalspells](src/main/resources/assets/primalspells/)。

## 反馈与贡献

欢迎通过 [Issues](https://github.com/NekoWs/PrimalSpells/issues) 反馈问题或提出建议，也欢迎提交 Pull Request。

反馈问题时，请附上 Minecraft、NeoForge、本模组与 ParticleDrawing 的版本、复现步骤，以及相关日志或崩溃报告。法术链问题请同时提供卡牌顺序和法杖数值。

提交代码前请运行 `build`，并说明改动目的及验证方式。涉及法术行为时，请提供能够复现的组合；涉及视觉时，可附截图或录屏。

## 许可证

项目许可证为 **GNU AGPLv3**，声明见 [gradle.properties](gradle.properties) 中的 `mod_license`。
