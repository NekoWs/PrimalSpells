# PrimalSpells 文档索引

> PrimalSpells 是一个 Minecraft（NeoForge）模组：**法杖 + 牌堆式法术组合**。
> 玩家持有的法杖里装着一组法术，施法时从牌堆抽牌，把「投射物」与「修饰器」组合成一次具体法术；法术小包用于收纳法术物品，法杖编辑台用于整理法杖。
>
> 本目录是项目的唯一权威文档来源；仓库根的 [`AGENTS.md`](../AGENTS.md) 只做路由，不重复内容。

---

## 1. 文档索引

| 文档 | 内容 | 什么时候读 |
|---|---|---|
| [architecture.md](architecture.md) | 分层架构、法术对象模型、牌堆与施法流程、投射物生命周期、持久化、网络、客户端 | **第一次接触本项目**；要改核心机制、排查「为什么没生效」 |
| [code-map.md](code-map.md) | 逐包逐文件的代码导航 + 「我要改 X → 去哪个文件」 | 知道要改什么、但不知道文件在哪 |
| [spell-development.md](spell-development.md) | 新增投射物 / 修饰器 / 特效 / 物品 / 资源 / 语言键的完整步骤与注册点 | 要添加或修改法术内容 |
| [build-and-run.md](build-and-run.md) | 版本矩阵、依赖、Gradle 命令、运行目录、CI、常见故障、**实机录制（§9）** | 要构建、运行、排障；**要「看一眼实机效果」** |
| [issues/README.md](issues/README.md) | **问题台账索引**：目录分工、严重度图例、维护规则 | 踩坑前先查；登记/关闭问题时对着规则来 |
| ↳ [issues/open.md](issues/open.md) | **仍未解决**：待续开发的功能、性能与表现问题 | 修 bug 前先看有没有已知坑 |
| ↳ [issues/verify.md](issues/verify.md) | **待实机验证清单**（编译过了但要进游戏确认手感） | 改完视觉/手感、或每次发版前 |
| ↳ [issues/resolved.md](issues/resolved.md) | **已解决记录**（按轮次，含历史崩溃与 PD 已闭环条目） | 想知道「当初为什么这么改」 |
| ↳ [issues/pd-api-requests.md](issues/pd-api-requests.md) | 要提给 ParticleDrawing 的 **API 需求**（未决）+ 能力边界 | 接粒子特效、升级前置模组 |
| [conventions.md](conventions.md) | 包结构规则、命名规则、注释语言、服务端/客户端分离、提交前检查、重构对照表 | 写任何代码之前 |
| [design/minecraft-visuals.md](design/minecraft-visuals.md) | MC实色粒子与44张像素法术卡规范 | 修改视觉或贴图 |
| [design/wand-casting.md](design/wand-casting.md) | 持杖法阵、角点锁印、冷却导槽、低模法杖与退场 | 修改持杖/施法表现 |
| [design/spell-quality.md](design/spell-quality.md) | 第54轮当前质感、修饰配方继承、跨投射物反应与高配档 | 修改特效或法术联动 |
| [design/animation-continuity.md](design/animation-continuity.md) | 动画起落/引导/视野与近场镜头冲击，含前置阻塞 | 修改动画与镜头反馈 |
| [design/effects-api.md](design/effects-api.md) | 与 ParticleDrawing 的粒子特效 API 设计（锚点、播放时钟、资源注册） | 做特效接线、升级前置模组版本 |
| [design/spell-expansion.md](design/spell-expansion.md) | 第52轮七张法术卡：水晶/电弧/远距/光束/斥力/冰冻，数值与组合验收 | 修改新增法术与组合 |
| [design/field-effects.md](design/field-effects.md) | 黑洞 / 重力场的造型、生命周期、粒子预算与实机证据 | 修改区域法术视觉 |
| [design/combat-pricing.md](design/combat-pricing.md) | **战斗数值口径**：攻击法术的伤害/施法间隔（对齐原版 DPS）、护盾的强度与抗性 | 改伤害、`delay`/`recharge`、护盾数值之前 |
| [design/wand-tiers.md](design/wand-tiers.md) | **法杖数值口径**：六级等级曲线（法力/充能速度/施放延迟/充能时间/槽位/施放数）、蓄能型变体、毫秒与余数累加、Alt 面板的展示路径 | 改法杖数值、加掉落/合成、调施法节奏之前 |

## 2. 技术栈与关键事实

| 项 | 值 |
|---|---|
| Minecraft / NeoForge | 26.2 / 26.2.0.59 |
| 语言 | Kotlin（主）+ Java 客户端 mixin |
| Java / Gradle | 25（toolchain）/ 9.6.1（wrapper） |
| modId / 包根 | `primalspells` / `work.nekow.primalspells` |
| 关键前置 | `particledrawing`（粒子，本地 `lib/particledrawing-<particledrawing_version>.jar`，现为 `1.0.15-ALPHA`） |
| 许可证 | GNU AGPLv3 |

细节见 [build-and-run.md](build-and-run.md)。

