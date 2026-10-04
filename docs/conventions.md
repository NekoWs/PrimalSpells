# 约定（Conventions）

> 本文规定 PrimalSpells 的**包结构、命名、注释语言与提交前检查**。
> 结构类改动一律以本文为准；如果你要新增文件，先在这里找到它该去的位置。
> 关联文档：[architecture.md](architecture.md)（架构）、[code-map.md](code-map.md)（代码导航）。
>
> ⚠️ **外部依赖红线**：前置模组 `particledrawing`（ParticleDrawing，ALPHA）出现 API 缺陷或能力缺失时，
> **不得在本模组自行另写一套替代实现** —— 应停止尝试，并在产出末尾列出 API 修改需求（见 §9）。

---

## 1. 包结构总览

包根：`work.nekow.primalspells`（与 `gradle.properties` 的 `mod_group_id` 保持一致）。

```
src/main/kotlin/work/nekow/primalspells/
├─ PrimalSpells.kt              模组主入口（@Mod，公共侧）：注册一切 + 注册网络包（不引用客户端类）
├─ registry/                    所有注册表与数据组件（唯一允许写 DeferredRegister 的地方）
│  ├─ ModItems.kt               物品、数据组件（WAND_ID/WAND_SPELLS/POUCH_ID/POUCH_SELECTED_SLOT/…）、创造模式标签、法术物品工厂
│  ├─ ModEntities.kt            实体类型（drone / test_dummy）
│  └─ ModDamageTypes.kt         伤害类型 + `projectileSourceOrNull()`（数据包缺失时安全降级）
├─ magic/                       法术系统
│  ├─ core/                     对象模型与调度
│  │  ├─ Magic.kt               法术基类（mana/delay/recharge/effects/lore/生命周期钩子/新建实例）
│  │  ├─ EffectPhase.kt         效果执行阶段（SETUP/MODIFIER/MOTION/OVERRIDE/VISUAL）
│  │  ├─ MagicStatus.kt         投射物运行时状态（pos/velocity/damage/age/bounces/爆炸参数）
│  │  ├─ MagicManager.kt        法术 id 注册表、投射物列表、每 tick 驱动、创建法术实例
│  │  ├─ ShieldGuard.kt         「命中/爆炸 → 护盾」接入缝：通用效果只问这一句，不认识具体法术
│  │  ├─ Modifier.kt            修饰器基类（effects() 克隆注入）
│  │  └─ TriggerSpell.kt        触发法术接口（payload 组装）
│  ├─ projectile/               投射物（非 MC Entity 的服务端对象）
│  │  ├─ Projectile.kt          投射物基类：装配 Position/Velocity/Move，命中分派
│  │  ├─ Fireball.kt DroneBolt.kt TriggerFireball.kt Laser.kt TeleportProjectile.kt
│  │  ├─ DelayedCastProjectile.kt DroneSwarm.kt
│  │  └─ immobile/              静止/定点类投射物
│  │     ├─ SmallExplosion.kt LargeExplosion.kt GravityFieldProjectile.kt BlackHoleProjectile.kt
│  ├─ modifier/                 修饰器实现（26 个）
│  └─ effect/                   特效（挂在投射物上的可组合行为）
│     ├─ BaseEffect.kt          特效基类：六钩子 + phase + 同类叠加辅助（stackScale/isStackPrimary）
│     ├─ geometry/              空间与运动：Position, Velocity, Move, Bounce, Tracking, ConstantAcceleration
│     ├─ lifecycle/             时机与结算：Trigger, DelayedTrigger, Hurt, Explosion（+ ExplosionFx 表现层）, AreaFieldEffect, BlackHole, GravityField
│     └─ visual/                粒子表现：Trajectory（头部本体标记 + 拖尾）
├─ wand/                        法杖运行时状态
│  ├─ Wand.kt                   牌堆（drawPile/hand/discardPile）、施法、法力与冷却（小数 tick + 余数累加）、NBT 序列化（槽位保序）
│  ├─ WandTier.kt               法杖等级表（六级 + 调试杖：法力/充能速度/施放延迟/充能时间/槽位/施放数）+ 蓄能型变体 + 创建时掷点，见 design/wand-tiers.md
│  ├─ WandManager.kt            法杖实例表（wandId → Wand）、每 tick 维护、世界存档读写
│  └─ WandSavedData.kt          SavedData：法杖 NBT 的持久化容器
├─ item/                        物品（编辑交互一律走原版「物品对上物品」，没有容器菜单）
│  ├─ WandItem.kt               法杖：长按施法、被动 tick、数值同步、容器内装卸
│  ├─ SpellPouchItem.kt         法术小包：16 格内容 + 选中槽，装卸逻辑与法杖一致
│  ├─ TestDummyItem.kt          测试木桩放置
│  └─ component/ WandID.kt      数据组件值类型（wand_id 与 pouch_id 共用）
├─ entity/                      自定义实体与 AI 目标：Drone(+3 Goal)/TestDummy/DamageNumber
├─ network/                     payload 定义与客户端分发点
│  ├─ SyncWandStatsPayload.kt / ScreenShakePayload.kt    S→C
│  ├─ SelectSpellSlotPayload.kt                           C→S（法杖/小包共用的选槽同步）
│  └─ ClientPayloadSink.kt                               公共侧→客户端的可空回调（专用服务器安全）
├─ event/                       公共侧事件处理器：WandEventHandler（右键屏蔽、登录同步、登出清理）、ShieldDummyCommand（测试指令 `/primalspells shielddummy`）、WandCommand（`/primalspells wand <等级>`，见 build-and-run.md §9）
├─ client/                      仅在客户端加载（Dist.CLIENT）的代码
│  ├─ PrimalSpellsClient.kt     客户端入口：安装 ClientPayloadSink、注册渲染器/tooltip/输入、登出清缓存
│  ├─ hud/                      WandHudRenderer
│  ├─ tooltip/                  ClientTooltipHandler, WandSpellTooltip/ClientWandSpellTooltip,
│  │                           PouchTooltip/ClientPouchTooltip, SpellSlotTracker
│  ├─ input/                    SpellSlotScrollHandler（滚轮选槽，法杖/小包共用）, ClientContainerDragHandler（Ctrl+左键拖拽交换）
│  ├─ render/                   DroneEntityModel/DroneEntityRenderer
│  ├─ capture/                  **实机录制驱动**（CaptureDriver 状态机 + CaptureScenes 场景表）：只在开发环境激活，见 build-and-run.md §9
│  └─ ScreenShakeClient.kt        镜头冲击（屏幕震动 / FOV 抽动）的客户端求值：消费 `ScreenShakePayload`，受原版「受伤视角晃动」强度控制
├─ util/                        工具：HitResult.kt、Lore.kt、ParticleShapes.kt（球体粒子采样）、Polyhedra.kt（球面多面体几何：截角二十面体面板）、PdParticles.kt（PD 管理器兜底 + spawnParticle）、ScreenShake.kt（镜头冲击的服务端入口）、Deferred.kt（服务端延迟一次性动作队列，由 MagicManager 每 tick 推）
└─ mixin/                       Java mixin；包名被 primalspells.mixins.json 引用，**不要改**
```

