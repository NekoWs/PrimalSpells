# ParticleDrawing API 需求（待对方实现）

> 本文件只放**还没实现**的需求。已闭环的条目见 [resolved.md](resolved.md) 末尾「ParticleDrawing 已闭环条目」。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：`gradle.properties` 的 `particledrawing_version=1.0.13-ALPHA`
> （`lib/particledrawing-1.0.13-ALPHA.jar`，另附 `-sources.jar` 便于核对签名）。

---

**当前未决：0 条。** 第 48 轮那组需求已由 **ParticleDrawing 1.0.14-ALPHA** 全部落地
（`d998e36`：*运行时发射器、逐粒子寿命曲线与批量生成，组级逐成员漂移*）—— 逐条对照见
[resolved.md](resolved.md) 附录 B。本侧已在第 48 轮完成迁移（`Trajectory.kt` 改发射器 + 寿命曲线 +
`spawnAll`/批量速度力）。

### ~~[需求组] 把「预制动画」那层能力下沉为**运行时发射器**（Trail/Emitter 一等公民）~~ ✅ 已由 1.0.14-ALPHA 实现

- **状态**：✅ **已落地**（ParticleDrawing `1.0.14-ALPHA`，commit `d998e36`）。逐条对照：
  - ① 运行时发射器 → `api/ParticleEmitter`（`spacing`/`interval`/`intervalMs`/`life`/`scale`/`visual(...)`/
    `maxAlive`）+ `EmitterHandle`（`updateAnchor`/`stop`）；`ClientEmitterManager.frameTick(partialTick)`
    **逐渲染帧**发射，`Anchor.Movable` 按「上一 tick 位置 + 速度 × partialTick」取本帧位置。
  - ② 插值语义统一 → `ParticleSpawnSpec.prevPosition(prev)`（首帧插值端点，与 `track` 同语义）；
    发射器锚点本身即该语义 ⇒ 本侧不再需要"延迟一 tick"。
  - ③ 寿命曲线写进 spawn → `ParticleCurve`/`CurveKey`/`ParticleLifeCurve` + `fadeOut`/`shrinkTo` 糖。
  - ④ 批量生成 → `ParticleManager.spawnAll(specs)` / `ParticleBatch.spawnAll`。
  - 额外 → 组级**逐成员漂移**（更早那条需求）一并实现。
- 以下为**登记时的原文**（保留以便读懂当时的降级设计，勿据此重新提需求）：
- **背景（为什么按"组"提）**：本模组前几轮的做法是"碰到边界就绕"——采样段延迟一 tick、按里程逐颗
  `create().spawn()`、把"头部白热/末端压暗"烘焙进生成时的颜色与 alpha。结果是：**特效每调一次参
  就要改一次代码**（用户这几轮把长度 8 格→3.5 格、间距 0.5→0.15 格，每次都是一次重写 + 重录 + 判读），
  而且绕法本身会制造新缺陷（相位差、缺颗空档）。
- **根因**：PD **已有**声明式、逐帧求值的一整套 ——
  `Anchor`（`Fixed`/`Entity`/`Movable` + 朝向，且"渲染层每 game tick 用当前锚点变换映射，再按
  partialTick 插值"）、指令集（`FadeIn`/`FadeOut`/`Recolor`/`ScaleBy`/`Translate`/`RotateOnce`/
  `MovePath`/`Spin`/`Pulse`/`Expression`，按 ms 计时）、组级 `fadeOut`/`scale`/`movePath`/`recolor`。
  但这套只对"播放一段预制动画"开放；运行时通路（`ParticleManager.create()` + `ParticleHandle`）
  只有"生成时定死外观 + 逐颗指令"。

- **1｜运行时发射器（Emitter）**
  - **期望形态**：`manager.emitter(anchor).spacing(格).life(ticks).sizeCurve(...).alphaCurve(...)
    .colorCurve(...).spawn()`；发射在**客户端按渲染帧求值**（用锚点的插值位置推进"按里程/按时间"两种口径），
    服务端只声明一次、变更时更新。
  - **现状为什么不够**：只能服务端每 tick 采样、逐颗下发 ⇒ 出现的时刻被 tick 量化，**高刷下"一跳一跳
    地长出来"**；带宽是 O(粒子)（每颗 1 spawn + 2 update 包）。
  - **收益**：出现/淡出逐帧；带宽 O(1)；"长度/密度/尺寸曲线"从代码变成**参数**。

