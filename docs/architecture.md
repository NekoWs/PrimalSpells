# 运行时架构

## 1. 总体分层

`PrimalSpells`是装配入口：先由`ModSpells`填充通用`SpellRegistry`，再创建物品与挂载事件、网络。`SpellRuntimeEvents`把世界生命周期交给`ProjectileRuntime`，不让工厂目录持有世界状态。

`Wand`只持有权威配置/运行状态、装卸和每刻维护；抽牌、付费、实际发射、预览展示与NBT分别有独立实现。客户端负责输入、HUD、模型和展示缓存；世界粒子的动画、插值与光照由PD负责。本项目是按职责分包的单个Gradle模块，不将持有caster/wand引用的协作对象描述成完全独立模块。

| 边界 | 实现与唯一职责 |
|---|---|
| 内容目录 | `registry/ModSpells`选择内置卡；`magic/core/SpellRegistry`提供只读工厂表与新实例创建，拒绝重复ID |
| 抽牌树 | `wand/WandDeck`解析预算、控制牌与独立荷载，并管理弃牌/回洗；状态仍只存一份于Wand |
| 付费事务 | `wand/CastTransaction`递归付费、记录接受的卡，并统一结算回洗费用、双冷却与链锯归零 |
| 实际施法 | `wand/WandCasting`检查门槛/事件，装配并发射付费弹体，最后通知表现与AfterCast |
| 只读预览 | `wand/WandPreviewBuilder`克隆牌堆，在同一解析器/事务上观察作用域与结果，不激活效果 |
| 存档边界 | `wand/WandPersistence`集中NBT键、旧单位兼容、空槽/牌堆和卡自身状态恢复顺序 |
| 世界运行时 | `magic/runtime/ProjectileRuntime`推进弹体、反应与延迟动作；停服清空在场及待加入对象 |
| 网络边界 | `network/ModNetworking`注册协议；`WandRequestHandler`在写入前校验服务端菜单/身份/权限 |

具体文件见 [代码导航](code-map.md)，端分离与目录见 [约定](conventions.md)。

## 2. 对象模型

| 对象 | 职责 |
|---|---|
| Magic | ID、MP、delay/recharge、逐卡剩余次数、抽牌预算cast与生命周期钩子 |
| Projectile | 独立MagicStatus、存活/寿命、碰撞/速度、配方、荷载、自伤、质量与后坐力 |
| Modifier | 为所属链提供独立effect实例；自身不进入投射物循环 |
| OtherSpell / DrawAction | 其他主动法术；DrawAction专门解析引用、复制、全量、追加与刷新，不生成弹体 |
| TriggerSpell / AppendedPayload | 独立作用域的荷载树；生物命中保留模板重复复制，定时/失效等条件消费一次 |
| MagicStatus | 同一投射物各effect共享的实时位置、速度、年龄和伤害 |
| SpellAssembly | 规范化修正装配、同源复制和静止源的运动准备 |

`Magic.clone()`从注册工厂新建实例，不复制运行状态。需要复制配方/荷载时调用SpellAssembly专用入口；浅拷贝effect中的可变引用仍需自行隔离。

## 3. 施法与抽牌

1. `WandItem`/公共SpellApi调用 `Wand.tick/spell`，检查装备预热、双冷却、施法者和BeforeCast事件。
2. `WandDeck.draw`从drawPile抽取一条树；cast预算消耗于投射物，多重施放增加预算；触发牌立即独立抽取荷载。
3. `CastTransaction.pay`按链收集修正并结算成功付费牌，同时预留实体卡次数。每张牌收费一次，荷载费用计入整次施法，不写进父投射物；真实施法在生命付款后提交次数，预览不提交。
4. `WandCasting`将本链修正统一装入本链投射物，设置caster/wand/杖首出生点/准星方向，激活后加入pendingAdd。荷载等触发时才释放，不重新抽牌或扣蓝。
5. `CastTransaction.finish`汇总双冷却并弃牌/回洗；任意成功付费链锯清零整次施放延迟，与牌序无关。所有抽到的实体牌进弃牌堆，抽空后回洗并加recharge；成功刷新可提前回洗，单张免充能、多张正常充能。

同链修正没有排列先后语义，按ID规范化装配；加值、倍率、归零需按规定阶段实现。父链的散射、穿刺、冻结、增伤和速度不自动传入独立荷载。根链和荷载费用都影响本次法杖事务。

一分为多复制下一张**卡**，不是下一条链；复制修正等价于多张修正。全量施放只展开后方剩余牌，不绕回弃牌堆。追加条件抽取独立荷载；展开与付费失败规则见 [法术开发](spell-development.md)。

