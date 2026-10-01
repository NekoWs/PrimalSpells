# 已解决记录（resolved）

> 按**轮次**记录「当初为什么这么改」。只增不改，作为备查；**当前仍未解决的东西不在这里**，见 [open.md](open.md)。
> 每轮格式：`编号 | 事项 | 处理方式 | 主要落点`。原先的 §1.2/§1.3/§1.4 三条待办分别由第 7/8 轮、第 10 轮、第 11 轮闭环。

## 轮次记录（第 1~10 轮，2026-09-29 ~ 2026-09-30）

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
| 9.7 | ParticleDrawing 升级到 1.0.8-ALPHA 并写入依赖政策 | 依赖改为由 `gradle.properties` 的 `particledrawing_version` 驱动（`build.gradle` 不再硬编码 jar 名），`mods.toml` 的 `versionRange` 下限提到 `[1.0.8,)`；1.0.8-ALPHA 编译通过（新增 `EffectCallbacks`/`EffectVarStore`，无破坏性变更）；把「遇 API 缺陷/缺失**不得自行另写一套**，须停下并列出 API 修改需求」写成硬性政策（[conventions.md](../conventions.md) §9 + [design/effects-api.md](../design/effects-api.md) §0 + 本文 §7） | `build.gradle`、`gradle.properties`、`neoforge.mods.toml`、`docs/conventions.md`、`docs/design/effects-api.md`、`docs/known-issues.md` |
| 9.8 | 运行时报 `requires particledrawing 1.0.8 or above / Currently ... 1.0.8-ALPHA` | 根因：FML 用 Maven 版本语义，`1.0.8-ALPHA` 是 1.0.8 的预发布版，排序**低于** 1.0.8，故 `[1.0.8,)` 不匹配。修正：区间下限带预发布标识 → `[1.0.8-ALPHA,)`；并把它也放进 `gradle.properties`（`particledrawing_version_range`）由 `build.gradle` 的 `expand` 插值进 `mods.toml`，避免两处手改不同步。验证：用 Maven `VersionRange` 实测（`[1.0.8,)` 对 `1.0.8-ALPHA` = false；`[1.0.8-ALPHA,)` = true，且接受 1.0.8/1.0.8-beta/1.0.9-ALPHA、拒绝 1.0.7），并跑通开发服务端（日志出现 `ParticleDrawing 1.0.8-ALPHA (particledrawing)` 且 `Done (1.793s)!`，无依赖报错） | `gradle.properties`、`build.gradle`、`META-INF/neoforge.mods.toml`、`docs/build-and-run.md`、`docs/conventions.md`、`docs/design/effects-api.md` |
| 9.9 | 「ParticleDrawing API 修改需求」清单出现时机太宽 | 收窄为：**只在本次功能要求涉及 ParticleDrawing API 时**才在产出末尾附该清单并登记 §7（纯配置/版本号/其它系统的改动不附）；「不得自行另写一套」的红线保持始终有效。同步更新 [conventions.md](../conventions.md) §9、[design/effects-api.md](../design/effects-api.md) §0、`AGENTS.md` 第 6 条 | `docs/conventions.md`、`docs/design/effects-api.md`、`AGENTS.md`、`docs/known-issues.md` |
| 9.10 | 规范 git 提交信息格式 | 定为 `<目的>：<简短描述>`（目的取自 `新增/修复/优化/重构/文档/测试/构建/杂项`；全角冒号；一句 ≤ 20 字；只写大概干了什么，不复述具体改动与文件名）；写入 [conventions.md](../conventions.md) §10 + 提交前清单 + `AGENTS.md` 第 7 条 | `docs/conventions.md`、`AGENTS.md` |

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

### 第 9 轮（工程卫生：编译警告 / 行尾规范 / 清理旧依赖）

| 编号 | 事项 | 处理方式 | 主要落点 |
|---|---|---|---|
| 9.1 | 全量编译有 4 条 `w: Redundant call of conversion method` | 都是对**已经是目标类型**的值再调一次转换：`Tracking.kt:107`（`entity.x` 是 Double、`status.pos.x` 是 Float → 差值已是 Double，去掉 `.toDouble()`）、`ExplosionProjectile.kt:91/92`（`angle` 是 Float，`kotlin.math.cos/sin(Float)` 直接返回 Float，去掉 `.toFloat()`）、`Serpentine.kt:66`（`Float * Int` 仍是 Float，`sin` 返回 Float，去掉 `.toFloat()`）。现全量编译 **0 warning** | `effect/geometry/Tracking.kt`、`modifier/ExplosionProjectile.kt`、`modifier/Serpentine.kt` |
| 9.2 | 行尾/二进制属性没规范，提交时反复刷 `LF will be replaced by CRLF` | 重写 `.gitattributes`：`* text=auto eol=lf`（仓库与工作区统一 LF，`core.autocrlf=true` 不再干扰）、`*.bat`/`*.cmd` 用 CRLF、`gradlew`/`*.sh` 用 LF、`*.jar`/`*.png`/`*.ogg`/`*.zip` 等标为 `binary`（禁止任何转换，`lib/` 下的前置模组 jar 也受保护）、文本类扩展名显式 `text`，并保留 MDK 的 `src/generated/**` 规则。`git add --renormalize .` 校验：**没有产生行尾 churn**（既存 blob 本就是 LF）；实测 CRLF 新文件提示「将被归一为 LF」、LF 新文件零警告 | `.gitattributes` |
| 9.3 | `lib/` 残留旧版前置模组 jar | 删除 `lib/particledrawing-1.0.6.jar`（已不被引用）；`lib/` 现在只留 `particledrawing-1.0.9-ALPHA.jar` + `-sources.jar`。依赖面同步：`particledrawing_version=1.0.9-ALPHA`、`version_range=[1.0.9-ALPHA,)`（由 `gradle.properties` 驱动 `mods.toml` 插值） | `lib/`、`gradle.properties`、`docs/build-and-run.md` |

### 第 10 轮（`ParticleSwarm` → PD `ParticleBatch` 迁移）

| 编号 | 事项 | 处理方式 | 主要落点 |
|---|---|---|---|
| 10.1 | 自研 `ParticleSwarm`（273 行：句柄表 + 寿命 + 三套写法）该退场了（§1.3） | PD 1.0.9 的 `ParticleBatch` 已覆盖全部簿记职责，三处接线改用：黑洞 `sucked.forEach { … } + trackAll` / `removeIf`、球壳 `ensureSize`；重力场 `removeIf` + `ensureSize`；护盾 `ensureSize`。**删除 `ParticleSwarm.kt`**（连同 `MAX_TOP_UP_PER_TICK`、`Attached`/`Simulated` 内部表） | `effect/lifecycle/{BlackHole,GravityField}.kt`、`modifier/EnergyShield.kt`、**删除** `effect/visual/ParticleSwarm.kt` |
| 10.2 | 「生成一颗粒子」的写法在三处各抄一遍 Builder 链 | 收敛为 `util/PdParticles.kt` 的 `ParticleManager.spawnParticle(pos, color, scale, lifetime, velocity?)` + 常量 `PD_IMMORTAL`（`-1`）。**不含任何簿记**（不持句柄、不管寿命），只是 Builder 链的一层便捷封装 | `util/PdParticles.kt` |
| 10.3 | 护盾球壳要 10 tick 才长满（旧 `MAX_TOP_UP_PER_TICK = 4`）；黑洞/重力场球壳被粒子上限拒掉后**永久缺量** | 改用 `ParticleBatch.ensureSize(n, spawner)`：被拒就停下、下一 tick 再补，不空转也不留缺口。护盾现在**首帧铺满**（40 个，一次性 40 生成 + 40 挂载包，之后 0 包/tick） | `EnergyShield.kt`、`BlackHole.kt`、`GravityField.kt` |
| 10.4 | 护盾锚定只用网络 id（`attachTo(entityId, …)`），实体重载/换维度后锚点可能失效 | 改用 1.0.9 的 `attachTo(Entity, offset)`（**UUID 为主身份**，网络 id 仅作解析缓存）。同时按管理器（维度）缓存粒子集，跨维度时先销毁旧球壳再重建（否则球壳会留在旧维度） | `EnergyShield.kt` |
| 10.5 | **本模组 bug（顺带修掉）**：`AreaFieldEffect` 用 `caster.level()`（施法者**当前**维度）做实体查询、投射物施力与粒子管理器。施法者一旦跨维度，区域场就会在新维度里查不到任何东西、粒子也发到新维度 —— 而场的坐标还在旧维度，表现为**区域场静默失效** | 维度在**首 tick 捕获**（`fieldLevel`），之后固定使用。副带效果：粒子管理器随之稳定，「一个区域场 = 一个维度」成立，子类缓存 `ParticleBatch` 才安全 | `effect/lifecycle/AreaFieldEffect.kt` |
| 10.6 | 迁移必须**不改运动学**（视觉已定稿，不能顺手变样） | 纯算术复算 500 个随机样本：迁移前（先判中心再积分）与迁移后（先积分再判中心）的**销毁 tick 最大差 = 0**，「到中心销毁 0/500、其余在 r=2.46 处寿命到期」两边完全一致。复算式与结论见第 11.1 条（一次性脚本未入库，两行递推写在文档里即可复现） | — |
| 10.7 | 迁移后的链接与加载验证 | 全量构建 **0 warning**；`javap` 核对 `lib/particledrawing-1.0.9-ALPHA.jar` 里 `ParticleBatch` 的 13 个方法签名与调用一致；开发服务端冒烟：`Done (0.242s)`，PD `1.0.9-ALPHA` + `primalspells` 正常加载，无 `ERROR`/`Exception` | — |

## 第 11 轮（2026-09-30，黑洞阻尼 / 死代码 / 文档结构）

| 编号 | 事项 | 处理方式 | 主要落点 |
|---|---|---|---|
| 11.1 | **黑洞「被吸入的粒子」从未抵达中心**（第 10 轮数值复核发现）：阻尼 `0.995` 太弱，粒子形成偏心率很大的椭圆轨道，半径降到约 2.2~2.6 后在 2.2~5.0 之间来回，靠 60 tick 寿命被回收 —— 「进入中心 0.1 格即销毁」这条判定从未触发（500 样本 **0/500**），中心区域始终是空的，与第 3/5 轮的需求不符 | 阻尼 `0.995` → **0.9**。复核：500 样本 **100%** 抵达中心、平均 **34 tick** 被销毁（峰值存量 60 → 约 34，顺带省带宽）；轨迹复核 t=1 r=4.98 → t=20 r=2.36 → t=30 r=0.71 → t=34 销毁。同时修掉代码里两句与实测不符的注释（「轨道半径单调收缩」「正常会在被吸入时提前销毁」） | `magic/effect/lifecycle/BlackHole.kt` |
| 11.2 | `util/ParticleShapes.kt` 的 `sphereSurface` 已无调用方（黑洞/重力场/护盾都改用 `fibonacciSphere` 取点）；私有 `emit(..., volume)` 的 `volume=false` 分支随之成为死代码 | 删除 `sphereSurface` 与 `volume` 形参（`emit` 直接内联进 `sphereVolume`），并把对象 KDoc 改成「原版撒点 + PD 取点两类用法」的取舍说明 | `util/ParticleShapes.kt` |
| 11.3 | `docs/known-issues.md` 近 500 行：**已解决**的记录把**未解决**的淹没了，格式也不统一 | 拆成 `docs/issues/`：`open.md`（未解决）/ `verify.md`（待实机验证）/ `resolved.md`（本文件）/ `pd-api-requests.md`（PD 需求），外加 `README.md` 写明分工与维护规则；`docs/known-issues.md` 保留为**纯指针**（旧链接不失效）；「不是 bug 的设计约定」（原 §3）去重后并入 `architecture.md` §8 | `docs/issues/*`、`docs/known-issues.md`、`docs/architecture.md` |

---

## 第 12 轮（2026-09-30，PD 1.0.10 批量运动入口接入 / 护盾自转）

| 编号 | 事项 | 处理方式 | 主要落点 |
|---|---|---|---|
| 12.1 | 前置升级到 **1.0.10-ALPHA**（PD 提交 `e0cbd5d`：批量设速/批量施力 + 「运动指令一律解除实体锚点」口径统一） | 在 PD 仓库 `./gradlew build` 出包（含其单测）→ `lib/` 换 jar（`particledrawing-1.0.10-ALPHA.jar` + `-sources.jar`，旧 1.0.9 删除）→ `gradle.properties` 的 `particledrawing_version` / `particledrawing_version_range=[1.0.10-ALPHA,)` → 构建通过（产物 `mods.toml` 为 `[1.0.10-ALPHA,)`） | `lib/`、`gradle.properties`、`docs/build-and-run.md` |
| 12.2 | **黑洞的「本模组自持速度表」临时降级可以撤了**（第 10/11 轮遗留：`velocities: HashMap<UUID, Vec3>` + 每 tick 一次 `trackAll` 写位置） | 改用 `ParticleBatch.setVelocityAll(velocities)`：每 tick 由 `forEach` 读 PD 的权威 `pos/vel` 算出**本 tick 每颗粒子该有的速度**（`v ← v·阻尼 + 向心加速度`），一包下发（**1 包/tick**），**位置积分交回 PD**（`pos += vel`，服务端权威、暂停不外推）。删掉速度表与自算积分 —— 本模组不再持有任何跨 tick 粒子状态；PD 需求 #1（批量施力/设速）就此闭环 | `magic/effect/lifecycle/BlackHole.kt`、`docs/issues/pd-api-requests.md` |
| 12.3 | 护盾**没有自转**（第 7/8 轮为绕开「错峰重建/滑入」把偏移固定死了），且球壳用 `ParticleBatch` + 逐粒子 `attachTo` 表达 —— 想自转只能「每 tick `trackAll` 下发旋转后的偏移」（1 包/tick，且位置指令会解除实体锚点） | 改用**编排式**的 `ParticleGroup`：`Draw.sphere(manager, 球心, 1.7, 40, 颜色, 缩放).followEntity(uuid, 身体中段偏移).spin(轴, 弧度/tick)` —— 球壳一次铺满（黄金角螺旋 = 均匀）、跟随与自转都在**客户端本地求值**，**0 包/tick**；跨维度 / 换实体才重建（按 `level` + `uuid` 判定）。`SHELL_SPIN_AXIS`（略倾斜，非纯 Y）与 `SHELL_SPIN_RADIANS_PER_TICK`（π/60 ≈ 3°/tick ≈ 6 秒一圈）是可调常数，改它们不花带宽 | `magic/modifier/EnergyShield.kt` |
| 12.4 | 依赖语义笔记（1.0.10 起） | `setVelocity`/`setVelocityAll`/`applyForce`/`applyForceAll` **都会解除实体锚点**（1.0.9 的 `setVelocity` 漏了这一步，位置每 tick 仍被锚点覆盖）→ 「先 `attachTo` 再给速度/力」是无效组合；护盾改用组编排后天然避开这条口径 | `docs/issues/pd-api-requests.md`（能力边界表）、`docs/architecture.md` §9 |

---

## 第 13 轮（2026-09-30，护盾球壳「掉到脚下」/ PD 1.0.11 轴心语义）

