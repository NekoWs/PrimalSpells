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

### 🟡 1.2 能量护盾的粒子表现（已改用 ParticleDrawing，仅剩调参）

- 现状：球壳已改为 **PD 粒子**（`EnergyShield.updateShieldVisual`）——24 个淡蓝色粒子铺在半径 0.85 的球壳上，
  以持有者身体中段为锚点、每 tick `track` 位置并绕 Y 轴缓慢自转；破盾立即清除。
- 实现要点：粒子寿命 20 tick、每 10 tick 整圈重建 —— 这样「法杖不再被持有」时（`onInventoryTick` 不再被调用、
  我们**没有**被动停止回调）粒子会自行过期，不会在世界里留下孤儿粒子。
- 仍可优化：粒子数与跟踪频率（每 tick 24 次位置广播）；若 PD 提供实体锚点 API 可省掉这些广播（见 §7）。

---

## 2. 性能与表现

| 严重度 | 问题 | 位置 | 说明 / 建议 |
|---|---|---|---|
| 🟡 | 黑洞粒子：每 tick 1 个、寿命最长 60 tick（峰值约 60 个），**位置由本模组积分 + `track` 写回** | `magic/effect/lifecycle/BlackHole.kt` | 改成位置驱动后每 tick 每粒子一次位置广播（峰值约 60/tick），可控；若嫌密就调 `LIFETIME_TICKS` |
| 🟡 | 重力场粒子：范围球壳 96 个（永存，仅生成时广播）+ 每 tick 3 个下落粒子 | `magic/effect/lifecycle/GravityField.kt` | 下落粒子寿命 120 tick 兜底，正常在碰到下半球表面时销毁；要更密改 `FALLING_PER_TICK` |
| 🟡 | `Move` 逐 1/16 格步进（`Laser` speed=5 → 单帧最多 80 步） | `magic/effect/geometry/Move.kt` | 逐步进是**刻意**的：为了拿到精确的接触点与接触面（方块碰撞箱 + 面法线）。已完成一次优化：实体查询由「每步一次」降为「每 tick 一次宽相位 + 子步进窄相位」；方块仍逐步检测，若日后仍嫌重可改射线求交（会牺牲面精度） |
| 🟡 | `EnergyShield` 每 tick 遍历全场投射物 | `magic/modifier/EnergyShield.kt` | 已加两道剪枝：场上无投射物直接返回；先按「到玩家中心的距离球」粗筛（半径 = 护盾半径 + 玩家包围盒最远角 1.0 + 该投射物碰撞半径，保证不漏判）。法术数量再上量级时可考虑空间分桶/网格 |
| 🟡 | `EnergyShield` 护盾球壳：24 个 PD 粒子，每 tick 各广播一次位置包 | `magic/modifier/EnergyShield.kt` | 寿命 40 tick，由 `tickAnchored` **错峰**重建（每 tick 只重建到点的那一个），不再整圈销毁+重建。要省广播就得等 PD 的实体锚点 API（§7） |

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
| 区域/本体粒子的「力」在本模组算，积分与渲染归 ParticleDrawing | `ParticleSwarm` 只负责生成句柄、写速度、到点销毁；位置积分（引擎 `pos += velocity`）、同步、可见性、寿命、渲染全由 PD 负责 | `magic/effect/visual/ParticleSwarm.kt` |
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
13. **黑洞粒子**（第 4 轮已改）：每 tick 1 个、紫色、起始绕中心切向旋转，**一直转到中心 0.1 格内才消失**
    （不应再有中心空洞）；单人按 Esc 暂停时粒子应**就地定格**，不再飘出范围。
14. **重力场粒子**（第 4 轮已改）：半径 5 的**半透明球壳**标识作用范围（96 个永存粒子）；
    下落粒子只出现在**上半球表面**、竖直下落，碰到**下半球表面**即消失（不应穿出球体继续下落）。
15. **法杖持有者记录与回收**（见 §6 第 3 轮）：放下/丢弃法杖一段时间后再拿起，法力、牌堆、冷却、
    护盾耐久是否都不丢。
16. **护盾球壳粒子**（第 4 轮已改）：淡蓝色球体跟随持有者移动并缓慢自转；**不应再周期性瞬移**
    （错峰重建后每次只替换一个粒子）；破盾立即消失；放下法杖后约 2 秒内粒子自行过期。

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

**未决需求：4 条**（前三条由第 3 轮接线暴露，第 4 条由第 4 轮实机观察暴露）。

