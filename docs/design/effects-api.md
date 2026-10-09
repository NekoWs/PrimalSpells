# 播放器模组特效 API 设计方案（ParticleDrawing × PrimalSpells）

> 第53轮已改为MC实色硬边方粒；下面早期柔光/alpha渐隐说明仅供历史参考，当前规范见 [minecraft-visuals.md](minecraft-visuals.md)。
## 0. 硬性政策：ParticleDrawing 的问题一律「停下 + 提需求」，**不得自行另写一套**（§0 优先于本文其余内容）

> **改能量/爆炸类特效前先看 `.refs/explosion-vfx/`**：用户提供的目标观感（8 帧参考 + 逐帧说明 + 相位拆解；本地目录、不入库）。这些历史外观能力已补齐；第62轮已接入组缩放接续与移动锚点插值；未决需求见 [../issues/pd-api-requests.md](../issues/pd-api-requests.md)。

ParticleDrawing（`particledrawing`）是本模组的**必需前置**，且仍在 **ALPHA** 迭代期
（当前锁定版本见 `gradle.properties` 的 `particledrawing_version`，现为 `1.0.15-ALPHA`）。
它是粒子能力的**唯一实现方**；PrimalSpells 只做接线与使用。

**当前锁定版本：`1.0.15-ALPHA`**（见 `gradle.properties`；本文提到具体 API 时以 `lib/` 里的
`-sources.jar` 为准，不看旧版本记忆）。

**触发条件**（满足任一即进入本政策）：

- 编译期：需要的 API 不存在、签名不匹配、`internal`/不可见、缺 KDoc 导致无法确认语义；
- 运行期：行为与文档/预期不符（崩溃、静默无效果、参数被忽略、状态错乱、网络/线程语义不明）；
- 能力缺失：我们需要的能力在 ParticleDrawing 中**根本没有**（而非我们用错）。

**出现时机（避免噪音）**：

「ParticleDrawing API 修改需求」清单**只在本次功能要求涉及 ParticleDrawing API 时**才出现在产出末尾，即：

- 新增/修改粒子特效接线（`magic/effect/visual/*`、`wand.status.particleManager` 相关）；
- 升级前置版本、且升级涉及 API 变化（编译失败、签名变动、行为变化）；
- 需求本身要求使用 ParticleDrawing 的某项能力。

以下情况**不要**附该清单（按正常流程做完即可）：纯配置/版本号/依赖文件名调整、法术数值与修饰器、
HUD/tooltip、无人机、资源与语言键等与 PD API 无关的工作。

> 区分清楚：「**不得自行另写一套实现**」这条红线**始终有效**（只要工作碰了 PD API）；
> 有时机限制的只是「产出末尾的清单」与「登记到 [../issues/pd-api-requests.md](../issues/pd-api-requests.md)」。

### 0.1 读法：**目标模型 → 现有能力 → 临时降级**（2026-10-04 重写）

本文过去的写法是「碰到边界就记一条坑」，于是越写越像一份**绕行指南**：调一次观感就要改一次代码，
而每次绕法自己又会制造新缺陷（相位差、缺颗空档、淡出不匀、逐 tick 跳变）。现在改成三段式，按顺序读：

| 段 | 回答什么 | 在哪 | 权威出处 |
|---|---|---|---|
| **A 目标模型** | 我们**想表达**什么（与 PD 无关的意图层） | §0.1.1 | 本节 |
| **B 现有能力** | PD 1.0.13 **实际给到**什么（尤其"哪条通路才是逐帧的"） | §0.1.2、§0.4 | PD 源码（`lib/particledrawing-*-sources.jar`） |
| **C 临时降级** | 缺口期间我们**怎么绕**、代价是什么、落地后删什么 | §0.1.3、§0.3 | 本节 |

**顺序不可颠倒**：先定 A、对着 B 列出缺口（→ 登记到 [../issues/pd-api-requests.md](../issues/pd-api-requests.md)），
**然后**才选 C。碰边界才补记 C，是本文件此前最大的结构性问题。

#### 0.1.1 A｜目标模型：四个原语

| 原语 | 我们想要的语义 | 谁在用 |
|---|---|---|
| **发射器 Emitter** | 挂在锚点上，按**里程或时间**发射；**出现/消隐逐帧**；服务端只声明一次 | 拖尾（`Trajectory`）、爆炸火星、护盾碎屑 |
| **寿命曲线 Curve** | 每颗粒子的 size / alpha / color 随**自己的寿命**演化（关键帧或 easing），写进 spawn 声明 | 拖尾余痕、退场余晖、烟雾 |
| **锚点 Anchor** | 位置 + 朝向 + 速度 + **上一位置**，且插值语义对所有粒子**一致** | 拖尾头部/本体、护盾面板组 |
| **批量 Batch** | 一整条尾迹一次下发；批量 spawn + 批量曲线 | 密集尾迹（≈23 颗/投射物） |

**不变量**：① 确定性（同种子两次录制出同一画面）；② 带宽 O(声明) 而非 O(粒子)；③ **观感参数化**——
调长度/密度/尺寸曲线只改参数、不改代码（这条正是前几轮反复改写的成本来源）。

#### 0.1.2 B｜现有能力：PD 有两条通路，差别是"逐帧"还是"逐 tick"

| 能力 | **运行时通路**（`ParticleManager.create()` + `ParticleHandle`） | **动画/脚本通路**（`Anchor` + 指令/表达式） |
|---|---|---|
| 求值频率 | **每 game tick 推进**；非运动粒子还按 `particleBatchSize` **轮转分批**（粒子多时会饿死部分粒子，PD 注释原话"时走时停"） | **每渲染帧**（`ClientAnimationManager.frameTick(partialTick)`，`ProcessClock` 推帧内毫秒） |
| 出现时机 | 服务端每 tick 一颗颗 `spawn()`；`delay(ticks: Int)` 只有整 tick | 由动画时钟/指令决定 |
| 位置插值 | `track` 按 partialTick 插值；**程序化粒子是绝对坐标、不插值** | 锚点相对坐标，渲染层按 partialTick 插值 |
| 寿命曲线 | 无声明式曲线；靠 `recolor`/`resize` 各下 1 个更新包补间 | `FadeIn/FadeOut/Recolor/ScaleBy/MovePath/Pulse/Expression`（按 ms） |
| 批量 | `ParticleBatch`（力/速度/位置）；**spawn 与曲线仍逐颗** | 组级 `fadeOut/scale/movePath/recolor` |
| 外观 | 贴图/UV/朝向/加色**生成时定死**；颜色（含 alpha）与 scalar 缩放可后改 | 同 |

**一句话**：**"高刷"不是缺几个函数，而是运行时粒子没有逐帧求值通路**；PD 里逐帧、曲线、锚点、批量
这四样**都已经存在**，只是原先锁在预制动画/脚本那条通路上。

> ✅ **2026-10-04：PD 1.0.14-ALPHA 把这条打通了** —— 多了**第三条通路：运行时发射器**
> （`api/ParticleEmitter` + `EmitterHandle`，`ClientEmitterManager.frameTick(partialTick)` 逐渲染帧发射、
> `Anchor.Movable` 用「上一 tick 位置 + 速度 × partialTick」取本帧位置）。于是"逐帧、曲线、锚点、批量"
> 运行时也能用，本侧第 48 轮已把拖尾迁上去（见 §0.1.3 的作废说明与 [../issues/resolved.md](../issues/resolved.md) 附录 B）。

#### 0.1.3 C｜临时降级（**2026-10-04 起整段作废**）

> ⚠️ 下表四条缺口**已由 PD 1.0.14-ALPHA 全部补齐**（发射器 / `prevPosition` / 寿命曲线 / `spawnAll`），
> 本侧已迁移。保留此表只为读懂旧代码与旧提交；**新接线不要再照着绕**。三条通路的选择见 §0.1.2。

| 缺口（对应需求组条目） | 本侧临时降级 | 代价 | 落地后 |
|---|---|---|---|
| 运行时发射器（需求 1） | 服务端每 tick 按里程采样、逐颗 `create().spawn()` + `recolor`/`resize` | 出现是 20Hz（高刷下"一跳一跳"）；每颗 3 个包 | 整段换成 `emitter(...)` 声明 |
| 插值语义不统一（需求 2） | 拖尾采样段**整体延迟一 tick** | 拖尾恒定滞后本体 ≈0.4 格 | 删掉延迟、直接对齐 |
| spawn 内无曲线（需求 3） | 生成时把"头部白热 / 末端压暗"**烘焙**进颜色与 alpha | 调色 = 改代码；淡出另付 2 个包 | 换成 `fadeOut/shrinkTo/curve` |
| 无批量 spawn（需求 4） | 逐颗下发 | 密集尾迹包数线性增长 | 一次下发 |

