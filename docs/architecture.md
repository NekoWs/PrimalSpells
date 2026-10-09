# 架构总览（architecture）

> 本文回答：**法术系统由哪些对象组成、一次施法发生什么、每 tick 发生什么、状态存在哪里、客户端看到什么。**
> 读代码前先读本文；文件位置见 [code-map.md](code-map.md)；写代码前读 [conventions.md](conventions.md)。

---

## 1. 总体分层

```
                     ┌──────────────────────────── 客户端（client/）────────────────────────────┐
                     │ HUD（法杖数值条）             tooltip（法术词条、槽位预览）                   │
                     │ 输入（滚轮选槽、Ctrl+左键拖拽交换）与 tooltip/HUD 表现                  │
                     │ 实体渲染（Cube/Sphere/Drone 的 Model+Renderer）                            │
                     └───────────────▲──────────────────────────────┬───────────────────────────┘
                          S→C payload（法杖数值/镜头）    C→S payload（选槽/编辑）
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
- `onInventoryTick()`：仅当 `needInventory = true` 且法杖在主手/副手时，由 `Wand.tick(caster)` 每 tick 驱动（被动法术的持有期入口）。
- `onPassiveIdle()`：法杖**这一 tick 没有被任何实体驱动**时，由 `Wand.tick(level = …)` 的闲置分支逐 tick 驱动一次
  —— 用于「放下后延迟清理 / 播淡出动画」这类收尾（**当前没有使用者**：第 26 轮护盾重做后不再有常驻球壳；钩子保留给将来需要「放下后收尾」的法术）。
  判断依据是 `lastPassiveTick` 落后当前 `gameTime` 超过 1 tick（闲置分支跑在 `ServerTickEvent.Pre`，早于实体 tick）。
- `onUnloaded()`：法术**被从法杖上取下**（或槽位被替换）时调用一次 —— 由 `Wand.load()` 对「本次重建后不再装载」的实例触发，
  也由 `Wand.unload()`（法杖内存实例被回收）触发。用于清理「只该在装载期间存在」的东西：
  取下之后该实例**不再有任何 tick**（`onInventoryTick`/`onPassiveIdle` 都不会再跑），
  不在这里清理就会把永存粒子组之类的资源永远留在世界里（**当前没有使用者**：护盾第 23 轮起改用「每次现铺、淡出后由 PD 自行销毁」的玻璃面板组）。
- `clone()`：**从注册表新建一枚「本次施法实例」**，不是字段拷贝——修饰器会把效果注入实例自身的 `effects`，复用实例会让效果累积；`id` 未注册时直接抛 `IllegalStateException`（而不是 `!!` 的 NPE）。

### 2.2 `Projectile`（`magic/projectile/Projectile.kt`）

`Projectile : Magic`。`spell()` 在调用 `super.spell()` 前，把 4 个内建 effect 追加到 `effects`：

```kotlin
effects += Position(position)          // 出生点
effects += Velocity(velocity * speed)  // 初速（speed 是倍率）
effects += Move(dragCoeff, hitRadius, throughBlocks, selfDamage)   // 每 tick 推进 + 碰撞
effects += Trajectory(color)           // 拖尾（头部菱形即本体标记）
```

随后 `super.spell()` 按阶段触发 `onActive()`：`Position`/`Velocity`（`SETUP`）先写入出生点与初速，修饰器注入的效果（`MODIFIER`）随后在有效状态上改数值 —— 所以 `SpeedBoost`、`GradualAccelerate` 这类修饰器才能改到初速。

命中分派：

- `hitEntity(entity)`：以「投射物位置 − 实体位置」为法线算反弹方向 → 构造 `HitResult(ENTITY, …)` → `hitTargets++`（`maxHitTargets > 0 && hitTargets >= maxHitTargets` 时 `alive = false`）→ 依次触发 `effects.onHitEntity/onHit` 与 `onHitEntity/onHit`。
- `hitBlock(pos, faceNormal)`：`status.bounces > 0` 时按 `bounceScaler` 缩放反弹速度并把位置推到面外 0.01 格，否则 `alive = false`。
- `maxHitTargets = -1`（`Piercing`）表示无限穿透。

### 2.3 `Modifier`（`magic/core/Modifier.kt`，原 `Revise`）

`Modifier : Magic`，唯一的额外 API 是 `effects(): List<BaseEffect>`——返回**克隆后**的 effect 列表。

`magic/modifier/` 下 27 个实现分两类：

1. **纯注入**：在 `init` 里 `effects += SomeEffect(...)`（如 `Homing` → `Tracking`、`MoreBounce` → `Bounce(8)`）；
2. **匿名效果**：在 `init` 里 `effects += object : BaseEffect() { override fun onActive() { … } }`，直接改 `status`/`projectile` 的字段（如 `Diffusion` 放大爆炸半径、`Piercing` 改 `maxHitTargets`）。

另有 3 个会**生成新投射物**（`ExplosionProjectile`、`BounceDuplicate`、`QuantumEntanglement`）——它们语义上更接近「法术」而非「修饰器」，但通过继承 `Modifier` 借用了手牌结算流程（结构债见 [issues/open.md](issues/open.md) 1.2）。

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
| | `Explosion` | 自定义爆炸：音效 + 粒子 + 距离衰减伤害与击退（**不破坏方块**，见 §8.1） |
| | `AreaFieldEffect` | 区域场基类：**每 tick** 结算 —— 范围内刷新迟缓 + 按半径拉拽 + **对半径内投射物施力（黑洞口把法术吸进去、重力场往下压）**；实体离开范围立即撤销迟缓。视觉由子类用 PD 粒子实现（`spawnVisuals`/`updateVisuals`/`clearVisuals` 三个钩子） |
| | `BlackHole` | 半径 5 引力场：迟缓 I + 拉拽生物和投射物；视觉委托 `BlackHoleFx`（小粒子暗核、倾斜双层吸积盘、星尘吸入、原组塌缩淡出）。星尘位置由 PD 积分 |
| | `GravityField` | 半径 5 重力场：迟缓 IV + 压低投射物；球面经线、顶部发源环、加速下压刻度波、落体细线与地面双井环，结束时固定结构淡出 |
| `visual/` | `Trajectory` | 头部一颗 `track` 白热菱形（本体标记）+ 发射器尾迹（按里程发射、寿命曲线驱动） |
| | `Trajectory` | 在投射物后方喷拖尾粒子（发射点反向偏移）；速度≈0 时退化为原地氛围粒子 |

**同类效果叠加**：统一用 `BaseEffect.stackScale`（√N）与 `isStackPrimary`（只有首个同类实例改写状态），
`Tracking`/`Reciprocate`/`Serpentine`/`GradualAccelerate`/`GradualDecelerate` 都改用了这两个辅助属性。

**球面几何**分两处：`util/ParticleShapes`（怎么撒点）与 `util/Polyhedra`（撒在什么拓扑上——截角二十面体的面板/边界点，护盾球壳在用）。
**球体采样**统一走 `util/ParticleShapes`：原版粒子的一次性撒点（`sphereVolume`，`Explosion` 在用），以及 PD 粒子共用的**取点工具**（`fibonacciSphere` 均匀球壳、`randomUnitVector`、`randomTangent`——黑洞/重力场/护盾在用）。已无调用方的 `sphereSurface` 在第 11 轮删除。

### 2.5 `MagicStatus`（`magic/core/MagicStatus.kt`）

投射物的运行时状态袋：`pos`/`velocity`（`org.joml.Vector3f`）、`damage`、`age`、`bounces`、`hitTargets`、`explosionDamage`/`explosionRadius`。所有效果共享同一份实例，读写顺序由 [EffectPhase](#27-effectphase效果执行顺序magiccoreeffectphasekt) 决定。

它是**服务端单例数据**：所有 effect 读写同一份 `status`，effect 之间的顺序会直接决定谁覆盖谁（见 §8）。

### 2.6 `TriggerSpell`（`magic/core/TriggerSpell.kt`）

`interface TriggerSpell { val triggerCast: Int; val payload: ArrayList<Magic> }`。

触发法术在 `Wand.castHand` → `loadPayload()` 阶段**额外抽牌**（预算 = `triggerCast`，这张法术自身也按 `cast` 计入预算），把抽到的法术克隆后填进 `payload`；抽到触发法术时会**递归**填充它自己的 payload，于是形成 `触发A( 触发B( 火球 ) )` 这样的链。

命中时由 `Trigger.onHit`（寿命到期当刻由 `DelayedTrigger.onExpire`）把 payload 里的投射物就地施放，并把 payload 内的修饰器效果注入这些投射物；**释放后立即清空 payload**（一次性）——payload 持有的是同一批实例，重复释放会让效果被反复注入、叠加。

### 2.7 `EffectPhase`：效果执行顺序（`magic/core/EffectPhase.kt`）

效果不再靠 `effects` 的插入顺序决定先后，而是各自声明阶段；`Magic.spell()` / `Magic.tick()` 按阶段**稳定排序**（同阶段内保持注入顺序）：

| 阶段 | onActive 位置 | onTick 位置 | 谁在用 |
|---|---|---|---|
| `SETUP` | 最先 | 最先（空实现） | `Position`、`Velocity` —— 写入出生点与初速 |
| `MODIFIER`（默认） | SETUP 之后 | 位置积分**之前** | 修饰器注入的效果（改速度/伤害/爆炸参数），以及 `Hurt`、`Trigger`、`Explosion` 等 |
| `GUIDANCE` | 修正之后 | 积分之前 | `SelfOrbit`、`QuantumEntanglement.OrbitEffect` —— 计算轨道目标速度，由Move扫掠 |
| `MOTION` | — | 中间 | `Move` —— 每 tick **唯一**推进 `status.pos` |
| `OVERRIDE` | — | 积分**之后** | `ChainArc` —— 慢漂移后的离散跃迁、落点检查与命中分派 |
| `VISUAL` | — | 最后 | `Trajectory`、`AreaFieldEffect`、束源/斥力场 —— 读本 tick 的最终位置 |

一句话规则：**普通改速用默认阶段，轨道导向用 `GUIDANCE` 并交给Move扫掠，离散跃迁等最终定位用 `OVERRIDE`，读最终位置用 `VISUAL`。**

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
                             ├─ GUIDANCE：周身环绕/量子复制体的目标速度
                             ├─ MOTION  ：Move 阻尼 → 子步进碰撞 → 命中回调 → status.pos 推进
                             ├─ OVERRIDE：连锁魔弹分段跃迁并检查落点
                             └─ VISUAL  ：Trajectory 读最终位置发粒子
    status.age++
    if status.age >= maxAge && alive: expire(); alive = false
projectiles.addAll(pendingAdd); pendingAdd.clear()
```

