# ParticleDrawing API 需求（只放**未决**）

> **本文件只放还没实现的需求。** 已闭环的条目一律在 [resolved.md](resolved.md) 附录 B
> 「ParticleDrawing 已闭环条目」里（含当时的登记原文），本文件不保留已解决内容。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **顺序（硬性）**：先写清**目标模型** → 对着 PD**现有能力**列缺口并登记 → **然后**才选临时降级
> （并在代码注释里标明"这是降级、需求落地后删"）。先绕后记账是本项目此前最大的结构性问题。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：见 `gradle.properties` 的 `particledrawing_version`（现为 `1.0.14-ALPHA`，
> `lib/particledrawing-1.0.14-ALPHA.jar` 另附 `-sources.jar` 便于核对签名）。

---

**当前未决：5 条。**

### [需求] 发射器无「逐粒子位置抖动 / 偏移」

- **状态**：未决
- **场景**：拖尾除了"沿路径铺一串"，还需要一点点**侧向不齐**（本侧原先用 0.045 格的抖动做"手划过的痕迹"）。
- **现有 API 为什么不够**：`ParticleEmitter` 只有 `spacing`/`interval`/`life`/`scale`/`color`/`velocity`/曲线，
  **没有 offset / jitter / spread**；要逐颗抖开就只能回到 `spawnAll` 逐颗给位置（那就是"每颗一发"，发射器省下的带宽又还回去了）。
- **期望形态**：`emitter.jitter(格)`（垂直运动方向的随机偏移，确定性哈希）+ 可选 `offsetAlong(前后偏移)`；
  或 `spread(半径, 数量)` 之类的面/体发射口。
- **影响面**：`magic/effect/visual/Trajectory.kt`（当前被迫放弃侧向抖动）。
- **临时降级**：不加抖动（链条更直、观感尚可，用户未反对）。

### [需求] `EmitterHandle` 不能改 `life` / 曲线

- **状态**：未决
- **场景**：本侧 `life = 拖尾长度 ÷ 速度 ÷ 可见占比`；投射物**带阻力**，速度逐 tick 衰减（实测 0.485 → 0.382 格/tick），
  寿命必须跟着变，否则尾迹长度会漂。
- **现有 API 为什么不够**：`EmitterHandle` 只提供 `updateAnchor` / `spacing` / `interval` / `stop`；
  `life` 与曲线是**声明时定死**的 ⇒ 本侧只能在速度漂移超过 20% 时 `stop()` + 重新 `spawn()` 一次。
- **期望形态**：`EmitterHandle.life(ticks)` / `.curve(...)` / `.scale(...)`（与 `ParticleEmitter` 同名同义，
  走小包更新，已生成的粒子不受影响）。
- **影响面**：`Trajectory.ensureEmitter`（当前的重挂逻辑 + `SPEED_REBUILD_RATIO` 阈值）。
- **临时降级**：速度相对变化 >20% 就重挂声明（旧粒子各自走完寿命，视觉无跳变）。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`Trajectory`）+ 发射器，没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一 |
| 发射器锚点是「最后一条 + 速度外推」，不是「上一→本两条样本插值」 | `ClientEmitterManager.resolveAnchor`：`Movable → pos + velocity × partialTick`（20Hz 采样） | 眼睛若跟着发射器铺出来的前沿，会看出 tick 台阶；与 `track` 粒子的相位也不严格相等，只能靠"锚点压后一 tick"错开 | 第 48 轮的做法：**头部用 `track` 管丝滑、尾迹用发射器管留痕**；若将来要"尾迹贴着头部且不越界"，按本文件提需求（锚点改用两条样本插值） |
| **粒子「实际尺寸」不是 `scale` 本身** | `BridgeParticle`：`quadSize = scale × EDITOR_TO_MC_SCALE × texScale`；16×16 贴图 `texScale = 1` ⇒ **渲染整宽 = scale × 0.2 格** | 按「scale = 格数」估密度会偏小；要按世界格给尺寸用 `anisoWorld` | 本侧的换算：`scale = 格数 × 5`（见 `Trajectory` 的尺寸常量注释）；`anisoWorld` 口径的粒子**吃不到** `resize` |

---

## 2. 需求登记模板（新增需求时照抄）

```
### [需求] <一句话标题>
- 状态：未决
- 目标能力：<我们在 PrimalSpells 想实现的效果 + 触发场景>
- 现有 API 为什么不够：<类 + 方法/属性 + 编译错误原文或运行现象 + 复现步骤>
- 期望 API 形态：<签名草案（类/函数/参数/返回）；服务端还是客户端；线程与生命周期约定>
- 影响面：<本模组受影响文件>
- 临时降级：<降级范围 + 恢复条件；没有则写「无（该功能暂不可用）」>
```

