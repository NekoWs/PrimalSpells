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

## 第 38 轮（2026-10-01，修「持盾朝攻击者走过去会被打中」）

> 生存模式实测（用户）：持盾朝攻击我的假人走去会被打中，且**法力一点没掉、护盾根本没展示** ——
> 说明那一发**连格挡判定都没进**，不是「挡了没挡住」。

**根因**：格挡判定看的是「投射物这一 tick 是否**从壳外穿进壳里**」（`shellEntry` 要求起点在壳外），
而球心 `shellCenter` 跟着持有者走 —— 朝投射物走过去时**壳是"迎着"弹扩张的**，弹会在某一 tick
直接落到壳**内**，前提不成立 ⇒ 整发不挡、不画碎片、不扣蓝。这与设计里「法术本来就生成在壳里 → 挡不住」
走了同一分支，但这里不是生成在壳里，**是壳追上了它**。

**修法**（保留原设计意图）：每颗进过壳外的投射物记一个时间戳
（`seenOutside: identityHashCode → gameTime`，20 秒过期清理）；`shellEntry` 增加 `allowInsideStart`：
**曾在壳外出现过的弹，即使这一 tick 已在壳内也照挡**；从没在壳外出现过的（真正生成在壳里的法术）仍不挡。
键用 `System.identityHashCode`（`Magic` 只有法术名 `id: String`，没有实例级 UUID）——
代价是对象回收后哈希可能重复（概率极低），最坏是"某发生成在壳里的法术被误挡一次"。

**验证**：4 场景 / **616 帧 / 丢帧 0 / 断言 21 条全过 / 运行日志 0 异常**，构建 **0 warning**（护盾 10 条断言无回归）。
⚠️ **本轮没有专门断言"走路迎上去"那一下** —— 需要一段"木桩朝飞来的火球移动"的场景时间线，
已作为下一步留在 [open.md](open.md)（本轮证据只到"改动无回归 + 逻辑对口"）。

## 第 39 轮（2026-10-01，护盾改「按 DPS 承担」+ 爆炸波及的片全部显示）

> 用户口径（[open.md](open.md) 第 39 轮登记后开工）：
> ① 「护盾防的是 **DPS** 而不是单次伤害，抗性改为在**一段时间内可以承担的强度大小**」；
> ② 「优化护盾遇到爆炸的视觉表现，改为**能够被爆炸波及的片全部显示**」。
> 追问确定：窗口 = **1 秒（20 tick）**、**累计超出预算立即开破口**。

### 39.1 窗口预算（`EnergyShield.payForBlock`）

滚动记录最近 20 tick 已承担的强度（`recentStrength`，只存内存），结算顺序改成
**先查窗口预算 → 再查法力**；预算爆掉时清空窗口记账（那一段破口 3 秒本来也不再累计）。
预算 = `抗性 × RESISTANCE_BUDGET_RATIO`。

⚠️ **系数取 2 而不是字面口径的 1**，这是本轮唯一的判断分歧，理由写进了
[design/combat-pricing.md](design/combat-pricing.md) §4.95：抗性 4 时预算 = 4 会让**单发火球（强度 5）**
直接破口，于是「超出抗性每点加价」能生效的区间 `4 < 强度 ≤ 4` 是空集 —— 第 26/35 轮那套法力定价
（含封顶）等于被架空，护盾会退化成"只挡激光"。取 2 之后的三个刻度（抗性 4，预算 8）：

| | 结果 |
|---|---|
| 单发火球（强度 5） | 挡下，10 MP（基础 5 + 超出 1 点 × 5）—— 法力加价路径仍然活着 |
| 同一窗口内第二发（累计 10 > 8） | **破口**（这一发连法力都不花） |
| 大爆 / 核弹（强度 60~144） | **破口**（`open.md` 已接受的"大爆必破"） |

旋钮就一个私有常量，改一行即可回到字面口径。

### 39.2 爆炸按影响范围选片

判据是字面：**面板中心到爆心的距离 ≤ 爆炸半径**的面板全部入选（分成"朝爆点 / 背对爆点"两批，
后者暗一档 —— 用户口径「背面的片可以暗一档」）。
半径 6 的核弹摆在壳外 3 格时最远的背面片也只有 5.2 格 ⇒ **32 片全部被波及**（整罩碎裂）；
小半径的爆炸只点亮朝着它的那几片。
**破口区就是碎裂的那一片**（表现即玩法）：投射物仍是"受击面板 + 相邻一圈"，爆炸是"波及到的全部面板"。

### 39.3 顺带修掉 §1.9.2（护盾刷屏日志，本侧那一半）

`spawnGlassPanels` 改成**按飘散位移分组**：位移相同的面板共用一个 `ParticleGroup`
（格挡玻璃 / 破口轮廓 / 爆炸碎裂各占 1 个组），只有"投射物打穿、每片沿自己法线向外"才一片一组（≤7 组）。
实测整场录制 `armed` 日志 **39 行**（此前**一次受击**就是 32 行）；一次整罩爆炸是 **3 个组**
（1080 + 1080 + ~1056 颗粒子），不再是 64 组。
PD 侧那条「`armed` 降级为 DEBUG」的需求保留在 [pd-api-requests.md](pd-api-requests.md)（本侧无权关前置模组的日志）；
（**后注（2026-10-04）：已闭环**，arm 行在 1.0.14-ALPHA 改为 DEBUG + 两个开关，见附录 B。）
同时新增一条**可选**需求：组级 `move` 只能整组一个方向向量，想要"每片各自外飞"就不得不一片一组。

### 39.4 验证

- `energy_shield` 场景重排（390 tick），**9 条场景内断言全过**，其中三条是本轮新增口径的决定性证据：
  - `budget-breach`（t=162）：两发火球落在同一个 20 tick 窗口里 ⇒ **破口**，而法力还剩 **2950/3000**
    —— 破口不是"没蓝"造成的，只能是窗口预算；
  - `small-blast-absorbed`（t=298）：5.75 格外的小额爆炸 ⇒ 强度 ≈ **5.95**（落在「抗性 4 < 强度 ≤ 预算 8」里）
    ⇒ 抵扣成功、扣 **14.7 MP**、不出破口（这一发就是"波及到的片全亮一下"）；
  - `blast-triggers-after-move`（t=314）：木桩挪走 3 格后把大爆摆在**旧球心** —— 判定跟着玩家，
    所以它在壳外 3 格 ⇒ 触发；若判定留在原地，爆点会落在旧壳**内部**、护盾根本不触发。
- `assemble.py` 新增 `breach_ratio` 判据：同一场景里**打穿窗口的粉色峰值 vs 大爆窗口的粉色峰值**
  （实测 2751 → **7951**，2.89×，要求 ≥2×）—— 这是"波及到的片全部显示"的自动代理。
- 全套：4 场景 / **706 帧** / 丢帧 0 / 20 条断言全过 / 运行日志 0 异常；构建 0 warning。
- 目视（接触图 + 逐帧）：见画廊 `build/capture/out/index.html`。

### 39.5 记录在案的两个观察 / 陷阱

1. **整罩的粉色破口是"逐渐露出来"的**：爆炸的烟云先挡在镜头前，粉色轮廓的像素峰值出现在
   **t≈366**（放弹在 309）—— 也就是烟散开之后才看清整罩都碎了。判定窗口因此取到破口到期前。
2. **`Magic.wand` 是 lateinit，`prepare` 阶段读不到**：场景里那句 `card.strengthBudget()` 抛了
   `UninitializedPropertyAccessException`（`resistance()` 要读 `wand.loadedMagics`），被驱动记成
   `prepare FAILED`。改成用公开字段自己算；`RESISTANCE_BUDGET_RATIO` 因此提为 public（与其它手感旋钮同级）。
3. **收尾断言会和 PD 的组销毁抢跑**：破口轮廓组是 `T + 54 停留 + 6 淡出 + 5 余量 = T+65` 才被销毁，
   而 `pd-particles-zero` 打在倒数第 3 帧 —— 场景原本 378 tick 时正好撞上（实测 `remaining=1080`，
   一次过、一次不过）。**不是泄漏，是没留余量**：场景加长到 390 tick（末尾留 22 tick）后稳定为 0。
   同一次日志里 PD 还会打几条 `group … has no members; animation program not sent`（整罩碎裂一次建 3 个大组时），
   录制里画面与销毁都正常（`pd-particles-zero` = 0），暂记为观察项，不作为缺陷登记。

## 第 40 轮（2026-10-01，护盾四改：红轮廓跟人 / 破口时长按超出抗性 / 抗性提高 / 挡反弹原版投射物）

> 用户口径（四条一起给的）：
> ① 「修改红色的破盾视觉，跟随着被破盾的护盾持有者移动」；
> ② 「修改破盾的冷却，改为根据超出抗性的部分决定」；
> ③ 「整体增加护盾的基础抗性」；
> ④ 「护盾能够防御/反弹原版的投射物」。

### 40.1 红色破口轮廓跟随持有者（口径①）

`spawnGlassPanels` 加了 `follow` 开关：**只有粉色破口轮廓**调 `followEntity(holder.uuid, 壳心偏移)`，
白色格挡玻璃与碎片仍然是"盖章"式的（第 36 轮口径不变）。
跟随偏移与成员偏移同源（都是"脚底 → 球心"），所以形状刚性、不会错位 —— 这正是第 13 轮踩过的那个
「客户端按 `成员 spawn 位置 − pivot 字段` 反算相对偏移」的约束（`followEntity` 必须在铺成员**之前**调）。

口径上这也更自洽：**判定与破口方向都是相对玩家算的**，轮廓留在原地就会与真正的破口错位。

### 40.2 破口时长按"超出抗性多少"定（口径②）

`breachTicks = 基础 20 tick + (强度 − 抗性) × 6，封顶 60 tick`（原来固定 60）。抗性 6 时的刻度：
箭矢/火球（强度 ≤6，超出 0）→ **1 秒**；强化火球（8，超出 2）→ **1.6 秒**；爆炸类（60+）→ 吃满 **3 秒**。
用的是**这一发自己的强度**（不是"超出预算多少"）—— 用户口径原文说的是"超出抗性的部分"。
三个新旋钮：`baseBreachTicks` / `breachTicksPerPoint` / `maxBreachTicks`。全文见
[design/combat-pricing.md](design/combat-pricing.md) §4.97。

### 40.3 基础抗性 2 → 4（口径③）

抗性变成 `4 + 每张卡 2 = 6`（一张卡），窗口预算随之 `12`。刻度：单发火球（5）只付基础 **5 MP**（原 10）、
单发满蓄力箭矢（6）同样 5 MP，**一秒内三发**（15 > 12）才破，爆炸类必破。
取值理由与"为什么不取字面口径"写在 [design/combat-pricing.md](design/combat-pricing.md) §4.95。

### 40.4 原版投射物：防御 + 反弹（口径④）

规则与自家法术**完全同一套**（穿越判定、强度/预算/法力/破口），新增的只有两件事：

1. **强度档位表**（`vanillaStrength`）：箭矢/光谱箭 `速度 × 2`（原版箭伤就是 `ceil(速度 × baseDamage)`）、
   三叉戟同式基准 8、火球 5/12、骷髅头 8、龙息弹 6、风弹 2、潜影贝子弹 4、羊驼唾沫 1、烟花 4、
   雪球/鸡蛋/药水 0（只花基础 5 MP，但**值得挡**）；没列出来的（钓钩、末影之眼…）**护盾不管**；
2. **反弹**：速度按壳面法线镜像、大小不变 + 位置推到壳外 + **归属改成持有者** ⇒ 箭会打回射手。
   自己的弹与队友/宠物的弹不弹（`owner === holder` 或 `holder.isAlliedTo(owner)`）。

**两个入口**与自家法术同构：每 tick 扫场（球心周围 `SHELL_RADIUS + 4` 的 AABB 查询）
+ **`ProjectileImpactEvent` 命中兜底**（新文件 `event/ProjectileShieldHandler.kt`，在 `PrimalSpells` 里注册）——
原版箭 3 格/tick、火球更快，可能在同一 tick 里就贴到身上（与自家 `laser` 是同一个理由）。
详见 [design/combat-pricing.md](design/combat-pricing.md) §4.98。

### 40.5 验证