自然到期先执行 `expire()` → effects `onExpire()`，延迟载体释放payload，休眠水晶触发真实爆炸；随后进入普通死亡清理。提前碰撞死亡或强制清除不会调用onExpire。

死亡路径：`alive = false` → 下一 tick 的循环开头 `die()` → effects `onDie()`（`Explosion` 的 `onDeath = true`、`TeleportProjectile` 传送施法者、`Trajectory` 停发射器并移除头部粒子）。

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
  读档时经 `Magic.loadRuntimeState` 回灌。**当前没有使用者**（第 26 轮护盾改成「强度 vs 抗性 + 法力」后不再有需要跨存档的进度）；机制保留给将来「有进度的法术」。
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
| `ScreenShakePayload` | S→C | amplitude(0..1)、ticks | 经 `ClientPayloadSink.screenShake` → `client/ScreenShakeClient`：**只发重音那一拍**（8 字节），逐帧偏移/衰减在客户端本地算（见 §9 第 9 条） |
| `SelectSpellSlotPayload` | C→S | targetId、slot、pouch | 遍历背包：`pouch=false` 按 `wand_id` 匹配并写 `WAND_SELECTED_SLOT`；`pouch=true` 按 `pouch_id` 匹配并写 `POUCH_SELECTED_SLOT` |

**公共侧不引用客户端类**：S→C 包的处理器在公共入口注册，但真正的客户端逻辑通过 `network/ClientPayloadSink`
（两个可空回调）转发，由 `client/PrimalSpellsClient` 在初始化时安装实现；专用服务器上它们保持 `null`。

**编杖/编包不走自定义包**：一律走原版「物品对上物品」交互（`WandItem`/`SpellPouchItem.overrideOtherStackedOnMe`），
只有滚轮选槽需要显式同步选中槽（物品组件只有服务端权威）。

