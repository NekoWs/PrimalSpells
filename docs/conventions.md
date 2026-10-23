# 开发约定

## 1. 包结构总览

代码根为 `src/main/kotlin/work/nekow/primalspells/`。

| 目录 | 职责 |
|---|---|
| `registry/` | 内置法术工厂清单、DeferredRegister与物品、实体、组件、伤害、战利品注册 |
| `magic/core/` | 法术抽象、阶段、注册工厂、配方、伤害分类与质量 |
| `magic/runtime/` | 弹体生命周期与待加入队列，不承载内容注册 |
| `magic/projectile/`、`immobile/` | 飞行/静态法术内容 |
| `magic/modifier/` | 修正和现有被动卡 |
| `magic/action/` | 复制单卡、全量施放、追加触发等抽牌控制卡 |
| `magic/effect/geometry/` | 位移、轨迹、制导与碰撞输入 |
| `magic/effect/lifecycle/` | 命中、伤害、触发、区域场、冻结与联动 |
| `magic/effect/visual/` | PD公开API造型和动画接线 |
| `wand/` | 法杖牌堆、预览、数值、保存、实例与持有者 |
| `item/`、`item/component/` | 物品交互与组件值 |
| `loot/` | 获取池、成品创建与GLM |
| `integration/` | 数据重载、公共服务端API与事件 |
| `entity/` | 实体、AI、假人统计与跳字 |
| `network/`、`event/` | 数据包、公共事件与指令 |
| `client/{hud,screen,tooltip,input,render}/` | 客户端界面、输入、缓存和渲染 |
| `client/capture/` | 显式开发录制场景和原生输出观察 |
| `util/` | 无状态工具与共享接线 |

Java mixin放 `src/main/java/work/nekow/primalspells/mixin/`。资源放 `src/main/resources/`；生成工具放 `tools/`，不进运行时JAR。详细入口见 [代码导航](code-map.md)。

## 2. 放代码的判断准则

注册放registry；抽牌控制放action；能在世界运行的法术放projectile；注入行为放effect对应子包；物品持久身份和牌堆放wand；领域状态放所属领域，不堆入util。客户端专属类只由客户端入口加载。

依赖由入口/事件指向领域，客户端可读公共数据，公共侧不得反向依赖client。`magic/core`可引用Projectile基类和效果契约，不导入具体法术卡或registry；内置卡清单放ModSpells，护盾具体接入放lifecycle。抽牌/付费不导入PD、表现与网络，实际发射由WandCasting接线。新增结构或迁移路径同步本页第1节和第8节。

## 3. 命名

包名小写单数；类和文件PascalCase且主类同名；法术ID、资源名与组件键用snake_case。Payload以动词+名词命名，注册对象以Mod开头，常量用UPPER_SNAKE_CASE。

法术ID、wandId和存档键是兼容契约，改类名不等于改ID。资源需匹配 `items/<id>.json`、`models/item/<id>.json`、`textures/item/<id>.png` 及中英文语言键。

## 4. 注释与文档

