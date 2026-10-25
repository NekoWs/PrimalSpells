# 修复结论与回归入口

仅保留当前仍适用的结论。旧实现、逐次过程、过期录制统计和已撤销方案查Git，不在这里与当前规则并列。未通过的门禁仍留在 [open](open.md)，不能作为修复结论。

| 主题 | 当前保证 | 回归入口 |
|---|---|---|
| 实体卡次数 | 同名卡各自计数，普通施放扣原卡；玩法复制免次数且可复制耗尽卡，预览/拒绝施法不扣；装卸/小包/存档保留次数，移除旧ID不串卡；回洗与刷新不补满 | foundations的LimitedUseChecks与真实预览/组件同步 |
| 引用与重装填 | 实体牌与副本游标隔离；引用/追加荷载保持独立费用与有界展开；刷新即时重装并从杖首续抽，第二次正常充能；只免本次刷新前的次数，仓库不被普通抽取，预览与NBT保持一致 | new_spells的ReferenceSpellChecks、RefreshSpellChecks；foundations、spell_chain |
| 诅咒付款与虚空 | 血魔法按接受卡支付不可减免HP；虚空默认60t且只受本链寿命修正，双端锁位、位置包纠正并隐藏法阵；普通及特殊原版锁定入口排除虚空目标，到期恢复移动/可见性 | new_spells的ReferenceSpellChecks、VoidSpellChecks、VoidMechanicChecks；void_phase、death_attribution |
| 世界运行时清理 | 停服同时清空在场与待加入弹体、冻结和延迟动作，旧队列不进入下一个世界 | foundations的ArchitectureChecks |
| 每刻性能 | 按需积分、同格邻域复用、无数组slab、反应分类配对；已加载区块查询避免同步生成，保留接点/曲线/伤害语义 | spell_performance、collision_precision；[基准](../design/performance.md) |
| 爆炸表现开销 | 大爆炸从约两千片改为最多177片相关火舌/余烬/烟团，共享不可变曲线；三种独立随机轮廓，原生批次与有限外扩退场，零默认光源，保留当刻伤害与晶体引爆 | explosion_performance的JFR/分配/数量/位置签名，explosion、crystal_lifecycle、spell_lighting；[实测](../design/performance.md#爆炸表现) |
| 模组静音 | 清除追踪/复制/爆炸/弹跳/传送/施法者/假人放置主动音效，自有实体始终静音；源码与声音资源护栏禁止重新引入 | checkSilentContent（含违规调用反例）、homing_light、spell_chain、exploration |
| 抽牌作用域 | 同链修正无牌序；触发荷载独立，子链散射不污染载体；付费一次、链锯清整次延迟 | spell_chain、foundations |
| 复制与后代 | 按配方重建独立effect/荷载，抵消重复速度倍率，量子复制有本体与真实命中 | spell_mechanics、spell_resonance |
| 寿命触发 | 定时到期当刻释放；休眠水晶到期爆炸，提前死亡与expire区分 | spell_timing、crystal_lifecycle |
| 命中触发与独立荷载 | 普通/追加命中触发随每次有效生物接触复制独立模板，穿刺重复命中不重新扣蓝、不叠加模板修正；墙/寿命/死亡不补发，定时/失效仍消费一次 | spell_chain的TriggerLifecycleChecks、new_spells、expansion_combinations、expansion_utilities |
| 命中对象 | 使用原版可被投射物命中的能力判定；掉落物/经验球/效果云/Display展示实体/交互实体不阻挡弹体或消费荷载，正常生物与可射击的物品展示框仍命中；索敌/束/准星一致 | collision_precision的EntityHitChecks、homing_light、spell_chain、shield_balance |
| 碰撞与反射 | 出生豁免不误伤、返弹自伤保留；双精度世界位置，解析扫掠薄形状/拼缝并按接点排序；斜墙/墙角不嵌入，同刻多次反弹走完余时；光束保留折点，水晶联动读取实际路径 | collision_precision、spell_chain、shield_balance |
| 等离子束 | 默认静态，本体随位置；有效射线在墙/护盾截断，视觉与伤害共用截点，允许自伤 | expansion_plasma、spell_chain |
| 追踪与弹速 | 扫掠预计飞行段找六格侧向范围内的目标中心，前方对准度优先，排除后方回锁；穿透后当刻沿当前切线渐入新目标的曲率，不抑制重复伤害；真实子步与光束共用路径 | homing_light、projectile_flight |
| 汇聚之光 | 原地锁定方向蓄能10t，释放后64格/t连续光脊；装“光”时本体照明有限退场，保留真实5次墙反弹 | homing_light、recoil_light、concentrated_light_first_person |
| 穿刺代价 | 费用及投射物渠道减伤遵循[战斗规则](../design/combat-pricing.md#3-资源与节奏)，不增加双冷却；与增伤相加且牌序无关，直接伤害≤0仍能触发，治疗/能量/切割不受该减值影响 | homing_light、spell_chain、piercing_contact |
| 穿刺接触次数 | 消除按空间子步重复伤害；首次立即命中，之后按1/16刻实际接触时间结算，默认水平穿木桩4次，提速更少；保留跨刻、重新进入、同刻反弹、穿刺荷载与锯片逐刻伤害，刻末首次进入不遗漏/双算 | piercing_contact接触计时检查、collision_precision、homing_light、spell_chain、shield_balance |
| 帧伤与命中修正 | 锯片同目标同刻的全部实体命中回调共用一次预算，直接伤害去重不能放任易爆重复；等离子束和黑洞显式限制同目标同刻直接伤害；不同目标/复制体独立，普通穿刺与真实墙反弹保留各自频率 | piercing_contact的DamageFrequencyChecks（32项）、spell_chain、shield_balance、damage_mass |
| MP与存档 | 当前MP封顶；空槽/牌堆保序；完整刻迁移旧秒/毫秒；复制不共享wandId | spell_mechanics、exploration、magic_hud |
| 伤害 | 独立分类；原版死亡界面/聊天显示真实施法者、自伤明确、未知来源不冒用旧攻击者；中英文文案贴近原版；正治疗恢复、负治疗伤害；强度击退与假人免击退 | death_attribution、damage_mass、combat_impact、new_spells |
| 多卡护盾 | 卡数提高抗性与立方窗口预算，破盾当次仍吸收；无常驻壳和多余圆环 | shield_balance |
| 黑洞 | 高质量互吸缓慢、静态源不碰撞、原维度持续结算，创造飞行免吸 | damage_mass、spell_stability |
| 晶体 | 外向晶面、多角可读、临爆加速白闪和爆碎 | crystal_lifecycle、sawblade_falling |
| 地形光照截断 | PD累计慢移位移、停住后精确刷新，脏section跨帧排队；同位置/光强重建前后地形一致、退场无残光 | light_boundary |
| 普通粒子观察 | PD只读visitor包含零光源全亮粒子，按UUID保存实际alpha数值，恢复真实拖尾淡出观察 | fireball_trail、projectile_flight |
| 法术环境照明 | 默认片元全亮/零光源；光修正每弹一源，重复卡去重、复制继承、荷载独立，2t升亮/4t熄灭；火把保留专用照明 | spell_lighting、particle_lighting |
| 持杖与模型 | 暮紫封印倒角折臂/错落厚封印片/切面紫晶，重复OBJ零件唯一组名避免覆盖；低位持握，原生idle/施法余光；共用尺寸校准出生点，长按不减速、成功使用不重举 | wand_design、wand_casting、wand_aim |
| 脚下法阵 | 实时跟实体、独立慢自转；透明出生，PD首次接管原子同步，正常退场等原生完成 | pd_api、wand_casting |
| HUD | 尊重原版隐藏界面；命中正十字、击杀斜十字，同一目标去重并以1t最小间隔续播，多目标可重叠；0.8t运动短尾交接，击杀打断保留全部尾层，位置与历史不累积；小数位描线、准星留空、受击方向及布局拖动缩放 | magic_hud、HudImpactChecks；32t真实连击每帧反馈持续可见 |
| 原编杖 | 原容器滚轮/右键，V只读分色；创造专用包校验，生存原版协议 | foundations |
| 获取与整合 | 原结构箱子追加、真实持杖敌人原物掉落、池/等级原子重载、不重掷旧杖 | exploration、progression |
| 录制写盘与诊断 | 双线程PNG编码、24帧在途上限；帧完成后正常保存退出；末刻检查在服务端完成后交付不可变快照，不跨线程迭代可变列表；场景完成前断线立即记录失败；收尾分阶段超时，只诊断/结束本次进程 | 六场1090帧、259项检查零丢帧通过；test_diagnostics.ps1的9项边界检查 |
| 录制报告与媒体 | NoVideo真正跳过视频/GIF编码与媒体引用，保留原帧/拼图/机制/像素门禁；按固定列解析检查名，缺少指定帧明确失败而不抛KeyError | test_capture_report.py、test_log_filter.py |
| 火把退场 | presence六刻缓入缓出后回收；光强最大单刻下降26.82%，95%→5%跨5刻，清零前5.61%，保留14级照明与零残留 | new_spells的SpellExitChecks |
| 抽牌与组合夹具 | MP上限与当前MP一致；在配方清场前验证复制；按真实悬停持续轮询异步分色；生物触发检查真实子弹与HitResult方向；锯盘ROI对准主体且保留原阈值，有限斥力使用同速慢弹/箭并在存活窗口观察 | foundations、new_spells、expansion_combinations、expansion_utilities |
| OSHI处理 | 按用户要求忽略系统信息采集记录及其已知关联堆栈，保留原日志，不作为待修或待重启项；其他错误/FATAL照常阻塞 | assemble.classify_log_errors、test_log_filter.py |

## 锯片连续退场

PD `ebf3055`修复运行期resize与SCALE寿命曲线的组合：取当前缓动尺寸后乘曲线，不再首刻跳至目标0。PS继续使用原有出生曲线、径向运动和8t resize，不删曲线、不重生碎片、不自管插值。`new_spells`同UUID原生面积95%→5%跨7t，最大单刻损失20.98%、清零前0.028%，均通过原阈值；`sawblade_falling`画面、`spell_lighting`与`pd_api`复验通过。证据见[构建指南](../build-and-run.md#9-实机录制流水线)。

## 已接入的PD能力

ParticleBatch批量设速/施力、实体锚点、运行时Emitter、Movable相邻样本、spawnAll与寿命曲线已使用。原生Bridge负责跟随/外观插值；首次程序接管初始化两端，热更变量终点可参与完成回报，法阵和等离子退场可retire。

PD提供LIGHT、静态光强乘数、从当前值接续热更、alpha/尺寸照明包络与分数年龄求值；累计地形刷新和待刷新section队列避免慢移漏刷，forEachParticleSample只读观察普通粒子。light_boundary、spell_lighting、homing_light、particle_lighting、projectile_flight、pd_api、fireball_trail七场78项机制检查、982帧零丢帧，原阈值下画面门禁通过；预算压力、多人和1～2t高刷范围仍在verify。

## 文档维护

目录说明改为当前机制/入口，移除轮次叙述、重复历史数值、旧方案和过时已解决状态；Git保留完整改动记录。后续在本表按主题更新结论，不追加开发流水账。
