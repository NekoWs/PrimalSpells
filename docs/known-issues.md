# 已知问题与技术债（known-issues）

> 本文只记录**当前仍然存在**的问题：现象、影响、涉及文件、建议。
> 已按用户裁决处理掉的问题见 §6（按轮次编号），不再重复登记。
>
> 严重度：🔴 崩溃/数据损坏/明显错误玩法 ｜ 🟠 影响体验或可维护性 ｜ 🟡 清洁度问题

---

## 1. 待继续开发的功能

### 🟠 1.1 能量护盾 `EnergyShield`（你已确认：后续继续开发）

- 现状：`magic/modifier/EnergyShield.kt` 是 `needInventory` 被动，能反射敌方法术、按耐久破盾/恢复；
  `initLore()` 已修复为调用 `super`。
- 已解决：耐久/破盾暂停已通过「法术运行期状态」存储纳入法杖存档（见 §6 第 3 轮），重登不再回满。
- 仍缺口：
  1. 数值与手感（反射强度、破盾暂停、耐久曲线）尚未调平；
  2. 结构上它是被动法术却继承 `Modifier`、放在 `magic/modifier/`（你已明确暂不处理命名/结构债）。

### 🟢 1.2 能量护盾的粒子表现（attachTo 锚定 + 均匀球壳 + 放下延迟清理）

- 现状：球壳 = **PD 粒子 + `attachTo(持有者, 世界空间偏移)`**（`EnergyShield.updateShieldVisual`）——
  40 个淡蓝色粒子按 **Fibonacci 球均匀**铺在半径 **1.7** 的球面上；球心在**身体中段**
  （挂载偏移 = 球面偏移 + 身高 55%，因为 `attachTo` 的偏移是相对**脚底**的）。服务端只在挂载时下发一次，
  之后由客户端每 tick 本地解析实体位置 —— **零逐 tick 带宽且天然刚性**。
- 放下清理：`Magic.onPassiveIdle()`（由 `Wand` 闲置分支逐 tick 通知）+ `SHELL_LINGER_TICKS = 4`
  → 法杖不再被持有 **4 tick 后**清除球壳（这段时间是留给后续「消失动画」的窗口）。
- 粒子**永存**（`lifetime = -1`），`ensureAttached` 只在缺数量时补齐、**不做周期重建**
  （重建会让新粒子从生成点滑到挂载点 → 实机上表现为「逐个瞬移/从上方落下」，见 §6 第 8 轮）。
- 不自转：挂载偏移固定。要自转就改用 `trackAll` 每 tick 发一次旋转后的偏移（1 包/tick）。
- 历史更正：第 5 轮曾把「球壳塌成一圈」误报为 PD 缺陷，实为**本模组 bug**（`tickAnchored` 在 spawn 时捕获 offset
  → 自转变成错峰剪切），第 6 轮修正；第 7 轮又修了「球壳贴在脚上、像在往下掉」的偏移坐标系问题（见 §6 第 7 轮）。

### 🟡 1.3 用 PD 的「程序化粒子集」入口替换自研的 `ParticleSwarm`（本模组技术债，**不是** PD 缺陷）

- 背景：`ParticleSwarm` 是在**没查 PD 组 API** 的情况下写的。但逐条比对后确认：
  **不能直接迁移到 `ParticleGroup`** —— 它是「编排式动画」抽象（成员基本固定、组级统一变换），
  而本项目要的是「程序化粒子流」（成员逐 tick 增删、每粒子独立受力、按位置条件回收）。
  差异总表见 [design/effects-api.md](design/effects-api.md) §0.2，**待 PD 侧补的能力见 §7.3**。
- 因此本项的**前置条件**是 §7.3 第 1 条（`ParticleBatch` 之类）与第 2 条（统一锚定身份/坐标）落地；
  在那之前，`ParticleSwarm` 保留为薄封装（只算位置/速度与销毁，不含渲染/同步逻辑）。
- 迁移后能顺带拿到的能力（属于 `ParticleGroup` 已有的强项，**现在就可以用**，与迁移解耦）：
  组级编排零带宽 —— `spin(axis, radiansPerTick)`（护盾自转不必再每 tick `trackAll`，1 包/tick → 0 包）、
  `fadeOut(ticks, removeAfter)` / `destroyAfter(ticks)`（正在预留的"消失动画"窗口可以直接用它做）。
- 收益（迁移完成后）：删掉整套句柄/寿命簿记（少一类 bug 源）、锚定语义由 PD 统一定义。
- 代价：护盾视觉需要再进游戏确认一次（跟随语义由 PD 客户端解析）。

---

## 2. 性能与表现

| 严重度 | 问题 | 位置 | 说明 / 建议 |
|---|---|---|---|
| 🟢 | 黑洞粒子带宽：范围球壳 96 个（一次 spawn、静止）+ 被吸入粒子（本模组积分 + **一次 `trackAll`**） | `magic/effect/lifecycle/BlackHole.kt` | 球壳 **0 包/tick**；被吸入粒子 **1 包/tick（与粒子数无关）**，峰值约 60 个（每 tick 1 个、寿命 60），嫌密就调 `LIFETIME_TICKS` |
| 🟢 | 重力场粒子带宽：范围球壳 96 个（Fibonacci 均匀、一次 spawn、静止）+ 下落粒子（生成时一次 `setVelocity`） | `magic/effect/lifecycle/GravityField.kt` | **0 包/tick**；回收判定只读 `position()`（读不产包）。要更密改 `FALLING_PER_TICK` |
| 🟡 | `Move` 逐 1/16 格步进（`Laser` speed=5 → 单帧最多 80 步） | `magic/effect/geometry/Move.kt` | 逐步进是**刻意**的：为了拿到精确的接触点与接触面（方块碰撞箱 + 面法线）。已完成一次优化：实体查询由「每步一次」降为「每 tick 一次宽相位 + 子步进窄相位」；方块仍逐步检测，若日后仍嫌重可改射线求交（会牺牲面精度） |
| 🟡 | `EnergyShield` 每 tick 遍历全场投射物 | `magic/modifier/EnergyShield.kt` | 已加两道剪枝：场上无投射物直接返回；先按「到玩家中心的距离球」粗筛（半径 = 护盾半径 + 玩家包围盒最远角 1.0 + 该投射物碰撞半径，保证不漏判）。法术数量再上量级时可考虑空间分桶/网格 |
| 🟢 | `EnergyShield` 护盾球壳带宽：`attachTo` 锚定（40 个粒子、半径 1.7、**永存**） | `magic/modifier/EnergyShield.kt` | **0 包/tick**（只在首次挂载与补齐时各发一次，不做周期重建）；放下法杖后由 `onPassiveIdle` 在 4 tick 内显式清除。想让它自转就用 `trackAll`（1 包/tick），见 §7 |

---

## 3. 设计约定与「不是 bug 的特性」

### 3.1 修饰器之间**没有先后顺序**（最重要的一条）

所有抽到的修饰器都作用在**同一枚投射物**上，例如「火球 + 伤害增强 + 穿刺」的伤害 =
`基础伤害 + 增强 − 穿刺惩罚`，不存在「谁先应用」的语义。因此修饰器的效果只允许做**可交换**运算：

