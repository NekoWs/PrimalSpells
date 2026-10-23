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
> 当前锁定版本：见 `gradle.properties` 的 `particledrawing_version`（现为 `1.0.20-ALPHA`，
> `lib/particledrawing-1.0.20-ALPHA.jar` 另附 `-sources.jar` 便于核对签名）。

---

**当前未决：1 条。**

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`Trajectory`）+ 发射器，没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一 |
| 发射器与可移动组轴心按相邻服务器样本插值 | PD 1.0.20-ALPHA提供Movable样本和updateAnchor | 无需本侧压后一tick | Trajectory传当前位置，与track头部同相位；固定朝向结构显式Orient.WORLD |
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


## 第85轮（2026-10-23）：短寿命粒子的曲线时钟与末端退场

- 状态：未决，PD 1.0.20-ALPHA；本模组停止1～2t紧凑拖尾接入，保留已有4t下限，不自建动画时钟或桥接渲染。
- 目标模型：高速弹的单颗尾迹只存在1～2t，仍应在60/144Hz下经历尺寸入场、alpha从1连续降到0，然后销毁。生命周期内的关键帧必须能被渲染，不应只因寿命短于若干完整刻就跳过整段。
- 现有API：ParticleLifeCurve配合ParticleCurve.alpha/scale、EmitterHandle.life/lifeCurve均存在；BridgeParticle能插值收到的外观端点，但没有收到的短寿命曲线中间/末端值无法凭空补出。
- 源码定位：lib内sources JAR的core/client/RenderParticle.kt中ageTicks=engineMs/50，advanceEngine只每刻+50；ClientParticleEngine.frameUpdate在advance后立即移除isDead粒子；frameSyncCurves虽每帧调用，却用同一整数刻年龄取样。因此1t粒子可在下一次取样前就被删除，2t粒子也会跳过短入场与alpha=0末端。
- 实机证据：build/capture/turn85-regression中的1～2t拖尾20Hz探针decreasing=0、minimum=0.50714284；随后RenderFrameEvent.Post逐渲染帧复验仍相同（录制日志build/capture-turn85-trail.log，归档turn85-short-tail）。变速后较长寿命拖尾的原生探针却正常，说明alpha通道接线存在且有效，不是本模组漏传曲线。
- 需要PD修改：寿命曲线在渲染端使用与出生时刻一致的分数年龄；把运动/游戏刻推进与外观曲线采样分开。销毁顺序保证短粒子的可见中间段和零alpha/零尺寸末态正确收束，暂停与恢复仍沿游戏时钟，不能引入跨帧闪现。
- 验收：相同声明分别使用1t、2t、4t寿命，在20/60/144FPS取0/25/50/75/接近100%寿命的实际Quad输出，验证中段可见、透明度/尺寸按声明演化、末端清零；stop后已出生粒子正常收尾，最后客户端/服务端计数均归零；重复lifeCurve覆盖不能叠乘或丢通道。