另有几处非 Kotlin 位置：

```
src/main/java/work/nekow/primalspells/mixin/     Java mixin 源码
src/main/resources/                              assets / data / META-INF（资源路径与包结构无关）
tools/capture/                                   实机录制流水线（PowerShell + Python，不进 jar）：见 build-and-run.md §9
```

---

## 2. 放代码的判断准则

1. **是不是「注册」？** 是 → `registry/`。任何 `DeferredRegister.create…` 只允许出现在这里。
2. **是不是「法术内容」？** 是 → `magic/` 下按语义归位：能飞的实体化法术 → `magic/projectile/`；只修改别的法术参数/注入特效 → `magic/modifier/`；挂在投射物上的行为 → `magic/effect/`（几何 → `geometry/`，时机与结算 → `lifecycle/`，粒子 → `visual/`）。
3. **是不是「法杖状态」？** 是 → `wand/`。
4. **只能在客户端跑吗？**（GUI、渲染、输入、本地缓存）是 → `client/` 下按 hud / tooltip / screen / input / render 归位，并确保只被 `client/` 内的类或 `PrimalSpellsClient` 引用。
5. **是不是事件处理器？** 公共侧 → `event/`；纯客户端事件 → `client/` 对应子包（例如滚轮选槽在 `client/input/`），并在 `PrimalSpellsClient` 里注册。
6. **都不是？** → 无状态纯函数放 `util/`；有状态的服务端系统放它所属的领域包（如 `wand/`）。

**依赖方向**（不要制造反向依赖）：

```
client/*        ──→ registry/ item/ entity/ network/ util/ magic/(只读数据)
event/ registry/ PrimalSpells ──→ magic/ wand/ item/ entity/ network/ util/
magic/core      ──→ magic/effect, util/            （core 不依赖具体 projectile）
magic/projectile──→ magic/core, magic/effect, util/
magic/modifier  ──→ magic/core, magic/effect
```