- 允许：累加 `damage += x`、累乘 `velocity *= k`、乘法归零 `x *= 0`。
- 禁止：`coerceAtLeast` 之类的钳制、把速度/伤害「重设为绝对值」（如 `normalize().mul(绝对目标速度)`）——
  这类写法会让结果依赖装配顺序，并抹掉其它修饰器的改动。
- 需要按增量推进的效果（逐渐加速/减速）：乘「本 tick 倍率 ÷ 上一 tick 倍率」。
- 同类叠加：`BaseEffect.stackScale`（√N）+ `isStackPrimary`（只有首个实例改写状态）。

实现位置与写法要求写在 `magic/core/Modifier.kt` 的 KDoc。

### 3.2 其它特性

| 项 | 说明 | 位置 |
|---|---|---|
| 穿刺 = 伤害 −8，可能为负 → 不结算伤害 | 加减法，与顺序无关；「穿刺后打不出伤害」是定价的预期结果 | `magic/modifier/Piercing.kt` |
| 爆炸**不破坏方块** | 预期行为（`explosionLevel` / `destroyBlocks` 死代码已删除） | `magic/effect/lifecycle/Explosion.kt` |
| 同一实体可每 tick 重复命中 | 穿刺玩法依赖，`Move` 刻意不去重 | `magic/effect/geometry/Move.kt` |
| 触发法术 payload 一次性 | 释放后清空；链式抽取（触发A(触发B(火球))）在施法时递归装配 | `magic/effect/lifecycle/{Trigger,DelayedTrigger}.kt` |
| `Magic.clone()` = 新建实例 | 修饰器把效果注入**实例自身**，复用实例会让效果累积 | `magic/core/Magic.kt` |
| `Magic.recharge` / `Magic.delay` 是有效字段 | `Wand.castHand` 与 `loadPayload` 会读取它们累加冷却 | `magic/core/Magic.kt`、`wand/Wand.kt` |
| 小包没有右键界面 | 编辑与法杖一致：滚轮选槽 + 物品对上物品装卸 | `item/SpellPouchItem.kt` |
| 蛇形与往复运动可叠加 | 蛇形只施加方向**旋转增量**，不再覆写方向 | `magic/modifier/Serpentine.kt` |
| 区域魔法每 tick 结算、离开即解除 | 离开范围**立刻**撤销迟缓（不等效果到期）；撤销时只清等级相同的迟缓 | `magic/effect/lifecycle/AreaFieldEffect.kt` |
| 区域场**会影响魔法投射物** | 黑洞把范围内投射物吸向中心、重力场给投射物持续的向下力；**区域弹自身除外**（否则会被自己往下压/吸住）。这是刻意的：黑洞就该「把飞进来的法术吸掉」 | `magic/effect/lifecycle/AreaFieldEffect.kt` |
| 区域/本体粒子的「位置」在本模组算，同步与渲染归 ParticleDrawing | `ParticleSwarm` 三种写法：`attachTo` 锚定（0 包/tick）/ 一次 `trackAll` 批量写位置（1 包/tick）/ 一次 `setVelocity` 交给 PD 积分（0 包/tick）；同步、可见性、寿命、渲染全由 PD 负责 | `magic/effect/visual/ParticleSwarm.kt` |
| PD 粒子管理器由 `wand.pdParticles(level)` 兜底创建 | 管理器原本只在「持有者背包物品被 tick」时建立；未来的生物施法者不走 `inventoryTick`，不兜底就会出现「有伤害没粒子」 | `util/PdParticles.kt` |

---

## 4. 待实机验证（静态编译通过，但需要进游戏确认手感）

1. 长按右键：举杖动画持续正常。
2. 对着**方块/生物**长按右键：不交互，但仍能施法。
3. 无人机行为：朝向/位置是否不再抖动（只服务端扰动 + 12°/tick 限速转向）。
4. 法术小包：滚轮选槽 + 右键放入/取出（含交换）、tooltip 高亮与法杖一致。
5. 能量护盾：反射、破盾暂停、HUD 条；重登后耐久不再回满（见 §6 第 3 轮）。
6. 轨道类修正（`self_orbit`/`sphere_orbit`/`quantum_entanglement`）在 `OVERRIDE` 阶段不再漂移。
7. 触发法术链（`trigger_fireball` + `delayed_cast`）释放表现与一次性语义。
8. **蛇形 + 往复运动**：往复反向不再被蛇形抹掉；蛇形与追踪/随机路径也能叠加。
9. **区域场**：黑洞吸力力度是否与之前相当（每 tick 施加，强度 0.12/(距离+0.5)）；
   重力场离开范围后迟缓是否**立刻**消失；场结束后（投射物死亡）是否同样立刻消失。
10. 命中一致性：`Move` 改为宽相位后，快速目标（奔跑的玩家/坐骑）是否仍能被正常命中。
11. **黑洞吸附投射物**：把法术打进黑洞范围，是否被明显吸向中心（含自己的法术）。
12. **重力场压投射物**：法术穿过重力场时是否可见下坠（与 `gravity` 修正同量级 0.03/tick）。
13. **黑洞粒子**（第 4/7 轮已改）：半径 5 的**灰黑半透明球壳**标识黑洞大小（96 个均匀粒子）；
    每 tick 1 个紫色粒子、起始绕中心切向旋转，**一直转到中心 0.1 格内才消失**（不应有中心空洞）；
    单人按 Esc 暂停时粒子应**就地定格**。
14. **重力场粒子**（第 4/7 轮已改）：半径 5 的**均匀**半透明球壳标识作用范围（96 个永存粒子）；
    下落粒子只出现在**上半球表面**、竖直下落，碰到**下半球表面**即消失（不应穿出球体继续下落）。
15. **法杖持有者记录与回收**（见 §6 第 3 轮）：放下/丢弃法杖一段时间后再拿起，法力、牌堆、冷却、
    护盾耐久是否都不丢。
16. **护盾球壳粒子**（第 7/8 轮已改）：淡蓝色球体应**均匀**（Fibonacci 球）、半径 1.7、球心在**身体中段**；
    跟随持有者平移、形状刚性、不自转；**不应出现任何「从上方滑入 / 逐个瞬移」的动画**，粒子数量也不应随时间减少；
    破盾立即消失；**放下法杖 4 tick 后**球壳消失（重新握杖后立即恢复）。
17. **本轮接线改造后的回归检查**（第 6 轮）：① 投射物**本体粒子**是否不再前后抖动（PD 已修直设位置链路）；
    ② 黑洞粒子是否仍能绕转并被吸入中心（已改 `trackAll` 批量下发）；③ 重力场下落粒子是否仍在下半球表面消失、
    且**按 Esc 暂停时不再飘**（PD 已修暂停外推）；④ 使用**旧构建**的 `1.0.8-ALPHA` jar 时应报
    `NoSuchMethodError`（提醒：本模组现在要求 2026-09-30 或更新的构建，见 §7 顶部）。

---

## 5. 历史崩溃记录（已有防护，保留备查）

- `run/client/crash-reports/crash-2026-08-10_06.39.49-server.txt`：
  `Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]` → 服务器 tick 崩溃。