> ⚠️ **历史包袱提醒**：§0.3 的「接线范式」与 §0.4 的「必踩的坑」都是上表的**降级细则** ——
> 它们回答"现在怎么绕"，不回答"PD 能力如何"。读它们时别把降级当成能力事实。

### 0.2 职责边界判定准则（什么属于 PD、什么属于本模组）

先看两张对照。本模组原先自研的 `ParticleSwarm` 已经**删除**（2026-09-30 第 10 轮迁移到 `ParticleBatch`），
但下面这张「取向对照」保留 —— 它正是当初判定「**不能直接迁移到 `ParticleGroup`**」的依据：

**⚠️ 结论：`ParticleGroup` 与「程序化粒子流」不是同一种抽象。**
`ParticleGroup` 面向**编排式动画**（成员基本固定、组级统一变换、指令流一次下发、客户端本地求值 —— 适合 `.pdrawc` 那类"播放"），
而本模组要的是**程序化粒子流**（成员逐 tick 增删、每粒子独立速度/轨迹/寿命、按位置条件回收）。
差异与由此产生的 PD 侧需求见 [../issues/README.md](../issues/pd-api-requests.md) —— 其中「程序化粒子集」一条已由 PD 以 **`ParticleBatch`** 落地（1.0.9-ALPHA），本模组已迁移完毕。

| 维度 | `ParticleGroup`（PD，编排式动画） | `ParticleBatch`（PD，程序化粒子集） | 这套用在哪 |
|---|---|---|---|
| 成员管理 | `add()` 会把 `armed` 置 false → **重发全量受控清单** | `add`/`addAll`/`remove` 增量登记、随时增删，**不重发全量** | 每 tick 增删 1~3 个成员：`Batch` |
| 每粒子状态 | 组级**统一**变换（`spin/rotate/scale/pulse/move/movePath`） | 位置/速度按句柄读（`forEach { i, h, pos, vel -> }`），位置用**一次** `trackAll` 批量写 | 「每粒子各自被吸向中心」：`Batch` |
| 条件回收 | 整组 `remove()` / `destroyAfter(ticks)` | `removeIf { h, pos, vel -> … }`，过期成员自动出列 | 按位置/速度回收：`Batch` |
| 锚定身份 | `followEntity(uuid, offset, local)`、`defineEntity` | 逐粒子 `attachTo(Entity / uuid / entityId, offset)`（1.0.9 起三入口统一） | 形状刚性、零逐 tick 包：**整组跟随用 `followEntity`（护盾球壳在用）**，零散粒子才用 `attachTo` |
| 补量/上限 | 组不负责补量 | `ensureSize(n, spawner)`：被维度上限拒了就停下，下次再补 | 球壳补齐：`Batch.ensureSize` |
| 组级动画 | **`spin`/`pulse`/`fadeOut`/`destroyAfter` 等零带宽编排** | 无（Batch 只走位置/速度/力） | 护盾的自转就用组：**第 12 轮已接线**（`Draw.sphere` + `followEntity` + `spin`，0 包/tick） |

**判定准则**：

| 归类 | 判定标准 | 例子 |
|---|---|---|
| **属于 PD**（用现成 API，别再自己写） | 与「粒子集合的通用机制」有关：批量成员管理、组跟随实体/旋转/淡出/延时、寿命与可见性、上限与拒绝处理、带宽优化 | `ParticleBatch`（成员簿记）、`ParticleGroup` 的组级编排、`trackAll`、`attachTo`、`applyForce` |
| **属于本模组**（玩法语义，PD 不该知道） | 只有游戏知道的东西：半径/数量/颜色/寿命取值、力的公式、回收判据、被动的持有/放下时机与淡出时长 | `BlackHole` 的吸力与阻尼、`GravityField` 的下落与下半球回收、`EnergyShield` 的 4 tick 延迟、`ParticleShapes.fibonacciSphere` 的选点 |
| **灰区（已收敛，无遗留）** | 曾经留在使用方的「随距离变化的每粒子受力」已被 PD 收走：1.0.11-ALPHA 的 `setVelocityAll` / `applyForceAll` **一次包覆盖全组**（按 `handles()` 顺序对齐、逐玩家按可见性裁剪） | `BlackHole` 第 12 轮改用 `setVelocityAll`：**速度每 tick 由本模组重算，位置积分归 PD**，本模组不再持有任何跨 tick 粒子状态（见 [../issues/resolved.md](../issues/resolved.md) 第 12 轮） |

⚠️ **判定顺序**：先查 PD 是否已有现成能力（`api/` 全量方法见 `ParticleHandle`/`ParticleGroup`/`ParticleBatch`/`ParticleManager` 的 KDoc，或 `lib/*-sources.jar`），
再决定要不要在本模组写封装。**「自己维护句柄表 + 逐粒子操作」通常意味着漏看了组/集 API** ——
本项目就踩过：自研 `ParticleSwarm` 的锚定模式本可用 `attachTo` 直接表达，结果多写了整套簿记，
并因此接连踩了「捕获偏移」「索引配对」「重复身体偏移」「重建滑入」四个坑（见 [../issues/README.md](../issues/resolved.md)）。

### 0.3 C｜降级细则：现有接线范式（接线前先看这张表；属 §0.1.3）

| 需求 | 用哪套 API | 每 tick 带宽 | 本项目落点 |
|---|---|---|---|
| 粒子固定不动 | `create()…spawn()`（不设速度、不发位置指令） | 0 | `GravityField` 范围球壳 |
| 匀速直线运动 | 生成时一次 `setVelocity(v)`，之后交给 PD 逐 tick 积分 | 0 | `GravityField` 下落粒子、`Trajectory` 拖尾 |
| 跟随一个非实体点（逐 tick 变位置） | 单体 `handle.track(pos)`；**多粒子用一次 `ParticleBatch.trackAll(positions)`** | 1 包/tick（批量，与粒子数无关） | 本模组没有「自己算位置」的逐 tick 跟随者了：本体标记改由 `Trajectory` 的头部 `track` 承担 |
| **每 tick 变化的每粒子速度**（受力随位置变化） | **`ParticleBatch.setVelocityAll(velocities)`**（1.0.10 起）：速度每 tick 重算、一包下发，**位置积分归 PD**（`pos += vel`） | **1 包/tick**（与粒子数无关） | `BlackHole` 被吸入粒子（`v ← v·阻尼 + 向心加速度`） |
| **组级编排：跟随 + 旋转/淡入淡出/平移** | **`Draw.<形状>(...)` 返回 `ParticleGroup`**，再 `.followEntity(uuid, offset)` / `.spin(axis, radPerTick)` / `.fadeIn` / `.fadeOut(ticks, removeAfter)` / `.move(delta, ticks, easing)`；都在**客户端本地求值**。**1.0.12 起口径变干净了**：`move`/`movePath` **不再改 arm 基准轴心**（轴心只由 `setPivot`/`followEntity` 决定）；`scale`/`pulse` **连「粒子到轴心的距离」一起缩放**；多条 `rotate`/`spin` 的角度**累加**（后一条不覆盖前一条的相位）；**增量追加的指令按「从现在起」读**（长寿组运行期 `delay(n).fadeOut(m)` 就是「n tick 后开始、m tick 淡完」）。顺序仍建议「先绑定轴心再铺成员」，那样最直观。⚠️ **尺寸别按 `scale` 当格数估**：`quadSize = scale × 0.1 × texScale`，实测 `texScale ≈ 2.1` → 实际格数 ≈ `scale × 0.213` | **0 包/tick** | `EnergyShield`（第 16 轮：**每块五/六边形面板一个组**，固定球体 + 逐块 `fadeIn`/`fadeOut` + `spin`）、`BlackHole` 退场后的 `ParticleBatch`、`GravityField` 球壳（`Draw.sphere` + `spin`）与波环/井环 |
| 恒定 / 分段恒定的力 | `handle.applyForce(accel, ticks)`；整组用 `ParticleBatch.applyForceAll(加速列表, ticks)`：两端同规则积分，只在施力时发一次 | 施力次数 | `GravityField` 下坠粒子（第 15 轮起：`spawnParticle(..., acceleration = Vec3(0,-0.01,0))` → 一次施力、两端逐 tick 积分，**0 逐 tick 包**）；黑洞的力随距离变化，仍用 `setVelocityAll` |
| 锚在一个实体身上（形状刚性） | 整组用 `ParticleGroup.followEntity(uuid, offset, local)`；零散粒子用 `handle.attachTo(Entity/uuid/entityId, 偏移)`（1.0.9 起身份统一） | **0** | `EnergyShield` 球壳已改用**组**（要自转）；`attachTo` 当前无使用者 |
| **逐粒子缓动移动**（一次性指令） | `handle.move(target, ticks, easing)`：每颗粒子一条指令，缓动在**客户端逐帧求值**；带宽 = 粒子数（一次性），不是每 tick | 一次性 | `BlackHole` **进场**（整壳 144 颗从球心长到球面）；⚠️ 服务端会**立即**把权威位置设为目标（`takeOverPosition`），所以只适合「位置无人读取」的纯视觉粒子 |
| **程序化粒子集**（成员逐 tick 增删、每粒子独立受力、按位置/速度回收） | **`ParticleBatch(manager)`**（1.0.9 起，1.0.10 补批量设速/施力）：`add`/`addAll`/`remove`/`track`/`trackAll`/`trackEach`/`setVelocity(All)`/`applyForce(All)`/`forEach`/`removeIf`/`ensureSize`/`evictDead`/`clear`；位置与速度一律读 PD 权威值，过期成员自动出列 | 取决于内部批量调用（`trackAll`/`setVelocityAll` 为 1 包/tick） | **已在用**：`BlackHole`（球壳 + 被吸入粒子）、`GravityField`（球壳 + 下落粒子）。见 [../issues/resolved.md](../issues/resolved.md) 第 10/12 轮 |
| **运行期才决定何时开始的组级动画**（长寿组） | 1.0.12 起**直接写**：增量追加的指令按「从现在起」读（`GroupClock`），`fadeOut(ticks, removeAfter = true)` 的销毁也按同一口径从当下起算 | 0 | `EnergyShield` 的消散波（`delay(阶梯) + fadeOut(5, removeAfter = true)`） |
| **给「已经转了一阵子」的组补相位** | 1.0.12 起**可以叠加**：多条 `rotate`/`spin` 的角度累加，「自转一阵 → 补一段一次性旋转 → 继续自转」能接上（`durationTicks = 0` 即瞬时补相位） | — | 暂无使用者（护盾消散不重建组，本来就不需要补相位） |
| 编辑器导出的 `.pdrawc` 动画 | `Effects.play(...)` + `Anchor`（`Fixed`/`Entity`/`Movable`） | — | **尚未接线** |

