# 构建与运行

## 1. 环境

Minecraft 26.2、NeoForge 26.2.0.59、Java toolchain 25、Kotlin 2.4.10、Gradle wrapper 9.6.1。实际配置以gradle.properties/build.gradle/wrapper为准。CI使用Temurin 25并运行build。

## 2. 依赖与升级

ParticleDrawing主JAR作为compileOnly与localRuntime本地依赖，sources JAR仅供API对照。当前lib版本1.0.21-ALPHA；版本号与加载下限统一来自gradle.properties，预发布下限必须含ALPHA，例如 `[1.0.21-ALPHA,)`。

当前PD构建源码为本地提交 `ebf3055`；`build sourcesJar --offline`和`test --rerun-tasks --offline`通过，共57组/384项测试、零失败。本项目 `build --no-build-cache --offline`通过，lib文件SHA-256与PD构建产物一致，打包的依赖下限为1.0.21-ALPHA。本次PD仍使用同一ALPHA版本号，以源码提交及JAR哈希区别构建：主JAR SHA-256为`004fece3f2e7e223b70ab175fefbcd9213d646917444e4ca457cc4f251004228`，sources JAR为`2dcb75a63c4d1cc23baf229523da8fe8271e2029c099321cf5b9d4623e0c7d29`。

PD本地项目在 `D:/Backup/IDEA Projects/ParticleDrawing`。获授权升级时在那里执行 `gradlew.bat build sourcesJar`，将匹配的主/源码JAR复制到lib，更新版本和下限，再运行本项目 `build --no-build-cache` 与相关录制。磁盘源码较新不代表运行依赖已更新。API缺陷按 [政策](design/effects-api.md#0-职责与缺陷处理) 停止登记，不能在本模组绕过。

## 3. 常用命令

在仓库根执行；Windows使用gradlew.bat，其他平台使用./gradlew。

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat build --offline --console=plain
.\gradlew.bat compileKotlin
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat gameTestServer
.\gradlew.bat runData
```

离线仅适用于依赖已缓存的机器；本机在线解析可能长时间等待，可用--offline。runClient使用run/client；runServer使用run/server并带nogui。build包含checkArchitecture源码依赖护栏和checkSilentContent内容静音护栏；可单独运行gradlew.bat checkArchitecture。当前无单元测试源，成功不等于实机玩法和视觉已通过。静音护栏用实际playSound调用反例验证拒绝，清除探针后通过；录制主音量为零，功能回归不冒充可听音轨验证。

## 4. 产物

| 路径 | 用途 |
|---|---|
| build/libs | 构建JAR |
| run/client/logs/latest.log、crash-reports | 开发客户端运行与崩溃诊断 |
| run/server | 开发服务器 |
| run/capture | 独立录制世界、配置、日志与截图 |
| build/capture/out | 原帧、meta、视频、拼图、报告、画廊 |
| build/capture/build.log | 本次录制前构建完整stdout/stderr |
| build/capture/failure.json | 录制启动/运行失败摘要 |
| src/generated/resources | 数据生成结果，按需入库 |

build与run不入库。工具重建capture目录前检查绝对路径位于工作区；不得清理run/client来代替录制世界重建。需保留结果时先将out归档到build/capture的其他目录，避免混合新旧帧。

## 5. CI

`.github/workflows/build.yml` 在push/PR执行Gradle build。构建覆盖编译、资源处理和打包；依赖客户端/世界的断言需开发录制单独执行，不把CI编译成功当效果验收。

## 6. 排障

| 现象 | 检查 |
|---|---|
| 首次依赖下载卡住 | NeoForge/Mojang仓库网络；缓存齐全后用--offline |
| Java class版本错误 | Gradle启动JVM与Java25 toolchain |
| 找不到PD或版本不匹配 | lib文件名、实际JAR版本、ALPHA下限及gradle.properties |
| damage_type缺失 | data资源是否打包/加载，run存档数据包状态；不屏蔽真实注册错误 |
| 移包后unresolved reference | package/import和 [目录规则](conventions.md) |
| 客户端启动错误界面不退出 | capture脚本监测崩溃/FATAL/超时，保留failure.json和当前日志 |
| OSHI系统信息采集异常 | 按用户要求忽略，不阻塞录制，不再要求重启或继续排障；原始日志保留 |

录制报告只豁免OSHI日志记录及已知SystemReport进程信息查询的关联异常堆栈，单独统计忽略数量。其他JNA异常、模组错误、FATAL和崩溃报告继续阻塞；不修改系统配置或游戏原始日志。分类回归可运行 `python -B tools/capture/test_log_filter.py`。

## 7. 纯数值验证

`tools/verify/CameraImpulseCheck.kt` 可调用真实CameraImpulse检查端点、连续性与限幅。几何/递推检查应编译真实实现，只为外部依赖提供最小类型替身，不复制被测算法。工具输出放build/verify。

纯数值通过不能证明PD实际曲线、发光、碰撞或网络生效；这些使用原生客户端/服务端场景。

## 8. 工作流

读路由文档和对应实现 → 改代码/当前设计 → build → 运行相关场景并保留证据 → 更新问题状态 → 检查diff并提交Git。无需为无关内容重复全场录制，但失败必须保留并明确边界。

## 9. 实机录制流水线

```powershell
pwsh tools/capture/capture.ps1 -Offline -Scene particle_lighting -TimeoutSec 600
pwsh tools/capture/capture.ps1 -Offline -Scene wand_casting,wand_aim -TimeoutSec 600
pwsh tools/capture/capture.ps1 -Offline -Scene all -TimeoutSec 1200
```

始终后台运行，PowerShell启动辅助进程使用Start-Process -WindowStyle Hidden并重定向stdout/stderr。客户端默认隐藏窗口，不抢键鼠；需要展示时显式使用runCapture的captureShowWindow属性。不要通过1×1窗口省略真实1280×720渲染。

脚本先构建，-Offline同时传递给构建和runCapture；-SkipBuild只在确认现有类产物可用时使用。它只拥有本次Gradle/游戏子进程，监测FATAL、崩溃报告、场景完成前断线、驱动错误和超时，失败保存当前日志/部分帧并停止，不杀其他MC实例。构建失败仅引用本次build.log。-NoVideo真正跳过视频/GIF与ffmpeg，但仍生成拼图和完整判定；画廊不引用未生成的媒体。

PNG由独立双线程编码，不共用原版区块I/O池，最多24帧在途（含异步GPU回读）。结束后先等实际写完并核对数量，再正常退出/保存世界；场景收尾先在服务端排空末刻检查，再向渲染线程交付不可变快照，避免并发迭代及漏掉末刻断言；日志记录written/failed/averageWriteMs，meta另记writeQueueDropped。-FinishTimeoutSec默认60，分别限制PNG排空与游戏保存退出的等待；超时用本次日志的客户端PID及启动时间验证身份，最多5秒采集threads.txt后，仅结束本次进程树。不要通过禁存档或强制成功退出绕过保存问题。

### 场景选择

| 场景 | 主要覆盖 |
|---|---|
| spell_icons | 完整78张原生16px贴图按三页展示，真实物品栏/快捷栏与清空后恢复；背包三排暖色ROI排除手臂、饥饿条与角色预览 |
| light_boundary | 单颗原生PD光源慢移跨section、同位置/光强重建对照；检查当前地形照明与光场一致、结束无残留 |
| spell_lighting | 默认投射物目录零光源、昼夜像素不变、光修正真实付费/去重/复制/荷载隔离与退场 |
| particle_lighting / pd_api | 原生光强连续性；track/Bridge/受光缓存/完成回报 |
| spell_performance | 固定32发高速物理/制导、512普通/冻结弹联动，逐样本服务端计时与线程分配；[口径与结果](design/performance.md) |
| explosion_performance | 三次单发与同刻八发真实爆炸，创建耗时/分配、原生数量与随机位置签名、零光源/双端清理，保留JFR；[口径与结果](design/performance.md#爆炸表现) |
| collision_precision | 原版可命中契约（掉落物/经验球/交互过滤、荷载不消费、物品展示框正例）、双精度接点、24种斜反弹、单刻余程、薄形状/拼缝/跨格形状、墙角分量、命中排序、自伤豁免与真实路径联动 |
| piercing_contact | 木桩接触计时、刻末/薄箱/墙排序；DamageFrequencyChecks观察AfterImpact，覆盖聚光高速/反弹、锯片附加爆炸、束/场逐刻上限、多目标和复制体预算 |
| homing_light | 前方优先/后方排除、同刻穿透换靶与接缝、64格光脊、穿刺付费冷却与十刻蓄能 |
| projectile_flight / fireball_trail | 原版弩速对比、六格扫掠追踪、复制速度、实际alpha递减 |
| black_hole / black_hole_moving / gravity_field / gravity_field_moving | 区域场原维度、移动跟随和收尾 |
| energy_shield / shield_balance / spell_chain | 护盾预算/接点/束截断/穿刺荷载/同链作用域；TriggerLifecycleChecks覆盖每次生物接触的独立荷载、副本/费用隔离及墙/寿命排除，定时与失效仍消费一次 |
| machinegun_visual / spell_timing | 高频视觉、随机链锯、延迟当刻释放 |
| void_phase | 真实持杖85t虚空、双端锁位/位置包纠正、法阵完全隐藏与结束后重建 |
| explosion / crystal_lifecycle / sawblade_falling | 爆炸外扩、临爆晶体与锯片下坠反弹 |
| expansion_* / spell_resonance / spell_mechanics | 光束/冰冻/远距、跨弹反应、刻迁移与后代 |
| death_attribution | 十种渠道的施法者/自伤/未知来源文本、原生战斗记录、真实玩家死亡界面及正常重生 |
| recoil_light / concentrated_light_first_person / combat_impact / damage_mass | 固定聚光、后坐力、伤害/自伤与质量 |
| wand_design | 暮紫封印户外材质、下半屏外侧位置、主副手/左惯用手与真实施法 |
| wand_casting* / wand_aim / magic_hud | 主副手/FOV、阵列/模型、HUD布局；magic_hud还覆盖32t真实连续伤害的去重续播/同刻转击杀、逐帧反馈与有限退场 |
| foundations / new_spells | 原编杖分色/真实V入口、引用/刷新/生命付款/虚空、抽牌控制和治疗/火把；foundations还覆盖NBT兼容/游标、注册目录与弹体生命周期顺序 |
| exploration / progression | 实际结构掉落、术士、数据重载、生活卡与罗盘 |

完整名称/帧数取CaptureScenes.all和assemble.SCENES，新场景两处一起更新。取景摄影棚用于对比，不代替自然地形/多人体验。

### 报告与判据

输出 `build/capture/out/index.html`、report.txt、各scene的meta.tsv、frames.tsv、frames/*.png、contact.png与MP4/GIF。每游戏刻抓第一渲染帧，20FPS证据不等于60/144FPS保证；高刷需原生子帧输出观察。

门禁分别检查：1280×720、声明帧数/丢帧预算、真实玩法断言、最低可见覆盖、空场/尾帧零残留、实际退场/专属alpha或光强判据、运行日志。短退场保持95%→5%跨度3～10帧、单帧损失≤35%、清零前≤10%的严格规则；不能改阈值掩盖断帧。

当前PD原生复验：light_boundary、spell_lighting、homing_light、particle_lighting、projectile_flight、pd_api、fireball_trail七场78项机制检查、982帧、0丢帧，机制与视觉门禁均通过。等价光场地形RGB差>5的像素占比为0.00%（要求≤1%）；默认零光源拖尾通过普通粒子只读visitor恢复逐颗alpha检查。已知5条OSHI诊断按用户要求计入忽略项，其他日志门禁保持。预算压力、多人与1～2t高刷范围仍见 [verify](issues/verify.md)，其他未过门禁见 [open](issues/open.md)。

实体筛选回归：collision_precision、homing_light、spell_chain、shield_balance四场209项机制检查、940帧零丢帧，画面/日志门禁通过。EntityHitChecks实际生成掉落物、经验球、药水云、交互/文本展示实体及物品展示框，检查真实扫掠、命中次数和触发荷载；不靠实体类型黑名单或仅验证筛选函数。

命中与荷载回归：`pwsh tools/capture/capture.ps1 -Offline -Scene piercing_contact,collision_precision,homing_light,spell_chain,shield_balance,foundations,new_spells,expansion_combinations,expansion_utilities -TimeoutSec 600`。九场分场复验汇总406项机制检查、1920帧零丢帧，机制、画面及日志门禁通过。piercing_contact的51项检查直接读取木桩totalDamage、弹体hitTargets和正式AfterImpact，覆盖接触计时、速度/入射相位、薄箱/墙排序、聚光4～128格/t、锯片附加效果、等离子束逐刻预算及独立复制体。穿刺减伤后的5HP计时夹具用真实增伤卡构造；不改变生产数值来维持旧断言。

spell_chain的TriggerLifecycleChecks读取实际新生子弹，覆盖两类命中触发连续穿过两个目标、每次接触的新副本、无直接伤害仍触发、单次收费、模板/嵌套配方不污染、墙/寿命排除和定时/失效单次消费。foundations按真实悬停逐刻轮询异步分色；new_spells同时设定夹具MP上限与当前MP，在清场前检查复制，锯片画面ROI覆盖真实主体而非碎屑。水晶/远距组合检查真实生物触发及HitResult方向；有限斥力夹具比较同速慢弹与箭，不要求高速弹被瞬间反射。证据按场保存在`build/capture/repeat-trigger-fixture-audit`（四场频率/碰撞/追踪/护盾通过部分）、`repeat-trigger-verified`（spell_chain与foundations）、`repeat-trigger-combinations-verified`（new_spells与expansion_combinations通过部分）及`repeat-trigger-utilities-verified/expansion_utilities`。旧夹具失败原始证据保留，不混作通过场；其它尚未通过门禁见[未解决问题](issues/open.md)。

当前性能与运动回归命令、测量负载和结果统一见[性能基准](design/performance.md)。正常捕获还检查碰撞、前方追踪/穿透切线、实际法杖命中、付费/独立荷载、护盾和晶体生命周期；机制与视觉门禁分别统计，未用FPS代替服务端计时。

recoil_light在服务端延迟队列和实际墙命中回调记录蓄能/反弹，不再用客户端固定帧号猜服务端年龄。spell_resonance的接触夹具使用真实发射后重叠位置，复制夹具实际装备bounce_duplicate并在子弹首次移动前检查继承。其混合视觉退场门禁见[open](issues/open.md)。

引用与实用法术覆盖78卡注册/贴图/来源、只读引用预览与NBT、免费复制与荷载隔离、有限展开、刷新计数、诅咒HP/真实死亡、虚空同步与60t恢复、区域力豁免；原有护盾与触发回归一并保留。通过证据按场保存在`build/capture/reference-spells-suite-verified/`、`reference-spells-fields-verified/`及`before-limited-uses/`。纯资源检查确认78张16px硬透明图标主体唯一、模型/双语键齐全，生成器像素幂等。

锯片/虚空与PD依赖复验：先执行`pwsh tools/capture/capture.ps1 -Offline -Scene new_spells,void_phase,sawblade_falling -TimeoutSec 300 -FinishTimeoutSec 45`，再独立执行`-Scene foundations,spell_lighting,pd_api`。六场259项机制检查、1090帧零丢帧，画面/日志门禁全过，PNG全部写完后正常保存退出。锯片保留出生曲线和8t resize，同UUID原生面积95%→5%跨7t、最大单步下降20.98%、清零前0.028%；虚空场景验证85t实际施法、双端锁位、真实位置包纠正、法阵零残留与到期恢复，普通放下法杖仍按现有短退场门禁检查。证据分别在`build/capture/void-saw-verified/`及`build/capture/void-pd-regression-verified/`。使用`-NoVideo`仍保留全分辨率原帧、拼图和完整门禁，报告不引用未生成媒体。

工具边界验证：`python -B tools/capture/test_capture_report.py`（2组）、`python -B tools/capture/test_log_filter.py`（5组）、`pwsh tools/capture/test_diagnostics.ps1`（9项）。覆盖缺失/重复/失败退场证据、NoVideo不调用编码器/不引用媒体、固定列解析、PNG/保存阶段、提前断线与失败日志检测。