- 现在由 `registry/ModDamageTypes.projectileSourceOrNull()` 统一解析：数据包缺少该伤害类型时
  只记一次错误日志并**跳过本次伤害**，`Hurt` 与 `Explosion` 都走这条路。

---

## 6. 按用户裁决完成的修复清单

### 第 1 轮（2026-09-29，结构与语义大修）

| 编号 | 裁决 | 处理方式 | 主要落点 |
|---|---|---|---|
| 1.1 | 每发法术需要新实例 | 保留「新建实例」语义，`clone()` 显式说明 + 未注册时抛可诊断异常；修正所有「继承父状态」的错误注释 | `magic/core/Magic.kt`、`ClusterBomb`、`ExplosionProjectile` |
| 1.2 | 效果顺序原意不明，稳妥解决 | 新增 `EffectPhase`（SETUP/MODIFIER/MOTION/OVERRIDE/VISUAL），按阶段稳定排序；轨道类改 `OVERRIDE` 消除漂移 | `magic/core/EffectPhase.kt`、`Magic.kt`、`SelfOrbit`、`SphereOrbit`、`QuantumEntanglement` |
| 1.3 | 每 tick 命中是特性 | 删除写后不读的 `hitEntities`/`atBlock`；`Move`/`Hurt` KDoc 写明 per-tick 命中语义 | `MagicStatus.kt`、`Move.kt`、`Hurt.kt` |
| 1.4 | 链式抽取没问题 | 保留链式装配；释放改为一次性（清空 payload + 每枚子弹独立效果克隆） | `Trigger.kt`、`DelayedTrigger.kt` |
| 1.5 | 修复 | 存档改为固定长度 + 空串占位，读档槽位不再前移 | `wand/Wand.kt` |
| 2.1 | 增加检查以防崩溃 | `projectileSourceOrNull()`：缺失伤害类型时跳过伤害并只警告一次 | `registry/ModDamageTypes.kt`、`Hurt.kt`、`Explosion.kt` |
| 2.2 | 要开服务器，必须修 | 公共入口不再引用客户端类：S→C 包经 `ClientPayloadSink` 由客户端入口安装处理 | `network/ClientPayloadSink.kt`、`PrimalSpells.kt`、`PrimalSpellsClient.kt` |
| 2.3 | 修复 | 小包容器菜单整体移除（生命周期漏洞随之消失） | `item/SpellPouchItem.kt`（原 `item/menu/*` 已删除） |
| 2.4 | 采取建议 | 登出清理 `lastCastWand` 与三处客户端缓存；`WandManager.load` 先清空 | `WandItem.kt`、`WandEventHandler.kt`、`client/hud/*`、`SpellSlotTracker.kt`、`WandManager.kt` |
| 3.1 | 删除 | 删除 `EditWandPayload`/`EditPouchPayload` 及约 110 行处理器与 4 个辅助函数 | `network/`、`PrimalSpells.kt` |
| 3.2 | 全部清理 | 删除调试实体 cube/sphere（含基类、渲染器、模型、贴图、lang）、`explosionLevel`+`destroyBlocks`、`Move.ignoreBlocks`、`Trajectory/ProjectileBody.texture`、`SyncWandStatsPayload.size`、空 `extractRenderState` 覆写、`PrimalSpells.debug/overlay` | 多处 |
| 4.1 | 穿刺无伤害是特性 | 伤害按加减法结算（后续第 2 轮进一步明确为「顺序无关」） | `Piercing.kt` |
| 4.2 | 移除调试残留 | 删除调试法术 `SpeedGetter`（注册项 + 资源 + lang） | `magic/core/MagicManager.kt` 等 |
| 4.3 | 按行为写 KDoc | `GravityField` KDoc 重写为真实参数 | `effect/lifecycle/GravityField.kt` |
| 4.4 | 按行为写 KDoc（粒子后续优化） | `BlackHole` KDoc 重写（半径 5、吸力公式、256 测试粒子） | `effect/lifecycle/BlackHole.kt` |
| 4.5 | 按行为写 KDoc | `MoreBounce` KDoc 重写（`Bounce(8)` 是累加） | `modifier/MoreBounce.kt` |
| 4.6 | 修偏移 | 取整代码位于已删除的死分支 `destroyBlocks`，随 3.2 一并消失 | `Explosion.kt` |
| 4.7 | 修重复代码 | 抽 `util/ParticleShapes`（球面/球体均匀采样） | `util/ParticleShapes.kt` |
| 4.8 | 修重复代码 | 抽 `AreaFieldEffect`（区域场）与 `ConstantAcceleration`（重力） | `effect/lifecycle/AreaFieldEffect.kt`、`effect/geometry/ConstantAcceleration.kt` |
| 4.9 | 找有没有库实现 | 检索结论：无通用库提供「同类效果首实例生效 + √N」，属领域逻辑；实现为 `BaseEffect.stackScale/isStackPrimary` | `effect/BaseEffect.kt` 及 `Tracking`/`Reciprocate`/`Serpentine`/`GradualAccelerate`/`GradualDecelerate` |
| 4.10 | 修语言文案 | `en_us` 的 `damage_boost.desc` 改为 "Increases the spell's damage." | `lang/en_us.json` |
| 4.11 | 移除死语言键 | 删除 `lore.primalspells.cast`、`gui.primalspells.close`、`tooltip.primalspells.wand_id[.ungenerated]` | `lang/*.json` |
| 4.12 | 移除孤儿贴图 | 删除无引用的 `textures/entity/drone_entity.png` | 资源 |
| 6 | 不管命名债，允许不兼容旧版 | §6 的命名/结构债条目不做处理 | — |
| 9.1 | 长按只播一次举杖动画 | `WandItem.use` 两侧都 `startUsingItem` | `item/WandItem.kt` |
| 9.2 | 右键不与方块/实体交互 | 取消结果显式设为 `PASS`，让交互回落到 `Item.use` | `event/WandEventHandler.kt` |
| 9.3 | 无人机行为奇怪、朝向乱晃 | 所有 `deltaMovement`/朝向扰动只在服务端计算（客户端只插值）；转向改 `Mth.approachDegrees` 限速 12°/tick；三个 goal 加「仅服务端」判定 | `entity/DroneEntity.kt` 等 |
| 9.4 | 移除小包右键菜单，与法杖同步 | 删除菜单/屏幕/容器与右键打开；改为滚轮选槽 + 右键装卸 + tooltip 高亮 | `item/SpellPouchItem.kt`、`network/SelectSpellSlotPayload.kt`、`client/input/SpellSlotScrollHandler.kt`、`client/tooltip/*` |
| 9.5 | 护盾没开发完 | 只做 `initLore` 调 `super` 的收尾修复，其余登记为待继续开发（§1.1） | `modifier/EnergyShield.kt` |
| 9.6 | 穿刺后加伤害强化仍打不出伤害 | 由「顺序无关」约定统一解释：加减法结果可能仍为负 → 不结算伤害（预期） | `Piercing.kt`、`Modifier.kt` |

### 第 2 轮（同日晚，针对残留问题与设计确认）

