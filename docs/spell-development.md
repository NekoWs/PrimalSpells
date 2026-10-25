# 法术开发

先读 [架构第8节](architecture.md#8-关键不变量与陷阱)、[约定](conventions.md) 和对应设计。内容注册与资源ID见 [代码导航](code-map.md)。

## 1. 选择类型

| 内容 | 基类/目录 |
|---|---|
| 飞行法术 | Projectile，magic/projectile |
| 静态场或瞬时法术 | Projectile，magic/projectile/immobile |
| 参数/行为修正 | Modifier，magic/modifier |
| 复制单卡/引用/刷新/抽牌控制 | DrawAction，magic/action |
| 非投射物主动法术 | OtherSpell，magic/action；直接与荷载释放入口执行 |
| 投射物行为 | BaseEffect，按geometry/lifecycle/visual归位 |
| 手持被动 | 当前使用needInventory=true的Modifier；需处理闲置与卸载 |

## 2. 最小投射物

```kotlin
class ExampleBolt : Projectile() {
    override val id = "example_bolt"
    override val bodyColor = Color.ORANGE
    init {
        mana = 3.0
        delay = 1
        maxAge = 200
        speed = 0.5f
        status.damage = 5.0
        hitRadius = 0.2
        effects += Hurt()
        effects += Trajectory(bodyColor)
    }
}
```

在registry/ModSpells集中注册工厂，由通用SpellRegistry创建独立实例，不把未注册ID当临时克隆对象。出生位置/方向由法杖赋值，Projectile.spell统一加Position/Velocity/Move，不在示例重复积分。speed是模板系数，发射再乘LAUNCH_SPEED_SCALE=8；静止为0。伤害/MP/时间规则见 [战斗规则](design/combat-pricing.md)，全部游戏时长用完整刻。

DirectDamage声明单次直接强度，实际命中走SpellDamage/SpellImpact；不要绕过护盾、事件、类型、冻结或击退入口。持续攻击同目标每刻最多一次使用damageOncePerTick。

Move对同一目标首次接触立即命中，后续每1/16刻接触结算；不要在空间子步内额外调用hitEntity或按子步数量计算伤害。更快穿过目标会减少次数，慢速停留会增加；普通穿刺完整刻内持续接触最多16次回调。damageOncePerTick使同目标实体命中回调（含附加爆炸等修正）及直接伤害均每刻最多一次；后者另在SpellDamage保护，不仅依赖Move的派发次数。集合由Projectile.tick重置，特殊逐刻束/场也应显式开启。不同目标和独立复制体各自计数，墙反弹事件按实际接触保留。直接手动调用hitEntity的特殊法术需自行声明频率，不能据此绕过普通飞行的接触时钟。

## 3. 修正与阶段

世界位置（Projectile.position、MagicStatus.pos、HitResult.pos及onHitBlock参数）使用Vector3d；从原版Vec3用toPosition转换，传回原版/PD用toVec3。速度与方向仍使用Vector3f，必须先用double世界坐标做差，再转换局部向量；复制/触发荷载不得中途降低接点精度。Move统一完成分段扫掠与本刻余时，效果不要另写端点碰撞或反弹后手动截断移动。

实体接触/索敌使用`projectile.canHitEntity(entity)`，保留原版`canBeHitByProjectile()`扩展契约，不维护实体类型黑名单，也不能只判断isPickable。无效实体不进入命中回调、不消费命中预算或荷载；区域伤害/吸引仍按自身设计与canHarm单独结算。

Modifier提供独立effect；BaseEffect默认MODIFIER。onActive用于修改已初始化参数，onTick用于持续行为；制导在GUIDANCE写速度或声明追踪目标，碰撞由MOTION的Move完成，粒子在VISUAL读最终位置。不要在普通轨道里直接改坐标绕过碰撞。

同一链的所有修正统一收集、按ID规范化装配，与放前放后无关。加法、倍率、归零分开处理，不依赖卡序；同类√N叠加使用isStackPrimary/stackScale，只由首实例计算一次。effect.clone为浅拷贝，可变集合/状态不能在投射物间共享。

被动以onInventoryTick工作；重复卡如护盾/火把只由主卡建一套视觉。onPassiveIdle用于正常退场，onUnloaded处理取下/替换后不再tick的清理。读档钩子只读写自身字段，不能访问尚未注入的caster/wand；magic_states按ID共享，不适合每张重复卡独立进度。

主动卡需要限制次数时覆盖`maxUses`，默认0不限次；剩余次数由核心逐卡保存/结算，不写进magic_states或具体effect。复制、荷载预付、旧存档及物品转移规则见[实体卡使用次数](design/spell-references.md#5-实体卡使用次数)。

## 4. 荷载与抽牌控制

- 触发牌从后续牌独立抽取一条链；父链修正不会传入荷载，费用只在原施法事务结算。重复命中不重新抽牌或收费。
- 普通触发及追加命中触发仅处理HitType.ENTITY且目标为LivingEntity的有效命中，不能用alive排除仍存活的穿刺载体。每次通过PayloadRelease深拷贝模板再装配该链自己的修正，不激活或修改模板本身；墙、护盾反射、到期与死亡不补发，载体结束清理模板。命中仍遵循Move和逐刻预算，不按空间子步额外触发。
- 追加定时保留20t计时及更早的终结碰撞条件，反弹不提前消费；追加失效触发仍在到期/死亡时释放。延迟载体仍仅在onExpire按原施法朝向释放，不等下一刻死亡清理。
- 一分为多复制后方单卡；例如十份加速是一张投射物获得十次加速，不复制整个链。复制牌各自付费，但实体牌只抽取/弃置一次。
- 全量施放抽取剩余后方链，不回洗。追加系列寻找载体时经过的普通修正免费，控制牌仍收费；未付费控制牌不释放/收费其展开子树，已预留实体牌仍弃置。
- 刷新通过CastTransaction.draw在抽牌当刻结算已进入的前缀，再决定是否重装；不能先展开全部右侧牌再于finish刷新。复制准备树必须按实际实例顺序处理刷新，不能先执行模板。次数与继续抽牌规则见[引用机制第2节](design/spell-references.md#2-刷新)。
- 本次根链和递归荷载成功付费的代价汇总一次；任意成功付费链锯清整个事务delay，未付费不生效。

引用、免费复制、刷新与生命付款的边界见[引用机制](design/spell-references.md)。SpellAssembly.copyCard必须保留mana/delay/recharge与castingCostsWaived；不能让二次复制重新收费。CURSE生命成本由CastTransaction汇总，在实际施法前支付，禁止注入每枚投射物。

同源复制使用SpellAssembly.copy/inherit，重建非生成配方、复制独立荷载。SpellAssembly.copyCard用于待发射模板的完整复制，保留该卡已装配的修正及嵌套树，每份配方只装一次。生成型修正不递归继承；当前实际速度经SpellAssembly.launch抵消模板与加速倍率，避免再乘一遍。独立触发荷载不能调用父配方inherit。

## 5. 伤害、质量与特殊释放

Projectile.damageType与selfDamage分别声明，类型不自动开启自伤。正HEALING恢复HP，负值取相反数造成healing伤害；不额外击退。新增类型同步ModDamageTypes、damage_type数据、原版标签、中英文类型/死亡文本（基础/.player/.self）和假人颜色。统一通过ModDamageTypes创建SpellDamageSource，死亡文本选择与来源规则见[战斗规则](design/combat-pricing.md#5-伤害类型自伤与击退)。

质量必须正且有限；实体可用NeoForgeData的primalspells:mass覆盖，投射物字段受MagicMass校验。引力按F/m，不能乘来源质量。后坐力只为付费直接施法排队，复制/触发不重复推施法者。

蓄能投射物参考ConcentratedLight：出手锁定部署点与方向、等待10t、再恢复运动；蓄能期间不能反复跑SETUP或重建效果，释放后的碰撞仍走Move。

## 6. 粒子与资源

粒子默认使用`spellVisual/spellParticle/spellParticleSpec`，保持全亮但不照亮环境；仅照明卡显式申请光源。PD接线先读 [effects-api](design/effects-api.md)；遇能力缺口按政策停止并登记，不在本侧补渲染/光照。造型遵循 [像素风格](design/minecraft-visuals.md) 与 [连贯性](design/animation-continuity.md)。

新增卡需物品定义、模型、纹理和zh_cn/en_us名称/描述；图标在tools/spells/pixel_icons.py提供12×12主体，运行generate_icons.py生成16×16类型框贴图并检查完整目录预览。创造栏按投射物、静态投射物、修正、其他/被动分组，别打乱现有注册排序规则。获取池在数据资源中提供来源；生成型卡不随意进入敌人配方。

## 7. 验证与交付

覆盖真实抽牌/付费、独立荷载、同链乱序、复制、到期/碰撞、自伤/护盾与最终清理。不要用同公式重写的测试冒充真实入口；视觉需录制原生输出，新增Scene同步CaptureScenes和assemble场景表。

跑 `./gradlew build`，按 [构建指南](build-and-run.md) 运行相关场景；分别报告机制、像素/光照门禁和环境错误。更新对应设计/台账后提交Git。原存档ID、物品组件和协议变化必须明确迁移规则。