## 7. 客户端

| 子包 | 内容 | 触发方式 |
|---|---|---|
| `client/hud/` | `WandHudRenderer`（法力/冷却条） | 消费 S→C payload，缓存按 `wandId`，登出清空 |
| `client/tooltip/` | `ClientTooltipHandler`（组装词条）、`WandSpellTooltip`/`PouchTooltip`（自定义 tooltip 组件，都高亮选中槽）、`SpellSlotTracker`（法杖与小包共用的「预览槽位」缓存） | `RenderTooltipEvent.GatherComponents` + 组件工厂 |
| `client/input/` | `SpellSlotScrollHandler`（滚轮选槽：法杖与小包共用，发 `SelectSpellSlotPayload`）、`ClientContainerDragHandler`（Ctrl+左键拖拽交换，复用原版 `slotClicked(PICKUP)` 协议） | `ScreenEvent`，注册在 `PrimalSpellsClient` |
| `client/render/` | `DroneEntityModel`/`DroneEntityRenderer`（立方体/球体调试实体已随死代码清理删除） | `EntityRenderersEvent` |
| `client/ScreenShakeClient.kt` | 镜头冲击求值：`ViewportEvent.ComputeCameraAngles`（有限偏航/俯仰冲击，不改变翻滚/FOV）；CameraImpulse按世界时间+partialTick逐帧求值，脉冲两端归零，叠加不重置相位；整体乘原版 `options.damageTiltStrength`（设 0 = 玩家关掉，不再打扰） | 消费 `ScreenShakePayload`，注册在 `PrimalSpellsClient` |
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

1. **执行顺序由 `EffectPhase` 决定，不再看插入顺序**。`onActive` 按 `SETUP → MODIFIER → …` 升序，`onTick` 按 `MODIFIER → GUIDANCE → MOTION → OVERRIDE → VISUAL` 升序；同阶段内保持注入顺序。普通改速留在默认阶段；需要碰撞的轨道在GUIDANCE写目标速度，由Move推进并扫掠。普通轨道不能覆盖位置绕开命中；连锁魔弹的离散跃迁是显式例外，在OVERRIDE自行检查落点并分派命中，故能越过薄墙。
2. **`Magic.clone()` 是「从注册表新建实例」**，不继承任何运行期状态（当前速度、被修饰器改过的伤害/爆炸参数都不继承）。`id` 未注册直接抛异常——所以**法术 id 不能随便改**。
3. **`Modifier.effects()` 返回的是浅拷贝**：多枚子弹各自拿一份克隆，但克隆内部的引用字段仍可能指向同一对象，别用可变字段存跨实例状态。
4. **修饰器效果只在 `castHand` 时注入一次**：`Modifier` 自身不进 `MagicManager.projectiles`，不参与 tick。
5. **修饰器之间没有先后顺序**：所有抽到的修饰器都作用在同一枚投射物上（例如「火球 + 伤害增强 + 穿刺」= 基础 + 增强 − 惩罚）。修饰器效果只允许**可交换**运算：累加、累乘、乘零；禁止钳制（`coerceAtLeast`）与「重设绝对值」（如 `normalize().mul(绝对速度)`）。同类叠加用 `stackScale`/`isStackPrimary`。写法要求见 `magic/core/Modifier.kt`。
6. **`status` 是所有 effect 共享的可变状态**：同阶段内的写入顺序就是注入顺序（抽牌顺序），但这只保证「确定性」，任何效果都不应依赖它。
7. **命中回调每个 tick 都会触发**（投射物停在实体体内时）：穿刺玩法依赖这一点，`Move` 刻意不去重。
8. **触发法术 payload 是一次性的**：释放即清空，穿刺的实体命中只结算本体伤害，荷载保留至方块命中；释放后不会重复。
9. **公共侧不引用客户端类**：S→C 包一律经 `network/ClientPayloadSink` 转发（见 §6）。
10. **粒子表现有两条路**：法术粒子走 `particledrawing`（`wand.status.particleManager`，服务端权威 + 客户端插值）——投射物本体/拖尾/黑洞与重力场的球壳与运动粒子/护盾玻璃面板都在这一路（集合簿记用 PD 的 `ParticleBatch`，见 §9）；纯装饰性的一次性撒点走原版 `ServerLevel.sendParticles`（爆炸、无人机、传送、护盾弹开命中）。
11. **区域场每 tick 结算、离开即解除**：`AreaFieldEffect` 每 tick 刷新迟缓并施加拉拽，实体离开范围（或效果结束）时**立刻**撤销迟缓，不留尾巴。
12. **区域场的维度在首 tick 捕获**（`AreaFieldEffect.fieldLevel`）：不能每 tick 读 `caster.level()` —— 施法者跨维度后那是**新**维度，而场的坐标还在旧维度，会导致迟缓/投射物施力/粒子一起静默失效。

### 8.1 已知的「不是 bug」的预期行为

这些看起来像 bug，其实是刻意的设计（原 `issues/README.md` §3，现归到这里）：

| 项 | 说明 | 位置 |
|---|---|---|
| 穿刺 = 伤害 −8，可能为负 → 不结算伤害 | 加减法，与顺序无关；「穿刺后打不出伤害」是定价的预期结果 | `magic/modifier/Piercing.kt` |
| 爆炸**不破坏方块** | 预期行为（`explosionLevel` / `destroyBlocks` 死代码已删除） | `magic/effect/lifecycle/Explosion.kt` |
| `Magic.recharge` / `Magic.delay` 是有效字段 | `Wand.castHand` 与 `loadPayload` 会读取它们累加冷却 | `magic/core/Magic.kt`、`wand/Wand.kt` |
| 小包没有右键界面 | 编辑与法杖一致：滚轮选槽 + 物品对上物品装卸 | `item/SpellPouchItem.kt` |
| 蛇形与往复运动可叠加 | 蛇形只施加方向**旋转增量**，不再覆写方向 | `magic/modifier/Serpentine.kt` |
| 区域场**会影响魔法投射物** | 黑洞把范围内投射物吸向中心、重力场给投射物持续的向下力；**区域弹自身除外**（否则会被自己往下压/吸住）。这是刻意的：黑洞就该「把飞进来的法术吸掉」 | `magic/effect/lifecycle/AreaFieldEffect.kt` |
| 法术粒子的驱动方式与「谁持有运动状态」 | 四种写法：**组级编排**（`Draw.sphere` + `followEntity` + `spin`：跟随与旋转都在客户端本地求值，**0 包/tick**）/ `attachTo` 锚定（0 包/tick）/ 一次 `setVelocity` 或每 tick 一次 `setVelocityAll` 交给 PD 积分（0~1 包/tick）/ 一次 `trackAll` 批量写位置（1 包/tick）；**成员簿记归 `ParticleBatch`/`ParticleGroup`，位置与速度也读 PD 的权威值**，本模组只算数值。见 §9 | `magic/effect/lifecycle/{BlackHole,GravityField}.kt`、`magic/modifier/EnergyShield.kt`、`util/PdParticles.kt` |
| PD 粒子管理器由 `wand.pdParticles(level)` 兜底创建 | 管理器原本只在「持有者背包物品被 tick」时建立；未来的生物施法者不走 `inventoryTick`，不兜底就会出现「有伤害没粒子」 | `util/PdParticles.kt` |

