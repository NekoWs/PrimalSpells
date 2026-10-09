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

> 想额外附加效果时，**不要**手动加 `Position`/`Velocity`/`Move`——`Projectile.spell()` 已自动装配，重复加会让投射物按两倍速度前进。

### `Projectile` 可调字段速查

| 字段 | 默认 | 作用 |
|---|---|---|
| `mana` / `delay` / `recharge` | 0 | 代价；`Wand.castHand` 结算 |
| `cast` | `0`（`Projectile` 覆写） | **抽牌预算**：投射物是 0 → 抽到即停止抽牌 |
| `maxAge` | 0 | 寿命（tick） |
| `maxBounces` | 0 | 可弹跳次数（`Bounce` 效果累加） |
| `maxHitTargets` | 1 | 命中实体几次后死亡；`-1` = 穿刺且穿盾；`0` = 连锁载体不按命中次数死亡 |
| `bounceScaler` | `(0.9, 0.9, 0.9)` | 每次弹跳的速度缩放（当前无 modifier 修改它） |
| `dragCoeff` | 0.02 | 每 tick 阻尼系数：`v *= 1/(1 + dragCoeff*|v|)` |
| `speed` | 1 | 初速倍率（格/tick） |
| `hitRadius` | 0.0 | 实体碰撞半径，**0 = 不做实体检测** |
| `throughBlocks` | false | true = 穿墙但不消失（会写 `status.atBlock`） |
| `selfDamage` | 普通弹false；爆炸/水晶/黑洞true | 是否允许命中施法者，穿刺也会开启；碰撞与直接伤害实时读取 |
| `bodyColor` | `Color.WHITE` | 拖尾与头部的主色（传给 `Trajectory`） |
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
| `onUnloaded()` | 法术被**从法杖上取下**（或槽位被替换）时一次；法杖内存实例被回收时也调 | 清理「只该在装载期间存在」的资源（粒子组等）—— 之后本实例不会再有任何 tick |
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
- **恒定 / 分段恒定的力** → 生成时一次施力：`ParticleManager.spawnParticle(..., acceleration = Vec3(0, -0.01, 0))`
  （内部一次 `applyForce(accel, ticks = -1)`），之后两端逐 tick 同为 `v += a; pos += v` —— **0 逐 tick 包**。
  `GravityField` 的下坠粒子在用（初速 0 + 恒定向下加速度 = 「越落越快」，雪花式的匀速下落是它的反面）；
  ⚠️ **随距离变化**的力表达不了，只能每 tick 重算速度（见 `BlackHole` 的说明）；
- **成员簿记一律用 PD 的 `ParticleBatch`**（`add`/`ensureSize`/`forEach`/`removeIf`/`clear`）——
  别再自己存句柄表或寿命表（原 `ParticleSwarm` 已在第 10 轮删除，见 [issues/resolved.md](issues/resolved.md) 第 10 轮）；
- **生成**一颗粒子统一用 `ParticleManager.spawnParticle(pos, color, scale, lifetime, velocity?)`；
- **管理器**统一用 `wand.pdParticles(level)` 获取（缺失/跨维度时兜底创建，未来的生物施法者也有粒子）；
- **编排动画（淡入淡出 / 自转 / 位移）** → `ParticleGroup`：`fadeIn`/`fadeOut(removeAfter = true)`、
  `rotate`/`spin`/`move`，都在**客户端本地求值（0 包/tick）**。三条约束：
  ① **要错开时间的"阶梯"只能用 `fadeOut` + `delay`，不能用 `fadeIn`**：`fadeOut` 在指令生效前默认「还没开始淡出」
     （不影响 alpha），而 `fadeIn` 默认值相反（1）——用它做阶梯会先闪一下（`EnergyShield` 的逐块消散在用）；
  ② **铺完成员后，第一条录进去的指令不能是 `move`** —— `move` 会改组的 `pivot` 字段，而客户端 arm 时
     用它反算每颗粒子的相对偏移，先 move 会让整体偏移错一个位移（见 [issues/pd-api-requests.md](issues/pd-api-requests.md)）；
  ③ 「整壳从中心胀开 / 缩回中心」这类**径向**动画当前**不能用 `scale`**（PD 只缩视觉尺寸、不缩到轴心的距离，同上），
     两条已验证的替代写法：**逐粒子缓动** `handle.move(target, ticks, easing)`（黑洞**进场**：整壳从球心长到球面；
     注意服务端会立即把权威位置设成目标，只适合位置无人读取的纯视觉粒子）、
     **同位置重新生成 + 向心初速 + 寿命兜底**（黑洞**退场**：永存粒子没有改寿命的入口，不重生成就会飞过中心留在世界里）；