## 3. 仓库结构（顶层）

```
PrimalSpells/
├─ build.gradle / gradle.properties / settings.gradle   构建与版本
├─ gradlew, gradlew.bat, gradle/wrapper/                Gradle wrapper（9.6.1）
├─ lib/particledrawing-1.0.15-ALPHA.jar                本地前置模组 jar（版本由 gradle.properties 指定）
├─ src/main/kotlin/work/nekow/primalspells/             模组源码（包结构见 conventions.md）
├─ src/main/java/work/nekow/primalspells/mixin/         Java mixin
├─ src/main/resources/                                  资源：assets（模型/贴图/语言/物品）、data（伤害类型）、META-INF
├─ src/generated/resources/                             数据生成输出（可选）
├─ docs/                                                本文档目录
├─ tools/capture/                                       实机录制流水线（capture.ps1 + assemble.py，见 build-and-run.md §9）
├─ .github/workflows/build.yml                          CI：JDK 25 + ./gradlew build
├─ run/                                                 开发运行目录（不入库）：存档/日志/崩溃报告
└─ build/                                               Gradle 产物与 NeoForm 缓存（不入库，体积大）
```

源码分包规则与每个包的职责见 [conventions.md](conventions.md#1-包结构总览)。

## 4. 术语表

| 术语 | 含义 | 主要位置 |
|---|---|---|
| **Magic** | 法术基类：`id`、`mana`、`delay`、`recharge`、`effects`、`lore`、生命周期钩子 | `magic/core/Magic.kt` |
| **Projectile** | 特殊法术：不是 MC 实体，而是服务端 Kotlin 对象，靠每 tick 的 effect 驱动位置与命中 | `magic/projectile/Projectile.kt` |
| **Modifier**（原 `Revise`） | 修饰器：施法时把自身携带的 effect 注入到本次施法的投射物上，或改动参数（伤害/速度/弹跳…） | `magic/core/Modifier.kt`、`magic/modifier/` |
| **Effect** | 挂在投射物上的可组合行为：几何（`Move`/`Velocity`…）、时机与结算（`Hurt`/`Explosion`/`Trigger`…）、表现（`Trajectory`） | `magic/effect/` |
| **MagicStatus** | 投射物运行时状态：`pos`/`velocity`/`damage`/`age`/`bounces`/`hitTargets`/爆炸参数 | `magic/core/MagicStatus.kt` |
| **EffectPhase** | 效果执行阶段（SETUP/MODIFIER/MOTION/OVERRIDE/VISUAL），决定效果谁先跑 | `magic/core/EffectPhase.kt` |
| **Wand** | 法杖运行时对象：牌堆（`drawPile`/`hand`/`discardPile`）、法力与冷却、NBT 序列化 | `wand/Wand.kt` |
| **牌堆 / 抽牌** | 施法时按 `cast` 预算从 `drawPile` 抽牌进 `hand`，结算后进 `discardPile`；抽牌堆空时回洗并附加 `recharge` | `wand/Wand.kt` |
| **TriggerSpell** | 触发法术：会额外抽牌组成 `payload`，命中时触发 payload 内法术 | `magic/core/TriggerSpell.kt` |
| **wandId / pouchId** | 物品数据组件里的唯一标识（UUID 字符串），用于把物品栈关联到服务端的 `Wand` 实例或小包内容 | `item/component/WandID.kt` |
| **HUD** | 法杖数值条（法力/冷却） | `client/hud/` |

## 5. 推荐的阅读 / 改动路径

- **我要跑起来**：`./gradlew runClient` → 失败看 [build-and-run.md](build-and-run.md#6-常见故障) 与 [issues/open.md](issues/open.md)。
- **我要加一个法术**：先 [spell-development.md](spell-development.md)，再照抄同类实现（同类清单见 [code-map.md](code-map.md)）。
- **我要理解施法全过程**：[architecture.md](architecture.md#3-一次施法的完整流程)。
- **我遇到「法术没效果 / 不命中 / 不显示」**：[issues/open.md](issues/open.md) → 再看 [architecture.md](architecture.md) 对应链路。
- **我要重命名或移动文件**：[conventions.md](conventions.md#1-包结构总览) 决定位置 → 改完必须 `./gradlew build`。

## 6. 文档维护规则

1. **代码结构变化 → 同步本文档**：移动/新增/删除包或文件后，更新 [code-map.md](code-map.md) 与 [conventions.md](conventions.md#8-本次结构重构的旧--新对照迁移记录)。
2. **新发现的问题 → 登记 [issues/open.md](issues/open.md)**，写清现象、复现、影响、涉及文件。
3. **修好问题 → 从 [issues/open.md](issues/open.md) 删掉**，把结论回填到 [issues/resolved.md](issues/resolved.md)（规则见 [issues/README.md](issues/README.md)）。
4. **[AGENTS.md](../AGENTS.md) 只允许放「指针」**，不得复制本文档内容；内容变化只改 docs/。
5. 文档与代码不一致时，**以代码为准**，并立刻修文档。
