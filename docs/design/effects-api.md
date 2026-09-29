# 播放器模组特效 API 设计方案（ParticleDrawing × PrimalSpells）

## 0. 硬性政策：ParticleDrawing 的问题一律「停下 + 提需求」，**不得自行另写一套**（§0 优先于本文其余内容）

ParticleDrawing（`particledrawing`）是本模组的**必需前置**，且仍在 **ALPHA** 迭代期（当前锁定 **1.0.8-ALPHA**）。
它是粒子能力的**唯一实现方**；PrimalSpells 只做接线与使用。

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
> 有时机限制的只是「产出末尾的清单」与「登记到 known-issues §7」。

### 0.2 职责边界判定准则（什么属于 PD、什么属于本模组）

先看一张对照：本模组自研的 `ParticleSwarm` 与 PD 的 `ParticleGroup` **职责有重叠，但不是同一种抽象**。

**⚠️ 结论：不能直接迁移。** `ParticleGroup` 面向**编排式动画**（成员基本固定、组级统一变换、指令流一次下发、
客户端本地求值 —— 适合 `.pdrawc` 那类"播放"），而本模组要的是**程序化粒子流**
（成员逐 tick 增删、每粒子独立速度/轨迹/寿命、按位置条件回收）。
逐条差异与由此产生的 PD 侧需求见 [../known-issues.md](../known-issues.md) §7.3（本模组**不再**把它当成"直接迁移项"）。

| 维度 | `ParticleSwarm`（本模组薄封装） | `ParticleGroup`（PD） | 差异 |
|---|---|---|---|
| 成员管理 | 句柄表自动增删，随时 `spawn` 加入 | `add()` 会把 `armed` 置 false → **重发全量受控清单** | 组不适合"每 tick 增删 1~3 个成员"的流 |
| 每粒子状态 | 每粒子独立 pos/vel，`tickSimulated(step)` 逐粒子算 | 组级**统一**变换（`spin/rotate/scale/pulse/move/movePath`） | 组表达不了"每粒子各自被吸向中心" |
| 条件回收 | `removeWhere { pos -> … }`（读 `position()`） | 只有整组 `remove()` / `destroyAfter(ticks)` | 缺"按每粒子位置移除" |
| 锚定身份 | 只用 `entityId`；我们自己存句柄 | `attachTo(entityId)` / `attachToLocal(entityId)` / `followEntity(uuid)` / `defineEntity(handle, uuid)` | **三套身份 + 两套偏移语义**，且 `followEntity` 只跟随位置 |
| 补量/上限 | 自己每 tick 补到 N 个（含上限拒绝重试） | 组不负责补量 | 缺通用「补齐到 N」 |
| 组级动画 | 无（用每 tick 位置写） | **`spin`/`pulse`/`fadeOut` 等零带宽编排** | PD 更强 —— 这是**该用**的部分 |

**判定准则**：

| 归类 | 判定标准 | 例子 |
|---|---|---|
| **属于 PD**（用现成 API，别再自己写） | 与「粒子集合的通用机制」有关：批量生成/更新、组跟随实体/旋转/淡出/延时、寿命与可见性、上限与拒绝处理、带宽优化 | `ParticleGroup` 的组级编排（`followEntity`/`spin`/`fadeOut`/`destroyAfter`）、`trackAll`、`attachTo`、`applyForce` |
| **属于本模组**（玩法语义，PD 不该知道） | 只有游戏知道的东西：半径/数量/颜色/寿命取值、力的公式、回收判据、被动的持有/放下时机与淡出时长 | `BlackHole` 的吸力与阻尼、`GravityField` 的下落与下半球回收、`EnergyShield` 的 4 tick 延迟、`ParticleShapes.fibonacciSphere` 的选点 |
| **灰区（当前留在使用方）** | 逐 tick 自定义受力/轨迹的**程序化粒子流** —— 等 PD 补齐 §7.3 第 1 条的泛用入口后再收敛 | `ParticleSwarm.spawnSimulated/tickSimulated`（一次 `trackAll`）、`removeWhere` |

⚠️ **判定顺序**：先查 PD 是否已有现成能力（`api/` 全量方法见 `ParticleHandle`/`ParticleGroup`/`ParticleManager` 的 KDoc，或 `lib/*-sources.jar`），
再决定要不要在本模组写封装。**「自己维护句柄表 + 逐粒子操作」通常意味着漏看了组 API** ——
本项目就踩过：`ParticleSwarm` 的锚定模式本可用 `ParticleGroup.followEntity`，结果多写了整套簿记，
并因此接连踩了「捕获偏移」「索引配对」「重复身体偏移」「重建滑入」四个坑（见 [../known-issues.md](../known-issues.md) §6 第 5–8 轮）。

### 0.3 现有接线范式（2026-09-30 更新，接线前先看这张表）

