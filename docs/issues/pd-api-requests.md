# ParticleDrawing API 需求（待对方实现）

> 本文件只放**还没实现**的需求。已闭环的条目见 [resolved.md](resolved.md) 末尾「ParticleDrawing 已闭环条目」。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：`gradle.properties` 的 `particledrawing_version=1.0.9-ALPHA`
> （`lib/particledrawing-1.0.9-ALPHA.jar`，另附 `-sources.jar` 便于核对签名）。

---

## 1. [需求] 批量施力 / 批量设速（一次网络包覆盖多粒子）

- 状态：**未决**（第 10 轮提出）
- 目标能力：黑洞的「被吸入粒子」每 tick 需要**每粒子各自**的加速度（随距离变化，
  `0.12/(距离+0.5)` 指向中心），且希望「位置由 PD 积分 + 服务端权威」。
- 现有 API 为什么不够：
  - `ParticleBatch.applyForce(handle, accel, ticks)` / `setVelocity(handle, v)` 都是**单粒子**入口，
    一颗粒子一个 `ParticleForcePayload` / `ParticleVelocityPayload` → **N 包/tick**（黑洞峰值约 34 个）；
  - 唯一稳定的批量入口 `ParticleBatch.trackAll(positions)` 是**位置**指令，而
    `ServerParticleEngine.takeOverPosition` 会**清零速度与力** —— 于是速度只能由使用方自己持有。
- 期望 API 形态（任一即可，命名仅供参考）：

  ```kotlin
  // 批量设速：一次包覆盖全组，之后由 PD 两端同规则逐 tick 积分（位置不由使用方下发）
  fun ParticleBatch.setVelocityAll(velocities: List<Vec3>): Int
  // 或批量施力（每粒子各自的加速度，施加 ticks 个 tick）
  fun ParticleBatch.applyForceAll(accelerations: List<Vec3>, ticks: Int = 1): Int
  ```

  约定：顺序与 `handles()`（登记顺序）一致；与 `trackAll` 一样按玩家可见性裁剪后再发。
- 影响面：`magic/effect/lifecycle/BlackHole.kt`（可删掉 `velocities` 表与自算积分，回到「PD 积分 + 0 包/tick」）。
- 临时降级：本模组自持「粒子 id → 速度」表 + 每 tick 一次 `trackAll`（**1 包/tick**、位置完全可控、
  暂停不外推）。恢复条件：上述批量入口可用后即可删除该表 —— 这张表只按键（UUID）关联、不做下标配对，
  不会有历史那类错位 bug，但终究是使用方在替 PD 存运动状态。

---

## 2. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 1.0.8 的 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`ProjectileBody`/`Trajectory`），没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| `lib/` 下的版本与 `particledrawing_version` 必须一致 | `lib/` 只留 `1.0.9-ALPHA` | 若手工塞入别的版本，容易对照错 API | 对照 API 一律以 `lib/particledrawing-1.0.9-ALPHA-sources.jar` 为准 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |

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