| 编号 | 裁决 | 处理方式 | 主要落点 |
|---|---|---|---|
| 1.2 | 法杖编辑台整个移除 | 删除方块类、`registry/ModBlocks.kt`（注册表已无方块）、方块物品与创造栏条目、全部资源（items/models/blockstates/textures）、语言键 `block.primalspells.wand_editing_table` | `block/`、`registry/ModBlocks.kt`、`registry/ModItems.kt`、资源 |
| 1.3 | 爆炸不破坏方块是预期 | 从「待开发缺口」移入 §3.2 设计约定；不再作为待办 | `docs/known-issues.md` |
| 2.1 | 黑洞粒子只是测试用 | 保留 256 粒子并注明「仅用于观察范围，后续优化」 | `BlackHole.kt` |
| 2.2 | 区域魔法每 tick 更新、离开后立刻去除效果 | `AreaFieldEffect` 改为**每 tick** 结算；记录受影响实体，离开范围/死亡/换维度或效果结束时**立刻**撤销迟缓（只撤销等级相同的迟缓，避免误清药水等来源）；黑洞吸力改为每 tick `0.12/(距离+0.5)`（等价旧「每 5 tick 0.6」） | `effect/lifecycle/AreaFieldEffect.kt`、`BlackHole.kt`、`GravityField.kt` |
| 2.3 | 逐步进是为了精确接触面/实体面；有更好优化就加 | 保留逐 1/16 步进与面法线求解；实体检测改为「每 tick 一次宽相位扫掠包围盒（外扩 1 格容忍目标自身移动）+ 子步进窄相位判定」，把每步一次的全维度查询降为整 tick 一次 | `effect/geometry/Move.kt` |
| 2.4 | 需要影响其它投射物，想办法优化（剪枝） | `EnergyShield` 加两道剪枝：空场直接返回；按「到玩家中心距离球」粗筛（半径含玩家包围盒最远角与该投射物碰撞半径，保证不漏判） | `modifier/EnergyShield.kt` |
| 3.1 | 修饰器没有先后顺序，所有修正都作用于同一投射物 | 新增硬性约定并写进 `Modifier` KDoc：只允许可交换运算（累加/累乘/乘零），禁止钳制与绝对值重设。据此回改：`Piercing` 恢复 `damage -= 8`；`ExplosionRemove` 改为 `*= 0`；`GradualAccelerate`/`GradualDecelerate` 改为「本 tick 倍率 ÷ 上 tick 倍率」的乘法增量 | `magic/core/Modifier.kt`、`Piercing.kt`、`ExplosionRemove.kt`、`GradualAccelerate.kt`、`GradualDecelerate.kt` |
| 4.6 | 三角函数改名蛇形，且与往复运动冲突 | `SineWave` → `Serpentine`（法术 id 仍为 `sine_wave`，资源不变；显示名改「蛇形 / Serpentine」）；改为只叠加方向的**旋转增量**而非覆写方向，往复/追踪/随机路径都能与其叠加；`RandomPath` 同步改为「随机偏转当前方向」（不再每 tick 重新随机整个方向） | `modifier/Serpentine.kt`（原 `SineWave.kt`）、`modifier/RandomPath.kt`、`lang/*.json` |
| 9.7 | ParticleDrawing 升级到 1.0.8-ALPHA 并写入依赖政策 | 依赖改为由 `gradle.properties` 的 `particledrawing_version` 驱动（`build.gradle` 不再硬编码 jar 名），`mods.toml` 的 `versionRange` 下限提到 `[1.0.8,)`；1.0.8-ALPHA 编译通过（新增 `EffectCallbacks`/`EffectVarStore`，无破坏性变更）；把「遇 API 缺陷/缺失**不得自行另写一套**，须停下并列出 API 修改需求」写成硬性政策（[conventions.md](conventions.md) §9 + [design/effects-api.md](design/effects-api.md) §0 + 本文 §7） | `build.gradle`、`gradle.properties`、`neoforge.mods.toml`、`docs/conventions.md`、`docs/design/effects-api.md`、`docs/known-issues.md` |
| 9.8 | 运行时报 `requires particledrawing 1.0.8 or above / Currently ... 1.0.8-ALPHA` | 根因：FML 用 Maven 版本语义，`1.0.8-ALPHA` 是 1.0.8 的预发布版，排序**低于** 1.0.8，故 `[1.0.8,)` 不匹配。修正：区间下限带预发布标识 → `[1.0.8-ALPHA,)`；并把它也放进 `gradle.properties`（`particledrawing_version_range`）由 `build.gradle` 的 `expand` 插值进 `mods.toml`，避免两处手改不同步。验证：用 Maven `VersionRange` 实测（`[1.0.8,)` 对 `1.0.8-ALPHA` = false；`[1.0.8-ALPHA,)` = true，且接受 1.0.8/1.0.8-beta/1.0.9-ALPHA、拒绝 1.0.7），并跑通开发服务端（日志出现 `ParticleDrawing 1.0.8-ALPHA (particledrawing)` 且 `Done (1.793s)!`，无依赖报错） | `gradle.properties`、`build.gradle`、`META-INF/neoforge.mods.toml`、`docs/build-and-run.md`、`docs/conventions.md`、`docs/design/effects-api.md` |
| 9.9 | 「ParticleDrawing API 修改需求」清单出现时机太宽 | 收窄为：**只在本次功能要求涉及 ParticleDrawing API 时**才在产出末尾附该清单并登记 §7（纯配置/版本号/其它系统的改动不附）；「不得自行另写一套」的红线保持始终有效。同步更新 [conventions.md](conventions.md) §9、[design/effects-api.md](design/effects-api.md) §0、`AGENTS.md` 第 6 条 | `docs/conventions.md`、`docs/design/effects-api.md`、`AGENTS.md`、`docs/known-issues.md` |
| 9.10 | 规范 git 提交信息格式 | 定为 `<目的>：<简短描述>`（目的取自 `新增/修复/优化/重构/文档/测试/构建/杂项`；全角冒号；一句 ≤ 20 字；只写大概干了什么，不复述具体改动与文件名）；写入 [conventions.md](conventions.md) §10 + 提交前清单 + `AGENTS.md` 第 7 条 | `docs/conventions.md`、`AGENTS.md` |

### 第 3 轮（同日晚，黑洞/重力场/粒子表现/法杖持有者）

