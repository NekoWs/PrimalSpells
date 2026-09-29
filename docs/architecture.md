# 架构总览（architecture）

> 本文回答：**法术系统由哪些对象组成、一次施法发生什么、每 tick 发生什么、状态存在哪里、客户端看到什么。**
> 读代码前先读本文；文件位置见 [code-map.md](code-map.md)；写代码前读 [conventions.md](conventions.md)。

---

## 1. 总体分层

```
                     ┌──────────────────────────── 客户端（client/）────────────────────────────┐
                     │ HUD（法杖数值条 / 护盾条）  tooltip（法术词条、槽位预览）                   │
                     │ 输入（滚轮选槽、Ctrl+左键拖拽交换）与 tooltip/HUD 表现                  │
                     │ 实体渲染（Cube/Sphere/Drone 的 Model+Renderer）                            │
                     └───────────────▲──────────────────────────────┬───────────────────────────┘
                          S→C payload（数值/护盾）          C→S payload（选槽/编辑）
                     ┌───────────────┴──────────────────────────────▼───────────────────────────┐
                     │ network/  payload 定义与编解码                                            │
┌────────────────────┴──────────────────────────────────────────────────────────────────────────┐
│ 服务端权威（公共侧）                                                                            │
│                                                                                                │
│  物品与界面         item/（WandItem、SpellPouchItem、TestDummyItem、component、menu）           │
│  法杖运行时         wand/（Wand 牌堆与冷却、WandManager 实例表、WandSavedData 存档）             │
│  法术对象模型       magic/core/（Magic、Modifier、Projectile 基类、MagicStatus、MagicManager）   │
│  法术内容           magic/projectile/（10）  magic/modifier/（27）                              │
│  行为与结算         magic/effect/{geometry,lifecycle,visual}/（14）                             │
│  注册与入口         registry/（ModItems/ModEntities/ModDamageTypes） + PrimalSpells.kt │
│  事件               event/（公共侧事件处理器）                                                   │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
                     粒子表现：particledrawing（服务端权威程序化粒子）+ 原版 sendParticles
```

对外只暴露两种「东西」：**物品**（法杖、法术物品、小包、测试木桩、编辑台）与**服务端对象**（法杖状态、投射物）。**法术投射物不是 Minecraft 实体**，没有实体 ID、不参与原版实体同步，只存在于服务端的 `MagicManager.projectiles` 列表里。

## 2. 对象模型

### 2.1 `Magic`（`magic/core/Magic.kt`）

所有法术的基类。字段分三类：

| 类别 | 字段 | 语义 |
|---|---|---|
| 身份 | `id: String` | 注册表键，也是**持久化键**（存档、物品组件、资源名都用它） |
| 代价 | `mana`、`delay`、`recharge` | 分别表示法力消耗、施法延迟、充能时间；`mana` 为负 = 返还法力 |
| 内容 | `effects: ArrayList<BaseEffect>`、`lore` | 行为列表与 tooltip 词条 |
| 上下文 | `caster: Entity`、`wand: Wand`（`lateinit`） | 由 `Wand.castHand` 在施法前注入 |

生命周期钩子（执行顺序由 `EffectPhase` 决定，见 §2.7）：

- `spell()`：按 `EffectPhase.activeOrder` 升序触发 `onActive()`，再调 `onSpell()`；
- `tick()`：按 `EffectPhase.tickOrder` 升序触发 `onTick()`；
- `die()`：按注入顺序触发 `onDie()`，再调 `onDie()`；
- `onInventoryTick()`：仅当 `needInventory = true` 且法杖在主手/副手时，由 `Wand.tick(caster)` 每 tick 驱动（被动法术的唯一入口，目前只有 `EnergyShield` 用）。
- `clone()`：**从注册表新建一枚「本次施法实例」**，不是字段拷贝——修饰器会把效果注入实例自身的 `effects`，复用实例会让效果累积；`id` 未注册时直接抛 `IllegalStateException`（而不是 `!!` 的 NPE）。

### 2.2 `Projectile`（`magic/projectile/Projectile.kt`）

`Projectile : Magic`。`spell()` 在调用 `super.spell()` 前，把 4 个内建 effect 追加到 `effects`：

