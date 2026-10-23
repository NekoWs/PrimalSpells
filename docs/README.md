# PrimalSpells 文档

PrimalSpells 是 Minecraft NeoForge 魔法模组：玩家把法术卡装入法杖，以牌堆抽取、修正与触发荷载组合施法，通过结构战利品和敌对施法者获取新配方。

`docs/` 是唯一权威说明。文档描述当前代码与明确的待办，修改历史查 Git；根目录 [AGENTS.md](../AGENTS.md) 只提供阅读路由。

## 1. 文档索引

| 任务 | 文档 |
|---|---|
| 理解对象、抽牌、结算与持久化 | [架构](architecture.md)；改核心前必读第8节 |
| 找实现或法术ID | [代码导航](code-map.md) |
| 写代码、放文件、提交 | [开发约定](conventions.md) |
| 新增或修改法术 | [法术开发](spell-development.md) |
| 构建、排障、实机取证 | [构建与运行](build-and-run.md)；录制见第9节 |
| 当前问题与验证边界 | [问题台账](issues/README.md) |
| 后续开发范围 | [TODO](todo.md) |
| 法术与护盾数值 | [战斗规则](design/combat-pricing.md) |
| 法杖数值、单位、存档迁移 | [法杖等级](design/wand-tiers.md) |
| 粒子API职责与接线 | [PD接入](design/effects-api.md) |
| 视觉与动画 | [像素风格](design/minecraft-visuals.md)、[连贯性](design/animation-continuity.md)、[法术联动](design/spell-quality.md) |
| 特定效果 | [持杖施法](design/wand-casting.md)、[模型资源](design/wand-materials.md)、[区域场](design/field-effects.md)、[特殊法术](design/spell-expansion.md) |
| 界面 | [HUD](design/hud.md)、[编杖与预览](design/spell-preview.md) |
| 获取与世界交互 | [探索获取](design/acquisition.md)、[生活法术与罗盘](design/world-utility.md) |
| 整合包与附属模组 | [数据包/API契约](design/pack-integration.md)、[可安装示例](examples/exploration-pack/pack.mcmeta) |

## 2. 技术栈与事实来源

Minecraft 26.2、NeoForge 26.2.0.59、Java 25、Kotlin 2.4.10、Gradle 9.6.1。版本以 `gradle.properties`、`build.gradle` 和 wrapper 配置为准。

ParticleDrawing 是必需前置，锁定版本以 `gradle.properties` 与 `lib/` 为准；API只对照同版本 sources JAR。服务端决定抽牌、伤害与碰撞；客户端负责界面和表现。时间统一完整刻，回蓝按每刻；世界粒子由 PD 管理。

## 3. 仓库结构

- `src/main/kotlin/work/nekow/primalspells/`：运行时代码。
- `src/main/java/work/nekow/primalspells/mixin/`：Java mixin。
- `src/main/resources/`：资源、语言、数据包与加载配置。
- `tools/`：录制、资源生成和诊断工具。
- `lib/`：当前PD主JAR与源码JAR；源码JAR不参与构建。
- `build/`、`run/`：生成产物、开发存档与日志，不作为源码入库。

## 4. 术语

| 名称 | 含义 |
|---|---|
| 法术卡 Magic | 注册模板的新实例，包含抽牌预算、MP与双冷却代价 |
| 投射物 Projectile | 服务端运行的法术对象，不要求对应原版Entity |
| 修正 Modifier | 装配同一法术链的效果；本链内与牌序无关 |
| 荷载 payload | 触发法术独立抽取的子链，不影响父链修正 |
| 施放延迟 delay | 下一次施放前的等待 |
| 充能时间 recharge | 牌堆抽空并回洗后的等待 |

## 5. 修改路径

先读对应设计和实现，再修改；机制变更同步架构与开发指南，行为变更更新所属设计，缺陷只在问题台账维护。构建和相关验证通过后提交Git，不自动推送。

## 6. 文档维护规则

1. 文档就地更新，只描述当前行为、约束、操作与未决事项；不追加轮次、工作流水账或被覆盖方案。
2. 每个规则只有一个权威页面，其他页面用链接引用。数值不在多个历史表格中重复维护。
3. 修复后从 [open](issues/open.md) 移除，在 [resolved](issues/resolved.md) 按主题保留简短结论与有效回归入口；详细过程留在Git。
4. [verify](issues/verify.md) 只保留尚未覆盖的体验/联机范围；[PD需求](issues/pd-api-requests.md) 只放未决能力，不把已解决需求原文当常驻文档。
5. 构建、机制断言、视觉门禁和环境日志分别报告，失败不得写成通过；证据注明复现命令，生成文件不当作永久链接。
6. 同步修正目录、链接与章节路由；不改写根AGENTS为说明正文。
