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

## 附录 A：历史崩溃记录（已有防护，保留备查）

- `run/client/crash-reports/crash-2026-08-10_06.39.49-server.txt`：
  `Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]` → 服务器 tick 崩溃。
- 现在由 `registry/ModDamageTypes.projectileSourceOrNull()` 统一解析：数据包缺少该伤害类型时
  只记一次错误日志并**跳过本次伤害**，`Hurt` 与 `Explosion` 都走这条路。

---

## 附录 B：ParticleDrawing 已闭环条目（备查）

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