六条硬要点：

1. **能在客户端本地解析的就别逐 tick 发**：只要粒子位置是「实体 + 固定偏移」，用 `attachTo`
   （**0 包/tick**、天然刚性）；需要逐 tick 变化的位置，再用 `trackAll` 批量下发。
2. **位置指令与速度/力不要混用**：`move`/`track`/`trackAll` 会**停掉**该粒子上的速度与力
   （PD `ServerParticleEngine.takeOverPosition` 的明确语义），所以同一粒子要么位置驱动、要么速度/力驱动。
   ⚠️ 推论：**用 `trackAll` 驱动的粒子，其速度只有使用方自己知道**（PD 里已被清零）——
   `BlackHole` 的「吸力随距离变化」正落在这个角落里，代价是自持一张「粒子 id → 速度」表。
3. **多粒子优先用批量 API**：`trackAll` 1 包/tick ↔ 逐粒子 `track` N 包/tick。
   本项目现状：护盾 **0 包**、黑洞 **1 包**、重力场 **0 包**（不随粒子数增长）。
   ⚠️ 组本身也是**开销单位**：PD 每次 arm 一个组打一行 INFO（见 [../issues/pd-api-requests.md](../issues/pd-api-requests.md)），
   所以**同一批粒子要按"变换是否相同"合并**——护盾第 39 轮把玻璃面板按「飘散位移」分组：
   位移相同的片共用一个组（格挡 / 破口轮廓 / 爆炸碎裂各 1 个组），只有"每片一个方向"才一片一组。
4. **引擎有每维度粒子上限**：超限时 `spawn()` 返回 `null`，使用方必须能接受「丢粒子」而不是当成错误；
   `ParticleBatch.ensureSize` 的约定正好吻合（返回 null 就停下，下次调用再补，不空转）。
5. **不要把「偏移」按列表索引配对**：一旦有个别 `spawn()` 被拒或粒子被重建，索引就会错位
   （表现为瞬移/形状剪切 —— 本项目第 5 轮就是这么踩的坑）。偏移要**绑在句柄上**，或者干脆用 `attachTo`。
6. **暂停语义已修，但仍建议按需选型**：PD 已修「服务端暂停时速度驱动粒子被客户端外推」；
   黑洞仍用位置驱动，是因为吸力随距离变化、且「抵达中心才消失」需要位置完全可控，不是因为暂停问题。

**硬性要求**：

1. **不要尝试自行编写另一套实现**（这是本政策的红线）。禁止的做法包括：
   - 在本模组内自研/内联一份粒子系统或复制 ParticleDrawing 的逻辑；
   - 反射、`Class.forName`、改字节码、往 `lib/` 里改 jar、把 jar 反编译后内联源码等绕过手段；
   - 用别的机制**假装**实现了同一个 API 语义以掩盖缺口（例如：把本应由 `.pdrawc` 动画承担的“特效播放”用原版 `sendParticles` 拼一个形似的东西，然后宣称已接线）。
   - 唯一允许的降级是：**明确标注为临时降级**、写清恢复条件、并同时提出 API 需求（见第 3/4 条）。
     注意边界：用原版 `ServerLevel.sendParticles` 实现**本就不属于 ParticleDrawing 职责**的原版效果是允许的，
     也是现状（如爆炸/黑洞/重力场的球体采样，统一走 `util/ParticleShapes.kt`）。
2. **停止在该接入点继续试错**：不要为一个缺失/异常的 API 反复猜签名、反复重试；不要在 PrimalSpells 侧「造轮子填坑」。
3. **在本次工作产出末尾列出「ParticleDrawing API 修改需求」清单**（**仅当本次功能要求涉及 ParticleDrawing API 时**，见上文「出现时机」），
   并**同步登记到 [../issues/README.md](../issues/pd-api-requests.md)** 的固定章节
   （永久落盘；只写在聊天/提交说明里不算数）。
4. **每条需求必须写成可执行的形式**（缺任何一项都不算合格）：

   | 字段 | 要求 |
   |---|---|
   | 需求标题 | 一句话说明要什么能力 |
   | 目标能力 | 我们在 PrimalSpells 里想实现的效果（附触发场景） |
   | 现有 API 为什么不够 | 具体到类 + 方法/属性 + 缺失点（编译错误原文或运行现象 + 复现步骤） |
   | 期望 API 形态 | 签名草案（类名/函数签名/参数与返回、是否需要服务端/客户端两侧、线程与生命周期约定） |
   | 影响面 | 本模组中受影响文件（接线点、效果类） |
   | 临时降级 | 有则写清降级范围与**恢复条件**；没有就写「无（该功能暂不可用）」 |

5. **升级依赖的固定流程**（改完必须重跑构建）：

   1. 新 jar 放进 `lib/`（含 `-sources.jar` 更好，便于核对签名）；
   2. 改 `gradle.properties` 的 `particledrawing_version`（值 = jar 文件名中的版本段）
      与 `particledrawing_version_range`（= 写入 `mods.toml` 的 `versionRange`，由 `build.gradle` 插值）；
   3. `./gradlew build`（无缓存：`--no-build-cache`）。
   **若第 3 步失败 → 回到第 1/2 条**：不要就地绕过，改为提交 API 需求。

   ⚠️ **预发布版本的区间写法**：FML 用 Maven 版本语义，`1.0.8-ALPHA` 排序低于 `1.0.8`，因此
   下限必须写成预发布号本身（`[1.0.8-ALPHA,)`）；写成 `[1.0.8,)` 会在运行时报
   `requires particledrawing 1.0.8 or above / Currently, particledrawing is 1.0.8-ALPHA`。

6. **版本纪律**：ALPHA 期间允许对方破坏性变更。本模组只保证对**当前锁定版本**编译通过，
   不假设未来版本兼容；`versionRange` 下限始终等于当前锁定版本。升级后若出现 API 破坏，
   同样按本政策处理（不要为了让旧接线继续工作而在本侧造兼容层）。

---