- `energy_shield` 场景重排（350 tick），**12 条场景内断言全过**，其中四条是本轮四条口径各自的证据：
  | 断言 | 数据 |
  |---|---|
  | `hits-blocked` | 四次**强化火球**（强度 8、抗性 6）各付 **15 MP** → 3000 → **2940**（正好 4 × 15） |
  | `budget-breach` | 同窗口第二发超预算 ⇒ 破口，而法力还剩 **2925** ⇒ 破口不是"没蓝" |
  | `breach-area-one-ring` / `blast-reaches-all-panels` | 激光打穿 = **7 片**（受击 + 一圈）；大爆 = **32 片**（整罩） |
  | `vanilla-arrow-reflected` | 原版箭矢被挡下并弹回：法力 **3000 → 2995**，箭最后停在 **x=5.25**（持有者 x=0.5） |
- 全套：4 场景 / **665 帧**（丢帧 1，在预算内）/ **23 条断言全过** / 运行日志 0 异常；构建 0 warning。
- 目视：`build/capture/out/energy_shield/contact.png` 与逐帧（含 280 前后"破口期内走一步"的两帧）。

### 40.6 记录在案的两件事

1. **像素判据量不出"波及到几片"**：32 片的粉色点阵在共享顶点/棱处大量重叠（1056 个点只落在 ~420 个
   位置上），整罩的粉色像素只有 7 片时的 **~1.7 倍**而不是 4 倍；爆炸烟云还会把粉色压暗
   （相机在烟里时整帧都被染色）。所以第 39 轮那条 `breach_ratio` 像素对比**已删除**，
   改成读内部状态 `EnergyShield.breachPanelCount` 的断言 —— 这才是"波及范围"的直接证据；
   像素判据只保留"红色确实渲染出来了 / 常驻 / 淡出"。
2. **"轮廓跟人"没有自动断言**：跟随发生在客户端（`followEntity`），服务端读到的粒子位置不跟随，
   所以这一条只能靠帧目视确认（场景里专门加了"破口期内走 3 格"的一段，产物取 280 前后对比）。
3. **实机截图发现"红色破口整片偏移"，根因是 PD `followEntity` 的 offset 语义**（当天修掉）：
   `followEntity(uuid, offset)` 不是"设成 实体+偏移"，而是 `pivot = offset + pivot`（**累加**到当时的轴心上）；
   客户端 arm 时算 `rel = 成员 spawn 位置 − pivot`、逐帧渲染 `实体位置 + offset + rel`
   —— offset 减一次加一次，**净效果为零**。初版因此把轴心设在球心、offset 传了"脚底 → 球心"的抬升，
   结果 `rel` 少了这一截 ⇒ 跟随的红色轮廓**整片下沉约 1.1 格**：球心落到脚底，下半球埋进地板。
   正确写法是**把轴心取在实体脚底**（`createGroup(holder.position())` + `followEntity(uuid)` 不传 offset），
   `rel` 里自然带上抬升。像素侧也对得上：同一窗口的粉色峰值 **2432 → 8033**（修好后与第 39 轮量到的
   整罩 ~7951 一致），因为原先埋在地板里的那半圈根本不参与判据。
   这条已写进 [design/effects-api.md](design/effects-api.md) §0.4 的"三个必踩的坑"第 3 条。

## 第 41 轮（2026-10-01，护盾玻璃改发光蓝；数值全线改按"小白 DPS"锚定）

> 用户口径：「护盾的提示太淡了」（实测原版骷髅 vs 护盾）→ ① **发光蓝色边框 + 半透明蓝色填充**，
> 保留稀疏粒子感；② 「护盾对于小白来说已经无敌了，还需要平衡数值，**根据小白的 DPS 作为基础
> 设计所有魔法数值**」。

### 41.1 视觉：发光蓝边 + 半透明蓝填充（口径①）

- 亮边 `Color.ofInt(120,200,255,240)`、填充 `Color.ofInt(60,140,255,70)`，两者都走
  **`additive(true)` + `glowing(true)`**（`spawnGlassPanels` 新增 `glow` 开关）；
- 为什么换色相而不是加亮：原来的**冷白在亮场景里和雪/水/云一个色**，而蓝色在任何背景上都跳出来；
- **稀疏粒子感保留**：仍是 6 点/棱的点阵、没有加密（第 20/21 轮定的取向不变）；
- 打穿的白玻璃跟着改成同色系更冲的蓝白（`190,230,255`）；**红色破口轮廓保持原样**（它是"这里能打"的警示，
  与蓝色不是一套语义），要它也发光只是把 `glow` 传成 true 的事。

### 41.2 数值锚：一个小白 = 3.0 DPS（口径②）

**锚的测量**（全部来自原版代码，可复核）：

```
RangedBowAttackGoal(this, 1.0, 20, 15.0)   20 tick 攻击间隔（AbstractSkeleton:55）
+ pullTime >= 20 拉弓                       ⇒ 2 秒一箭
BowItem.getPowerForTime(20) = 1.0           ⇒ 初速 power × 3.0 = 3.0 格/tick
AbstractArrow.setBaseDamageFromMob(1.0)     ⇒ baseDamage = 2 ± 0.57
⇒ 箭伤 ceil(3.0 × ~2.2) ≈ 6，6 / 2 秒 = 3.0 DPS = 1 U
```

**所有数字改成用 U 读**（`combat-pricing.md` §1.1 有完整表）：玩家基础弹（火球 10 DPS）= **3.3 U**、
AoE 单体 = 2.7 U、`drone_bolt` = 1.7 U、**护盾 = 2 U**。
⇒ 一个玩家 ≈ 3 个小白的输出，护盾扛得住 2 个 —— 数字之间第一次有了统一的"人话"刻度。

**改了什么绝对数值**：只有护盾的窗口预算，**12 → 6**（= 2 U）：

| | 旧（预算 = 抗性 × 2 = 12） | 新（预算 = 2 U = 6） |
|---|---|---|
| 1 个小白（6 强度/2 秒） | 永远打不破（还有法力池） | 刚好压在预算内 ⇒ 挡得住 |
| 2 个小白（6 强度/秒） | 打不破 | **正好吃满预算**（没有余量） |
| 3 个小白（13 tick 一箭） | 打不破 | **第二发就 6 + 6 > 6 ⇒ 破口** |
| 玩家火球连发（5/发） | 两发 10 ≤ 12 挡得住 | 两发 10 > 6 ⇒ **破口** |

口径也从"抗性 × 系数"改成**与抗性脱钩**（多张卡只抬高单发阈值，不再抬高每秒承受量）——
这正是"无敌"的来源：旧的倍数写法把 6 张小卡也算成了每秒 24 的承受量。
旋钮：`EnergyShield.SKELETON_DPS`（锚）与 `EnergyShield.SKELETONS_HELD`（扛几个小白，默认 2）。

### 41.3 验证

- `energy_shield` 场景加了两段**按小白真实射速**打的段（用原版 `Arrow` 实体 + 满蓄力 `shoot`）：
  - `skeleton-dps-held`（1 个小白，40 tick 一箭 × 3）：三发全挡下，法力 3000 → **2985**（每发基础 5 MP）；
  - `skeleton-volley-breaks`（3 个小白，13 tick 一箭）：**第二发就把 20 tick 预算打爆 ⇒ 破口**。
- 其余 12 条断言（含整罩 32 片、红轮廓跟人、反弹原版箭、破口时长随超出抗性）全部保持通过。
- 全套：4 场景 / **839 帧**（丢帧 5，在预算内）/ **25 条断言全过** / 运行日志 0 异常；构建 0 warning。
- 场景重排：预算收紧到 6 之后，"点射"探针从强化火球（8，会一发超预算）改回**普通火球**（5），
  小额爆炸/挪位/大爆整体后移到 260/270/276（与前一次受击拉开 20 tick 以上）；场景总长 350 → 528。
- 目视：`build/capture/out/energy_shield/frames/00034.png`（受击那一下的蓝色玻璃）与 `00346.png`（小白段）。

### 41.4 记录在案（两个陷阱）

1. **场景的 `checksAt` 跑在客户端计数器上，与服务端 tick 有 ±10 tick 漂移**：
   第 41 轮把"3 个小白破口"直接写成"事件当 tick 断言"，结果一次过、一次不过（服务端确实在 t=457
   的第二发上开了破口，但客户端那一刻还没跑到）。
   修法是**在服务端 tick 上记录状态、断言稍后再读**（场景里 `volleyBreached` 就是这个套路）。
2. **测试助手的瞄准点写死了 `CZ + 0.5`**：`vanillaArrow` 原本瞄准"竞技场中心那一列"，
   而木桩在第 41 轮会走位（破口期 +Z 3 格）⇒ 小白段的箭全部从壳边擦过去、一发都没打中护盾
   （表现是"法力一点没掉"，很容易误判成护盾失效）。瞄准点改成持有者**当前**位置后正常。
   教训：**测试里任何"写死的坐标"都会在场景加了走位之后变成假阴性**。

## 第 42 轮（2026-10-01，强度系统定案：Noita 式分工 —— 法术给伤害与消耗，法杖给节奏与总闸）

> 用户口径（三轮收敛）：
> ①「**不要加延迟之类的，要削削伤害，手感保持不变**。上次那一轮改的延迟之类的已经把手感改得很差了。」
> ②「以火球 + 施放延迟 0.05s 为基准」。
> ③「**法杖不需要改，把法杖改回去**。法术延迟不能太高，**整体强度由法杖限制**。**还原 Noita 的强度系统**。」

### 42.1 最终改了什么

| | 处理 |
|---|---|
| **法杖**（T1–T6 的施放延迟 0.50→0.30 s、充能时间 1.00→0.50 s、法力/回蓝/槽位/施放数） | **完全不动**（第 28 轮那条用户自己调过的曲线原样保留） |
| 所有法术 / 修饰器的 `delay` / `recharge` | 归零；只有 `fireball` 保留 **1 tick = 基准 0.05 秒**（`laser` 的负值保留，它是加速） |
| 伤害 | **回到 Noita 式的单发值**：火球 5.0、触发火球 4.0、小爆中心 12.0、大爆中心 24.0、爆炸兜底 6.0、`damage_boost` +3 |
| `small_explosion` / `large_explosion` 的法力 | 2.0 / 5.0 → **40.0 / 80.0**（本轮唯一保留的新数字，理由见 42.2） |

**机制依据**：手牌实际间隔 = `max(法杖施放延迟, Σ卡牌 delay, Σ卡牌 recharge)`（`Wand.spell`）。
把卡牌压到 ≤ 基准之后，间隔就等于**法杖的施放延迟** —— 节奏回到法杖手里，
低级杖慢、顶级杖快；卡牌再也不能把节奏顶上去。

### 42.2 为什么只有爆炸的法力被抬高

卡牌延迟归零 + 法杖 0.3~0.5 秒 ⇒ 重法术会以 2~3 发/秒连发（每秒 2~3 团爆炸粒子云，特效直接过载），
而"炸一下很疼"的单发伤害必须保住（大爆中心 24 = 无甲玩家必死）。要让两者同时成立，只能让**法力**当总闸：
小爆 / 大爆 40 / 80 ⇒ 满蓝连放 2 / 1 发（T5 是 7 / 3 发），之后被回蓝限成 2.5 / 1.25 发/秒。
这正是 Noita 的做法：**贵法术靠法力限速，而"多贵"是法术属性、法杖决定负担得起几发**。
（想改回 2 / 5 的话，爆炸会变回 20 发/秒的连喷，特效与数值都会失守 —— 要改请连特效预算一起谈。）

### 42.3 验证

- `energy_shield` 场景的"防 DPS"段：连射（每 tick 一发）—— 火球单发强度 5、预算 6
  ⇒ **第 2 发就 5 + 5 > 6 破口**，而当时法力还剩 **2975**（只花了 2 发的钱）⇒ 破口只可能来自预算。
  破口状态在 `t=145` 的**服务端 tick** 上记录、断言稍后再读（客户端计数有 ±10 tick 漂移，第 41 轮踩过）。
- 4 场景 / **844 帧** / 丢帧 0 / **25 条断言全过** / 运行日志 0 异常；构建 0 warning。
- 目视：`frames/00148.png`（连射：橙色火球流 + 护盾蓝色玻璃环）、接触图 144/287/335 三帧。

### 42.4 记录在案（三轮的教训）

1. **先用"延长法术间隔"平衡**（第 26 轮：火球 0.5 秒、爆炸 1.5~3 秒一发）→ 用户实测"手感很差"。
2. **再想用"削伤害 + 把法杖时间值也压掉"来救** → 被否：「法杖不需要改，把法杖改回去」。
3. **定案**：手感轴（射速）不是平衡旋钮；**平衡旋钮是 伤害 / 法力，而节奏与总闸归法杖**。
   U 表（DPS 表）本身不变 —— 它只是"读法"，不是"数值来源"。