`magic/core` 不得 import 具体投射物或修饰器；注册具体法术的地方只有 `MagicManager` 的 `init` 块（它位于 `core`，是唯一例外，见 [architecture.md](architecture.md) §2.1 / §4）。

---

## 3. 命名规则

| 对象 | 规则 | 示例 |
|---|---|---|
| 包 | 全小写、单数、语义分组 | `magic.effect.geometry` |
| 类/对象 | PascalCase，名词 | `Trajectory`, `WandManager` |
| 文件 | 与其中主类同名（一个文件一个对外类） | `Trajectory.kt` → `class Trajectory` |
| 法术 `id` | snake_case，与资源名严格一致 | `bounce_explosion` |
| 法术物品资源 | `assets/primalspells/items/<id>.json`、`models/item/<id>.json`、`textures/item/<id>.png` | 三件套必须齐 |
| 与法术 id 对应的物品 | 名称 == 法术 id（`ModItems` 自动注册） | `item.primalspells.bounce_explosion` |
| 数据组件 | snake_case | `wand_id`, `spell_pouch_contents` |
| 网络 payload | `<动词><名词>Payload` | `SyncWandStatsPayload`, `SelectSpellSlotPayload` |
| 注册表对象 | `Mod*` | `ModItems`, `ModEntities` |
| 常量 | `UPPER_SNAKE_CASE`，放进 `companion object` | `TRAIL_LIFETIME` |

**法术 id 是不可随意更改的持久化键**：法杖存档（`WandSavedData`）与物品组件里存的是 id 字符串，改名会使旧存档失效。类名可以改，id 不要改。

---

## 4. 注释与文档语言

- 代码注释与 KDoc 用**中文**；标识符、命令、资源键保留英文。
- 对外可见的类与公开函数必须写 KDoc，说明：它是什么、被谁调用、参数含义、服务端/客户端归属。
- 非显然的算法（如碰撞面法线求解、partialTick 插值、牌堆抽牌预算）必须写「为什么这么做」，而不是复述代码。
- `// TODO` / `// FIXME` 必须写清「卡在哪里、下一步做什么」，并在 [issues/open.md](issues/open.md) 里登记。

---

## 5. 服务端 / 客户端分离

- 公共侧代码（`registry/ magic/ wand/ item/ entity/ network/ event/ util/`）**不允许 import `client/` 下的任何类**，也不允许引用 `net.minecraft.client.*`。
- 客户端代码只能由 `client/` 内的类或 `PrimalSpellsClient`（`@Mod(dist = Dist.CLIENT)`）加载。
- 纯客户端事件（`ScreenEvent`、渲染事件）注册在 `PrimalSpellsClient`，不要挂在公共入口上——公共入口在专用服务器上也会加载。
- **S→C 包的公共侧处理器不能直接调用客户端类**：用 `network/ClientPayloadSink` 的可空回调中转，
  由 `PrimalSpellsClient` 在初始化时安装实现（服务端保持 `null`，处理器什么都不做）。
- 服务端权威的判定原则：**法术的伤害、移动、命中一律只算在服务端**；客户端只做表现（HUD、tooltip、动画）与本地输入→payload。

---

## 6. 网络约定

- payload 一律放 `network/`，实现 `CustomPacketPayload` + `StreamCodec`，并提供 `ID`。
- 注册集中在 `PrimalSpells` 的 `RegisterPayloadHandlersEvent` 监听器里（客户端 → 服务端用 `playToServer`，服务端 → 客户端用 `playToClient`）。
- **编辑交互优先走原版协议**：编杖/编包用 `Item.overrideOtherStackedOnMe`（物品对上物品），
  只有「物品组件必须显式同步」的场景才加自定义包（如滚轮选槽 `SelectSpellSlotPayload`）。
- 服务端 → 客户端只同步**展示所需**的标量（如 `SyncWandStatsPayload` 的数值字段），不要同步整个对象，也不要冗余字段（能被组件带过去的信息就别塞进包里）。

---

## 7. 新增内容时的最小检查清单

新增一个法术（完整步骤见 [spell-development.md](spell-development.md)）：

- [ ] 法术类放在 `magic/projectile/` 或 `magic/modifier/`，`id` 为 snake_case 且与资源同名
- [ ] 在 `MagicManager.init` 中 `register { Xxx() }`
- [ ] 资源三件套：`items/<id>.json`、`models/item/<id>.json`、`textures/item/<id>.png`
- [ ] 语言文件：`zh_cn.json` 与 `en_us.json` 至少补 `item.primalspells.<id>` 与 `lore.primalspells.<id>.desc`
- [ ] 新效果声明正确的 `EffectPhase`（改速度→默认；写位置→`OVERRIDE`；读最终位置→`VISUAL`）
- [ ] `./gradlew build` 通过