引用卡通过同一解析器的副本游标展开，活动原卡身份阻止循环；免费复制、刷新、生命付款与Zeta随机流见[引用机制](design/spell-references.md)。血魔法HP只在真实发射前按事务支付，预览只计费用。OtherSpell由直接施法/统一荷载入口执行，两处均先prepare本链修正，不加入弹体循环。

## 4. 投射物循环与命中

`ServerTickEvent.Pre → SpellRuntimeEvents → ProjectileRuntime.tick()`：处理冻结；遍历存活弹体并按阶段tick，age加1；寿命耗尽的当刻先onExpire，再标记死亡。已死亡对象在循环开头执行die/onDie并移除；pendingAdd最后统一加入，避免遍历中修改列表。随后处理跨投射物反应与Deferred有限动作。停服由`ProjectileRuntime.clear`统一清空在场列表、pendingAdd、冻结与延迟队列，待加入对象不能进入下一个集成服务器世界。联动每刻预取反应元数据，只有水晶/黑洞或冰火互补组合才枚举；普通弹彼此、冻结弹彼此不全配对，保留原先i/j结算顺序。

激活按EffectPhase.activeOrder，SETUP先写出生和初速，MODIFIER随后改数值。每刻顺序：`MODIFIER → GUIDANCE → MOTION → OVERRIDE → VISUAL`。普通轨道在GUIDANCE改速度，追踪声明目标与曲率强度；Move在真实子步转向/扫掠并记录本刻flightPath，光束表现读取该路径与最终接点。

世界位置、出生点和HitResult使用Vector3d，方向/速度使用局部Vector3f；原版/PD接口经WorldVectors转换，不能先把世界坐标转float。Position复制出生点，运行中位置不与初始位置共享可变实例。

实体接触共用`Projectile.canHitEntity`：原版`Entity.canBeHitByProjectile()`、存活/非旁观及本模组自伤权限；掉落物、经验球、展示/交互实体不消耗命中次数、不释放荷载。追踪、连锁、等离子与护盾候选同样遵循；准星选点使用相同原版能力判定。该接触契约与区域伤害权限`canHarm`分开，后者不改写爆炸/引力的作用对象。

Move阻尼一次后，以最多1/16格分段积分弧线，每段解析求线段与实际碰撞形状/实体扩张箱的进入点，按墙、护盾、实体的真实进入时间排序，同距墙优先。包含跨格形状与方块拼缝，不能以端点包含判定代替扫掠。实体宽相位覆盖整刻任意反弹可达范围；曲线按需逐子步积分，不预建将被反弹/换靶丢弃的未来路径。同格子步复用邻域形状，跨格重收集；坐标缓存使用原生长整数键，形状/邻域缓存只在本刻复用并在命中回调后失效。未命中的slab测试不分配数组，实体扩张箱在原箱变化时刷新。

