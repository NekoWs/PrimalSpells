# 法术开发指南（spell-development）

> 目标：**照着本文就能新增/修改一个法术，并且知道每一步为什么必须做。**
> 前置阅读：[architecture.md](architecture.md)（对象模型与流程）、[conventions.md](conventions.md)（包结构与命名）。
> 相关：文件位置见 [code-map.md](code-map.md)，已知缺陷见 [issues/README.md](issues/README.md)。

---

## 0. 先想清楚：你要加的是哪一类？

| 类型 | 特征 | 放哪 | 基类 |
|---|---|---|---|
| **投射物** | 会飞/会停、有位置与速度、能命中 | `magic/projectile/`（静止定点类放 `immobile/`） | `Projectile` |
| **修饰器** | 不独立存在，施法时改造同手牌里的投射物 | `magic/modifier/` | `Modifier`（原 `Revise`） |
| **特效** | 挂在投射物上的可组合行为（运动/结算/表现） | `magic/effect/{geometry,lifecycle,visual}/` | `BaseEffect` |

一个完整的玩法通常由「1 个投射物 + N 个修饰器」组合而成：玩家手里抽到的牌决定这次法术长什么样。

---

## 1. 新增投射物（最常用）

### 步骤

1. 新建 `magic/projectile/<Name>.kt`（静止定点类放 `magic/projectile/immobile/`）。
2. 在 `init { }` 里设置参数与默认 effect（参考 `projectile/Fireball.kt`）。
3. 在 `magic/core/MagicManager.kt` 的 `init { }` 里 `register { <Name>() }`。
4. 补资源三件套：`assets/primalspells/items/<id>.json`、`models/item/<id>.json`、`textures/item/<id>.png`。
5. 补语言键：`item.primalspells.<id>`、`lore.primalspells.<id>.desc`（中英各一份）。

### 骨架（对照 `Fireball.kt` 的真实结构）

```kotlin
package work.nekow.primalspells.magic.projectile

import work.nekow.particledrawing.api.Color
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.visual.Trajectory

class IceShard : Projectile() {
    override val id = "ice_shard"                       // 持久化键：一旦发布不要改
    override val bodyColor = Color.of(0.6f, 0.9f, 1f)   // 本体粒子颜色

    init {
        mana = 4.0            // 法力消耗（负数 = 返还）
        delay = 1             // 施法后附加的延迟（tick）
        recharge = 2          // 附加充能时间（tick）
        maxAge = 120          // 寿命；到点 alive=false
        status.damage = 2.0   // 命中伤害（由 Hurt 读取；Hurt 无参时回退到它）
        hitRadius = 0.2       // >0 才做实体碰撞检测
        speed = 1.2f          // 初速倍率（单位：格/tick，乘在单位视线向量上）
        effects += Trajectory(bodyColor)   // 视觉：拖尾
        effects += Hurt()                  // 结算：命中造成伤害
    }
}
```

> 想额外附加效果时，**不要**手动加 `Position`/`Velocity`/`Move`/`ProjectileBody`——`Projectile.spell()` 已自动装配，重复加会让投射物按两倍速度前进。

### `Projectile` 可调字段速查

| 字段 | 默认 | 作用 |
|---|---|---|
| `mana` / `delay` / `recharge` | 0 | 代价；`Wand.castHand` 结算 |
| `cast` | `0`（`Projectile` 覆写） | **抽牌预算**：投射物是 0 → 抽到即停止抽牌 |
| `maxAge` | 0 | 寿命（tick） |
| `maxBounces` | 0 | 可弹跳次数（`Bounce` 效果累加） |
| `maxHitTargets` | 1 | 命中实体几次后死亡；`-1` = 无限穿透 |
| `bounceScaler` | `(0.9, 0.9, 0.9)` | 每次弹跳的速度缩放（当前无 modifier 修改它） |
| `dragCoeff` | 0.02 | 每 tick 阻尼系数：`v *= 1/(1 + dragCoeff*|v|)` |
| `speed` | 1 | 初速倍率（格/tick） |
| `hitRadius` | 0.0 | 实体碰撞半径，**0 = 不做实体检测** |
| `throughBlocks` | false | true = 穿墙但不消失（会写 `status.atBlock`） |
| `selfDamage` | false | 是否允许命中施法者 |
| `bodyColor` | `Color.WHITE` | `ProjectileBody` 的颜色 |
| `status.damage` | 0.0 | 命中伤害 |
| `status.explosionRadius` / `explosionDamage` | 2.0f / 5.0 | `Explosion` 效果使用（只做伤害与击退，不破坏方块） |