| 编号 | 裁决 | 处理方式 | 主要落点 |
|---|---|---|---|
| 3.1 | 修复黑洞无法吸引投射物 | `AreaFieldEffect` 新增「对半径内魔法投射物每 tick 施力」的钩子（含距离平方剪枝、排除区域弹自身）；`BlackHole` 用吸力 `0.12/(距离+0.5)` 沿指向中心方向给投射物加速 | `effect/lifecycle/AreaFieldEffect.kt`、`BlackHole.kt` |
| 3.2 | 重力场对投射物附加向下的力 | `GravityField` 覆写钩子，给范围内投射物每 tick `velocity.y -= 0.03`（与 `gravity` 修正同量级） | `effect/lifecycle/GravityField.kt` |
| 3.3 | 黑洞粒子改为 PD：每 tick 32 个、紫色、初始切向力、逐渐被吸入、到中心消失 | 新增 `ParticleSwarm`（只有句柄表 + 施力 + 销毁；积分/同步/渲染全归 PD）；黑洞粒子在 5 格球壳生成、初速度取随机切向 `0.22`，每 3 tick 施加 `0.022×3` 的向心加速度，进入 `0.35` 格即销毁 | `effect/visual/ParticleSwarm.kt`、`BlackHole.kt`、`util/ParticleShapes.kt`（新增球面/球内/切向采样） |
| 3.4 | 重力场粒子改为 PD：每 tick 1 个、淡灰色、不动、中速下落 | 每 tick 在 2.5 格球体内随机位置生成 1 个淡灰粒子，初速度竖直向下 `0.18` 后不再施力（世界空间下落，不跟随投射物）；只做句柄清理 | `effect/lifecycle/GravityField.kt` |
| 3.5 | 护盾粒子改为 PD 锚点球体 | **已实现**：24 个淡蓝色 PD 粒子铺在半径 0.85 的球壳上，锚点为持有者身体中段，每 tick `track` + 绕 Y 缓慢自转；有限寿命（20t）+ 每 10t 重建，避免法杖不再被持有时留下孤儿粒子；破盾立即清除。仅剩调参（粒子数/跟踪频率） | `modifier/EnergyShield.kt`、`effect/visual/ParticleSwarm.kt`（新增 `trackAround`） |
| 3.6 | 记录「持有法杖实体」，无实体持有时回收 | **已实现**：`WandManager` 持有者表 `wandId → (实体 UUID, 最近确认 tick)`（唯一写入口 `markHeld`，入口 = `WandItem.inventoryTick` 与 `Wand.spell`）；每 100 tick 回收「在线玩家背包里没有 + 超过 200 tick 未被确认」的内存实例，回收前先写回存档；`get` 按需从存档懒加载；**存档条目永不删除**。 为了让「丢杖→捡起」不丢牌堆，`saveTag/fromTag` 增加了 `draw_pile`/`hand`/`discard_pile` | `wand/WandManager.kt`、`wand/Wand.kt`、`item/WandItem.kt` |
| 3.7 | 采取 known-issues 1.1 建议 | **已实现**：`Wand.magicStates`（法术 id → NBT）纳入存档；`Magic.saveRuntimeState/loadRuntimeState` 钩子；`EnergyShield` 持久化 `durability`/`pause_ticks`（读档 clamp，未动战斗数值）。重复卡按法术 id 共享一份状态（KDoc 写明，将来要每卡计时需换槽位键） | `wand/Wand.kt`、`magic/core/Magic.kt`、`magic/modifier/EnergyShield.kt` |
| 3.8 | PD 粒子管理器兜底 | 新增 `wand.pdParticles(level)`：管理器缺失/跨维度时现场建立并缓存，保证未来的生物施法者也有粒子表现；`Trajectory`/`ProjectileBody`/区域场统一走它 | `util/PdParticles.kt`、`effect/visual/{Trajectory,ProjectileBody}.kt`、`effect/lifecycle/AreaFieldEffect.kt` |
| 3.9 | 施法时弃牌堆会无界重复累积 | 顺带修：`Wand.discard()` 把暂存表 `discarded` 并进弃牌堆后**从不清空**，导致每次施法都把历史弃牌重复并入（并随新加的 `discard_pile` 写进存档）。现已在并入后 `discarded.clear()` | `wand/Wand.kt` |

### 第 4 轮（同日晚，实机观感修正）

| 编号 | 裁决 | 处理方式 | 主要落点 |
|---|---|---|---|
| 4.1 | 黑洞粒子太多 → 每 tick 1 个 | `PARTICLES_PER_TICK` 32 → **1** | `effect/lifecycle/BlackHole.kt` |
| 4.2 | 黑洞粒子在离中心一段距离有明显空域；预期到中心（≈0.1 格）才消失 | 两个根因都修：①销毁半径 0.35 → **0.1**；②吸力改为**随距离增强**（`0.12/(距离+0.5)`，与对生物/投射物同一公式）+ 每 tick 轻微阻尼（0.995），否则粒子会在 `r ≈ v²/a` 处稳定环绕形成空域 | `effect/lifecycle/BlackHole.kt` |
| 4.3 | 重力场粒子改为：球面均匀球壳标识范围 + 下落粒子只在上半球表面生成、碰到下半球表面消失 | 球壳：半径 **5**（= 作用范围）上均匀铺 96 个半透明淡蓝灰永存粒子；下落粒子：每 tick 3 个、出生点取**上半球表面**、竖直下落，`y ≤ center.y − √(r²−dx²−dz²)`（下半球表面）即销毁 | `effect/lifecycle/GravityField.kt` |
| 4.4 | 护盾粒子每隔一段时间瞬移一下 | 根因不是 PD 插值，而是**整圈同时销毁+重建**时按「索引 → 偏移」配对：一旦 PD 因维度粒子上限拒掉个别生成，所有粒子就会整体错位（表现为瞬移）。改为 `tickAnchored` **错峰重建**（每个粒子独立倒计时、每 tick 只换到点的那一个）+ 偏移**绑在句柄上**，寿命 20 → 40 | `effect/visual/ParticleSwarm.kt`、`modifier/EnergyShield.kt` |
| 4.5 | 单人按 Esc 时黑洞粒子飘出原定范围 | 根因：粒子之前靠引擎按速度积分，**服务端暂停时客户端仍按最后速度外推**。改为**本模组自己积分 + `track` 写回位置**（`ParticleSwarm.spawnSimulated/tickSimulated`）：暂停时不再下发新位置，粒子就地定格；顺带把「到达中心才消失」变成可控的位置判定。重力场下落粒子同步改为该模式 | `effect/visual/ParticleSwarm.kt`、`effect/lifecycle/BlackHole.kt`、`GravityField.kt` |

### 第 5 轮（同日晚，实机截图反馈）

| 编号 | 裁决 | 定位与处理 | 主要落点 |
|---|---|---|---|
| 5.1 | 拖尾的「粒子本体」出现前后瞬移 | **定位为 PD 缺陷**（不是本侧重复更新：`ProjectileBody.onTick` 每 tick 恰好一次 `track`，位置只有 `status.pos` 一处来源，且没给粒子设速度）。已按用户要求写成 PD issue（§7.1 第 1 条，含用法代码、复现与期望）；本侧无法规避 | `effect/visual/ProjectileBody.kt`（不改）、`docs/known-issues.md` §7 |
| 5.2 | 护盾球壳初始正常，环绕时塌成一排/一圈 | 先按本侧可规避的成因处理：**取消球壳自转**（偏移固定，`track` 只承担平移，球壳保持刚性）；同时把「一组 `track` 粒子的偏移每 tick 变化时不刚性」写成 PD issue（§7.1 第 2 条，附两种怀疑方向：插值不刚性 / 错峰重建继承插值状态）。PD 修好后可把自转加回 | `modifier/EnergyShield.kt`、`docs/known-issues.md` §7 |
| 5.3 | （顺带）球壳粒子重建更稀一些 | `SHELL_LIFETIME_TICKS` 40 → **60**：错峰重建频率降为约「每 2.5 tick 换 1 个粒子」，减少销毁/新生的抖动面 | `modifier/EnergyShield.kt` |