4. `wand-tiers.md` 与 `verify.md` 里我一度写下的"第 42 轮推翻时间曲线"已撤回，法杖曲线维持第 28 轮的原样。

## 第 48 轮（2026-10-04，拖尾定稿：连续彗尾 + 头部 `track` + 去掉柔光点；PD 1.0.14 迁移）

> 用户口径（原文，按时间顺序）：「不要有多个'菱形'作为轨迹，只保留第一个最大的作为本体，然后可选拖尾（逐渐变小减淡的）
> 的长度之类的，稍微拉长一点」→「整体太长了，并且间距太长了，我喜欢的效果是原有里的那种'**密，但不叠**'」
> →「拖尾的初始大小应该和本体差不多大，略小一些，然后逐渐变小变淡」→「一定要实现拖尾**严格在本体固定位置后面**」
> →「还是有'小的拖尾出现在最大的本体前面'的问题，而且看起来有种**闪烁感**」→「法术前进的**帧率只有大约 20**」
> →「本体明显比后面的拖尾要大，优化一下，目标是**之前的连续感**」。

| # | 事 | 结论 / 落点 |
|---|---|---|
| 48.1 | **一串等大菱形的根因** | 旧写法「每 tick 沿扫过的那一段铺 5 颗、尺寸按**段内索引**递减」⇒ 段内梯度**每 tick 重置一次**，于是每隔一个 tick 重复一颗大菱形。改成「**按里程在精确落点采样**（跨过 0.5 格就是一颗，落点在段内插值出来）+ 每颗在**自己的寿命里**收缩淡出」⇒ 大小/明暗严格跟着年龄（= 离本体的距离）走 |
| 48.2 | **观感参数化** | 新增 `length`（**格**，口径 = **肉眼可见的那一段**，含 `TRAIL_VISIBLE_FRACTION ≈ 0.85` 补偿）；间距 / 出生尺寸 / 尾端尺寸 / 尺寸与透明度曲线全成参数 —— 用户后面连改三次口径，都只动数字 |
| 48.3 | **头部必须是单独一颗**（两次独立判读都判"头部不是最亮/最大"） | 定稿：**单颗 `track` 白热菱形 0.60 格**（永存、`onDie` 移除）。「尾迹最前那颗当头部」试过一版，被 48.5 推翻 |
| 48.4 | **"冒出到本体前面"与"闪烁"是同一个根因** | 头部走 `track`（客户端按 partialTick 在 `prev → pos` 之间插值），发射器锚点走「锚点位置 + 速度 × partialTick」**外推** ⇒ 两条通路相位不严格相等，尾迹最前那颗会**冒到头部前面**，且相位逐帧摆动正是**闪烁**。旧绕法「采样段整体延迟一 tick」；**正解：锚点压到上上 tick**（`prevPrevPos`）⇒ 任何相位下恒 ≤ 头部渲染位置，且相位差**固定** |
| 48.5 | **"法术前进只有 20 帧"是本轮自己改出来的** | 把头部换成"尾迹最前那颗"之后，**眼睛跟着的那个点**变成发射器锚点的 20Hz 采样 ⇒ 看出 tick 台阶。定论：**头部管丝滑（`track` = MC 自己的逐帧 partialTick 插值），尾迹管留痕（发射器）**，两者用 48.4 的错位共存 |
| 48.6 | **连续感** | 尾迹出生尺寸 0.50 → **= 头部 0.60 格**，并**去掉前段陡降**（曾用「前 1/3 寿命掉到 55%」，会在本体与尾迹之间留尺寸台阶）；两颗同尺寸菱形（半径各 0.3 格）顺手盖住 48.4 那 0.4 格的错位 |
| 48.7 | **删掉本体柔光点 `ProjectileBody`** | 用户口径「如果能丝滑前进的话就去掉」。它同样走 `track`、与头部重复标记同一位置，两套时钟还会看成闪烁；`Projectile` 不再装配、类文件删除，文档 15 处同步（`architecture`/`conventions`/`README`/`spell-development`/`code-map`/`effects-api`） |
| 48.8 | **判据口径跟着模型搬**（不是放宽） | 尾迹粒子现在**只活在客户端**（发射器，服务端只有声明）⇒ 场景里那条"服务端 PD 粒子数 ≥ 18"必然误报；改读客户端引擎 `ClientParticleEngine.activeCount()`，并**新增**客户端侧"零残留"检查（粒子搬了家，泄漏检查也得跟过去） |
| 48.9 | **PD 侧闭环** | 本轮登记的需求组（运行时发射器 / 插值语义统一 / spawn 内寿命曲线 / 批量生成）已由 **ParticleDrawing 1.0.14-ALPHA**（`d998e36`，顺带做了更早那条"组级逐成员漂移"）全部落地，本侧随之迁移：拖尾 = 一句发射器声明（`spacing`/`life`/`scale`/`sizeCurve`/`alphaCurve`），一次性撒点走 `spawnAll` + 批量速度/力 + 曲线；那组需求随即从需求单**移入本节**（需求单现在只剩 **2 条未决**：发射器抖动、`EmitterHandle` 改寿命/曲线） |

**验证（实机录制 + 独立判读）**：`fireball_trail` **PASS**（72/72 帧、丢帧 0、场景内断言 4/4、退场渐变 10~13 帧、日志 0 异常）；
"严格在本体之后"逐帧检查 **0/28 帧**越界；`gravity_field` PASS（退场 9~13 帧）；`black_hole` 仍只失败在**既有**那条退场判据
（`50%→5%` 只跨 2 帧）—— A/B 把本轮改动 stash 掉后**同样失败**，与本轮无关。
⚠️ 录制流水线每 tick 抓一帧（20fps）⇒ **"丝滑"这件事它证明不了**，只能靠实机眼睛；本轮的丝滑依据是机制（`track` 走的就是 MC 自己的插值）。

**教训**：① **看到"帧率只有 20"先问"眼睛跟着的是哪条通路"** —— 这一轮的帧率问题不是 PD 的锅，是把逐帧插值的 `track` 换成 20Hz 采样+外推造成的；
② **同一位置不要放两套时钟**（头部 `track` + 柔光点 `track` + 尾迹发射器 = 三份相位），能合并就合并；
③ **判据要跟着模型搬家**：粒子搬到客户端之后，服务端计数就是错的证据源；
④ **"绕"出来的效果会带绕的副作用**（延迟一 tick 换来了缝，缝又逼出尺寸台阶），能回到正解（48.4）时收益是复利的。

## 第 47 轮（2026-10-02，尾迹锚点对齐本体粒子 + 调短；静止场退场余晖）

> 用户口径（原文，看第 46 轮实机图后）：「**尾迹调短些**，然后我发现**本体粒子跟不上尾迹**，
> 图里我用红色箭头指出了本体粒子的位置。」

| # | 事 | 结论 / 落点 |
|---|---|---|
| 47.1 | **本体与尾迹差一整格位移的根因** | 本体粒子走 `handle.track(...)`，PD 客户端用 `TrackBuffer` 的 `previous → current` 两端点做 partialTick 插值 ⇒ 屏幕上**本体位置永远落后权威位置最多一整 tick**；而尾迹粒子是「生成即静止、按绝对位置渲染」的 ⇒ 拿本 tick 的 `pos` 当锚点时，**尾迹最前一颗会系统性地跑到本体前面一格**（红箭头指的就是它） |
| 47.2 | 修法：锚点换成**上一 tick 的权威位置** `from` | `onTick` 里 `val anchor = from`，`emitSmear` / `emitCore` / `emitMotes` 全部以它为起点 ⇒ 两者落在同一个插值端点上、一起前进。`emitSmear` 的纵向展开量改用**本 tick 位移** `pos.distanceTo(anchor)`（静止/极慢时退化为不展开，不会铺成一条线） |
| 47.3 | 尾迹调短 | `SMEAR_LIFETIME` **8 → 7**（尾迹 ≈ 速度 × 7；火球 0.5 格/tick ⇒ 约 3.5 格）。退场那 4 帧的余量改由死前补撒承担：`DEATH_SPARKS` 18 → 22、`DEATH_LIFETIME_MAX` 18 → 20 |
| 47.4 | **静止场没有尾迹 ⇒ 退场是断崖**（本轮判据抓到） | 黑洞/重力场 `speed = 0`，粒子全靠 `emitAmbient` 每 tick 现撒，本体一死就断供 ⇒ 退场判据 `50%→5%` 只跨 **3 帧**（要求 ≥4）。新增 `emitFieldAfterglow`：26 颗符文按球面摊开、速度朝外很慢、**寿命 6~24 tick 错开** ⇒ 黑洞场景回到 **12 帧**渐隐 |
| 47.5 | 余晖第一版**太弱**，反而更差 | 首版给 0.06~0.13 格、`hot` 0.25~0.70 —— 实测把黑洞的退场从 5 帧打成 **3 帧**：黑洞自己的塌缩在某帧"啪"地结束时，屏幕上得**已经有足够亮的东西接住**，否则 5% 阈值正好落在断崖上。按「**≥ 它替掉的那套（飞行式补撒）的亮度与尺寸**」调到 0.09~0.20 格、`hot` 0.60~1.00 后过关 |

| 47.6 | `gravity_field` 的 `decay` 口径跟着改 | 该场景原声明 `decay: "instant"`（"那时确实没有退场动画"）。加了共用的退场余晖之后，静止场的收场**设计上**就是逐颗淡出 ⇒ 改成 `gradual`。⚠️ 这是**设计变了、口径跟着变**，不是为了让判据变绿而放宽：同一个余晖在黑洞场景把 3 帧修成 12 帧，是同一件事的两面 |

**验证（5 场景全量实机录制 + 像素判定）**

- **916 帧 / 丢帧 0 / 场景内断言 28 条全过 / 运行日志 0 异常**，构建 **0 warning**。
- `fireball_trail`：退场渐变 **12 帧**（改短到 3.5 格后反而比第 46 轮的 5 帧更从容）；
  `black_hole` **12 帧**（修前 3 帧）；`gravity_field` **13 帧**（由"立即"改为"渐变"）；
  `explosion` 仍 **1 帧**（触发即爆，未受影响）；`energy_shield` **14/14**（未受影响）。
- 本体/尾迹对齐：见 47.1/47.2，`fireball_trail` 的实机帧里本体不再甩在尾迹后面。

**教训**：**「加一层」之前先看"这层够不够亮"**。退场余晖这种东西如果比它替掉的旧行为更暗，
在阈值型判据上就是**负优化** —— 本轮第 47.5 条就是活例（数值我留在了常量注释里，避免下次再调回去）。

## 第 46 轮（2026-10-02，拖尾两处实机反馈修正：改回广告牌 + 收敛飞行中的小粒子）

> 用户口径（原文，看第 45 轮实机图后）：「整体还不错，但是**为什么不是广告牌**？这样看起来就像是
> 一个片向我过来，**正视几乎就是一个薄片**。而且**飞出去的小粒子太扎眼了**。」
> 补充澄清：「我说的扎眼的是**飞行过程中**的，命中效果还不错。」

| # | 事 | 结论 / 落点 |
|---|---|---|
| 46.1 | **不是广告牌 = 我自己加的 `alignTo` 造成的** | 第 45 轮为了让菱形「沿轨迹首尾相接」用了 `ParticleVisual.alignTo(...)`，而 `alignTo` 会**强制 `billboard = false`**（PD 的 KDoc 写得很清楚：`alignTo` 会一并关掉广告牌）⇒ 每颗菱形都是**固定世界朝向的薄片**，从侧面看是一条线、正面几乎看不见。**全部去掉 `alignTo`**（余痕 / 核 / 细碎符文 / ambient 四处），恢复默认广告牌 |
| 46.2 | 为什么当初会误用 | 符文是**对称菱形**，把它「转到位」在视觉上几乎不产生差别 —— 也就是说 `alignTo` 在这里**只带来代价（丢广告牌）、没有收益**。串形状靠的是**位置 + 尺寸 + 亮度梯度**（46.1 之后仍然成立）。这条已写进 `emitSmear` 的注释：**别给这一层加 `alignTo`** |
| 46.3 | **飞行中的小粒子太扎眼** | 细碎符文（`emitMotes`）三处同时收敛：颗数 **6→3.5/tick**（上限 9→6）、尺寸 **0.06~0.11 → 0.045~0.075 格**、亮度（`hot`）**0.30~0.90 → 0.18~0.62**、寿命上限 5→4。死亡补撒（用户认可的那一拍）保持原样 |
| 46.4 | 改回广告牌后**退场判据回退** | 广告牌让每颗菱形在屏幕上的投影变大 → 退场曲线更陡：`50%→5%` 从 5 帧掉到 **3 帧**（要求 ≥4）。余痕寿命 **6→8**（尾迹长度 ≈ 速度 × 寿命 = 0.5 × 8 ≈ 4 格，仍属"近身"）+ 死亡补撒 **12→18 颗 / 最长寿命 12→18** ⇒ 回到 **5 帧** |

