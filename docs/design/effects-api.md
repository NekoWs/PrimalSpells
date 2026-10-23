# ParticleDrawing 接入

## 0. 职责与缺陷处理

PD是粒子成员管理、运动积分、锚点、渲染插值、寿命曲线、动画编排和动态光照的唯一实现方。PrimalSpells只提供玩法触发、几何初值、颜色、力量与时长参数。

遇到API缺陷或能力缺失，**停止该接入点并登记需求，不在本模组自建替代实现**。禁止复制播放器、手写插值时钟、维护另一套粒子寿命或光照平滑缓存。先明确目标模型，再核对当前sources JAR，最后记录缺口、验收和恢复条件；规则见 [开发约定第9节](../conventions.md#9-外部依赖政策particledrawing)。

当前依赖为1.0.21-ALPHA，实际版本取 `gradle.properties`；主JAR与sources JAR必须匹配。未决项仅在 [PD需求](../issues/pd-api-requests.md) 维护。改爆炸前查看本地参考 `.refs/explosion-vfx/`，它不入库。

## 1. 选用哪种API

| 需求 | 入口 | 约束 |
|---|---|---|
| 有限单颗片元 | ParticleManager.create / ParticleHandle | spawn可被容量拒绝，处理null |
| 刚性造型、组变换、表达式 | ParticleGroup | 先设轴心/锚点再放成员；成员增删不当作粒子模拟 |
| 每颗速度不同、条件回收 | ParticleBatch | 成员簿记和权威pos/vel归PD，批量track/velocity/force |
| 飞行拖尾按里程发射 | emitter + Anchor.Movable | 传当前位置/速度，由PD逐渲染帧采样，不在本侧延后一刻 |
| 跟实体 | attachTo或组followEntity/defineEntity | 程序化表达式实体getter负责持杖阵列实时跟随 |
| 生物施法管理器 | wand.pdParticles(level) | 在法术原维度兜底创建，不依赖玩家背包tick |

ParticleGroup面向编排；ParticleBatch面向独立运动成员。不要为了每刻增删成员不断重发整个组，也不要用自管句柄表重新实现Batch。黑洞只计算向心速度，位置积分交PD。

## 2. 位置、朝向与时钟

- `track/move/trackAll`接管位置并清除速度/力；速度/力指令解除实体锚点。同颗粒子只选一种运动驱动。
- 可移动锚点与track使用相邻服务器样本插值，不补本侧“延后一tick”。固定世界结构显式选择WORLD朝向。
- 组move不改变arm基准轴心；轴心由setPivot/followEntity决定。组scale/pulse同时影响成员距轴心的距离，面板缩片不能误缩整层球壳。
- 增量指令按“从现在起”追加，不手工叠加播放游标。游戏暂停应冻结动画；没有客户端墙钟替代品。
- 表达式必须明确xyz，不用仅sc表达式假定保留世界位置；`t`是PD表达式毫秒，配置/玩法时间仍为刻，边界处换算。
- 原生Bridge插值相邻外观端点，首次程序接管原子设置两端。不能据20FPS录制宣称所有短生命周期都具有高刷连续性。

## 3. 外观与曲线

ParticleVisual定义贴图、UV、广告牌/方向、各向异性尺寸、混合与全亮；RGB使用固定色阶、SQUARE硬边，默认不使用additive，详见 [视觉规范](minecraft-visuals.md)。

`scale`是编辑器单位，16px贴图整宽约 `scale × 0.2` 格；按世界格声明用 `anisoWorld`。各向异性世界尺寸不能假定由单一resize按格数改写，使用相应尺寸/组倍率API。

`ParticleLifeCurve`的通道是ALPHA、SCALE、RED、GREEN、BLUE、LIGHT；同通道多条曲线相乘，总计最多8条。`.sizeCurve/alphaCurve`是追加；重复热更必须用 `.lifeCurve(ParticleLifeCurve.of(...))`覆盖完整通道集合，避免不断叠乘或丢掉另一通道。

拖尾在spawn和life热更时都声明SCALE与ALPHA，当前视觉仍保留4t最低寿命。PD按出生partialTick计算分数年龄并逐帧采样曲线；1～2t拖尾的完整实机验收见 [待验证范围](../issues/verify.md)，不因升级直接改变既有尾长。

## 4. 出生与回收

- 持杖阵列和冷却组用透明出生，表达式首帧给出正确位置与零入口尺寸，避免先展示完整造型。
- 重定向从当前值接续。主法阵presence用8t线性变量缓动收束，再调用retire；保留会话直到PD组清空。
- 有限表达式/变量用PD完成信号回收；离线超时由PD兜底。固定destroyAfter只用于现有有限尾段，不是修补动画断帧的通用方案。
- 停止发射器只停止新粒子，已出生尾迹按自身曲线结束；不能stop后立即删全部尾粒子。
- 死亡/换维/登出需按生命周期清组；正常动画退场与强制世界卸载区分。

## 5. 全亮与动态光照

`glowing(true)`表示片元免环境光照，`lightLevel(0..15)`表示向世界照明，两者不是同一效果，也不是加法混合。PD默认lightLevel为15，不能只设置全亮而漏掉光源等级。

法术统一使用`spellVisual()`、`ParticleManager.spellParticle()`、`spellParticleSpec()`：visual、单颗Builder与批量Spec均显式全亮、零环境照明。`spawnParticle`与Draw使用同一外观契约；飞行发射器另外显式`glowing(true).lightLevel(0)`。爆炸、护盾、晶体、光束、施法阵和碎屑都遵守该默认值，夜晚保持颜色且不登记动态光源。

修正`light`（光）由`SpellLightFx`给所属链每枚弹体添加一颗14级动态光源，不给每个装饰片元/拖尾采样点登记光源。重复卡仍收费，但只由首实例创建；复制体继承独立源，触发荷载完全隔离。光源跟随本体的PD track样本，2t升亮，结束从当前光强接续4t熄灭，6t后回收；没有逐刻重建光源或热更全套装饰片元。长光束只照亮本体附近，不铺设整条路径光源。火把是明确的被动照明法术，保留一颗14级光源。

DynamicLightManager读取PD原生lightIntensity(partialTick)：基础lightLevel × 静态乘数 × LIGHT曲线 × alpha × 尺寸包络，再叠加热更接续偏移。LIGHT只影响环境照明，不改变片元alpha/全亮；ParticleVisual.lightIntensity为静态乘数，ParticleHandle.lightIntensity从当前光强接续。全局/空间格预算与相机距离裁剪继续由PD负责，不在本模组另写预算管理器。

`spell_lighting`实际运行注册投射物目录，默认特效峰值超过一万颗时光源数为零；昼夜对照检查实际像素颜色。真实付费重复卡、同源复制、独立荷载以及光源有限退场均有原生断言。`particle_lighting`保留PD曲线/缩片原生光场检查；六个相关场景134项机制断言及画面门禁通过，1120帧零丢帧。普通拖尾逐颗透明度观察所需只读接口见[PD需求](../issues/pd-api-requests.md)。

单颗移动光源的地形刷新存在已确认PD缺陷：光场索引移动但地形烘焙缓存可能不刷新，形成旧亮区或区块截断。`light_boundary`的等价光场地形门禁失败，既有光强数值通过不能代替地形照明正确性；修复要求见[PD需求](../issues/pd-api-requests.md#移动光源的地形烘焙刷新丢失光照截断)。

## 6. 升级与验证

在PD项目 `D:/Backup/IDEA Projects/ParticleDrawing` 构建主JAR和sourcesJar；只有明确升级时复制二者并更新版本/下限，不能仅因磁盘源码版本较新就视为当前运行版本。项目内不修改PD源码或JAR绕过缺陷。

构建与录制见 [构建指南](../build-and-run.md)。`pd_api`观察原生track/Bridge/受光缓存/完成回报，`particle_lighting`检查alpha与组缩片时真实光场的过渡，阈值不因升级调整。观察器不提供替代渲染或光照。
