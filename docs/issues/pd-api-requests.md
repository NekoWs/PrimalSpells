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
> 当前锁定版本：见 `gradle.properties` 的 `particledrawing_version`（现为 `1.0.16-ALPHA`，
> `lib/particledrawing-1.0.16-ALPHA.jar` 另附 `-sources.jar` 便于核对签名）。

---

**当前未决：1 条。**

---

## 第72轮：完成回报尚未覆盖表达式与变量缓动（2026-10-09）

- 状态：1.0.16-ALPHA新onAnimationComplete/retire部分接入，停止表达式/变量通路的完成销毁接入。
- 目标：脚下三角形presence归零及施放/充能内外边表达式收尾，等客户端真正完成和桥接插值结束后清理；中断后以当前值继续。
- 现有能力：普通scaleTo等有限糖指令可回报完成；setVariableInterpolated通过独立SetProgramVarEasePayload下发，不更新ParticleGroup.timelineEndMs，纯expression组hasFiniteInstruction=false，retire直接警告并返回。ClientAnimationProgramManager.tick的expressionFrame分支continue，跳过reportCompletionIfDue；表达式上即使追加有限糖指令也不会正常回报，且该分支不执行糖指令。不能用无效果有限命令充当完成标记。
- 期望：把变量缓动的有限时长纳入客户端和服务端完成账本（支持新目标取消旧结束点、最后一个有效缓动完成后回报）；表达式组同样参与完成检测，或提供显式retireVariable(name,target,duration,easing)与有限表达式结束时刻。onAnimationComplete/retire在这两条通路不得静默不生效，客户端缺席仍由PD兜底。
- 影响：WandCastingFx主阵presence/延迟及充能提示、PlasmaBeam表达式收尾；普通PixelVfx组已可使用新接口。
- 临时处理：保留原有有限定时销毁与余量，不自写回报协议/客户端播放器。ParticleHandle.resize与Batch成员仍沿用现有有限清理，不假装已取得组完成信号。
- 验收：表达式组只调用setVariableInterpolated(presence,0,7).retire即可在当帧归零并完成位置/尺寸插值后销毁；重复重定向不提前触发旧回调；无客户端不泄漏，切维/登出清理有效。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`Trajectory`）+ 发射器，没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一 |
| 发射器与可移动组轴心按相邻服务器样本插值 | PD 1.0.16-ALPHA提供Movable样本和updateAnchor | 无需本侧压后一tick | Trajectory传当前位置，与track头部同相位；固定朝向结构显式Orient.WORLD |
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