- **2｜位置指令的插值语义统一（程序化粒子 ↔ `track` 粒子）**
  - **期望形态**：`Anchor.Movable` 那套"每 tick 下发 pos+velocity、渲染层按 partialTick 插值"的语义，
    请同样给程序化粒子与 `track` 粒子：或加 `interpolate = false` 开关，或暴露客户端 `partialTick` /
    已渲染位置供服务端查询。
  - **现状为什么不够**：`track` 走 `TrackBuffer` 插值、程序化粒子按**绝对位置**渲染且不插值；服务端拿不到
    partialTick ⇒ 同类粒子在屏幕上相差最多**一整 tick 位移**（第 48 轮实测 0.45 格，判读抓到过"小菱形冒在
    头部菱形前面"）。本侧只能把采样段整体延迟一 tick 绕开。
  - **临时降级**：采样段延迟一 tick（拖尾严格在身后，恒定滞后约 0.4 格）。

- **3｜逐粒子寿命曲线写进 spawn 包**
  - **期望形态**：`Builder.fadeOut(ticks, easing)` / `.shrinkTo(scale, ticks, easing)` /
    `.curve(通道, 关键帧)` —— 声明式、零额外带宽。
  - **现状为什么不够**：表达"寿命内淡出 + 收缩"要额外 2 个更新包（`recolor` + `resize`）；密集尾迹
    （≈23 颗/投射物）就是 ≈70 个包。

- **4｜批量 spawn / 批量寿命曲线**
  - **期望形态**：`ParticleBatch` 已有批量力/速度/位置；请补批量 spawn 与批量曲线，让"一条尾迹"一次下发。
  - **现状**：只能逐颗。

- **本侧边界（不会推给 PD 的部分）**：游戏语义留在本模组 —— "拖尾长度按格给、寿命＝长度÷速度、按里程
  发射、总强度倍率、静止场走另一套分支"这些仍由我们算，PD 只提供机制（发射器/曲线/插值/批量）。
- **影响面**：`magic/effect/visual/Trajectory.kt`、`ProjectileBody.kt`、`ExplosionFx.kt`、
  `EnergyShield.kt`（这四处都在用"逐颗 spawn + 逐颗更新包"的绕法）。

---

### ~~[需求组] 程序化粒子需要「贴图 / UV / 各向异性 / 朝向 / 加色」整组能力~~ ✅ 已由 1.0.13-ALPHA 实现

第 23/28 轮登记的「贴图 / UV / 各向异性 / 朝向 / 加色」整组能力
已由 **ParticleDrawing 1.0.13-ALPHA** 全部实现 —— 逐条对照与「本侧因此改成了什么」见
[resolved.md](resolved.md) 附录 B。口径与坑见 [design/effects-api.md](../design/effects-api.md) §0.4。

四个历史需求（第 15/17/19 轮登记）已由 **ParticleDrawing 1.0.12-ALPHA** 全部实现，
逐条对照与「本侧因此简化了什么」见 [resolved.md](resolved.md) 附录 B「ParticleDrawing 已闭环条目」。

---

### ~~[需求组] 程序化粒子需要「贴图 / UV / 各向异性 / 朝向 / 加色」整组能力~~ ✅ 已由 1.0.13-ALPHA 实现

第 23 轮登记贴图/UV，第 28 轮按下图参考合并成整组，**ParticleDrawing 1.0.13-ALPHA 全部落地**
（`ParticleStyle.SOFT_DOT/LINE`、`ParticleVisual`、`registerTexture`、`Draw.polyline`、`anisoWorld`、
`alignTo`、`additive`、逐粒子 `delay`）。逐条对照与「本侧因此改成了什么」见
[resolved.md](resolved.md) 附录 B「1.0.13-ALPHA：外观整组」；口径与两个坑见
[design/effects-api.md](../design/effects-api.md) §0.4。

---

### [需求] `armed` 日志降级为 DEBUG / 可开关

- **状态**：未决
- **场景**：护盾每次受击都会**新画一批面板碎片**（用户口径：碎片不跟人、每次都新画），
  每个面板一个组 → 一次受击 arms **32 个组**（截角二十面体 = 12 五边形 + 20 六边形），
  生存模式实战里日志被刷屏。
- **日志原文**（用户实机提供）：
  `[wo.ne.pa.co.cl.ClientAnimationProgramManager/]: [ParticleDrawing] program 0e45113d-beab-4714-abc1-b1f05f9d26d4 armed: 72 particles, 0 instructions, anchorOffset=1`
