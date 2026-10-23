# 代码导航

运行时代码根：`src/main/kotlin/work/nekow/primalspells/`。包职责见 [开发约定](conventions.md)。以下路径均相对此根，资源路径相对 `src/main/resources/`。

## 1. 按任务找入口

| 要改什么 | 入口 |
|---|---|
| 抽牌、作用域、MP、双冷却 | `wand/Wand.kt`、`magic/core/{DrawAction,SpellAssembly}.kt` |
| 只读预览/整副分色 | `wand/WandPreview.kt`、Wand.preview/previewSlots、`client/tooltip/WandPreviewCache.kt`、`client/screen/WandPreviewScreen.kt` |
| 原容器编杖/小包 | `item/{WandItem,SpellPouchItem}.kt`、`client/input/{SpellSlotScrollHandler,ClientContainerDragHandler,CreativeWandEditHandler}.kt` |
| 法杖创建/保存/持有者 | `wand/{WandTier,WandManager,WandSavedData}.kt`，WandStats定义在WandTier.kt |
| 法术注册 | `magic/core/MagicManager.kt`；物品由 `registry/ModItems.kt` 自动创建 |
| 运动、反弹、追踪 | `magic/effect/geometry/{Move,SweptCollision,Tracking,Bounce,ConstantAcceleration}.kt` |
| 命中/伤害/击退/冻结 | `magic/effect/lifecycle/{SpellDamage,SpellImpact,DirectDamage,FrozenStatus}.kt` |
| 护盾判定/反射点 | `magic/core/ShieldGuard.kt`、`magic/modifier/EnergyShield.kt`、Projectile命中入口 |
| 触发/到期 | `magic/effect/lifecycle/{Trigger,DelayedTrigger,AppendedTrigger}.kt` |
| 爆炸与跨投射物联动 | `magic/effect/lifecycle/{Explosion,ExplosionFx,SpellInteractions,SpellResonance,CrystalReaction}.kt` |
| 真实弧线光束采样 | `magic/effect/visual/FlightTrace.kt`；读取Projectile.flightPath，高速光脊按实际里程采样，不改写命中轨迹 |
| 普通尾迹/特殊弹本体 | `magic/effect/visual/`；通用造型 `PixelVfx.kt` |
| 持杖三角形/宝石出生点 | `magic/effect/visual/WandCastingFx.kt`、`wand/WandAim.kt` |
| 模型/姿势/施法余光 | `client/render/{WandModelProperty,WandHandPose,WandArmPose}.kt`、`tools/wands/generate_models.py` |
| HUD/布局/反馈 | `client/hud/`、`client/screen/HudEditorScreen.kt`、`network/HudImpactPayload.kt` |
| 假人DPS/跳字 | `entity/{TestDummyEntity,DummyStatsDisplay,DamageNumber}.kt` |
| 获取池/掉落 | `loot/`、`registry/ModLoot.kt` |
| 术士流派/AI/生成 | `entity/{RogueMageEntity,RogueMageAttackGoal,MageSchool}.kt`、`event/SpellcasterSpawns.kt` |
| 罗盘/生活卡 | `item/SpellCompassItem.kt`、`magic/projectile/immobile/{SurveyPulse,ItemPull,FeatherWard}.kt` |
| 数据重载/API/事件 | `integration/{PackRules,SpellApi,SpellEvents}.kt` |
| PD管理器/共享spawn | `util/PdParticles.kt`；PD能力问题只记台账 |
| 录制/诊断 | `client/capture/CaptureDriver.kt`、CaptureScenes及各Scene；`tools/capture/{capture.ps1,diagnostics.ps1,assemble.py}` |
| 碰撞精度回归 | `client/capture/CollisionPrecisionScene.kt`，场景collision_precision；薄形状、拼缝、墙角、24种斜反弹、命中排序和远坐标 |
| 弧线/照明回归 | `client/capture/HomingLightScene.kt`，场景homing_light |
| 光照连续性复现 | `client/capture/ParticleLightingScene.kt`，场景particle_lighting |