提交前（对所有改动）：

- [ ] `./gradlew build` 通过（重构或改包名后必须重跑）
- [ ] 全项目 grep 无旧包名残留（`magic.revise`、`primalspells.utils` 等已废弃路径）
- [ ] 未在公共侧引用 `client/` 类；新增的 S→C 包走 `ClientPayloadSink`
- [ ] 改动过的资源键（id）与语言键没有留下死键（无人引用的 key 直接删除）
- [ ] 新增的按 id 缓存（客户端/服务端静态表）有清理点（登出/卸载）
- [ ] 提交信息符合 §10 的 `<目的>：<简短描述>` 格式（简短、不复述具体改动）

---

## 8. 本次结构重构的旧 → 新对照（迁移记录）

| 旧位置 | 新位置 | 说明 |
|---|---|---|
| `magic/Magic.kt`, `MagicStatus.kt`, `MagicManager.kt` | `magic/core/` | 对象模型与调度 |
| `magic/Revise.kt` → 类 `Revise` | `magic/core/Modifier.kt` → 类 `Modifier` | 语义是「修饰器」，不是「修订」 |
| `magic/Projectile.kt` | `magic/projectile/Projectile.kt` | 与子类同包 |
| `magic/revise/` | `magic/modifier/` | 27 个修饰器；`ReciprocateRevise`→`Reciprocate`、`SineWaveRevise`→`SineWave` |
| `magic/effect/*`（单层平铺） | `magic/effect/{geometry,lifecycle,visual}/` | `BaseEffect.kt` 留在 `effect/` 根 |
| `ModDamageTypes.kt`（包根） | `registry/ModDamageTypes.kt` | 注册类统一归口 |
| `item/ModItems.kt`, `item/ModMenus.kt` | `registry/` | 同上 |
| `block/ModBlocks.kt` | `registry/ModBlocks.kt` | 同上 |
| `entity/ModEntities.kt` | `registry/ModEntities.kt` | 同上 |
| `item/SpellPouchMenu.kt`, `item/SpellPouchInventory.kt` | `item/menu/` | 与物品强耦合的容器实现 |
| `utils/` | `util/` | 单数命名 |
| `client/entity/`（Model/Renderer/RenderState） | `client/render/` | 与 `entity/` 区分：这是表现层 |
| `client/WandHudRenderer.kt` | `client/hud/` | HUD |
| `client/ClientTooltipHandler.kt`, `WandSpellTooltip.kt`, `PouchTooltip.kt`, `WandTooltipTracker.kt` | `client/tooltip/` | tooltip |
| `client/SpellPouchScreen.kt` | `client/screen/` | 屏幕 |
| `client/ClientContainerDragHandler.kt` | `client/input/` | 输入交互 |
| `event/WandEventHandler.kt` 的 `onMouseScrolled` | `client/input/WandScrollHandler.kt` | 纯客户端事件改由 `PrimalSpellsClient` 注册 |
| `event/WandEventHandler.getWandData` | `item/WandItem.getWandData` | 读组件，与侧无关，归属物品 |
| `effects-api-design.md`（仓库根） | `docs/design/effects-api.md` | 设计文档归入 docs/ |

**未改动**（有意保留）：法术 `id`、资源路径、`primalspells.mixins.json` 的 `mixin` 包名、`src/main/java/.../mixin/` 的 Java 包名、`neoforge.mods.toml` 的 modId。

> **后续变更提示**：上表中若干目标文件在随后的修复轮次中被删除或改名，以当前目录树（§1）为准 ——
> `item/menu/*`（小包容器菜单）、`registry/ModMenus.kt`、`client/screen/SpellPouchScreen.kt`
> 随「小包改用与法杖一致的物品交互」删除；`client/tooltip/WandTooltipTracker.kt` → `SpellSlotTracker.kt`；
> `client/input/WandScrollHandler.kt` → `SpellSlotScrollHandler.kt`；
> `block/WandEditingTableBlock.kt` 与 `registry/ModBlocks.kt`（连同方块资源）已整体删除；
> `magic/modifier/SineWave.kt` → `Serpentine.kt`（法术 id 仍为 `sine_wave`）。

---

## 9. 外部依赖政策：ParticleDrawing（硬性）

`particledrawing` 是本模组的**必需前置**（`neoforge.mods.toml`：`required` + `AFTER`），
仍是 **ALPHA**，当前锁定版本见 `gradle.properties` 的 `particledrawing_version`（现为 `1.0.13-ALPHA`）。
它是粒子能力的**唯一实现方**，PrimalSpells 只负责接线。

