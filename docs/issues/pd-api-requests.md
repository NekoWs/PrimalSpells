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
> 当前锁定版本：见 `gradle.properties` 的 `particledrawing_version`（现为 `1.0.15-ALPHA`，
> `lib/particledrawing-1.0.15-ALPHA.jar` 另附 `-sources.jar` 便于核对签名）。

---

**当前未决：1 条。**

---

### [需求] 组动画及热更变量的视觉尺寸逐渲染帧求值

- 状态：未决，第62轮，2026-10-09；停止严格高刷新率连续性的接入尝试。
- 目标模型：组scaleTo/scaleBy、表达式sc、变量缓动在60/144Hz时也连续变化；提前中断捕获当帧值，完成至零后才销毁。
- 现有能力：1.0.15-ALPHA已补scaleTo当前倍率接续、零尺寸、移动样本、setVariableInterpolated。其ClientAnimationProgramManager.tick只从ParticleRenderHandler.onPlayerTick调用，now按gameTime×50求值；refreshInputs先复制变量，applyVarEases后更新变量。ClientParticleEngine.applyProgramFrame直接syncScale，BridgeParticle.getQuadSize(partialTick)直接返回scaleH，没有组尺寸的相邻帧插值。位置端点能由桥接插值，但尺寸/变量输出仍是20Hz阶梯。
- 证据：已核对本地源码与1.0.15-ALPHA sources JAR；第62d轮第三人称正面退场覆盖t197仍3423px、t198归零，销毁早于该视角最后变量收束；当时PNG已被后续补录替换，数值与日志保留在assembly62d.log；当前PNG/视频见build/capture/out。其余视角20fps门禁通过，不能据此证明高刷连续。
- 期望API：组动画/变量/表达式视觉通道使用同一单调渲染时钟逐帧求值；或桥接层插值尺寸/色彩。变量重定向先按当前时钟求旧曲线值，再捕获起点，热更新新目标每tick持续输入不能饥饿；先更新变量再形成表达式输入快照。公开onAnimationComplete/retire(duration)让销毁与客户端真正到零一致。
- 影响面：WandCastingFx的presence/移动变量、PlasmaBeam端点，以及全部组级尺寸起落；ParticleLifeCurve自身已有逐帧尺寸通路，不属于此缺口。
- 临时降级：保留前置原生组/变量动画及现有位置桥接插值，不自写播放器；法阵350ms原生缓动、600ms销毁留余量，继续逐帧检查末尾不能硬切。严格高刷保证等待前置补齐。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`Trajectory`）+ 发射器，没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一 |
| 发射器与可移动组轴心按相邻服务器样本插值 | PD 1.0.15-ALPHA提供Movable样本和updateAnchor | 无需本侧压后一tick | Trajectory传当前位置，与track头部同相位；固定朝向结构显式Orient.WORLD |
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

- 第63轮补充：持续运动时每刻重启1刻setVariableInterpolated确实导致法阵/移动光束位置不收敛。本轮停止该接入点，玩家位置改用已有实体getter、束端点改用已有setVariableLive+原生位置桥；没有自写粒子更新/插值。仍需修正重定向时当前值捕获与refreshInputs/applyVarEases顺序，并实现逐渲染帧求值。

## 第66轮：静止非发光粒子的光照缓存不失效（2026-10-09）

- 状态：未决；本地源码及锁定1.0.15-ALPHA sources JAR均确认同一逻辑，未修改前置或在本模组补渲染器。
- 目标能力：非发光片元在外部动态光靠近时变亮，光源移动/销毁/关闭发光后及时恢复环境亮度；原版方块光变化、区块重新加载同样刷新。
- 复现：暗室生成位置固定、`glowing(false)`、寿命长的方片；首次渲染前在旁边创建`glowing(true)`光源，随后移开或销毁光源。方片不跨方块时仍保持先前亮度；把方片跨过方块边界才刷新。反向情况（先暗后加光）也会错误保持暗。
- 源码：`core/client/BridgeParticle.kt:334–354`，`cachedLight`只在初值<0或整数方块坐标改变时调用`super.getLightCoords`，没有时间、光源版本或世界亮度失效条件。MC26.2 `Particle.getLightCoords`调用`LightCoordsUtil.getLightCoords`，PD `BrightnessGetterMixin`叠加动态光；因此缓存的是动态光合并后的结果，而非稳定环境基值。与是否additive无关。
- 期望修复：由PD在客户端管理光照缓存失效；可缓存环境采样但按世界亮度变更刷新，并在每个渲染帧用当前动态光重新合并；或使用环境/动态光版本号使缓存失效。发光→非发光、光源移动/关闭/删除、动态光总开关切换、区块卸载重载都必须覆盖。无需PS轮询/重发粒子。
- 验收：固定非发光方片暗→亮→暗，无移动、无重建；60/144Hz均能恢复；移动方片与静止方片结果一致，客户端离开世界后不残留旧光照。
- 影响面：爆炸烟团、晶面暗边与全部`glowing(false)`法术片元。临时处理：保留原生光照与粒子，不通过强制发光、改色、抖位置或逐刻重生绕过。等待PD修复后更新前置再实机闭环。


## 第68轮：track位置接管错误冻结寿命曲线（2026-10-09）

- 状态：未决；本地源码及锁定1.0.15-ALPHA sources JAR均确认相同逻辑。
- 目标能力：ParticleBatch.trackAll只接管位置；sizeCurve仍独立控制片元出生和熄灭，特别是0→1出生。
- 复现：spawnAll创建anisoWorld(0.125,0.125)、lifetime(-1)、sizeCurve(0→0,3→1)方片，随后每刻trackAll。实机第68a轮第一人称第43帧只剩1～3像素亮点，尺寸没有成长。
- 原因：ClientParticleEngine.applyTrackPosition把id加入directIds，但frameSyncCurves无条件跳过directIds；位置桥只syncPosition，不刷新外观。track占用的标记与完整动画程序直写外观共用，冻结在首帧曲线乘数。
- 期望修复：区分仅位置接管与完整外观接管；track/trackAll的粒子逐渲染帧正常求寿命曲线，并保留anisoWorld尺寸。覆盖出生、保持、收尾、改变位置源及60/144Hz。
- 影响面：汇聚之光原先的逐刻棱片跟随，以及所有track+lifeCurve组合。
- 接入处理：停止本次trackAll+sizeCurve组合；固定镜阵无需追踪实体，改用已有公开ParticleHandle.move一次性20刻插值至合拢位置，sizeCurve只管尺寸。不自写求值、渲染、尺寸补偿或重生粒子；等PD修复后再验证需要实时跟随的组合。