（第 1、7 两条与上面 5./7./8./10./11. 有重叠，不重复列出。）

## 9. 与 ParticleDrawing 的接口现状

> **第 49 轮区域视觉更新**：黑洞视觉已拆到 `magic/effect/visual/BlackHoleFx.kt`，
> 固定结构改用 PD 组，退场直接追加缩放/淡出；旧的球壳重建、反弹与 Deferred 三拍已删除。
> 重力场改为经线/刻度波/双井环，固定结构淡出。当前模型、预算和验证见
> [design/field-effects.md](design/field-effects.md)；下方注明轮次的旧接线记录保留供迁移追溯。

- 依赖：`lib/particledrawing-${particledrawing_version}.jar`（当前 **1.0.17-ALPHA**；`compileOnly` + `localRuntime`，版本由 `gradle.properties` 驱动），`mods.toml` 声明 `required`、`AFTER`。
- 集合类粒子（黑洞球壳、被吸入粒子、下落粒子）**成员簿记统一交给 PD 的 `ParticleBatch`**：
  `add`/`ensureSize`(补齐到 N、被上限拒了就下次再补)/`forEach`(读权威 pos/vel)/`removeIf`(按位置回收)/`clear`；
  本模组只算数值（位置、速度、颜色、寿命）。原自研的 `ParticleSwarm` 已在第 10 轮删除，见
  [issues/resolved.md](issues/resolved.md) 第 10 轮与 [design/effects-api.md](design/effects-api.md) §0.3。
- **护盾玻璃**（`EnergyShield`）现状（第 26/39 轮；几何仍是第 16 轮那套）：面板拓扑是**截角二十面体**（足球）——
  12 个五边形 + 20 个六边形共 32 块（几何由 `util/Polyhedra.kt` 生成，带 `require` 自检），
  **按「飘散位移」分组的 `ParticleGroup`**（第 39 轮：位移相同的面板共用一个组 —— 格挡玻璃 / 破口轮廓 /
  爆炸碎裂各只占 1 个组，一次爆炸的 arm 日志从 32 条降到 3 条；只有「投射物打穿、每片沿自己法线向外」
  才不得不一片一组），沿各自边界铺一圈粒子（每条棱 6 点）。
  ⚠️ **密度按「粒子实际尺寸」反推**（第 20 轮实测的换算，第 21 轮定的取向）：PD 的
  `quadSize = scale × 0.1 × texScale`，实测 `texScale ≈ 2.1` → **实际格数 ≈ scale × 0.213**，不是 `scale` 本身。
  棱长 1.166 格（半径 1.7）÷ 每条棱的点数 = 点距。
  **取向：点距明显大于粒径，保留「粒子感」，靠点阵读出五/六边形** ——
  现在 **6 点/棱（点距 0.194 格）+ 亮边 `scale = 0.26`（实际 ≈0.055 格）**。
  第 20 轮曾把点距做到 ≈ 粒径（14 点/棱 + scale 0.40 = 2520 颗）去追求**连续实线**：
  球外看是干净的线框，但**球内第一人称**最近处一颗方块占 80+ 像素，糊成粗带、多边形也看不清
  （实机反馈「不完美的形状很丑」）。结论：**不要再往连续实线调**（要成倍粒子，且近看必然糊）。
  第 23 轮按实机反馈（太挡视野 / 一直转会晕 3D）**重做成「受击处展开的玻璃护盾」**，第 26 轮沿用这套表现：
  **平时完全不可见**，只在两个时机现形 —— ① **格挡成功**：受击方向最近的那一块面板亮起一块玻璃板
  （**第 41 轮起是发光蓝**：蓝亮边 + 半透明蓝填充，两者都 `additive` + `glowing` —— 原来的冷白在雪/水/天空
  这类亮场景里和背景一个色，实机根本看不清；稀疏点阵的取向没变。约 0.35 秒淡出，每次格挡各出一片）；② **打穿（超窗口预算 / 法力不足）**：
  **投射物**给「受击那一块 + 相邻一圈」、**爆炸**给「**这一发波及到的全部面板**」（面心到爆心 ≤ 爆炸半径；
  半径 6 的核弹摆在壳外 3 格时 32 片全被波及 ⇒ 整罩碎裂，朝爆点的片白热、背对的暗一档），
  向外飘散 / 沿爆点方向被吹开 + 淡出，随后是**持续整段破口期的粉色轮廓**
  （`Color.ofInt(255,118,118,150)`，提示攻击者「这里能打」）。
  做法上 `shieldPanels` 是**方向索引**（`maxByOrNull { it.center.dot(受击方向) }`），
  `panelNeighbours`（`Polyhedra.adjacency`：共享一条棱 = 相邻）给出破口那一圈；
  每个时机用 `spawnGlassPanels` 现铺**新建的组**（白色玻璃/碎片**不** `followEntity`：盖章在原地，
  判定仍跟着玩家；**粉色破口轮廓 `followEntity` 跟随持有者** —— 第 40 轮口径，破口方向是相对玩家算的，
  轮廓留在原地就会与真正的破口错位；⚠️ 跟随时**轴心必须取在实体脚底**，`followEntity` 的 offset 参数
  不改变渲染位置，见 [design/effects-api.md](design/effects-api.md) §0.4 第 3 个坑），
  `delay + fadeOut(removeAfter = true)` → PD 自己销毁。
  **破口时长按"超出抗性多少"给**（第 40 轮）：`20 tick + 超出每点 6 tick`，封顶 60 tick。
  **原版投射物**（箭矢/三叉戟/火球/雪球/药水…）走**同一套**判定与结算，挡下即按壳面法线**反弹**
  并把归属改成持有者；除每 tick 扫场外还有 `ProjectileImpactEvent` 命中兜底
  （见 [design/combat-pricing.md](design/combat-pricing.md) §4.97/§4.98）。
  格挡结算自第 39 轮起是「**先查 1 秒窗口预算、再查法力**」（抗性既是单发阈值、也是每秒可承担的强度）——
  见 [design/combat-pricing.md](design/combat-pricing.md) §3/§4.95。
  **自转、逐块构建波、消散波、整罩「一闪而碎 / 恢复一闪」、以及第 22 轮那套「分批建组补相位」全部删除** ——
  护盾不再有常驻外观，也就没有相位问题。玻璃的「磨砂」观感是**临时降级**：程序化粒子的贴图写死为纯白方块，
  真玻璃要等 PD 开放贴图/UV（需求已登记，见 [issues/pd-api-requests.md](issues/pd-api-requests.md)）。