注释/KDoc用中文，标识符和命令保留英文。公开类型和函数说明用途、调用方、参数与端归属；算法说明原因和边界。TODO/FIXME必须说明缺口和下一步，并在台账登记。文档按 [维护规则](README.md#6-文档维护规则) 就地更新，不堆叠工作记录。

## 5. 服务端与客户端

公共侧禁止import `net.minecraft.client.*` 或本项目client类。客户端入口为 `PrimalSpellsClient`，专用服务器不加载客户端事件。S→C处理经 `ClientPayloadSink` 可空回调分发。

抽牌、MP、伤害、运动、碰撞均为服务端权威；客户端只表现和提交输入。录制观察器可以读取PD原生输出，但不能注册替代渲染器、改写实际伤害或伪造测试成功。

## 6. 网络

Payload放network，提供ID、StreamCodec；公共协议注册集中在 `network/ModNetworking`，服务端编杖/预览校验在 `WandRequestHandler`，由PrimalSpells挂载，客户端处理注册集中在客户端入口。当前协议为1.5.0，改编码需同步两端。

生存编杖/编包优先原版物品PICKUP交互；创造编杖使用受服务端校验的专用包。选择槽和预览请求校验实际拥有的菜单/背包物品、范围与请求频率。只同步展示所需数据，不把客户端组件当权威状态。

## 7. 提交前检查

当前内容禁止主动音效：不播放施法/锁定/复制/爆炸/弹跳/传送/放置提示，不注册声音资源；自有生物与假人保持isSilent。`checkSilentContent`随check/build扫描运行时Kotlin/Java与声音资源，录制工具调主音量不在此限制内。原版世界的环境/生物声音不由本模组全局关闭。


- 内容已注册，资源/语言/获取来源完整；修改创建配方不能污染模板或共享可变effect。
- 核心机制符合 [架构第8节](architecture.md#8-关键不变量与陷阱)，新效果遵循阶段和独立荷载规则。
- 粒子使用PD；缺陷遵守第9节；动画按 [连贯性](design/animation-continuity.md) 验证。
- 跑 `./gradlew build`（包含checkArchitecture源码依赖护栏）；依赖已缓存时可加 `--offline`。运行与取证见 [构建指南](build-and-run.md)。
- 更新相关文档和问题状态，检查差异，按第10节提交Git。

## 8. 路径迁移对照

只保留影响查找或兼容的对照；新增文件直接按第1节落位，不逐次记录。

| 旧入口 | 当前入口/规则 |
|---|---|
| `magic/core/MagicManager` | 工厂目录为`magic/core/SpellRegistry`，内容清单为`registry/ModSpells`，世界循环为`magic/runtime/ProjectileRuntime`，事件为`event/SpellRuntimeEvents` |
| `Wand.drawChain/payChain/castHand` | `wand/WandDeck`、`CastTransaction`、`WandCasting`；原Wand公共入口保留 |
| `Wand`内NBT与预览构建 | `wand/WandPersistence`、`WandPreviewBuilder`，NBT键和网络编码保持 |
| `PrimalSpells`内网络处理 | `network/ModNetworking`注册、`WandRequestHandler`校验 |
| `magic/core/ShieldGuard` | `magic/effect/lifecycle/ShieldGuard` |
| `Revise` | `magic/core/Modifier.kt` |
| `magic/modifier/SineWave.kt` | `Serpentine.kt`，法术ID仍为sine_wave |
| `WandTooltipTracker`、`WandScrollHandler` | `SpellSlotTracker`、`SpellSlotScrollHandler`，法杖与小包共用 |
| 小包菜单、法杖编辑台、`WandEditorOverlay` | 已移除；容器内原物品交互，V键只读预览 |
| `Hurt`内统一伤害入口 | `magic/effect/lifecycle/SpellDamage.kt`、`SpellImpact.kt`与DirectDamage契约 |
| 自管粒子集合 | PD的ParticleBatch/ParticleGroup，禁止恢复自建实现 |

## 9. 外部依赖政策：ParticleDrawing

PD是必需前置，也是粒子、动画、锚点插值与动态光照的唯一实现方。本模组只声明玩法时机、造型和参数。

发现API缺陷、签名/可见性不符、缺少必要语义或能力时：**停止该接入点，不在本模组另写替代实现**。禁止反射绕过、修改lib内JAR、复制引擎代码、自建渲染器/时钟/光照平滑系统或用其他机制伪装同一API能力。

先写目标效果，再对照当前sources JAR列出现有能力和缺口，登记到 [PD需求](issues/pd-api-requests.md)，包含证据、API草案、影响面、验收和恢复条件。仅在当前工作涉及PD时，在产出末尾列“ParticleDrawing API 修改需求”；纯数值或界面任务不附无关清单。

可调整本模组对现有API的误用；不属于PD的原版独立装饰可用原版粒子，但不能用来绕开缺失能力。完整接线和职责见 [effects-api第0节](design/effects-api.md#0-职责与缺陷处理)。

升级时同时更换主JAR和sources JAR，更新 `gradle.properties` 的版本及含ALPHA标识的下限；不硬编码mods.toml。运行 `build --no-build-cache` 并做相关原生验证。API/语义失败仍按上述规则登记，不就地打补丁。

## 10. Git提交

每次更新完成后提交Git，不自动push。一条提交只表达一个目的，格式为 `目的：简短描述`，全角冒号，无句号，尽量不超过20字；不写文件名、实现清单或长body。

目的仅用：新增、修复、优化、重构、文档、测试、构建、杂项。例如 `优化：法术动画表现`。背景与兼容说明写入对应文档。
