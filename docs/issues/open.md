# 未解决问题

PD光照硬切与短生命周期曲线缺口集中在 [PD需求](pd-api-requests.md)，不重复维护状态。

## 本机OSHI计数器异常

- 级别：验证阻塞。启动MC记录Perflib 009英文计数器读取失败、JNA参数错误及OSHI当前进程userTime/virtualSize/residentSetSize空指针；日志门禁不能通过。
- 已核实：系统计数器备份/重建、WMI同步和PerfOS禁用字段类型修正已执行，新进程仍失败，WMI服务重启被Windows拒绝。用户选择保存工作后手动重启，不自动重启。
- 复现：任一capture新客户端；最近particle_lighting录制仍有5条上述异常。
- 下一步：用户重启后复验真实OSHI当前Java进程数据，再跑录制；未恢复再排查系统组件。不得过滤ERROR或篡改SystemReport。
- 工具与证据：`tools/verify/RepairPerformanceCounters.ps1`，`build/verify/oshi/backup-*`，`run/capture/logs/latest.log`。脚本需管理员且先备份，不作为常规构建步骤。

## 混合场景退场门禁

- 级别：验证缺口。`machinegun_visual`机制6/6、可见性和零残留通过，但95%→5%跨度4帧、最大单帧覆盖下降42.2%，超35%阈值；归零前9.2%满足10%门槛。独立fireball_trail实际alpha检查通过。
- `spell_mechanics`与`new_spells`混合多组独立生命周期，全局颜色峰值到末端的瞬退判据也曾失败，不能以玩法断言通过替代视觉通过。
- 复现：`pwsh tools/capture/capture.ps1 -Offline -Scene machinegun_visual,spell_mechanics,new_spells`。机枪当前证据保存在 `build/capture/projectile-baseline/machinegun_visual`；原帧与meta可复核，后续录制需保留当前输出再覆盖。
- 下一步：按真实退场事件分ROI或拆独立场景，定位头部/短尾/切痕各自的覆盖变化。保留严格门禁，不统一延长粒子寿命或放宽阈值凑通过。
- 位置：SpellTimingScenes、SpellQualityScenes、NewSpellScene及 `tools/capture/assemble.py`。

## 护盾主动迎弹缺少专属回归

- 级别：验证缺口。现有seenOutside与allowInsideStart处理护盾向弹体移动后起点落在壳内的情况，但没有独立时间线证明“慢弹飞行中持盾者迎向它”仍拦截。
- 下一步：真实慢弹、移动持盾者，断言反射/扣MP/零生命伤害；增加本来就在壳内生成的弹体应绕过的反例。
- 位置：EnergyShield、ShieldGuard与护盾录制场景。静态靶/破口轮廓跟随不能代替该项。

## NoVideo开关未传递到合成流程

- 级别：工具缺陷。assemble解析--no-video但仍调用视频/GIF生成；已有帧时执行 `python tools/capture/assemble.py --no-video` 可复现。
- 下一步：将开关传入analyse，跳过媒体生成及对应存在性断言，保留帧/像素/机制/日志门禁；修改前不要把该开关写成可用功能。