> **文档状态（2026-09 结构重构时补记，2026-09-29 更新版本）**
> - 本文件原在仓库根（`effects-api-design.md`），现已归入 `docs/design/`。
> - 依赖版本：**1.0.14-ALPHA**（`lib/particledrawing-1.0.14-ALPHA.jar`，附 `-sources.jar`），
>   由 `gradle.properties` 的 `particledrawing_version` 驱动；下方正文中出现的 1.0.6 等版本号是**当时的历史记录**。
> - §10 中标记为 ⏳ 的「PrimalSpells 依赖升 1.0.6 + 迁移 `Trajectory.kt`」**已经完成**：`Trajectory` 使用 `ParticleManager.create()` Builder。
> - 1.0.9-ALPHA 相对 1.0.8 的 API 增量：**`api/ParticleBatch`**（程序化粒子集：`add`/`trackAll`/`trackEach`/`forEach`/`removeIf`/`ensureSize`/`evictDead`/`clear`）、
>   `ParticleGroup.followEntity(..., local)`（组级跟随可选随朝向）、`ParticleHandle.position()/applyForce()/attachTo()` 等（1.0.8 起）。`.pdrawc` 动画特效在 PrimalSpells 侧的接线仍未落地。
> - 1.0.11-ALPHA 相对 1.0.9 的 API 增量：批量运动指令 **`ParticleBatch.setVelocityAll(速度列表)` / `applyForceAll(加速列表, ticks = 1)`**
>   （各列表与 `handles()` 顺序一一对应，一包覆盖全组，逐玩家按可见性裁剪；`ParticleManager` 同名的 id 列表版本），
>   以及修正「`setVelocity`/`applyForce` 未解除实体锚点」（现在与 `move`/`track` 一致：运动指令接管位置）。
>   **第 12 轮已接入**：黑洞改用 `setVelocityAll`（速度每 tick 重算、位置积分交回 PD，删掉了自持的「粒子 id → 速度」表）。
> - **2026-09-30（第 16 轮，按实机反馈重改）**：黑洞进/退场改成**整壳径向运动**（进场用逐粒子缓动
>   `ParticleHandle.move`、退场用「同位置重新生成 + 向心初速 + 寿命兜底」），结构退回 `ParticleBatch`（去掉组的 `spin`）；
>   重力场球壳提亮/加密（alpha 70 → 120、96 → 120 颗）并换 `ParticleGroup` 加慢速自转，波环/井环压暗；
>   护盾从「面板飞进飞出」改成**固定球体逐块构建/消散**（每 tick 建 2 块、反向逐块 `fadeOut`），
>   粒子密度 2 → 3 点/棱（360 → 540 颗）。见 [../issues/resolved.md](../issues/resolved.md) 第 16 轮。
> - **2026-09-30（第 15 轮）**：黑洞球壳改用 `ParticleGroup`（144 粒、`fadeIn`+`spin`）并在进/退场各撒一批
>   「向心收拢」的一次性粒子；重力场下坠改用**恒定力**（一次施力、越落越快）并补了加速下压的波环与地面井环；
>   护盾球壳改成 `util/Polyhedra.kt` 的**截角二十面体**（12 五边形 + 20 六边形 = **32 个面板组**，`move` 展开/收起）。
>   本轮登记的 PD 需求见 [../issues/pd-api-requests.md](../issues/pd-api-requests.md)：
>   **`scale`/`pulse` 只缩尺寸不缩到轴心的距离**（与 KDoc 不符）、**`move` 会改 arm 基准轴心**（编排顺序陷阱）。
> - **2026-09-30（第 10 轮）**：本模组已按 §0.2/§0.3 完成迁移 —— 自研 `ParticleSwarm`（273 行）**已删除**，
>   三处接线改用 `ParticleBatch`，生成入口收敛为 `util/PdParticles.kt` 的
>   `ParticleManager.spawnParticle(...)`；随距离变化的每粒子受力当时仍留在使用方（该需求已由 1.0.10 的
>   `setVelocityAll`/`applyForceAll` 闭环，本模组已在第 12 轮迁移）。
> - 本文引用的包路径按重构前书写，现对应关系：`magic/effect/Trajectory.kt` → `magic/effect/visual/Trajectory.kt`；`magic/effect/*` 其余类分入 `magic/effect/{geometry,lifecycle}`；`magic/Revise.kt` → `magic/core/Modifier.kt`（类 `Revise` → `Modifier`）；`magic/Projectile.kt` → `magic/projectile/Projectile.kt`；`magic/{Magic,MagicStatus,MagicManager}.kt` → `magic/core/`；`item/ModItems.kt` → `registry/ModItems.kt`。完整对照见 [../conventions.md](../conventions.md) 第 8 节。


> 状态：**播放器侧 API 轮子已实现**（`particledrawing` 仓库，§5 全部落地并通过 `gradlew build` + 单测）。
> 魔法模组端（`primalspells`）动画/特效接线**后续再做**。
> 涉及仓库：
> - 播放器：`D:\Backup\IDEA Projects\ParticleDrawing`（`particledrawing`，当前锁定 1.0.15-ALPHA）
> - 魔法模组：`D:\Backup\IDEA Projects\PrimalSpells`（`primalspells`，MC 26.2 / NeoForge 26.2.0.59 / Java 25）

---

### 0.4 B｜外观能力（ParticleDrawing 1.0.13 起）与尺寸口径（属 §0.1.2 的能力事实）

第 29 轮起程序化粒子终于能"指定外观"。参考实现：`magic/effect/lifecycle/ExplosionFx.kt`
（爆炸「内爆 → 爆发」，对齐用户给的参考 `.refs/explosion-vfx/`）。**动特效前先读这一节。**

**能力入口**

| 想要 | 用法 |
|---|---|
| 柔边圆点 / 线段贴图 | `ParticleVisual().style(ParticleStyle.SOFT_DOT / LINE / SQUARE)`（内置 16×16，两端各自生成，不占带宽） |
| 自定义贴图 | `ParticleManager.registerTexture(name, pngBytes)`（幂等；**同名第一次生效**，换图请换名字；未知名字回落纯白方块、不丢粒子）。本模组已有用例：`primalspells:rune_spark`（`client/render/ParticleTextures.kt`，第 45 轮）。⚠️ 贴图 **16×16 ⇒ `texScale = 1`** ⇒ `anisoWorld(w, h)` 的 `w/h` **就是世界格整宽**；注册时机必须在**首个客户端 tick**（会碰 `RenderSystem`） |
| 逐粒子贴图 / 子矩形 UV | `Builder.texture(name)` / `.uv(u0,v0,u1,v1)`（或 `.visual(ParticleVisual().uv(...))`） |
| 拉长的丝线 | `anisoWorld(长, 粗)`（**世界格数**）+ `alignTo(from, to)`（长轴对齐到线段）+ `billboard(false)`。⚠️ **`alignTo` 会强制关掉广告牌**（PD KDoc 明说）：它只适合「转个方向看得出来」的**拉长形状**；对称点状精灵（圆点/菱形）用它**纯亏** —— 会变成固定朝向的薄片，侧看一条线、正视几乎看不见（第 46 轮踩过） |
| 一条折线一个调用 | `Draw.polyline(points, thickness, taper, texture, additive, glowing, colorFn, …)` —— **每段一颗粒子**，内部自动 `anisoWorld(段长, 粗)` + `alignTo`；`taper = true` 时自动用 `LINE` 贴图并两端收细 |
| 发光溢出 | `additive(true)`（加色）+ `glowing(true)`（全亮不受光照）—— **护盾玻璃在用**（第 41 轮：亮场景里靠色相与加色拉开对比度） |
| 分批出现 | `Builder.delay(ticks)`，或 `Draw.*` 的 `stagger` |
| **越靠后越暗** | **没有衰减通道** → 只能在**生成时**把颜色与 alpha 一起按「离头部的远近」压低（第 43 轮拖尾就是这么分层的） |
| 换外观 | **没有更新通道** → `remove()` 后重新生成 |

**尺寸口径（别再按 `scale` 当格数估）**

- 渲染整宽 = 编辑器尺寸 × **0.2 格** × 贴图尺寸系数；内置贴图 16×16 → 系数 1 → **整宽 = scale × 0.2 格**。
- `anisoWorld(w, h)` 收的是**世界格数**（渲染侧自己换算），**别再乘 0.2** ——
  第 29 轮把标称尺寸喂进去过，黑核直接大了 5 倍。
- 内置贴图**可见尺寸小于标称**（边缘柔化必然有半透明带）：`SOFT_DOT` 可见直径 ≈ **0.75 × 尺寸**、
  `LINE` 可见粗 ≈ **0.7 × 尺寸**（两端各 ≈ 0.2 × 长度渐隐）。密铺间距按「≤ 0.3 × 可见直径」给就无方块感/空隙。
  `ExplosionFx` 里的 `dotWorld()` / `lineWorld()` 就是这套反推。

**五个必踩的坑（降级期实测；每条都是 §0.1.3 某条降级的细则，不是 PD 的能力事实）**

1. **内置贴图要在渲染线程就绪之后再确保**：`ParticleManager.registerBuiltinTextures()` 会碰 `RenderSystem`，
   放在 `FMLClientSetupEvent` 里会抛 `Rendersystem called from wrong thread` 把客户端打崩；
   `PrimalSpellsClient` 改在**首个客户端 tick**里做一次（布尔量保证只做一次）。
   不做的话客户端解析不到 `particledrawing:builtin/*` → 回落纯白方块，表现就是"球壳全是硬边方块"。
