# ParticleDrawing API 修改需求

本页只保留未决需求。规则见 [effects-api第0节](../design/effects-api.md#0-职责与缺陷处理)：PD负责粒子通用能力，本模组不另写替代实现。当前运行依赖1.0.20-ALPHA，以下源码定位均指lib中同版本sources JAR；磁盘PD项目1.0.21的DynamicLightManager仍使用固定lightLevel，尚不具备下述光照过渡。

## 发光粒子的光照生命周期与连续强度

**状态：未决，阻塞所有发光效果的照明过渡。** 本模组停止该接入，不自建光照缓存、客户端平滑器或逐刻整数亮度模拟，也不通过关闭光照掩盖问题。

### 目标

所有照明来源在出生、持续、重定向、正常退场和删除时具有连续光强，与视觉动画处在同一时钟。包括单颗寿命曲线、Emitter子粒子、组scale/fade/表达式presence、实体锚点与主动回收。保持MC不透明硬边片元，不能强迫所有造型变成半透明才能控制光。

### 源码与实机证据

- `api/ParticleVisual.kt`：默认lightLevel=15；`ParticleHandle.lightLevel(Int)`只有0～15整数更新，ParticleCurve无光强通道。
- `core/client/ClientParticleEngine.getGlowingParticles()`：按存活和effectiveAlpha>0.01筛选，尺寸为零不排除。
- `lighting/DynamicLightManager.collectSources()`：`luminance = p.lightLevel().toDouble()`，不读取实际alpha、尺寸、组presence或生命周期。fade/scale只改画面，不改变此值；remove后光源直接不再收集。
- 同文件 `signatureOf()`用luminance.toLong，`BakedState`只保存位置，dirty sections只检测新增/移除/位移。加入连续光强后还须避免小数亮度被签名截断、静止世界网格未刷新、非发光粒子照明缓存不失效。
- 复现：`pwsh tools/capture/capture.ps1 -Offline -Scene particle_lighting`。110/110帧、0丢帧，原生alpha为0.125、0.25、0.091时光源中心仍为15；组8t缩到零后，照明直到回收才从15一次跳到0，最大单刻差15。
- 场景4项断言中原生alpha过渡和最终零粒子通过，光强跟随与无整级硬切两项失败。原始值在 `build/capture/out/particle_lighting/meta.tsv`，完整报告在 `build/capture/out/report.txt`；正常情况下会被下次录制覆盖，复现命令与场景源码可重建。5条既有OSHI日志是独立环境问题。

### 期望API与语义

由PD提供等价于以下能力的公开入口，签名为草案，不是现有API：

- 光强浮点乘数通道，例如 `CurveChannel.LIGHT` / `ParticleCurve.light(...)`，可在Builder、SpawnSpec、Emitter和Handle声明/覆盖；不改变片元alpha和全亮标志。
- 组和表达式可声明光照presence，或公开统一生命周期包络，使出生、缩片/淡出和retire共享连续强度；目标值热更从当前实际值接续，不重置成满光。
- 渲染端按分数游戏年龄求值，并处理光源退出/显式remove/寿命到期的有限收尾；空间位置、光强和可见性同帧一致，暂停冻结、换维清理。
- 小数光强更新令静止区块及非发光粒子缓存正确失效；预算/可见性裁剪导致的光源替换也不能出现满亮开关。

PD决定最终API形态；本模组只选择每种法术的光强与入/退场时长，再统一接线。不能由使用方维护光源副本来实现以上语义。

### 验收与恢复条件

先复验原生alpha/组退场场景，再覆盖不透明片元的独立光强曲线、表达式presence、短命Emitter子粒子、移动/静止/删除光源以及火把卸载。20/60/144FPS检查同位置连续光强与受光物体，尺寸归零不残留满光；重定向不回闪、结束光强和粒子计数均归零。每种法术保留自身时序，不统一拖长动画。

影响入口：PdParticles、PixelVfx、Trajectory、WandCastingFx、Torch、ExplosionFx、BlackHoleFx、各晶体/电弧/光束/护盾表现。PD主JAR与sources JAR同步升级且上述验证通过后恢复接入。

## 短寿命粒子的曲线时钟与末端退场

**状态：未决。** 当前本模组保留拖尾4t下限，停止1～2t短尾接入，不自建渲染时钟。

- 目标：1～2t单颗尾迹在高刷下仍显示尺寸入场、alpha连续降到0和正确末端，而不是被整数刻采样跳过。
- 现有API：ParticleLifeCurve、EmitterHandle.life/lifeCurve可声明曲线；Bridge仅插值已经收到的端点。
- 源码：RenderParticle.ageTicks=engineMs/50，advanceEngine每刻+50；ClientParticleEngine.frameUpdate推进后即移除isDead。frameSyncCurves每帧仍用该整数年龄，短粒子可能在有效中段/零末端前删除。
- 证据：1～2t尾迹在客户端刻与RenderFrame.Post两种探针下，同粒子alpha下降次数均为0、最小alpha约0.507；较长寿命变速拖尾正常。观察入口为ProjectileFlightScene与CaptureScenes.fireballTrail。
- 需要：PD使用与出生时刻一致的分数游戏年龄采样曲线，分离游戏运动与外观采样；销毁前完成可见中段和零末端，暂停/恢复不跳变。
- 验收：1/2/4t分别在20/60/144FPS采实际Quad的RGBA和尺寸；stop只停止发射，既有粒子收尾，最终双端归零；重复lifeCurve覆盖不叠乘、不丢通道。

## 登记规范

每条保留目标、状态、当前API/源码或报错、最小复现、API草案、影响范围、临时限制和验收。闭环后从本页移除，将当前可用能力写入effects-api，必要修复结论合并到resolved。
