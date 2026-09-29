# 代码导航（code-map）

> 用途：**知道要改什么，但不知道文件在哪**。结构规则见 [conventions.md](conventions.md)，运行机制见 [architecture.md](architecture.md)。
> 路径均相对 `src/main/kotlin/work/nekow/primalspells/`（Java mixin 在 `src/main/java/…`）。
>
> 检索约定：本仓库**没有** `.codegraph/` 索引，找代码用 `grep` / `glob` / `read`。类名与文件同名，法术 `id` 与类名可能不同（对照见 §3）。

---

## 1. 「我要改 X → 去哪里」

| 我想… | 主要文件 | 关联文件 |
|---|---|---|
| 改施法节奏 / 牌堆抽牌规则 | `wand/Wand.kt`（`spell`/`draw`/`castHand`/`reloadIfNeeded`） | `item/WandItem.kt`（触发时机）、`wand/WandManager.kt`（持有者/回收/懒加载） |
| 改法力上限 / 回复速率 | `wand/Wand.kt`（`mana`/`charge` 字段与 `tick`） | `item/WandItem.kt`（`syncStats`） |
| 改投射物飞行 / 碰撞 | `magic/effect/geometry/Move.kt` | `magic/projectile/Projectile.kt`（`hitBlock`/`hitEntity`） |
| 改命中伤害 | `magic/effect/lifecycle/Hurt.kt` | `magic/core/MagicStatus.kt`（`damage`） |
| 改爆炸表现 / 范围 | `magic/effect/lifecycle/Explosion.kt` | `magic/core/MagicStatus.kt`（`explosion*`） |
| 改区域场（黑洞 / 重力场） | `magic/effect/lifecycle/AreaFieldEffect.kt`（半径/迟缓/拉拽/投射物施力钩子） | `BlackHole.kt` / `GravityField.kt`（参数与 PD 粒子）、`magic/effect/visual/ParticleSwarm.kt` |
| 改投射物外观（粒子） | `magic/effect/visual/ProjectileBody.kt`（本体）、`Trajectory.kt`（拖尾） | `magic/effect/visual/ParticleSwarm.kt`（力驱动粒子）、`util/PdParticles.kt`（管理器）、`lib/particledrawing` |
| 改「效果谁先跑」 | `magic/core/EffectPhase.kt`（阶段表） | `magic/core/Magic.kt`（排序执行） |
| 加一个新的法术 | 见 [spell-development.md](spell-development.md) | `magic/core/MagicManager.kt`（注册） |
| 改某法术的数值 | `magic/projectile/<类>.kt` 或 `magic/modifier/<类>.kt` 的字段 | id ↔ 类见 §3 |
| 改判定某个法术是否可用 / 代价 | 对应法术类的 `mana`/`delay`/`recharge`/`cast` | `wand/Wand.kt`（结算） |
| 加修饰器 | `magic/modifier/`（照抄同类） | `magic/core/Modifier.kt`（基类）、`magic/effect/BaseEffect.kt`（叠加辅助） |
| 加新效果行为 | `magic/effect/{geometry,lifecycle,visual}/` | `magic/effect/BaseEffect.kt`（钩子 + `phase`） |
| 改长按施法 | `item/WandItem.kt`（`use`/`onUseTick`/`getUseDuration`） | `event/WandEventHandler.kt`（右键屏蔽） |
| 改法杖物品内装卸法术 | `item/WandItem.kt`（`overrideOtherStackedOnMe`/`placeMagic`/`clearMagic`/`selectedSlot`） | `magic/core/MagicManager.kt`（`create`） |
| 改小包容量 / 内容 | `item/SpellPouchItem.kt`（`SLOT_COUNT` + 组件读写） | `client/tooltip/PouchTooltip.kt` |
| 改小包/法杖的键鼠操作 | `item/SpellPouchItem.kt` / `item/WandItem.kt`（`overrideOtherStackedOnMe`） | `client/input/SpellSlotScrollHandler.kt` |
| 改滚轮选槽（法杖/小包） | `client/input/SpellSlotScrollHandler.kt` | `network/SelectSpellSlotPayload.kt`、`registry/ModItems.kt`（选中槽组件） |
| 改 HUD（法力/冷却/护盾条） | `client/hud/WandHudRenderer.kt`、`ShieldHudRenderer.kt` | `network/SyncWandStatsPayload.kt`、`SyncShieldPayload.kt` |
| 改 tooltip 文案/布局 | `client/tooltip/ClientTooltipHandler.kt`、`WandSpellTooltip.kt`、`PouchTooltip.kt` | `util/Lore.kt`（词条类型） |
| 改 Ctrl+左键拖拽交换 | `client/input/ClientContainerDragHandler.kt` | `mixin/AbstractContainerScreenInvokerMixin.java` |
| 改法术 id ↔ 物品映射 | `registry/ModItems.kt`（`MAGICS`/`spellItem`/`spellIdOf`） | — |
| 加数据组件 | `registry/ModItems.kt`（`COMPONENTS`） | `item/component/WandID.kt`（值类型范例） |
| 加实体 / 伤害类型 | `registry/ModEntities.kt` / `ModDamageTypes.kt` | `PrimalSpells.kt`（挂 bus） |
| 加网络包 | `network/`（照抄同类） | `PrimalSpells.kt`（`RegisterPayloadHandlersEvent`）、S→C 需接 `ClientPayloadSink` |
| 改无人机 AI | `entity/DroneEntity.kt` + `entity/Drone{Attack,FollowOwner,Height}Goal.kt` | `magic/projectile/DroneSwarm.kt`（召唤） |
| 改测试木桩 | `entity/TestDummyEntity.kt`、`entity/DamageNumber.kt`、`item/TestDummyItem.kt` | `registry/ModEntities.kt` |
| 改语言文本 | `resources/assets/primalspells/lang/{zh_cn,en_us}.json` | `util/Lore.kt`（key 定义） |
| 改贴图 / 模型 | `resources/assets/primalspells/{items,models/item,textures/item}/` | 命名规则见 [conventions.md](conventions.md#3-命名规则) |

---

## 2. 包级文件清单

### 入口与注册

| 文件 | 内容 |
|---|---|
| `PrimalSpells.kt` | 公共 `@Mod` 入口：事件总线注册、DeferredRegister 挂 bus、实体属性、网络包注册（S→C 经 `ClientPayloadSink` 转发，不引用客户端类） |
| `registry/ModItems.kt` | 物品（法杖/小包/木桩/编辑台物品 + 动态 N 个法术物品）、6 个数据组件（含 `POUCH_SELECTED_SLOT`）、创造栏标签、法术 id ↔ 物品查表 |
| `registry/ModEntities.kt` | `drone`、`test_dummy` |
| `registry/ModDamageTypes.kt` | `primalspells:projectile` 伤害类型 + `projectileSourceOrNull()`（数据包缺失时安全降级） |

### 法术系统 `magic/`

| 文件 | 内容 |
|---|---|
| `core/Magic.kt` | 法术基类：`id`/`mana`/`delay`/`recharge`/`effects`/`lore`、按阶段执行的 `spell`/`tick`/`die`、`clone()`（新建施法实例） |
| `core/EffectPhase.kt` | 效果执行阶段：`SETUP` / `MODIFIER` / `MOTION` / `OVERRIDE` / `VISUAL` |
| `core/MagicStatus.kt` | 投射物运行时状态：`pos`/`velocity`/`damage`/`age`/`bounces`/`hitTargets`/爆炸参数 |
| `core/MagicManager.kt` | 36 个法术的注册表（`init`）、投射物列表、每 tick 驱动、`create` |
| `core/Modifier.kt` | 修饰器基类，`effects()` 返回克隆后的效果 |
| `core/TriggerSpell.kt` | 触发法术接口（`triggerCast` + `payload`） |
| `projectile/Projectile.kt` | 投射物基类：装配 4 个内建效果、命中/弹跳分派、`maxAge`/`maxHitTargets` 等字段 |
| `projectile/Fireball.kt` | `fireball`（基础火球：Trajectory + Hurt） |
| `projectile/TriggerFireball.kt` | `trigger_fireball`（+ `Trigger`） |
| `projectile/DelayedCastProjectile.kt` | `delayed_cast`（静止，死亡时沿施法方向释放 payload） |
| `projectile/DroneSwarm.kt` | `drone_swarm`（召唤 5 架无人机后立即死亡） |
| `projectile/Laser.kt` | `laser`（高速短命，delay/recharge 为负） |
| `projectile/TeleportProjectile.kt` | `teleport`（命中/消亡时传送施法者） |
| `projectile/immobile/SmallExplosion.kt` | `small_explosion`（死亡时半径 3 / 伤害 4） |
| `projectile/immobile/LargeExplosion.kt` | `large_explosion`（半径 6 / 伤害 8） |
| `projectile/immobile/GravityFieldProjectile.kt` | `gravity_field`（静止光环，200 tick） |
| `projectile/immobile/BlackHoleProjectile.kt` | `black_hole`（静止引力场） |
| `modifier/`（26 个，逐个见 §3） | 修饰器实现 |
| `effect/BaseEffect.kt` | 六钩子契约 + `phase` + 同类叠加辅助（`stackCount`/`stackScale`/`isStackPrimary`） |
| `effect/geometry/{Position,Velocity,Move,Bounce,Tracking,ConstantAcceleration}.kt` | 空间与运动（`Move` 是唯一的碰撞实现与唯一推进 `pos` 的地方） |
| `effect/lifecycle/{Trigger,DelayedTrigger,Hurt,Explosion,AreaFieldEffect,BlackHole,GravityField}.kt` | 时机与结算（`AreaFieldEffect` 是区域场基类：生物迟缓/拉拽 + **投射物施力钩子** + PD 粒子视觉钩子） |
| `effect/visual/{ProjectileBody,Trajectory,ParticleSwarm}.kt` | 粒子表现（particledrawing）：本体、拖尾，以及粒子群工具（静止 / 运动「本模组积分 + `track`」/ 锚定 三种模式） |

### 法杖、物品、实体

| 文件 | 内容 |
|---|---|
| `wand/Wand.kt` | 牌堆/手牌/弃牌堆、施法结算、法力与冷却、被动驱动、NBT 序列化（槽位保序 + 牌堆 + `magic_states` 运行期状态）、`saveRuntimeState` 钩子的调用方 |
| `wand/WandManager.kt` | `wandId → Wand` 实例表 + **持有者记录**（`markHeld`/`holderOf`/`lastHeldTick`）+ 每 100 tick 回收无人持有的内存实例 + 按需懒加载（`get` 会从存档重建）；存档条目永不删除 |
| `wand/WandSavedData.kt` | `SavedData`：`wandId → CompoundTag` 的持久化容器 |
| `item/WandItem.kt` | 法杖物品：长按施法、被动 tick、属性同步、容器内右键装卸、`getWand`/`getWandData`/`syncStats`/`forgetCastState` |
| `item/SpellPouchItem.kt` | 法术小包：16 格内容 + 选中槽，右键装入/取出（与法杖同一套交互），服务端补 `pouchId` |
| `item/TestDummyItem.kt` | 木桩物品：按盔甲架规则放置 |
| `item/component/WandID.kt` | `WandID` 数据组件值类型（`wand_id` 与 `pouch_id` 共用） |
| `entity/TestDummyEntity.kt` | 伤害测试木桩（不死、免击退、空手回收） |
| `entity/DamageNumber.kt` | 头顶飘字（`Display.TextDisplay`）+ `spawnDamageNumber()` |
| `entity/DroneEntity.kt` | 无人机：飞行导航、速度控制、分离、路径噪声、发射火球；含 `DroneRandomStrollGoal` |
| `entity/DroneAttackGoal.kt` | 目标搜索 + 战斗平移机动 |
| `entity/DroneFollowOwnerGoal.kt` | 非战斗时跟随 owner |
| `entity/DroneHeightGoal.kt` | 维持离地 2–8 格 |

### 网络、事件、工具

| 文件 | 内容 |
|---|---|
| `network/SyncWandStatsPayload.kt` | S→C：法杖数值（HUD / tooltip） |
| `network/SyncShieldPayload.kt` | S→C：护盾状态（HUD） |
| `network/SelectSpellSlotPayload.kt` | C→S：选中的法术槽位（法杖/小包共用，含 `send()`） |
| `network/ClientPayloadSink.kt` | 公共侧→客户端的可空回调；由 `PrimalSpellsClient` 安装，保证公共入口不引用客户端类 |
| `event/WandEventHandler.kt` | 公共侧：右键拦截（不与方块/实体交互但可施法）、登录补发同步、登出清理施法记录 |
| `util/HitResult.kt` | 命中结果（`HitType`、`pos`、`normal`、`entity`） |
| `util/Lore.kt` | 词条类型枚举（本地化 key）+ `LoreEntry` |
| `util/ParticleShapes.kt` | 球体采样：球面/球内均匀取点、随机切向（原版粒子撒点 + PD 粒子生成共用） |
| `util/PdParticles.kt` | `Wand.pdParticles(level)`：取/兜底创建 ParticleDrawing 粒子管理器 |

### 客户端 `client/`

| 文件 | 内容 |
|---|---|
| `PrimalSpellsClient.kt` | 客户端 `@Mod(dist=CLIENT)`：安装 `ClientPayloadSink`、注册 tooltip 工厂/渲染器/输入、登出清理缓存 |
| `hud/WandHudRenderer.kt` | 准星下法力/延迟/充能条（`@EventBusSubscriber(CLIENT)`，含 `clear()`） |
| `hud/ShieldHudRenderer.kt` | 屏幕顶端护盾条（`@EventBusSubscriber(CLIENT)`，含 `clear()`） |
| `tooltip/ClientTooltipHandler.kt` | 组装 tooltip：法杖槽位网格/Alt 属性、小包网格、选中槽法术词条 |
| `tooltip/WandSpellTooltip.kt` | 法杖 9 列槽位 tooltip 组件（+`ClientWandSpellTooltip`，选中槽高亮） |
| `tooltip/PouchTooltip.kt` | 小包 8 列槽位 tooltip 组件（+`ClientPouchTooltip`，选中槽高亮） |
| `tooltip/SpellSlotTracker.kt` | 客户端「id → 预览槽位」缓存（法杖与小包共用，登出清空） |
| `input/SpellSlotScrollHandler.kt` | 滚轮选槽（法杖与小包共用，纯客户端输入） |
| `input/ClientContainerDragHandler.kt` | Ctrl+左键拖拽交换（走原版 PICKUP 协议） |
| `render/DroneEntityModel.kt` + `DroneEntityRenderer.kt` | 无人机模型/渲染器 |

### Java mixin

| 文件 | 内容 |
|---|---|
| `src/main/java/work/nekow/primalspells/mixin/AbstractContainerScreenInvokerMixin.java` | `@Invoker("slotClicked")`：暴露 protected `slotClicked` 给拖拽交换 |
| `src/main/java/work/nekow/primalspells/mixin/AbstractContainerScreenMixin.java` | `@Inject` 到 `showTooltipWithItemInHand`，手持法杖时强制显示 tooltip |
| `resources/primalspells.mixins.json` | mixin 配置，`package` 固定为 `work.nekow.primalspells.mixin` |

---

## 3. 法术 id ↔ 类 ↔ 资源对照

> 资源三件套：`assets/primalspells/items/<id>.json`、`models/item/<id>.json`、`textures/item/<id>.png`；语言键 `item.primalspells.<id>` 与 `lore.primalspells.<id>.desc`。
> 注册顺序与 `magic/core/MagicManager.kt` 的 `init` 一致（共 36 项：10 投射物 + 26 修饰器）。调试法术 `speed_getter` 已随死代码清理移除。

| id | 类 | 类型 | 文件 |
|---|---|---|---|
| `fireball` | `Fireball` | 投射物 | `magic/projectile/Fireball.kt` |
| `trigger_fireball` | `TriggerFireball` | 投射物（触发） | `magic/projectile/TriggerFireball.kt` |
| `delayed_cast` | `DelayedCastProjectile` | 投射物（触发） | `magic/projectile/DelayedCastProjectile.kt` |
| `drone_swarm` | `DroneSwarm` | 投射物 | `magic/projectile/DroneSwarm.kt` |
| `damage_boost` | `DamageBoost` | 修饰器 | `magic/modifier/DamageBoost.kt` |
| `gravity` | `Gravity` | 修饰器（注入 `ConstantAcceleration`） | `magic/modifier/Gravity.kt` |
| `bounce` | `MoreBounce` | 修饰器 | `magic/modifier/MoreBounce.kt` |
| `double_cast` | `DoubleCast` | 修饰器（抽牌 +1） | `magic/modifier/DoubleCast.kt` |
| `speed_boost` | `SpeedBoost` | 修饰器 | `magic/modifier/SpeedBoost.kt` |
| `extended_lifetime` | `ExtendedLifetime` | 修饰器 | `magic/modifier/ExtendedLifetime.kt` |
| `laser` | `Laser` | 投射物 | `magic/projectile/Laser.kt` |
| `piercing` | `Piercing` | 修饰器 | `magic/modifier/Piercing.kt` |
| `bounce_explosion` | `BounceExplosion` | 修饰器 | `magic/modifier/BounceExplosion.kt` |
| `homing` | `Homing` | 修饰器（注入 `Tracking`） | `magic/modifier/Homing.kt` |
| `bounce_duplicate` | `BounceDuplicate` | 修饰器（生成子弹） | `magic/modifier/BounceDuplicate.kt` |
| `gradual_accelerate` | `GradualAccelerate` | 修饰器 | `magic/modifier/GradualAccelerate.kt` |
| `gradual_decelerate` | `GradualDecelerate` | 修饰器 | `magic/modifier/GradualDecelerate.kt` |
| `random_path` | `RandomPath` | 修饰器 | `magic/modifier/RandomPath.kt` |
| `reciprocate` | `Reciprocate` | 修饰器 | `magic/modifier/Reciprocate.kt` |
| `sine_wave` | `Serpentine` | 修饰器（正弦蛇形路径） | `magic/modifier/Serpentine.kt` |
| `mana_injection` | `ManaInjection` | 修饰器（返还法力） | `magic/modifier/ManaInjection.kt` |
| `diffusion` | `Diffusion` | 修饰器 | `magic/modifier/Diffusion.kt` |
| `implosion` | `Implosion` | 修饰器 | `magic/modifier/Implosion.kt` |
| `volatile` | `Volatile` | 修饰器 | `magic/modifier/Volatile.kt` |
| `cluster_bomb` | `ClusterBomb` | 修饰器（生成子弹） | `magic/modifier/ClusterBomb.kt` |
| `explosion_remove` | `ExplosionRemove` | 修饰器 | `magic/modifier/ExplosionRemove.kt` |
| `explosion_projectile` | `ExplosionProjectile` | 修饰器（生成子弹） | `magic/modifier/ExplosionProjectile.kt` |
| `small_explosion` | `SmallExplosion` | 投射物 | `magic/projectile/immobile/SmallExplosion.kt` |
| `large_explosion` | `LargeExplosion` | 投射物 | `magic/projectile/immobile/LargeExplosion.kt` |
| `gravity_field` | `GravityFieldProjectile` | 投射物 | `magic/projectile/immobile/GravityFieldProjectile.kt` |
| `black_hole` | `BlackHoleProjectile` | 投射物 | `magic/projectile/immobile/BlackHoleProjectile.kt` |
| `self_orbit` | `SelfOrbit` | 修饰器（`OVERRIDE` 定位） | `magic/modifier/SelfOrbit.kt` |
| `sphere_orbit` | `SphereOrbit` | 修饰器（`OVERRIDE` 定位） | `magic/modifier/SphereOrbit.kt` |
| `teleport` | `TeleportProjectile` | 投射物 | `magic/projectile/TeleportProjectile.kt` |
| `quantum_entanglement` | `QuantumEntanglement` | 修饰器（生成子弹） | `magic/modifier/QuantumEntanglement.kt` |
| `energy_shield` | `EnergyShield` | 修饰器（**被动**，`needInventory`；未完成） | `magic/modifier/EnergyShield.kt` |

**非法术物品**（`registry/ModItems.kt` 直接注册）：`wand`、`spell_pouch`、`test_dummy`。

---

## 4. 注册点速查（新增内容必看）

| 要注册的东西 | 写在哪 | 挂在哪 |
|---|---|---|
| 物品 / 数据组件 / 创造栏 | `registry/ModItems.kt` | `PrimalSpells.init` 的 `ModItems.ITEMS/COMPONENTS/TABS.register(bus)` |
| 实体 | `registry/ModEntities.kt`（属性在 `EntityAttributeCreationEvent`） | `ModEntities.ENTITY_TYPES.register(bus)` |
| 伤害类型 | `registry/ModDamageTypes.kt` + `resources/data/primalspells/damage_type/<name>.json` | `DAMAGE_TYPES.register(bus)` |
| 法术 | `magic/core/MagicManager.kt` 的 `init { register { X() } }` | 同时自动获得同名物品（`ModItems` 的 `init` 遍历注册表） |
| 网络包 | `network/<Name>Payload.kt` | `PrimalSpells` 的 `RegisterPayloadHandlersEvent`；S→C 的处理写进 `ClientPayloadSink` |
| 公共事件 | `event/` | `PrimalSpells.init` 的 `NeoForge.EVENT_BUS.register(...)` |
| 客户端事件 / 渲染 / tooltip | `client/` 对应子包 | `PrimalSpellsClient`（或 `@EventBusSubscriber(CLIENT)`） |
| 语言键 | `resources/assets/primalspells/lang/{zh_cn,en_us}.json` | — |
| 资源三件套 | `resources/assets/primalspells/{items,models/item,textures/item}/` | — |

---

## 5. 资源文件清单（`src/main/resources/`）

| 路径 | 内容 |
|---|---|
| `META-INF/neoforge.mods.toml` | modId/版本/作者/依赖（neoforge、particledrawing、minecraft）/mixin 配置声明；`${...}` 由 `build.gradle` 的 `ProcessResources` 插值 |
| `primalspells.mixins.json` | mixin 包名与两个客户端 mixin（`package` 字段与 Java 源码包名绑定） |
| `assets/primalspells/items/<id>.json` | 物品定义（1.21+ 数据驱动格式） |
| `assets/primalspells/models/item/<id>.json` | 物品模型 |
| `assets/primalspells/textures/{item,entity}/*.png` | 法术物品贴图与无人机实体贴图 |
| `assets/primalspells/lang/zh_cn.json`、`en_us.json` | 语言文件（两侧键集合一致） |
| `data/primalspells/damage_type/projectile.json` | 伤害类型数据（与 `registry/ModDamageTypes.kt` 对应） |