- **现有 API 为什么不够**：`core/client/ClientAnimationProgramManager` 每次 arm 组都打一行 **INFO**，
  使用方在模组侧关不掉（也不该让使用方去改 log4j 配置来消音前置模组）。
- **期望 API 形态**：arm 日志降到 **DEBUG**；或提供 `ParticleManager.setDebugLogging(enabled)` /
  一个配置项，让使用方按需开关。
- **影响面**：`magic/modifier/EnergyShield.kt`（`spawnPanels`：每次受击重建 32 组）、
  `magic/effect/lifecycle/ExplosionFx.kt`（爆发时一次建 5~8 组）。
- **临时降级**：无（只影响日志可读性，不影响功能；生存模式里会持续刷屏）。

---

### [需求·可选] 组级 `move` 支持「按成员各自的方向」飘散

- **状态**：未决（本侧已用「一片一组」绕过，代价是拿不回那部分组数 / 日志）
- **场景**：护盾被打穿时，那一批面板碎片应当**各自沿自己的法线向外**飞；而爆炸吹开的整罩碎片则是
  **整批沿同一个方向**（爆点方向）。后者一个组就够（第 39 轮已合并），前者只能**一片一个组**（≤7 组）。
- **现有 API 为什么不够**：`ParticleGroup.move(delta, ...)` 是**组级**指令 —— 整组只有一个位移向量，
  想按成员方向各自飘就只能多建组；而 PD 每次 arm 组都打一行 INFO（见上一条），
  组数直接等于日志行数与 arm 开销。
- **期望 API 形态**：`group.moveAlongOffset(scale, durationTicks, easing)` —— 每个成员沿自己的
  `offsetFromPivot` 方向平移 `scale × |offsetFromPivot|`（对「球面碎片向外飞」是现成语义）；
  或 `group.moveEach(deltas: List<Vec3>, durationTicks, easing)`。
- **影响面**：`magic/modifier/EnergyShield.kt`（`spawnGlassPanels` 按位移分组：位移相同 = 共用一个组）。
- **临时降级**：投射物打穿用 `panelNormalDrift` 一片一组（≤7 组）；格挡玻璃、破口轮廓、
  爆炸碎裂（整批沿爆点方向）已经各自合并成 **1 个组**。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 1.0.8 的 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`ProjectileBody`/`Trajectory`），没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| `lib/` 下的版本与 `particledrawing_version` 必须一致 | `lib/` 只留 `1.0.12-ALPHA` | 若手工塞入别的版本，容易对照错 API | 对照 API 一律以 `lib/particledrawing-1.0.12-ALPHA-sources.jar` 为准 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一（此前 `setVelocity` 漏了解除，位置每 tick 仍被锚点覆盖） |
| **程序化粒子固定使用纯白方块贴图** | `TextureCache.defaultWhite()` = 8×8 全白；`ParticleHandle.Builder` 与 `ParticleSpawnPayload` 都没有贴图/UV 字段（贴图只对 `.pdrawc` 开放） | 「细线」「柔边」「玻璃质感」都只能用密集硬方块逼近，近距离必然发糊（第 20 轮实测） | 已并成上文的 **P0 整组需求**；落地前，护盾球壳/玻璃面板用点阵、爆炸用少量方块（不再堆密度） |
| **粒子「实际尺寸」不是 `scale` 本身** | `BridgeParticle`：`quadSize = scale × 0.1 × texScale`；实机/录制实测 `texScale ≈ 2.1` → **实际格数 ≈ scale × 0.213** | 按「scale = 格数」估密度会严重偏小：护盾球壳 3 点/棱 + scale 0.3 画出来是一串**断点**（实机反馈「有两条线条」就是它） | 密度按「点距 ≈ 实际尺寸」反推：点距 0.083 格 → `scale 0.40`；见 `EnergyShield` 的 `SHELL_POINTS_PER_EDGE`/`SHELL_SCALE` |

---

## 2. 需求登记模板（新增需求时照抄）

```
### [需求] <一句话标题>
- 状态：未决 / 已由 ParticleDrawing <版本> 解决
- 目标能力：<我们在 PrimalSpells 想实现的效果 + 触发场景>
- 现有 API 为什么不够：<类 + 方法/属性 + 编译错误原文或运行现象 + 复现步骤>
- 期望 API 形态：<签名草案（类/函数/参数/返回）；服务端还是客户端；线程与生命周期约定>
- 影响面：<本模组受影响文件>
- 临时降级：<降级范围 + 恢复条件；没有则写「无（该功能暂不可用）」>
```
