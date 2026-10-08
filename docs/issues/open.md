# 未解决的问题（open）

> 只放**现在还没解决**的东西。修好就删掉，结论写进 [resolved.md](resolved.md)（规则见 [README.md](README.md)）。
> 严重度：🔴 崩溃/数据损坏/明显错误玩法 ｜ 🟠 影响体验或可维护性 ｜ 🟡 清洁度问题。

---

## 1. 待继续开发的功能

### 🟠 1.1 能量护盾 `EnergyShield`（第 26 轮按新口径重做）

> **当前口径（第 36/39/40 轮收敛后）**：真正的护盾**始终跟随玩家** —— 判定与球心都用持有者**实时位置**；
> 不跟人的只有**白色碎片 / 格挡玻璃**（每次受击盖章在玩家当时的位置）；
> **粉色破口轮廓跟随持有者**（第 40 轮用户口径：破口方向是相对玩家算的，轮廓留在原地就会与真正的破口错位）。
> 结转（非缺陷）：碎片是"盖章"式的，玩家快速移动时会看到一串旧碎片；要不要让它们更快淡出由手感定。
> 数值口径（强度 / 抗性 / 窗口预算 / 破口时长 / 原版投射物）全文见
> [design/combat-pricing.md](../design/combat-pricing.md) §3–§4.98。

- 现状：**第 26 轮已按下列新口径实现**；表现（受击玻璃 / 打穿碎裂 / 粉色破口）见 [architecture.md](../architecture.md) §9，
  录制产物在 `build/capture/out/energy_shield`。
- **第 26 轮新口径（已定稿，全文见 [design/combat-pricing.md](../design/combat-pricing.md) §3–§4）**：
  删除耐久 / 破盾冻结 / 回复 / HUD 条；只要装卡护盾就**常驻展开**，受击按「强度 vs 抗性」结算 MP；
  抗性 = 基础 2 + 每张卡 2；消耗 = 基础反弹 5 MP + 超出抗性每点 5 MP（固定值，等法杖分等级再重调）；
  挡不住 → 投射物照常命中持有者，并在受击方向「一片 + 相邻一圈」碎裂、该方向 3 秒内不挡（**定向破口**），
  破口期间那一圈显示淡红色轮廓；穿透弹完全无视护盾；爆炸按「中心在内侧不触发 / 外侧按衰减伤害 × 半径抵扣 / 只替持有者吃」。
- 仍缺口 / 已明确搁置：
  1. 它是被动法术却继承 `Modifier`、放在 `magic/modifier/`（**你已明确暂不处理**）；
  2. 法力定价属于「法杖像 Noita 那样分等级」那一轮——现在池 3000 / 回复 1000 每秒，任何 MP 消耗都不可达。

### 🟠 1.2 法杖的自然获取途径（第 27 轮只做了"拿得到"）

- 现状：六级法杖（见 [design/wand-tiers.md](../design/wand-tiers.md)）只能从**创造模式物品栏**或
  `/primalspells wand <等级> [charged]` 获得；世界里的箱子**不会**出法杖，也没有合成配方。
- 待做（用户口径：**下一轮**）：用 NeoForge 的 `LootTableEvents.Modify` 往地牢/废弃矿井/村庄/要塞/末地城等
  箱子按等级加权注入，"刷新点越高级越偏向高等级"；蓄能型变体另算权重。
- 好消息：数值在**创建时**掷出（`WandStats.roll`），所以掉落只需要决定"哪个物品 + 变体组件"，
  战利品表里不用写任何一个数。

### 🟡 1.3 「持盾迎向飞来的投射物」还缺一条场景断言（第 38 轮修复的遗留）

- 第 38 轮修的是「持盾朝攻击者走过去会被打中」：球心跟着持有者走，**壳会"迎着"弹扩张**，
  弹在某一 tick 直接落到壳内 ⇒ `shellEntry` 的「起点在壳外」前提不成立、整发不挡。
  修法是 `seenOutside`（曾在壳外出现过的弹照挡）+ `shellEntry(allowInsideStart = seen)`。
- **当时的证据只到「改动无回归 + 逻辑对口」**：录制场景用的是**静止木桩**，没有"木桩朝飞来的火球移动"的时间线。
  （第 40 轮加的「破口期内走 3 格」是另一件事：那一段验的是红色轮廓跟随，第 40 轮台账 §40.6。）
- 待做：给 `energy_shield` 加一段 —— 先朝壳外放一发**慢速**弹，**在它飞的途中把木桩朝它移动**，
  断言这一发仍被挡下（法力被扣、伤害数字为 0）；顺带覆盖反例「本来就生成在壳里的法术不挡」（设计口径）。
- 相关口径：`EnergyShield.seenOutside` / `seenOutsideTtl`、[design/combat-pricing.md](../design/combat-pricing.md) §4「两个入口」。

---

## 2. 性能与表现

