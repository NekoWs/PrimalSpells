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

**当前未决 1 条**（第 23 轮登记）：程序化粒子也应能指定贴图 / UV（见下）。

四个历史需求（第 15/17/19 轮登记）已由 **ParticleDrawing 1.0.12-ALPHA** 全部实现，
逐条对照与「本侧因此简化了什么」见 [resolved.md](resolved.md) 附录 B「ParticleDrawing 已闭环条目」。

---

### [需求] 程序化粒子也应能指定贴图 / UV（现在是**写死的 8×8 白方块**）

- 状态：未决（第 23 轮登记；本侧已用「密集小方块拼几何」降级，玻璃质感因此只能做到"磨砂"，做不到"真玻璃"）
- 目标能力：
  1. **柔边/半透明贴图**：护盾「半透明玻璃」的受击面板、能量球壳、薄雾类效果 —— 硬边方块的堆叠在近距离会糊成一块（实机反馈过「不完美的形状很丑」）；
  2. **线段贴图 + 各向异性**：一条棱用**一张沿线段拉长的贴图、一颗粒子**就能画完。现在画一条连续的棱要沿棱每 0.05 格放一颗、一条棱 20+ 颗、整颗球壳上千颗，近距离仍是一串方块（第 20 轮实测：`scale 0.40` 的方块在球内最近处占 80+ 像素）；
  3. **子矩形 UV**：一张图集里放多种形状（点/线/月牙/六边形），按粒子选 UV。
- 现有 API 为什么不够（均为源码核对，不是猜）：
  - **贴图写死**：`core/client/TextureCache.defaultWhite()` 造的是 **8×8 全不透明纯白**，且它是程序化粒子的唯一贴图（KDoc 明说「无贴图粒子的默认全白纹理」）；
  - **API 无入口**：`ParticleHandle.Builder` 只暴露 `position/color/scale/lifetime/group/offsetFromPivot/glowing/lightLevel/spawn`，**没有 texture / uv / style**；`api/ParticleStyle.kt` 目前是**空文件**；
  - **载荷无槽位**：`core/network/ParticleSpawnPayload` 的字段只有 `particleId/xyz/rgba/scale/lifetime/groupId/glowing/lightLevel` —— 服务端就算想指定贴图，**也没有地方传**（批量子类载荷同理）；
  - **能力其实已经有了，只是没接过来**：`.pdrawc` 那条路一直支持自定义贴图与 UV ——
    `Animation.Builder.texture(name, bytes)`、`groupUV(name, UvData)`、`ParticleBuilder.uv(UvData)`、
    `FunctionBuilder.uv(...)`，底层走 `TextureCache.load(name, pngBytes)`（运行时注册 `DynamicTexture`，名字映射到 `particledrawing:custom/<md5>`）。
    也就是说「解码 + 注册 + UV 归一化」这套机制是现成的，缺的只是**程序化粒子的入口与载荷字段**。
  - 复现：`manager.create().position(p).color(c).scale(0.25f).spawn()` → 屏幕上是一个**硬边白方块**；
    想画「一条线」只能靠密集排列（见上面的数量级），且没有柔边可用。
- 期望 API 形态（(a)+(b) 为主，(c) 顺手做也好）：
  - **(a) 注册入口**：`ParticleManager.registerTexture(name: String, pngBytes: ByteArray)`（内部走现成的 `TextureCache.load`），
    或复用一个全局 `ParticleTextures.register(...)`；返回可复用的句柄/名字。未知名字必须**回落 `default_white` 而不是丢粒子**。
  - **(b) 逐粒子指定**：`ParticleHandle.Builder.texture(name)` + 可选 `.uv(u0, v0, u1, v1)`（子矩形）
    + `.anisotropic(width, height)`（或 `scale(x, y)`）—— 后者让「一张线段贴图沿棱拉长」成立。
  - **(c) 内置基础形状**（对第三方最省事）：`ParticleStyle.SOFT_DOT`（柔边圆点）/ `SQUARE`（现状）/ `LINE`（沿指定方向拉长），
    配 `Builder.style(ParticleStyle)`；其中**柔边圆点**是能量护盾 / 玻璃 / 薄雾这类效果最缺的那一个。
  - **载荷**：`ParticleSpawnPayload`（及批量子类载荷）增加贴图标识与可选 UV/各向异性字段；
    若组程序的成员也要带贴图，按「生成时定死、后续更新不变」的约定最省带宽（否则每次全量重发都要带）。
  - 线程与生命周期：注册沿用现有 `TextureCache`（客户端 `TextureManager`，主线程）；服务端只持有名字/字节。
    「贴图在生成时确定、之后不可变」这个约定我们能接受，不接受也不会阻塞 —— 只要入口存在即可。
- 影响面：`magic/modifier/EnergyShield.kt`（受击玻璃面板）、`magic/effect/lifecycle/*`（凡是「用粒子拼线」的地方）、
  `util/PdParticles.kt`（封装入口会加 texture/style 参数）。
- 临时降级：**继续用密集小方块拼几何**（现状）：玻璃面板 = 半透明方点铺满 + 亮边，
  能做到「磨砂玻璃」，做不到「清透玻璃」；棱仍是点阵而非实线。
  恢复条件 = (a)+(b) 或 (c) 任一落地后，本侧把「铺满面板」换成**一张柔边贴图**、把「沿棱排点」换成**沿棱拉长的线段贴图**
  （粒子数可降一到两个数量级）。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 1.0.8 的 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`ProjectileBody`/`Trajectory`），没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| `lib/` 下的版本与 `particledrawing_version` 必须一致 | `lib/` 只留 `1.0.12-ALPHA` | 若手工塞入别的版本，容易对照错 API | 对照 API 一律以 `lib/particledrawing-1.0.12-ALPHA-sources.jar` 为准 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一（此前 `setVelocity` 漏了解除，位置每 tick 仍被锚点覆盖） |
| **程序化粒子固定使用纯白方块贴图** | `TextureCache.defaultWhite()` = 8×8 全白；`ParticleHandle.Builder` 与 `ParticleSpawnPayload` 都没有贴图/UV 字段（贴图只对 `.pdrawc` 开放） | 「细线」「柔边」「玻璃质感」都只能用密集硬方块逼近，近距离必然发糊（第 20 轮实测） | 第 23 轮已登记为 API 需求（见上）；在它落地前，护盾球壳与玻璃面板都用**点阵/磨砂**路线（点距 0.194 格 vs 粒径 0.053 格） |
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