### 可覆写的钩子

| 钩子 | 时机 | 典型用途 |
|---|---|---|
| `init { }` | 实例创建（每次 `MagicManager.create`） | 设参数、挂默认 effect |
| `onSpell()` | 施法、`onActive` 之后 | 依 `status` 初始化爆炸参数（`SmallExplosion`）、生成实体（`DroneSwarm`） |
| `onHitEntity(entity)` / `onHitBlock(pos)` / `onHit(result)` | 命中（在 effect 的同名钩子之后调用） | 投射物专属命中行为 |
| `onDie()` | 死亡（`alive=false` 后的下一 tick） | 清理、结算 |
| `onInventoryTick()` | 仅 `needInventory = true` 且法杖在主/副手时，每 tick | 被动法术（见 §5） |
| `onPassiveIdle()` | 仅 `needInventory = true` 且法杖**这一 tick 没被持有**时，每 tick | 放下后的延迟清理/淡出（见 §5） |
| `initLore()` | 物品 tooltip 生成时 | 追加词条；**记得先 `super.initLore()`** |
| `clone()` | 复制到牌堆/子弹时 | 默认 `MagicManager.create(id)!!`（见 §7 坑 2） |

---

## 2. 新增修饰器（Modifier）

修饰器只在**施法瞬间**起作用：`Wand.castHand` 把同手牌的 `Modifier` 摘出、扣法力、累加 `delay/recharge`，然后把它们的 effect **克隆后**注入到这次施法的每个投射物上。

### 写法 A：纯注入 effect（推荐，能复用）

```kotlin
package work.nekow.primalspells.magic.modifier

import work.nekow.primalspells.magic.core.Modifier
import work.nekow.primalspells.magic.effect.geometry.Tracking
import work.nekow.primalspells.utils.Lore

class Homing : Modifier() {
    override val id = "homing"

    init {
        mana = 4.0
        delay = 1
        effects += Tracking(range = 5.0f, strength = 0.1f, delayTicks = 20)
        lore(Lore.SPEED, "%.1f".format(0.1f))
    }
}
```

### 写法 B：匿名 effect 直接改状态

适合「改参数/加音效/生成子弹」这类一次性逻辑：

```kotlin
class Volatile : Modifier() {
    override val id = "volatile"
    init {
        mana = 2.0
        delay = 1
        effects += object : BaseEffect() {
            override fun onActive() {
                status.explosionDamage *= 2
            }
        }
    }
}
```

### 修饰器注入点速查

| 想改的东西 | 注入方式 |
|---|---|
| 速度 | `status.velocity`（`onActive` 时改，`onTick` 时改也可以） |
| 伤害 | `status.damage` |
| 寿命 / 弹跳 / 穿透 | `projectile.maxAge` / `maxBounces` / `maxHitTargets` |
| 爆炸参数 | `status.explosionRadius` / `explosionDamage` |
| 抽牌预算 | 覆写 `cast` 字段（如 `DoubleCast.cast = 2`） |
| 别人的效果 | `effects += 具名 Effect(...)` |

### 多实例叠加的既有约定（√N 样板）

多个同类修饰器叠加时，主流做法是**只有第一个实例执行，效果按 √N 放大**：

```kotlin
override fun onTick() {
    val duplicates = effects.count { it::class == this::class }   // 粗略示意
    val scale = sqrt(duplicates.toFloat())
    // …仅当自己是首个实例时改写 status
}
```

真实实现见 `modifier/{Tracking,Serpentine,GradualAccelerate,GradualDecelerate,Reciprocate}.kt`，统一使用 `BaseEffect` 提供的 `stackCount`/`stackScale`/`isStackPrimary`。

