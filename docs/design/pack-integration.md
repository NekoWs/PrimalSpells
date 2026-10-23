# 整合包接口 v1

数据包与公共服务端接口供整合包和附属模组使用。红石施法器按用户要求暂缓。

## 数据包

Minecraft 26.2的数据包版本为107.1。可直接复制[完整示例](../examples/exploration-pack/pack.mcmeta)所在的exploration-pack目录到存档datapacks目录，再执行`/reload`；示例主动改变前哨站奖励、配方与T1新杖参数，测试后可禁用。无需修改JAR。

资源目录中的重复primalspells不是笔误：第一个是命名空间，第二个是本模组读取的资源路径前缀。

| 资源 | 作用 |
|---|---|
| `data/<namespace>/primalspells/loot_profiles/<name>.json` | 散卡、成品/精英配方、等级范围、生成权重、结构标签与维度 |
| `data/primalspells/primalspells/wand_tiers/1.json`到`6.json` | 创建新杖时的等级数值；不接受调试杖覆盖 |
| `data/<namespace>/loot_modifiers/<name>.json` | 使用`primalspells:exploration`，profile引用池键，原版/NeoForge条件决定箱子来源 |
| `data/<namespace>/tags/worldgen/structure/<name>.json` | 实际术士生成地点与罗盘搜索地点 |

内置池键保留starter、ruins、raiders、arcane、nether、relic；其他命名空间池使用`namespace:name`。同资源ID按原版数据包优先级覆盖，不合并JSON字段；散卡数组重复ID可作为相对权重，成品/精英配方数组均匀抽取。各字段如下：

| 字段 | 边界/语义 |
|---|---|
| min_tier / max_tier | 1～6，闭区间、均匀抽取 |
| cards | 非空注册法术ID列表，最多4096项 |
| loadouts / elite_loadouts | 成品配方列表；普通配方非空，精英为空时使用普通配方；单份1～18张 |
| structure_tag | 真实结构标签ID；不使用虚构地点 |
| dimensions | 可选维度ID列表；空列表不限维度 |
| spawn_weight | 0禁用本池自然术士，正数追加原版MONSTER单人候选，最大10000 |
| elite_chance / wand_chance / charged_chance | 0～1；精英出生率、箱子成品杖率、蓄能变体率 |
| card_count | 箱子散卡数量0～64；精英死亡另有一张本池散卡 |
| priority | 可选整数，重叠结构优先取高值；相同优先级取max_tier较高的池 |

敌人只在已加载的实际结构piece内生成，维度和spawn_weight也参与筛选；仍遵守原版怪物预算、地面和非和平条件。池不是新的结构生成器。自定义池要加入罗盘地点，只需有有效结构标签和对应维度；即使spawn_weight=0也可以定位探索。

等级JSON需要max_mana、mana_per_tick、delay_ticks、recharge_ticks、slots、casts。法力为正有限数，回蓝非负，两个时间为0～72000完整刻，槽位1～18。casts是加权抽样数组，例如`[1,1,1,2]`表示75%抽1、25%抽2；每项1～18，总项数1～1024。原变体倍率继续作用于新配置；实际掉落配方比槽位长时容量扩到配方长度，最多18格，不能吞掉尾部卡。

PackRules先在准备阶段校验全部条目，成功后一次发布快照。未知法术、非法数字或结构ID语法使本次重载失败，运行中的旧快照不被半份配置覆盖。标签是否存在由原版注册表/标签系统决定；不存在的标签不会产生目标或术士。不要把无效配置“自动纠正”为另一个法术。

## 公共服务端入口

`work.nekow.primalspells.integration.SpellApi.VERSION = 1`。

Java示例：

```java
// 在服务端主线程；npc当前朝向决定瞄准，最好实际装备该ItemStack。
boolean accepted = SpellApi.cast(npc, wandStack);
```

只接受存活服务端实体、真实WandItem及服务端主线程。创建尚无ID的新杖，已有ID走原持久化/懒加载；调用原Wand.tick/spell，维护被动、MP、完整刻冷却和抽牌，玩家装备预热照常。返回值沿用Wand.spell：表示一次施放事务通过前置门禁，低MP导致整手没有付费投射物时也可能为true；判断实际扣蓝或投射物请结合后事件。调用者负责施法频率、朝向与装备生命周期，不能异步操作世界。不得把事件监听器中的同杖递归施法当作触发荷载。

## 事件

以下为`SpellEvents`中的公共类，通过服务端`NeoForge.EVENT_BUS`同步派发，Java可直接addListener；没有客户端类依赖。

| 事件 | 契约 |
|---|---|
| BeforeCast | 装备/双冷却门禁之后，抽牌、扣MP之前；可取消，取消不消费牌堆或费用 |
| AfterCast | 正常事务结算后；含caster、wandId、manaBefore/After及最终双冷却；低MP空事务仍有事件 |
| BeforeImpact | 所有法术伤害/治疗的统一原版结算入口，可取消、修改amount；有限数校验；保留独立伤害类型 |
| AfterImpact | 原版实际接受伤害/治疗后；amount是提交给原版护甲/抗性的金额，不冒充扣除后的生命差值 |
| StatusChanged | 本模组冻结状态实际进入/结束；status=`primalspells:frozen`，结束ticks=0；原版药水使用NeoForge原有药水事件 |
| UtilityAction | 显踪/收纳/轻羽逐目标请求；可取消，action为法术ID，供领地/队伍等保护规则接入 |

BeforeImpact发生在既有护盾格挡之后，盾已抵消的弹体不会再触发一份伤害事件；该入口涵盖普通弹、光束、爆炸、暗核和负治疗伤害。取消伤害时不追加该入口的击退和命中HUD反馈。监听器修改金额也影响强度击退。游戏正常药水、箭矢和其他模组伤害不被本事件拦截。

```java
NeoForge.EVENT_BUS.addListener((SpellEvents.UtilityAction event) -> {
    if (!myClaims.canInteract(event.getCaster(), event.getTarget())) {
        event.setCanceled(true);
    }
});
NeoForge.EVENT_BUS.addListener((SpellEvents.BeforeImpact event) -> {
    if (myRules.protectedTarget(event.getTarget())) event.setCanceled(true);
});
```

myClaims/myRules是整合方提供的对象，不是本模组自带领地系统；示例不声称自动兼容所有保护模组。收纳另外沿用原版拥有者、拾取延迟、背包容量和NeoForge拾取事件。

## 存档与扩展兼容

不改已有wandId、法术ID、SavedData键和伤害类型ID。数值只在新杖创建时读取当前等级规则；重载/升级不会给玩家旧杖重新掷点。旧未知法术继续按原机制保留空槽。术士原手杖仍走原版装备掉落；精英标志和池键保存在实体NeoForge持久数据，不依赖客户端标签或名称判断。

删除自定义池后已有杖仍按保存的牌与数值工作；已有该池术士额外奖励回退ruins，装备不替换。罗盘旧池选择在下一次使用时回退当前维度可用池；坐标使用原版LODESTONE_TRACKER，tracked=false避免把结构当磁石校验掉。API v1使用上述事件字段和服务器线程语义，未来破坏性调整必须提升API版本并在本文注明迁移。