### 第 6 轮（PD 侧修完 6 条后的接线改造）

| 编号 | 起因 | 处理方式 | 主要落点 |
|---|---|---|---|
| 6.1 | **本模组 bug**：`tickAnchored` 在 spawn 时捕获 offset、之后不再调 `offsetAt`，导致「自转」实际是错峰剪切（球壳摊成扇形） | 删除 `tickAnchored`；护盾球壳改用 PD 的 `attachTo(实体, 世界空间偏移)`：偏移固定、刚性平移、**零逐 tick 包** | `effect/visual/ParticleSwarm.kt`、`modifier/EnergyShield.kt` |
| 6.2 | PD 新增批量位置 API | 黑洞粒子位置改用**一次 `ParticleManager.trackAll`** 下发（原来逐粒子 `track` ≈ 60 包/tick → **1 包/tick，与粒子数无关**） | `effect/visual/ParticleSwarm.kt`、`effect/lifecycle/BlackHole.kt` |
| 6.3 | PD 新增 `position()`（api 级只读位置） | 重力场下落粒子回到「生成时一次 `setVelocity`」的零包写法，回收判定只读 `position()`；**不再使用 `handle.data()`**（core 类型依赖消失） | `effect/lifecycle/GravityField.kt`、`effect/visual/ParticleSwarm.kt` |
| 6.4 | PD 修复「服务端暂停时外推」 | 位置驱动不再是为了规避暂停漂移（黑洞保留位置驱动是为了**轨迹确定 + 中心判定可控**，理由写在 `BlackHole` KDoc） | `effect/lifecycle/BlackHole.kt` |
| 6.5 | 同一版本号换 jar | 记录依赖卫生问题：`1.0.8-ALPHA` 的旧构建没有新 API，`versionRange` 无法区分；已在 §7 顶部写明「必须 ≥ 2026-09-30 构建」并建议 PD 提升版本号 | `docs/known-issues.md` §7、`docs/build-and-run.md` §2 |

### 第 7 轮（实机截图反馈：护盾球壳位置/均匀度/清理时机 + 黑洞范围标识）

| 编号 | 裁决 | 处理方式 | 主要落点 |
|---|---|---|---|
| 7.1 | 护盾粒子「玩家在下方、像从中间往下落」 | **本模组坐标错**：`attachTo` 的偏移是相对 `entity.position()`（**脚底**）的，而我只把「身体中段」加进了初始 spawn 位置、没加进挂载偏移 → 球壳整个贴在脚上。修正：挂载偏移统一为「球面偏移 + 身高 55%」 | `modifier/EnergyShield.kt` |
| 7.2 | 护盾粒子要**均匀球体分布**、半径**增加一倍** | 球面取点从随机采样改为 **Fibonacci 球**（点间距近似相等，见 `util/ParticleShapes.fibonacciSphere`）；半径 0.85 → **1.7**；粒子数 24 → 40（`attachTo` 零逐 tick 带宽，加数量不增加每 tick 开销） | `util/ParticleShapes.kt`、`modifier/EnergyShield.kt` |
| 7.3 | 护盾粒子**放下法杖后 4 tick 后消失**（为后续消失动画预留） | 新增被动生命周期钩子 `Magic.onPassiveIdle()`（法杖这一 tick 未被任何实体驱动时逐 tick 触发），由 `Wand.tick(level = …)` 的闲置分支驱动；`EnergyShield` 用它做 `SHELL_LINGER_TICKS = 4` 的延迟清理，重新握杖时归零。这样清理时机不再依赖粒子寿命自然过期 | `magic/core/Magic.kt`、`wand/Wand.kt`、`wand/WandManager.kt`、`modifier/EnergyShield.kt` |
| 7.4 | 黑洞也要画一个**灰黑色球体**表示大小 | 黑洞新增**范围球壳**：半径 5（= 实效半径）上用 Fibonacci 球均匀铺 96 个灰黑半透明粒子并永存（场静止 → 0 包/tick），效果结束时统一销毁 | `effect/lifecycle/BlackHole.kt` |
| 7.5 | （顺带）重力场范围球壳也改成均匀 | 重力场球壳由随机采样改为 **Fibonacci 球**均匀取点（同一工具） | `effect/lifecycle/GravityField.kt` |

### 第 8 轮（护盾球壳「从上方落下 / 逐个瞬移 / 逐渐消失」）

| 编号 | 现象 | 根因与处理 | 主要落点 |
|---|---|---|---|
| 8.1 | 切出护盾时，整体粒子**从上方落下**到目标位置 | **本模组把身体中段偏移加了两次**：`anchor = holder.position() + 身体偏移`，同时 `offset` 里又加了身体偏移 → 生成点比挂载目标高 ≈1 格（生成位置 = anchor + offset），PD 客户端从生成点插值到挂载点就是「从上方落下」。修正：`entityPos` 只传 `holder.position()`（脚底，与 `attachTo` 同坐标系），身体偏移**只出现在 offset 里** | `modifier/EnergyShield.kt`、`effect/visual/ParticleSwarm.kt` |
| 8.2 | **每个粒子逐个**「瞬移到上方再落回原位」 | 根因是「错峰重建」：为让粒子在法杖不再被持有时自行过期，我每约 1.5 tick 销毁并新建 1 个粒子 —— 而**新建的粒子必然从生成点滑到挂载点**，于是这个滑入动画被不断重演。现在 `Magic.onPassiveIdle` 已能可靠触发清理，不再需要靠寿命过期：改为 `ensureAttached` **一次性锚定 + `lifetime = -1` 永存**，只在数量不足时补齐、不做周期重建 | `effect/visual/ParticleSwarm.kt`、`modifier/EnergyShield.kt` |
| 8.3 | 过一段时间粒子开始消失、散落到脚下 | 8.1/8.2 的产物（生成点错位 + 反复重建 + 60 tick 寿命陆续到期；寿命一到 PD 就地销毁，视觉上就是「消失/落在错误位置」）。改永存 + 显式清理后不再有寿命到期这一路径；顶部每 tick 的 `ensureAttached` 会把被 PD 回收的粒子补齐。**若仍出现异常**，按 §7 流程提 PD issue（附复现步骤与场景） | 同上 |

---

## 7. ParticleDrawing API 需求（待对方实现）

> **政策**：见 [conventions.md](conventions.md) §9 与 [design/effects-api.md](design/effects-api.md) §0。
> 遇到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **出现时机**：需求清单与本章节的登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：`gradle.properties` 的 `particledrawing_version=1.0.8-ALPHA`
> （`lib/particledrawing-1.0.8-ALPHA.jar`，另附 `-sources.jar` 便于核对签名）。

**当前状态：原 6 条全部闭环**（PD 侧 2 个提交 + 本模组 1 处自己的 bug）；**新增 3 条需求未决**（§7.3）。

