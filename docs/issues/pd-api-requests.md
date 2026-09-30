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

**当前未决：1 组**（第 23 轮首次登记贴图/UV，第 28 轮合并为「达成整个特效所需的整组能力」）。

四个历史需求（第 15/17/19 轮登记）已由 **ParticleDrawing 1.0.12-ALPHA** 全部实现，
逐条对照与「本侧因此简化了什么」见 [resolved.md](resolved.md) 附录 B「ParticleDrawing 已闭环条目」。

---

### [需求组] 程序化粒子需要「贴图 / UV / 各向异性 / 朝向 / 加色」整组能力

- **状态**：未决（第 23 轮登记贴图/UV；第 28 轮按"达成整个特效"合并成一组，补上优先级与源码依据）
- **场景**：爆炸的**「内爆 → 爆发」**表现 —— 黑色核心球 + **发光螺旋丝线**向心收拢 → 塌成亮点 →
  **半透球壳**撑到爆炸半径 + 内部锯齿闪电丝 + 壳裂成分段弧飞散。
  目标观感的关键是**柔边 + 发光的长丝线**；用现在的硬边白方块点阵堆，只会得到"马赛克"，堆得越多越糊
  （第 28 轮实机反馈：「太丑」「没有冲击力」）。

**为什么现有 API 不够（逐条源码依据，`lib/particledrawing-1.0.12-ALPHA-sources.jar`）**

| 想要的效果 | 现状 | 源码位置 |
|---|---|---|
| 柔边圆点（黑核 / 能量球 / 薄雾） | 程序化粒子的贴图写死为 **8×8 纯白硬边方块** | `core/client/TextureCache.defaultWhite()`（`NativeImage(8,8)` 全不透明）；`core/client/BridgeParticle.resolveTexture()` 对没有 uv 的粒子直接回落它 |
| 逐粒子贴图 / 子矩形 UV | `ParticleHandle.Builder` 只有 `position/color/scale/lifetime/group/glowing/lightLevel/offsetFromPivot/spawn` —— **没有 texture / uv**；`api/ParticleStyle.kt` 是**空文件** | `api/ParticleHandle.kt`（Builder 私有字段与方法清单） |
| 载荷没有槽位可传 | `ParticleSpawnPayload` 字段只有 `particleId/xyz/rgba/scale/lifetime/groupId/glowing/lightLevel` | `core/network/ParticleSpawnPayload.kt:24-33` |
| 一条丝 = 一张沿线段拉长的贴图 | **渲染层其实已经支持**：双轴尺寸 `scaleW/scaleH`、非广告牌朝向 + 自转都对 `.pdrawc` 开放；缺的只是程序化入口与载荷 | `BridgeParticle`：`scaleW/scaleH = scaleArray[0/1] × EDITOR_TO_MC_SCALE × texScale`（:177-179）、`syncOrientation(billboard, spin, spinLocal)`（:57）、`orientationQuaternion()`；同层的动画模型 `AnimParticle(..., uv, billboard, spinLocal)`（`core/network/ParticleAnimationCodec.kt:188`）早就在用这些字段 |
| 发光溢出（加色辉光） | 只有 `glowing`（全亮不受光照）与 `lightLevel`；**加色语义在动画那条路已有**（`FunctionObject(..., additive)`），程序化入口没有 | `api/Animation.kt:247`（`glowing`）、`core/network/ParticleAnimationCodec.kt:277`（`additive`） |
| 图元直接产出发光丝线 | `Draw.*` 内部 `spawnInto` 只设 `position/color/scale/lifetime/group/offsetFromPivot` —— **连 `glowing` 都没设** | `api/Draw.kt:338-346` |

**期望 API 形态**（按对本次效果的必要性排序）

**P0 —— 硬阻塞（不做就出不来参考质感）**

1. **贴图注册入口**
   `ParticleManager.registerTexture(name: String, pngBytes: ByteArray): Boolean`
   （内部走现成的 `TextureCache.load(name, pngBytes)`，幂等；**未知名字必须回落 `default_white` 而不是丢粒子**）