- 已接线（七种用法，均对应 PD 的低带宽写法）：
  1. **组级编排**：`Draw.<形状>(...)` 铺形状 + `.followEntity(uuid, offset)` / `.spin(axis, radPerTick)` /
     `.fadeIn/.fadeOut` / `.move`——护盾的玻璃面板组（每次格挡/碎裂现铺一组）、黑洞球壳、重力场的波环与井环；
     跟随/旋转/淡入淡出/平移都由客户端每 tick 本地求值，**0 包/tick**；
  2. **锚定**：`ParticleHandle.attachTo(实体, 世界空间偏移)`——需要「一堆各自独立的粒子钉在实体上」时用
     （当前没有使用者；护盾已改用第 1 条）；
  3. **批量设速**：`ParticleBatch.setVelocityAll(velocities)`——黑洞被吸入粒子（吸力**随距离变化**，
     每 tick 重算每颗粒子的速度），之后由 PD 逐 tick 做 `pos += vel`，**1 包/tick、与粒子数无关**；
  4. **速度驱动（一次设速）**：生成时一次 `setVelocity`，之后交给 PD 积分——`Trajectory`（拖尾）与
     黑洞**退场**的整壳塌缩粒子（速度 = 距离/寿命，到点正好抵达中心并自行过期，**0 包/tick**）；
     回收判定只读 `ParticleBatch.forEach/removeIf` 给的权威位置；
  5. **恒定力（一次施力）**：生成时一次 `applyForce(加速度, ticks = -1)`（`spawnParticle(..., acceleration = ...)`），
     两端逐 tick 同为 `v += a; pos += v`——重力场下坠粒子（**0 包/tick**，起步慢落地快，这才是「重力」的样子）；
  6. **逐粒子缓动移动**：`handle.move(target, ticks, easing)`——每颗粒子一条指令（一次性），
     缓动在**客户端逐帧求值**，位置总包数 = 粒子数 —— 黑洞**进场**的整壳「从球心长到球面」在用；
  7. **一次性向心爆发**：`spawnParticle(pos, color, scale, lifetime, velocity)`，不进任何集合
     （自带寿命、不需回收）——黑洞退场塌缩粒子的写法（重新生成 + 向心初速）；
  9. **一次性向心加速（悬停 → 砸进中心）**：生成时一次 `applyForce(向心加速度, -1)`，加速度按
     `a = 2d/(T(T+1))` 反推 —— 离散积分 `d(n) = a·n(n+1)/2` 下第 T tick 恰好抵达中心，寿命 = T 同刻过期。
     观感是「先悬一下、最后几 tick 猛地塌进去」（黑洞**退场**在用，第 18 轮把匀速直线收缩换成了它）；
     全程 **0 逐 tick 包**、本模组不留跨 tick 状态；
  10. **用 `scale` 做径向胀缩**（PD 1.0.12 起）：`ParticleGroup.scale(ratio, ticks, easing)` 现在
      **连「粒子到轴心的距离」一起缩放** —— 半径 3 的圆 `scale(3f)` 之后半径就是 9。
      整壳「胀开 / 缩回」一个调用写完、客户端本地求值、零带宽。第 28 轮起**爆发的球体放大**在用这条
      （内爆结束后球体从 0.35R 胀到 R，见 `magic/effect/lifecycle/ExplosionFx.kt`）。
  11. **多拍连招**（第 19 轮）：黑洞退场「整壳塌缩 → 反弹小球 → 收成一点」三拍，
     每拍都在 `Deferred` 里排下一拍（`Deferred.after(...)` 返回**可取消句柄**：
     特效提前结束时撤销还没到点的重音，否则会出现「场没了、地面还在被压出环」）。
     ⚠️ `Deferred.tick()` 必须**先摘出到期任务、再统一执行** —— 任务体会再排新任务，
     边迭代边执行会抛 `ConcurrentModificationException` 打崩服务端（第 19 轮实测踩到）。
  12. **落点重音（一次性粒子 + 镜头冲击）**：`spawnParticle(pos, color, scale, lifetime, velocity)`
     炸一圈 + `ScreenShake.shake(...)`（黑洞塌缩爆闪、重力场波环落地溅射）。
     这些时刻**晚于特效自己的生命周期**，所以由 `util/Deferred`（服务端延迟队列，`MagicManager` 每 tick 推）
     排到点触发；`Deferred` 是**我们自己的时序簿记**，不涉及任何粒子同步/求值（那仍然全归 PD）。
  8. **「重建组」代替「给旧组追加动画」**（第 17 轮）：需要「运行期才决定何时开始」的组级动画时，
     不要对旧组 `delay + fadeOut`（游标是绝对时间，晚追加＝瞬间完成），而是
     **销毁旧组、在原地按同样的成员/轴心建新组**，再对新组下发 `delay + fadeOut` ——
     新组程序起点就是 now，动画时序与销毁时刻都精确。代价：一次性重铺成员（与构建同量级）。
     （当前无使用者：第 17 轮为护盾消散波引入，第 23 轮重做后随常驻球壳一起删除。）