**验证（实机录制 + 像素判定 + 像素级复核）**

- `fireball_trail` **PASS**：72/72 帧、丢帧 0、断言 3/3、0 异常；峰值 **1681** 像素、空场起飞 0、末 3 帧 0、退场渐变 **5 帧**；构建 **0 warning**。
- **"扎眼"的量化复核**（飞行段 `t=22` 的连通亮团分析）：剩 **7 个主链亮团 + 2 个头部亮团**，
  带外的游离亮点只有 **4 个像素、亮度 71~74**（此前那一版是成簇的 200+ 亮斑）。
- 目视复核 `contact.png`：飞行段是一条**均匀的符文链**（头部白热、尾部收细变暗），命中拍不变。
- 静止场未受影响（第 45 轮已回归：`black_hole` / `gravity_field` 均 PASS）。

**教训**：用 `alignTo` 之前先问「**这个形状转个方向看得出来吗**」——看不出来就纯亏（丢广告牌）。
PD 的 `alignTo` 是给**线段/拉长的丝**用的（那种确实需要固定朝向），不是给对称点状精灵用的。

## 第 45 轮（2026-10-02，拖尾定稿：符文余痕 —— 清晰轮廓 + MC 魔法感）

> 用户口径（原文，看第 44 轮实机图后）：「**这一版还不如之前那一版呢。不需要加光晕，那个只会增加廉价感。
> 要的是清晰的轮廓和 MC 魔法感。你可以提出几个建议我来选**」。
>
> 我给三组选项（形状 / 贴图 / 配色），用户选定：
> **① 扫过的余痕** + **C 新增「符文/菱形」自定义贴图** + **保持「法术主色 + 白芯」**。

| # | 事 | 结论 / 落点 |
|---|---|---|
| 45.1 | **删掉柔光晕（halo）** | 第 44 轮加的 `emitHalo` 整层删除。用户口径：光晕「只会增加廉价感」——它把轮廓糊掉了，而那正是这一轮要的东西 |
| 45.2 | 尾迹主体改成**扫过的余痕** | `emitSmear`：在本体「上一帧 → 本帧」这段上均分铺 **5 颗符文菱形**，尺寸 **0.38 → 0.16 格**、亮度（`hot`）**0.55 → 0.08** 单调递减。**不给速度**（粒子留在原地由 PD 积分、本体继续飞）⇒ 仍然是 0 逐 tick 包 |
| 45.3 | 「清晰的轮廓」怎么来的 | 每颗菱形用 `alignTo(沿运动方向)` 把长轴对齐 ⇒ 一串**首尾相接、朝向一致**的菱形；尺寸/亮度沿轨迹单调递减 ⇒ 有明确**方向与锥度**。这跟第 44 轮"围着路径乱散的圆点云"是相反的构造 |
| 45.4 | **新增自定义贴图**（本轮唯一的资源新增） | `assets/primalspells/textures/particle/rune_spark.png`（16×16 方形菱形：白热内芯 + 硬边轮廓 + 一圈很窄的消锯齿柔化，占用 112 px）；登记入口 `client/render/ParticleTextures.kt`（`ParticleManager.registerTexture`），沿用「**首个客户端 tick** 注册一次」的时机（`FMLClientSetupEvent` 会崩，第 29 轮踩过） |
| 45.5 | 尺寸口径 | 贴图 16×16 ⇒ `texScale = 1` ⇒ `anisoWorld(w, w)` 的 `w` **就是世界格整宽**（菱形占 14/16 ⇒ 看着的直径 ≈ 0.85 w）。已写进 `ParticleTextures` 的 KDoc |
| 45.6 | 细碎符文（保质感，不拉长尾迹） | `emitMotes`：尺寸 0.06~0.11 格、速度 0.10~0.22 格/tick、寿命 2~5 tick ⇒ 只散开 0.2~1.0 格就消失（用户第 44 轮口径「细小、快速、就近散开消失」保留） |
| 45.7 | 静止场一并换符文 | 黑洞/重力场/`delayed_cast` 的 ambient 改用同一张符文贴图（同一个观感语言） |
| 45.8 | 机位再拉近 | `A=2 / S=4.2` → 相机离火球 **4.65 格**：尾迹改短改细之后，只有这个距离才看得出「一颗颗菱形」而不是一条糊线 |

**验证（实机录制 + 像素判定）**：`fireball_trail` **PASS** —— 72/72 帧、丢帧 0、断言 3/3、0 异常；
`orange` 峰值 **1169 像素**、空场起飞 0、末 3 帧 0、退场渐变跨 5 帧；构建 **0 warning**。
目视复核（`frames/00020.png`）：一条**锥形收细的符文余痕**（头部最大最亮、尾部最小的菱形几乎只剩主色）
+ 头部一颗白热核 + 几颗贴身的细碎符文；**没有光晕、没有点云、没有硬边方块**。

**这轮的教训（写给下一轮的我）**：观感问题**不要用"加一层"解决**。第 44 轮我给头部套柔光晕，
本意是"魔法感"，实际效果是**把好不容易做出来的形状糊掉**，用户直接判「廉价」。
正确做法是**把形状本身做干净**（对齐、锥度、轮廓），而不是在外面加雾。

## 第 44 轮（2026-10-01，拖尾改「细碎火花 + 柔光头部」：去贴图感、去火花带）

> 用户口径（原文，看实机图后）：「**贴图感太重了，要保留 MC 的粒子感风格**。并且那些小粒子**太远了**，
> Noita 原本的设计是**很细小的粒子很快的向附近散开消失**。而且那个**「本体粒子」太突兀了**。
> 要的不是**一路火花带闪电**的感觉，而是**魔法感**。」

按四条反馈逐条改（全部落在 `Trajectory.kt` + `ProjectileBody.kt`）：

| 反馈 | 病因 | 改法 |
|---|---|---|
| 「贴图感太重」 | 头部亮核用 `LINE` 贴图 + 方比例 ⇒ 一颗**硬边白方块**；丝线又长又粗（0.45 格 × 0.075） | 核改用 `SOFT_DOT` 柔边点；丝线压到 **长 ≤0.18 格、可见粗 0.035**；所有粒子尺寸砍到 **0.05~0.14 格**（MC 粒子量级） |
| 「小粒子太远了」 | 出生带拖到后方 **1.5 格**、侧向半径 0.22、寿命 **8~26 tick** ⇒ 火花散成一大片 | 出生带压到 **0.05~0.55 格**、侧向半径 **0.10**、寿命砍到 **2~6 tick**；散开速度提到 **0.10~0.22 格/tick**（**快**，但 `v·t` 只有 0.2~1.0 格就消失）—— 即 Noita 的「细小、快速、就近散开即消失」 |
| 「本体粒子太突兀」 | `ProjectileBody` 一直在用 PD **默认贴图** ⇒ 硬边纯白方块 | 改用 `ParticleVisual().style(SOFT_DOT).glowing(true).additive(true)`：读成一团柔光 |
| 「要魔法感，不是火花带闪电」 | 长丝线 + 长寿命火花 = 火器曳光弹 | 新增 **halo 柔光晕层**（每 tick 4 颗更散更暗的柔边点，寿命 4 tick，半径 0.30 格）：头部由「一颗亮块」变成**一团会呼吸的微光**；下坠力 0.030 → **0.012**（寿命短了，只需一点点"重量感"） |
| 「保留 MC 粒子感」 | 靠大尺寸/长寿命堆观感 | 改成**多而小**：火花每 tick 8~12 颗（原 7.5）、尺寸 0.05~0.09 |

**验证（实机录制）**：`fireball_trail` **72/72 帧 / 丢帧 0 / 断言 3/3 / 0 异常**，
`orange` 像素峰值 **798**（改前 819）、空场起飞 0、末 3 帧 0、退场渐变跨 6 帧；构建 0 warning。
目视复核（`frames/00010.png`、`00020.png`）：尾迹是**贴着弹体的一小团细碎火花**（约 2~4 格），
头部一团柔白光晕，没有硬边方块、没有长丝、没有拖尾带。

**这一轮把上一轮的审美结论也修正了**：第 43 轮追求「连续亮芯」而把丝线做长做粗 —— 那是**火器**的观感；
Noita 火花弹的观感恰恰相反（`SPARK_LIFETIME 2~6` 这一档才是它的手感）。

## 第 43 轮（2026-10-01，法术拖尾重做成 Noita 火花弹式分层尾迹）

> 用户口径（原文）：「优化法术拖尾的特效，改的像 Noita 那样细致的感觉」，参考 Noita 火花弹演示
> （`noita.wiki.gg/zh/images/Demo_light_bullet.gif`，逐帧看）。对齐结论：**拖尾 + 静止场一起改**、
> **保留每个法术的主色、另加一层白热核心**、**新增实机录制场景作证据**。

| # | 事 | 结论 / 落点 |
|---|---|---|
| 43.1 | 原来只有「每 tick 两颗随机小点」 | `Trajectory` 从 111 行改成 **395 行三层结构**：**bead 白热核**（`LINE` 贴图方比例，贴着本体、略快于本体被甩在后面）、**filament 丝线**（`LINE` 两端渐隐、近零速「钉」在轨迹上 ⇒ 一根**连续亮芯**）、**ember 余烬**（`SOFT_DOT`、反向初速 + 侧向散开 + **恒定下坠力**）。位置沿「上一帧 → 本帧」这段位移铺，所以尾迹是**拉出来的**而不是堆在头部 |
| 43.2 | 「保留主色 + 加白热核」怎么落地 | `hotColor(hot)`：`color.lerp(白热, hot·0.75)` → 头部白黄、尾部主色，再乘亮度与 alpha 的双增益。⚠️ **`COLOR_TOWARD_WHITE` 别调大**：`hot` 的高段是**余烬主体**，调大整条尾迹褪成白黄、丢掉法术辨识度（实测 1.15 → 0.75 才回到"橙尾 + 白芯"） |
| 43.3 | 静止场（黑洞 / 重力场 / `delayed_cast`） | ambient 分支从「原地点几个点」改成**球面缓漂余烬 + 偶发白核**（半径 0.25~1.30 格的一圈火星），`BlackHole` / `GravityField` 无需改接线 |
| 43.4 | 静止时「吐火星」的观感 | 位置按黄金角摊在球面上（`spherePoint`），速度朝外且很慢 |
| 43.5 | 死亡补撒 | `onDie` 撒 10 颗余烬（**寿命错开 8~26 tick**）。⚠️ 见 43.9 |
| 43.6 | 随机数**确定性** | 不用 `Random`，改整数哈希取位段（`hash01`）：同一颗投射物同一 tick 永远同一组偏移 —— 实机录制与离线自检要求可复现；取偏移用哈希而不是递推序列，避免相邻粒子取到相邻值而聚成串 |
| 43.7 | 每法术强度旋钮 | `Trajectory(color, intensity)`（默认 1，0 = 关掉该法术尾迹）；9 处接线（火球/触发火球/激光/传送/无人机弹/无人机群/延迟施法/黑洞/重力场）**签名不变**，只在需要时用得上 |
| 43.8 | 新增实机录制场景 | `fireball_trail`（`CaptureScenes`）：**前置回望机位**跟拍火球 72 tick，断言「尾迹建起来 ≥18 颗」「收干净」「PD 粒子归零」；`assemble.py` 同步加 `SCENES` 条目 + **新的 `orange` 像素判据** |
| 43.9 | **退场成悬崖**（判据抓到） | 死亡补撒原本给同一个常数寿命 ⇒ 整批火花同一 tick 到点、`50%→5%` 跨 **0~3 帧**，`decay: gradual` 判 FAIL。改成寿命 8~26 tick 错开后跨 **11 帧** |
| 43.10 | **场景比粒子寿命短**（判据抓到） | 场景 60 tick 时收尾断言 `pd-particles-zero` 报 `remaining=1`：死亡补撒最晚在 `33+26=59` 才到点，只差 2 tick。场景拉到 **72** 后转绿（「长度 ≥ 收球 tick + 最长寿命 + 余量」写进常量注释） |

