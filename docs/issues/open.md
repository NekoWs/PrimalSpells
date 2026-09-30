# 未解决的问题（open）

> 只放**现在还没解决**的东西。修好就删掉，结论写进 [resolved.md](resolved.md)（规则见 [README.md](README.md)）。
> 严重度：🔴 崩溃/数据损坏/明显错误玩法 ｜ 🟠 影响体验或可维护性 ｜ 🟡 清洁度问题。

---

## 1. 待继续开发的功能

### 🟠 1.1 能量护盾 `EnergyShield`（你已确认：后续继续开发）

- 现状：`magic/modifier/EnergyShield.kt` 是 `needInventory` 被动，能反射敌方法术、按耐久破盾/恢复；
  `initLore()` 已调用 `super`；粒子表现见 `resolved.md` 第 7/8 轮与 [architecture.md](../architecture.md) §9。
- 已解决：耐久/破盾暂停已作为「法术运行期状态」纳入法杖存档（`resolved.md` 第 3 轮），重登不再回满。
- 仍缺口：
  1. **数值与手感**未调平：反射强度、破盾暂停、耐久曲线、每次弹开的耐久消耗；
     多张护盾卡的**回复速率**目前不随卡数放大（容量叠加、回复仍按单卡 10/s）——要不要跟着放大由你定；
     容量变大**不会白送耐久**（避免反复拔插刷满，见 `EnergyShield` 类注释）；
  2. 结构债：它是被动法术却继承 `Modifier`、放在 `magic/modifier/`（**你已明确暂不处理**）。

### 🟡 1.2 4 个「会生成新投射物」的修饰器靠继承 `Modifier` 借用结算流程

- 现状：`ClusterBomb`、`ExplosionProjectile`、`BounceDuplicate`、`QuantumEntanglement` 语义上更接近**法术**
  （它们会生成新投射物），却通过继承 `Modifier` 借用了手牌结算流程。
- 影响：`Modifier` 的语义被撑大（「改参数」与「生成法术」混在一个基类里），新增同类内容时容易选错基类。
- 建议：暂不动（**你已明确暂不处理命名/结构债**）；真要拆就在 `magic/` 下另立一个基类，并同步 [conventions.md](../conventions.md) 的落位表。

---

## 2. 性能与表现

| 严重度 | 问题 | 位置 | 说明 / 建议 |
|---|---|---|---|
| 🟢 | 黑洞粒子带宽：范围球壳 **144** 个（`ParticleGroup`：`fadeIn` + `spin`）+ 被吸入粒子（每 tick 3 颗；本模组重算速度 + **一次 `setVelocityAll`**，位置由 PD 积分） | `magic/effect/lifecycle/BlackHole.kt` | 球壳 **0 包/tick**；被吸入粒子 **1 包/tick（与粒子数无关）**，峰值约 **100**；进/退场各一批一次性向心粒子（生成时一次初速，自带寿命，**0 包/tick**、无需簿记） |
| 🟢 | 重力场粒子带宽：范围球壳 96 个（`ensureSize`）+ 下坠粒子（生成时一次**施力** `applyForce(a, -1)`）+ 波环/井环（`ParticleGroup`） | `magic/effect/lifecycle/GravityField.kt` | 下坠粒子 **0 包/tick**（恒定力由两端逐 tick 积分）；波环每 22 tick 建一个组（一次性，自带淡出与销毁）；回收只读 PD 权威位置（读不产包）。要更密改 `FALLING_PER_TICK`，要更砸改 `FALL_ACCELERATION` |
| 🟡 | `EnergyShield` 球壳带宽（第 15 轮）：**32 个面板组**（12 五边形 + 20 六边形；每条棱 2 点 → 360 颗**永存**粒子、半径 1.7）+ 跟随 + 自转 + 展开/收起 | `magic/modifier/EnergyShield.kt` | 稳态 **0 包/tick**（跟随/自转/淡入淡出/平移都在客户端本地求值）；**代价在建立时刻**：32 份程序包 + 360 个生成包（一次性，可按 `SHELL_POINTS_PER_EDGE` 减半），收起为 32 份追加包。转速/轴/展开时长是常数，调它不花带宽 |
| 🟡 | `EnergyShield` 收起动画是「发射后不管」：`fadeOut(removeAfter = true)` 由 PD 调度销毁 | `magic/modifier/EnergyShield.kt` | 收起途中重新握杖会出现**一瞬间两层球壳**（新壳展开 + 旧壳淡出，约 0.6 秒）。要严格无缝就得等动画结束再重建（为一段过渡多存一份状态，暂不做） |
| 🟡 | `Move` 逐 1/16 格步进（`Laser` speed=5 → 单帧最多 80 步） | `magic/effect/geometry/Move.kt` | 逐步进是**刻意**的：为了拿到精确的接触点与接触面（方块碰撞箱 + 面法线）。已完成一次优化：实体查询由「每步一次」降为「每 tick 一次宽相位 + 子步进窄相位」；方块仍逐步检测，若日后仍嫌重可改射线求交（会牺牲面精度） |
| 🟡 | `EnergyShield` 每 tick 遍历全场投射物 | `magic/modifier/EnergyShield.kt` | 已加两道剪枝：场上无投射物直接返回；先按「到玩家中心的距离球」粗筛（半径 = 护盾半径 + 玩家包围盒最远角 1.0 + 该投射物碰撞半径，保证不漏判）。法术数量再上量级时可考虑空间分桶/网格 |

> 自转/淡出这类**组级编排**都用 `ParticleGroup`（客户端求值、零逐 tick 包），与 `ParticleBatch` 的分工见
> [design/effects-api.md §0.2](../design/effects-api.md)。
