# ParticleDrawing API 需求（待对方实现）

> 本文件只放**还没实现**的需求。已闭环的条目见 [resolved.md](resolved.md) 末尾「ParticleDrawing 已闭环条目」。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：`gradle.properties` 的 `particledrawing_version=1.0.11-ALPHA`
> （`lib/particledrawing-1.0.11-ALPHA.jar`，另附 `-sources.jar` 便于核对签名）。

---

**当前未决 4 条**：第 15 轮登记的两条 —— `ParticleGroup.scale`/`pulse` 只缩视觉尺寸、
不缩「粒子到轴心的距离」（与它们的 KDoc 不符）；`ParticleGroup.move` 会改掉 arm 基准轴心（编排顺序陷阱）；
外加第 17 轮登记的**「组指令缺少『从现在起』的时间锚点」**（建组很久之后再追加动画会瞬间完成）与
第 19 轮登记的**「`spin` 与一次性 `rotate` 应该叠加，而不是互相覆盖」**（见下节最后两条）。

另有两条已闭环：

- **批量施力 / 批量设速** → PD `e0cbd5d`（**1.0.10-ALPHA**）落地 `ParticleBatch.setVelocityAll` / `applyForceAll`
  （一次包覆盖全组、按 `handles()` 顺序对齐），本模组第 12 轮接入（黑洞据此删掉了自持的速度表）；
- **「写明 `ParticleGroup` 变换指令用哪个轴心」（文档级）** → PD `3007b22`（**1.0.11-ALPHA**）补写了轴心语义
  （轴心是**程序级状态**、**调用顺序即语义**、绑定一次对其后的 `rotate`/`spin`/`scale`/`pulse` 生效），
  并删掉了指令里那个从未被消费的 `PivotRef` 字段。本模组第 13 轮据此定位并修掉了护盾球壳的轴心偏移 bug。

已闭环条目见 [resolved.md](resolved.md) 附录 B。

## 未决需求（第 15 轮登记）

### [需求] `ParticleGroup.scale`/`pulse` 应「连粒子到轴心的距离一起缩放」，或改掉 KDoc

- 状态：未决
- 目标能力：球壳类特效的**径向**动画 —— 护盾「整壳胀开 / 缩回体内」、黑洞球壳「胀大 / 塌缩」。
  「一个组整体放大缩小」是这件事最自然的表达（一个 `scale` 就够，客户端本地求值、0 带宽）。
  触发场景：`EnergyShield` 的展开/收起、`BlackHole` 的球体进出场（第 15 轮）。
- 现有 API 为什么不够：
  - `ParticleGroup.scale(ratio, durationTicks, easing)` 的 KDoc 写「相对**当前轴心**等比缩放：
    **粒子到轴心的距离**与视觉大小同乘 [ratio]」；`pulse` 写「相对**当前轴心**缩放，同 `scale`」。
  - 但 1.0.11-ALPHA 的实现里，`ScaleBy` / `Pulse` 只写 `Program.scaleMul` / `pulseMul`，
    最终位置是 `ClientAnimationProgramManager.sugarFrame` 的
    `pivot.apply(p.pathOffset.add(st.rel))` —— **`rel` 完全没有乘倍率**，倍率只进了
    `engine.applyProgramFrame(..., scale)` 的**视觉尺寸**参数；表达式模式（`expressionFrame`）
    同样只把 `o[4]` 用作 `scl`。
  - 复现：`val g = manager.createGroup(center)` → `Draw.circle(manager, center, 3.0, 24, group = g)`
    → `g.scale(3f, 20)`。预期（按 KDoc）：圆周半径 3 → 9；实际：只有每颗粒子变大，**圆周半径不变**。