**踩坑留档（都是这轮真金白银试出来的）**

1. **加法混合下 alpha 就是亮度权重，而且没有「逐 tick 淡出」的通道** —— 「越靠尾越暗」只能在**生成时**
   写进颜色与 alpha（口径已补进 [design/effects-api.md](../design/effects-api.md) §0.4 第 4 条）。
2. **拖尾的机位是几何题，不是审美题**：尾迹沿飞行方向铺开，相机与飞行方向的夹角 θ 决定屏幕投影
   （≈sin θ）。同一份代码换了五组机位：正后方跟拍 → 压成一小团；`B=8/S=12`（θ≈34°）→ 只剩 56%；
   侧拍 `B=3/S=8` → 93% 但相机 20 格外、粒子只有几个像素；**最终"前置回望" `A=3/S=5`**
   （相机离球 5.8 格、θ≈59°）。整条链上还顺手踩了两次「相机被塞进影棚墙体/从墙顶穿出去」——
   跟拍机位必须**留够棚子宽度与墙高**。
3. **场景里施法朝向不会自动对齐**：`placeForCast` 只改位置，`Wand.spell` 读的是玩家当前朝向，
   而预热期机位函数已经在改它 —— 不显式定住朝向，火球会沿预热的视角**斜着飞、t≈17 扎进地面**
   （实测 `v=(0.30,-0.21,-0.34)`）。已在 `start` 里显式 `setYRot(180)/setXRot(0)`。
4. **别用 `grep "BUILD (SUCCESS\|FAILED)"` 当编译判据**：Gradle 失败时打的是 `BUILD FAILED`，
   不匹配那个正则 —— 本轮有一次 `setRotation`（该映射里不存在）编译失败被静默放过，
   白跑了一轮录制才发现。**要看退出码**。

**验证（实机录制 + 像素判定）**

- `fireball_trail`：**72/72 帧 / 丢帧 0 / 场景内断言 3/3 / 运行日志 0 异常**；`orange` 判据峰值
  **822 像素**（空场起飞 0、退场末 3 帧 0、退场渐变跨 11 帧）；`trail-built-up` 在 `t=20` 量到
  **120 颗粒子**（三层累积）；调机位前的同一场景峰值是 **1369**（相机 5.9 格时的对照值）。
- 目视复核（`build/capture/out/fireball_trail/contact.png` + `frames/00009.png`）：
  头部一颗**白热核** → 一串**连续橙丝** → 主体向外散开、**按重力往下掉**的一圈余烬，
  亮度从白黄逐级降到暗橙；这与 Noita 火花弹「亮芯 + 密集火花 + 会掉」的结构一致，且保留了法术主色。
- 构建 `./gradlew build` 通过，**0 warning**。

**仍可继续打磨（留给下一轮）**

- 余烬的**亮度层次**还能再拉开（现在靠 `hot` 的 0.15~0.65 段 + 双增益，尾部偏暗）；
- `ProjectileBody`（本体）仍是**硬边白方块**（历史实现，用 PD 默认贴图）——
  用户这轮只点了拖尾，若要「本体也换柔边/白热核」是下一件事（一行 `ParticleVisual` 的事）；
- 侧后机位下**尾迹最老的一端**仍有透视压缩，想完全摊平得上正交感机位。

## 附录 A：历史崩溃记录（已有防护，保留备查）

- `run/client/crash-reports/crash-2026-08-10_06.39.49-server.txt`：
  `Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]` → 服务器 tick 崩溃。
- 现在由 `registry/ModDamageTypes.projectileSourceOrNull()` 统一解析：数据包缺少该伤害类型时
  只记一次错误日志并**跳过本次伤害**，`Hurt` 与 `Explosion` 都走这条路。

---

## 附录 B：ParticleDrawing 已闭环条目（备查）

### 1.0.15-ALPHA：第62轮闭环（2026-10-09）

本地ParticleDrawing编译与49套/322项测试通过；对应接线为发射器jitter/life/lifeCurve覆盖、scaleTo零尺寸/当前倍率接续、Movable/updateAnchor及插值空间变量。
速度/力/位置/锚点/销毁载荷由前置统一按协议上限拆包。双黑洞120tick及1024成员实机结果见第62轮记录。
以下保留当时登记原文，原“未决”字样为历史状态；本节的主要缺口已闭环。更严格的逐渲染帧组尺寸/变量保证另在pd-api-requests登记，不能把这5项接线当成高刷全部完成。

### [需求] 发射器无「逐粒子位置抖动 / 偏移」

- **状态**：未决
- **场景**：拖尾除了"沿路径铺一串"，还需要一点点**侧向不齐**（本侧原先用 0.045 格的抖动做"手划过的痕迹"）。
- **现有 API 为什么不够**：`ParticleEmitter` 只有 `spacing`/`interval`/`life`/`scale`/`color`/`velocity`/曲线，
  **没有 offset / jitter / spread**；要逐颗抖开就只能回到 `spawnAll` 逐颗给位置（那就是"每颗一发"，发射器省下的带宽又还回去了）。
- **期望形态**：`emitter.jitter(格)`（垂直运动方向的随机偏移，确定性哈希）+ 可选 `offsetAlong(前后偏移)`；
  或 `spread(半径, 数量)` 之类的面/体发射口。
- **影响面**：`magic/effect/visual/Trajectory.kt`（当前被迫放弃侧向抖动）。
- **临时降级**：不加抖动（链条更直、观感尚可，用户未反对）。

### [需求] `EmitterHandle` 不能改 `life` / 曲线

- **状态**：未决
- **场景**：本侧 `life = 拖尾长度 ÷ 速度 ÷ 可见占比`；投射物**带阻力**，速度逐 tick 衰减（实测 0.485 → 0.382 格/tick），
  寿命必须跟着变，否则尾迹长度会漂。
- **现有 API 为什么不够**：`EmitterHandle` 只提供 `updateAnchor` / `spacing` / `interval` / `stop`；
  `life` 与曲线是**声明时定死**的 ⇒ 本侧只能在速度漂移超过 20% 时 `stop()` + 重新 `spawn()` 一次。
- **期望形态**：`EmitterHandle.life(ticks)` / `.curve(...)` / `.scale(...)`（与 `ParticleEmitter` 同名同义，
  走小包更新，已生成的粒子不受影响）。
- **影响面**：`Trajectory.ensureEmitter`（当前的重挂逻辑 + `SPEED_REBUILD_RATIO` 阈值）。
- **临时降级**：速度相对变化 >20% 就重挂声明（旧粒子各自走完寿命，视觉无跳变）。

### [需求] 批量速度更新超过512成员导致客户端断线

- 状态：未决，2026-10-08第55轮；本侧停止该接入点的修复尝试。
- 目标模型：移动/互吸黑洞仍保留完整星尘，公开ParticleBatch.setVelocityAll应支持其全部存活成员，保持ID和速度一一对应。
- 实际证据：run/client/logs/latest.log于13:38:16与13:38:46记录particle_velocity_batch解码失败，条数分别519和514；这是本次双黑洞问题的实际日志；本轮全量实机回归再次复现517条载荷断线，证据保存在build/verify/black-hole-batch-disconnect.log。crash-reports最新文件为旧版本2026-08-10的伤害注册表错误，与这次断线不同。
- 现有能力：1.0.14-ALPHA的ParticleVelocityBatchPayload.MAX_BATCH=512，decode限制0..512，encode却直接写updates.size；ParticleBatch核心setVelocityAll经aligned把全部成员交给sendVelocities，没有公开API要求调用方限制512成员。
- 修改需求：在PD发送端自动拆包，每包不超过MAX_BATCH；对速度/位置/力等批量载荷统一审计编码与解码上限，并测试0/512/513/1024成员、过期成员回收后ID与向量顺序、客户端不掉线及双端最终状态一致。
- 影响：BlackHoleFx.update的星尘吸入批次；移动黑洞远离旧星尘时容易积累9×60tick的存活成员，超过512。
- 本侧处理：不截断星尘、不逐粒替代批量API、不修改lib前置jar；双黑洞长时视觉验收仍被此前置缺陷阻断。

### [需求] 组缩放继承当前倍率并提供零尺寸入场

- 状态：未决，2026-10-09第61轮；停止多段ScaleBy入场接入。
- 目标模型：固定结构由零尺寸展开，提前死亡时从当前姿态收尾，追加缩放不重置已有倍率、脉冲或旋转。
- 实际能力：ParticleGroup.scale声明相对倍率，但ClientAnimationProgramManager.applySlot对每条ScaleBy直接赋值`scaleMul=1+(ratio-1)*k`，没有当前倍率快照；`.scale(.01,0).scale(100,3)`被后一条覆盖而非恢复至1；从1.5倍接退场也先重置到1。sugarFrame还把最终视觉尺寸钳到0.001。
- 期望API：`group.scaleTo(target,ticks,easing)`与`group.scaleBy(ratio,ticks,easing)`明确区分；执行起点捕获当前合成变换，支持从0展开、任意时刻退场、帧级重定向；尺寸0应允许完全不绘制。Draw形状生成入口支持ParticleLifeCurve，方便实色片元的入场而不缩整个形状。
- 影响面：黑洞、重力场、水晶、冰壳、斥力场、爆炸火团的组级入场/退场。
- 本侧降级：取消错误的多段缩放入场；固定结构保留既有出生形式。仅从完整1倍结构追加有限缩到零的退场，爆炸取消额外膨胀倍率，力量感由移动火团与冲击环表达。已接入的单片尺寸寿命曲线继续使用现有PD通路。不能保证提前死亡或脉冲中断时组形变完全连续；前置修复后恢复验证，不能在本模组复制缩放播放器。

### [需求] 移动锚点及热更空间变量提供相邻样本插值

- 状态：未决，2026-10-09第61轮；停止改写锚点/变量插值器。
- 目标模型：移动黑洞/重力场、水晶、持续光束与拖尾的所有构件在高刷下共用连续的位姿，相位一致，不能20Hz闪移。
- 现有能力：ParticleGroup.setPivot下发BindPivot(Fixed)，客户端applySlot直接替换pivotFixed；PlasmaBeam使用setVariableLive空间端点，热更新是立即赋常量。现有Emitter Movable锚点按最后样本位置+速度×partialTick外推，未用相邻样本插值，已在本文件§1记录；track头部与尾迹因此不严格同相位。
- 期望API：`group.anchor(Anchor.Movable)`/`updateAnchor(prev,current)`、`setVariableInterpolated(name,value,ticks,easing)`；位置与朝向支持相邻服务器样本插值，明确断流、暂停、瞬移语义；发射器与运行时头部沿同一锚点求值。
- 影响面：BlackHoleFx.followCenter、GravityField移动场、CrystalReaction.setPivot、PlasmaBeam端点、Trajectory.ensureEmitter。
- 本侧降级：保留现有锚点、空间变量与发射器通路，不在本侧保存/积分一套替代粒子位姿；本轮改善起落曲线，移动的帧间连续性仍待前置补齐。


> **1.0.12-ALPHA 一次性闭环了四条**（本模组第 15/17/19 轮登记），本侧相应的简化见第 20 轮 20.1：
>
> | 需求（登记轮次） | 1.0.12 的落地 | 本侧简化 |
> |---|---|---|
> | 组指令缺「从现在起」的时间锚点（第 17 轮） | 新增 `api/GroupClock`：**增量追加的指令按「从现在起」换算**（每次跨 tick 的录制开启新会话），`fadeOut(removeAfter)` 的销毁延时也按同一口径从当下起算（`scheduleDestroy`） | 护盾消散回到 `delay(阶梯) + fadeOut(5, removeAfter = true)`；删掉「自己算游标 + 自己排销毁 + `bornAt`」 |
> | `spin` 与一次性 `rotate` 应可叠加（第 19 轮） | 多条 `rotate`/`spin` 的角度**累加**，后一条不再覆盖前一条的相位 | 「先自转一阵、再补一段一次性旋转、继续自转」可以直接写；本侧目前不需要补相位（不重建组） |
> | `scale`/`pulse` 应连「粒子到轴心的距离」一起缩放（第 15 轮） | `ScaleBy`/`Pulse` 同时作用于**到轴心的距离**与视觉大小（KDoc：半径 3 的圆 `scale(3f)` 后半径就是 9） | 尚未使用（黑洞进场仍是逐粒子 `move`）；后续「整壳胀开/缩回」可一个调用写完 |
> | `move()` 不应悄悄改 arm 基准轴心（第 15 轮） | `move`/`movePath` **不再改轴心**（轴心只由 `setPivot`/`followEntity` 决定），平移只作用于渲染位置 | 「铺完成员后第一条指令不能用 `move`」这条顺序硬约束解除