| 需求 | 用哪套 API | 每 tick 带宽 | 本项目落点 |
|---|---|---|---|
| 粒子固定不动 | `create()…spawn()`（不设速度、不发位置指令） | 0 | `GravityField` 范围球壳 |
| 匀速直线运动 | 生成时一次 `setVelocity(v)`，之后交给 PD 逐 tick 积分 | 0 | `GravityField` 下落粒子、`Trajectory` 拖尾 |
| 跟随一个非实体点（逐 tick 变位置） | 单体 `handle.track(pos)`；**多粒子用一次 `ParticleManager.trackAll(ids, positions)`** | 1 包/tick（批量，与粒子数无关） | `ProjectileBody`（单体）、`BlackHole`（批量） |
| 恒定 / 分段恒定的力 | `handle.applyForce(accel, ticks)`：两端同规则积分，只在施力时发一次 | 施力次数 | 暂未使用（黑洞吸力随距离变化） |
| 锚在一个实体身上（形状刚性） | `handle.attachTo(entityId, 世界空间偏移)`；偏移要随朝向旋转用 `attachToLocal` | **0** | `EnergyShield` 球壳 |
| 编辑器导出的 `.pdrawc` 动画 | `Effects.play(...)` + `Anchor`（`Fixed`/`Entity`/`Movable`） | — | **尚未接线** |

六条硬要点：

1. **能在客户端本地解析的就别逐 tick 发**：只要粒子位置是「实体 + 固定偏移」，用 `attachTo`
   （**0 包/tick**、天然刚性）；需要逐 tick 变化的位置，再用 `trackAll` 批量下发。
2. **位置指令与速度/力不要混用**：`move`/`track`/`trackAll` 会**停掉**该粒子上的速度与力
   （PD 明确语义），所以同一粒子要么位置驱动、要么速度/力驱动。
3. **多粒子优先用批量 API**：`trackAll` 1 包/tick ↔ 逐粒子 `track` N 包/tick。
   本项目改造后：护盾 0 包、黑洞 1 包、重力场 0 包。
4. **引擎有每维度粒子上限**：超限时 `spawn()` 返回 `null`，使用方必须能接受「丢粒子」而不是当成错误。
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
   并**同步登记到 [../known-issues.md](../known-issues.md#7-particledrawing-api-需求待对方实现)** 的固定章节
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
> - 依赖版本：**1.0.8-ALPHA**（`lib/particledrawing-1.0.8-ALPHA.jar`，附 `-sources.jar`），
>   由 `gradle.properties` 的 `particledrawing_version` 驱动；下方正文中出现的 1.0.6 等版本号是**当时的历史记录**。
> - §10 中标记为 ⏳ 的「PrimalSpells 依赖升 1.0.6 + 迁移 `Trajectory.kt`」**已经完成**：`Trajectory` 使用 `ParticleManager.create()` Builder。
> - 1.0.8-ALPHA 相对 1.0.6 的 API 增量：新增 `api/EffectCallbacks`（播放结束/参数变更回调）与 `api/EffectVarStore`（`setParam` 覆盖值记忆，`getParam` 的诚实实现），**无删除、无签名破坏**；`.pdrawc` 动画特效在 PrimalSpells 侧的接线仍未落地。
> - 本文引用的包路径按重构前书写，现对应关系：`magic/effect/Trajectory.kt` → `magic/effect/visual/Trajectory.kt`；`magic/effect/*` 其余类分入 `magic/effect/{geometry,lifecycle}`；`magic/Revise.kt` → `magic/core/Modifier.kt`（类 `Revise` → `Modifier`）；`magic/Projectile.kt` → `magic/projectile/Projectile.kt`；`magic/{Magic,MagicStatus,MagicManager}.kt` → `magic/core/`；`item/ModItems.kt` → `registry/ModItems.kt`。完整对照见 [../conventions.md](../conventions.md) 第 8 节。


> 状态：**播放器侧 API 轮子已实现**（`particledrawing` 仓库，§5 全部落地并通过 `gradlew build` + 单测）。
> 魔法模组端（`primalspells`）动画/特效接线**后续再做**。
> 涉及仓库：
> - 播放器：`D:\Backup\IDEA Projects\ParticleDrawing`（`particledrawing`，当前锁定 1.0.8-ALPHA）
> - 魔法模组：`D:\Backup\IDEA Projects\PrimalSpells`（`primalspells`，MC 26.2 / NeoForge 26.2.0.59 / Java 25）

---

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

沿用/重命名 `Trajectory`（或新建 `magic/effect/ProjectileBody.kt`）：`onActive` 创建 `ParticleHandle`，`onTick` `handle.move(status.pos, 1, LINEAR)`，`onDie` `remove()`。本体颜色/大小/发光由各投射物子类参数化（如 `Fireball` 传橙色、`Laser` 传亮白）。

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