- 期望 API 形态（二选一）：
  - (a) 让 `ScaleBy`/`Pulse` 同时缩放相对偏移：`pivot.apply(p.pathOffset.add(st.rel.scale(p.scaleMul * p.pulseMul)))`
    —— 与现有 KDoc 一致，使用方无感；
  - (b) 若「只缩尺寸」才是本意，请**改 KDoc** 并另给两个明确的入口，例如
    `radialScale(ratio, durationTicks, easing)`（只缩到轴心的距离）与现有 `scale`（只缩尺寸）；
    或 `scale(ratio, ticks, easing, radial: Boolean = true)`。
  服务端/客户端：组指令本就是「两端同规则、客户端本地求值」，无新增同步需求；线程与生命周期同现有组指令。
- 影响面：`magic/modifier/EnergyShield.kt`、`magic/effect/lifecycle/BlackHole.kt`。
  ⚠️ **第 16 轮起本侧不再被它阻塞**：护盾改成「固定球体 + 逐块淡入淡出」（不需要径向缩放），
  黑洞的径向进/退场改用「逐粒子缓动 `ParticleHandle.move`」与「同位置重新生成 + 向心初速」。
  该需求仍然成立（一个 `scale` 就能替掉这两套写法），只是不再挡路。
- 临时降级：**无**（该能力暂不可用）。径向动画已用**不依赖该 API** 的两种写法保住观感：
  逐面板 `move`（护盾）与向心初速粒子（黑洞）；恢复条件 = (a) 或 (b) 任一落地后，
  可删掉一部分面板组、把展开/收起压回单组。

### [需求] 明确 `ParticleGroup` 的 arm 基准轴心：`move()` 不应悄悄改掉它

- 状态：未决（**语义待明确 + 建议**；已在本侧按最保守口径规避）
- 目标能力：「先铺形状、再录动画」这种最自然的写法应当得到正确的相对偏移。触发场景：
  `EnergyShield` 的 32 个面板组（第 15 轮）；任何 `Draw.*(...)` 之后紧跟 `move(...)` 的组都会遇到。
- 现有 API 为什么不够：
  - `ParticleGroup.move(delta, durationTicks, easing)` 在 `emit` **之前**执行 `pivot = pivot.add(delta)`；
  - 客户端 `ClientAnimationProgramManager.arm()` 用**收到「全量程序」那一刻的轴心字段**反算每颗粒子的相对偏移：
    `rel = 粒子 snapshot 位置 − initialPivot`，而 `flush()` 发出的正是**当前** `pivot` 字段。
  - 于是**第一条被录进组里的指令决定了 arm 基准**：若它是 `move`，基准已经被平移过。
    对**固定轴心**的组，误差会与 `Translate` 的位移精确抵消（渲染位置 = spawn 位置 + 累积位移，与基准无关）；
    但**对实体轴心**（`followEntity`）不会抵消 —— 渲染用的是「实体当前坐标 + 绑定偏移」，
    与 arm 时的字段无关，误差会**整体保留**。
  - 复现（把 `move` 放最前）：`createGroup(feet)` → `followEntity(uuid, Vec3(0,1,0))` → 铺一圈成员
    → `.move(Vec3(0,-1,0), 0)` → `.move(Vec3(0,1,0), 20)`。观察服务端日志里的
    `AnimationProgramPayload(id, members, anchor, pivot, …)`：`pivot` 已是 `feet`（少了身体中段），
    客户端每颗粒子的 `rel` 因此多出 `(0,-1,0)`，实机表现是**整壳相对预期偏移一个位移**。
  - 本轮踩到的具体后果：护盾面板组若把 `move` 排在 `fadeIn` 之前，arm 基准少一个「面板中心偏移」，
    整壳半径变成 2 倍（`rel` 全体多出 `radial`）。
- 期望 API 形态（二选一）：
  - (a) 首选：把「**绑定轴心**」（`setPivot`/`followEntity` 设定的帧）与「**当前帧**」（`move`/`movePath` 平移的那个）
    拆成两个字段 —— arm 只读前者，平移只改后者；
  - (b) 或提供显式 `arm()`（把当前成员清单与轴心先发出去），并在 `move`/`movePath` 的 KDoc 里写明
    「会改 arm 基准，请在铺完成员后立即 `arm()`」；`Draw.*` 也可以顺手在返回前 `arm()`。
  服务端/客户端：纯服务端下发语义调整，客户端 `arm()` 无需改动；线程与生命周期同现有组指令。