> 需求落地后：把本条**整段搬进** [resolved.md](resolved.md) 附录 B（连同当时的原文），
> 本文件只留标题式的一行指针都不要留 —— 保持"打开这个文件看到的全是未决项"。


### [需求] 批量速度更新超过512成员导致客户端断线

- 状态：未决，2026-10-08第55轮；本侧停止该接入点的修复尝试。
- 目标模型：移动/互吸黑洞仍保留完整星尘，公开ParticleBatch.setVelocityAll应支持其全部存活成员，保持ID和速度一一对应。
- 实际证据：run/client/logs/latest.log于13:38:16与13:38:46记录particle_velocity_batch解码失败，条数分别519和514；这是本次双黑洞问题的实际日志；本轮全量实机回归再次复现517条载荷断线，证据保存在build/verify/black-hole-batch-disconnect.log。crash-reports最新文件为旧版本2026-08-10的伤害注册表错误，与这次断线不同。
- 现有能力：1.0.14-ALPHA的ParticleVelocityBatchPayload.MAX_BATCH=512，decode限制0..512，encode却直接写updates.size；ParticleBatch核心setVelocityAll经aligned把全部成员交给sendVelocities，没有公开API要求调用方限制512成员。
- 修改需求：在PD发送端自动拆包，每包不超过MAX_BATCH；对速度/位置/力等批量载荷统一审计编码与解码上限，并测试0/512/513/1024成员、过期成员回收后ID与向量顺序、客户端不掉线及双端最终状态一致。
- 影响：BlackHoleFx.update的星尘吸入批次；移动黑洞远离旧星尘时容易积累9×60tick的存活成员，超过512。
- 本侧处理：不截断星尘、不逐粒替代批量API、不修改lib前置jar；双黑洞长时视觉验收仍被此前置缺陷阻断。

### [需求] 组缩放继承当前倍率并提供零尺寸入场

- 状态：未决，2026-10-09第61轮；停止多段ScaleBy入场接入。
- 目标模型：固定结构由零尺寸展开，提前死亡时从当前姿态收尾，追加缩放不重置已有倍率、脉冲或旋转。
- 实际能力：ParticleGroup.scale声明相对倍率，但ClientAnimationProgramManager.applySlot对每条ScaleBy直接赋值`scaleMul=1+(ratio-1)*k`，没有当前倍率快照；`.scale(.01,0).scale(100,3)`被后一条覆盖而非恢复至1；从1.5倍接退场也先重置到1。sugarFrame还把最终视觉尺寸钳到0.001。
- 期望API：`group.scaleTo(target,ticks,easing)`与`group.scaleBy(ratio,ticks,easing)`明确区分；执行起点捕获当前合成变换，支持从0展开、任意时刻退场、帧级重定向；尺寸0应允许完全不绘制。Draw形状生成入口支持ParticleLifeCurve，方便实色片元的入场而不缩整个形状。
- 影响面：黑洞、重力场、水晶、冰壳、斥力场、爆炸火团的组级入场/退场。
- 本侧降级：取消错误的多段缩放入场；固定结构保留既有出生形式。仅从完整1倍结构追加有限缩到零的退场，爆炸取消额外膨胀倍率，力量感由移动火团与冲击环表达。已接入的单片尺寸寿命曲线继续使用现有PD通路。不能保证提前死亡或脉冲中断时组形变完全连续；前置修复后恢复验证，不能在本模组复制缩放播放器。

### [需求] 移动锚点及热更空间变量提供相邻样本插值

- 状态：未决，2026-10-09第61轮；停止改写锚点/变量插值器。
- 目标模型：移动黑洞/重力场、水晶、持续光束与拖尾的所有构件在高刷下共用连续的位姿，相位一致，不能20Hz闪移。
- 现有能力：ParticleGroup.setPivot下发BindPivot(Fixed)，客户端applySlot直接替换pivotFixed；PlasmaBeam使用setVariableLive空间端点，热更新是立即赋常量。现有Emitter Movable锚点按最后样本位置+速度×partialTick外推，未用相邻样本插值，已在本文件§1记录；track头部与尾迹因此不严格同相位。
- 期望API：`group.anchor(Anchor.Movable)`/`updateAnchor(prev,current)`、`setVariableInterpolated(name,value,ticks,easing)`；位置与朝向支持相邻服务器样本插值，明确断流、暂停、瞬移语义；发射器与运行时头部沿同一锚点求值。
- 影响面：BlackHoleFx.followCenter、GravityField移动场、CrystalReaction.setPivot、PlasmaBeam端点、Trajectory.ensureEmitter。
- 本侧降级：保留现有锚点、空间变量与发射器通路，不在本侧保存/积分一套替代粒子位姿；本轮改善起落曲线，移动的帧间连续性仍待前置补齐。
