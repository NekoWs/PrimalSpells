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
| 🟢 | 黑洞粒子带宽：范围球壳 96 个（`ParticleBatch.ensureSize` 首 tick 铺满、静止）+ 被吸入粒子（本模组每 tick 重算速度 + **一次 `setVelocityAll`**，位置由 PD 积分） | `magic/effect/lifecycle/BlackHole.kt` | 球壳 **0 包/tick**；被吸入粒子 **1 包/tick（与粒子数无关）**，峰值约 **34** 个（每 tick 1 个 × 平均 34 tick 抵达中心销毁，阻尼 0.9 下实测）。**本模组不再持有任何跨 tick 粒子状态** |
| 🟢 | 重力场粒子带宽：范围球壳 96 个（`ensureSize` 首 tick 铺满、静止）+ 下落粒子（生成时一次 `setVelocity`） | `magic/effect/lifecycle/GravityField.kt` | **0 包/tick**；回收判定用 `ParticleBatch.removeIf` 只读 PD 的权威位置（读不产包）。要更密改 `FALLING_PER_TICK` |
| 🟢 | `EnergyShield` 护盾球壳带宽：`ParticleGroup`（40 个粒子、半径 1.7、**永存**）+ 跟随 + 自转 | `magic/modifier/EnergyShield.kt` | 跟随与自转都是**客户端本地求值** → **0 包/tick**（只在建立时下发一次；跨维度/换实体才重建）；破盾立即 `group.remove()`，放下法杖后由 `onPassiveIdle` 在 4 tick 内清除。转速/轴是常数，调它不花带宽 |
| 🟡 | `Move` 逐 1/16 格步进（`Laser` speed=5 → 单帧最多 80 步） | `magic/effect/geometry/Move.kt` | 逐步进是**刻意**的：为了拿到精确的接触点与接触面（方块碰撞箱 + 面法线）。已完成一次优化：实体查询由「每步一次」降为「每 tick 一次宽相位 + 子步进窄相位」；方块仍逐步检测，若日后仍嫌重可改射线求交（会牺牲面精度） |
| 🟡 | `EnergyShield` 每 tick 遍历全场投射物 | `magic/modifier/EnergyShield.kt` | 已加两道剪枝：场上无投射物直接返回；先按「到玩家中心的距离球」粗筛（半径 = 护盾半径 + 玩家包围盒最远角 1.0 + 该投射物碰撞半径，保证不漏判）。法术数量再上量级时可考虑空间分桶/网格 |

> 自转/淡出这类**组级编排**都用 `ParticleGroup`（客户端求值、零逐 tick 包），与 `ParticleBatch` 的分工见
> [design/effects-api.md §0.2](../design/effects-api.md)。