| 编号 | 事项 | 处理方式 | 主要落点 |
|---|---|---|---|
| 13.1 | 前置升级到 **1.0.11-ALPHA**（PD `3007b22`：把「组轴心是程序级状态、调用顺序即语义」写进 KDoc 与 `api-guide.md`，并删掉指令里从未被消费的 `PivotRef` 字段）—— 这正是第 12 轮提出、登记在 `pd-api-requests.md` 的那条文档需求 | `lib/` 换 jar（`particledrawing-1.0.11-ALPHA.jar` + `-sources.jar`）、`gradle.properties` 的版本与区间 → `[1.0.11-ALPHA,)`；构建 + 服务端冒烟通过（`Done (0.262s)`）；文档里的版本引用同步 | `lib/`、`gradle.properties`、`docs/build-and-run.md`、`docs/README.md`、`docs/design/effects-api.md`、`docs/architecture.md`、`docs/conventions.md` |
| 13.2 | **实机 bug（本模组）：护盾球壳出现时位置正确，之后「跳」到脚下的错误位置** | 根因是**组轴心与成员相对偏移的账算错了**：客户端的 `rel` 是按「成员 spawn 位置 − arm 时下发的组 `pivot` 字段」重算的（`ClientAnimationProgramManager.arm`），而 `followEntity(uuid, offset)` 会**顺手把组 `pivot` 字段加上这个 offset**（`pivot = offset.add(pivot)`）。原写法「先 `Draw.sphere(球心)` 再 `followEntity(身体中段)`」于是让 `pivot` 变成「球心 + 身体中段」，`rel` 少算了一整个身体中段（−0.99 格）→ arm 后整壳正好掉回脚下（arm 前渲染的是 spawn 位置，所以「开始是对的」）。修法：**先 `createGroup(脚底)` → `followEntity(uuid, 身体中段)` 绑定轴心 → 再让 `Draw.sphere` 的 `center` 等于绑定后的轴心**（= 球心）铺成员，这样 `rel = 球面偏移`、每 tick 轴心 = 球心、`spin` 才是原地转 | `magic/modifier/EnergyShield.kt` |
| 13.3 | 上述 bug 的**离线验证**（客户端看不到，只能按 PD 源码语义算账） | 按 6 条源码事实（`followEntity` 改 pivot 字段 / `flush` 用当前字段当 `initialPivot` / `arm` 用 `spawn − initialPivot` 算 rel / `resolvePivot` = 实体位置 + offset / `PivotFrame.apply` = pivot + rel / `spin` 旋转 rel）复算三种写法：**修前**球壳中心落在脚底（+0.000，期望 +0.990）且球心相对轴心 0.990 → 自转还会绕轴心画圈；**修后** +0.990 且球心相对轴心 0.011 ≈ 0（自转原地转）。对照写法「轴心留脚底、偏移塞进粒子」位置对但自转画圈（0.990），说明偏移必须落在**轴心**上而不是粒子上 | `build/verify/shield-pivot-accounting.txt`（一次性脚本，未入库）；结论已写进 `EnergyShield.kt` 的 KDoc |
| 13.4 | 易踩点固化 | PD 的 KDoc 已写明「轴心是程序级状态、顺序即语义」，本模组在 `EnergyShield.updateShieldVisual` 的 KDoc 与 [design/effects-api.md](../design/effects-api.md) §0.3 里补上**本项目的正确顺序**（先绑轴心再铺成员），并在 [verify.md](verify.md) 第 20 项写成实机检查点 | `magic/modifier/EnergyShield.kt`、`docs/design/effects-api.md`、`docs/issues/verify.md` |

---

## 第 14 轮（2026-09-30，护盾：取下泄漏 / 多张卡只出一层球壳）

| 编号 | 事项 | 处理方式 | 主要落点 |
|---|---|---|---|
| 14.1 | **实机 bug：护盾生效期间把护盾法术从法杖里取出 → 球壳粒子一直留着**（顺带确认：效果本身已经停了） | 根因：法术被取下后 `Wand.load()` 重建 `loadedMagics`，该实例**不再有任何 tick**（`onInventoryTick`/`onPassiveIdle` 都不会跑），而球壳是 `lifetime = -1` 的永存粒子组 → 没人销毁它。**效果**本身不会残留（弹开逻辑只在该实例的 `onInventoryTick` 里，已不再执行），残留的只有表现。修法：新增生命周期钩子 `Magic.onUnloaded()` —— 由 `Wand.load()` 对「本次重建后不再装载」的实例调用，另由 `Wand.unload()`（法杖内存实例被回收，`WandManager.recycleUnheld`）调用；`EnergyShield` 在钩子里 `clearShieldVisual()` | `magic/core/Magic.kt`、`wand/Wand.kt`、`wand/WandManager.kt`、`magic/modifier/EnergyShield.kt` |
| 14.2 | **多张护盾卡的球壳互相叠加**（不好看），且各自独立结算 | 改为「**一层球壳 + 一个耐久池**」：容量 = 每张 `maxDurability` × 卡数；由 `loadedMagics` 里的**第一张卡**（主卡）负责球壳/弹开/HUD，其余卡每 tick 把主卡的 `durability`/`pauseTicks`/`durabilityLoaded` 镜像过来后直接返回。镜像的目的是让「同一法术 id 只有一份存档状态」（`Wand.magicStates` 按 id 存、保存时同 id 互相覆盖）在**任何一张卡上写都一致**。主卡被取下后下一张自动接任（下一 tick 铺出自己的球壳，旧的由 14.1 的钩子先清掉）。容量变大**不白送耐久**（池子原样保留、只在超过新容量时钳下去），避免「反复拔插第二张卡刷满耐久」 | `magic/modifier/EnergyShield.kt` |
| 14.3 | 耐久读档的钳制口径 | `loadRuntimeState` 不能再用「钳到单卡上限」（那时拿不到 `wand`、算不出卡数）：改为只保证非负 + 记录「是否读到过耐久」（`durabilityLoaded`）；**首次结算**（旧档/新装）按当前容量补满，之后每 tick 按 `shieldCapacity()` 钳制。HUD 的 `maxDurability` 字段改为传**本杖容量**（HUD 本就按 `durability / maxDurability` 画条） | `magic/modifier/EnergyShield.kt` |

---

## 第 15 轮（2026-09-30，黑洞 / 重力场 / 护盾的视觉重做）

用户要求：① 黑洞加粒子、球体加**进入/退出**动画；② 重力场「完全不像重力、更像下雪」，重做表现；
③ 护盾改成**五/六边形构成的球体** + **展开/收起**动画。

- **黑洞**（`magic/effect/lifecycle/BlackHole.kt`）
  - 球壳从 `ParticleBatch`（96 颗灰黑单色、`ensureSize` 补齐）换成 `ParticleGroup`：`Draw.sphere` 铺 **144** 颗，
    `ColorSource` 做「上偏紫、下近黑 + 每 5 颗一个亮紫星点」的渐变，`fadeIn(14)` 淡入 + 慢速 `spin` 自转
    （**0 包/tick**）。代价：球壳成了**固定成员集**，不再有 `ensureSize` 的缺量补齐（个别生成被上限拒绝时留空缺）。
  - **进入动画**：外球面（1.45 倍半径）撒 72 颗粒子，初速**指向中心**、大小 = `距离/寿命`（14 tick）
    → 到点正好落进中心并自行过期；**退出动画**：球壳 `delay(已存在 tick)` + `fadeOut(16, removeAfter = true)`，
    同时在球壳半径撒 96 颗向心粒子 → 「整个场塌进奇点」。两批都是**一次性常量速度**粒子（0 包/tick、自带寿命、
    不需要簿记，即使黑洞提前消失也只是多飞不到一秒）。
  - 被吸入粒子 1 → **每 tick 3 颗**（第一颗亮紫小点、其余暗紫大点），峰值存量约 100。
- **重力场**（`magic/effect/lifecycle/GravityField.kt`）视觉重做。诊断：旧表现是「上半球生成 + **匀速**垂直下落」，
  **匀速下落正是雪花的运动特征**；而且回收面取「下半球表面」，场心常在半空，粒子是在看不见的地方凭空消失的。
  - **加速度**（重力的第一特征）：下坠粒子初速 0，生成时一次 `applyForce(Vec3(0,-0.01,0), ticks = -1)`
    （`spawnParticle(..., acceleration = ...)`），之后两端逐 tick `v += a; pos += v` —— 10 格约 45 tick、
    末速 0.45 格/tick，**起步慢、落地快**，且 **0 逐 tick 包**。
  - **方向感**：每 22 tick 从场顶压下一层**重力波环**（`Draw.circle` + `move(..., EASE_IN)` 加速下坠 + `fadeOut(removeAfter)`），
    场地结束时把还在下坠的环一并 `remove()`。
  - **地面**（重力的第二特征）：首 tick 向下逐格探测 5 格求出**场底平面**（全探空则退化为球体最底端），
    粒子落到该平面即回收，并在平面上铺一条缓慢自转的**井环**标出「重力井口径」—— 砸在地上而不是消失在半空。
- **护盾**（`magic/modifier/EnergyShield.kt`）：球壳换成**截角二十面体**（足球）。新增 `util/Polyhedra.kt`：
  由正二十面体（12 顶点 / 30 棱 / 20 面）截断顶点得到 **32 块面板（12 五边形 + 20 六边形）/ 60 顶点 / 90 棱**，
  所有棱等长；面板边界点按「每条棱 N 点」细分并投回球面，绕向统一为**逆时针**。
  - **每块面板一个 `ParticleGroup`**（32 个组）：组级编排只能整组动，而「各面板从球心飞向自己的位置」是逐面板动画，
    只能用「一面板一组」表达。成员 = 沿边界的粒子（每条棱 2 点 → 360 颗永存）。
  - **展开**：`fadeIn(14)` → `move(-面板中心, 0)`（瞬时折到球心）→ `move(+面板中心, 14, EASE_OUT)`（飞回球面）
    → `spin`（持续自转）；**收起**：`delay(已存在 tick)` + `move(-面板中心, 12, EASE_IN)` + `fadeOut(12, removeAfter = true)`
    —— 破盾、放下法杖（4 tick 缓冲后）、取下法术都走收起；**只有跨维度/换实体**才立即销毁（旧面板在旧维度上，
    动画没有意义）。稳态仍是 **0 包/tick**；代价是建立时刻一次性 32 份程序包 + 360 个生成包。
  - ⚠️ **本轮新踩到的 PD 语义坑（已登记需求）**：`ParticleGroup.move()` 在 `emit` 之前会 `pivot = pivot.add(delta)`，
    而客户端 `arm` 时用**收到全量程序那一刻的组轴心字段**反算每颗粒子的相对偏移 —— 所以**第一条录进组里的指令
    决定了 arm 基准**。对实体轴心（`followEntity`）尤其致命：渲染用的是「实体当前坐标 + 绑定偏移」，与 arm 时
    的字段无关，误差不会抵消。本模组把 `fadeIn` 排在两条 `move` **之前**（代码里写明了原因）。
    这与第 13 轮「先绑轴心、再铺成员」是同一族约束（都是「arm 基准必须等于预期原点」）。
- **数值自检**：`build/verify/geom/` 用 Kotlin 编译器（`kotlin-compiler-embeddable` +
  `kotlin-stdlib`，都在 Gradle 缓存里）+ 一个同语义的 `Vec3` 替身，**直接执行真实的 `util/Polyhedra.kt`**
  （不是另写一份 JS 复刻），断言 19 项：32 面板（12 五 + 20 六）、60 顶点、90 棱、顶点度数 3、棱度数 2、
  棱长一致（0.686031961 @ 半径 1.7）、面板共面、环上点等角距且绕向统一、五-五不相邻 / 五-六棱 60 / 六-六棱 30、
  每个五边形邻 5 个六边形、每个六边形邻 3 五 + 3 六 —— 全绿（输出：`build/verify/geometry-selfcheck.txt`；
  自检脚本本身不入库（`build/` 已 gitignore），复跑方法见 [build-and-run.md](../build-and-run.md) §7）。
  另外把真实几何数据正交投影成线框做了一次**目视核对**（`build/verify/shield-wireframe.png`：五/六边形
  严格边对边铺满球面，与数值断言一致）；顺带确认了「不连线只看点」在这个密度下不像球 —— 这也解释了
  为什么球壳必须靠「每条棱 2 点 + 粒子尺寸 0.4（≈点距 0.345）」把边界画成接近实线。
  **第一次运行就抓到一个真 bug**：截断点用了**无向**键，`cut(a→b)` 与 `cut(b→a)` 撞成同一个点，
  60 个顶点只剩 30 个（`require` 直接抛错）。修法是给「有向顶点对」单独一个键。
- 落点：`magic/effect/lifecycle/{BlackHole,GravityField,AreaFieldEffect}.kt`、`magic/modifier/EnergyShield.kt`、
  `util/{Polyhedra,PdParticles}.kt`
- PD 侧新增需求 2 条（见 [pd-api-requests.md](pd-api-requests.md)）：`scale`/`pulse` 不缩放到轴心的距离；
  `move` 会改 arm 基准轴心。
- 构建：`./gradlew build --no-build-cache` **0 warning**；开发服务端冒烟 `Done (0.203s)!`，0 `ERROR`/`Exception`。

## 第 16 轮（2026-09-30，按实机反馈重改三个特效的动画与可读性）

实机反馈四条：① 黑洞进出场时「外面的大球不动、里面另有一个小球在缩」；② 重力场「看不出范围大小，
明明是球形范围」；③ 护盾的展开/收起不好看，要「固定球体逐渐构建/消失」；④ 护盾粒子太稀疏，
看不出五/六边形。

- **黑洞**（`BlackHole.kt`）：动画与球壳脱节的根因是第 15 轮的做法 —— 球壳只是 `fadeIn`，
  另撒一圈**独立**的向心粒子当动画。本轮把动画**还给球壳本身**：
  - **进场**：整壳 144 颗粒子先在球心附近（0.18 倍半径）生成，随即逐粒子下发
    `ParticleHandle.move(自己的球面位置, 14 tick, EASE_OUT)` —— 逐粒子缓动由 **PD 在客户端求值**
    （服务端只发一次指令），观感是「这颗球从中心长出来」；
  - **退场**：把整壳粒子按**原位置/原颜色**重新生成一遍，初速指向中心、寿命 = 14 tick
    （速度 = 距离 / 寿命）→ 整颗球同步塌缩、抵达中心那一刻全部过期。为什么必须重新生成：
    球壳粒子是永存的，而 `ParticleHandle` 没有改寿命的入口 —— 直接给速度会「穿过中心继续飞出去」，
    变成永远留在世界里的粒子。
  - 结构上从 `ParticleGroup` 退回 `ParticleBatch`（成员固定）：逐粒子径向运动与组的客户端指令会互相覆盖；
    顺带去掉了组的 `spin`（在这种球壳上几乎看不出来，却要多维护一套程序与生命周期）。
    带宽：进场 1 包/粒子、退场 2 包/粒子（一次性），稳态 **0 包/tick**。
- **重力场**（`GravityField.kt`）：范围看不出来的祸首是第 15 轮的「把球壳压暗到 alpha 70 + 加了很抢眼的
  地面环」。本轮把球壳提回 alpha 120、120 颗、尺寸 0.45，并换成 `ParticleGroup` 加**缓慢自转**
  （绕竖直轴约 10 秒一圈，0 包/tick）；波环（36 颗 / alpha 105）与井环（44 颗 / alpha 95）整体压暗，
  让**球形轮廓重新成为主角**。
