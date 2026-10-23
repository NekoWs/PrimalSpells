# 构建与运行

## 1. 环境

Minecraft 26.2、NeoForge 26.2.0.59、Java toolchain 25、Kotlin 2.4.10、Gradle wrapper 9.6.1。实际配置以gradle.properties/build.gradle/wrapper为准。CI使用Temurin 25并运行build。

## 2. 依赖与升级

ParticleDrawing主JAR作为compileOnly与localRuntime本地依赖，sources JAR仅供API对照。当前lib版本1.0.21-ALPHA；版本号与加载下限统一来自gradle.properties，预发布下限必须含ALPHA，例如 `[1.0.21-ALPHA,)`。

当前PD构建源码为本地提交 `55a15b5`；`build sourcesJar --offline`和`test --rerun-tasks --offline`通过，共55组/372项测试、零失败。本项目 `build --no-build-cache --offline`通过，lib文件SHA-256与PD构建产物一致，打包的依赖下限为1.0.21-ALPHA。

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

离线仅适用于依赖已缓存的机器；本机在线解析可能长时间等待，可用--offline。runClient使用run/client；runServer使用run/server并带nogui。build包含checkArchitecture源码依赖护栏；可单独运行gradlew.bat checkArchitecture。当前无单元测试源，成功不等于实机玩法和视觉已通过。

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

脚本先构建，-Offline同时传递给构建和runCapture；-SkipBuild只在确认现有类产物可用时使用。它只拥有本次Gradle/游戏子进程，监测FATAL、崩溃报告、驱动错误和超时，失败保存当前日志/部分帧并停止，不杀其他MC实例。构建失败仅引用本次build.log，不引用旧运行日志。NoVideo开关尚有 [已登记缺陷](issues/open.md#novideo开关未传递到合成流程)，不要依赖它跳过ffmpeg。

### 场景选择

| 场景 | 主要覆盖 |
|---|---|
| light_boundary | 单颗原生PD光源慢移跨section、同位置/光强重建对照；当前地形一致性门禁失败，见PD需求 |
| spell_lighting | 默认投射物目录零光源、昼夜像素不变、光修正真实付费/去重/复制/荷载隔离与退场 |
| particle_lighting / pd_api | 原生光强连续性；track/Bridge/受光缓存/完成回报 |
| spell_performance | 固定32发高速物理/制导、512普通/冻结弹联动，逐样本服务端计时与线程分配；[口径与结果](design/performance.md) |
| collision_precision | 双精度接点、24种斜反弹、单刻余程、薄形状/拼缝/跨格形状、墙角分量、命中排序、自伤豁免与真实路径联动 |
| homing_light | 前方优先/后方排除、同刻穿透换靶与接缝、64格光脊、穿刺付费冷却与十刻蓄能 |
| projectile_flight / fireball_trail | 原版弩速对比、六格扫掠追踪、复制速度、实际alpha递减 |
| black_hole / black_hole_moving / gravity_field / gravity_field_moving | 区域场原维度、移动跟随和收尾 |
| energy_shield / shield_balance / spell_chain | 护盾预算/接点/束截断/穿刺荷载/同链作用域 |
| machinegun_visual / spell_timing | 高频视觉、随机链锯、延迟当刻释放 |
| explosion / crystal_lifecycle / sawblade_falling | 爆炸外扩、临爆晶体与锯片下坠反弹 |
| expansion_* / spell_resonance / spell_mechanics | 光束/冰冻/远距、跨弹反应、刻迁移与后代 |
| recoil_light / concentrated_light_first_person / combat_impact / damage_mass | 固定聚光、后坐力、伤害/自伤与质量 |
| wand_design | 暮紫封印户外材质、下半屏外侧位置、主副手/左惯用手与真实施法 |
| wand_casting* / wand_aim / magic_hud | 主副手/FOV、阵列/模型、HUD与布局 |
| foundations / new_spells | 原编杖分色/真实V入口、抽牌控制和治疗/火把；foundations还覆盖NBT兼容/游标、注册目录与弹体生命周期顺序 |
| exploration / progression | 实际结构掉落、术士、数据重载、生活卡与罗盘 |

完整名称/帧数取CaptureScenes.all和assemble.SCENES，新场景两处一起更新。取景摄影棚用于对比，不代替自然地形/多人体验。

### 报告与判据

输出 `build/capture/out/index.html`、report.txt、各scene的meta.tsv、frames.tsv、frames/*.png、contact.png与MP4/GIF。每游戏刻抓第一渲染帧，20FPS证据不等于60/144FPS保证；高刷需原生子帧输出观察。

门禁分别检查：1280×720、声明帧数/丢帧预算、真实玩法断言、最低可见覆盖、空场/尾帧零残留、实际退场/专属alpha或光强判据、运行日志。短退场保持95%→5%跨度3～10帧、单帧损失≤35%、清零前≤10%的严格规则；不能改阈值掩盖断帧。

当前PD 1.0.21原生复验：particle_lighting 4/4、pd_api 10/10、fireball_trail 5/5，三场共302帧、0丢帧，机制与视觉门禁均通过。光照随alpha下降，组缩片退场最大单刻光强差1.875，原15→0硬切未复现。已知5条OSHI诊断按用户要求计入忽略项，不再使整体报告失败；其他日志门禁保持。所有法术入退场与短寿命高刷范围见 [verify](issues/verify.md)，其他未过门禁见 [open](issues/open.md)。

当前性能与运动回归命令、测量负载和结果统一见[性能基准](design/performance.md)。正常捕获还检查碰撞、前方追踪/穿透切线、实际法杖命中、付费/独立荷载、护盾和晶体生命周期；机制与视觉门禁分别统计，未用FPS代替服务端计时。

recoil_light在服务端延迟队列和实际墙命中回调记录蓄能/反弹，不再用客户端固定帧号猜服务端年龄。spell_resonance的接触夹具使用真实发射后重叠位置，复制夹具实际装备bounce_duplicate并在子弹首次移动前检查继承。其混合视觉退场门禁见[open](issues/open.md)。