- 管理器获取：统一走 `wand.pdParticles(level)`（缺失/跨维度时现场建立），未来生物施法者也有粒子表现；
  生成一颗粒子的写法统一走 `ParticleManager.spawnParticle(...)`（`util/PdParticles.kt`，不含簿记）。
- 未使用：`attachToLocal`（需要「贴在身前/身侧」的粒子时可用）、`applyForceAll`（需要每颗各自的力时才有意义；
  恒定的力已用逐粒子 `applyForce` 接入重力场）、`ParticleGroup.pulse`/`recolor`/`movePath`/`expression`、
  `ParticleGroup.destroyAfter`（收起动画用 `fadeOut(removeAfter = true)` 即可）。
- 未接线：`.pdrawc` 动画特效（锚点/播放时钟/资源注册表那一整套）。设计方案与落地阶段见 [design/effects-api.md](design/effects-api.md)；
  1.0.8-ALPHA 新增的 `EffectCallbacks` / `EffectVarStore` 正是为此准备；1.0.9-ALPHA 另提供 `ParticleBatch`（程序化粒子集）与统一的实体锚点身份；
  1.0.11-ALPHA 再补批量设速/批量施力（`setVelocityAll`/`applyForceAll`）与「运动指令解除实体锚点」的修正。
- 历史 problem 记录：未决需求见 [issues/pd-api-requests.md](issues/pd-api-requests.md)，已闭环条目见 [issues/resolved.md](issues/resolved.md) 附录 B
  （6 条已全部闭环：PD 修了 3 处位置链路偏差 + 补了 `position`/`trackAll`/`applyForce`/`attachTo`）。
- ✅ **依赖构建口径（已解决）**：PD 每加一批 API 就提 `mod_version`（当前 **1.0.17-ALPHA**），本模组的
  `particledrawing_version_range=[1.0.14-ALPHA,)` 已能表达「最低可用构建」——旧构建（没有 `position`/`applyForce`/
  `attachTo`/`trackAll`，或 §7.3 的 `ParticleBatch`/三入口锚点，或 1.0.10 的 `setVelocityAll`/`applyForceAll`）
  会在加载期被 FML 拒绝，不再等到运行期报 `NoSuchMethodError`。
- ⚠️ **遇到 ParticleDrawing 的 API 缺陷或能力缺失时的处理政策（硬性）见 [conventions.md](conventions.md) §9 与 [design/effects-api.md](design/effects-api.md) §0**：
  不得在本模组自行另写一套替代实现，应停止尝试并列出 API 修改需求。


### 第 51 轮：区域场跟随最终定位

`AreaFieldEffect.phase = VISUAL`，范围结算与表现统一读取 Move/OVERRIDE 之后的场心。
黑洞暗核/内盘、重力场球壳/井环移动时更新 PD 组轴心；黑洞外环用 PD 表达式仿作惯性椭圆，
停止后恢复。已发射的世界空间粒子保持历史轨迹。造型与预算见 [design/field-effects.md](design/field-effects.md)。


### 第52轮新增内容的核心接缝

`Projectile` 保存 `launchDirection`，Velocity 乘速使用副本；`motionEnabled=false` 停止 Move（含命中后子步）与恒定加速度。
短命冻结状态由 MagicManager 驱动和停服清理；不会持久化。直接伤害效果声明 `DirectDamage`，
Hurt/电弧/束源通过 `SpellDamage` 共用护盾和冰冻接入；水晶爆炸通知只排队、不递归结算。
内容规则和新增包文件见 [design/spell-expansion.md](design/spell-expansion.md)。


### 第53轮视觉口径

粒子接线改为MC实色方粒，尺寸/寿命退场；不再用柔光、加色或alpha渐隐。伤害/范围/移动/抽牌机制保持不变。
Trajectory 不再引用客户端 ParticleTextures；公共侧仅使用PD方形原语。当前造型见 design/minecraft-visuals.md。


## 第54轮配方与反应补充

用户授权拓展旧机制：Wand和触发荷载经SpellAssembly保存修饰配方，后代重建非生成型修饰effect；
生成型修饰不向后代传播。伤害数值在基础构造后按牌序onActive计算，小/大爆炸不在onSpell重设参数。
MagicManager在本tick投射物推进与pendingAdd合并后执行同维度SpellInteractions快照，
反应只标记存活/储能，实际死亡仍走下一轮die，不在列表遍历期间删除对象。
护盾弹反射同时修改Projectile.caster和每个BaseEffect.caster；无人机非生成型配方通过实体NBT持久化。
详见 [design/spell-quality.md](design/spell-quality.md)。


### 第55轮运动与碰撞补充

Projectile.motionEnabled只控制位置积分；collisionEnabled独立控制接触查询，区域场必须关闭后者，不能关闭前者代替。
发射初速统一乘1.6；继承当前世界速度的子弹必须使用SpellAssembly.launch，抵消基础速度/速度卡/全局倍率，不能二次放大。
Tracking从age=0的首tick生效，零距离方向不归一化。throughBlocks仅跳过方块检测，不跳过本tick位置更新。


### 第56轮持杖预热与出生点

真实玩家持杖会话先经过16tick装备预热，Wand.spell在抽牌/扣法力前阻止未就绪施法；不能用status.delay代替这段会话计时。
WandCastingFx.origin是汇聚点与持杖弹体出生点的共同来源；普通delay/recharge继续用原计数及余数结算，只由视觉读取。
放下/切杖/死亡/跨维度/登出必须删除该会话的永存PD组。会话不保存到NBT，非玩家及直接配方调用保留原有施法入口。


## 第57轮：宝石出生点与有限退场

持杖玩家出生点改到视线前方0.85格、视线上方0.25格、手侧0.38格；模型、汇聚与发射共用位置口径。施法迸发只在成功释放第一枚投射物后触发，不改牌堆/伤害/冷却语义。持杖会话切换从立即销毁改为16tick有限退场；死亡、跨维度、登出和停止仍立即取消，包括尚在退场的会话。

## 第63轮运行时契约补充