- **护盾**（`EnergyShield.kt`）：展开/收起从「面板从球心飞出 / 缩回球心」改成**球体固定、逐块构建/消散**：
  - **构建**：`updateShieldVisual` 每 tick 建 2 块面板（各自的组 + 一圈粒子 + `fadeIn(6)`），
    面板顺序**按中心高度由低到高**排序 → 整颗球像被从脚下往上搭起来，约 16 tick 完成；
  - **消散**：反向（LIFO）逐块 `fadeOut(5, removeAfter = true)`，每 2 块错开 1 tick（约 1 秒）。
    这里能安全使用 `delay` 阶梯，是因为 `fadeOut` 在指令生效前默认「还没开始淡出」（因子 0），
    而 `fadeIn` 的默认值相反（1）——用 `fadeIn` 做阶梯会先闪一下；
  - **不再使用 `move`**：面板不位移，于是彻底绕开了「`move` 会改 arm 基准轴心」那条 PD 坑；
  - **粒子加密**：`SHELL_POINTS_PER_EDGE` 2 → **3**（360 → **540** 颗），棱上点距 0.229 < 粒子尺寸 0.4，
    边界才连成实线、五/六边形才认得出来。构建被摊到 16 tick，每 tick 约 34 颗粒子，瞬时突发比第 15 轮小。
- 落点：`magic/effect/lifecycle/{BlackHole,GravityField}.kt`、`magic/modifier/EnergyShield.kt`
- PD 需求清单不变（2 条），但两条都已不再是本侧的阻塞项：`scale` 的径向缩放本轮改用
  「逐粒子 `move` 缓动」与「重新生成 + 向心初速」绕开；arm 基准那条则通过「面板不位移」绕开。
- 构建：`./gradlew build --no-build-cache` **0 warning**（产物 `build/verify/build-round16.txt`）。

## 第 17 轮（2026-09-30，实机录制流水线；顺带修掉护盾消散瞬间消失）

起因：使用方无法操作电脑、只能对话和看文件，因此需要「进游戏截取真实表现」的能力 ——
于是把「开客户端 → 进世界 → 摆位置施法 → 按 F2 截图」整条链自动化（新功能），
并在第一次跑通后**当场抓到两个问题**（这就是这条流水线的价值）。

### 17.1 实机录制流水线（新增）

- `client/capture/CaptureDriver.kt`（开发期客户端驱动）+ `CaptureScenes.kt`（场景表）+
  `tools/capture/capture.ps1`（一条命令）+ `tools/capture/assemble.py`（合成与判定）。
- 全自动：固定种子的超平坦世界（不存在则现场建）→ 黑底摄影棚布景 → 冻结时间/天气/刷怪 →
  相机轨迹（绕球心公转 / 第三人称转台）→ **真实施法路径**（`Wand.spell` / `wand.tick(caster, level)`）→
  **每 tick 抓一帧**（20fps）→ 场景内断言 → 等帧落盘后 `mc.stop()` 自己退出。
- 判定不看人眼：帧数/丢帧 + 场景内断言 + 「偏蓝亮像素」覆盖曲线（空场起飞 / 真的渲染 / 收干净 /
  退场是渐变还是立即），有失败项就非零退出。
- 与 `run/client` 完全隔离（工作目录 `run/capture`），生产包里驱动不激活
  （`CaptureDriver.shouldInstall()` 要求非生产环境 + 系统属性）。

### 17.2 录制发现的两个问题

- **① 护盾消散是「瞬间消失」而不是渐变**（第 16 轮实现，实机还没被看到）：
  帧曲线 104 帧仍是完整球壳、105 帧整壳没了。根因不在本模组的写法，而在 PD 的时间轴语义：
  `ParticleGroup.delay` 推进的是**从建组时刻起累加的绝对游标**，而客户端对「`startMs` 已经过去」的指令
  是「下一帧立刻完成」——建组 100 tick 后再 `delay + fadeOut` 等于当场淡完；
  同一个游标又被 `fadeOut(removeAfter)` 拿去排**相对 now** 的销毁任务，两端口径不一致，
  所以「把游标推到 now 让客户端正确」也不行（销毁会被推后整段已运行时长）。
  已按政策登记为 PD 需求（见 [pd-api-requests.md](pd-api-requests.md)），本侧改成
  **消散时原地重建成一批新组**再下发 `delay + fadeOut`（新组程序起点＝now，两个口径同时成立，自清理）。
  修复后曲线：峰值 9715 → 109 帧 4441 → 120 帧 1113 → 130 帧 0，跨约 20 帧的逐块淡出。
- **② 护盾粒子太密/太近看不出是球**（第 16 轮定稿前的旧机位）：第三人称默认相机距离 4 格、
  而球壳直径 3.4 格 —— 录出来是「一屏蓝色方块」。录制场景因此改成**用测试木桩当持有者**
  （`SceneCtx.spawnDummy`），摄像机退到 7 格外绕圈，整颗球壳才进得了画面。
- 附：黑洞/重力场场景首次录制时上方露出一条亮蓝天空，抢走粒子对比度 → 摄影棚墙高 16 → **28**。

- 落点：`client/capture/`（新）、`tools/capture/`（新）、`magic/modifier/EnergyShield.kt`、
  `build.gradle`（`capture` 运行配置）、`client/PrimalSpellsClient.kt`（条件安装）
- 验证：`pwsh tools/capture/capture.ps1`（三个场景 388 帧全 PASS）；构建 0 warning。

## 第 18 轮（2026-09-30，按实机观感加「力量感」：加速塌缩 + 落点重音 + 镜头冲击）

反馈原话是「有很大提升，但是还是没有力量感」，并指明主要来自**黑洞收起那种线性慢慢收起的感觉**。
录制流水线（第 17 轮）正好让这一轮可以自己看帧改，不用等人测。

### 18.1 黑洞退场：匀速直线收缩 → **悬停 + 加速砸进中心**

- 旧写法是「重新生成整壳、初速 = 距离/寿命」——**匀速**，观感就是慢慢缩回去。
- 新写法：初速 0，每颗粒子各给一个**向心加速度** `a = 2d / (T(T+1))`（`applyForce(..., -1)` 一次性下发，
  两端按 `v += a; pos += v` 积分），寿命 = T。离散积分下 `d(n) = a·n(n+1)/2`，第 T tick 正好抵达中心。
- 实测覆盖半径（录制帧量出来的）：`5.0 → 4.5(4t) → 3.1(8t) → 1.7(11t) → 1.2(12t) → 0`：
  **前 1/3 时间几乎不动（悬停），最后 3 tick 走完 25% 距离**，视觉上就是被猛吸进去。
- 塌到中心那一拍补**中心爆闪**（40 颗亮紫白一次性粒子向外炸开）—— 收完画面不会突然空掉。
- 带宽反而变好了：旧写法 2 包/粒子（生成 + 设速），新写法也是 2 包（生成 + 施力），但**0 逐 tick 包**。

### 18.2 新增：镜头冲击（屏幕震动 + FOV 抽动）

粒子再多，镜头纹丝不动就始终差一口气。新增一条**极小的 S→C 包** `ScreenShakePayload`（8 字节）：

- 服务端 `util/ScreenShake.shake(level, center, radius, amplitude, ticks)`：给范围内玩家发一条，
  按距离衰减（边缘 35%）；**只在重音那一拍发**，之后逐帧偏移与衰减全在客户端本地算（`client/ScreenShakeClient`）；
- 手感配法：振幅按 `(剩余/总时长)^1.35` 衰减 + 前 4 tick 叠一记方向性冲击（先被压一下）+ FOV 张开，
  抖动用三组不同频率正弦叠加（不用随机数、两端可复现），相位按 tick 推进、渲染帧用 partialTick 插值（帧率无关）；
- **玩家自主权**：整体乘原版「受伤视角晃动」强度（`options.damageTiltStrength`，默认 1.0，设 0 完全不晃）；
- 接线点：黑洞开场 0.45 / 塌缩 0.95、重力场开场 0.35 / 首环落地 0.65 / 常规波环 0.30、护盾合拢 0.35 / 破盾 0.85。

**怎么验证「它真的生效」**（不是只看代码）：临时把 `MAX_*_DEG` 放大 5 倍录一遍，再把
`damageTiltStrength` 强制读成 0（= 关闭）录一遍，**同一 tick 逐帧做差**：

| 帧段 | 画面整体垂直位移 |
|---|---|
| 稳态 20-64（无震动） | **0 px**（两次录制逐像素一致 → 也说明录制是确定性的） |
| 冲击 86-95 | −59 / −28 / +13 / +28 / +17 / +7 / +27 / +5 px |

位移只在塌缩那一拍出现、其余帧为 0 —— 机制确认生效，随后把角度调回 2.6°/1.8°/1.4° 的保守值。

### 18.3 顺带补上的其他重音

- **重力场**：第 1 层波环改为**开场重砸**（13 tick 落地、冲击 0.65）+ 每层波环**落地溅射**
  （30 颗一次性粒子沿井环圆周向外向上炸开）—— 之前环只是「淡出消失」，落点没有反馈，下压感断在半空；
- **能量盾**：**合拢那一拍**向外炸一圈冲击波（44 颗）+ 镜头轻撞；**破盾**改为向外炸裂（60 颗、更快更亮）+ 强冲击；
- 这三种「落点重音」都发生在**特效已经死掉之后**（塌缩爆闪、波环落地、护盾合拢都晚于效果自身生命周期），
  所以新增 `util/Deferred.kt`（服务端延迟队列，`MagicManager.tick()` 每 tick 推，按所在维度 `gameTime` 到期；
  这是**我们自己的时序簿记**，不涉及任何粒子同步/求值 —— 那仍然全归 PD）。

- 落点：`magic/effect/lifecycle/{BlackHole,GravityField}.kt`、`magic/modifier/EnergyShield.kt`、
  `util/{ScreenShake,Deferred}.kt`（新）、`network/ScreenShakePayload.kt`（新）、`client/ScreenShakeClient.kt`（新）、
  `network/ClientPayloadSink.kt`、`PrimalSpells.kt`、`client/PrimalSpellsClient.kt`、`magic/core/MagicManager.kt`
- 验证：`pwsh tools/capture/capture.ps1`（三场景 388 帧全 PASS）；镜头冲击用「震动开/关同 tick 逐帧做差」实测位移只在冲击帧出现；构建 0 warning。

## 第 19 轮（2026-09-30，四条实机反馈：塌缩更快 + 反弹小球 + 收起不停转 + 撤销孤儿溅射）

反馈四条：① 黑洞收起还是慢了一点；② 收起后那记白色「爆炸」有点廉价，想要「先收起、再反弹一个很小的球、
最后收起全部」；③ 护盾收起时不再旋转很奇怪，而且转着看着更粗、收起时更细；④ 重力场截图里出现
「没有球体、但下面还是压下去一个环」。

### 19.1 黑洞退场：拍子变快，并改成「反弹小球」收尾

- `EXIT_TICKS` 14 → **8**（第一拍更快；实测 RMS 半径 151→146→135→121→103→81→55→0 px，
  距离几乎全在最后 4 tick 走完）；
- 第二拍新增**反弹小球**（[`spawnReboundBall`]）：整壳砸到中心后就地生成一颗**小得多**的球
  （48 颗、起始半径 0.30），给**向外初速 + 向内加速度**（`v₀=0.325`、`a=0.065` 一次性下发），
  位置曲线 `d(n) = d₀ + v₀n − a·n(n+1)/2` → 第 4~5 tick 弹到最远约 0.95 格、停一下、被吸回中心，
  第 10 tick 收在一点。实测 RMS 半径：`9 → 17 → 23 → 27 → 29 → 29 → 27 → 23 → 16 → 0`（球的 RMS ≈ 29px，
  只有整壳的 1/5 —— 确实是「一个很小的球」）；
- 第三拍把原来那记 40 颗**白色爆闪**换成 **14 颗微光**（尺寸 0.34、寿命 5、颜色避开纯白偏紫）：
  力量感来自塌缩过程本身，不再炸一朵花。三拍衔接都用 `Deferred`（此刻特效已死）。

### 19.2 护盾消散：不再重建球壳，自转一路不停

- 第 17 轮的消散是「把球壳**原地重建成一批新组**再下发 fadeOut」，代价是新组**没有 `spin`** ——
  实机反馈正是「收起时不再旋转、看着更细」（转起来时点在动，观感更粗）。
  试过在新组上补回旧相位：PD 的 `spin` 与一次性 `rotate` 是**各自按自己的基准写 `rel`、后应用者覆盖前者**
  （`ClientAnimationProgramManager.applySlot` → `rotateToSnapshot`），两者**不叠加**，补不出来。
  所以第 19 轮干脆**不重建**：直接对**旧组**追加 `delay(该组已运行时长 + 阶梯) + fadeOut`，
  自转自然延续；`removeAfter` 不用（它拿绝对游标按相对 now 排销毁，会把销毁推后一整段时长），
  销毁改由 `Deferred` 在淡出结束后执行 —— 所以「法术被取下 / 法杖被回收后不再 tick」时动画照样放完。
- **量化验证**（把自转与相机公转分开）：同一帧间隔的像素变化率，壳区 / 背景墙区
  —— 稳态 5.08/0.81 = **6.3×**，收起刚开始 104~108 帧 4.64/0.78 → 4.30/0.84 = **5.1~5.9×**
  （与收起前同量级 → 自转没停），之后才随面板淡出降到 1.0 以下。若自转在收起瞬间停住，这个比值会直接掉到 1。

### 19.3 重力场：撤销「还没落地的落地溅射」

- 现象：场已经结束（球壳/井环都销毁了），但几条**还没落到地面的波环**早在生成时就排好了
  「N tick 后溅一圈」的延迟任务，到点照放 → 地上凭空压出一道环。实机截图（`resolved.md` 里那张
  `gf-tail` 同款）与录制帧都能看到第 76~90 帧只剩一道环。
- 修法：`Deferred.after(...)` 现在返回**可取消句柄**（[`Deferred.Handle`]），重力场在 `clearVisuals`
  里把未到点的溅射全部 `cancel()`；同时溅射本身也改成只在场还活着时才排。

### 19.4 顺带修掉的新 bug 与新增的护栏

- **`ConcurrentModificationException`（服务端崩溃）**：`Deferred.tick()` 边迭代边执行任务，
  而任务体会再排新任务（黑洞三拍连排）→ 直接打崩集成服务端。已改成「先摘出本轮到期的、再统一执行」，
  失败也从「静默吞掉」改成 `WARN` 日志。**这个崩溃是录制流水线第一次跑就抓到的**（人肉测很难复现到这么精确的时序）。
- **新增粒子级护栏**：录制场景里加了 `pd-particles-zero` 断言 —— 直接用
  `ParticleManager.getEngine().particleCount()` 读「这个维度还剩几颗粒子」。
  像素判定只能证明「屏幕上没有亮点」，证明不了「没有 alpha=0 的孤儿粒子还活着」（第 15/16/17 轮反复踩残留）。
  三个场景结束时刻现在都是 `remaining=0`。