- 影响面：`magic/modifier/EnergyShield.kt`（第 15 轮把 `fadeIn` 排在两条 `move` 之前规避）。
  ⚠️ **第 16 轮起本侧不再被它阻塞**：护盾面板**不做任何位移动画**（球体固定、逐块淡入淡出），
  因此完全绕开了这条坑；但坑本身还在，`move` 的 KDoc 里也没有任何提示。
- 临时降级：已按 (b) 的口径自行遵守 —— **铺完成员后第一条指令不使用 `move`**；
  恢复条件 = (a) 或 (b) 落地后可去掉这条顺序约束与注释。

### [需求] 组指令需要一个「从现在起」的时间锚点（长寿组在运行期追加动画会瞬间完成）

- 状态：未决（第 17 轮登记；本轮用「原地重建组」绕开，绕法有额外一次性成本）
- 目标能力：**长寿组在运行期追加一段动画** —— 护盾球壳在建成约 100 tick 之后才「收起法杖」，
  需要下发「逐块延迟 + 淡出」的消散波；任何「出现后等玩家操作再消失」的组级特效都会遇到同一件事。
- 现有 API 为什么不够（**这不是签名问题，是时间轴语义问题，现场实测过**）：
  - `ParticleGroup.delay(ticks)` 推进的是**从建组时刻起累加的绝对游标** `cursorMs`，
    录进指令的 `AnimInstruction.startMs` 就是这个绝对时刻（建组时为 0）。
  - 客户端 `ClientAnimationProgramManager.applySlot` 的第一句是 `if (now < start) return` ——
    **`startMs` 落在过去的指令会在下一帧立刻生效**，`progress(now - start, duration)` 随即夹到 1。
    于是「建组 100 tick 之后 `delay(1).fadeOut(5)`」＝ **当场淡完**：实测 104 帧还是完整球壳、
    105 帧整壳消失（第 17 轮的实机录制帧序列，见 [resolved.md](resolved.md) 第 17 轮）。
  - 同一个游标又被 `fadeOut(removeAfter = true)` 拿去排**相对 now** 的销毁任务
    （`AnimationScheduler.schedule((cursorMs + duration * 50 + 250) / 50)`）—— 两端口径不一致：
    为了让客户端时间正确而把游标推到 now，服务端的销毁就会被推后「已经运行过的时长」那么多 tick，
    留下看不见但还活着的粒子；两者无法同时正确。
- 复现步骤：`val g = manager.createGroup(pos)` + 铺成员 → 等 100 tick →
  `g.delay(1).fadeOut(5, removeAfter = true)`。预期：1 tick 后开始、5 tick 淡出；
  实际：下一帧即完全消失（粒子要到 `cursorMs+15` tick 之后才被销毁）。
- 期望 API 形态（任一即可）：
  - (a) **增量追加时把 `startMs` 视作「相对追加时刻」**：`flush()` 的 armed 分支给增量指令统一加
    「追加时刻 − 程序起点」的偏移，`removeAfter` 的销毁延时同样以追加时刻为基准（最省事、使用方无感）；
  - (b) 暴露 `ParticleGroup.elapsedMs()`（或 `timeCursor()`），让调用方自己把游标推到 now；
  - (c) 提供显式的 `delayFromNow(ticks)` / `fadeOutFromNow(...)`，与现有 `delay` 并存。
- 影响面：`magic/modifier/EnergyShield.kt`（消散波）。黑洞的进/退场不受影响（它用的是 `ParticleHandle`
  与一次性生成，不走组时间轴）。