2. **外观没有更新通道**（这正是「零逐 tick 开销」的前提）：想换贴图/尺寸/朝向只能 `remove()` 后重新生成。
   所以"边动边变"要用**组级一次性编排**（`scale` 连「到轴心的距离」一起缩 = 向心收拢 + 变短）或逐粒子 `move` / `delay`。
3. **`followEntity(uuid, offset)` 的 `offset` 不改变渲染位置**（第 40 轮踩）：它不是"设成 实体+偏移"，
   而是 `pivot = offset + pivot`（把 offset **累加**到当时的组轴心上）；客户端 arm 时算
   `rel = 成员 spawn 位置 − pivot`、逐帧渲染 `实体位置 + offset + rel` —— offset 减一次加一次，**净效果为零**。
   想要"跟随 + 抬高到球心"必须**把轴心取在实体脚底**（`createGroup(holder.position())` + 成员铺在球心处），
   这样 `rel` 里自然带上"脚底 → 球心"的抬升；传 offset 只会让 `rel` 少掉同样的量（表现为整组下沉/偏移）。
   推论：**跟随时渲染位置 = 实体位置 + (成员 spawn 位置 − createGroup 时的轴心)**，与 `offsetFromPivot`
   无关（客户端 arm 只读 spawn 位置），所以"跟不跟、抬多高"全由这两个量决定。

4. **`track` 驱动的位置在客户端是「落后一格」的**（第 47 轮踩，与 1.0.13 无关，是原版插值语义）：
   `ParticleHandle.track(...)` 走 PD 的 `TrackBuffer`，客户端把相邻两条权威位置当作
   `xo → x` 两端点、按 partialTick 插值 ⇒ **屏幕上看到的位置最多落后权威位置一整 tick**
   （`partialTick = 0` 时正好落后一格位移）。而程序化粒子是「生成即静止、按绝对位置渲染」的，
   两者混用时会出现「尾迹跑到本体前面一格」。要么把尾迹的锚点也放在**上一 tick 的权威位置**上
   （`Trajectory` 的做法），要么让这一层也只由同一种驱动方式产生。

5. **加法混合下 alpha 就是亮度权重，而且没有「随时间淡出」这一说**（第 43 轮踩）：
   用 `additive(true)` 的粒子，`Color.of(..., a)` 的 `a` 直接决定它叠上去多亮
   （PD `BridgeParticle.getLayer` 里 `translucent = additive || alpha < 1`，走 `ADDITIVE_PARTICLE` 管线）；
   而**逐 tick 改颜色/透明度没有入口**（`Builder` 的外观生成时定死），`lifetime` 到点又是**整颗消失**、
   不是渐隐 —— 这两条合起来推出一个反直觉的结论：**「越靠尾越暗」只能在生成时一次性写进颜色与 alpha**。
   所以 `Trajectory` 是按「离头部的距离」分组给色的（头部 `hot=1` → 全白不透明；尾部 `hot≈0.2` → 主色 + 压暗 + 半透），
   而不是「先生成再逐 tick 变暗」。⚠️ 别把这条当成缺陷去提需求：Noita 式拖尾本来就可以用「颜色分层 + 寿命」表达。

## 第 49 轮区域视觉接线（2026-10-08）

目标、PD 现有能力、造型与生命周期预算见 [field-effects.md](field-effects.md)。
黑洞固定结构已迁到 `ParticleGroup`，吸入星尘继续用 `ParticleBatch`；
重力场使用 `SOFT_DOT/LINE`、`Draw.curve/polyline` 与组级淡出。本轮未遇到新的 PD 能力缺口。
旧 §0.3 中黑洞逐粒子进退场与三拍重建属于历史实现，当前不再使用。
第 50 轮追加固定几何交叉透镜弧；用户明确先看仿作观感，不要求随每位玩家视角变化。
现有 PD 能力满足本轮目标，无新增 API 需求；未来若要求按观察者求值，再先登记渲染相机输入需求。

## 1. 目标

让魔法模组在代码执行期间，用编辑器导出的粒子动画（`.pdrawc`）渲染法术特效，并让粒子投射物平滑跟随一个**不可预测轨迹**（追踪/索敌）的投射物。

两个核心需求：

1. **投射物本体**：粒子跟随投射物移动（本体用程序化粒子）。
2. **特效动画**：施法 / 轨迹 / 命中动画，在代码执行期间播放（用 `.pdrawc`）。

---

## 2. 关键事实（两仓库盘查结论）

### 2.1 播放器（ParticleDrawing 1.0.6 源码）

- 只读 `.pdrawc` 二进制（`PDC1` v11，Ed25519 签名）；`.pdraw` 无读取器。
- 「服务端权威、客户端本地求值渲染」；渲染 = 20Hz tick 求值 + 渲染帧 partialTick 插值（`BridgeParticle` 的 `xo/x`）。
- `.pdrawc`/代码动画播放路径的 `origin` 是**构造期 `val` 固定世界坐标**，播放期间不可变 → 无「跟随」能力。
- 无 pause/seek/变速；进度 = `gameTime - startGameTick`（`AnimationProgress`）。
- 已有：多实例（`ConcurrentHashMap<UUID,...>`）、循环/一次性、`updateVariable`、`stop`、内嵌贴图+UV、动态光照、配置阶段文件同步（`AnimationSync*`）。
- 已有实体跟随能力，但只在 **Draw/ParticleGroup 客户端自驱程序** 系统里（`ParticleGroup.followEntity(uuid, offset)` → `BindPivot(FollowEntity)`，客户端每 tick 解析实体位置），与 `.pdrawc` 时间轴系统彼此独立。
- 关键类：
  - `animation/ServerAnimationManager.kt`（服务端播放/停止/变量；`Playback` 记录 `origin/startGameTick/maxTick/loop`）
  - `animation/ClientAnimationPlayer.kt`（求值器；`origin: Vec3` 在 `init`/`advanceTo`/`reconcileFxStates` 三处使用）
  - `animation/AnimationProgress.kt`（纯函数 `tickAt`/`isFinished`）
  - `core/client/ClientAnimationManager.kt`（客户端播放管理；`play`/`tick`/`sync`）
  - `core/network/NetworkHandler.kt` + `PlayAnimationPayload`/`PlayAnimationDataPayload`/`VariableUpdatePayload`/`StopAnimationPayload`
  - `api/Animation.kt`（代码生成动画 DSL + Java Builder）
  - `api/ParticleManager` / `api/Draw` / `api/ParticleGroup`（服务端权威程序化粒子系统）

### 2.2 魔法模组（PrimalSpells）

- 法术投射物**不是 Minecraft Entity**，而是服务端 Kotlin 对象：
  - `magic/Projectile.kt`（`Projectile : Magic`）
  - `magic/MagicStatus.kt`（`org.joml.Vector3f` `pos` / `velocity`）
  - `magic/MagicManager.kt`（`ServerTickEvent.Pre` 每 tick 驱动 `effects.onTick()`）
- 移动：`magic/effect/Move.kt`（子步进碰撞/反弹）；追踪：`magic/effect/Tracking.kt`（`velocity.add(direction*strength)`），由 revise `Homing` 注入。
- **投射物无朝向/yaw 概念**，只有 `pos` + `velocity`。
- 生命周期钩子（全服务端）：
  - 施法：`item/WandItem.kt` → `wand/Wand.kt` → `Projectile.spell()` → `effects.onActive()`
  - 每 tick：`MagicManager.onTick` → `Projectile.tick()` → `effects.onTick()`
  - 命中：`Projectile.hitEntity/hitBlock` → `effects.onHit*`
  - 死亡：`MagicManager` 置 `alive=false` → `Projectile.die()` → `effects.onDie()`
- 已有 ParticleDrawing 集成：`magic/effect/Trajectory.kt`，但用的是 **1.0.0 已删除 API**（`ParticleStyle.DUST` + `.style()`）。
- 依赖声明：`work.nekow:particledrawing:1.0.2`（`compileOnly` + `localRuntime`），`neoforge.mods.toml` `required [1.0.0,) AFTER BOTH`。
- 网络：标准 NeoForge payload；`CustomPacketPayload + StreamCodec + registrar.playToClient(...) + PacketDistributor.sendToPlayer(...)`（参考 `network/SyncWandStatsPayload.kt` / `SyncShieldPayload.kt`）。
- 客户端入口：`client/PrimalSpellsClient.kt`（`@Mod(dist=[Dist.CLIENT])`）。

---

## 3. 已确认决策（grilling 共识）