- 落点：`magic/effect/lifecycle/{BlackHole,GravityField}.kt`、`magic/modifier/EnergyShield.kt`、
  `util/Deferred.kt`、`client/capture/CaptureScenes.kt`
- 验证：三场景 388 帧、**12 条断言全过**（含 3 条粒子级归零）；构建 0 warning。

## 第 20 轮（2026-09-30，升级 PD 1.0.12 并按其新语义收敛表现）

### 20.1 前置模组升到 1.0.12-ALPHA：四个需求全落地，本侧跟着简化

PD 1.0.12-ALPHA 把第 15/17/19 轮提的四条**全部实现**（见附录 B），本侧相应改动：

| PD 改动 | 本侧动作 |
|---|---|
| 增量追加的指令按「**从现在起**」读（新增 `api/GroupClock`） | 护盾消散**回到最朴素写法**：`delay(阶梯) + fadeOut(5, removeAfter = true)`；删掉「自己算游标 `delay(已运行时长+阶梯)`」「自己用 `Deferred` 排销毁」「`ShellPanel.bornAt`」三处绕法 |
| `rotate` / `spin` 角度**累加**（不再互相覆盖） | 不再需要「不重建组来保住自转」这个理由；本侧仍保留「不重建」，因为淡出本来就不需要重建 |
| `scale` / `pulse` 现在**连到轴心的距离一起缩放** | 暂未使用：黑洞进场仍走「逐粒子 `move`」，可选的简化项记在这里（一处 `group.scale` 就够，但现阶段没必要动已验证过的动画） |
| `move` / `movePath` **不再改 arm 基准轴心** | 本侧本来就没在铺成员后用 `move`，无改动；接线约束从「硬要求」降级为「注意顺序更直观」 |

⚠️ **升级踩到的坑（本侧责任）**：旧绕法在**新语义下会重复计时** ——
`delay(已运行时长 + 阶梯)` 现在被读作「从现在起再等这么长」，于是淡出被推后约 100 tick。
实机反馈的「护盾外壳似乎不止一个球体」正是它：旧壳迟迟不淡出，球壳一旦重建就与旧壳重叠成两层。
**教训**：前置模组改时间轴语义时，本侧所有「自己补偿时钟」的写法都要重新审一遍。
本侧另加了一道防线：`clearShieldVisual` 会连同「正在淡出中的旧壳」一起 `remove()`（`fading` 列表），
即使将来再出现时序错位，也不会叠出第二层球。

### 20.2 护盾：干净的一条线（去掉抖动与爆开，重算密度）

三条实机反馈一起改：

1. **去掉展开「爆开」与破盾「炸裂」**（以及配套的镜头冲击）：外扩粒子让轮廓变糊、
   看起来像叠了两层球 —— 与「干净的一条线」直接冲突。现在球壳只有**球面轮廓**本身。
2. **重算密度与尺寸**（原来看起来是一串断点）：
   - 实测 PD 的尺寸换算：`quadSize = scale × 0.1 × texScale`，录制里标定出 `texScale ≈ 2.1`
     → **实际格数 ≈ scale × 0.213**（两次实测：scale 0.3 → 0.064 格；scale 0.95 → 糊成实心蓝球，反推 0.20 格）；
   - 原来 3 点/棱（点距 0.229 格）配 scale 0.3（实际 0.063 格）→ 点距是粒子宽度的 3.6 倍 → **断点**；
     前后半球的断点交错，观感就是实机反馈的「还有两条线条」；
   - 现在 **14 点/棱（点距 0.083 格）+ scale 0.40（实际 ≈0.085 格）** → 相邻粒子刚好叠住，
     每条棱是一条连续细线，五/六边形一眼可辨；共 **2520 颗**（每块面板 70/84 颗）。
3. 另外确认了「不是第二个球体」：几何自检显示 540 个点里跨面板**完全重合** 360 对、
   去重后独立位置 **240** 个（= 90 棱 × 2 内点 + 60 顶点）——共享棱由两块面板各画一遍，
   位置逐位相同，不存在偏移的第二条线（渲染帧里检出的团块数 231 ≈ 240，也印证了这点）。

### 20.3 黑洞与重力场：镜头冲击收敛

- **黑洞**：去掉退场两拍（反弹小球、收成一点）的镜头冲击，改成**站在范围内就持续极轻微地抖**
  （`rumbleInside`：半径内每 tick 续期一条 8 字节包，振幅 0.05~0.13，越靠近球心略强）；
  开场那一下保留。
- **重力场**：**全部去掉**镜头冲击（场是「压」不是「撞」）；波环间隔 **22 → 12 tick**（下压更密，
  存在感来自一层层压下来）；白色粒子减少（下坠 3 → 2 颗/tick、落地溅射 30 → 20 颗、溅射颜色不再接近纯白）；
  溅射粒子收进 `ParticleBatch`，收场时一并清掉 —— 修掉「球体已经没了、地面还在溅」。

### 20.4 录制流水线：丢帧不再静默

第 20 轮发现录制会**静默少帧**：客户端 tick 若在渲染线程取走上一帧之前又前进一格，
`pendingCaptureTick` 被覆盖，那一 tick 就没录到，而旧计数器不认这笔账（产出少几帧、视频凭空缺一段）。
现在这类丢帧会被如实计入 `framesDropped`，判定口径也从「必须 0 丢帧」改为
「**丢帧在预算内（≤5%）且如实报出**」——录制是同步回读帧缓冲，偶发一次 GPU stall 少一帧属正常，
但丢成片段（>5%）会被拦下。

- 落点：`magic/modifier/EnergyShield.kt`、`magic/effect/lifecycle/{BlackHole,GravityField}.kt`、
  `client/capture/CaptureDriver.kt`、`tools/capture/assemble.py`、`gradle.properties`（PD 版本由使用方提交）
- 验证：三场景 388 帧、12 条断言全过；构建 0 warning；护盾在 2.6× 放大下逐帧目视确认为连续线框。

## 第 21 轮（2026-09-30，护盾回到「粒子感」点阵；录制不再抢鼠标）

### 21.1 护盾：连续实线这条路走不通，改回点阵（实机选的）

第 20 轮把棱做成**连续实线**（14 点/棱 + `SHELL_SCALE` 0.40 = 2520 颗）—— 球外看是干净的线框，
但实机**球内第一人称**反馈「不完美的形状很丑」：最近处一颗方块占 **80+ 像素**，整条棱糊成一条粗带、
五/六边形的轮廓反而看不出来。

- **离线渲染台定位**：把 `Polyhedra` 的边界点 dump 成 TSV（`build/verify/geom/Dump2.kt`），
  再用 Python 按**真实透视**（眼睛在球心上方 0.63 格 = 第一人称实际机位）把粒子画成实心方块，
  一次对比 4 组密度/尺寸 —— 不用反复开游戏，几十秒就能看遍所有候选。
  （这套「dump 几何 + Python 复现视角」的方法本身值得留着，见 `docs/build-and-run.md` 第 7 节。）
- **结论（实机拍板）**：**保留粒子感**，点距明显大于粒径 ——
  **6 点/棱（点距 0.194 格）+ `SHELL_SCALE` 0.25（实际 ≈0.053 格）= 1080 颗**，
  粒子颗颗分明、轮廓清晰。`docs/issues/resolved.md` 里同时记下反方向的经验：
  **不要再往连续实线调**（要成倍粒子，且球内近看必然糊）。
- 顺带确认的换算（第 20 轮实测、这轮继续用）：`实际格数 ≈ scale × 0.213`（`quadSize = scale × 0.1 × texScale`，
  `texScale ≈ 2.1`）。
- 粒子量 2520 → **1080**（-57%），构建突发随之减半。

### 21.2 录制流水线：不再抢使用方的鼠标/焦点

实机反馈「截图程序会抢我的鼠标」。原因是录制跑的是**真客户端窗口**，会弹出来抢焦点并抓鼠标。

- 修法：`CaptureDriver.hideWindow` 在配置阶段直接 `GLFW.glfwHideWindow` + 关掉 `GLFW_FOCUS_ON_SHOW`，
  窗口根本不出现；另外**每 tick 兜底** `MouseHandler.releaseMouse()`（万一某版本又把窗口显示出来，
  鼠标也不会被锁进游戏）。想要窗口可见（调机位/调场景）用 `-PcaptureShowWindow=true`。
- **风险与验证**：隐藏窗口只影响「是否呈现」，渲染与帧缓冲照旧（截图读 `gameRenderer.mainRenderTarget()`）。
  实测跑完整流水线：三场景 388 帧、12 条断言全过，峰值像素 3003 / 2335 / 12684 ——
  帧内容与可见窗口时一致（判定里的「特效确实渲染」就是这条的兜底验证）。

- 落点：`magic/modifier/EnergyShield.kt`、`client/capture/CaptureDriver.kt`、`build.gradle`
- 验证：`pwsh tools/capture/capture.ps1`（隐藏窗口下三场景全 PASS）；构建 0 warning。

## 第 22 轮（2026-09-30，护盾「错位叠加」的真凶：分批建壳 + 每块各自起转）

实机反馈很关键：「第一人称看像是**两个错位的五边形/六边形混在一起**，但我看消失动画里消失的块
似乎是正确的一个五边形/六边形，**可能真的叠加了两个外壳**」。

### 22.1 定位：不是两个壳，是**相位错位**

- 先把「是不是真的两层壳」排掉：PD 粒子数在收场时归零（`pd-particles-zero`）、几何去重只有 240 个独立位置，
  且**单看一块面板确实是正常的多边形** —— 这三条把「两层壳」否掉了。
- 剩下唯一的解释就是「各块的**朝向**不一致」。查代码立刻对上：球壳是**分批**建的
  （`BUILD_PANELS_PER_TICK = 2`，共 16 tick），而 `group.spin(...)` 是**每块面板从自己的组建立那一刻**
  开始累计角度的 —— 于是相邻面板的相位差最多 `16 tick × 3°/tick ≈ 48°`。
  整颗球从建完那一刻起就是「一堆各自转过不同角度的面板」：棱从别的格子中间穿过去、多边形互相错位。
- **离线复现**（几十秒，不用开游戏）：把几何点 dump 出来，按「第 i 块在第 i/2 tick 出生」给每块加上
  `(T − 出生) × 3°` 的旋转，再按第一人称透视画出来 —— 上半张图与实机截图一模一样地乱；
  下半张（所有面板共享同一相位）就是一颗干净的多面体。**结论当场成立**。
  （复现图：`build/capture/spin-phase-diagnosis.png`，方法见 `docs/build-and-run.md` 第 7 节。）

### 22.2 修法：用 PD 1.0.12 的「旋转可叠加」把相位补平

```kotlin
group.fadeIn(BUILD_FADE_TICKS, EasingType.EASE_OUT)
group.spin(SHELL_SPIN_AXIS, SHELL_SPIN_RADIANS_PER_TICK)          // 继续自转
val lateBy = (level.gameTime - shellBornAt).coerceAtLeast(0L)     // 本块比整壳晚生了多久
group.rotate(SHELL_SPIN_AXIS, lateBy * SHELL_SPIN_RADIANS_PER_TICK, 0)  // 瞬时补相位
```

- `shellBornAt` = 建第一块那一刻的 `gameTime`（清壳/消散时归零）；
- PD 1.0.12 起 `rotate` 与 `spin` 的角度**累加**（第 19 轮提的需求），所以「先补相位、再继续自转」写得出来；
  `durationTicks = 0` 就是瞬时补相位；
- 效果：**任何时刻所有面板都处在同一个全局相位**，整球始终是一致多面体；
  新搭上来的那一块也直接落在正确相位上（构建波不再错位）。
- 实机验证（把录制机位临时放进球壳内部，即第一人称视角）：五/六边形整齐相接、三方交汇，
  没有穿插错位；消散中的那一块单独淡出，其余仍然整齐（图：`build/capture/fix-es-inside.png`）。

### 22.3 顺带：丢帧口径修正

第 20 轮加的「如实计丢帧」这轮开始报数了（重力场 6 帧、护盾 1~4 帧）—— 于是暴露了两个问题：

- 「帧数完整」原来要求 `frames == length`，但**丢帧本来就是被计数的**，两者相加才等于总长；
  改成 `frames + dropped == length`，丢帧率另按 5% 预算单独卡（丢成片段才 FAIL）；
- 帧写盘的在途上限 12 → 24（`MAX_INFLIGHT_WRITES`）：重力场那 6 帧丢的是「回读+写盘跟不上」，
  放宽后归零。护盾偶尔还丢 1~2 帧（是最重的场景：1080 颗粒子 + 32 份组程序），在预算内且如实报出。

- 落点：`magic/modifier/EnergyShield.kt`、`client/capture/CaptureDriver.kt`、`tools/capture/assemble.py`
- 验证：三场景 388 帧（护盾 148+2 丢帧）、12 条断言全过；构建 0 warning。

## 第 23 轮（2026-09-30，护盾重做成「受击处展开的玻璃护盾」+ 登记程序化粒子贴图需求）

实机反馈：护盾**太挡视野**、**一直在转容易让晕 3D 的玩家晕**。使用方提出按《葬送的芙莉莲》那种设计：
**只在要被击中的位置展开**、且是**半透明玻璃质感**。这一轮先按「三轮问答」把设计谈定，再动手。

### 23.1 谈定的设计（全部是使用方拍的板）

| 决策 | 结论 |
|---|---|
| 玩法语义 | **只改表现**：全向格挡、耐久、破盾、回复全部照旧（定向格挡以后单独一轮再谈） |
| 自转 | **完全停止** |
| 常驻外观 | **完全不可见**（「护盾在不在」由 HUD 盾条告知） |
| 受击表现 | 在**受击方向上最近的那一块面板**处展开一块玻璃板：亮边 + 极淡填充（≈15%） |
| 停留时长 | 约 0.35 秒淡出；**每次命中各出一片、不限片数** |
| 破盾 | 整颗玻璃罩现形**一闪而碎**（面板沿各自法线向外飘散 + 淡出） |
| 恢复 | 暂停结束重新上线时，全身轮廓**极淡地闪一下** |
| 触发出玻璃的范围 | **只在格挡真的生效时**（本模组投射物被弹开、扣了耐久）；原版箭/近战不反应 |

### 23.2 代码：删掉常驻球壳那一整套

`EnergyShield` 里删掉：`shell`/`ShellPanel`/`fading`/`buildCursor`/`shellBornAt`/`shellLevel`/`shellHolder`/
`idleTicks` 与 `updateShieldVisual`/`buildShieldPanels`/`foldShieldVisual`/`clearShieldVisual`/
`onPassiveIdle`/`onUnloaded`，以及自转轴/角速度、构建波/消散波、放下延迟（`SHELL_LINGER_TICKS`）等常量
（**第 22 轮那套「分批建组补相位」也随之不再需要** —— 整壳只在破盾/恢复两个瞬间整体出现，天然同相位）。

新增：`shieldWasUp`（捕捉破盾/恢复两个翻转瞬间）、`spawnHitPanel`/`spawnBreakFlash`/`spawnRecoveryFlash`
共用一个 `spawnGlassPanel`（先 `followEntity` 再铺成员、`offsetFromPivot` 用相对球心的偏移、`delay + fadeOut(removeAfter)`），
`fillOffsets` 用「从面板中心向边界点连线扇形采样再归一化回球面」铺出极淡填充。

