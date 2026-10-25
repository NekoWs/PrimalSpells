# 未解决问题

## 混合场景退场门禁

- `new_spells`机制136/136、可见性/锯片本体/零残留及日志通过，但混合效果全局50%→5%跨33帧，未达该场≤3帧的瞬时退场判据。证据在`build/capture/limited-uses-expanded-audit/new_spells`；此前同一混合场为5帧，保存在`limited-uses-initial-audit/new_spells`。需按独立退出事件检查，不以更换随机样本或放宽阈值作为修复；新增次数夹具移至foundations，避免机械验证与该场随机表现互相干扰。
- 级别：验证缺口。`machinegun_visual`机制6/6、可见性和零残留通过，但95%→5%跨度4帧、最大单帧覆盖下降42.2%，超35%阈值；归零前9.2%满足10%门槛。独立fireball_trail实际alpha检查通过。
- `spell_resonance`机制12/12通过、可见性与零残留通过，但全局50%→5%覆盖量仅跨3帧，未达4帧；证据在 `build/capture/performance-interactions-audit/spell_resonance`。需拆分真实退出事件/ROI，未改阈值或粒子寿命掩盖此问题。
- `spell_mechanics`混合多组独立生命周期，全局颜色峰值到末端的瞬退判据尚待按实际退出事件复验，不能以玩法断言通过替代视觉通过。
- `spell_timing`机制12/12、末尾粒子零残留与日志检查通过，但混合机枪覆盖量50%→5%仅跨2帧，未达4帧；证据在`build/capture/before-spell-icons/spell_timing`。该场包含移动延时载体、连续火花弹/链锯及独立长寿命切痕，需按真实退场事件定位，不放宽阈值或延长所有效果寿命。
- 复现：`pwsh tools/capture/capture.ps1 -Offline -Scene machinegun_visual,spell_mechanics,spell_resonance`。机枪当前证据保存在 `build/capture/projectile-baseline/machinegun_visual`；原帧与meta可复核，后续录制需保留当前输出再覆盖。
- 下一步：按真实退场事件分ROI或拆独立场景，定位头部/短尾/切痕各自的覆盖变化。保留严格门禁，不统一延长粒子寿命或放宽阈值凑通过。
- 位置：SpellTimingScenes、SpellQualityScenes及 `tools/capture/assemble.py`。

## 护盾主动迎弹缺少专属回归

- 级别：验证缺口。现有seenOutside与allowInsideStart处理护盾向弹体移动后起点落在壳内的情况，但没有独立时间线证明“慢弹飞行中持盾者迎向它”仍拦截。
- 下一步：真实慢弹、移动持盾者，断言反射/扣MP/零生命伤害；增加本来就在壳内生成的弹体应绕过的反例。
- 位置：EnergyShield、ShieldGuard与护盾录制场景。静态靶/破口轮廓跟随不能代替该项。

## NoVideo开关未传递到合成流程

- 级别：工具缺陷。assemble解析--no-video但仍调用视频/GIF生成；已有帧时执行 `python tools/capture/assemble.py --no-video` 可复现。
- 下一步：将开关传入analyse，跳过媒体生成及对应存在性断言，保留帧/像素/机制/日志门禁；修改前不要把该开关写成可用功能。

## 录制保存阶段的系统I/O等待

- 级别：环境/验证缺口。foundations的104项机制检查全部完成，但录制223/260帧、37丢帧，超出13帧预算；退出时保存区块持续等待数分钟，未生成成功画廊。
- 证据：`build/capture/limited-uses-shutdown-audit/`保留原帧、日志、failure.json与线程转储。服务端停在`ChunkMap.saveAllChunks → CompletableFuture.join`，区块I/O线程停在`RegionFile.write → FileDispatcherImpl.pwrite0`；没有法术结算栈。只终止了已确认属于本次录制的Capturer进程，未操作其他实例或用户存档。
- 下一步：系统存储I/O恢复后重跑`foundations`，分别核对完整帧预算与正常退出；不能把104项机制通过记作整场录制通过。
