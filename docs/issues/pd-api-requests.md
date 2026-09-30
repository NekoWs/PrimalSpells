# ParticleDrawing API 需求（待对方实现）

> 本文件只放**还没实现**的需求。已闭环的条目见 [resolved.md](resolved.md) 末尾「ParticleDrawing 已闭环条目」。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：`gradle.properties` 的 `particledrawing_version=1.0.12-ALPHA`
> （`lib/particledrawing-1.0.12-ALPHA.jar`，另附 `-sources.jar` 便于核对签名）。

---

**当前未决：0 条。** 第 23/28 轮登记的「贴图 / UV / 各向异性 / 朝向 / 加色」整组能力
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