## 2. 法术ID与实现

此表对照MagicManager注册工厂；显示名称不改变持久化ID。数值见 [战斗规则](design/combat-pricing.md)，新增流程见 [法术开发](spell-development.md)。

| ID / 名称 | 实现 |
|---|---|
| `survey_pulse` / 显踪脉冲 | `magic/projectile/immobile/SurveyPulse.kt` |
| `item_pull` / 收纳术 | `magic/projectile/immobile/ItemPull.kt` |
| `feather_ward` / 轻羽庇护 | `magic/projectile/immobile/FeatherWard.kt` |
| `fireball` / 火球 | `magic/projectile/Fireball.kt` |
| `divide_two` / 一分为二 | `magic/action/DivideTwo.kt` |
| `divide_three` / 一分为三 | `magic/action/DivideThree.kt` |
| `divide_five` / 一分为五 | `magic/action/DivideFive.kt` |
| `divide_ten` / 一分为十 | `magic/action/DivideTen.kt` |
| `cast_all` / 全量施放 | `magic/action/CastAll.kt` |
| `add_trigger` / 追加触发 | `magic/action/AddTrigger.kt` |
| `add_timer` / 追加定时 | `magic/action/AddTimer.kt` |
| `add_expiration` / 追加失效触发 | `magic/action/AddExpiration.kt` |
| `triple_cast` / 三重施放 | `magic/modifier/TripleCast.kt` |
| `quadruple_cast` / 四重施放 | `magic/modifier/QuadrupleCast.kt` |
| `quintuple_cast` / 五重施放 | `magic/modifier/QuintupleCast.kt` |
| `reduce_recharge` / 缩短充能时间 | `magic/modifier/ReduceRecharge.kt` |
| `torch` / 火把 | `magic/modifier/Torch.kt` |
| `sawblade` / 锯片 | `magic/projectile/Sawblade.kt` |
| `healing_bolt` / 治疗弹 | `magic/projectile/HealingBolt.kt` |
| `drone_bolt` / 无人机弹 | `magic/projectile/DroneBolt.kt` |
| `trigger_fireball` / 触发火球 | `magic/projectile/TriggerFireball.kt` |
| `delayed_cast` / 延迟施法 | `magic/projectile/DelayedCastProjectile.kt` |
| `drone_swarm` / 无人机群 | `magic/projectile/DroneSwarm.kt` |
| `damage_boost` / 伤害强化 | `magic/modifier/DamageBoost.kt` |
| `gravity` / 重力 | `magic/modifier/Gravity.kt` |
| `bounce` / 弹跳 | `magic/modifier/MoreBounce.kt` |
| `double_cast` / 双重施放 | `magic/modifier/DoubleCast.kt` |
| `speed_boost` / 速度提升 | `magic/modifier/SpeedBoost.kt` |
| `extended_lifetime` / 存在时间延长 | `magic/modifier/ExtendedLifetime.kt` |
| `laser` / 激光 | `magic/projectile/Laser.kt` |
| `chainsaw` / 链锯 | `magic/projectile/Chainsaw.kt` |
| `concentrated_light` / 汇聚之光 | `magic/projectile/ConcentratedLight.kt` |
| `recoil_damper` / 后坐力阻尼 | `magic/modifier/RecoilDamper.kt` |
| `recoil_boost` / 反冲增幅 | `magic/modifier/RecoilBoost.kt` |
| `piercing` / 穿刺 | `magic/modifier/Piercing.kt` |
| `bounce_explosion` / 弹跳爆炸 | `magic/modifier/BounceExplosion.kt` |
| `homing` / 追踪 | `magic/modifier/Homing.kt` |
| `omnidirectional_scatter` / 全向散射 | `magic/modifier/OmnidirectionalScatter.kt` |
| `bounce_duplicate` / 弹跳复制 | `magic/modifier/BounceDuplicate.kt` |
| `gradual_accelerate` / 逐渐加速 | `magic/modifier/GradualAccelerate.kt` |
| `gradual_decelerate` / 逐渐减速 | `magic/modifier/GradualDecelerate.kt` |
| `random_path` / 随机路径 | `magic/modifier/RandomPath.kt` |
| `reciprocate` / 往复运动 | `magic/modifier/Reciprocate.kt` |
| `sine_wave` / 蛇形 | `magic/modifier/Serpentine.kt` |
| `mana_injection` / 额外法术 | `magic/modifier/ManaInjection.kt` |
| `diffusion` / 扩散 | `magic/modifier/Diffusion.kt` |
| `implosion` / 聚爆 | `magic/modifier/Implosion.kt` |
| `volatile` / 易爆 | `magic/modifier/Volatile.kt` |
| `explosion_remove` / 移除爆炸 | `magic/modifier/ExplosionRemove.kt` |
| `explosion_projectile` / 爆炸投射 | `magic/modifier/ExplosionProjectile.kt` |
| `small_explosion` / 小型爆炸 | `magic/projectile/immobile/SmallExplosion.kt` |
| `large_explosion` / 大型爆炸 | `magic/projectile/immobile/LargeExplosion.kt` |
| `gravity_field` / 重力场 | `magic/projectile/immobile/GravityFieldProjectile.kt` |
| `black_hole` / 黑洞 | `magic/projectile/immobile/BlackHoleProjectile.kt` |
| `self_orbit` / 周身环绕 | `magic/modifier/SelfOrbit.kt` |
| `teleport` / 传送魔弹 | `magic/projectile/TeleportProjectile.kt` |
| `quantum_entanglement` / 量子纠缠 | `magic/modifier/QuantumEntanglement.kt` |
| `energy_shield` / 能量盾 | `magic/modifier/EnergyShield.kt` |
| `dormant_crystal` / 休眠水晶 | `magic/projectile/DormantCrystal.kt` |
| `crystal_detonation` / 水晶引爆 | `magic/projectile/immobile/CrystalDetonation.kt` |
| `chain_bolt` / 连锁电弧 | `magic/projectile/ChainBolt.kt` |
| `long_distance_cast` / 远距施放 | `magic/modifier/LongDistanceCast.kt` |
| `plasma_beam` / 等离子束 | `magic/projectile/PlasmaBeamProjectile.kt` |
| `repulsion_field` / 斥力场 | `magic/projectile/immobile/RepulsionFieldProjectile.kt` |
| `freeze_charge` / 冰冻附魔 | `magic/modifier/FreezeCharge.kt` |

## 3. 资源与注册

- `assets/primalspells/{items,models/item,textures/item}/<id>`：物品三件套；`lang/{zh_cn,en_us}.json`：名称、描述、单位和死亡文本。
- `data/primalspells/damage_type/`及`data/minecraft/tags/damage_type/`：伤害数据与原版标签。
- `data/primalspells/loot_modifiers/`：NeoForge自动发现GLM，不建立旧式global_loot_modifiers索引。
- `data/<namespace>/primalspells/{loot_profiles,wand_tiers}/`：数据驱动获取与新杖规则；结构标签放 `tags/worldgen/structure/`。
- `META-INF/neoforge.mods.toml`、`primalspells.mixins.json`、`META-INF/enumextensions.json`：依赖、客户端mixin与手持动作扩展。
- `tools/spells/generate_icons.py`：法术像素卡；`tools/wands/generate_models.py`：原生低模模块资源。

## 4. Java mixin

`src/main/java/work/nekow/primalspells/mixin/`：`WandUseAnimationMixin`取消成功施法后的重举，`ItemInHandRendererAccessor`供原生高度回归观察。只在mixin配置client列表加载，不让公共服务端类引用。