> `Modifier` 自身**不会**进入 `MagicManager.projectiles`，不参与 tick；它的一切行为都必须通过注入的 effect 或对 `projectile`/`status` 的字段操作完成。

---

## 3. 新增特效（BaseEffect）

六个钩子：`onActive / onTick / onHitEntity / onHitBlock / onHit(result) / onDie`。

| 想要的行为 | 用哪个钩子 |
|---|---|
| 出生时初始化 | `onActive` |
| 每 tick 运动/追踪 | `onTick` |
| 命中实体（拿到 `Entity`） | `onHitEntity` |
| 命中方块（拿到坐标） | `onHitBlock` |
| 命中通用处理（拿到 `HitResult`） | `onHit` |
| 死亡清理 | `onDie` |

### 三条铁律

1. **执行顺序由 `EffectPhase` 决定，不要依赖插入顺序**：改速度留在默认的 `MODIFIER`；
   要在位置积分后**绝对定位**（轨道类）请声明 `OVERRIDE`；要读本 tick 的最终位置发粒子请声明 `VISUAL`。
   详见 [architecture.md](architecture.md#27-effectphase效果执行顺序magiccoreeffectphasekt)。
2. **写 `status` 只允许可交换运算**（累加/累乘/乘零）：修饰器之间没有先后顺序（见 `magic/core/Modifier.kt` 的 KDoc）；
   禁止 `coerceAtLeast` 这类钳制与「重设绝对值」的写法。
3. **不要在多实例间共享可变字段**：`BaseEffect.clone()` 是浅拷贝，`private var` 字段会被同一枚子弹的多个克隆共享。

### 粒子表现：一律走 ParticleDrawing（PD）

按「粒子的位置由谁决定」选 API（每 tick 带宽对照表见 [design/effects-api.md](design/effects-api.md) §0.1）：

- **位置固定在实体身上** → `handle.attachTo(entityId, 世界空间偏移)`：跟随实体、**0 包/tick**、天然刚性
  （偏移要随朝向旋转用 `attachToLocal`）；
- **匀速直线** → 生成时一次 `setVelocity(v)`，之后交给 PD 逐 tick 积分（**0 包/tick**）；
- **逐 tick 变化的轨迹**（被吸引、绕转） → 本模组算好位置，用 `ParticleBatch.trackAll(positions)`
  一次包下发（**1 包/tick，与粒子数无关**）；单体可用 `handle.track(pos)`；
- **恒定 / 分段恒定的力** → `handle.applyForce(accel, ticks)`（两端同规则积分，只在施力时发一次）；
  ⚠️ 随距离变化的力表达不了，只能自算位置（见 `BlackHole` 的说明）；
- **成员簿记一律用 PD 的 `ParticleBatch`**（`add`/`ensureSize`/`forEach`/`removeIf`/`clear`）——
  别再自己存句柄表或寿命表（原 `ParticleSwarm` 已在第 10 轮删除，见 [issues/resolved.md](issues/resolved.md) 第 10 轮）；
- **生成**一颗粒子统一用 `ParticleManager.spawnParticle(pos, color, scale, lifetime, velocity?)`；
- **管理器**统一用 `wand.pdParticles(level)` 获取（缺失/跨维度时兜底创建，未来的生物施法者也有粒子）；
- 采样工具在 `util/ParticleShapes.kt`（`fibonacciSphere` 均匀球壳取点、随机单位方向/球内取点、随机切向）；
- 参考实现：`BlackHole.kt`（`ensureSize` 铺球壳；切向初速 → 自己积分 → `trackAll` 批量写回 → 抵达中心销毁）、
  `GravityField.kt`（球壳 `ensureSize`；下落粒子一次 `setVelocity` + `removeIf` 判定回收）、
  `EnergyShield.kt`（`attachTo` 刚性球壳 + `ensureSize` 补齐、不做周期重建）。
- ⚠️ 两条铁律：**不要自己写粒子系统**；**不要按列表索引给粒子配对偏移**（有粒子被拒/重建就会错位）——
  PD 缺能力时停下并提需求（[conventions.md](conventions.md) §9）。

### 骨架

```kotlin
package work.nekow.primalspells.magic.effect.lifecycle

import work.nekow.primalspells.magic.effect.BaseEffect

/**
 * 减速光环：每 [cooldown] tick 对 [radius] 内的实体施加迟缓。
 * 服务端权威（依赖 [caster].level() 为 ServerLevel）。
 */
class SlowAura(
    private val radius: Double = 5.0,
    private val cooldown: Int = 5,
) : BaseEffect() {
    private var timer = 0

    override fun onTick() {
        if (timer++ % cooldown != 0) return
        val level = caster.level() as? net.minecraft.server.level.ServerLevel ?: return
        // …施加效果（参考 effect/lifecycle/GravityField.kt）
    }
}
```

---

## 4. 触发法术（TriggerSpell）

触发法术会在施法时**额外抽牌**装填 payload，并在命中时释放：

```kotlin
package work.nekow.primalspells.magic.projectile

import work.nekow.primalspells.magic.core.Magic
import work.nekow.primalspells.magic.core.TriggerSpell
import work.nekow.primalspells.magic.effect.lifecycle.Hurt
import work.nekow.primalspells.magic.effect.lifecycle.Trigger

class IceTrigger : Projectile(), TriggerSpell {
    override val id = "ice_trigger"
    override var triggerCast = 1                       // payload 的抽牌预算
    override var payload = arrayListOf<Magic>()         // 由 Wand.loadPayload 填充

    init {
        mana = 5.0
        maxAge = 200
        hitRadius = 0.2
        status.damage = 1.0
        effects += Hurt()
        effects += Trigger()                            // 命中时释放 payload
    }
}
```

要点：

- `triggerCast` 是**额外抽牌预算**，被抽到的牌同样按各自的 `cast` 增减预算。
- `Trigger.onHit` 会用 `HitResult.normal` 作为 payload 投射物的初速方向；命中实体时该方向是**反弹方向**。
- 释放前，payload 里的 `Modifier` 会被摘出并把效果转发给 payload 投射物（`Trigger` 与 `DelayedTrigger` 都这么做）。
- **已知缺陷**：`Trigger.onHit` 不清空 payload，配合 `maxHitTargets > 1`/`Piercing` 会重复释放；且不会给 payload 投射物设置 `speed` 之外的状态（见 [issues/README.md](issues/README.md)）。

---

## 5. 被动法术（`needInventory`）

```kotlin
class MyPassive : Modifier() {
    override val id = "my_passive"
    override var needInventory = true          // 关键：法杖在主/副手时每 tick 驱动

    private var idleTicks = 0

    override fun onInventoryTick() {
        // caster / wand 已由 Wand.tick(caster) 注入
        idleTicks = 0                            // 被重新持有：复位放下倒计时
    }

    override fun onPassiveIdle() {
        // 法杖这一 tick 没有被任何实体持有/驱动（每 tick 调用一次）
        // 适合「放下后延迟清理」：先保留几 tick 再销毁，为消失动画留窗口
        if (++idleTicks >= 4) { /* 清理表现/资源 */ }
    }
}
```

约束：

- 只有 `Wand.loadedMagics`（`innate + magics`）中的法术会被驱动；`Wand.tick(caster)` 内部按 `gameTime` 去重，保证每 tick 只驱动一次。
- `onPassiveIdle()` 由 `Wand.tick(level = …)` 的**闲置分支**逐 tick 驱动（`WandManager.tick(level)` → 所有已加载法杖），
  在「法杖没被持有」时持续触发；**不要**假设它只触发一次，清理逻辑要幂等。
- 法力/冷却维护由 `WandManager.tick()`（`caster == null`）负责，被动分支**不要**再做这些事。
- 运行期状态要持久化就覆写 `saveRuntimeState`/`loadRuntimeState`（见 `EnergyShield` 的耐久），
  不要再往 `Wand.saveTag()` 里塞专用字段。

---

## 6. 资源与本地化

| 资源 | 路径 | 备注 |
|---|---|---|
| 物品定义 | `assets/primalspells/items/<id>.json` | 1.21+ 数据驱动格式，内容参考任一现有法术 |
| 物品模型 | `assets/primalspells/models/item/<id>.json` | 通常 `{"parent": "minecraft:item/generated", "textures": {"layer0": "primalspells:item/<id>"}}` |
| 物品贴图 | `assets/primalspells/textures/item/<id>.png` | 16×16 |
| 中文名 | `lang/zh_cn.json` → `item.primalspells.<id>` | 必填 |
| 英文名 | `lang/en_us.json` → `item.primalspells.<id>` | 必填 |
| 描述行 | `lang/{zh_cn,en_us}.json` → `lore.primalspells.<id>.desc` | **必填**：`Magic.initLore()` 无条件加 `Lore.DESCRIPTION`，缺失就会显示原始 key |
| 参数行 | `lore.primalspells.{mana,delay,recharge,damage,max_age,speed,durability,regen,break_pause,bounce_cost}` | 由 `lore(Lore.X, args…)` 触发，通常已有通用键 |

命名规则：**资源名必须与 `id` 完全一致**（`ModItems` 用 id 作为注册名，`spellItem`/`spellIdOf` 靠它双向查表）。

---

## 7. 注册与自检清单

新增内容后逐条确认：

- [ ] 法术类已 `MagicManager.register { X() }`（`magic/core/MagicManager.kt`）——否则 `MagicManager.create` 返回 null，多处 `!!` 直接 NPE
- [ ] `id` 全局唯一（`register` 会互相覆盖，且不会报错）
- [ ] 资源三件套 + 两条语言键齐全（对照 [code-map.md](code-map.md#3-法术-id--类--资源对照)）
- [ ] `./gradlew build` 通过
- [ ] `./gradlew runClient` 实测：创造栏能找到物品、装进法杖、长按右键能打出来
- [ ] 若是修饰器：确认它对「投射物 + 修饰器」的组合生效，且不干扰其它修饰器

---

## 8. 常见坑（Top 12）

1. **改 `id` 会破坏存档**：法杖存档与物品组件里存的是 id 字符串；改名后旧法杖会找不到法术（`create` 返回 null）。
2. **`Magic.clone()` 不是拷贝**：它是 `MagicManager.create(id)!!`——依赖「继承父投射物状态」的逻辑会拿到一份**全新默认实例**（`ClusterBomb`/`ExplosionProjectile` 的注释就与实现相反）。
3. **不要重复挂内建 effect**：`Projectile.spell()` 已加 `Position/Velocity/Move/ProjectileBody`。
4. **`hitRadius = 0` 不做实体检测**：不写它就永远不会命中实体（但会撞方块）。
5. **`speed` 是倍率不是速度上限**：`velocity = 视线单位向量 * speed`；`Move` 之后每 tick 只受阻尼影响。
6. **`mana` 为负是返还**：`Wand.consumeMana` 靠符号实现，别写 `mana = 0` 又指望返还。
7. **`cast` 是抽牌预算**：投射物 `cast = 0` 会立刻结束抽牌；想让一张牌「多抽一张」就设 `cast = 2`。
8. **效果顺序由 `EffectPhase` 决定**（不再是插入顺序的逆序/正序）：改速度留在默认阶段，写位置用 `OVERRIDE`，读最终位置用 `VISUAL`。见 [architecture.md](architecture.md#27-effectphase效果执行顺序magiccoreeffectphasekt)。
9. **`BaseEffect.clone()` 是浅拷贝**：不要用可变的 `private var` 字段保存跨实例状态。
10. **`lore.primalspells.<id>.desc` 缺失会在游戏里显示原始 key**：新增法术时中英两份都要补。
11. **服务端/客户端**：法术逻辑全部在服务端；effect 里不要写客户端代码，`caster.level()` 用 `as? ServerLevel` 判空。
12. **不要把调试内容混进正式注册表**：调试法术 `SpeedGetter`（`speed_getter`，会在屏幕上打印速度）已在死代码清理中移除；需要临时调试就用本地代码 + 不注册的实例。