轨道类`SelfOrbit/SphereOrbit/QuantumEntanglement`改为`GUIDANCE`：SETUP→MODIFIER→GUIDANCE→Move扫掠→OVERRIDE→VISUAL。轨道不能在Move之后瞬移覆盖坐标，否则复制体沿实际轨道没有命中判定。GUIDANCE出生排序在数值修正之后，量子复制体分别从±2.2格偏移出生；复制体保留独立effects和联动死亡。

寿命计数达到maxAge的当刻，MagicManager先转发一次`onExpire`，再标记死亡；下一刻的`onDie`只做清理。DelayedTrigger仅从onExpire消费一次payload，提前碰撞/强制清理不冒充到期触发。实体命中不扣bounces，只有方块反弹扣次数；BounceExplosion仅响应成功方块弹跳。

法杖时间统一完整刻，回蓝为每刻；存档迁移与余数取消见[design/wand-tiers.md](design/wand-tiers.md)。穿刺扫掠每个子步使用不变起点，实体命中回调更新status.pos不得改变后续子步的积分基准。

## 第64轮：链锯结算与电弧导电窗口

`Magic.resetsCastDelay`默认false；只有成功支付法力并实际施放的投射物参与。Wand按投射物牌序结算，链锯清零此前的delay，后续投射物仍追加delay；出现有效清零时不再由法杖delay下限重新顶回去。无链锯的原有max(法杖delay,卡牌delay)语义保留。链锯-3刻recharge与同次施放的卡牌、末尾回洗相加，最终钳制至0；不会跨施放保留负余数。触发荷载中的链锯不清除载体那次施放的delay。

ChainBolt基础maxAge=4，实际初速0.75格/刻（字段0.46875×全局1.6倍率），无阻尼，单跳半径=命中时实际速度×4=3格。首击建立锚并重置age=0，从当刻开始按修正后的maxAge计导电窗口；首击当刻不再二次跳跃，随后每刻最多一跳，默认含首击共4个敌人。寿命修正继续增加窗口、速度修正继续扩大范围。未命中前也只有4刻飞行；墙体截断、已命中过的UUID去重、每跳伤害×0.7。

DelayedCastFx的锁印由原生PD批量trackAll跟随最终位置，不再提前向外飞散。MagicManager寿命到期调用onExpire时，视觉释放与DelayedTrigger荷载出生发生在同一服务端刻、同一最终位置；普通死亡只收尾，不冒充一次成功施放。

第64轮追加：SphereOrbit彻底移除（旧NBT未知id保留原空槽，不移位）；SelfOrbit半径改`2+速度×0.5`、最大6。CrystalReaction在onExpire调用幂等detonate，预警时间取min(剩余寿命,引爆倒计时+1)，只在最后40刻闪白，半周期ceil(剩余刻/8)并限制1～5；onDie只做碎裂清理，不重复结算爆炸。

## 第65轮：跃迁、强度击退与自伤

连锁魔弹取消“先碰撞命中再停住连锁”：同一载体低速漂移，onTick在OVERRIDE执行离散跃迁，仅检查落点方块；经过的薄墙不阻断。基础15刻，在age=4/7/10/14跃迁；调度为ceil(跳序×10/3)，延寿后继续同一时间轴，不累积四舍五入误差、不重置age。每次按实体id取3格内目标，避开最近两次命中；无目标则沿当前来向前跳搜索半径×0.625，允许之后重访更早目标。每次命中仍4点，不再按跳数衰减。初速经数值修正后乘0.025慢漂移，搜索半径为当前漂移速度/0.025×4×areaScale，运动修正/外力继续联动。

自伤以Projectile.selfDamage为统一运行时属性。Explosion/CrystalReaction默认危险，黑洞显式开启，穿刺在prepare/onActive开启；Move直接读取当前值，并要求普通弹先离开施法者碰撞箱才允许返弹命中自己，避免枪口重叠误伤。电弧、等离子束和共鸣脉冲也使用canHarm筛选。爆炸的统一入口总会纳入施法者，仍由护盾与创造/旁观等原规则判断能否受伤；不额外绕过原版无敌模式。子弹重放非生成型穿刺配方后自动具有同样自伤属性。

SpellImpact是直接伤害和爆炸共用的唯一原版受伤/推力入口，临时绕过无敌帧但finally还原。数据包将primalspells:projectile加入minecraft:no_knockback，禁用原版固定推力；成功受伤才追加按强度计算的速度，尊重击退抗性。黑洞暗核伤害禁用向外推力，保留向心吸力；不改原版箭矢、近战和其它模组伤害类型。

跃迁命中仍调用Projectile.hitEntity公共分派：载体基础maxHitTargets=0，寿命而非实体碰撞次数终结它；穿刺卡可改为-1并启用自伤/穿盾。爆炸、复制等onHit修正会在跃迁落点真实触发，不只播放一条装饰电流。

## 第66轮：伤害渠道与惯性质量

SpellDamageType定义8条独立伤害渠道，ModDamageTypes注册并安全解析对应数据包类型。Projectile.damageType为直接伤害类型；SpellDamage读该值，Explosion强制使用EXPLOSION，不把载体命中类型传给径向爆炸。冰冻附魔只转换直接伤害为ICE，碎冰奖励加入同次伤害；冻结控制不额外产生第二份伤害。原版is_projectile/is_explosion/is_fire/is_freezing标签参与各自抗性/免疫，8类均no_knockback以继续使用SpellImpact统一推力。

Projectile.mass为惯性质量（默认1，黑洞256、重力场32、等离子束4），不是引力源强度；质量越大，同一力的加速度越小。实体用NeoForge持久数据`primalspells:mass`正数覆盖，未标注时当前碰撞箱体积/0.729，默认夹0.25～256。无效质量回退；有效显式质量夹0.05～100万。黑洞生物拉力、魔法弹引力、重力场弹体下压、斥力和爆炸对魔法弹冲击都读取目标质量。黑洞重弹只衰减径向速度，保留外部施加的切向移动，避免两个重黑洞互吸积累高速，也不破坏移动场源穿墙。

## 第67轮：实际释放与施法者反冲