`shieldPanels` 保留但从「球壳成员表」变成**方向索引**：受击时 `maxByOrNull { it.center.dot(受击方向) }` 找最近那块。

### 23.3 录制场景：旧场景作废，新场景能**证明**「不挡视野」

旧场景期望「常驻球壳」，新设计下会**全黑**，所以重写成一条完整时间线（196 帧）：
静止 20 tick → 四次点射（每次一处玻璃板）→ 连射破盾 → 破盾暂停 50 tick → 恢复闪一下。
攻击者是**第二个测试木桩**（`caster` 必须不是持有者，否则投射物会被 `p.caster === player` 排除），
拿一根只装火球、`delay/mana = 0` 的法杖，站在 7 格外平射 —— 走真实施法与真实弹开路径。

新增两条判定（`assemble.py`）：
- **`平时完全不可见`**：静止窗口（帧 2-18）最大覆盖必须 ≤「空场阈值」—— 实测 **0 像素**，
  这正是「太挡视野」被解决的可复核证据；
- **`受击才亮起（多次亮点）`**：受击窗口里检出多处「亮起→消失」（实测 8 个亮点）。
另外因为单块玻璃板只有破盾整罩的约 4%，这一场景的「空场阈值」单独调到峰值的 2%。

**踩到的两个坑**（都记在代码注释里）：
1. **攻击者的法杖要按两条路径各推一次**：`Wand.tick(caster)` 只跑被动（该分支直接 return），
   `status.delay` 的递减只在 `tick(caster = null)` 的闲置分支里 —— 少推一次就只能开出第一发（实测后面全是 `[Delay 1/0]`）；
2. 破盾后的**残余火球**挂着自己的粒子，会让收尾的「粒子归零」断言误报 —— 场景在连射段结束时把在飞的火球收掉
   （与 `setup` 清残留同一套做法）。

### 23.4 登记新的 PD 需求：程序化粒子也要能指定贴图 / UV

做玻璃质感时确认：程序化粒子的贴图是**写死的 8×8 纯白方块**（`TextureCache.defaultWhite()`），
`ParticleHandle.Builder` 与 `ParticleSpawnPayload` 里连贴图字段都没有；而自定义贴图 + 逐粒子 UV
**只对 `.pdrawc` 动画开放**（`Animation.Builder.texture` / `ParticleBuilder.uv` / `groupUV`），
底层那套「解码 + 注册 + UV 归一化」其实是现成的。已按政策登记为需求（第 23 轮，当前未决 1 条），
本侧临时降级＝用密集小方块做出「磨砂玻璃」（做不到清透玻璃）。

- 落点：`magic/modifier/EnergyShield.kt`、`client/capture/CaptureScenes.kt`、`tools/capture/assemble.py`、
  `docs/issues/pd-api-requests.md`
- 验证：三场景 434 帧、13 条断言全过（含「平时完全不可见 0 像素」「受击 8 个亮点」「收尾粒子归零」）；
  构建 0 warning。

## 第 24 轮（2026-09-30，受击反馈只留「玻璃变白」：删掉原版粒子堆）

实机看录制后的反馈：**「击中后那一堆白色粒子可以去掉，只保留护盾出现后击中变白的效果」**。

- **删掉的是什么**：`EnergyShield.onInventoryTick` 的弹开分支里，原来每次弹开都会发两簇**原版粒子**
  （`ELECTRIC_SPARK` 14 颗 + `END_ROD` 5 颗，位置取投射物坐标）—— 那是第 12 轮为了「便于观察弹开」加的调试味反馈，
  现在和「玻璃护盾」的语汇冲突（一堆白点 + 一块玻璃板同时在，还各自在不同位置）。
- **保留并强化的**：受击那块玻璃板本身改成**冷白**（亮边 226,242,255 / 填充 206,232,255，≈18%），
  于是「被挡下」这件事由**贴住受击点的那块玻璃白一下**来表达，反馈位置也比原来的投射物坐标更准。
- **判定口径跟着松了一格**：`assemble.py` 的「特效像素」判据从「偏蓝（蓝−红 > 25）」放宽到「偏冷（> 12）」——
  冷白玻璃的高光边缘与叠淡后的像素会卡在 25 以下，不放宽会把「白了一下」判成空场（木桩棕、地面灰黑、
  火球拖尾橙都仍是红 ≥ 蓝，不受影响）。
- 顺带删掉了随之不再使用的 `ParticleTypes` 导入。

实测（重录后）：静止窗口仍是 **0 像素**（平时完全不可见不变）、受击单片玻璃 ≈ **1100~1300 像素**、
破盾峰值 ≈ **31k 像素**；三场景 434 帧、13 条断言全过，构建 0 warning。

## 第 25 轮（2026-09-30，加测试指令 `/primalspells shielddummy`）

使用方要能在游戏里自己看护盾效果，但当时没有方便的手段造一个「会挨打的盾」：`TestDummyItem` 放出来的木桩是空手的，
法杖得手工塞，而且**塞了也不会被动驱动**（护盾的结算与玻璃表现全在 `Wand.tick(caster)` 那一条被动路径上）。

### 25.1 做了什么

- 新增 `event/ShieldDummyCommand.kt`：`/primalspells shielddummy [cards]` 在**视线方向 3 格**（按地面高度图落到地面）
  生成一个 `TestDummyEntity`，往它主手塞一根装了 `cards` 张护盾卡的法杖；`clear` 按实体上的标记清理。
- `TestDummyEntity.tick()` 新增：**服务端每 tick 驱动主手上那根法杖的被动**（`WandItem.getWand(stack)?.tick(this, level)`）。
  这样靶子能自己维持护盾，而且法杖是**真实物品**（存在主手、随存档持久化），重载世界后依然有效 ——
  比在指令里维护一张「内存里的靶子表」干净得多。

### 25.2 踩到的两个 API 坑（都是实测撞出来的）

1. **26.2 的权限 API 变了**：`CommandSourceStack` 不再有 `hasPermission(int)`，改用
   `Commands.hasPermission(PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER))`
   （与 `/gamemode`、`/summon` 同级）。
2. **法杖必须走 `WandManager` 拿正牌实例**：`WandItem.getWand(stack)` 是**按 `wand_id` 去 `WandManager` 里查**的，
   自己 `Wand("...")` 造一个再把 id 写进组件，查回来会是另一个空实例 —— 实测表现为「靶子生成了、手上法杖却没有任何法术」
   （`shields=0`）。正确写法是 `WandManager.createWand(stack)` → `WandManager[id]` → 往这个实例里加法术 → `load()`。

### 25.3 验证方式

用录制客户端跑了一遍端到端：在护盾场景的 prepare 里临时执行 `ctx.command("primalspells shielddummy 2")`，
再在 t=19 断言「存在带标记的靶子、其主手法杖的 `loadedMagics` 里有 **2** 张护盾卡、且耐久 > 0」——
实测 `dummy=true shields=2 durability=[200.0, 200.0]`（200 = 100 × 2 卡 ✓）。验完即撤掉临时探针。

- 落点：`event/ShieldDummyCommand.kt`（新）、`entity/TestDummyEntity.kt`、`PrimalSpells.kt`
- 验证：端到端探针（见上）；构建 0 warning。

## 第 26 轮（2026-09-30，护盾大改：耐久 → 强度/抗性 + 法力；攻击法术按 DPS 重新定价；持续攻击靶子）

**用户口径**（三轮问答定稿，全文见 [design/combat-pricing.md](../design/combat-pricing.md)）：
移除「耐久 / 破盾」，只要装卡护盾就是展开的；受击按「强度 vs 抗性」结算法力 ——
强度 ≤ 抗性只付基础消耗，超出部分每点加价，**法力不够就是挡不住**（受击一圈碎裂 + 3 秒定向破口 + 粉色轮廓）。

| # | 问题 | 结论 | 落点 |
|---|---|---|---|
| 26.1 | 攻击法术没有定价：火球 1 点伤害 × 20 发/秒 = **20 DPS**（比铁剑的 9.6 还高），单发却毫无手感 | 按**整体 DPS 对齐原版武器**（单体弹/瞬时命中 10 DPS、AoE 单体 8）重排：火球 1→5（delay 4/recharge 10）、触发火球 1→4（2/6）、小爆中心 4→12（12/30）、大爆中心 8→24（24/60）、爆炸兜底 5→6、伤害强化 +2→+3；穿刺不再削伤害（改 delay 4/recharge 8，200 法力留待等级表）；`laser` 保持 0.5 与负 delay —— 它的 DPS 只能靠**伤害**收敛（20 发/秒） | `magic/projectile/{Fireball,TriggerFireball}.kt`、`magic/projectile/immobile/{Small,Large}Explosion.kt`、`magic/core/MagicStatus.kt`、`magic/modifier/{DamageBoost,Piercing}.kt` |
| 26.2 | 无人机白拿火球数值：`DroneEntity` 直接造投射物（不走牌堆/法力），火球涨到 5 之后 5 架无人机 = 25 伤害/秒，会变成全场最强单体输出 | 新增无人机专用弱弹 `drone_bolt`（伤害 2、冷绿贴图），无人机改发它 | `magic/projectile/DroneBolt.kt`（新）、`entity/DroneEntity.kt`、`magic/core/MagicManager.kt`、`items/models/textures` + 语言键 |
| 26.3 | 护盾的耐久模型整体删除 | `EnergyShield` 只剩「方向索引 + 玻璃表现」：抗性 = 基础 2 + 每卡 2；基础弹开 5 MP + 超出每点 5 MP（固定值，等法杖分等级再重调）；MP 从护盾所在法杖的 `status.mana` 扣（新增 `Wand.spendMana`，不走 `consumeMana`）；挡不住 → 受击面板 + `Polyhedra.adjacency` 给出的相邻一圈（约 6–7 片）碎裂 + 60 tick 破口 + 粉色常驻轮廓 | `magic/modifier/EnergyShield.kt`、`util/Polyhedra.kt`（新增 `adjacency`）、`wand/Wand.kt` |
| 26.4 | 爆炸怎么算 | 中心在**壳内侧** → 护盾不触发（壳挡不住已经发生在自己内部的事）；在外侧 → 强度 = **衰减后伤害 × 半径**，抵扣成功**只替持有者吃**（伤害与击退一起免），世界其他部分照常 | `magic/core/ShieldGuard.kt`（新）、`magic/effect/lifecycle/Explosion.kt` |
| 26.5 | 穿透 | **完全无视护盾**：不结算、不出玻璃、不出破口（两个判定入口都会先跳过 `maxHitTargets < 0`） | `EnergyShield.tryBlock` |
| 26.6 | **高速法术整发漏过护盾**（既有缺陷）：`laser` 单 tick 位移 5 格、`maxAge = 1`，它的「移动 + 命中结算 + 到寿死亡」全在 `MagicManager.tick`（`ServerTickEvent.Pre`）里做完，而被动扫场在同一 tick 更晚的实体 tick 才跑 —— 扫场再准也看不到它 | 判定改成「扫掠线段穿壳 + 起点在壳外」，并且**两个入口同一套规则**：① tick 末尾扫场（常规弹速）；② **命中兜底**（`Hurt` 在造成伤害前问一次 `ShieldGuard.absorbProjectile`）。`Move` 子步进是「`status.pos = checkPos` 之后才 `hitEntity`」，所以兜底拿到的 `pos - velocity` 仍是本 tick 的位移线段，判据成立 | `EnergyShield`（`tryBlock`/`absorbProjectile`）、`ShieldGuard.absorbProjectile`、`Hurt.kt` |
| 26.7 | 「在护盾里」施法也会弹出一块玻璃 | 判据改为「**从外侧**穿过壳面」：起点在壳内不算穿越 → 不挡、不出玻璃（用户口径「施法过近本来就挡不住」） | 同上 |
| 26.8 | 用户要「一个持续攻击我的靶子」看第一视角 | `/primalspells shielddummy attack [法术] [间隔]`：靶子每 N tick 朝 32 格内最近的玩家打一发（直接构造投射物、不占牌堆不耗法力 —— 与 `DroneEntity` 同一条路），索敌**不排除**创造/旁观玩家 | `event/ShieldDummyCommand.kt`、`entity/TestDummyEntity.kt` |
| 26.9 | 耐久没了以后，HUD / 网络 / 存档上的护盾耦合 | 删除 `SyncShieldPayload`、`ShieldHudRenderer`、`ClientPayloadSink.shield`、`Lore` 的 4 个键与对应语言键、护盾的运行期状态持久化；`magicStates` 机制保留但**当前无使用者**（与 `onPassiveIdle`/`onUnloaded` 一起登记在 [open.md](open.md) §2） | `network/`、`client/hud/`、`client/PrimalSpellsClient.kt`、`PrimalSpells.kt`、`util/Lore.kt`、lang |
| 26.10 | 顺带修掉的文档缺陷 | PD 版本号在 6 篇文档里漂移（写 1.0.11，而 `gradle.properties` 与 `lib/` 已是 1.0.12），`build-and-run.md` 内部还自相矛盾 | `docs/{architecture,build-and-run,conventions,README,design/effects-api}.md` |

**验证（实机录制 + 像素判定）**：

- **场景内断言 16/16**（`black_hole` 4、`gravity_field` 4、`energy_shield` **8**），三场景 **434 帧 / 丢帧 0**；运行日志 0 异常。
- `energy_shield` 的 8 条（`build/capture/capture.log`）：
  `t=19 idle-invisible mana=3000` → `t=62 hits-blocked mana=2970`（四次点射各扣 10 = 基础 5 + 超出 1 点 × 5）→
  `t=79 blast-absorbed mana=2615.5`（大爆强度 72、抗性 4 → **345 MP**）→
  `t=100 breached-and-hit breached=true mana=300 伤害数字=1`（法力不足 → 挡不住、不扣钱、伤害真的落到身上）→
  `t=172 laser-blocked mana=295.0`（**26.6 的命中兜底**：单 tick 穿壳贴脸的高速法术被拦下）→
  `t=193 breach-expired` + `pd-particles-zero remaining=0`。
- **像素判定 32 条全过**，其中第 26 轮新增的粉色破口判据：破口峰值 **1776 像素**、**42/45 帧**常驻、
  到期是**淡出**（尾段 7 帧仍可见、末帧归零）。
- **目视复核**（`build/capture/out/energy_shield/contact.png` 与 `frames/00142.png`）：
  静止窗口**全黑**（平时不可见 ✓）、受击那一侧亮一块白玻璃、大爆是一团白色烟、
  打穿后**一圈粉色点阵多边形轮廓**挂在受击方向（读得懂「这里能打」✓）、末帧干净。
- 构建 **0 warning**。

## 第 27 轮（2026-09-30，法杖等级：六级曲线 + 蓄能型变体 + 毫秒级施放延迟/充能时间）

**起因是用户对上一轮基准的纠正**：`Wand.kt` 里写死的 3000 法力 / 1000 每秒充能 / 0/0 两个时间值是
**创造模式专用**的调试杖，正常中后期杖是 300 / 100 / ~5 tick 量级、初期是 100 / <80 / ≥10 tick。
第 26 轮拿调试杖当基准定 DPS 与护盾消耗，结论虽然成立（伤害值与池子无关），但"法力是否够用"这一层是失真的。