```kotlin
effects += Position(position)          // 出生点
effects += Velocity(velocity * speed)  // 初速（speed 是倍率）
effects += Move(dragCoeff, hitRadius, throughBlocks, selfDamage)   // 每 tick 推进 + 碰撞
effects += ProjectileBody(bodyColor)   // 本体粒子
```

随后 `super.spell()` 按阶段触发 `onActive()`：`Position`/`Velocity`（`SETUP`）先写入出生点与初速，修饰器注入的效果（`MODIFIER`）随后在有效状态上改数值 —— 所以 `SpeedBoost`、`GradualAccelerate` 这类修饰器才能改到初速。

命中分派：

- `hitEntity(entity)`：以「投射物位置 − 实体位置」为法线算反弹方向 → 构造 `HitResult(ENTITY, …)` → `hitTargets++`（`maxHitTargets > 0 && hitTargets >= maxHitTargets` 时 `alive = false`）→ 依次触发 `effects.onHitEntity/onHit` 与 `onHitEntity/onHit`。
- `hitBlock(pos, faceNormal)`：`status.bounces > 0` 时按 `bounceScaler` 缩放反弹速度并把位置推到面外 0.01 格，否则 `alive = false`。
- `maxHitTargets = -1`（`Piercing`）表示无限穿透。

### 2.3 `Modifier`（`magic/core/Modifier.kt`，原 `Revise`）

`Modifier : Magic`，唯一的额外 API 是 `effects(): List<BaseEffect>`——返回**克隆后**的 effect 列表。

`magic/modifier/` 下 26 个实现分两类：

1. **纯注入**：在 `init` 里 `effects += SomeEffect(...)`（如 `Homing` → `Tracking`、`MoreBounce` → `Bounce(8)`）；
2. **匿名效果**：在 `init` 里 `effects += object : BaseEffect() { override fun onActive() { … } }`，直接改 `status`/`projectile` 的字段（如 `Diffusion` 放大爆炸半径、`Piercing` 改 `maxHitTargets`）。

另有 4 个会**生成新投射物**（`ClusterBomb`、`ExplosionProjectile`、`BounceDuplicate`、`QuantumEntanglement`）——它们语义上更接近「法术」而非「修饰器」，但通过继承 `Modifier` 借用了手牌结算流程（见 [known-issues.md](known-issues.md)）。

### 2.4 `BaseEffect` 与 14 个实现（`magic/effect/`）

`BaseEffect` 定义 6 个钩子：`onActive / onTick / onHitEntity / onHitBlock / onHit(result) / onDie`，由 `Magic` 统一转发。

| 分组 | 类 | 职责 |
|---|---|---|
| 根 | `BaseEffect` | 契约（六个空实现）+ 阶段声明（`phase`）+ 同类叠加辅助（`stackScale`/`isStackPrimary`） |
| `geometry/` | `Position`, `Velocity` | `onActive` 时写入出生点/初速（阶段 `SETUP`） |
| | `Move` | 每 tick 阻尼推进 + 1/16 格子步进碰撞（阶段 `MOTION`，唯一推进 `pos` 的地方）。逐 1/16 步进是为了拿到**精确的接触点/接触面**；实体检测走「每 tick 一次宽相位包围盒筛选 + 子步进窄相位判定」 |
| | `Bounce` | `onActive` 时 `maxBounces += amount` |
| | `Tracking` | 延迟后把速度偏向最近实体；多实例只跑首个，其余按 √N 抬高系数 |
| | `ConstantAcceleration` | 每 tick 给速度叠加一个恒定加速度（默认竖直向下，重力类共用实现） |
| `lifecycle/` | `Trigger` | `onHit` **一次性**释放 `TriggerSpell.payload`（释放后清空） |
| | `DelayedTrigger` | `onDie` 沿施法方向一次性释放 payload（`DelayedCastProjectile` 用） |
| | `Hurt` | 命中实体造成伤害（临时清零 `invulnerableTime`；伤害类型缺失时跳过而不是崩服） |
| | `Explosion` | 自定义爆炸：音效 + 粒子 + 距离衰减伤害与击退（**不破坏方块**，见 [known-issues.md](known-issues.md) §1.3） |
| | `AreaFieldEffect` | 区域场基类：**每 tick** 结算 —— 范围内刷新迟缓 + 按半径拉拽 + **对半径内投射物施力（黑洞口把法术吸进去、重力场往下压）**；实体离开范围立即撤销迟缓。视觉由子类用 PD 粒子实现（`spawnVisuals`/`updateVisuals`/`clearVisuals` 三个钩子） |
| | `BlackHole` | 半径 5 引力场：迟缓 I + 把生物与**魔法投射物**拉向中心；PD 粒子每 tick 1 个（紫色、随机切向初速、**本模组积分位置**、吸力随距离增强 + 阻尼，抵达中心 0.1 格才销毁） |
| | `GravityField` | 半径 5 重力场：迟缓 IV + 给**魔法投射物**向下的力；PD 粒子两层 —— 半径 5 的半透明球壳标识范围（96 个永存），以及上半球表面生成、竖直下落、碰到下半球表面即销毁的下落粒子 |
| `visual/` | `ProjectileBody` | 一个永存粒子贴在投射物当前位置（`track`，天然带客户端插值） |
| | `Trajectory` | 在投射物后方喷拖尾粒子（发射点反向偏移）；速度≈0 时退化为原地氛围粒子 |