| 维度 | 结论 |
|---|---|
| 依赖 | NeoForge mod 依赖（`particledrawing` 作前置） |
| 多人 | 服务端 → 客户端同步 |
| 投射物本体 | 程序化粒子（`ParticleManager`/`Draw`），修 `Trajectory.kt` |
| 特效 | 编辑器导出 `.pdrawc` |
| 投射物跟随 | 可移动锚点：服务端每 tick 发变换包 + 客户端 partialTick 插值 |
| 特效朝向 | 速度方向（由 `velocity` 推 yaw/pitch） |
| 播放能力 | 一次性 / 挂接 / 循环 / seek / 暂停 / 变速 / 多实例 |
| 控制权 | 默认客户端本地，可标记服务端权威 |
| 资源 | 播放器资源注册表，`.pdrawc` 进魔法模组 jar 按 key 缓存（只同步一次） |
| 引用 | 资源路径字符串（`primalspells:effects/xxx.pdrawc`） |
| 入口 | 服务端触发 + 纯客户端本地入口 |
| 缩放 | 整体 `scale` 参数 |
| 并发 | 100+ 实例（需性能预算/上限） |
| 版本 | 对齐 1.0.6，迁移 `Trajectory.kt` |
| 本轮 | 先出设计文档 |

---

## 4. 总体方案

```
魔法模组（PrimalSpells，服务端代码）
  ├─ 投射物本体：ParticleManager / Draw（程序化，修复 Trajectory）
  ├─ 施法特效：Effects.play(key, EntityAnchor(玩家), opts)         ← 一次性，朝玩家视线
  ├─ 轨迹特效：Effects.play(key, MovableAnchor(投射物), opts)       ← 挂接，每 tick updateAnchor
  └─ 命中特效：Effects.play(key, FixedAnchor(命中点), opts)          ← 一次性
        │
        ▼
播放器（ParticleDrawing）
  ├─ EffectRegistry：注册 jar 内 .pdrawc（key → bytes + 验签）
  ├─ ServerEffectManager：播放/锚点更新/时钟控制 + 网络下发
  ├─ 资源下发：客户端缓存缺失时按需请求 EffectDataPayload（key → bytes，验签后驻留）
  └─ 客户端：
       ├─ ClientAnimationManager：本地缓存 + 播放实例管理
       ├─ 锚点解析：Fixed / Entity(客户端每 tick 按实体解析) / Movable(每 tick 样本)
       ├─ 播放时钟：legacy(gameTime) / 客户端本地 / 服务端权威
       └─ 锚点应用：local-space 状态 → 每 tick 锚点变换 → 世界坐标 → 原版 partialTick 位置插值
```

核心架构改动一句话：**把 `.pdrawc` 播放的固定 `origin` 替换为「锚点」，把「时间轴求值」与「锚点变换」解耦**——时间轴在 20Hz tick 求值输出**本地坐标**，锚点变换每 game tick 映射到世界坐标后，由原版粒子渲染按 partialTick 在相邻 tick 端点间插值，得到帧级平滑（位置）；朝向为每 tick 采样（速度方向/实体朝向）。

---

## 5. 播放器模组改动（ParticleDrawing）

### 5.1 资源注册表 `EffectRegistry`

新增 `api/EffectRegistry.kt`（已实现）：

```kotlin
object EffectRegistry {
    @JvmStatic fun register(key: Identifier, data: ByteArray): Boolean  // .pdrawc 字节，注册时验签
    @JvmStatic fun contains(key: Identifier): Boolean
    @JvmStatic fun dataFor(key: Identifier): ByteArray?
    @JvmStatic fun keys(): Set<Identifier>
}
```

- 魔法模组在自己的 mod 初始化里读 `assets/primalspells/effects/*.pdrawc` 并 `register`。
- 资源下发采用**按需请求**：`ServerEffectManager.play` 只发 `PlayEffectPayload(key, anchor, options, ...)`；
  客户端缓存缺失时回 `EffectRequestPayload(key)`，服务端回 `EffectDataPayload(key, bytes)`，验签后驻留客户端缓存。
- 之后重复播放只传 **key**，不再重传全文（满足「只同步一次」）。

### 5.2 锚点模型 `Anchor`

新增 `api/Anchor.kt`（sealed，Java 可构造；已实现）：

```kotlin
sealed class Anchor {
    class Fixed(val pos: Vec3) : Anchor()                         // 固定世界坐标
    class Entity(val entityId: Int, val offset: Vec3 = Vec3.ZERO) : Anchor()  // 实体引用
    class Movable(                                                // 可移动锚点（非实体投射物）
        val pos: Vec3,
        val velocity: Vec3,
        val orient: Orient = Orient.VELOCITY,
    ) : Anchor()
}

enum class Orient { VELOCITY, WORLD }
```

- `Entity` 用于玩家/法杖等真正的实体（施法特效朝向玩家视线）。
- `Movable` 用于非实体投射物（轨迹特效朝向速度方向）；播放实例的 `playbackId` 即锚点身份，不再单设 `anchorId`。
- 后续「多挂接点」在此扩展（`Anchor` 增 `slotId: String` 字段即可，不破坏 API）。

### 5.3 可移动锚点 + 变换包 + partialTick 插值

**服务端**：
- `ServerEffectManager`（新，或并入 `ServerAnimationManager`）维护 `playbackId → anchor`。
- 魔法模组每 tick 调 `updateAnchor(playbackId, pos, velocity)`。
- 服务端每 tick 末（挂到现有 `core/server/ServerParticleHandler.onServerTick(ServerTickEvent.Post)`）**批量 flush**：把本 tick 变更的锚点打包成 **一个** `AnchorUpdateBatchPayload` 发给覆盖玩家（避免 100×20 个包）。

**客户端**：
- `EffectAnchorResolver`（`core/client`）维护当前锚点 `(pos, quaternion, scale)`：
  - `Fixed`：恒定；`Entity`：每 tick 解析实体位置 + yaw/pitch；`Movable`：锚点更新包写入 pos+velocity，朝向由速度方向推 yaw/pitch（`Orient.VELOCITY`）或恒单位（`WORLD`）。
- 每个 game tick：`ClientAnimationManager.sync()` 用 `resolver.apply(localPos)` 把本地坐标映射为世界坐标，写入 `BridgeParticle`（`snap=false`）。
- 渲染帧：原版 quad 按 `partialTick` 在上一 tick 与当前 tick 的世界坐标端点间插值 → 帧级平滑（位置）；朝向为每 tick 采样。

**关键解耦**：
- `ClientAnimationPlayer` 对「锚定播放」以 `origin=ZERO` 构造，输出**本地坐标**，不再 `origin.add(...)` 换算世界。
- 固定锚点（`Fixed`）与旧路径同样通过 `origin` 世界坐标处理，零回归风险。
- 后续若追踪转向出现可感知的 20Hz 朝向跳变，可在渲染帧钩子里补 slerp（本地坐标 + 双样本锚点已在模型内，改动局部）。

### 5.4 播放时钟（seek / 暂停 / 变速）

现状进度被 `gameTime - startGameTick` 钉死。新增每播放实例的 `PlaybackClock`（`animation/PlaybackClock.kt`，已实现）：

```kotlin
class PlaybackClock(
    var position: Double = 0.0,   // 时间轴位置（tick，可小数）
    var playing: Boolean = true,   // false = 暂停
    var speed: Double = 1.0,       // 倍速（每 tick 推进的 tick 数）
) { fun advance() { if (playing) position += speed } }
```

- 旧路径（`clock == null`）仍走 `AnimationProgress`（`gameTime - startGameTick`），**完全向后兼容**。
- 新特效播放（`clock != null`）：服务端与客户端各持一份时钟，以相同起点/速度/播放态确定性推进，无需逐 tick 同步。
- `CLIENT_LOCAL`（默认）：控制命令只在本地生效（客户端 `ClientEffects`）；服务端 `EffectHandle` 的控制方法为 no-op。
- `SERVER`（标记权威）：控制命令经 `ClockSyncPayload` 广播，所有玩家进度一致。

`ClientAnimationPlayer` 新增 `tickExternal(targetTick)`（复用既有 `advanceTo(t)`）：支持任意 seek（含向后回退）、循环回卷与非循环结束。

### 5.5 客户端本地入口

新增 `api/ClientEffects.kt`（`@JvmStatic`，`Dist.CLIENT`）：

```kotlin
object ClientEffects {
    @JvmStatic fun play(key: ResourceLocation, anchor: Anchor, options: EffectOptions): ClientEffectHandle?
}
```

- 读本地缓存 key → 验签解析 → 走 `ClientAnimationManager`，`authority` 强制 `CLIENT_LOCAL`。
- 供手柄/UI 等纯本地装饰用，不经服务端、不占同步。

### 5.6 新网络包（`core/network/`）

