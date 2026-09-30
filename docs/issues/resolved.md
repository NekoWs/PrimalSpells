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