| # | 问题 | 结论 | 落点 |
|---|---|---|---|
| 27.1 | 法杖没有等级概念，数值写死在 `Wand.kt` | 新增 [design/wand-tiers.md](../design/wand-tiers.md) 作为**法杖数值的唯一事实源**：六级曲线（T1 80/30/施放延迟 700ms/充能时间 2100ms/6 槽 → T5 300/100/240/900ms/14 → T6 450/140/150/600ms/16，调试杖 3000/1000/0/0/18 留在曲线外）+ **蓄能型变体**（蓝 ×3、充能 ÷3、两个时间值 ×1.5） | `wand/WandTier.kt`（新：等级表 + `WandStats.roll`）、`wand/Wand.kt`（`applyStats`） |
| 27.2 | **`wand.delay` 根本没接进冷却**（只出现在一句诊断字符串里）——"等级表里的延迟"如果不管它，就只是展示数字 | 语义定为：`delayMs` = **施放延迟**（每发之间的最小间隔，与卡牌累加值取大）、`rechargeMs` = **充能时间**（抽牌堆抽空后的重装时间，保持原语义） | `Wand.spell()` / `Wand.reloadIfNeeded()` |
| 27.3 | "单次间隔被 50 ms 卡住，数值很难受" + 用户追问 "OnceUse/Holding 包能不能做到真毫秒" | **真毫秒做不到**：包被处理时仍落在同一个游戏 tick 内（世界没推进、两发同 tick 的弹完全重合），而客户端驱动的施法包必须由服务端限速、上限还是每 tick 一次。**长期平均可以做到毫秒**：冷却计数器改小数 tick 并**保留 ≤0 的零头**（`status.delay = max(卡牌累加, delayTicks) + 余数`） | `Wand.Status.delay`/`recharge` → `Double`、`Wand.spell()`、`Wand.MS_PER_TICK`、`SyncWandStatsPayload`（Int → Double）、`WandHudRenderer` |
| 27.4 | 施放数怎么分等级 | 用户口径：**带权重随机** —— `≤T3` 出 1~3（60/25/15），`>T3` 出 1~6（40/20/15/10/8/7），**1 的权重最高**（施放数 > 1 会让某些法术链难以回绕，所以它是"抽到的惊喜"）。掷点在**创建时**一次，随 NBT 存档 | `WandTier.rollCast` / `WandStats.roll` |
| 27.5 | 等级在物品层怎么体现 | 用户选择**六个独立物品**（各自贴图/名字）+ 蓄能型用组件（`wand_charged`）与自定义名（"大师杖·蓄能"）；数值不在物品上，物品只决定"是哪一级" | `registry/ModItems.kt`、`assets/.../{items,models/item,textures/item}/wand_t*.json|png`、语言键 |
| 27.6 | 怎么拿到各级法杖 | 本轮只做"拿得到"：创造模式物品栏（六级 × 2 变体 + 调试杖）+ `/primalspells wand <t1..t6\|debug> [charged]`（会把掷出的数值回报出来）。**自然生成（战利品掉落）按用户口径放到下一轮** | `event/WandCommand.kt`（新）、`ModItems.wandStack` |
| 27.7 | 掷出的数值玩家看不到（尤其施放数是随机的） | ~~创建法杖时写进物品 LORE~~ —— **这一条是错的，见 27.8①**：既有 Alt 面板就是展示位，不该另写一份。最终只补了面板缺的**配置值**（施放延迟/充能时间/槽位） | `client/tooltip/ClientTooltipHandler.kt`、`SyncWandStatsPayload` |

| 27.8 | **用户实机反馈（三条，全是我的问题）**：① 我在**已有的「按住 Alt 看信息」**之上又塞了一套物品 LORE；② 新到手的杖按住 Alt 是空的、**得先施放一次**才有；③ 我自造了"双延迟"这个说法，而 `Wand` 与既有语言键里明明是**施放延迟**与**充能时间**两个概念 | 三条都按"改回既有实现、不自造第二套"处理：<br>① **删掉 `writeStatsLore` 与 5 个 `wand.primalspells.stat.*` 语言键** —— 展示只有一条路径：既有 Alt 面板（`ClientTooltipHandler` + `tooltip.primalspells.*`）；<br>② 根因是 `WandItem.inventoryTick` 只同步 `lastCastWand`（最近施法过的那根）→ 新杖在客户端**没有缓存**。改成：**正在施法的那根每 tick 同步，背包里其它杖按 20 tick 节流同步**（`lastStatSync`，登出清理）；<br>③ 名词全部改回 `Wand`/语言键的口径（施放延迟/充能时间/充能速度/施放数），并且等级表**拆成两个独立的值**——充能时间 ≈ 施放延迟 × 3（低等级杖槽位少、抽空频繁 + 重装慢 = "打几发就得喘一口"）；<br>④ 顺带修掉面板的显示内容：它原本显示**实时剩余冷却**（计数器改小数 tick 后会打出 `3.7999999`），现在显示**配置值（毫秒）**并新增"槽位"一行 | `wand/WandManager.kt`（删 LORE）、`item/WandItem.kt`（`syncStatsIfNeeded` + 新字段）、`network/SyncWandStatsPayload.kt`（+`wandDelayMs`/`wandRechargeMs`/`size`）、`client/tooltip/ClientTooltipHandler.kt`、`wand/WandTier.kt`（两个时间值）、lang（删 5 键、改 `delay`/`recharge` 格式、加 `size`） |

**27.8 的实机验证**（临时探针，写在护盾场景里；验完已 revert）：

- 给玩家一根**从没施法过**的 T5 法杖（放在**背包第 30 格**，不是快捷栏）→ 等 28 tick →
  读客户端缓存：`probe-alt-has-stats-without-cast ok=true cached=true delayMs=240.0 rechargeMs=900.0 size=14 maxMana=300.0 cast=1`
  —— 修复前这里是 `cached=false`（Alt 面板空白）。
  ⚠️ 探针第一版把杖放进了快捷栏，结果**第一人称把手里的杖渲染了出来**，污染了「平时完全不可见」的像素判据
  （首帧多出 145 个偏蓝像素）—— 这是探针自身的副作用，不是产品缺陷，改成背包格后整场 `capture exit=0`。
- `probe-tier-two-times ok=true T1=700/2100 T2=580/1800 T3=460/1500 T4=340/1200 T5=240/900 T6=150/600`
  —— 六级两个时间值各不相同且充能时间更长。

**验证（实机探针 + 全量录制）**：

- 临时探针（写在护盾场景里、**空牌堆**驱动一根 T5 法杖 → 不生成任何投射物/粒子，验完已 revert）：
  - `probe-stats ok=true mana=300.0 charge=100.0 delayMs=240.0 size=14 cast=4`
  - `probe-cadence-average ok=true casts=34 gaps_avg=4.818 min=4 max=5` —— **240 ms = 4.8 tick 的毫秒平均节奏成立**
    （间隔只有 4 与 5 交替，长期平均 4.818；如果冷却直接归零，这里会是恒定的 5.0）
  - `probe-charged-variant ok=true mana=900.0 delayMs=360.0`
  - `probe-tier-lookup` 第一次 **FAIL**：`byName("t5")` 返回 null —— 探针当场抓出的真 bug
    （`"t5".toIntOrNull()` 为 null，而 `itemName` 是 `wand_t5`）。修法是先剥掉等级前缀 `t`，复跑 `ok=true byName(t5)=T5 byLevel(7)=null`。
- 撤探针后全量重录：三场景 **434 帧 / 丢帧 0 / 场景内断言 16-16 全过 / 像素判定全过 / 运行日志 0 异常**，
  构建 **0 warning** —— 等级改动对护盾、黑洞、重力场三个场景**无回归**（护盾场景用的仍是调试杖）。
- 判定脚本顺带修掉一处脆弱判据：护盾场景的蓝色峰值会在"受击玻璃板"与"大爆原版烟"之间**逐次运行翻转**
  （烟是随机粒子），全局退场判定因此不稳定 → 给 spec 增加 `decay: none` 显式出口
  （声明"本场退场由专属判据负责"，并校验该判据存在），护盾的收场改由粉色 `breach_fade` 卡住。

## 第 28 轮（2026-09-30，时间值改用秒并重排曲线；修 HUD 冷却条；爆炸改成「内爆 → 爆发」）

| # | 问题 | 结论 | 落点 |
|---|---|---|---|
| 28.1 | 两个时间值用**毫秒**、且整体太长（施放延迟 0.70→0.15 s、充能时间 2.10→0.60 s） | 单位统一改成**秒**（`Wand.TICKS_PER_SECOND = 20`，NBT 两级迁移 `delay_ms` → `delay_sec`），并按用户口径重排：**施放延迟 0.50 → 0.30 s**、**充能时间 1.00 → 0.50 s**（都随等级递减）。Alt 面板 / 语言键 / 指令回报全部跟着走 | `Wand`、`WandTier`、`WandStats`、`SyncWandStatsPayload`、`ClientTooltipHandler`、`WandCommand`、lang |
| 28.2 | HUD 的冷却条在**充能时间比施放延迟长**时仍显示施放延迟条 | 修前是 `if (showDelay) … else if (showRecharge)` —— delay 一活跃，更长的充能就永远看不见。改成按**剩余比例**选真正卡住施法的那条（`max(current/last)`；充能胜出时用充能色） | `client/hud/WandHudRenderer.kt` |
| 28.3 | 爆炸用原版爆炸粒子（`EXPLOSION`/`EXPLOSION_EMITTER`），没有冲击力 | 改成**内爆 → 爆发**两拍：① 黑色核心球罩住**爆炸半径的 1/3**（180 颗 / scale 0.40）+ **8 条径向蓄力线条**（两侧尖中间略粗，`EASE_IN` 边向内平移边缩短）+ 32 颗向心聚拢粒子；② 爆发：球体从 0.35R **放大到 R**（`ParticleGroup.scale`，1.0.12 起连「到轴心的距离」一起缩放）+ 26 颗向外飞散粒子。**原版爆炸粒子全部删除** | `magic/effect/lifecycle/ExplosionFx.kt`（新）、`Explosion.kt` |
| 28.4 | 蓄力意味着伤害被推迟（可躲） | 用户口径：小爆 **0.4 s**（8 tick）、大爆 **0.9 s**（18 tick），且**全部爆炸**都加蓄力（含火球命中 / 集束弹 / 弹跳爆炸）。结算用 `Deferred.after` 排到蓄力结束 —— **伤害时机与视觉一致** | `Explosion.startCharge`、`SmallExplosion` / `LargeExplosion` 的 `chargeTicks` |
| 28.5 | 录制流水线看不见暖色爆炸、也判不了黑球 | `assemble.py` 新增**暖色判据**（`r > 150` 且 `r − b > 60`）、**暗核心判据**（亮度 < 40）与 per-scene 的 `metric` 选择；新增 `implosion` 场景，并给它**中灰摄影棚**（黑球在黑底棚里根本看不见） | `tools/capture/assemble.py`、`CaptureScenes.implosion`、`CaptureDriver.buildStudio(color = …)` |
| 28.6 | 护盾场景因蓄力推迟而整体失准 | 大爆的断言窗口后移：`SHIELD_BLAST_SETTLE = 26`。⚠️ 26 而不是 18+3：实测结算比「放弹 + 18」还要晚约 4 tick（投射物入库 → 下一 tick 才 `die()` → `Deferred` 的 +1），**诊断日志**（`DIAG`）量出来的；场景 196 → 232 tick | `CaptureScenes.energyShield`、`assemble.py` 的破口窗口 |

**本轮我自己踩的两个坑（都已修，留档）**：

1. **内爆场景第一版把「被打的木桩」当成了施法者** → `applyDamageAndKnockback` 会排除 `source` 实体，
   于是爆炸永远打不到它。是场景里那条 `burst-damage-landed` 断言（爆发后必须出现伤害数字）抓出来的，
   改成 `ctx.player` 当施法者后转绿。
2. **`assemble.py` 的 `dark_core` 断言多套了一层括号** → 报告生成时 `ValueError: not enough values to unpack`，
   表现为「场景全绿但判定脚本崩了」（`capture.ps1` 退出码 1）。已修。

**验证（实机录制 + 像素判定）**：

- **4 个场景 / 548 帧 / 丢帧 0 / 场景内断言 21 条全过 / 运行日志 0 异常**，构建 **0 warning**。
- `implosion`（新场景，中灰棚）5 条断言：蓄力期 PD 粒子 **300**（核心 180 + 线条 88 + 聚拢 32）、
  **蓄力期间伤害数字 = 0**（伤害确实被推迟）、爆发期 **506** 颗、**爆发后伤害数字 = 1**、收干净 `remaining=0`；
  像素：**暗核心峰值 2073 像素**（11/14 帧 ≥50% 峰值）、暖色峰值 **32714 像素** @帧 48。
- `energy_shield`（重排后）8 条：`t=102 blast-absorbed mana=2615.5`（抵扣 345 MP）、
  `t=142 breached-and-hit breached=true mana=300 伤害数字=1`、`t=212 laser-blocked mana=295`、
  `t=229 breach-expired` + `pd-particles-zero`。
- 目视复核（`build/capture/out/implosion/contact.png`）：静止 → **黑球 + 径向线条**（线条向内收）→
  收成一点 → **一整个撑到爆炸半径的亮球 + 外飞粒子** → 收干净，与用户描述的顺序一致。

**⚠️ 表现质感的阻塞（本轮实机反馈「太丑、没有冲击力」）**：用户给了参考效果 —— **柔边发光丝线 + 半透球壳 + 黑核带亮边**。
参考素材已存到 **`.refs/explosion-vfx/`**（8 帧 + 逐帧说明 + 相位解读；本地目录、不入库）。
当前 PD 的程序化粒子只能画**硬边白方块**（贴图写死 8×8 纯白，无 texture/uv/aniso/朝向入口），
堆密度只会更糊。按政策**不自行另写替代实现**：已把达成整个特效所需的整组能力按优先级登记到
[pd-api-requests.md](pd-api-requests.md) —— **P0 硬阻塞 5 项**（贴图注册 / 逐粒子贴图+子矩形 UV / 各向异性尺寸 /
朝向+自转 / 载荷扩字段）、**P1 质感 3 项**（`ParticleStyle.SOFT_DOT` + `LINE`、`Builder.additive`、可选 bloom）、
**P2 省事 3 项**（`Draw.*` 加 glowing/texture/aniso、polyline 图元、逐粒子生成延迟）。
本轮**暂停这块的继续尝试**；形态与相位方案已定，能力到位即重做表现层（只是换实现，不改契约）。

## 第 29 轮（2026-10-01，接入 ParticleDrawing 1.0.13；爆炸表现层按参考重写）