| # | 条目 | 结论 | 落点 |
|---|---|---|---|
| 1 | `track` 跟随高速目标前后瞬移 | **PD 已修**（`e73d88b`「直设位置端点重扫」） | PD core/client |
| 2 | 一组 `track` 粒子的偏移每 tick 变化时被拉成一排/一圈 | **❌ 撤回：这是本模组的 bug**，PD 侧复现不出是对的（插值误差上限只有一个 tick 的旋转量 ≈ 0.025 格）。真因：`ParticleSwarm.tickAnchored` 在 spawn 时把 offset 捕获进 `Anchored.offset`，之后**再也不调 `offsetAt`** → 错峰重建把「不同时刻的旋转角」陆续注进同一个球壳，球面被逐渐剪切、摊平成扇形。已改为 `attachTo` 方案（偏移固定、刚性） | 本模组 |
| 3 | 服务端暂停时速度驱动粒子被客户端继续外推 | **PD 已修**（`e73d88b`「暂停时外推」） | PD core/client |
| 4 | `ParticleHandle` 缺 api 级只读位置 | **PD 已加**（`2244515`）→ `position(): Vec3?` | PD api |
| 5 | 缺力/加速度与批量位置下发 | **PD 已加**（`2244515`）→ `applyForce(accel, ticks)`、`ParticleManager.trackAll(ids, positions)` | PD api |
| 6 | 原始粒子缺实体锚点 | **PD 已加**（`2244515`）→ `attachTo(entityId, offset)` / `attachToLocal(...)` | PD api |

**本模组据此改成三种零/低带宽写法**（详见 `magic/effect/visual/ParticleSwarm.kt`）：

| 用途 | PD 用法 | 每 tick 带宽 |
|---|---|---|
| 护盾球壳 | `attachTo(实体, 世界空间偏移)`（客户端本地解析实体位置） | **0 包** |
| 黑洞粒子 | 本模组积分 + **一次** `trackAll` | **1 包**（与粒子数无关） |
| 重力场下落粒子 | 生成时一次 `setVelocity` + 只读 `position()` 做回收判定 | **0 包** |
| 重力场范围球壳 | 一次 `spawn`（静止，永存） | **0 包** |

`applyForce` 暂未使用：黑洞吸力**随距离变化**，逐几 tick 重发力的包量（约 20 包/tick）高于 `trackAll`（1 包/tick），
且位置驱动能保证「抵达中心 0.1 格才消失」完全可控。理由写在 `ParticleSwarm` 的类注释里。

> ⚠️ **依赖版本卫生（建议 PD 侧处理）**：新 jar 沿用了同一个版本号字符串 `1.0.8-ALPHA`
> （`lib/particledrawing-1.0.8-ALPHA.jar` 于 2026-09-30 00:10 被替换），而本模组的
> `particledrawing_version_range=[1.0.8-ALPHA,)` **无法区分「没有新 API 的旧构建」**——
> 用旧构建启动会在运行期报 `NoSuchMethodError`（`attachTo` / `trackAll` / `position`）。
> 建议 PD 把 `mod_version` 提到 `1.0.9-ALPHA`（或加构建后缀），本模组才能真正声明「最低可用构建」。
> 本模组口头约定：**必须使用 2026-09-30 或更新的 1.0.8-ALPHA 构建**（见 [build-and-run.md](build-and-run.md) §2）。

### 7.1 历史条目（备查）

#### [缺陷 ✓已修] `track` 跟随高速目标时「前后瞬移」（往返抖动）

- 现象：法术飞行时**粒子本体**（`ProjectileBody`）沿运动方向前后瞬移；速度越高越明显
  （`laser` speed=5 格/tick 最明显）
- 我们的用法（同一 tick 恰好一次 `track`，位置只有一处来源，可排除「重复更新 / 速度叠加」）：

  ```kotlin
  // magic/effect/visual/ProjectileBody.kt
  override fun onTick() {                    // EffectPhase.VISUAL：位置积分完成后执行
      ensureSpawned()                        // 只 spawn 一次（lifetime = -1）
      val h = handle ?: return
      val pos = status.pos
      h.track(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())   // 每 tick 一次
  }
  ```

- 结论：PD `e73d88b` 修掉了直设位置链路的端点重扫偏差。

#### [缺陷 ❌撤回] 一组 `track` 粒子的偏移每 tick 变化时被拉成一排/一圈

- 现象（当时）：护盾球壳初始是标准球，随自转几秒后塌成一条/一圈。
- **正确结论（本模组 bug，已修）**：`tickAnchored` 的 `Anchored.offset` 是 **spawn 那一刻**捕获的，
  每 tick 只跟踪它、不再调 `offsetAt`。于是「自转」并没有真的转：错峰重建时，每个被重建的粒子
  取到的是**重建那一刻**的旋转角，而其余粒子还停在各自旧角度上 —— 球面被错峰剪切成扇形，
  看起来就是「一圈/一排」。PD 侧的插值误差上限只有一个 tick 的旋转量（0.03 rad × 0.85 ≈ **0.025 格**），
  远不足以解释该现象，所以 PD 复现不出是正确的。
- 修法：球壳改用 `attachTo`（偏移固定，天然刚性，零逐 tick 包）；
  `tickAnchored` 整个删掉（它的「按索引取偏移」写法本身就是这次 bug 的温床）。
  若以后要自转：每 tick 用一次 `ParticleManager.trackAll(ids, 旋转后的偏移)`（1 包/tick），不要退回逐粒子 `track`。

#### [缺陷 ✓已修] 服务端暂停时，速度驱动的粒子仍被客户端继续外推

- 现象：单人按 Esc（集成服务端暂停）后，`setVelocity` 驱动的粒子沿最后速度飘出原定范围
- 当时的临时降级：黑洞/下落粒子改「本模组积分 + `track`」；**现已不需要**（PD `e73d88b` 修掉外推，
  重力场下落粒子已回到「生成时一次 `setVelocity`」的零包写法）

### 7.2 已实现的 API 需求（本模组已在用）

#### [需求 ✓已加] `ParticleHandle.position(): Vec3?`

- 用途：黑洞/下落粒子按**权威位置**做「抵达中心」「碰到下半球表面」的回收判定
- 现状：已改用 `position()`；**不再依赖** `handle.data()`（那是 `core.server.ParticleData`，非 api 类型）
- 落点：`magic/effect/visual/ParticleSwarm.kt`

#### [需求 ✓已加] `applyForce(accel, ticks)` 与 `ParticleManager.trackAll(ids, positions)`

- 现状：`trackAll` 已用于黑洞（1 包/tick，与粒子数无关）；`applyForce` 备而不用（理由见上文表格后的说明）
- 落点：`magic/effect/visual/ParticleSwarm.kt`、`magic/effect/lifecycle/BlackHole.kt`

#### [需求 ✓已加] 实体锚点 `attachTo` / `attachToLocal`

- 现状：护盾球壳用 `attachTo`（世界空间偏移，刚性平移、零逐 tick 包）；
  `attachToLocal`（随实体朝向旋转）暂未使用 —— 需要「贴在身前/身侧」的粒子时可用
- 落点：`magic/modifier/EnergyShield.kt`、`magic/effect/visual/ParticleSwarm.kt`

### 7.3 新需求（ParticleGroup 与「程序化粒子流」的差异，2026-09-30）