实体首次接触立即派发，之后按真实接触时间每1/16刻重复，不能按1/16格子步重复伤害。Move解析每段进入/离开实体扩张箱的时间区间，在区间内安排命中并与墙/盾按时间排序；只有实际执行的接触才推进该目标的下次时钟，跨刻、反弹和换靶重排仍保留它。已到期时钟逐刻回收，不形成永久免疫；持续接触的刻末重复由下一刻处理，避免边界双算；首次进入即使恰在刻末也立即命中。内部物理时间可以细分，卡牌的寿命与冷却仍用完整刻。数值基准见[战斗规则](design/combat-pricing.md#6-速度质量与后坐力)。

碰撞只读取getChunkNow已加载区块；相邻必要区块未知时停在最后已知位置，保留速度、不伪造命中/反弹，寿命仍照常推进。之后区块加载可继续运动；世界高度外是已知虚空。禁止用getBlockState强制沿高速弹路程同步加载/生成地形。

护盾在死亡标记和命中回调前拦截。墙/盾反射从真实接点重排剩余刻时间，按反射后速度继续，不能把反弹当作一整刻结束。安全分离仅1e-7格，与视觉接点分开；墙角同时进入的轴分别反向，避免平均法线交换不等长分量。墙反弹消耗bounce，实体命中不消耗墙反弹。光束等距采样额外保留反射折点，水晶联动也读取flightPath，不用最终速度倒推入射路径。

普通触发与追加命中触发只在有效生物命中时释放，使用HitResult的精确接点和原有出射方向；方块、非生物实体、护盾反射、自然到期与其它死亡不释放命中荷载。每次命中经PayloadRelease复制已付费模板，发射全新弹体与独立递归荷载；模板不激活、不追加修正、不重复抽牌或扣蓝，直到载体结束才清理。穿刺的每次接触均可触发，仍受Move接触时钟及damageOncePerTick约束；直接伤害≤0不阻止命中回调。父链穿刺/减伤不传入荷载。

追加定时仍以20t或更早的终结碰撞释放，存活反弹不提前消费它；追加失效触发仍走到期/死亡条件。这些一次性条件先清空再使用已准备实例，不能重复消费。延迟施法仍仅在onExpire按原施法朝向释放。SpellAssembly.copyCard保留每张待发射弹体的规范化修正配方并深拷贝嵌套荷载，不能让重复发射共享状态或把同一修正装配两次。

追踪仅选择前方目标，进入目标碰撞范围后跳过已穿过的制导目标；从接触切线重排当刻剩余路程，新目标的曲率渐入，不瞬间改向。制导宽相位覆盖本刻任意可能转向，实际已行驶路程才推进接入状态；方块反弹后可重新选靶，不抑制真实接触的重复伤害。水晶自然到期爆炸。提前碰撞死亡不调用onExpire。持续切割同一目标每刻最多一次，不因子步增加伤害。

damageOncePerTick是每弹体、每目标、每刻的预算：Projectile在护盾拦截后、实体命中计数与全部onHitEntity/onHit回调前去重，防止直接伤害去重后附加爆炸仍被重复派发；SpellDamage保留直接伤害入口的独立去重，覆盖光束/区域场等不走hitEntity的结算。两份集合在Projectile.tick开头清空，复制体不共享。锯片、等离子束和黑洞显式开启；普通穿刺仍按接触时间重复，真实墙反弹和不同目标/不同弹体不合并。各法术频率见[战斗规则](design/combat-pricing.md#7-伤害频率)。

## 5. 状态与持久化

物品的WAND_ID指向WandManager内存实例及WandSavedData；WAND_SPELLS是展示副本，WAND_SELECTED_SLOT是编辑选择。小包内容与选择直接保存在物品组件。

`Wand.saveTag/fromTag`委托`WandPersistence`；保存数值、法力/冷却、固定槽位、三牌堆与可选magic_states。空槽占位、未知/已删除法术保留空位，不前移后续牌。magic_states按法术ID索引，重复ID共享状态；loadRuntimeState只能读取自身字段，不能依赖尚未设置的caster/wand。

限制次数属于实体卡，使用独立逐槽NBT与物品组件，不使用按ID共享的magic_states。三牌堆以新增槽位索引恢复同名卡身份，保留旧ID列表兼容读取；装卸转换统一经`item/component/SpellCardData`。字段与结算边界见[实体卡使用次数](design/spell-references.md#5-实体卡使用次数)。

WandManager通过背包tick及主动施法markHeld确认任意实体持有者；周期回收闲置内存实例前先落盘，get按需懒加载，存档条目不因无人持有删除。服务器换世界先清实例和持有记录。投射物不跨存档存活。

时间以完整刻保存，旧秒/毫秒键迁移与法杖配置见 [等级与单位](design/wand-tiers.md)。MP在读档、支付和维护边界钳制到0～上限。

## 6. 网络与整合

公共协议版本统一定义在`ModNetworking.PROTOCOL_VERSION`，当前1.8.0。S→C数值、命中反馈、成功施法模型通知、镜头冲击和预览结果经ClientPayloadSink分发。C→S选槽/预览校验实际菜单与背包中的身份和范围；创造专用编辑包只接受创造玩家和合法散卡，生存走原版PICKUP。

`WandPreviewBuilder`在隔离牌堆/卡牌/法力副本上调用同一WandDeck与CastTransaction，不运行施法钩子、不影响原杖。整副配置分色只观察WandDeck.draw，不付费、不回洗；按卡牌对象身份标记槽，重复ID不合并。

PackRules校验数据包后原子发布池/等级快照，新数值仅作用于新杖。SpellApi与公共事件的线程、取消及兼容契约见 [整合接口](design/pack-integration.md)。

## 7. 客户端

原容器滚轮选槽、右键装卸；V只读预览。HUD分法力、双冷却、准心反馈，H配置布局。模型用原生OBJ/MTL组合与数值属性选帧，成功施法按wandId播宝石余光。长按不减速，成功使用不重置持物高度，真实切槽仍装备。

命中/击杀HUD只来自实际接受的非治疗、非自伤命中。CameraImpulse只偏航/俯仰，受原版受伤视角强度控制；退出/换世界清缓存与动画，保留本机HUD布局。详见 [HUD](design/hud.md)、[持杖表现](design/wand-casting.md)。

## 8. 关键不变量与陷阱

- 核心结算仅服务端执行；公共侧不得加载client。修改抽牌、effect顺序和持久化前复核本节。
- 同链修正与牌序无关，荷载完全独立；预览复用真实解析，不写近似客户端解析器。
- Modifier只装配一次；同源复制重新构造非生成配方与独立荷载，不能共享可变effect、重复乘速度或递归继承生成卡。
- 普通运动必须经过Move；轨道不直接覆盖坐标绕开碰撞。追踪的弧线也是实际运动与碰撞路径，不能仅把直线命中画成曲线；世界位置不能降为float，反弹不能丢弃本刻剩余时间。连锁魔弹的离散跃迁是例外，落点自行命中并可越过薄墙。
- 穿刺重复接触按时间结算，不能按空间子步结算或固定封顶每目标次数；提速减少重叠时间和命中次数。帧伤去重另限制同目标同刻，不是永久命中免疫。
- 一次性荷载释放即清空；命中荷载每次有效生物接触复制新实例，直到载体结束才清模板，寿命/死亡不能伪造额外命中。护盾拦截同一次命中的爆炸/荷载不得利用刚产生的破口补伤。
- 正治疗反向结算并通过护盾；负治疗是独立healing伤害。伤害类型不自动决定自伤。
- 爆炸不破坏方块。穿刺开启所属链自伤并绕盾；独立荷载不继承。静态场不碰撞，仍参与质量受力和位置积分。
- 区域场在原维度运行；不能每刻追读已跨维度施法者的新level。离场/结束即时撤销本场迟缓；黑洞不吸创造飞行者。
- 引用副本不写回实体牌堆；Miu/Phi/Sigma仅免复制品的资源/时间代价，生命付费不免；预览不得推进随机流或扣HP。虚空状态同时限制命中、索敌、移动与对外施法；持续时间只读取所属链存在时间修正，不能继承父链。
- 次数属于每张实体卡，不能按ID共享；正常运行实例绑定原卡，玩法复制免次数且可复制耗尽卡。预览不提交次数，回洗/刷新/装卸不补次数。
- MP不得超过上限；触发时不重复付费。链锯清整次施放延迟，不消除法力或充能门槛。
- 卸下/闲置被动必须有限退场，死亡/换维/登出清理；PD成员、时钟、插值和光照由PD管理，遇缺陷按 [约定第9节](conventions.md#9-外部依赖政策particledrawing) 停止并登记。

## 9. ParticleDrawing接入

法术世界表现使用 `wand.pdParticles(level)` 获取原维度管理器，保证非玩家施法也有粒子。ParticleGroup用于刚性造型/表达式编排，ParticleBatch用于独立运动成员，Emitter用于逐渲染帧拖尾；不自建集合或插值器。

PD原生首次外观原子接管、变量终点完成回报和退场retire已接入。透明出生与零尺寸表达式共同防止切杖先闪满尺寸。PD 1.0.21的`ebf3055`构建同时修复resize与SCALE曲线组合，按分数年龄采样，并将尺寸/alpha包络与独立LIGHT通道共同用于照明；完整法术覆盖与高刷短尾见 [待验证范围](issues/verify.md)。完整 API 使用见 [effects-api](design/effects-api.md)。

## 10. 扩展边界与自动检查

增加卡只扩展内容类与ModSpells，不往目录类增加tick/事件；改抽牌进入WandDeck，改费用/冷却进入CastTransaction，不能为预览再复制一份结算。NBT兼容只在WandPersistence维护，不因内部类拆分变更ID、键或协议。公共外部入口仍为SpellApi；Wand的spell/preview/saveTag/fromTag入口保持。

`gradle/architecture.gradle`的checkArchitecture随check/build执行：公共侧禁止导入客户端，core禁止导入具体卡和内容注册，抽牌/付费禁止导入PD、表现与网络。它检查Kotlin显式import，不是完整的字节码依赖证明；全限定调用和反射仍需代码审查。实机foundations额外覆盖注册/物品表一致、重复ID拒绝、存档游标/单位恢复、预览只读和弹体插入/到期/清理顺序；progression保留真实外部事件取消与公共API验证。

当前重构回归：`foundations,spell_chain,progression,exploration,spell_performance,collision_precision,homing_light,shield_balance`八场306项机制检查通过，1804帧、16丢帧均在原预算内，视觉与日志门禁通过。证据在`build/capture/architecture-suite/`；独立性能复测在`build/capture/out/spell_performance/`。源码依赖检查另用违规公共侧/核心导入验证确实拒绝构建，删除探针后通过；图标生成器覆盖全部注册ID。构建无单元测试源，这些通过项来自原生场景与依赖检查。