| 严重度 | 问题 | 位置 | 说明 / 建议 |
|---|---|---|---|
| 🟢 | `EnergyShield` 无常驻粒子，受击实色晶格与裂纹；第54轮完整填充与独立剥落碎片，爆炸按影响范围选片，固定轮廓按飘散位移合并组，碎片独立受力 | `magic/modifier/EnergyShield.kt` | 稳态0粒子/0包；当前MC视觉以 [design/minecraft-visuals.md](../design/minecraft-visuals.md) 为准，不再追求半透明玻璃 |
| 🟡 | **黑洞范围内的「持续轻抖」强度还没实机调过**（第 20 轮收敛后只剩这一处镜头冲击） | `BlackHole.kt` 的 `RUMBLE_MIN`/`RUMBLE_RANGE`、`client/ScreenShakeClient.kt` 的 `MAX_*_DEG` | 现在 0.05~0.13 振幅（越靠球心略强）+ 角度上限 2.6°/1.8°/1.4°：实机站在黑洞里会不会嫌晃、还是根本感觉不到，需要人上手。调参入口就是这两个常量；玩家侧可用原版「受伤视角晃动」一键关掉 |
| 🟡 | `Move` 逐 1/16 格步进（`Laser` speed=5 → 单帧最多 80 步） | `magic/effect/geometry/Move.kt` | 逐步进是**刻意**的：为了拿到精确的接触点与接触面（方块碰撞箱 + 面法线）。已完成一次优化：实体查询由「每步一次」降为「每 tick 一次宽相位 + 子步进窄相位」；方块仍逐步检测，若日后仍嫌重可改射线求交（会牺牲面精度） |
| 🟡 | `EnergyShield` 每 tick 遍历全场投射物 | `magic/modifier/EnergyShield.kt` | 剪枝只剩「场上无投射物直接返回」：第 26 轮的扫掠判定要看**线段**，原来的「到玩家中心距离球」粗筛（按点距离）会漏判，已删除。法术数量上量级时可考虑空间分桶/网格 |
| 🟡 | 三个**当前无使用者**的扩展点：`Magic.onPassiveIdle`、`Magic.saveRuntimeState`/`loadRuntimeState`（+ `Wand.magicStates`）、`Magic.onUnloaded` 的既有用例 | `magic/core/Magic.kt`、`wand/Wand.kt` | 第 26 轮护盾重做后它们都没有使用者了（护盾不再有常驻球壳、也不再需要跨存档进度）。机制本身是通用钩子、文档齐全，**保留**待将来「有进度的法术 / 放下后收尾」；清理与否待定 |

> 自转/淡出这类**组级编排**都用 `ParticleGroup`（客户端求值、零逐 tick 包），与 `ParticleBatch` 的分工见
> [design/effects-api.md §0.2](../design/effects-api.md)。

## 3. 本机验证环境

### 🟡 3.1 OSHI 性能计数器异常导致录制总体报告失败（2026-10-08）

- 现象：本机启动 MC 时记录 `Unable to locate English counter names in registry Perflib 009`，
  OSHI 查询进程 userTime / virtualSize / residentSetSize 时又有空指针；特效场景断言均过，总体仍失败。
- 复现：`pwsh tools/capture/capture.ps1 -Scene black_hole,gravity_field`；本轮修改前的基线也出现相同日志。
- 影响：无法宣称录制流水线整体通过；第49轮两场及第52轮五场均1280×720、零丢帧，末尾PD粒子数为零，视觉判据通过。
- 涉及：Windows Perflib/OSHI，`run/capture/logs/latest.log`、`tools/capture/assemble.py` 的日志门禁。
- 2026-10-09排查：OSHI 6.9.0读取虚拟注册表`Perflib\009\Counter`返回Windows错误87（参数错误）；该语言键由Windows动态提供，不能用PowerShell的`Test-Path/Get-Item`判为“文件/键不存在”。直接WMI能查询当前进程；OSHI注册表/PDH路径只返回约128个进程，而WMI/WTS约410个，缺少当前Java PID，导致MC的SystemReport连续3次空指针。
- 已执行：管理员权限下备份Perflib/PerfOS与原始PerfStringBackup.INI；按微软流程重建64/32位计数器、从本机原始备份恢复、同步WMI。PerfOS的禁用字段原为字符串“0”，已规范为DWORD 0。相关备份和逐次结果在`build/verify/oshi/backup-*`。
- 复查仍失败：英文表读取错误和OSHI当前进程null未恢复；WMI服务重启被Windows拒绝，服务保持运行。不能把命令返回0当成修复完成。
- 可审查脚本：`tools/verify/RepairPerformanceCounters.ps1`（必须管理员；先备份、只规范数值0，不关闭计数器；可选服务重启，失败也恢复原先运行的依赖服务）。官方依据见build-and-run的OSHI小节。
- 用户已选择稍后保存工作并手动重启，本轮不自动重启。修复尝试后的63d新客户端仍记录5条OSHI异常，26项法术机制断言全过。下一步：重启后重新验证真实OSHI进程数据和runCapture；仍失败则继续系统计数器组件排障。未过滤异常、关闭日志门禁或篡改SystemReport。

### 🟡 3.2 录制装配器的NoVideo参数未生效（2026-10-08）

- 现象：assemble.py解析 `--no-video` 后未读取该值，依然运行ffmpeg。
- 复现：已有录制帧时执行 `python tools/capture/assemble.py --no-video`，静态调用路径仍进入build_video/build_gif。
- 影响：无法用文档声明的只判定/拼图模式节省装配时间；本轮使用默认视频模式，视频证据不受影响。
- 涉及：`tools/capture/assemble.py`、`tools/capture/capture.ps1` 的NoVideo开关。
- 下一步：将开关传入analyse并跳过视频/动图生成与对应断言，保留全部帧、像素、玩法及日志门禁。


## 第62轮：组视觉尺寸/变量仍20Hz求值

1.0.15-ALPHA解决当前倍率与移动样本等5项历史主要接入点，但组视觉尺寸/表达式变量依然在PlayerTick 20Hz求值，严格高刷连续性尚未实现。
已核对源码和第三人称收尾PNG，见pd-api-requests的“组动画及热更变量的视觉尺寸逐渲染帧求值”。本侧保留原生动画，增加有限销毁余量，不复制前置引擎。