### 1.0.14-ALPHA：运行时发射器 / 插值语义 / spawn 内寿命曲线 / 批量生成（第 48 轮登记 → 第 48 轮迁移）

> 起因：第 47/48 轮的实机反馈逼出四条"**运行时通路**"的能力缺口（"高刷出现/淡出"、"严格在本体后面"、
> "调一次观感就要改一次代码"）。本轮**按「目标模型 → 现有能力 → 缺口」重写了需求单**（原文见下方
> 「登记原文（备查）」；需求单里已移走），PD 侧 commit `d998e36` 一次落地。

| 需求（第 48 轮登记） | 1.0.14 的落地 | 本侧因此改成了什么 |
|---|---|---|
| ① 运行时发射器（出现/淡出逐帧、带宽 O(1)） | `api/ParticleEmitter`（`spacing`/`interval`/`intervalMs`/`life`/`scale`/`visual(...)`/`maxAlive`）+ `EmitterHandle`（`updateAnchor`/`stop`）；`ClientEmitterManager.frameTick(partialTick)` **每渲染帧**发射；`Anchor.Movable` 按「上一 tick 位置 + 速度 × partialTick」取本帧位置；`EmitterAdvance` 在段内**插值落点**发射 | 拖尾整段塌成**一句声明**：`.spacing(0.15).life(life).scale(3.0f).texture(RUNE_SPARK).glowing(true).additive(true).sizeCurve(...).alphaCurve(...)`；每 tick 只 `updateAnchor` |
| ② 位置指令的插值语义统一 | `ParticleSpawnSpec.prevPosition(prev)`（首帧插值端点，与 `track` 同语义） | 不再需要"采样段整体延迟一 tick"的绕法；改由**锚点压到上上 tick**保证尾迹永不越过头部（48.4） |
| ③ 逐粒子寿命曲线写进 spawn | `ParticleCurve`/`CurveKey`/`ParticleLifeCurve` + `fadeOut`/`shrinkTo` 糖；`RenderParticle.refreshCurve` 按**墙钟**逐帧采样 | "逐渐变小/变淡/末尾消失"变成曲线参数；每颗省掉 2 个更新包 |
| ④ 批量生成 | `ParticleManager.spawnAll(specs)` / `ParticleBatch.spawnAll` + 既有 `setVelocityAll`/`applyForceAll` | 细碎符文、死亡补撒、静止场 ambient/退场余晖：**1 包生成 + 1 包速度 + 1 包力**（原先逐颗 3 包） |
| （更早那条）组级按成员各自方向漂散 | 组级**逐成员漂移** | 尚未使用（护盾当前仍"一片一组"） |

**迁移中发现的两个小缺口**（已登记进 [pd-api-requests.md](pd-api-requests.md) "当前未决"）：
① `ParticleEmitter` 无**逐粒子位置抖动/偏移**（原先那 0.045 格侧向抖动只能放弃，链条更直）；
② `EmitterHandle` **不能改 `life`/曲线**（速度漂移 >20% 时本侧只能重挂声明 —— 能用但不优雅）。

**登记原文（备查；需求单里已移走，这里保留当时的原话）**

- **背景（为什么按"组"提）**：本模组前几轮的做法是"碰到边界就绕"——采样段延迟一 tick、按里程逐颗
  `create().spawn()`、把"头部白热/末端压暗"烘焙进生成时的颜色与 alpha。结果是：**特效每调一次参
  就要改一次代码**（长度 8 格→3.5 格、间距 0.5→0.15 格，每次都重写 + 重录 + 判读），
  而且绕法本身会制造新缺陷（相位差、缺颗空档）。
- **根因**：PD **已有**声明式、逐帧求值的一整套（`Anchor`、`FadeIn`/`FadeOut`/`Recolor`/`ScaleBy`/
  `Translate`/`RotateOnce`/`MovePath`/`Spin`/`Pulse`/`Expression` 指令集、组级 `fadeOut`/`scale`/`movePath`），
  但**只对"播放一段预制动画"开放**；运行时通路（`ParticleManager.create()` + `ParticleHandle`）
  只有"生成时定死外观 + 逐颗指令"。
- **1｜运行时发射器**：期望 `manager.emitter(anchor).spacing(格).life(ticks).sizeCurve(...).alphaCurve(...)
  .colorCurve(...).spawn()`，**客户端按渲染帧求值**、服务端只声明一次；现状是服务端每 tick 采样逐颗下发
  ⇒ 出现时刻被 tick 量化、带宽 O(粒子)（每颗 1 spawn + 2 update 包）。
- **2｜位置指令的插值语义统一**：期望把 `Anchor.Movable` 那套语义同样给程序化粒子与 `track` 粒子
  （或加 `interpolate = false` 开关、或暴露客户端 partialTick）；现状是 `track` 走 `TrackBuffer` 插值、
  程序化粒子按绝对位置渲染且不插值 ⇒ 同类粒子在屏幕上最多差**一整 tick 位移**（实测 0.45 格）。
- **3｜逐粒子寿命曲线写进 spawn 包**：期望 `Builder.fadeOut(ticks, easing)` / `.shrinkTo(...)` /
  `.curve(通道, 关键帧)`（零额外带宽）；现状是"寿命内淡出 + 收缩"要额外 2 个更新包
  （≈23 颗/投射物 ⇒ ≈70 个包）。
- **4｜批量 spawn / 批量寿命曲线**：期望 `ParticleBatch` 补批量 spawn 与批量曲线；现状只能逐颗。
- **本侧边界（当时就写明没推给 PD 的部分）**：游戏语义留在本模组 —— 拖尾长度按格、寿命＝长度÷速度、
  按里程发射、总强度倍率、静止场另一套分支，都由我们算好再翻译成 `spacing`/`life`/`scale`/曲线。

**同批实现的更早一条：组级 `move` 支持「按成员各自的方向」飘散**（第 39 轮登记）

- **场景**：护盾被打穿时那批面板碎片应当**各自沿自己的法线向外**飞；而爆炸吹开的整罩碎片是
  **整批沿同一个方向**（爆点方向）。后者一个组就够（第 39 轮已合并），前者只能**一片一个组**（≤7 组），
  而组数直接等于 arm 日志行数与 arm 开销。
- **期望形态**：`group.moveAlongOffset(scale, durationTicks, easing)`（每个成员沿自己的 `offsetFromPivot`
  方向平移 `scale × |offsetFromPivot|`）或 `group.moveEach(deltas, durationTicks, easing)`。
- **落地**：1.0.14-ALPHA（`d998e36`）加了组级**逐成员漂移**。
- **本侧现状**：尚未迁移（护盾仍"一片一组"）；迁移后能拿回那部分组数与日志。

**同批闭环的更早一条：`armed` 日志降级为 DEBUG / 可开关**（第 41 轮登记，2026-10-04 核实）

- **登记原文（场景）**：护盾每次受击都会**新画一批面板碎片**（碎片不跟人、每次新画），每个面板一个组
  → 一次受击 arms **32 个组**（截角十二面体 = 12 五边形 + 20 六边形），生存模式实战里日志被刷屏。
  实机日志原文：`[ParticleDrawing] program <uuid> armed: 72 particles, 0 instructions, anchorOffset=1`。
- **当时的缺口**：`core/client/ClientAnimationProgramManager` 每次 arm 组都打一行 **INFO**，
  使用方在模组侧关不掉（也不该让使用方改 log4j 配置来消音前置模组）。
- **期望形态**：arm 日志降到 DEBUG，或给一个开关。
- **落地（含在 1.0.14-ALPHA）**：arm 正常行改为 **DEBUG**；开关有两处 ——
  运行期 `ParticleManager.setDebugLogging(true)`，或客户端配置 `debugProgramLogging`（默认 `false`）；
  **异常分支**（"armed with zero known particles … client spawn packets missing?"）仍留 INFO，这是有意的
  （那是真出问题时的诊断）。
- **本侧处理**：**无需改动**（默认就安静了）。排查"程序到底有没有 arm、arm 了几颗粒子"时再打开开关。
- **核实方式**：从 `lib/particledrawing-1.0.14-ALPHA-sources.jar` 抽出的
  `api/ParticleManager.kt`（`fun setDebugLogging`）与 `core/client/ClientAnimationProgramManager.kt`
  （INFO/DEBUG 两条 `LOGGER` 分支 + `DebugFlags.verboseProgramLogging` 判断）逐条对上。

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


## 第 49 轮（2026-10-08，区域法术视觉重构）

- 黑洞由稀疏球壳改为小粒子暗核、双层倾斜螺旋吸积盘、边界星点和吸入星尘；
  `BlackHoleFx` 分离表现与结算，退场直接给原组追加旋转塌缩/淡出，删除重铺、反弹及 Deferred 三拍。
- 重力场改为断开球面经线、顶部发源环、加速落体细线、向下尖刻度波、地面双井环；
  修好台账中的固定结构一帧消失，改为 10 tick 淡出。玩法参数保持原值。
- 修复本机隐藏录制窗口变成 1×1：显式设置离屏尺寸，装配器检查所有帧尺寸；
  修复控制台 GBK 无法打印损坏日志字符：UTF-8 + 替代字符输出。
- 构建通过；两场实机录制 108/130 帧、零丢帧，全部场景与视觉断言通过，末尾 PD 零残留。
  OSHI 环境日志仍使总体失败，登记到 open.md。造型、预算与验证限制见 design/field-effects.md。
- 本轮 ParticleDrawing API 无新增需求，未升级依赖。


## 第 50 轮（2026-10-08，黑洞缩小与粒子透镜仿作）

- 按用户要求将暗核半径 1.05→0.65，密铺粒子 240→160，尺寸 2.1→1.4；
  盘外半径 3.78/4.23→2.78/3.13，玩法范围与引力参数保持原值。
- 增加固定几何的上方亮主弧与下方弱副弧，交叉两个平面绕过暗核；
  单个 PD 组负责淡入及塌缩淡出，无新增逐 tick 位置包。
- 用户明确「先不用管随每位玩家视角准确变化」：本轮只仿作亮弧，不扭曲背景。
  此前提出的相机 getter 需求撤出本轮未决台账；未来若重新要求视角相关表现，再按 PD 政策登记。
- 构建通过；黑洞 108/108 帧、1280×720、零丢帧，全部场景与视觉断言通过，末尾 PD 零残留。
  总体日志门禁仍因已登记的本机 OSHI 环境异常失败。


## 第 51 轮（2026-10-08，移动区域特效跟随与外环惯性）

- 根因一：黑洞与重力场固定结构只在出生时绑定坐标，移动本体没有更新组轴心。
  黑洞暗核/内盘/核边/边界逐次移动调用 `setPivot`；重力场球壳/地面井环同样跟随，移动后重新探测地面。
- 根因二：`AreaFieldEffect` 默认 MODIFIER，在 Move/轨道 OVERRIDE 之前读取位置；
  改为 VISUAL，范围结算与视觉统一使用最终场心，不调整范围与施力常量。
- 黑洞去掉交叉透镜弧，改细核边亮带；两层外环由 PD 表达式做有限惯性滞后和沿移动方向的椭圆拉伸，
  急停回圆、反向平滑切换，瞬移清零滞后。没有替代粒子引擎或新增 PD API 缺口。
- 同类检查：拖尾头部/发射器已读最终位置；护盾破口已随持有者。历史尾迹、下压波、受击碎片、爆炸
  保持发生位置是设计行为。自然地形的落地细节仍按 verify.md 留待验收。
- 构建通过；静止黑洞、移动黑洞、移动重力场录制分别 108/107/130 帧，移动黑洞丢 1 帧（预算内），
  全部 1280×720，场景断言 4/5/5 项通过，末尾 PD 粒子数为零，视觉判据通过。
  已检查移动/停顿/反向原始帧与拼图；证据 `build/capture/out/`。整体日志门禁仍因既有 OSHI 环境异常失败。
