# 未解决问题

## 发光效果完整接入

- 级别：应用层待完善。PD 1.0.21已支持光强过渡，particle_lighting的4项原生检查通过；已有alpha/尺寸退场可自动带动照明。
- 汇聚之光已显式注册主芯光源，蓄能、光束和释放余光声明LIGHT曲线，中断/正常释放衔接熄灭。尚未覆盖所有法术：固定尺寸且没有曲线的发光粒子、直接remove的正常结束路径，需要逐项声明出生/退场光强或已有视觉包络；不能以升级依赖代替完整改造。
- 下一步：按PdParticles、PixelVfx及各法术/护盾/火把的真实生命周期补齐声明，验证移动、重复触发和收尾；使用PD现有LIGHT/lightIntensity接口，不自建光照系统。范围见 [verify](verify.md)。

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
