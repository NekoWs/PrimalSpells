# ParticleDrawing API 需求（待对方实现）

> 本文件只放**还没实现**的需求。已闭环的条目见 [resolved.md](resolved.md) 末尾「ParticleDrawing 已闭环条目」。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：`gradle.properties` 的 `particledrawing_version=1.0.10-ALPHA`
> （`lib/particledrawing-1.0.10-ALPHA.jar`，另附 `-sources.jar` 便于核对签名）。

---

**当前 1 条未决**（文档级，见下）。上一条（批量施力 / 批量设速）已由 PD `e0cbd5d`（**1.0.10-ALPHA**）落地
（`ParticleBatch.setVelocityAll` / `applyForceAll`，一次包覆盖全组、按 `handles()` 顺序对齐），
本模组已在第 12 轮接入（黑洞据此删掉了自持的「粒子 id → 速度」表）。已闭环条目见 [resolved.md](resolved.md) 附录 B。

## 1. [需求] 写明 `ParticleGroup` 各变换指令用「哪个轴心」（文档级）

- 状态：**未决**
- 目标能力：护盾球壳要「跟随持有者 + 持续自转」，即 `.followEntity(uuid, offset)` 之后再 `.spin(axis, radPerTick)`。
  文档只说 `spin` 是「无限匀速旋转」，**没说它绕哪个轴心转**，也没说它和 `followEntity` 的调用顺序是否有约束。
- 现有 API 为什么不够（不是行为错，是语义没写明）：
  - `ParticleGroup.spin` 的实现是 `emit(AnimInstruction.Spin(cursorMs, PivotRef.Fixed(pivot), axis, radiansPerTick / 50))`
    —— 它塞进去的 `PivotRef.Fixed(pivot)` 在客户端**根本没被读**：客户端处理 `Spin` 时只做
    `rotateToSnapshot(p, slot, ins.axis, angle)`（`ClientAnimationProgramManager` 内），轴心用的是
    **当前轴心绑定**（`p.pivotEntity` / `p.pivotFixed`，由 `BindPivot` 设）。
  - 结论是「先 `followEntity` 再 `spin` 才绕实体转」，但这一点只能靠**读客户端源码**确认；
    `spin`/`followEntity` 的 KDoc 都没写。指令载荷里那个被忽略的 `PivotRef` 也容易让人误判。
- 期望 API 形态（二选一即可）：
  1. **补文档**：`spin`（以及 `rotate`/`scale`/`pulse` 等以轴心为基准的指令）KDoc 写明
     「绕**当前**轴心绑定旋转；要用实体轴心请先 `followEntity(...)`」；
  2. 或**去掉** `AnimInstruction.Spin` 里未被消费的 `PivotRef`（避免读者以为可以逐指令指定轴心）。
- 影响面：`modifier/EnergyShield.kt`（第 12 轮接入护盾自转时踩到）；未来任何「跟随 + 旋转」的组动画
- 临时降级：本模组已按「先 `followEntity` 再 `spin`」接线，并把结论写进
  `magic/modifier/EnergyShield.kt` 的 KDoc 与 [design/effects-api.md](../design/effects-api.md) §0.3；
  实机若出现「转起来后球壳偏出身体」就是这条没吃透（[verify.md](verify.md) 第 20 项）。

## 2. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 1.0.8 的 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`ProjectileBody`/`Trajectory`），没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| `lib/` 下的版本与 `particledrawing_version` 必须一致 | `lib/` 只留 `1.0.10-ALPHA` | 若手工塞入别的版本，容易对照错 API | 对照 API 一律以 `lib/particledrawing-1.0.10-ALPHA-sources.jar` 为准 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.10-ALPHA 起口径统一（此前 `setVelocity` 漏了解除，位置每 tick 仍被锚点覆盖） |

---

## 3. 需求登记模板（新增需求时照抄）

```
### [需求] <一句话标题>
- 状态：未决 / 已由 ParticleDrawing <版本> 解决
- 目标能力：<我们在 PrimalSpells 想实现的效果 + 触发场景>
- 现有 API 为什么不够：<类 + 方法/属性 + 编译错误原文或运行现象 + 复现步骤>
- 期望 API 形态：<签名草案（类/函数/参数/返回）；服务端还是客户端；线程与生命周期约定>
- 影响面：<本模组受影响文件>
- 临时降级：<降级范围 + 恢复条件；没有则写「无（该功能暂不可用）」>
```