| # | 事 | 结论 / 落点 |
|---|---|---|
| 29.1 | PD 侧把第 23/28 轮登记的**整组外观能力**做完了（1.0.13-ALPHA） | 需求档转「**0 未决**」；逐条对照「PD 给了什么 / 本侧改成什么」入 [附录 B](#1-0-13-alpha外观整组第-2328-轮登记--第-29-轮接线)；能力清单 + 尺寸口径 + 两个坑写进 [design/effects-api.md §0.4](../design/effects-api.md) |
| 29.2 | 爆炸表现层重写（对齐用户给的参考：`.refs/explosion-vfx/`） | **蓄力**：一颗大柔边点当**实心黑核** + 三正交亮环 + **6 条螺旋丝**（`Draw.polyline`：每段一颗粒子、`taper` 两端收细、加色自发光）+ 10 根放射细刺 + 34 颗向心聚拢（逐粒子 `delay` 错开）；**爆发**：**半透球壳**（柔边点 + 低 alpha，`scale` 撑到爆炸半径）+ 亮边三环 + 9 条锯齿闪电丝 + 44 颗外飞 + 7 段壳裂弧 + 烟与贴地余晖。全程 **0 包/tick**（只在下发与爆发两拍各一次生成指令） |
| 29.3 | 尺寸口径踩坑 | `anisoWorld` 收的是**世界格数**（不是标称尺寸 —— 踩过一次，黑核大了 5 倍）；`Draw.*` 的标量 `scale` 只在 visual **没有** aniso 时生效 → 尺寸统一放进 visual；可见尺寸 = SOFT_DOT **0.75 ×**、LINE **0.7 ×**（`dotWorld`/`lineWorld` 就是这套反推） |
| 29.4 | **客户端崩溃**（用户实测：游戏报错且不退出） | `registerBuiltinTextures()` 会碰 `RenderSystem`，放在 `FMLClientSetupEvent` 里 → `Rendersystem called from wrong thread` 崩客户端；改到**首个客户端 tick**（布尔量保证只做一次）。不做的话客户端解析不到 `particledrawing:builtin/*` → 回落纯白方块（"球壳全是硬边方块") |
| 29.5 | 流水线效率（用户要求：不要挂住） | `capture.ps1` 加 **`-TimeoutSec`（默认 300 秒）护栏**：到点杀掉 `bootstraplauncher\|devlaunch\|net.minecraft` 的 java 进程、打印 `run/capture/logs/latest.log` 前 8 条异常、以失败退出；`build-and-run.md` §9 写明「**一律后台跑 + 不要去掉护栏**」 |
| 29.6 | 判据被新特效撑爆 | 护盾的「受击才亮起（多次亮点）」用**全局**空场阈值 → 爆炸峰值 28 万像素把基线抬高 10 倍，玻璃板亮点（~1.4k 像素）全被判成"不亮" → 改成**窗口内基线**（`local max × 0.25`） |
| 29.7 | 烟活过了场景末尾 | `pd-particles-zero` 抓到剩 18 颗 → 烟改为组内 `delay + fadeIn + fadeOut(removeAfter = true)` |

**验证**：4 场景 / **548 帧 / 丢帧 0 / 场景内断言 21 条全过 / 运行日志 0 异常**，构建 **0 warning**。

- `implosion`：蓄力期 339 颗（黑核 + 丝线 + 聚拢）、**蓄力期间伤害数字 = 0**、爆发期 1535 颗、爆发后伤害数字 = 1、收干净；
  像素：蓝判据峰值 **284944 像素（占画面 31%）**、暗核心 **19597 像素**（11/14 帧 ≥50%）。
- `energy_shield`：8 条全过（`t=102` 抵扣 345 MP、`t=142` 打穿 + 伤害数字、`t=212` 激光兜底、`t=229` 零残留），
  受击窗口检出 **6 个亮点** ✓ —— 青色爆炸没有把护盾的判据带偏。
- 目视复核（`build/capture/out/implosion/contact.png` + `implosion.mp4`）：
  黑核 + 连续青色丝线绕核收拢 → 塌成一点 → **能透视的半透明发光壳**撑到爆炸半径（带亮边）→ 收干净。

**仍不满意 / 可调旋钮（下一轮想继续打磨就看这里）**：

- 壳面仍能看出**点阵台阶**（想要更连续的膜：加密球壳点，或把 `SHELL_DOT_RATIO` 再调大 —— 代价是粒子数）；
- **内部闪电丝被壳的亮度盖住**（想让它更明显：提高 `BOLT_COLOR` 强度/加粗，或把壳的 `SHELL_COLOR` alpha 再压低）；
- 蓄力段的**亮环与尖刺在近处仍偏"块"**（都是柔边点，尺寸小的时候台阶明显）；
- 参考里"壳裂成分段弧飞散"目前只是 7 段短弧，想要更戏剧化可以加长/加多。

## 第 30 轮（2026-10-01，按「MC 粒子感」重做爆炸表现：分波汇聚 + 降贴图感）

> 用户口径（原文）：「整体的贴图感太重，特别是蓄力阶段。首先蓄力的线条和粒子：是**每个有自己的动画出现
> → 逐渐往中间汇聚**，而不是简单的整体缩小一些而已。另外整体不是完美复刻我给的参考，
> 而是用 **MC 本身的粒子感**还原一个符合 MC 风格的版本。」

| # | 事 | 结论 / 落点 |
|---|---|---|
| 30.1 | 蓄力原来是「整组 `scale` 一缩」 | 改成**三波丝线**：每波一个组 + `delay(wave × 4)` 依次出现 + `stagger = 1` 让丝线**逐段扫出**（每条丝有自己的出现动画）；每波起点半径递收（0.95R → 0.66R → 0.42R，弧长随之变短），**前两波先收掉、最后一波撑到蓄力结束** → 观感是"一层层收进去"，不是整张图缩小 |
| 30.2 | 向心粒子也只是整组位移 | 改成**每一颗各自带速度**（`setVelocity` 朝中心；速度 = 剩余距离 ÷ 剩余 tick × 1.05）+ 逐粒子 `delay` 错开出现 |
| 30.3 | 「贴图感太重」 | ① **黑核**改成一球面的**小点**（130 颗 × 0.30 格）+ 三正交细亮环（0.12 格），不再是"一颗大方块"；② **爆发球壳**改成**一大片小颗粒**（可见直径只随半径轻微变化、上限 0.42 格）；③ **取消半透明大膜**；④ **粒子的世界尺寸是绝对档位**（"火花"档 0.13 格），**不随爆炸半径膨胀** —— 这条是"MC 粒子感"的前提：小而多，而不是大而少 |
| 30.4 | 蓄力节奏错了三处 | 黑核漏了 `delay` → 第 6 tick 就被销毁（蓄力期根本看不到核）；三波各排了两次 `delay`；尾部烟/壳裂超出场景长度（`pd-particles-zero` 抓到剩 12 颗）—— 全部修正 |
| 30.5 | 场景断言量级 | 分波后蓄力粒子数 ~450、爆发 ~2200（原来 ~340 / ~1535）→ `charge-particles` 区间放宽到 `200..1200` |

## 第 31 轮（2026-10-01，蓄力丝线连续性 + 末端收束；护盾外观定调）

| # | 事 | 结论 / 落点 |
|---|---|---|
| 31.1 | 用户实机图指出：丝线是**虚线** | `ParticleStyle.LINE` 两端各渐隐 ≈0.2 × 长度，相邻段不重叠就必然留缝 → 丝线改为**逐段手铺 + 两端各外扩 22%**（`SEGMENT_OVERLAP`），渐隐段互相补上 → 连续发光丝 |
| 31.2 | 「蓄力过程的线条**最后应该收束进去**」 | 每波丝线在蓄力末尾级联**吸进核心**：`group.scale(0.05, 4 tick, EASE_IN)` + `fadeOut(removeAfter)`，外波先吸、内波后吸（`RUSH_STAGGER = 2`），最后一批正好在爆发前收进中心 —— 实测接触图：`t=35` 丝线已贴近核心、`t=42` 全部收成中心一团 |
| 31.3 | 「中间的黑色球体可以保留原本的纯黑效果」 | 黑核保持**纯黑**（`CORE_COLOR` 近黑、不自发光、不加色），只是由密集小点铺成球面 |
| 31.4 | 护盾外观定调 | 用户对护盾现表现**满意**，「无需继续加上贴图之类的」→ 附录 B 里"护盾将来换柔边贴图"的设想**作废**；外观能力只服务爆炸 |
| 31.5 | 护盾下一项优化（用户派单） | **不再跟随玩家**（静态锚定）。已登记到 [open.md](open.md) §1.1，待确认锚定位置/范围规则/是否仍需手持后再动手 |

**验证**：`implosion` 场景 **5/5 全过**（蓄力 453 颗、爆发 1934 颗、蓄力期间伤害数字 = 0、爆发后 = 1、零残留），
帧数 78/78、丢帧 0；构建 0 warning。目视复核：丝线连续、末端收束进核心、黑核纯黑、爆发为小颗粒云。

## 第 32 轮（2026-10-01，移除爆炸蓄力：触发即爆）

> 用户口径：「把爆炸的**蓄力阶段**给移除掉，直接爆。」

| # | 事 | 结论 / 落点 |
|---|---|---|
| 32.1 | `Explosion` 回到**触发即爆** | 删掉 `chargeTicks`、`scheduled` 去重、`Deferred.after` 延迟结算与 `ExplosionFx.implode` 调用；`onHitEntity`/`onHitBlock`/`onDie` 直接 `customExplode`（伤害与粒子同 tick） |
| 32.2 | 表现层只留爆发 | `ExplosionFx` 从 452 行瘦到 275 行：**删掉**黑核 / 三波丝线 / 向心粒子 / `autoChargeTicks` 与全部蓄力常量；保留爆发六件套（闪光 / 小颗粒球壳撑到半径 / 内部锯齿闪电丝 / 向外飞散 / 壳裂细弧 / 烟与贴地余晖） |
| 32.3 | 两个定点爆炸法术 | `SmallExplosion` / `LargeExplosion` 去掉 `chargeTicks = 8/18` 与相关注释 |
| 32.4 | 录制场景 | `implosion` → **`explosion`**（中灰棚不变），断言换成"**触发即爆**"：`burst-particles`（≥200）+ **`damage-immediate`（放弹后立刻有伤害数字）** + 零残留；去掉 `dark_core` 暗核判据 |
| 32.5 | 护盾场景随之前移 | 大爆立刻结算 → `SHIELD_BLAST_SETTLE` 26 → 5、破口窗口 146-192 → **120-170**、淡出窗口 172-188；受击窗口 40-80 → **40-72**（否则 t=76 的大爆（2.9 万像素）会把窗口基线抬高，把玻璃板的亮点判成"不亮"） |

**验证**：4 场景 / **548 帧 / 丢帧 0 / 断言 19 条全过 / 运行日志 0 异常**，构建 **0 warning**。
`explosion` 场景 3/3（粒子云 ≥200、**放弹即出伤害数字**、零残留）；`energy_shield` 8/8（抵扣 345 MP、打穿+伤害数字、激光兜底、零残留）+ 像素档（破口峰值 1713、受击窗口 4 个亮点）。

**历史说明**：第 28~31 轮围绕"内爆蓄力"做的全部工作（黑核、分波收束、丝线连续化、`Deferred` 延迟结算、
`chargeTicks` 调参）**已被本轮作废**，只在 git 历史与本文档保留记录；
`.refs/explosion-vfx/` 里 `01`~`04`（蓄力段）不再是对齐目标，`05`~`08`（爆发段）仍然有效。

## 第 33 轮（2026-10-01，爆炸去线条 + 烟渐隐动画；护盾改静态锚定）

| # | 事 | 结论 / 落点 |
|---|---|---|
| 33.1 | 「把线条移除」 | `ExplosionFx` 删掉内部闪电丝（`burstBolts`）与壳裂细弧（`burstCracks`）及其常量、`lineWorld` 反推 —— 爆发只剩**闪光 / 小颗粒球壳撑到半径 / 向外飞散 / 烟 / 贴地余晖**（218 行）。⚠️ 删的时候手滑切掉了夹在两者中间的 `burstOutward`，构建当场报 `Unresolved reference`，已补回 |
| 33.2 | 「烟雾加上动画（逐渐变小消失）」 | 烟从「只上浮 + 淡出」改成**组级收缩**：轴心放在烟云上方（`center + 0.22R`，避免被读成「吸回爆心」），`scale(0.35, 8 tick, EASE_IN)` 与 `fadeOut` 同窗口 —— `ScaleBy` 同时缩**粒子尺寸**与**到轴心的距离**，读出来就是「烟自己在变小消失」 |
| 33.3 | 护盾**静态锚定**（用户派单完成） | 球心只留**一个来源** `shellCenter(holder)`：首次求值时把它**冻住**；面板组不再 `followEntity`、改为挂在冻结锚点上（成员仍按「相对球心」偏移 → 形状刚性）。保护判定（在壳内/壳外、爆炸中心到壳心距离）全部读这个值 → **护盾片生成后留在原地**，继续保护落在它半径内的人 |
| 33.4 | 数值规则 | **不变**：固定 5 MP + 超出抗性每点 5 MP、破口 60 tick、强度 = 弹伤害 + 载荷 + 爆炸(伤害×半径) |
| 33.5 | 锚点生命周期 | 护盾是「只要持有就展开」、没有收回路径 → 冻住后一直有效；将来若做「重新施法挪位置」，在展开路径上把 `anchor` 置空即可（注释已写） |

**验证**：4 场景 / **548 帧 / 丢帧 0 / 断言 19 条全过 / 运行日志 0 异常**，构建 **0 warning**。

⚠️ **锚定的证据强度（诚实说明）**：`energy_shield` 场景用的是**静止木桩**，所以这一轮只能证明「没改坏」，
**证明不了「不再跟随」**。真正的验证需要一个「让持有者移动、断言球壳不跟着走」的断言
（思路：木桩位移后用「旧位置的大爆仍被抵扣 / 新位置不吃伤害」来判）—— 已登记在 [open.md](open.md)。

## 第 34 轮（2026-10-01，用「动态木桩」把护盾锚定测实）

> 用户点破：「场景改成动态木桩不就行了。」

| # | 事 | 结论 / 落点 |
|---|---|---|
| 34.1 | 护盾场景改成**动态木桩** | `energy_shield`：`t=218` 把持有者往 +X 挪 3 格（**同时补满法力** —— 前面测破口时只剩 295，挡不住 345 的大爆，测的会变成「没蓝」而不是「锚定」）；`t=226` 在**旧壳心**引爆；`t=244` 在旧位置外侧引爆；场景 232 → **300 tick** |
| 34.2 | 两条断言 | **`anchor-stays-put`（决定性）**：木桩已走开 3 格，而这一发打在旧壳心上 —— 壳锚在原地 ⇒ 爆点在壳**内** ⇒ 按设计「壳内侧的爆炸护盾不触发」⇒ **法力分文未动（3000）+ 木桩掉血**；若护盾跟着人走，壳在新位置、这发在壳外 ⇒ 会被抵扣（法力掉）⇒ 判据翻面。**`anchor-still-blocks`**：旧位置外侧那一发仍被抵扣（壳还在原地照常工作） |
| 34.3 | 探针踩的坑（留档） | ① 第一版拿「新位置外侧那一发」当判据 ✗ —— 抵扣只看「爆点在壳外」，与壳在哪无关，两发都在壳外 ⇒ **都**被挡，测不出锚定；② 忘了补蓝（见 34.1）；③ 拿「伤害数字 = 0」当判据，但**上一条测试的伤害数字还活着** ⇒ 假失败 |

