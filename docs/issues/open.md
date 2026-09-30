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
| 🟢 | 黑洞粒子带宽（第 16 轮）：球壳 **144** 颗（`ParticleBatch`，成员固定）+ 被吸入粒子（每 tick 3 颗，本模组重算速度 + 一次 `setVelocityAll`） | `magic/effect/lifecycle/BlackHole.kt` | 稳态 **0 包/tick**；进场 1 包/粒子、退场 2 包/粒子（各一次性：逐粒子缓动 `move` / 重新生成 + 向心初速），被吸入粒子 1 包/tick、峰值约 100 |
| 🟢 | 重力场粒子带宽（第 16 轮）：范围球壳 **120** 颗（`ParticleGroup`：`Draw.sphere` + 慢速自转，**0 包/tick**）+ 下坠粒子（生成时一次施力）+ 波环/井环（组，一次性） | `magic/effect/lifecycle/GravityField.kt` | 下坠粒子 **0 包/tick**（恒定力由两端逐 tick 积分）；波环每 22 tick 建一个组；回收只读 PD 权威位置。球壳 alpha/数量就是「看得出球形范围」的旋钮 |
| 🟢 | `EnergyShield` 粒子量（第 23 轮重做后**不再有常驻粒子**）：受击时**单片**玻璃板 ≈ 36 亮边 + 约 36 填充；破盾时整罩 32 片 ≈ 2300 颗（一次性、约 0.7 秒内由 PD 自行销毁） | `magic/modifier/EnergyShield.kt` | 稳态 **0 粒子 / 0 包**（这正是「太挡视野」的解法）；一次性峰值只在破盾那一瞬。玻璃是**磨砂**而非真玻璃：程序化粒子的贴图写死为纯白方块，真玻璃要等 PD 开放贴图/UV（需求已登记） |
| 🟡 | `EnergyShield` 消散动画是「发射后不管」：消散时**原地重建成一批新组**、`fadeOut(removeAfter = true)` 由 PD 调度销毁（第 17 轮改，原因见 [resolved.md](resolved.md) 第 17 轮与 [pd-api-requests.md](pd-api-requests.md)） | `magic/modifier/EnergyShield.kt` | 两个代价：① 消散瞬间要重铺一遍球壳（一次性突发，与构建同量级）；② 拆到一半又握起法杖时，未拆完的面板会被立即清掉、从头重新搭（`clearShieldVisual` + `buildCursor` 归零）。PD 需求落地后可改回「直接对旧组追加 fadeOut」并去掉重建 |
| 🟡 | **黑洞范围内的「持续轻抖」强度还没实机调过**（第 20 轮收敛后只剩这一处镜头冲击） | `BlackHole.kt` 的 `RUMBLE_MIN`/`RUMBLE_RANGE`、`client/ScreenShakeClient.kt` 的 `MAX_*_DEG` | 现在 0.05~0.13 振幅（越靠球心略强）+ 角度上限 2.6°/1.8°/1.4°：实机站在黑洞里会不会嫌晃、还是根本感觉不到，需要人上手。调参入口就是这两个常量；玩家侧可用原版「受伤视角晃动」一键关掉 |
| 🟢 | **重力场退场没有动画**：`clearVisuals` 直接把球壳/波环/井环销毁（存活 200 tick，但退场是一帧内消失） | `magic/effect/lifecycle/GravityField.kt` | 与黑洞（整壳塌缩）/护盾（逐块淡出）不一致；第 17 轮的录制把它拍了下来（`build/capture/out/gravity_field`）。是否补退场动画待定；判定脚本已按「设计上就是立即消失」记录该口径。第 20 轮已保证「退场后地面不会再冒环/溅粒子」（未落地的溅射会撤销、已溅出的粒子随批次清掉） |
| 🟡 | `Move` 逐 1/16 格步进（`Laser` speed=5 → 单帧最多 80 步） | `magic/effect/geometry/Move.kt` | 逐步进是**刻意**的：为了拿到精确的接触点与接触面（方块碰撞箱 + 面法线）。已完成一次优化：实体查询由「每步一次」降为「每 tick 一次宽相位 + 子步进窄相位」；方块仍逐步检测，若日后仍嫌重可改射线求交（会牺牲面精度） |
| 🟡 | `EnergyShield` 每 tick 遍历全场投射物 | `magic/modifier/EnergyShield.kt` | 已加两道剪枝：场上无投射物直接返回；先按「到玩家中心的距离球」粗筛（半径 = 护盾半径 + 玩家包围盒最远角 1.0 + 该投射物碰撞半径，保证不漏判）。法术数量再上量级时可考虑空间分桶/网格 |

> 自转/淡出这类**组级编排**都用 `ParticleGroup`（客户端求值、零逐 tick 包），与 `ParticleBatch` 的分工见
> [design/effects-api.md §0.2](../design/effects-api.md)。