- 录制夹具通过真实施法并追加 OVERRIDE 定位；需等 pendingAdd 入列后再装定位效果，已修正首次录制中的过早访问。

移动黑洞明亮内盘的像素回归通过：t=18/41/66 横向中位中心为 611/510/702px，
往返两侧均离开出生点超过 60px，避免仅验证服务端位置而漏掉渲染不跟随。


## 第 52 轮（2026-10-08，七张可组合的新法术卡）

新增休眠水晶/水晶引爆、连锁电弧、远距施放、等离子束、斥力场、冰冻附魔；
七套物品/模型/原创像素图标/中英文描述同步，注册表44项。完整参数及组合见
[design/spell-expansion.md](../design/spell-expansion.md)。

本轮修复的接入缺陷：

- 静止法术的初速会把共享来向向量乘成零：Projectile 保存 launchDirection，Velocity 接受副本。
- 触发荷载出生点可能仍深入墙表面：LongDistanceCast 只允许沿来向到相邻1/8格空气修正，
  保留整块墙截断。实机黑洞墙前停在z=-2.15，触发墙面的反向荷载前移到z≈-8.074。
- 无实体碰撞的束源被旧护盾入口忽略：DirectDamage 声明单次伤害强度，无碰撞射线也按真实线段接盾。
  连续命中测试盾蓝1000→985，零伤害、未冰冻；电弧命中盾蓝1000→995、未冰冻。
- 电弧首击复活并停止后，Move 曾继续当前候选/子步循环；立即终止两层循环。
  两个重叠木桩测试各接受4/2.8伤害，链不会在首击后被再次碰撞杀死。
- 新卡重复添加通用描述行：移除多余 initLore 覆写，沿用 Magic 的描述生成。

表现与录制夹具：光束最终端点表达式+10tick线性淡出；电弧折线光点+8tick淡出。
电弧/护盾混合场在独立游戏tick8–64时间窗检查可见性与渐隐，保持相同最低像素门槛，
防止护盾玻璃亮度掩盖电弧。组合场使用移动木桩施法者，避免录制相机重置玩家位置；
延长至180tick，给最后电弧拖尾完整的26tick寿命，不关闭零残留断言。

构建通过，无Kotlin编译警告；七套资源与两种语言键以及打包JAR检查通过。
最终五场共835/835帧、零丢帧，30/30项玩法/清理断言通过，全部1280×720；
各场末尾投射物和PD粒子均为零，五场可见性/退场渐变/无残留像素判据全部通过。
已检查水晶、侧面光束、移动环绕光束和斥力场原始帧；录像及像素判据结果见 `build/capture/out/report.txt`。
本机既有OSHI环境日志异常仍保留门禁，
不宣称流水线整体通过。自然环境、联机与压力验收留在 verify.md；无新增ParticleDrawing API请求。


## 第 53 轮：MC法术视觉与全量贴图（2026-10-08）

用户要求每次更新提交Git，法术整体遵循MC风格，取消渐变半透明颗粒，并重画旧法术贴图。
先以 `新增：扩展法术与区域特效` 提交前轮已完成的七卡与区域特效，再独立提交本轮视觉重写。

- 全部法术视觉统一硬边SQUARE/默认方形，不用SOFT_DOT/LINE、additive、alpha曲线或透明度淡出。
  RGB为有限色阶，退场由PD尺寸动画、有限寿命和destroyAfter处理；伤害、施力、抽牌与黑洞移动惯性保持原行为。
- 黑洞小暗核与吸积臂、重力波、青蓝光束、电弧、紫水晶、冰壳和橙色斥力边界改为实色方粒。
  护盾填充采样降至1/5，使用实色蓝边/方屑，破口实色红点，避免不透明填满屏幕。
- 爆炸改为64–160外飞碎片、4短命闪块、20灰尘屑，灰屑受世界光照，取消半透明球壳和柔光烟雾。
  录制夹具在半径6场景的死亡回调内检查184颗初始粒子、同tick伤害与age=1，稍后读取快照，
  避免客户端计数领先服务端的竞态；同时保留末尾零残留检查。
- 修正火球色阶阈值导致尾迹全白的问题；稍增通用弹体与尾迹的方粒尺寸，保持同尺寸连接。水晶棱线加粗为0.75尺度，退场用10tick线性尺寸收缩，
  保持远机位组合场的可见性与退场跨度。
  移动黑洞像素追踪以DISC_INNER色板窄容差识别内盘，排除惯性外环和DUST_BRIGHT历史星尘；原两侧60px位移门槛保持不变。
- 44张已注册法术卡均重新绘制；32×32逻辑像素最近邻放大64×64，alpha仅0/255，单图最多16色。
  原创机制符号、金色投射物边框与紫色修饰器边框；注册id、模型引用与语言键不变。
  重建脚本核对注册表全覆盖；法杖、包与木桩资源未改。

验证结果和证据见 [design/minecraft-visuals.md](../design/minecraft-visuals.md)。
无新增ParticleDrawing API请求；仅使用锁定版本公开的组、批次、表达式和方粒样式。


## 第54轮：法术质感与组合修复（2026-10-08）

- 小/大爆炸在onSpell重设参数，覆盖修饰onActive：基础爆炸参数提前到构造阶段，扩散/易爆/去爆按牌序生效。
- 生成子弹丢修饰、零速源归一化产生无效方向：统一有序配方继承、静止源方向兜底，后代独立effect；子弹速度抵消基础speed和速度卡的重复倍率。
- 瞬时静止法术的集束/复制/定时发射没有触发时机：赋予运动或至少40tick发射窗口，生成弹只保留一份爆炸生命周期。
- 等离子束写死40tick截断延寿：改读maxAge，保持真实持续攻击与墙挡/护盾接入。
- 护盾整组缩小、水晶线框、爆炸匀速撒粒子观感不足：替换为固定尺寸晶格剥落、实体多晶面晶簇、闪爆/喷射/三向冲击/分时烟团。
- 晶面初版误把alignTo长轴当成法线：按表面法线计算三轴spin，方粒实际贴在晶面上。
- 多枚晶体被PD默认可见容量截断：用公开配置提高高配档上限，保留公开粒子生命周期和容量保护。
- 被反射魔法弹只改投射物caster会留下effect旧归属：护盾弹同时同步所有effect施法者。
- 取消休眠晶体每人24枚的旧限制，允许导能/冻结/引爆配方叠加，死亡仍统一回收。

实机验证结论见 [design/spell-quality.md](../design/spell-quality.md)。

第54轮验收：完整构建无警告；13场2309帧、零丢帧，81项玩法/清理断言与所有像素判据通过。
448组合矩阵含独立effect、有序加乘与静止生成源激活；当前已知OSHI异常仍留在open，不计为修复。


## 第55轮：静态源碰撞与穿刺积分（2026-10-08）

- 区域场/静态功能源此前即使hitRadius=0仍做地形碰撞，受其它场牵引后可能误死。现在collisionEnabled独立于motionEnabled，关闭碰撞时直接积分，不查询地形或实体。可飞炸弹及水晶仍保留接触行为。
- throughBlocks此前在实心方块处return，未写newPos，穿刺弹会停在墙前。现在跳过地形分支，正常完成位移。
- 追踪与子弹发射的零向量不再无条件归一化，避免重合目标生成NaN。
- 移动黑洞PD批量包断线仍未解决，留在open.md和pd-api-requests.md，不能与本节混为已修复。


第55轮验证补充：build通过；spell_stability中t40双场存活并穿墙，age=0的首tick追踪已经偏转，穿刺弹由z=2.8移动到3.587；末帧投射物和PD粒子均为零。晶体本体624颗。长时PD断线另列未决。


## 第56轮：录制第三人称标记与夹具（2026-10-08）

CaptureDriver此前只记录Scene.thirdPerson，没有应用到客户端CameraType；现在场景切换时明确选择FIRST_PERSON/THIRD_PERSON_BACK。
实机确认第三人称能看见玩家与完整脚下三角形，第一人称仍按实际瞄准机位查看汇聚点。
持杖验收的法杖在预热阶段存入玩家背包，避免无人持有回收后测试还调用已unload的旧Wand引用。修复夹具后装备预热、重复施法、双冷却、切杖、副手、清理8项断言通过。
这项修正不改变生产侧的法杖回收规则，也未过滤旧OSHI日志异常。


## 第57轮：随机吸入、完整退场与真实模型录制

用户指出规则顶点汇聚缺少力量感、冷却完成和整座法阵没有退场。本轮改为三角形内随机采样、末段加速、短尾吸入，白边和琥珀线在真实冷却完成后继续有限收尾，切杖/放下750ms分段解体，16tick销毁。
新增原创24元件法杖并让真实弹体与粒子汇聚共用宝石出生点；客户端仅补偿70°手持投影与世界FOV差异。
发现原版隐藏HUD也隐藏第一人称物品：CaptureDriver新增showHands场景，只取消GUI绘制，60°与90°实机均能看到新模型。关闭教程气泡，避免遮挡。
出生点检查必须等MagicManager.pendingAdd并入活动表再读；清理检查留出服务端Pre调度和客户端场景取样之间的1tick边界，不在销毁同tick的调度之前误报。没有修改生产PD调度，也没有关闭像素或日志门禁。
最终四场980帧、零丢帧，48项游戏内检查通过（含448项配方矩阵）；末尾PD粒子归零。白边完成后覆盖1684→0、琥珀线490→0，均逐帧收尾，数值证据build/verify/wand-cooldown57.tsv。
OSHI/Perflib/JNA既有5条环境日志仍影响总体报告；双黑洞批量速度包缺陷保持在open.md和pd-api-requests.md，未宣称修复。

## 第58轮：原创法杖、弧光汇聚与准星发射

用户报告旧方块杖缺乏质感、随机方屑像虫群、物品施法后上下跳动、宝石发射偏离准星及第三人称贴臂。
重绘四区原创材质图集并改为曲面OBJ：细长深色杖身、皮革握柄、细金线、镂空弧形托爪、长切面宝石。六束连续Bezier光流替代短粒子串，退场冻结当前随机循环并错峰收束，防止循环重置提前截断尾迹。
数值同步导致原版重新装备：改为依据WAND_ID判断，同一根杖的数值变化不触发跳动，换槽/换杖仍正常。原发射方向与视线平行：改为眼睛射线选最近实体/方块，再从宝石朝目标发射，迸发共用同方向。第三人称官方枚举扩展抬臂握柄，杖轴与臂轴交叉。
实机发现OBJ当前版本map_Kd使用纹理槽名，不直接解析资源id；绑定atlas槽并取消错误V翻转，材质警告归零。装配器增加缺失材质门禁，不只检查服务端状态。
新增真实近远/主副手/墙体遮挡碰撞检查，以及第三人称正面取景。抬头取景飞行弹的死亡余痕会混入全局粒子归零断言；夹具在验证施法后提前结束弹道，独立测持杖退场，最后219tick仍检查全部弹体与粒子归零，未修改生产尾迹寿命。
6场1360帧，72项游戏内断言通过、零丢帧；含448项配方矩阵。模型和材质打包字节一致。证据见build/capture/out/summary58.tsv、index.html、report.txt与build/capture/final58.log。
既有Windows OSHI/Perflib/JNA日志错误仍使总体日志门禁失败，未过滤。PD批量速度包超过512的黑洞缺陷未改动；本次PD API无新增修改需求。

## 第59轮：低模法杖、连续符印与独立充能柱（2026-10-09）

用户反馈写实模型与游戏不协调、细弧粒子不好看且间歇、充能收尾变形、右键仍重新举杖。
模型改为462面分面OBJ：宽银托架、阶梯金环、分段握柄、六面蓝宝石，7种离散材质乘原版纹理；删除写实图集。
旧弧流周期关闭可见性，改为宝石两侧常驻的两枚8块符印；实机宽视野复验又发现公转进入模型背后造成遮挡，改为固定两侧轻浮动，独立色域检查排除宝石、白色焦点和脚下法阵。充能由三根脚下柱自下填充，完成后上浮收细，取消向宝石插值的形变；常驻预算173颗，施法24颗大方粒。
右键成功调用ItemInHandRenderer.itemUsed直接清零高度，绕过前轮WAND_ID保护。客户端WandUseAnimationMixin只取消法杖使用手的清零。实机验证主副手调用前高度>0.95且调用后不变；空主手使用仍清零，组件同步与换槽检查保留。
旧冷却夹具t72重复施法早于新柱体完整收尾，会把下一轮/上轮未结束柱体误判为残留。独立窗口改t65–77，重复施法t78，保留至少4帧可见及末尾≤5%门槛。
构建通过，6场1360帧、77项游戏内断言通过、零丢帧，含448项配方矩阵。模型材质资源与JAR字节一致、旧图集无打包残留、客户端mixin打包完整。最终像素结果见design/wand-casting.md。
既有5条OSHI/Perflib/JNA环境日志仍触发总体门禁，不过滤，不宣称整个流水线全通过。本轮PD API无新增修改需求。