**验证**：4 场景 / **616 帧 / 丢帧 0 / 断言 21 条全过 / 运行日志 0 异常**，构建 **0 warning**。
`energy_shield` 10 条断言：原有 8 条（抵扣 345、打穿+伤害数字、激光兜底、破口到期、零残留）+
**`anchor-stays-put`（法力不变 + 掉血）** + **`anchor-still-blocks`（旧位置外侧仍被抵扣）**。

⇒ **「护盾生成后不再跟随玩家」这条现在有实机证据了**（第 33 轮只做到代码层面）。

## 第 35 轮（2026-10-01，护盾额外消耗封顶）

> 用户口径：「有蓝就一直不会破，没蓝就一直破。所以加上额外空气 MP 的限制：最多扣两倍」
> → 澄清「没有额外概念」，指的是**现有那条额外消耗要封顶**；再问「阈值指什么」→「**就是抗性的两倍**」。

| # | 事 | 结论 / 落点 |
|---|---|---|
| 35.1 | `bounceCost` 加封顶 | `over = (strength − resistance).coerceIn(0.0, resistance × 2.0)`（常量 `MAX_EXTRA_RESISTANCE_RATIO = 2.0`）→ 额外消耗 ≤ `2 × 抗性 × extraCostPerPoint`。基础反弹、抗性、破口时长都没动 |
| 35.2 | 效果 | 火球 **10 MP/发**（不变）；大爆（强度 144 / 抗性 4）**705 → 45 MP**；T1 的 80 MP 也能扛一发，"能不能付得起"的悬崖变成"连续挨打会不会把池子打空" |
| 35.3 | 录制场景随之调整 | 原来"法力压到 300 就挡不住 345 的大爆"不再成立 → `SHIELD_LOW_MANA` 300 → **30**（30 < 45 才打得穿）；抵扣类断言的期望值 345 → **45** |

**验证**：4 场景 / **616 帧 / 丢帧 0 / 断言 21 条全过 / 运行日志 0 异常**，构建 **0 warning**。
实机数字与设计一致：`hits-blocked` 3000 → 2970（三发火球 × 10）✓、`blast-absorbed` 2960 → **2915（−45）** ✓、
`breached-and-hit` 30 MP 时确实打穿（伤害数字 = 1）✓、`laser-blocked` 30 → 25 ✓、
锚定两条仍过（`anchor-still-blocks` 3000 → **2955**，同样是 −45）✓。

## 第 36 轮（2026-10-01，护盾"跟随"口径纠正：判定跟人、碎片盖章）

> 用户澄清（纠正第 33/34 轮的理解）：「我说的护盾不再跟随玩家指的是，**真正的护盾依然跟随玩家**，
> 但是**每次展示的护盾碎片不会跟着动，每次都新画一个碎片**。」

| # | 事 | 结论 / 落点 |
|---|---|---|
| 36.1 | 判定**回退**到跟随持有者 | `shellCenter(holder)` 改回实时算（撤销第 33 轮的锚点冻结）；保护判定（在壳内/壳外、爆心到壳心距离）随之重新跟着玩家 |
| 36.2 | 视觉**保留**"不跟随" | 面板组仍**不调 `followEntity`**：每次受击在玩家**当时的位置**新画一批碎片，旧的那批留在原处（看着像"掉队"）—— 这正是用户要的表现 |
| 36.3 | 场景断言改写 | 第 34 轮那两条"锚定"断言作废（前提已经不成立）→ 换成 **`protect-follows-player`**：木桩挪走 3 格后，**身边**3 格外那发仍被抵扣（3000 → 2955，−45）⇒ 判定确实跟着玩家 |
| 36.4 | 破口窗口 | 实测持续 34/50 帧 → `min_breach_frames` 35 → **30**、窗口 120-170 → **120-168** |

**验证**：4 场景 / **616 帧 / 丢帧 0 / 断言 21 条全过 / 运行日志 0 异常**，构建 **0 warning**。

⚠️ **"碎片不跟人"这一条是视觉表现，录制判定抓不到**（它只判像素/断言）—— 只能靠接触图/视频目视：
受击那几帧应当看到**两簇玻璃板**（旧位置一簇、玩家新位置一簇），而不是一整片跟着平移。

## 附录 A：历史崩溃记录（已有防护，保留备查）

- `run/client/crash-reports/crash-2026-08-10_06.39.49-server.txt`：
  `Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]` → 服务器 tick 崩溃。
- 现在由 `registry/ModDamageTypes.projectileSourceOrNull()` 统一解析：数据包缺少该伤害类型时
  只记一次错误日志并**跳过本次伤害**，`Hurt` 与 `Explosion` 都走这条路。

---

## 附录 B：ParticleDrawing 已闭环条目（备查）

> **1.0.12-ALPHA 一次性闭环了四条**（本模组第 15/17/19 轮登记），本侧相应的简化见第 20 轮 20.1：
>
> | 需求（登记轮次） | 1.0.12 的落地 | 本侧简化 |
> |---|---|---|
> | 组指令缺「从现在起」的时间锚点（第 17 轮） | 新增 `api/GroupClock`：**增量追加的指令按「从现在起」换算**（每次跨 tick 的录制开启新会话），`fadeOut(removeAfter)` 的销毁延时也按同一口径从当下起算（`scheduleDestroy`） | 护盾消散回到 `delay(阶梯) + fadeOut(5, removeAfter = true)`；删掉「自己算游标 + 自己排销毁 + `bornAt`」 |
> | `spin` 与一次性 `rotate` 应可叠加（第 19 轮） | 多条 `rotate`/`spin` 的角度**累加**，后一条不再覆盖前一条的相位 | 「先自转一阵、再补一段一次性旋转、继续自转」可以直接写；本侧目前不需要补相位（不重建组） |
> | `scale`/`pulse` 应连「粒子到轴心的距离」一起缩放（第 15 轮） | `ScaleBy`/`Pulse` 同时作用于**到轴心的距离**与视觉大小（KDoc：半径 3 的圆 `scale(3f)` 后半径就是 9） | 尚未使用（黑洞进场仍是逐粒子 `move`）；后续「整壳胀开/缩回」可一个调用写完 |
> | `move()` 不应悄悄改 arm 基准轴心（第 15 轮） | `move`/`movePath` **不再改轴心**（轴心只由 `setPivot`/`followEntity` 决定），平移只作用于渲染位置 | 「铺完成员后第一条指令不能用 `move`」这条顺序硬约束解除

### 1.0.13-ALPHA：外观整组（第 23/28 轮登记 → 第 29 轮接线）

> 起因：用户给了参考效果（"这种效果才叫有冲击力"），素材与相位拆解在 `.refs/explosion-vfx/`。
> 能力清单、尺寸口径与两个坑见 [design/effects-api.md](../design/effects-api.md) §0.4。

| 需求（登记轮次） | 1.0.13 的落地 | 本侧改成什么 |
|---|---|---|
| 程序化粒子贴图写死 8×8 硬边白方块，无 texture/uv 入口（第 23 轮） | `ParticleStyle { SQUARE, SOFT_DOT, LINE }`（内置 16×16，两端各自生成、不占带宽）+ `ParticleManager.registerTexture(name, bytes)`（幂等；**同名第一次生效**；未知名字回落白方块、不丢粒子）+ `Builder.texture/uv` | 黑核、丝线、球壳全部改用内置贴图；尺寸按"**可见**尺寸"反推（`dotWorld`/`lineWorld`） |
| 各向异性：一条线段贴图应能沿长度拉长（第 23 轮） | `ParticleVisual.anisoWorld(w, h)`（**世界格数**）+ `Builder.scale(w, h)` | 丝线不再"每 0.05 格一颗小方块"，改用 `Draw.polyline`：**每段一颗粒子**、内部自动 `anisoWorld(段长, 粗)` |
| 固定朝向（丝线对齐线段）（第 28 轮） | `ParticleVisual.alignTo(from, to)` + `billboard(false)` + `spin(...)` | 同上（`polyline` 内部已用） |
| 加色 / 发光（第 28 轮） | `Builder.additive/glowing`（动画侧 `FunctionObject(additive)` 早已在用） | 亮边、内部闪电丝、外飞粒子走加色 + 自发光；**球壳反过来走 alpha 半透**（加色叠几百颗会过曝成白球） |
| 逐粒子生成延迟（第 28 轮 P2） | `Builder.delay(ticks)`（+ `Draw.*` 的 `stagger`） | 向心聚拢粒子按 `index % 4` 错开出现 |
| （顺手）一条折线一个调用（第 28 轮 P2） | `Draw.polyline(points, thickness, taper, texture, additive, glowing, colorFn, stagger, group)` | 丝线 / 尖刺 / 内部闪电丝 / 壳裂弧段各一个调用 |

**本侧踩到的两个坑（都记在 effects-api §0.4）**：
① 内置贴图必须在**渲染线程就绪之后**才 `registerBuiltinTextures()` —— 放 `FMLClientSetupEvent` 会抛
`Rendersystem called from wrong thread` 把客户端打崩，现改在**首个客户端 tick**里做一次；
② `anisoWorld` 收的是**世界格数**，别再乘 0.2（踩过一次，黑核大了 5 倍）。

**意义**：`ParticleStyle.kt` 从空文件变成有内容的枚举 —— 爆炸这类"需要连续丝线/柔边"的效果终于做得出质感。

⚠️ **但护盾不改**（第 31 轮用户口径）：「之前的护盾表现效果其实我很满意，**无需继续加上贴图之类的**」——
护盾现有的"实心小方块拼球壳 / 磨砂玻璃面板"就是用户要的"粒子感"，**不要**为了它引入贴图或柔边。
外观能力只服务确实需要的效果（爆炸的内爆/爆发）。

> 未决需求与登记模板见 [pd-api-requests.md](pd-api-requests.md)。下面这些都已闭环，留在这里是因为
> 「当初为什么提、对方怎么修的」对后来人有价值。

### B.1 历史缺陷条目

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

### B.2 已实现的 API 需求（本模组已在用）

#### [需求 ✓已加] `ParticleHandle.position(): Vec3?`

- 用途：黑洞/下落粒子按**权威位置**做「抵达中心」「碰到下半球表面」的回收判定
- 现状：已改用 `position()`；**不再依赖** `handle.data()`（那是 `core.server.ParticleData`，非 api 类型）。
  第 10 轮起多由 `ParticleBatch.forEach { i, h, pos, vel -> }` / `removeIf { h, pos, vel -> }` 直接拿到权威位置
- 落点：`magic/effect/lifecycle/BlackHole.kt`、`GravityField.kt`

#### [需求 ✓已加] `applyForce(accel, ticks)` 与 `ParticleManager.trackAll(ids, positions)`

- 现状：`trackAll` 已用于黑洞（1 包/tick，与粒子数无关，第 10 轮起经 `ParticleBatch.trackAll` 调用）；
  `applyForce` 备而不用（理由见上文表格后的说明）
- 落点：`magic/effect/lifecycle/BlackHole.kt`

#### [需求 ✓已加] 实体锚点 `attachTo` / `attachToLocal`

- 现状：护盾球壳**第 12 轮已改用组编排**（`Draw.sphere` + `ParticleGroup.followEntity(uuid, offset)` + `spin`，
  跟随与自转都在客户端本地求值、0 包/tick）；`ParticleHandle.attachTo(Entity, offset)` 保留给
  「一堆各自独立的粒子钉在实体上」的场景（当前无使用者）；
  `attachToLocal`（随实体朝向旋转）也仍未使用 —— 需要「贴在身前/身侧」的粒子时可用
- 落点：`magic/modifier/EnergyShield.kt`（组编排）、`magic/effect/lifecycle/BlackHole.kt`（不涉及锚定）

#### [需求 ✓已加] 批量施力 / 批量设速（一次网络包覆盖多粒子）

- 当初的困难：黑洞「被吸入粒子」每 tick 需要**每颗粒子各自**的加速度（`0.12/(距离+0.5)` 指向中心，
  再由本模组积分），而 `ParticleBatch.applyForce(handle, accel, ticks)` / `setVelocity(handle, v)`
  都是**单粒子**入口 —— 一颗粒子一个包（峰值约 34 包/tick）；唯一稳定的批量入口 `trackAll` 是**位置**指令，
  `takeOverPosition` 会清零速度与力，于是每颗粒子的速度只能由本模组自持一张表。
- PD 侧落地（`e0cbd5d`，1.0.10-ALPHA）：`ParticleBatch.setVelocityAll(速度列表)` /
  `applyForceAll(加速列表, ticks = 1)`（列表与 `handles()` 顺序一一对应，长度不同按短的截断；
  一次包覆盖全组，逐玩家按可见性裁剪后再发；`ParticleManager` 另有同名的 id 列表版本），
  以及新载荷 `ParticleVelocityBatchPayload`/`ParticleForceBatchPayload`（`MAX_BATCH = 512`，
  载荷集合变化 → 网络注册版本提到 `"4"`，两端必须同一个 jar）。
  同批还修掉了「`setVelocity`/`applyForce` 未解除实体锚点」——此前先 `attachTo` 再设速度/力，
  位置每 tick 仍被锚点覆盖，现在与 `move`/`track` 统一为「运动指令接管位置」。
- 本侧落地（**第 12 轮**）：黑洞改用 `ParticleBatch.setVelocityAll(velocities)` —— 每 tick
  `forEach` 读 PD 的权威 `pos/vel` 算出该 tick 的速度（`v ← v·阻尼 + 向心加速度`）、一包下发，
  **位置积分交回 PD**（`pos += vel`，服务端权威、暂停不外推），自持的速度表与自算积分一并删除。
  带宽与之前同为 1 包/tick，但**本模组不再持有任何跨 tick 粒子状态**，且「抵达中心才销毁」仍用
  `removeIf` 读 PD 的权威位置判定（判定精度不变）。轨迹已在接入前用 500 随机样本复算过（第 11 轮）。
- 落点：`magic/effect/lifecycle/BlackHole.kt`


#### [需求 ✓已加] 写明 `ParticleGroup` 各变换指令用「哪个轴心」（文档级）

- 当初的困难：护盾要「跟随持有者 + 持续自转」，但 `spin` 的 KDoc 只说「无限匀速旋转」，
  **没说绕哪个轴心**、也没说和 `followEntity` 的调用顺序有没有约束；指令载荷里那个
  `PivotRef`（客户端根本不读）反而容易让人以为可以逐指令指定轴心。只能靠读客户端源码才敢接线。
- PD 侧落地（`3007b22`，1.0.11-ALPHA）：`ParticleGroup` 类注释 + `setPivot`/`followEntity`/`rotate`/`spin`
  的 KDoc 都写明「轴心是**程序级状态**、绑定一次对其后的 `rotate`/`spin`/`scale`/`pulse` 生效、
  **调用顺序即语义**」，`api-guide.md` 也加了「轴心：组级变换都绕它算（调用顺序即语义）」一节；
  同时**删掉了指令里从未被消费的 `PivotRef`**（`RotateOnce`/`Spin`）。
- 本侧价值：第 13 轮正是靠这条文档把护盾「出现时对、之后掉到脚下」的轴心偏移账算清楚的
  （`rel` 按「成员 spawn 位置 − arm 时的 pivot 字段」重算，而 `followEntity(offset)` 会顺手改那个字段）。
- 落点：`magic/modifier/EnergyShield.kt`（第 13 轮修复），见 [第 13 轮](#第-13-轮2026-09-30护盾球壳掉到脚下--pd-1011-轴心语义)