**同类效果叠加**：统一用 `BaseEffect.stackScale`（√N）与 `isStackPrimary`（只有首个同类实例改写状态），
`Tracking`/`Reciprocate`/`Serpentine`/`GradualAccelerate`/`GradualDecelerate` 都改用了这两个辅助属性。

**球体粒子采样**统一走 `util/ParticleShapes`（`sphereSurface` / `sphereVolume`），爆炸、黑洞、重力场共用。

### 2.5 `MagicStatus`（`magic/core/MagicStatus.kt`）

投射物的运行时状态袋：`pos`/`velocity`（`org.joml.Vector3f`）、`damage`、`age`、`bounces`、`hitTargets`、`explosionDamage`/`explosionRadius`。所有效果共享同一份实例，读写顺序由 [EffectPhase](#27-effectphase效果执行顺序magiccoreeffectphasekt) 决定。

它是**服务端单例数据**：所有 effect 读写同一份 `status`，effect 之间的顺序会直接决定谁覆盖谁（见 §8）。

### 2.6 `TriggerSpell`（`magic/core/TriggerSpell.kt`）

`interface TriggerSpell { val triggerCast: Int; val payload: ArrayList<Magic> }`。

触发法术在 `Wand.castHand` → `loadPayload()` 阶段**额外抽牌**（预算 = `triggerCast`，这张法术自身也按 `cast` 计入预算），把抽到的法术克隆后填进 `payload`；抽到触发法术时会**递归**填充它自己的 payload，于是形成 `触发A( 触发B( 火球 ) )` 这样的链。

命中时由 `Trigger.onHit`（死亡时由 `DelayedTrigger.onDie`）把 payload 里的投射物就地施放，并把 payload 内的修饰器效果注入这些投射物；**释放后立即清空 payload**（一次性）——payload 持有的是同一批实例，重复释放会让效果被反复注入、叠加。

### 2.7 `EffectPhase`：效果执行顺序（`magic/core/EffectPhase.kt`）

效果不再靠 `effects` 的插入顺序决定先后，而是各自声明阶段；`Magic.spell()` / `Magic.tick()` 按阶段**稳定排序**（同阶段内保持注入顺序）：

| 阶段 | onActive 位置 | onTick 位置 | 谁在用 |
|---|---|---|---|
| `SETUP` | 最先 | 最先（空实现） | `Position`、`Velocity` —— 写入出生点与初速 |
| `MODIFIER`（默认） | SETUP 之后 | 位置积分**之前** | 修饰器注入的效果（改速度/伤害/爆炸参数），以及 `Trajectory`、`Hurt`、`Trigger`、`Explosion` 等 |
| `MOTION` | — | 中间 | `Move` —— 每 tick **唯一**推进 `status.pos` |
| `OVERRIDE` | — | 积分**之后** | `SelfOrbit`、`SphereOrbit`、`QuantumEntanglement.OrbitEffect` —— 绝对定位，不再被积分位移叠加 |
| `VISUAL` | — | 最后 | `ProjectileBody`、`Trajectory` —— 读本 tick 的最终位置发粒子 |

一句话规则：**改速度留在默认阶段，写位置用 `OVERRIDE`，读最终位置用 `VISUAL`。**

## 3. 一次施法的完整流程

```
客户端：长按右键（WandItem.use → startUsingItem，getUseDuration 恒 72000 不自动停）
   │
服务端每 tick：WandItem.onUseTick  →  getWand(stack).spell(player)
   │
Wand.spell(caster)                      wand/Wand.kt
   ├─ status.delay > 0 或 status.recharge > 0 → 直接失败（status.failedReason 记录原因）
   ├─ 清零 delay / recharge
   ├─ draw(cast)                        按预算从 drawPile 抽牌进 hand
   └─ castHand(caster, hand)
        ├─ 摘出 Modifier → consumeMana → 累加 status.recharge/delay
        ├─ for 每个 Projectile：
        │    ├─ consumeMana（不足则跳过）
        │    ├─ projectile.clone()（= 工厂新建实例）
        │    ├─ 若是 TriggerSpell → loadPayload() 递归抽牌填 payload
        │    ├─ p.effects += 所有有效 Modifier 的 effects()（克隆）
        │    ├─ p.caster / p.wand / p.position(眼睛位置 −0.1Y) / p.velocity(视线方向)
        │    ├─ p.spell()   → 内建 4 effect + 修饰器 effect 全部 onActive
        │    ├─ 累加 p.recharge/delay，写 status.lastDelay/lastRecharge
        │    └─ MagicManager.add(p)   ← 进入待添加队列
        └─ discard() → hand → discardPile；抽牌堆空则 reloadIfNeeded()
                            （回洗并附加 recharge，见 Wand.reloadIfNeeded）
   │
同 tick 稍后：MagicManager.tick() → projectiles.addAll(pendingAdd)
```

**牌堆语义**（`wand/Wand.kt`）：

- `drawPile`/`hand`/`discardPile` 三个列表，`loadedMagics = innate + magics` 是回洗来源；`load()` 重建牌堆（装卸法术、读档后都必须调用）。
- 抽牌预算从 `Wand.cast` 起算，每抽一张牌 `remaining += magic.cast - 1`：`cast > 1` 是「多抽」（如 `DoubleCast.cast = 2`），`cast = 0`（投射物默认）**立即耗尽预算**——这正是「抽到一张投射物就停」的机制。
- 回洗（`reloadIfNeeded`）发生在抽牌堆空且有弃牌堆时：重置牌堆并给 `status.recharge += recharge`。
- 失败原因写进 `status.failedReason`（如 `"Mana(120.0) < 200.0"`），供 HUD/调试查看。

**三层「消耗」不要混淆**：

| 名字 | 位置 | 含义 |
|---|---|---|
| `Magic.mana/delay/recharge` | 法术模板 | 单个法术的代价（`mana < 0` 是返还） |
| `Projectile.cast` / `Wand.cast` | 抽牌预算 | 影响本次能抽几张牌 |
| `Wand.status.delay/recharge/mana` | 法杖运行时 | 结算后的剩余冷却与当前法力；`Wand.mana`/`charge` 是上限与每秒回复量（`charge / 20` 每 tick） |

## 4. 每 tick 的投射物循环

`PrimalSpells`（公共入口）把 `MagicManager` 注册到 `NeoForge.EVENT_BUS`，`ServerTickEvent.Pre` → `MagicManager.tick()`：

```
for p in projectiles:
    if !p.alive: p.die(); remove; continue
    p.tick()              → effects 按 EffectPhase.tickOrder 稳定排序后 onTick()
                             ├─ MODIFIER：修饰器注入的效果（改速度/伤害）
                             ├─ MOTION  ：Move 阻尼 → 子步进碰撞 → 命中回调 → status.pos 推进
                             ├─ OVERRIDE：轨道类绝对定位（SelfOrbit / SphereOrbit / 量子纠缠复制体）
                             └─ VISUAL  ：ProjectileBody / Trajectory 读最终位置发粒子
    status.age++
    if status.age >= maxAge: alive = false
projectiles.addAll(pendingAdd); pendingAdd.clear()
```

死亡路径：`alive = false` → 下一 tick 的循环开头 `die()` → effects `onDie()`（`Explosion` 的 `onDeath = true`、`DelayedTrigger` 释放 payload、`TeleportProjectile` 传送施法者、`ProjectileBody` 移除粒子）。

`ServerStoppingEvent` 时 `MagicManager.projectiles.clear()`（投射物不跨存档存活）；`WandManager` 同时落盘法杖。

## 5. 法杖状态与持久化

```
物品栈（ItemStack）                         服务端内存                      世界存档
WAND_ID: WandID(uuid)  ────────────────►  WandManager.wands[WandID]  ──►  WandSavedData
WAND_SPELLS: List<String>（客户端展示）      Wand（牌堆/法力/冷却）            （wandId → NBT）
WAND_SELECTED_SLOT: Int（滚轮选中的槽位）
POUCH_ID / SPELL_POUCH_CONTENTS / POUCH_SELECTED_SLOT（小包）
```

- 物品栈只存「身份 + 展示副本」，权威状态在 `WandManager`（`wand/WandManager.kt`）；`wandId` 不存在时由 `WandManager.createWand` 现场分配。
- 落盘：`WandManager.onServerStopping → save(overworld)` → `Wand.saveTag()`；读档：`onServerStarted → load()`（**先清空** `wands` 与持有者记录，避免整合服务器换世界时残留上一个世界的法杖）→ `Wand.fromTag()`。
- **槽位保序**：`saveTag` 固定写满到 `max(size, 已用槽数)`，空槽写空串占位；`fromTag` 把空串还原为 `null` —— 读档后槽位索引不会前移。
- **牌堆也入库**：`draw_pile`/`hand`/`discard_pile`（法术 id 序列）随存档保存并在读档后覆盖还原。
  这是「内存实例可被回收 + 懒加载」的前提 —— 不存就会出现「放下法杖再捡起 = 重新洗牌」。
- **法术运行期状态**：`magic_states`（法术 id → NBT），由 `saveTag` 逐个问 `loadedMagics` 要（`Magic.saveRuntimeState`），
  读档时经 `Magic.loadRuntimeState` 回灌。当前用户是 `EnergyShield`（当前耐久 + 破盾暂停），因此重登/重启/被回收后再懒加载都不再回满。
  注意键是**法术 id**：同一 id 的重复卡共享一份状态。
- **持有者记录与回收**（`WandManager`）：`markHeld(wandId, 实体 UUID, gameTime)` 是唯一写入口，
  由 `WandItem.inventoryTick`（任何实体的背包/装备槽 tick）与 `Wand.spell`（任何实体主动施法，覆盖生物施法）调用；
  每 100 tick 回收一次「在线玩家背包里没有 + 超过宽松 TTL 未被确认」的法杖**内存实例**（回收前先写回存档），
  `WandManager.get` 会按需从存档**懒加载**重建。**存档条目永不删除**（法杖可能躺在箱子里）。
- 小包的内容与选中槽都存物品组件（`SPELL_POUCH_CONTENTS` / `POUCH_SELECTED_SLOT`），以 `pouchId` 定位，不依赖容器菜单槽位。

## 6. 网络

注册集中在 `PrimalSpells.kt` 的 `RegisterPayloadHandlersEvent`（`event.registrar("1.0.0")`）。payload 定义在 `network/`。

| payload | 方向 | 关键字段 | 处理 |
|---|---|---|---|
| `SyncWandStatsPayload` | S→C | wandId、当前/上限法力、当前/上次 delay、recharge、cast、charge | 经 `ClientPayloadSink.wandStats` → `client/hud/WandHudRenderer.putStats` |
| `SyncShieldPayload` | S→C | wandId、durability、maxDurability、pauseTicks、breakPauseTicks | 经 `ClientPayloadSink.shield` → `client/hud/ShieldHudRenderer.putShield` |
| `SelectSpellSlotPayload` | C→S | targetId、slot、pouch | 遍历背包：`pouch=false` 按 `wand_id` 匹配并写 `WAND_SELECTED_SLOT`；`pouch=true` 按 `pouch_id` 匹配并写 `POUCH_SELECTED_SLOT` |

**公共侧不引用客户端类**：S→C 包的处理器在公共入口注册，但真正的客户端逻辑通过 `network/ClientPayloadSink`
（两个可空回调）转发，由 `client/PrimalSpellsClient` 在初始化时安装实现；专用服务器上它们保持 `null`。

**编杖/编包不走自定义包**：一律走原版「物品对上物品」交互（`WandItem`/`SpellPouchItem.overrideOtherStackedOnMe`），
只有滚轮选槽需要显式同步选中槽（物品组件只有服务端权威）。

## 7. 客户端

| 子包 | 内容 | 触发方式 |
|---|---|---|
| `client/hud/` | `WandHudRenderer`（法力/冷却条）、`ShieldHudRenderer`（护盾条） | 消费 S→C payload，缓存按 `wandId`，登出清空 |
| `client/tooltip/` | `ClientTooltipHandler`（组装词条）、`WandSpellTooltip`/`PouchTooltip`（自定义 tooltip 组件，都高亮选中槽）、`SpellSlotTracker`（法杖与小包共用的「预览槽位」缓存） | `RenderTooltipEvent.GatherComponents` + 组件工厂 |
| `client/input/` | `SpellSlotScrollHandler`（滚轮选槽：法杖与小包共用，发 `SelectSpellSlotPayload`）、`ClientContainerDragHandler`（Ctrl+左键拖拽交换，复用原版 `slotClicked(PICKUP)` 协议） | `ScreenEvent`，注册在 `PrimalSpellsClient` |
| `client/render/` | `DroneEntityModel`/`DroneEntityRenderer`（立方体/球体调试实体已随死代码清理删除） | `EntityRenderersEvent` |
| `client/PrimalSpellsClient.kt` | 客户端入口（`@Mod(dist = CLIENT)`）：安装 `ClientPayloadSink`、集中注册以上全部、登出清理缓存 | — |

划分原则：**公共侧不 import 客户端类**；纯客户端事件不挂在 `PrimalSpells`（公共入口）上。

**物品栏内的编辑交互**（法杖与小包同一套，全部走原版协议 + 服务端权威）：

| 操作 | 法杖 | 法术小包 |
|---|---|---|
| 选中槽位 | 滚轮切换 `WAND_SELECTED_SLOT` | 滚轮切换 `POUCH_SELECTED_SLOT` |
| 装入 | 光标拿法术物品右键法杖 → 进入选中槽（原法术回光标） | 同左 |
| 取出 | 光标为空右键法杖 → 选中槽法术到光标 | 同左 |
| 展示 | `WandSpellTooltip` 9 列网格 + 高亮 + Alt 显示数值 | `PouchTooltip` 8 列网格（固定 16 格）+ 高亮 |

## 8. 关键不变量与陷阱（改代码前必读）

1. **执行顺序由 `EffectPhase` 决定，不再看插入顺序**。`onActive` 按 `SETUP → MODIFIER → …` 升序，`onTick` 按 `MODIFIER → MOTION → OVERRIDE → VISUAL` 升序；同阶段内保持注入顺序。想改速度就留在默认阶段，**想写 `status.pos` 必须用 `OVERRIDE`**，否则会被 `Move` 的积分叠加（这就是过去轨道漂移的根因）。
2. **`Magic.clone()` 是「从注册表新建实例」**，不继承任何运行期状态（当前速度、被修饰器改过的伤害/爆炸参数都不继承）。`id` 未注册直接抛异常——所以**法术 id 不能随便改**。
3. **`Modifier.effects()` 返回的是浅拷贝**：多枚子弹各自拿一份克隆，但克隆内部的引用字段仍可能指向同一对象，别用可变字段存跨实例状态。
4. **修饰器效果只在 `castHand` 时注入一次**：`Modifier` 自身不进 `MagicManager.projectiles`，不参与 tick。
5. **修饰器之间没有先后顺序**：所有抽到的修饰器都作用在同一枚投射物上（例如「火球 + 伤害增强 + 穿刺」= 基础 + 增强 − 惩罚）。修饰器效果只允许**可交换**运算：累加、累乘、乘零；禁止钳制（`coerceAtLeast`）与「重设绝对值」（如 `normalize().mul(绝对速度)`）。同类叠加用 `stackScale`/`isStackPrimary`。写法要求见 `magic/core/Modifier.kt`。
6. **`status` 是所有 effect 共享的可变状态**：同阶段内的写入顺序就是注入顺序（抽牌顺序），但这只保证「确定性」，任何效果都不应依赖它。
7. **命中回调每个 tick 都会触发**（投射物停在实体体内时）：穿刺玩法依赖这一点，`Move` 刻意不去重。
8. **触发法术 payload 是一次性的**：释放即清空，穿刺多次命中不会重复释放。
9. **公共侧不引用客户端类**：S→C 包一律经 `network/ClientPayloadSink` 转发（见 §6）。
10. **粒子表现有两条路**：投射物本体/拖尾走 `particledrawing`（`wand.status.particleManager`，服务端权威 + 客户端插值）；爆炸/黑洞/重力场/无人机/传送/护盾用原版 `ServerLevel.sendParticles`（球体采样统一走 `util/ParticleShapes`）。
11. **区域场每 tick 结算、离开即解除**：`AreaFieldEffect` 每 tick 刷新迟缓并施加拉拽，实体离开范围（或效果结束）时**立刻**撤销迟缓，不留尾巴。

## 9. 与 ParticleDrawing 的接口现状

- 依赖：`lib/particledrawing-${particledrawing_version}.jar`（当前 **1.0.8-ALPHA**；`compileOnly` + `localRuntime`，版本由 `gradle.properties` 驱动），`mods.toml` 声明 `required`、`AFTER`。
- 已接线（三种用法，均对应 PD 的低带宽写法）：
  1. **锚定**：`attachTo(实体, 世界空间偏移)`——护盾球壳，服务端只在挂载时发一次，客户端本地解析实体位置，
     **0 包/tick** 且天然刚性；
  2. **批量写位置**：`ParticleManager.trackAll(ids, positions)`——黑洞粒子（本模组积分，吸力随距离变化），
     **1 包/tick，与粒子数无关**；
  3. **速度驱动**：生成时一次 `setVelocity`，之后交给 PD 积分——重力场下落粒子（**0 包/tick**），
     回收判定只读 `ParticleHandle.position()`；另 `Trajectory`（拖尾）同样只发一次速度。
- 管理器获取：统一走 `wand.pdParticles(level)`（缺失/跨维度时现场建立），未来生物施法者也有粒子表现。
- 未使用：`applyForce(accel, ticks)`（黑洞吸力随距离变化，用 `trackAll` 更省也更可控，理由见 `ParticleSwarm` KDoc）、
  `attachToLocal`（需要「贴在身前/身侧」的粒子时可用）。
- 未接线：`.pdrawc` 动画特效（锚点/播放时钟/资源注册表那一整套）。设计方案与落地阶段见 [design/effects-api.md](design/effects-api.md)；
  1.0.8-ALPHA 新增的 `EffectCallbacks` / `EffectVarStore` 正是为此准备。
- 历史 problem 记录：API 缺陷/需求清单与闭环情况见 [known-issues.md](known-issues.md) §7
  （6 条已全部闭环：PD 修了 3 处位置链路偏差 + 补了 `position`/`trackAll`/`applyForce`/`attachTo`）。
- ⚠️ **依赖构建口径**：`lib/particledrawing-1.0.8-ALPHA.jar` 在 2026-09-30 被同名替换为新构建
  （旧构建没有上述新 API，且 `versionRange` 无法区分）——**必须使用该日期或更新的构建**。
- ⚠️ **遇到 ParticleDrawing 的 API 缺陷或能力缺失时的处理政策（硬性）见 [conventions.md](conventions.md) §9 与 [design/effects-api.md](design/effects-api.md) §0**：
  不得在本模组自行另写一套替代实现，应停止尝试并列出 API 修改需求。