### [需求] 服务端暂停时，速度驱动的粒子不应被客户端继续外推

- 状态：未决（**已在用法侧规避**：黑洞/重力场下落粒子改为「本模组积分位置 + `track`」，暂停时定格）
- 现象：单人游戏按 Esc（集成服务端暂停）后，`setVelocity` 驱动的粒子会沿最后速度**飘出原定范围**
- 现有 API 为什么不够：引擎对「有速度的粒子」每 tick 自行推进位置，而客户端在服务端没有新状态时仍按最后速度
  继续外推（PD 有 `ClockSyncPayload` 时钟同步，但没有「暂停时不推进」的语义）
- 期望行为/API 形态（任一）：① 服务端暂停（时钟冻结）时客户端停止推进速度驱动粒子；
  ② 恢复时下发一次权威位置做重同步；③ 在 API 文档里明确「速度驱动粒子在暂停期间会被外推」，便于使用方选择 `track`
- 影响面：任何使用 `setVelocity` 的效果（本项目现在只用位置驱动规避）
- 临时降级：改用 `ParticleSwarm.spawnSimulated/tickSimulated`（本模组积分 + `track`）

### [需求] `ParticleHandle` 缺少 api 级的只读位置查询

- 状态：未决
- 目标能力：黑洞粒子要「每 tick 朝中心施力」，因此必须知道 PD 引擎里该粒子的**权威当前位置**
- 现有 API 为什么不够：`ParticleHandle.velocity()` 属于 api 包，但**位置**只能通过 `data(): ParticleData?` 取，
  而 `ParticleData` 在 `work.nekow.particledrawing.core.server`（非 api 包）——使用方被迫依赖 core 内部类型
- 期望 API 形态：`fun position(): Vec3?`（与 `velocity()` 对称，内部转调 engine 的 `getParticle(id)?.position()`）；
  若不想暴露单粒子查询，也可在 api 包提供只读快照类型
- 影响面：`magic/effect/visual/ParticleSwarm.kt`
- 临时降级：用 `handle.data()?.position()`（能跑，但绑定了 core 类型，PD 内部重命名/移动包会波及本模组）

### [需求] 缺少「力/加速度」与批量更新，只能逐粒子每 tick 广播速度

- 状态：未决
- 目标能力：黑洞需要持续修正**上千个**粒子的速度（每 tick 32 个生成、寿命最长 60 tick）
- 现有 API 为什么不够：`setVelocity` 是**单粒子即时广播**（`ParticleVelocityPayload`）。
  逐粒子逐 tick 广播，网络量与对象分配都过高；为此我们不得不每 3 tick 才施力一次（牺牲轨迹精度）
- 期望 API 形态（任一即可）：
  1. `applyForce(particleId, accel: Vec3, ticks: Int)`：服务端权威加速度，客户端按同一规则积分，只在开始时广播一次；
  2. 批量接口 `setVelocity(ids: Collection<UUID>, velocities: ...)`：一个包覆盖多粒子；
  3. 「速度缓动版 `move`」：`setVelocity(target: Vec3, durationTicks: Int, easing)`，一次下发、N tick 内插值到位
- 影响面：`magic/effect/visual/ParticleSwarm.kt`、`magic/effect/lifecycle/BlackHole.kt`
- 临时降级：`BlackHole.FORCE_INTERVAL_TICKS = 3`（每 3 tick 施一次力，加速度按 tick 数补齐）

### [需求] 原始粒子缺少「实体锚点」绑定

- 状态：未决（**不阻塞**：护盾已用 `trackAround` 每 tick 钉位置实现，只是广播开销偏高）
- 目标能力：能量护盾的淡蓝色球体（§1.2）以持有者为锚点，随实体移动/转向；理想情况下服务端只更新锚点一次
- 现有 API 为什么不够：`Anchor.Entity(entityId)` 只服务 `.pdrawc` **动画播放**路径；
  原始粒子要跟随实体，使用方只能每 tick 对**每个**粒子 `track(...)`（同样是一次广播/粒子/tick）
- 期望 API 形态：`ParticleHandle.attachTo(entityId: Int, offsetFromPivot: Vec3)`，
  或让 `ParticleGroup` 支持实体锚点（服务端只更新锚点一次，客户端本地解析实体位置与朝向）
- 影响面：`magic/modifier/EnergyShield.kt`（现用降级方案）、`magic/effect/visual/ParticleSwarm.kt`
- 临时降级：用 `trackAround` 每 tick 钉位置，粒子数控制在数十个（当前 24）

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