| 包 | 方向 | 内容 |
|---|---|---|
| `PlayEffectPayload` | s→c | `playbackId`、`key: Identifier`、`anchor`、`options`（scale/loop/speed/startTick/authority）、`startGameTick` |
| `AnchorUpdateBatchPayload` | s→c | 本 tick 变更的 `List<AnchorUpdate(playbackId, x,y,z, vx,vy,vz)>` |
| `ClockSyncPayload` | s→c | `playbackId`、`position`、`playing`、`speed`（仅 `SERVER` 权威控制时发） |
| `EffectDataPayload` | s→c | 单个特效的 `.pdrawc` 字节（客户端缓存缺失时按需下发） |
| `EffectRequestPayload` | c→s | 客户端请求缺失 key 的数据 |

- 全部沿用现有 `NetworkHandler` 注册模式（`playToClient` / `playToServer`）。
- 旧 `PlayAnimationPayload`（带全文 bytes）保留给 `<gameDir>/animations/` 路径与向后兼容。

### 5.7 性能预算（100+ 并发）

- **锚点更新批量化**：每 tick 一个 `AnchorUpdateBatchPayload`，而非每实例一包。
- **距离分层**：超过阈值距离的播放实例，锚点更新频率降为每 N tick，或不做渲染帧插值（直接 tick 对齐）。
- **全局实例上限**（可配置，建议 256）：超出时拒绝新播放或回收最旧/最远的非循环实例。
- **粒子预算**：复用现有 5w 粒子能力；每实例粒子数预算 + 全局预算，超限时按距离/年龄降级（降低更新频率或跳过静态动画求值，现有 `isStaticAnimation` 快路径继续复用）。
- **固定锚点实例零每帧开销**：只有 `Movable`/`Entity` 锚定实例走渲染帧插值。

### 5.8 API 提案（Java 友好，Kotlin 实现 + `@JvmStatic`/`@JvmOverloads`）

```kotlin
// 服务端门面（魔法模组主要用它；已实现，api 包）
object Effects {
    @JvmStatic fun register(key: Identifier, data: ByteArray): Boolean

    @JvmStatic @JvmOverloads
    fun play(key: Identifier, level: ServerLevel, anchor: Anchor,
             options: EffectOptions = EffectOptions()): EffectHandle
    @JvmStatic @JvmOverloads
    fun play(key: Identifier, players: Collection<ServerPlayer>, anchor: Anchor,
             options: EffectOptions = EffectOptions()): EffectHandle
}

class EffectHandle {
    fun updateAnchor(pos: Vec3, velocity: Vec3)      // Movable 锚点每 tick 调用
    fun stop()
    fun seek(tick: Double)                          // SERVER 权威时广播；CLIENT_LOCAL 为 no-op
    fun pause(); fun resume(); fun setSpeed(s: Double)
    fun setVariable(name: String, value: String)    // 复用现有变量热更
    fun isActive(): Boolean
}

class EffectOptions {                               // Java 流式：new EffectOptions().scale(2f).speed(1.5)
    fun scale(Float): EffectOptions                 // 整体缩放，默认 1
    fun loop(Boolean): EffectOptions                // null = 用动画自身 loop
    fun speed(Double): EffectOptions                // 倍速，默认 1.0
    fun startTick(Double): EffectOptions            // 起始 tick，默认 0
    fun authority(Authority): EffectOptions         // CLIENT_LOCAL（默认）| SERVER
}
// 朝向 orient 是 Anchor.Movable 的字段（VELOCITY/WORLD），不在 EffectOptions
```

Java 用法示例见 §6.5。

---

## 6. 魔法模组改动（PrimalSpells）

### 6.1 版本对齐 + `Trajectory.kt` 迁移

- `build.gradle`：`particledrawing` 依赖从 `1.0.2` 升到 **1.0.6**（与播放器源码一致）。
- 迁移 `magic/effect/Trajectory.kt`：去掉 `ParticleStyle.DUST` 与 `.style()`，改用 1.0.6 `ParticleManager.create()` Builder（`position/color/scale/lifetime/glowing/lightLevel`）+ `handle.move(Vec3(status.pos), 1, EasingType.LINEAR)`（现有 `wand/Wand.kt` 已维护 `status.particleManager = ParticleManager.of(caster.level())`）。
- 保留 `Trajectory` 的 `onActive/onTick/onDie` 骨架，只替换创建调用。

### 6.2 投射物本体（程序化）

沿用/重命名 `Trajectory`（**已定稿：不新建该类，本体标记并入 `Trajectory` 的头部**）：`onActive` 创建 `ParticleHandle`，`onTick` `handle.move(status.pos, 1, LINEAR)`，`onDie` `remove()`。本体颜色/大小/发光由各投射物子类参数化（如 `Fireball` 传橙色、`Laser` 传亮白）。

> 本体走服务端权威程序化粒子系统，其客户端渲染层已有 `xo/x` partialTick 插值，跟随平滑无需新锚点机制。

### 6.3 特效接线点（`.pdrawc`）

新增 `magic/effect/` 下若干效果，或直接在生命周期钩子调 `Effects`：

| 特效 | 钩子 | 锚点 |
|---|---|---|
| 施法 | `Projectile.spell()` / 新 effect `onActive()` | `Anchor.Entity(玩家)`（朝向玩家视线） |
| 轨迹 | 新 effect `onActive()` 创建 + `onTick()` 更新 | `Anchor.Movable`（朝向速度方向） |
| 命中 | `Projectile.hitEntity/hitBlock` / `effect.onHit*` | `Anchor.Fixed(命中点)` |
| 死亡/消散 | `effect.onDie()` | `Anchor.Fixed(status.pos)` |

- 轨迹特效的生命周期：`onActive` 时 `Effects.play(key, level, Anchor.Movable(status.pos, status.velocity), opts)` 拿到 `EffectHandle` 存进 effect 字段；`onTick` 里 `handle.updateAnchor(status.pos, status.velocity)`；`onDie`/`onHit` 里 `handle.stop()`。
- 命中特效：`onHit*` 里一次性 `Effects.play(key, level, Anchor.Fixed(hitPos), opts)`（自然播完，或返回 handle 以备提前停）。

### 6.4 资源组织

- 特效文件放 `src/main/resources/assets/primalspells/effects/<name>.pdrawc`。
- mod 初始化：遍历该目录，`Effects.register(Identifier.fromNamespaceAndPath("primalspells", "effects/<name>"), bytes)`。
- 代码里用字符串 key：`"primalspells:effects/arc_shot"`。

### 6.5 Java 用法示例（示意）

```java
// 施法特效（一次性，朝向玩家视线）
Effects.play(
    Identifier.fromNamespaceAndPath("primalspells", "effects/cast_arc"),
    level,
    new Anchor.Entity(player.getId()),
    new EffectOptions().scale(1.2f).authority(Authority.CLIENT_LOCAL)
);

// 轨迹特效（挂接投射物，速度朝向）
EffectHandle trail = Effects.play(
    Identifier.fromNamespaceAndPath("primalspells", "effects/fireball_trail"),
    level,
    new Anchor.Movable(toVec3(status.pos), toVec3(status.velocity), Orient.VELOCITY),
    new EffectOptions().loop(true).scale(0.8f)
);
// 每 tick（Move.kt 更新 velocity 后）：
trail.updateAnchor(toVec3(status.pos), toVec3(status.velocity));
// 命中/死亡：
trail.stop();

// 命中特效（一次性，命中点）
Effects.play(
    Identifier.fromNamespaceAndPath("primalspells", "effects/fireball_hit"),
    level,
    new Anchor.Fixed(hitPos),
    new EffectOptions().startTick(0).authority(Authority.SERVER)
);
```

---

## 7. 时钟与数据流

**Legacy（无控制）**：`position = clamp/wrap(gameTime - startGameTick)`（`AnimationProgress`），服务端 `syncPlaybacksToPlayer` 重发对齐——**保持现状**。

**CLIENT_LOCAL（默认）**：
- 服务端下发 `PlayEffectPayload(key, anchor, options, startGameTick)`。
- 客户端本地推进 `position += speed`（tick 级）；seek/pause/speed 只改本地时钟。
- 重连/切维度重发时，服务端带「当前参考位置」保证大致对齐（视觉性效果可接受轻微偏差）。

**SERVER（标记权威）**：
- 服务端推进时钟，变化时发 `ClockSyncPayload`；客户端以服务端时钟为准。
- 迟到加入/重发带当前 `positionTicks`，与在场玩家严格一致。

**锚点数据流（Movable）**：
1. 魔法模组每 tick `handle.updateAnchor(pos, velocity)`。
2. 服务端收集，tick 末批量发 `AnchorUpdateBatchPayload`。
3. 客户端 `EffectAnchorResolver` 更新 `(pos, velocity→quaternion, scale)`。
4. 每个 game tick `sync()` 用 `resolver.apply(localPos)` 写 `BridgeParticle`；渲染帧按 `partialTick` 在相邻 tick 世界坐标端点间插值。