- 临时降级（第 19 轮更新）：**自己把游标推到 now**（`delay(该组已运行时长 + 阶梯)`，运行时长由本模组按
  `level.gameTime − 建组时刻` 记），客户端淡出时刻因此正确；`removeAfter` **不用**（它的销毁延时拿同一个绝对游标
  却按相对 now 调度，会把销毁推后一整段已运行时长 → 留下看不见但活着的粒子），销毁改由本模组的
  `util/Deferred` 在淡出结束后执行。
  第 17~18 轮曾用「原地重建成一批新组」绕开，第 19 轮弃用：重建会让组丢掉 `spin`（实机反馈「收起时不再旋转」），
  而在新组上补回旧相位补不出来 —— PD 的 `spin` 与一次性 `rotate` 是**各自按自己的基准写 `rel`、后者覆盖前者**，
  不叠加（见 `ClientAnimationProgramManager.applySlot` → `rotateToSnapshot`）。
  恢复条件 = (a)/(b)/(c) 任一落地后，可删掉「自己算游标 + 自己排销毁」这两步。

### [需求] `ParticleGroup` 的 `spin`（持续）与 `rotate`（一次性）应可叠加

- 状态：未决（第 19 轮登记；本侧已改用「不重建组、保住原有自转」绕开）
- 目标能力：**在一个已经转了一阵子的组上「接着转下去」**。触发场景：护盾球壳消散时想把旧组的淡出
  挪到新建的组上（因为 PD 缺「从现在起」的游标，见上一条），需要让新组**从旧组当前的角度**继续自转，
  而不是跳回 0（跳回去会看到球壳「啪」地整颗转一下）。
- 现有 API 为什么不够：`ClientAnimationProgramManager.applySlot` 里 `Spin` 与 `RotateOnce` 都调用
  `rotateToSnapshot(p, slot, axis, angle)`，而它是 **`st.rel = base.rotateAround(axis, angle)`** ——
  `base` 是**该指令槽自己**的 `snapRel` 快照。两条旋转指令各自从自己的快照整体重写 `rel`，
  **后应用的覆盖先应用的**，两者不相加。于是「`rotate(补相位, 0t)` + `spin(同轴同速)`」里
  `spin` 会把自己的角度（程序刚开始 ≈ 0）写回去，补的相位白补；反过来顺序则干脆不转。
- 复现：对同一个组先 `spin(axis, r)`（跑 100 tick），再 `rotate(axis, 100*r, 0)` + 再 `spin(axis, r)`：
  期望「从当前角度继续以 r 转」；实际角度由最后应用的那条决定（≈ 0 或不转）。
- 期望 API 形态（任一即可）：
  - (a) `rotateToSnapshot` 改为**在当前 `rel` 上叠加**（`st.rel = st.rel.rotateAround(axis, delta)`，
    并把 `RotateOnce` 的缓动按每帧增量下发）——最符合直觉，但会改变现有语义，需在 KDoc 里写明；
  - (b) 另给 `spinFrom(axis, radiansPerTick, initialRadians)`，一次性指定起始相位 + 角速度；
  - (c) 至少暴露「当前已累计的旋转角」（配合上一条的 `elapsedMs()`），让使用方自己烘进成员坐标。
- 影响面：`magic/modifier/EnergyShield.kt`（消散时想保留自转相位；当前改为**不重建组**，
  于是不需要补相位，但这也意味着上一条需求的绕法被迫更绕）。
- 临时降级：**不重建组** —— 直接对旧组追加淡出（自转天然延续），销毁交给本模组的 `util/Deferred`。
  恢复条件 = 本需求 (a)/(b)/(c) 之一 + 上一条游标需求落地后，可回到「重建组」的写法（若那时还有必要）。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 1.0.8 的 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`ProjectileBody`/`Trajectory`），没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| `lib/` 下的版本与 `particledrawing_version` 必须一致 | `lib/` 只留 `1.0.11-ALPHA` | 若手工塞入别的版本，容易对照错 API | 对照 API 一律以 `lib/particledrawing-1.0.11-ALPHA-sources.jar` 为准 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一（此前 `setVelocity` 漏了解除，位置每 tick 仍被锚点覆盖） |

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