> 起因：本模组一度打算把自研的 `ParticleSwarm` 直接迁移到 PD 的 `ParticleGroup`。
> 逐条比对后确认**不能直接迁移**：两者是不同取向的抽象 ——
> `ParticleGroup` = **编排式动画**（成员基本固定、组级统一变换、指令流一次下发、客户端本地求值）；
> 本项目要的是**程序化粒子流**（成员逐 tick 增删、每粒子独立速度/轨迹/寿命、按位置条件回收）。
> 差异总表见 [design/effects-api.md](design/effects-api.md) §0.2。下列需求供 PD 侧决定补哪些。

#### [需求] 面向「程序化粒子集」的泛用入口（Batch / 动态成员）

- 状态：未决
- 目标能力（全部是真实用例）：
  1. **黑洞**：每 tick 在球壳上新增 1 个粒子（各自随机切向初速、各自寿命 60），**每 tick 对每个粒子施加随距离变化的向心力**，粒子进入中心 0.1 格内立即销毁；
  2. **重力场**：每 tick 在上半球表面新增 3 个匀速下落粒子，**碰到下半球表面（按位置判定的几何面）立即销毁**；
  3. **护盾**：40 个粒子钉在玩家身上、永存、破盾/放下时整批销毁。
- 现有 API 为什么不够：
  - `ParticleGroup` 的成员管理是**一次性录制**模型：`add()` 会把 `armed` 置回 false → **重发全量受控清单**，
    不适合「每 tick 增删 1~3 个成员」的持续流；
  - 组级变换（`spin/rotate/scale/pulse/move/movePath`）是**整组统一变换**，表达不了「每个粒子沿各自轨迹被吸向中心」；
  - **没有**「按每粒子位置/速度的条件移除」；
  - `ParticleManager.trackAll(ids, positions)` 可用，但要求**使用方自己维护 id ↔ 每粒子状态（pos/vel）表** ——
    这正是本模组 `ParticleSwarm` 在做的事，也是第 5–8 轮四个 bug 的来源
    （捕获偏移、索引配对、重复偏移、重建滑入）。
- 期望 API 形态（任一或组合，命名仅供参考）：
  1. **`ParticleBatch`**：`add(handle)` 增量登记、`track(handle, pos)` 与批量 `trackAll(map)`（一次包）、
     `velocity(handle, v)`、`removeIf { handle, pos, vel -> … }`、`size()`、`clear()`；
     成员可随时增删且**不重发全量**；组内粒子 `lifetime` 到点自动出列（对使用方透明）；
  2. 或给 `trackAll` 加**按句柄列表**的重载：`trackAll(handles: List<ParticleHandle>, positions: List<Vec3>)`
     （省掉使用方维护 UUID 列表）；
  3. 或给 `ParticleGroup` 补 `removeIf(predicate)` 与「增量成员变更（不重发全量）」。
- 影响面：`magic/effect/visual/ParticleSwarm.kt`、`effect/lifecycle/BlackHole.kt`、`GravityField.kt`
- 临时降级：本模组自研 `ParticleSwarm`（薄封装：只算位置/速度与销毁，不含渲染/同步逻辑）

#### [需求] 统一「锚定 / 跟随」的身份体系与坐标系

- 状态：未决
- 目标能力：**一个入口**表达「这 N 个粒子钉在实体 E 上，偏移相对 E（可选随朝向）」，并能换实体/解除 ——
  即护盾球壳需要的「刚性、零逐 tick 包、跟随实体」。
- 现有 API 为什么不够（**三套身份 + 两套偏移语义**，使用方得自己挑）：

  | 入口 | 身份 | 偏移语义 | 覆盖面 |
  |---|---|---|---|
  | `ParticleHandle.attachTo(entityId: Int, offset)` | 实体**网络 id** | 世界空间（相对 `Entity.position()` 脚底） | 单粒子 |
  | `ParticleHandle.attachToLocal(entityId, offset)` | 实体网络 id | 实体局部（随朝向） | 单粒子 |
  | `ParticleGroup.followEntity(uuid: UUID, offset)` | 实体 **UUID** | 组轴心相对实体的偏移（**仅位置、不含朝向**） | 整组（粒子靠 `offsetFromPivot` 表达） |
  | `ParticleGroup.defineEntity(handle, uuid)` | UUID | 供 `.pdrawc` 表达式引用 | 动画 |

  另外：`followEntity` 只跟随位置，「随朝向」在组上没有对应入口；两种身份（Int / UUID）需要使用方各存一份。
- 期望 API 形态：
  1. **统一身份**：`attachTo` / `followEntity` 各提供 `uuid` 与 `entityId` 两个重载（内部同一实现），
     并在 KDoc 写明生命周期语义（实体不在场 / 换维度 / 死亡时的行为）；
  2. **统一偏移语义**：明确 `offsetFromPivot`（相对轴心）与 `attachTo`（相对实体脚底）的组合关系，
     给出「相对实体 + 相对轴心」的推荐写法（现在是隐式两段相加，正是我们踩坑的地方）；
  3. 让组也能随朝向：`followEntity(uuid, offset, local = true)`（等价 `attachToLocal`）；
  4. 可选：`ParticleGroup.ensureSize(n) { spawner }` —— 把「补齐到 N + 上限拒绝重试」做成通用能力。
- 影响面：`modifier/EnergyShield.kt`（球壳）、未来的跟随型特效；`ParticleSwarm.ensureAttached` 可随之删除
- 临时降级：本模组用「逐粒子 `attachTo` + 每 tick 补量」实现（0 包/tick，但簿记是自己写的）

#### [需求] 文档：区分「编排式动画」与「程序化粒子流」两条路径

- 状态：未决
- 背景：本项目接线时只看了 `ParticleManager` / `ParticleHandle`，**没意识到 `ParticleGroup` 的存在与取向**，
  于是自研了一整套集合簿记（并接连踩坑）。若在 `ParticleGroup` 的 KDoc 顶部（或 `doc/`）用一两句话说明
  「成员固定的编排动画用 Group；逐 tick 增删 / 每粒子独立受力的粒子流请用 X」，
  下一个人就不会重走这条路。
- 期望：`ParticleGroup` 类注释补一句适用边界 + 指向程序化路径的入口（7.3 第 1 条落地后即 `ParticleBatch`）。
已知的**能力边界**（不是缺陷，但接线前必须知道）：

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按 §7 提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 1.0.8 的 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`ProjectileBody`/`Trajectory`），没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| `lib/` 下仍保留旧版 `particledrawing-1.0.6.jar` | 已不被 `build.gradle` 引用（引用由 `particledrawing_version` 决定） | 容易误用旧 jar 对照 API | 需要时删除；对照 API 请以 `-sources.jar` 为准 |

### 需求登记模板（新增需求时照抄）

```
### [需求] <一句话标题>
- 状态：未决 / 已由 ParticleDrawing <版本> 解决
- 目标能力：<我们在 PrimalSpells 想实现的效果 + 触发场景>
- 现有 API 为什么不够：<类 + 方法/属性 + 编译错误原文或运行现象 + 复现步骤>
- 期望 API 形态：<签名草案（类/函数/参数/返回）；服务端还是客户端；线程与生命周期约定>
- 影响面：<本模组受影响文件>
- 临时降级：<降级范围 + 恢复条件；没有则写「无（该功能暂不可用）」>
```