后坐力只由Wand.castHand在成功扣蓝、创建独立投射物并spell后queueCastRecoil；触发荷载/量子复制/弹跳复制不排队，避免飞行中凭空反复推施法者。反冲与伤害击退分离，沿释放速度反向按施法者质量施加一次push，hurtMarked走原版实体同步。基值+加值最后乘倍率，防止两张后坐力修正的顺序改变结果。

ConcentratedLight在onSpell保存经修正的发射速度，停止Move；awaitingRelease期间只更新自身蓄能视觉，暂停其它逐刻效果，直接施法每刻跟随当前杖首。age=20当刻重新取准星方向，恢复速度/运动、播放宝石亮态并施加已排队反冲，随后接原有effect阶段和Move扫掠。寿命仍由MagicManager计数，80刻含20刻蓄能+60刻飞行，延寿增加飞行窗口；荷载/复制体在世界出生点蓄能并沿原来向释放。施法者死亡/换维度取消蓄能，不发射。蓄能体不参与其它法术的受力/连锁反应，避免未出手就被移动或引爆；释放后恢复原有联动。

黑洞affectsEntity排除Player.isCreative且abilities.flying的实体；创造落地仍被吸引，飞行豁免也不刷新迟缓或暗核命中，旧迟缓按离场规则清理。质量F/m不变；生物每刻速度保留96%，重型投射物只对径向速度施加最多4%阻尼。

## 第68轮：固定聚光阵与一次命中护盾上下文

ConcentratedLight在创建时保存launchDirection，经修正的速度在onSpell保存；awaitingRelease期间只播放原地聚焦动画，不更新杖首坐标、不读玩家当前lookAngle。age=20沿launchDirection释放；普通修正从释放后继续原流水线，默认maxBounces=5。远处聚光阵发射不再次在当前持有者杖首播放闪光，避免误导发射位置。

护盾窗口预算随同杖卡数立方增加，所有记账仍仅由primaryShield维护。resolveIncoming超预算/付不起MP时开破口并返回一次有效ShellHit，阻止破盾当次伤害；absorbExplosion同样在开破口后返回已抵扣。已存在破口、穿刺和内部来源的绕过保持不变。

Projectile.shieldedImpactTargets保存本次已被ShieldGuard拦下的实体UUID，SpellDamage和同载体Explosion都读取它。Projectile.tick开始清空，onDie不清空，避免一次命中后的死亡爆炸第二次击穿；只保护同一个载体的同次命中，不给目标全局无敌、不阻止其它法术/旁人受伤。复制体不继承该运行时集合。

TestDummyEntity在knockback之外覆盖push(x,y,z)，并设原版KNOCKBACK_RESISTANCE=1，法术SpellImpact的直接推力也不能移动靶子。原有伤害显示/装备/攻击行为保留。


## 第69轮：纯视觉发射反冲

汇聚之光不再调用releaseCastRecoil，基础recoil归零；付费施法排队不会在释放时推施法者，反冲增幅也不会为本法术触发实际推力。20刻蓄能、世界部署点/方向和5次反弹不变。反冲只在ConcentratedLightFx的镜阵上表现。


## 第70轮：轻反冲与护盾拦截次序

Projectile.hitEntity现在先调用ShieldGuard拦截，成功返回true，不计hitTargets、不置死亡、不派发onHitEntity/onHit；Move收到true立即结束本刻旧扫掠，保留护盾给出的壳外位置与反射速度。避免先死亡后反射，以及旧newPos覆盖反射位置。伤害通道里的ShieldGuard仍为绕过公共命中入口的光束提供兜底。ChainArc的跃迁入口同样尊重true，恢复慢漂移速率但保留被反射的方向。

Projectile.recoilOnCast默认为false，蓄能法术可显式true让付费queueCastRecoil立即施力；releaseCastRecoil仍用recoilApplied保证一次性，复制/触发不排队。汇聚之光recoil=0.06、recoilOnCast=true，在拿牌实际付费出手时轻推，20刻释放不再次推。


## 第71轮：球壳接点与长按移动

Move宽相位另查询持盾LivingEntity，扩大到球壳而非只看本体；按真实相邻子步from/to调用ShieldGuard.interceptSegment，早于实体碰撞、方块后续推进和VISUAL。护盾反射成功立即停掉本刻入射扫描。EnergyShield对这类已由Move负责的运动弹不再于实体tick被动扫场，避免反射晚于光束绘制或使用pos-velocity造出未走过的线。原版投射物、静态束源及特殊命中兜底保留。

Projectile.shieldReflectionPoint记录本刻精确球壳接点，Projectile.tick清空；ConcentratedLightFx把白光脊/金侧线接到该点而非推出壳外0.05格后的安全位置，下一刻也从此点续接。成员簿记/插值仍由PD原生完成，本侧仅提供真实路径端点。

WandItem为所有法杖提供原版USE_EFFECTS=UseEffects(canSprint=true,interactVibrations=true,speedMultiplier=1)，保留startUsingItem与onUseTick连发，不用mixin改玩家输入。


## 第72轮：PD外观与完成信号

PD1.0.16负责track位置与寿命外观分离、原生尺寸/颜色帧间插值、变量目标重定向和静止光照缓存刷新。普通有限编排退场由客户端完成信号驱动清理，服务端兜底属于PD；本侧仅给几何和时长。法杖及束端表达式暂不接retire，因为表达式/变量仍不进入该完成通路，缺口见issues/pd-api-requests第72轮。其余法术结算、20刻聚光锁定、5墙反弹、护盾真实子步交点和长按原速均保留。


## 第73轮：短距制导与负延迟

Tracking在GUIDANCE阶段旋转当前速度指向锁定生物的碰撞箱中心，数值修饰在前、Move真实碰撞在后；方向对准不增加速度。有效目标持续保留，掉落物、显示实体、盟友及创造/旁观玩家不进入目标集合。

负delay既抵扣本次卡牌累计delay，也降低法杖基础delay下限；正delay仍沿用与法杖基础下限取大的口径。链锯在抽牌位置清零，后置正delay仍可追加。全向散射只在激活阶段旋转初速，全空间均匀随机，不在飞行中不断重抽方向。

持续光束先按真实球壳入射点从近到远测试护盾，再用同一个截点绘制、伤害及水晶反应；不改变静态束源的位置和朝向。穿刺仍绕过护盾。起点在生物碰撞箱内也参与束伤害，保证自伤和近距离判定。