**红线：遇到它的 API 缺陷 / 签名不符 / 能力缺失时，不得在本模组自行编写另一套实现。**

- **顺序（2026-10-04 补，硬性）**：先按 [design/effects-api.md](design/effects-api.md) §0.1 的三段式走 ——
  ① 写清**目标模型**（我们想表达什么，与 PD 无关）；② 对着 PD 的**现有能力**（含"哪条通路才是逐帧的"）
  列出缺口并登记到 [issues/pd-api-requests.md](issues/pd-api-requests.md)；③ **然后**才选临时降级，
  并在代码注释里标明"这是降级、需求落地后删"。
  **先绕后记账**（碰到边界才补一条坑）是本项目此前最大的结构性问题：它让每次观感调参都变成改代码，
  而且绕法自己会制造新缺陷（相位差、缺颗空档、淡出不匀）。

- 先**停止**该接入点的尝试（不要猜签名反复试、不要反射绕过、不要改 `lib/` 下的 jar、
  不要把它的逻辑复制/内联到本仓库、不要用别的机制伪装实现同一 API 语义）。
- 然后在本次产出末尾列出**「ParticleDrawing API 修改需求」清单**，并登记到
  [issues/pd-api-requests.md](issues/pd-api-requests.md)（永久落盘，不能只写在聊天里）。
  **出现时机**：**仅当本次功能要求涉及 ParticleDrawing API 时**（特效接线、升级导致 API 变化、需求要求用它的能力）；
  纯配置/版本号/其它系统的改动**不要**附该清单，避免噪音。
  每条形如：需求标题 / 目标能力 / 现有 API 为什么不够（类 + 方法 + 编译错误或运行现象）/ 期望签名草案 /
  影响面 / 临时降级（含恢复条件）。
- 允许的例外：用原版 `ServerLevel.sendParticles` 实现**本就不属于 ParticleDrawing 职责**的效果
  （如爆炸/黑洞/重力场的球体采样，统一走 `util/ParticleShapes.kt`）；禁止的是「把 ParticleDrawing 该做的事在本侧重生一份」。
- 接线前先看：[design/effects-api.md](design/effects-api.md) §0.1（**模型 → 能力 → 降级**三段式）、
  §0.2（什么归 PD、什么归本模组）、§0.3（降级细则/现有范式表）—— 集合类粒子用 `ParticleBatch`、
  锚定用 `attachTo`、**不要自研粒子集合**（第 10 轮删除的 `ParticleSwarm` 就是反例：自写的句柄/寿命簿记接连引出四个 bug）。
- 升级依赖的固定流程（都用 `gradle.properties` 做单一数据源，不再手改 `mods.toml`）：
  新 jar 放 `lib/` → 改 `particledrawing_version`（jar 文件名版本段）与 `particledrawing_version_range`
  （下限 = 该版本；**预发布必须带标识**，如当前的 `[1.0.13-ALPHA,)`，写 `[1.0.13,)` 不匹配 `1.0.13-ALPHA`）→
  `./gradlew build --no-build-cache`；
  **构建失败就回到上面两条（停下 + 提需求），不要就地绕开**。

完整政策（含字段模板与版本纪律）见 [design/effects-api.md](design/effects-api.md) §0。

---

## 10. Git 提交信息格式（硬性）

一次提交只表达**一个目的**。格式：

```
<目的>：<简短描述>
```

- `<目的>` 从下面这几个词里选（不要自造、不要用 `feat:`/`fix:` 等英文前缀）：
  `新增` / `修复` / `优化` / `重构` / `文档` / `测试` / `构建` / `杂项`。
- 分隔符用**全角冒号** `：`（不是 `:`）。整句中文，专有名词/标识符可保留英文。
- `<简短描述>` **只说明大概干了什么**：不列具体改动内容、不点文件名/类名、不写实现细节、
  不写"为什么"；**一句、尽量 ≤ 20 字**，结尾不加句号。
- 不使用 body/列表罗列改动 —— 细节由 `diff` 与 `docs/` 承载；需要背景说明的决策写进
  [issues/open.md](issues/open.md) 或对应文档，而不是塞进提交信息。
- 破坏性变更不需要额外标记（本项目允许不兼容旧版），但应在 `docs/` 里留痕。

示例：

```
修复：粒子渲染异常的问题
优化：减少查询法术次数以优化性能
重构：整理包结构与文档
文档：补充法术开发指南
新增：法术小包
构建：升级前置模组版本
```
