# 未解决问题

## 旧录制夹具的法力上限

- new_spells仍用10000当前MP、3000最大MP构造夹具，支付正确封顶后，4条旧费用断言错误地期待99xx余额，实际为29xx。
- 下一步：将需要10000MP的夹具同时设定10000最大MP，再跑实际抽牌/收费；不要放开生产环境MP封顶规则。初始证据在 `build/capture/aim-first-pass/new_spells`。

## 混合场景退场门禁

- 级别：验证缺口。`machinegun_visual`机制6/6、可见性和零残留通过，但95%→5%跨度4帧、最大单帧覆盖下降42.2%，超35%阈值；归零前9.2%满足10%门槛。独立fireball_trail实际alpha检查通过。
- `spell_resonance`机制12/12通过、可见性与零残留通过，但全局50%→5%覆盖量仅跨3帧，未达4帧；证据在 `build/capture/performance-interactions-audit/spell_resonance`。需拆分真实退出事件/ROI，未改阈值或粒子寿命掩盖此问题。
- `spell_mechanics`与`new_spells`混合多组独立生命周期，全局颜色峰值到末端的瞬退判据也曾失败，不能以玩法断言通过替代视觉通过。
- 复现：`pwsh tools/capture/capture.ps1 -Offline -Scene machinegun_visual,spell_mechanics,new_spells,spell_resonance`。机枪当前证据保存在 `build/capture/projectile-baseline/machinegun_visual`；原帧与meta可复核，后续录制需保留当前输出再覆盖。
- 下一步：按真实退场事件分ROI或拆独立场景，定位头部/短尾/切痕各自的覆盖变化。保留严格门禁，不统一延长粒子寿命或放宽阈值凑通过。
- 位置：SpellTimingScenes、SpellQualityScenes、NewSpellScene及 `tools/capture/assemble.py`。

## 护盾主动迎弹缺少专属回归

- 级别：验证缺口。现有seenOutside与allowInsideStart处理护盾向弹体移动后起点落在壳内的情况，但没有独立时间线证明“慢弹飞行中持盾者迎向它”仍拦截。
- 下一步：真实慢弹、移动持盾者，断言反射/扣MP/零生命伤害；增加本来就在壳内生成的弹体应绕过的反例。
- 位置：EnergyShield、ShieldGuard与护盾录制场景。静态靶/破口轮廓跟随不能代替该项。

## NoVideo开关未传递到合成流程

- 级别：工具缺陷。assemble解析--no-video但仍调用视频/GIF生成；已有帧时执行 `python tools/capture/assemble.py --no-video` 可复现。
- 下一步：将开关传入analyse，跳过媒体生成及对应存在性断言，保留帧/像素/机制/日志门禁；修改前不要把该开关写成可用功能。