---

## 8. 性能与上限（提案值，可配置）

| 项 | 提案 |
|---|---|
| 全局活跃播放实例上限 | 256 |
| 锚点更新 | 每 tick 批量 1 包 |
| 锚点更新距离降频 | 距离 > 64 blocks：每 4 tick 更新；> 128：每 8 tick |
| 渲染帧插值 | 仅 `Movable`/`Entity` 锚定实例 |
| 粒子预算 | 沿用 5w 全局；超限降频/回收最远最旧非循环实例 |
| 静态特效 | 复用 `isStaticAnimation` 快路径，不每 tick 重算 |

---

## 9. 测试与验证

播放器（ParticleDrawing，已实现并通过）：
- `PlaybackClockTest`：推进/暂停/恢复。
- `EffectAnchorResolverTest`：Fixed/Movable 的本地→世界映射、速度→yaw/pitch、WORLD 朝向。
- `ClientAnimationPlayerExternalClockTest`：`tickExternal` 的显式起始 tick、前进/向后 seek、循环回卷、非循环结束。
- `gradlew build`（compileKotlin + test）通过。

魔法模组（PrimalSpells，后续）：
- `gradlew build`（含 Kotlin 编译）通过；`Trajectory.kt` 对 1.0.6 API 编译通过。
- 开发世界冒烟：施法/轨迹/命中特效可见、投射物本体跟随平滑、多人下另一玩家可见。

---

## 10. 分阶段落地

- ✅ **Phase 1**：播放器 `EffectRegistry` + 按需资源下发 + 客户端缓存。
- ✅ **Phase 2**：播放器锚点模型 + 可移动锚点传输 + partialTick 位置插值（核心）。
- ✅ **Phase 3**：播放器播放时钟（seek/pause/speed）+ 客户端本地入口。
- ⏳ **Phase 0**：PrimalSpells 依赖升 1.0.6 + `Trajectory.kt` 迁移（魔法模组侧，后续）。
- ⏳ **Phase 4**：PrimalSpells 接线（施法/轨迹/命中/死亡特效 + 资源注册）。
- ⏳ **Phase 5**：性能预算、上限、多人冒烟。

---

## 11. 风险与待确认项

1. **新 API 落在播放器仓库**（`particledrawing`），需提交到该仓库 git；PrimalSpells 侧改动提交到它自己的仓库（两边分别提交，与 AGENT.md 规则一致）。
2. **速度方向朝向的边界**：`velocity ≈ 0` 时朝向保持上一次（避免翻转）；是否需要「世界朝向」变体已由 `Orient.WORLD` 覆盖。
3. **时钟偏差**：`CLIENT_LOCAL` 模式下重连/迟到加入的位置只保证「大致对齐」，若要求严格一致需对关键特效标记 `SERVER`。
4. **锚点更新频率**：默认每 tick 20Hz + 渲染帧插值；若实测带宽吃紧，按 §8 距离降频。
5. 类名与包名已确定：`Effects`/`EffectHandle`/`Anchor`/`EffectOptions`/`EffectRegistry`/`ClientEffects`，均放 `api` 包（与现有 `Animation`/`Draw`/`ParticleManager` 一致）。
6. 是否需要「世界空间尾迹」（投射物飞过留痕）不在本期范围（已确认仅吸附；`Anchor.Fixed` + 循环可作为后续扩展路径）。

---

*播放器侧 API 轮子（§5）已在 `particledrawing` 仓库实现并通过 `gradlew build`；魔法模组端接线见 §6，后续再做。*

### 第 51 轮移动区域视觉

PD `ParticleGroup.setPivot` 更新已生成固定结构的中心；`expression` + `setVariableLive` 让两层黑洞
外环由客户端逐帧求值，本侧只持有一个整体滞后向量，不持有粒子位置/速度表。
表达式负责外环形变和退场收缩，避免与糖指令 scale 竞争；fadeOut 仍由 PD 负责。现有 API 足够，无新增请求。


### 第 52 轮内容扩展

水晶/电弧/斥力场/冰壳/远距符文分别用 Draw.curve、Draw.sphere、Draw.circle 创建整组粒子；
实体冰壳组在脚部建轴心，成员以身体中心给世界坐标，followEntity 保留该局部偏移。
光束用表达式读六个实时端点变量；位置与颜色由表达式决定，FADE 因子按 PD 文档叠加其上。
10tick线性淡出提供清晰余晖；服务端只更新整体端点，不自建逐粒子状态或补间引擎。
单组预算和验收见 [spell-expansion.md](spell-expansion.md)。本轮所需现有 API 足够，无新增请求。


### 第54轮质感组合

PixelVfx只生成实色几何：晶面方粒通过billboard(false)/三轴spin贴在六面棱柱表面（alignTo对齐的是长轴，不能用它对齐法线）；
裂纹/冲击用固定色阶折线与有限寿命，碎片速度/重力交给PD公开ParticleBatch/Handle。
效果要求及接入见 [spell-quality.md](spell-quality.md)，不使用alpha渐隐或加法混合。

第61轮停止旧的组ScaleBy/Fixed轴心接入；第62轮用1.0.15-ALPHA恢复当前倍率和移动样本接线。严格高刷组尺寸/变量求值仍有新缺口，见[animation-continuity.md](animation-continuity.md)与pd-api-requests。


## 第62轮：1.0.15-ALPHA接线

- `scaleTo(0,0).scaleTo(target,2–4ticks)`用于区域场、晶体、冰壳和爆炸火团快速入场；退场统一`scaleTo(0,ticks)`捕获当前倍率，保留销毁余量。
- 移动结构首次`anchor(Anchor.Movable(center,Vec3.ZERO))`，之后`updateAnchor(previous,current)`；黑洞、重力边界/井环、水晶、斥力场不再热改Fixed轴心。井环保留上一帧地面高度。
- 等离子束空间端点使用`setVariableInterpolated(name,value,1)`；尾迹发射器用当前位置，与头部track同相位，不再自行延后一tick。
- 尾迹声明`.jitter(0.035)`；寿命改变时用`.life(...).lifeCurve(...)`覆盖整套曲线，已有片元走完自己的曲线，不重建发射器。
- 运行时位置/寿命/缩放求值与拆包全部由PD实现，本侧仅提供样本和动画参数。


源码限制：组编排tick仍20Hz，尺寸直接syncScale；变量快照先于VarEase更新。保留原生通路和销毁余量，不把20fps捕获PASS当成144Hz连续保证；第62轮新请求见pd-api-requests。

## 第63轮接线修正

实时玩家法阵使用实体getter；等离子束端点使用setVariableLive样本，随后由原生Bridge位置插值。不得对持续更新的坐标每刻重启1刻setVariableInterpolated：当前refreshInputs/applyVarEases顺序会阻止新样本收敛。presence退场只启动一次，可以保留有限变量缓动。此举只修当前跟随错误，不宣称未决逐渲染帧变量/尺寸能力已闭环。

爆炸采用PD ParticleBatch的速度/力与逐片sizeCurve；创建按spawnAll公开256成员上限组织，运动更新仍交给PD原生自动拆包。shatter用moveAlongOffset抵消scaleTo的几何内缩并向外移动，片元缩小不改变“爆炸/碎裂的轮廓继续外扩”的语义。已经自由飞行的批次用逐片resize收尾，避免重新挂组缩回场心。

第64轮：CrystalReaction的晶面顶点绕序改朝外，ParticleVisual.spin仍按公开API弧度/局部XYZ使用。PD单面正面朝局部+Z，不能用反向面或加一层反面粒子遮掩绕序错误。白闪使用现有ParticleGroup.recolor的一刻过渡，锁印用ParticleBatch.trackAll/逐片resize；未另写渲染、跟随、颜色插值引擎。

第65轮：分段魔弹常驻本体用ParticleBatch.trackAll，跃迁连接用已有PixelVfx.arc，到期retireBatch；爆炸用spawnAll、setVelocityAll、applyForceAll与sizeCurve组合体积火舌，0～2刻分批释放仍由现有Deferred调度。未新增自研渲染/曲线/集合引擎，未遇到新的PD API缺口；历史高刷组变量/尺寸缺口仍未闭环。

爆炸长火星/压力碎片使用原生ParticleVisual.alignTo对齐飞行方向；广告牌下spin本来就不生效，不把无效的平面旋转当作已正确对齐，也不补写客户端朝向渲染器。

第66轮检查发现锁定1.0.15-ALPHA BridgeParticle的光照缓存只按方块坐标失效，会留住动态光已合并结果。详见issues/pd-api-requests第66轮；已停止该接入点，不抖位置/重生粒子/自写光照绕过。尚未更新前置，也不宣称残留亮度修复。