## 第60轮：模块待机、宝石余能和状态引导（2026-10-09）

延迟视觉原按完成进度逐渐揭露，所以开始阶段看不见。现在结算结束立即发布真实冷却，白色束印从开始完整出现再回位；蓝色导晶表示牌堆充能，基座即时亮、中层逐步亮、顶部锁晶只在真实充能结束后亮，取消不明含义的橙色。Alt属性面板提供两种状态的中英解释。
法杖增加侧晶插座、护片、肩扣及独立悬浮模块；原生组合模型32帧待机，成功施法事件驱动主宝石四档亮态，350ms恢复，主副手/观察者共用通知，不以周期同步推断发射。保持握柄与真实发射锚点稳定。
法阵粒子错相轻浮动；装备400ms、持杖退场350ms、冷却收尾160–205ms、施法迸发200ms。黑洞暗核/边界纯塌缩改6/4tick；不会改法杖延迟/充能数值或旧PD批量速度接入。
测试适配短促视觉：持杖纯退场要求1–6帧，黑洞固定边界色域独立检查相同短跨度及8tick归零，运动星尘不混入结构塌缩。冷却至少2帧收尾且末尾≤5%，保留最低可见量和零残留；新增施法下一tick白色/蓝色可见门禁与真实客户端模块/宝石亮态回归。副手弹体物理余痕与持杖退出分开，末尾全体粒子归零门禁保持。
构建与实机结果见design/wand-casting.md；无新增PD API需求，既有OSHI环境异常仍单独报告。

## 第61轮：有限收尾、状态引导与镜头冲击（2026-10-09）

- 重力场/水晶/冰壳/斥力主体退出不再直接删除；爆炸火团移动与收束阶段交接，片元/护盾/烟团/拖尾接入尺寸寿命曲线。
- 法杖白色卸能束印与蓝色回洗导晶分轮起落，持续时间声明后PD逐帧推进；缩小杖首符印范围，脚下阵列350ms收尾。
- 宝石16级短余光，重复施法继承当前亮度，到期精确归零。
- 取消黑洞逐tick重置冲击；服务端只给近处有限重音，距离边缘归零、旁观者排除；客户端从零起落，不改变翻滚/FOV，尊重玩家强度设置、跨世界清理。
- 实际CameraImpulse纯函数260078项端点/速度/连续性/限幅/叠加检查通过。最终合并实机3529帧/零丢帧/168项断言与19场视觉门禁通过；OSHI本机6条异常仍使整体流水线FAIL。高速炸弹的pendingAdd取样时机已修正，两条曾漏跑的断言确实通过，完整来源见设计文档。
- 不能将本条解释为“全部动画严格连续”：PD缩放接续与移动锚点插值仍在open/pd-api-requests，固定结构入场及高刷运动待前置修复。


## 第62轮：前置升级、法阵融合与电流传导（2026-10-09）

- 在用户指定的ParticleDrawing源码项目构建1.0.15-ALPHA及sources，升级本项目版本/下限，仅保留新JAR；前置322项测试与本项目构建通过。
- 区域结构零尺寸快速入场、当前倍率退场与移动锚点/束端/拖尾插值接入；5项历史PD需求从未决搬到附录B。
- 地面三角形固定平面/世界朝向，沿边流光与角点呼吸；白色内边表示施放间隔、蓝色外边表示回充，完成后并回主边。全阵350ms收尾，600ms销毁。
- 删除眼锚近杖idle符印，保留原生模型模块idle/宝石余光及主副手瞄准，消除脸旁悬浮与第一人称投影错位。
- 连锁电弧命中后结束自由飞行头/发射器，不再重复静止ambient；折线逐片依次起落，让电流导向下一实体。
- 创造标签先工具/法杖，再移动投射物、零初速静态投射物、修正；同类保持MagicManager注册顺序，不依赖MAGICS哈希迭代。
- 回归夹具：长时双洞与1024成员；spawnAll上限256，测试改用公开ensureSize建立1024集合后一次批量设速；火球清除前序内部墙并扩大棚，第一人称末段俯视验收地面退场。
- 接线自检修正sizeCurve追加误用和仅sc的expression清零位置，改为覆盖寿命曲线及逐片尺寸动画；没有修改前置/绕过限制。

实机最终结果见design/animation-continuity.md。


第62轮最终证据为62c的14场与62f的5场补录：3529帧/零丢帧/170项游戏内断言、19场视觉门禁均通过，版本升级无断线；build通过、PD322项测试通过。
报告整体因已有5条Windows OSHI环境异常FAIL，未掩盖。严格高刷求值仍有新请求（见open与pd-api-requests），本轮只交付原生接线和已验证的有限收尾。

## 第63轮：语义退场、真实轨道与刻单位（2026-10-09）

- 修复持杖三角形不再跟随：停止每刻重启位置缓动，改用PD实体getter；所有层共享慢速自转相位，玩家旋转不驱动法阵。位置仍由PD桥插值，组尺寸逐渲染帧能力未闭环。
- 爆炸火球整体缩回与外部蓝环已移除，改为随机径向火舌/碎片/烟尘；晶体、冰壳、斥力场破裂向外打开，场内自由碎片保留运动只收片元。
- 量子复制体原先OVERRIDE位置覆盖绕开轨道碰撞且速度置零，改GUIDANCE→Move扫掠；两份复制体有独立本体/拖尾，偏移2.2格，真实命中和伤害已验证。周身/球形环绕扩大并由速度驱动半径与角速度，驱动速度不因弦长反馈坍缩。
- 电弧按实际寿命逐次索敌，来速决定范围；无目标时不提前失去后续搜索机会。延迟施放从寿命到期当刻onExpire触发一次，payload清空。
- 追踪目标改包围盒中心；弹跳爆炸不再把实体穿刺当弹跳，方块反弹才扣次数/触发。Move固定子步起点，实体回调位置修改不污染后续积分。
- 激光从真实扫掠段显示短白芯蓝束；等离子束默认静止，移动端点不重启变量缓动。传送60刻，逐渐减速x2→x0.3，移除集束，双重施放改抽牌图标。
- 法杖完整刻冷却、每刻回蓝、提示/网络/存档同步；新刻/旧秒/旧毫秒/最早整数刻迁移和保存往返已验证，清掉负余数，重复施法固定7刻。
- 回归发现显式被动持杖（盔甲架木桩）未刷新持有记录，可能被回收：Wand.tick(caster)补记录，初次装备夹具立即驱动一次，持续护盾场景验证。视觉清理的激光/束/电弧门禁只作用于粒子，保留护盾反射和数值共鸣联动。

实机证据与最终门禁结果见build-and-run第63轮。

## 2026-10-09：PerfOS禁用字段类型修复

本机`Disable Performance Counters`为REG_SZ字符串“0”，OSHI要求REG_DWORD。备份后规范为DWORD 0，维持“启用”的原意。此项修好不等于OSHI进程查询恢复；计数器错误87/当前进程null仍在open.md §3.1，待重启复验。

## 2026-10-09：第64轮法术节奏与水晶表现

- 延迟施法通用漂浮绿尘提前散开，载体仍在移动：改为随最终坐标收拢的锁印，到期onExpire当刻与payload在同位置爆开；静止/加速载体都验证，强制清除不释放。
- 连锁电弧原5刻一跳过慢：改为每刻1跳，首击重置4刻导电窗口；基础速度0.75格/刻、索敌3格，共首击+3跳约4敌。延寿增加索敌次数、加速扩大范围仍验证。
- 新增链锯：同次施放中清零此前累计及法杖基础施放延迟，充能时间-3刻。付费成功才生效，后置法术可重新增加延迟，payload链锯不清父法杖延迟。末位多重施放实现连续20刻20发；真实命中和伤害修正通过，延寿/加速时持续切割可见。
- 休眠水晶面朝内导致单面PD片元从外侧漏面：翻转上下晶面顶点绕序，四个外侧视角均检查；最后40刻按剩余时间加速白闪，半周期5→3→1刻。自然到期调用唯一爆炸入口，真实伤害与连锁执行，取消不误爆。
- 删除球形环绕的注册、实现、资源与语言；周身环绕速度半径系数2→0.5，上限12→6，真实轨道扫掠保留。

构建通过。最终8场实机1520帧/零丢帧，79项玩法检查全部通过，逐场视觉和媒体门禁通过；证据及来源见build-and-run第64轮。总体日志门禁仍因既有OSHI的5条异常失败，环境问题继续留在open.md §3.1，待用户手动重启后复验。

## 2026-10-09：第65轮危险法术与强度反馈

- 原版固定击退让小火花弹也明显推人，爆炸再叠一份固定推力：统一SpellImpact真实伤害与击退入口，仅本模组伤害类型加入no_knockback。原版生物实测5点伤害推力0.015、20点0.15834，满击退抗性保持零，护盾未接受伤害不推离。
- 爆炸错误排除来源、Move快照自伤字段导致穿刺开关失效：统一动态selfDamage/canHarm，爆炸/水晶/黑洞默认危险，穿刺开启返弹自伤并跟随配方继承。原版生物实际爆炸自伤100→94、普通返弹100不变、穿刺返弹能真实致死，枪口重叠不误判。
- 黑洞中心只有吸力无伤害：吸力系数提升5倍，球形区域读取实体中心并抑制过冲；暗核每5刻造成基础4点真实伤害，无向外击退。原版施法生物反复自伤100→88，中心速度仍为零；三格目标的首次向心速度0.17143。
- 连锁改为Noita式分段漂移/跃迁，基础15刻、4/7/10/14刻跃迁，避让最近两次目标，空场前跳、薄墙相位及实心落点停止均验收。保留公共命中分派，易爆修正在跳跃目标和邻近生物产生真实爆炸伤害。
- 延寿改+25刻/法力40/施放延迟+5刻；连锁装一张后40刻11次跃迁。连续目标不足时允许更早目标重访，不把11次跃迁误写成11个不同敌人。
- 爆炸增加厚像素火舌切片、白热前锋、沿飞行方向的长碎片及翻卷烟块；全程外扩、逐片熄灭，大小/随机性和晶体配色保留。初轮夹具将弹跳爆炸修正误转Projectile及旧连锁断言已修正，最终验收不采用失败轮次。

构建通过，最终65f十场1781帧/零丢帧/93项玩法检查全部通过。视觉与媒体证据见build-and-run第65轮；既有OSHI环境错误仍独立留在open.md §3.1，本轮未重启或隐藏异常。

65d/e的大爆炸燃尽集中导致暖色覆盖50%→5%仅跨2/3帧：扩大切片寿命差异并采用末段EASE_OUT熄灭，65f实测4帧，满足原有≥4帧门禁，未调整阈值。最终十场视觉/媒体全部通过；爆炸位置始终外扩，仅单片尺寸燃尽。

## 2026-10-09：第66轮伤害分类、质量与逐刻伤害

8类独立原版伤害源与数据包/语义标签已接通，命中/爆炸不再混为同一个projectile来源；中英文伤害名称、死亡文本与木桩分类文字颜色均接入。冰冻直接类型转换不改变爆炸渠道。FIRE为已注册可用渠道，不额外引入点燃机制。

黑洞默认质量256，目标加速度按质量除，重弹径向阻尼抑制互吸高速，切向外力继续运动并穿墙。实体持久化质量覆盖与体积默认值共用，非法NaN/无穷/非正数不会进入受力计算。原版猪与魔法弹质量8对1的实测吸力比均8；两个重黑洞95刻互吸后速度约0.009格/刻。

等离子束/暗核每刻真实伤害，连续5刻生命值分别98.5/97/95.5/94/92.5和96/92/88/84/80。既有护盾预算下高频光束可正确击穿，未改护盾定价。构建、128项玩法通过；独立证据/来源见build-and-run第66轮。PD受光缓存与既有OSHI仍未修复，继续留在未决台账。