2. **逐粒子贴图 + 子矩形 UV**
   `ParticleHandle.Builder.texture(name: String)` + `.uv(u0: Float, v0: Float, u1: Float, v1: Float)`
   （或直接 `.uv(uv: UvData)`，复用动画那套结构）
3. **逐粒子各向异性尺寸**
   `ParticleHandle.Builder.scale(w: Float, h: Float)` → 接到 `BridgeParticle.scaleW/scaleH`
   （现在的 `scale(Float)` 是各向同性；一条沿线段拉长的贴图必须能单独控制长/短轴）
4. **逐粒子朝向**
   `ParticleHandle.Builder.billboard(enabled: Boolean)` + `.spin(radians: Double, spinLocal: Boolean = true)`
   → 接到 `syncOrientation(...)`。**这是"丝线沿线段方向对齐"的必要条件**（否则线段贴图永远面向相机，方向不可控）
5. **载荷扩字段**（唯一涉及协议改动的一项）
   `ParticleSpawnPayload`（若批量/更新类载荷也要带，同样扩）增加
   `textureName: String?`、`uv: UvData?`、`scaleW/scaleH: Float`、`billboard: Boolean`、`spin: Double`、`spinLocal: Boolean`。
   **约定：生成时定死、后续不变** → 不增加任何逐 tick 包（现有 `ParticleUpdatePayload` 等更新类载荷可以不动）。

**P1 —— 质感的一半在这里，工作量很小，建议与 P0 同批**

6. **内置基础形状**：把 `api/ParticleStyle.kt`（现在空文件）填成
   `enum class ParticleStyle { SQUARE, SOFT_DOT, LINE }`，并加 `Builder.style(style)` + `ParticleManager.registerBuiltinTextures(…)`。
   `SOFT_DOT` = 柔边圆点（黑核"实心球"、能量球壳"膜感"、薄雾最缺的就是它）；
   `LINE` = 沿指定方向拉长、两端渐隐的线段贴图（配合 3、4 用）
7. **加色混合开关**：`Builder.additive(enabled: Boolean)`。参考图「光溢出来」的那层效果靠它；
   PD 自己持有渲染类型（`core/client/BatchedQuadParticleGroup.kt:22` 注册 `PARTICLE_DRAWING_BATCHED`），
   加一趟 additive pass 即可，不必让使用方碰渲染层
8. （可选）**emissive 泛光/bloom 后处理**：能让能量类特效再上一个台阶；不做也能靠 6+7 到八成

**P2 —— 省事，不急**

9. `Draw.*` 图元接受 `glowing / texture / aniso / taper` 参数（现在连 `glowing` 都没有，见上表最后一行）
10. 一个 **polyline / beam 图元**：`Draw.polyline(points: List<Vec3>, thickness: Float, taper: Boolean, texture: String?, additive: Boolean)`
    —— 闪电丝/能量弧一条调用画完（我现在得为每条丝手写 40+ 颗粒子）
11. **逐粒子生成延迟**（动画路径有 `st`）：让我用一条指令铺出「分批出现」的收拢粒子

- **影响面**：`magic/effect/lifecycle/ExplosionFx.kt`（本次全部表现）、`util/PdParticles.kt`（封装入口要加 `texture/style/scale2/spin` 参数）、
  `magic/modifier/EnergyShield.kt`（受击玻璃面板同样受益：一条棱一张贴图，粒子数可降一到两个数量级）
- **临时降级**：**本次爆炸视觉暂停在这一步**（不再往程序化粒子上堆密度 —— 堆到参考的连续度需要每颗爆炸上千颗粒子，
  近距离仍是硬方块、且只会更糊）。已交付的**形态**保留：黑核 + 蓄力线条 + 向心聚拢 → 球壳放大到爆炸半径 + 外飞粒子、
  伤害推迟到蓄力结束（见 [resolved.md](resolved.md) 第 28 轮）。
- **恢复条件**：P0 任一项落地即可开始逐项改善（贴图/UV 先解锁柔边，各向异性+朝向解锁丝线，加色解锁辉光）；
  **P0 全落地 = 能完整做出参考那套**。几何与相位方案已定，届时只需重写表现层。

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