- 采样/几何工具：`util/ParticleShapes.kt`（`fibonacciSphere` 均匀球壳取点、随机单位方向/球内取点、随机切向）、
  `util/Polyhedra.kt`（截角二十面体 = 五/六边形球体的面板与边界点，护盾在用）；
- 参考实现：`BlackHole.kt`（球壳 `ParticleBatch` + 逐粒子缓动进场 + 退场整壳塌缩；被吸入粒子切向初速 +
  每 tick `setVelocityAll` 批量设速 + 抵达中心 `removeIf`）、
  `GravityField.kt`（范围球壳 `ensureSize`；下坠粒子**一次施力**；波环 `move`+`EASE_IN` 加速下压；
  地面井环 `Draw.circle` + `spin`；回收只读 PD 权威位置）、
  `EnergyShield.kt`（32 个面板组：`followEntity` 跟随 + `spin` 自转 + **分 tick 逐块构建** +
  反向逐块 `fadeOut` 消散 —— 球体位置固定不动）。
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

    override fun onUnloaded() {
        // 被从法杖上取下：本实例之后**不会再有任何 tick**，
        // 只该在装载期间存在的资源必须在这里销毁（否则粒子会永远留在世界里）
        clearVisual()
    }
}
```

约束：

- 只有 `Wand.loadedMagics`（`innate + magics`）中的法术会被驱动；`Wand.tick(caster)` 内部按 `gameTime` 去重，保证每 tick 只驱动一次。
- `onPassiveIdle()` 由 `Wand.tick(level = …)` 的**闲置分支**逐 tick 驱动（`WandManager.tick(level)` → 所有已加载法杖），
  在「法杖没被持有」时持续触发；**不要**假设它只触发一次，清理逻辑要幂等。
- 法力/冷却维护由 `WandManager.tick()`（`caster == null`）负责，被动分支**不要**再做这些事。
- **取下即静默**：法术被取下后不再进入 `loadedMagics`，因此 [onInventoryTick]/[onPassiveIdle] 都不会再触发 ——
  需要收尾的东西必须走 `onUnloaded()`（`Wand.load()`/`Wand.unload()` 会调），不能指望「下一次 tick 自己发现」。
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
3. **不要重复挂内建 effect**：`Projectile.spell()` 已加 `Position/Velocity/Move`。
4. **`hitRadius = 0` 不做实体检测**：不写它就永远不会命中实体（但会撞方块）。
5. **`speed` 是倍率不是速度上限**：`velocity = 视线单位向量 * speed`；`Move` 之后每 tick 只受阻尼影响。
6. **`mana` 为负是返还**：`Wand.consumeMana` 靠符号实现，别写 `mana = 0` 又指望返还。
7. **`cast` 是抽牌预算**：投射物 `cast = 0` 会立刻结束抽牌；想让一张牌「多抽一张」就设 `cast = 2`。
8. **效果顺序由 `EffectPhase` 决定**（不再是插入顺序的逆序/正序）：改速度留在默认阶段，写位置用 `OVERRIDE`，读最终位置用 `VISUAL`。见 [architecture.md](architecture.md#27-effectphase效果执行顺序magiccoreeffectphasekt)。
9. **`BaseEffect.clone()` 是浅拷贝**：不要用可变的 `private var` 字段保存跨实例状态。
10. **`lore.primalspells.<id>.desc` 缺失会在游戏里显示原始 key**：新增法术时中英两份都要补。
11. **服务端/客户端**：法术逻辑全部在服务端；effect 里不要写客户端代码，`caster.level()` 用 `as? ServerLevel` 判空。
12. **不要把调试内容混进正式注册表**：调试法术 `SpeedGetter`（`speed_getter`，会在屏幕上打印速度）已在死代码清理中移除；需要临时调试就用本地代码 + 不注册的实例。


## 第 52 轮接入补充

新增短命射线/连锁直接伤害时，声明 `lifecycle/DirectDamage.damageStrength`，通过
`SpellDamage.hit` 接入护盾和冰冻；不要复制 Hurt 的伤害代码，也不要把无伤害束源的碰撞半径改大来接盾。
束源可以 `hitRadius=0`，命中时以实际线段临时提供 status.pos/velocity 并在 finally 恢复。
需要粘附/命中后停止积分时设 `motionEnabled=false`；Move 的子步与同一检查点候选循环都会立即停止。
静止法术与触发荷载的方向读取 `launchDirection`，不要依赖被自身速度乘成零的 status.velocity。
`Magic.initLore` 已添加描述行，新法术无额外专用数值时不用重写 initLore，避免描述重复。
七张卡与测试装配见 [design/spell-expansion.md](design/spell-expansion.md)。


### 第54轮组合装配

新增投射物应使用基础构造参数，不在onSpell重设已被修饰的伤害/爆炸状态。
修饰装配统一走SpellAssembly.apply；生成后代走inherit后再spell，禁止共享effect/复制已结算状态。
场/束范围读areaScale、寿命读maxAge；直接伤害走SpellDamage，爆炸传shot上下文以继承冻结。
当前跨投射物规则和实机矩阵见 [design/spell-quality.md](design/spell-quality.md)。


## 创造标签排序（第62轮）

工具/法杖之后按移动投射物→静态投射物→修正排列；同类保持MagicManager.registry注册顺序。
静态投射物以Projectile初始speed==0识别（仍能受力移动的区域场也属于静态施放）；Modifier最后。
不要用ModItems.MAGICS的HashMap迭代顺序展示法术，否则新增条目会让已有分类散乱。

第64轮新增投射物卡特性`resetsCastDelay`：只供链锯式实际施放的投射物在牌序当前位置清除此前施放延迟，普通Modifier不能用它改变可交换组合规则。触发载体只累加荷载数值，不继承该标记。自然寿命结算用onExpire，像水晶定时爆炸、延迟载体的释放视觉也接该钩子；onDie只承担收尾或明确的死亡结算。

第65轮自伤/击退：Explosion/CrystalReaction及黑洞默认自伤，穿刺配方自动开启，读取Projectile.canHarm而非单独排除caster；Move需先离开施法者碰撞箱才允许返弹自伤。新直接伤害走SpellDamage.hit，新爆炸走Explosion.customExplode，二者共用SpellImpact.hurt处理真实受伤及推力；不要直接hurtServer后另加固定击退。暗核等吸引伤害用knockback=false避免反向推离。无伤害接受则无击退，原版击退由专用伤害tag禁用。

第66轮：新伤害渠道在`magic/core/SpellDamageType.kt`添加枚举后，补ModDamageTypes通用注册对应数据包、no_knockback标签、中英名称与death.attack三变体；需要原版抗性的再加入原版伤害标签。直接命中设置Projectile.damageType，爆炸独立走EXPLOSION。FIRE目前是可用渠道，不自动点燃。质量通过Projectile.mass或实体NeoForgeData中的`primalspells:mass`配置；字段必须正且有限，无效数据安全回退。复制型法术仍按原法术模板和继承修饰配方重建，冰冻转换随配方重放；触发荷载保留自身法术类型。

## 第67轮：蓄能投射物与后坐力

新法术可配置Projectile.recoil；后坐力修正分别积累recoilBonus/recoilMultiplier，不在Modifier里直接push施法者。实际Wand施放统一queueCastRecoil；后代spell不能重复排队。需要延时射出的投射物使用awaitingRelease，真正释放后再releaseCastRecoil；不把法杖冷却和发射等待混为同一个计时器。蓄能动画持续跟随杖首，释放棱片在最后杖首坐标收尾，不能跟随首刻高速Move跳到光束前端。

concentrated_light、recoil_damper、recoil_boost已按注册→物品资源→中英语言→图标生成器→真实录制完整接入，18投射物+28修正=46张卡。汇聚之光既有一次反弹仍由Move/Projectile.hitBlock管理，可与弹跳、穿刺、速度、伤害、延寿等原配方联动。

第68轮：需要固定部署的蓄能法术只用出生时的launchDirection与status.pos，不能在蓄能tick重新取玩家lookAngle/杖首。护盾拦截属于整次命中语义：新直接/爆炸伤害应遵守Projectile.shieldedImpactTargets，禁止对刚开破口的同次已拦截目标再次结算；新的一枚投射物不共享该集合。护盾只对主卡记账，增加卡数同时影响抗性和滚动预算，不能再引入固定全局窗口上限。


## 第70轮：轻反冲与护盾拦截次序

公共Projectile.hitEntity返回true表示护盾拦截，不是实体命中；自定义碰撞/跃迁必须立即放弃旧路径，不能继续用入射位置/速度覆盖反射结果，也不能消耗命中次数或触发荷载。普通命中返回false，按原有回调和死亡规则执行。

蓄能法术设置recoilOnCast=true可在付费施法时反冲；默认false保持等待释放。子弹不调用queueCastRecoil，不重复推动施法者。


## 第71轮：球壳接点与长按移动

普通有hitRadius的运动投射物，护盾球壳碰撞由Move的真实子步负责；不要再在实体tick用pos-velocity重建路径。特殊射线/跃迁若直接调用命中入口，仍需提供真实来向；visual读取shieldReflectionPoint可用精确球壳接点连接束，而非安全推出位置。
