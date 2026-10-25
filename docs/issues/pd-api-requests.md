# ParticleDrawing API 修改需求

运行依赖为1.0.21-ALPHA，主JAR与sources JAR来自同一构建，源码提交/哈希见[构建指南](../build-and-run.md#2-依赖与升级)。

## 运行期resize与SCALE曲线叠加时直接跳到目标尺寸

- 状态：已确认，停止该缩放接入点的改写；PS不以删除出生曲线、重生碎片、改用另一套动画或自管尺寸插值绕过。
- 目标：片元保持当前尺寸开始退场，在指定8t内连续缩到0，保留独立径向运动及各向异性外观。
- 最小复现：`spellParticleSpec().visual(spellVisual().anisoWorld(0.109375f, 0.109375f)).lifetime(-1).sizeCurve(CurveKey.at(0,0), CurveKey.at(1.5,1,EASE_OUT))`生成后正常track；稳定存在后调用`handle.setVelocity(radial).resize(0f,8,LINEAR)`。取同一UUID的原生`forEachParticleSample`并观察真实画面。
- 证据：`new_spells`的`SpellExitChecks`在真实锯片死亡时冻结同批UUID，读取有效尺寸×alpha。面积从38.2775在下一刻降到3.6075（下降90.58%，要求≤35%），其余尾迹继续正常退场。完整原帧/曲线在`build/capture/exit-io-verified/new_spells/`；该次录制440/440帧、无丢帧、无阻塞日志，排除了写盘丢帧伪造断点。
- 源码：当前sources JAR的`RenderParticle.setTargetColorScale`在设置缓动起点后立即`setScaleScalar(target)`；`ClientParticleEngine.syncAppearance/frameSyncCurves`对含SCALE曲线的片元读取`scaleArray × curveMultiplier`，因此直接使用目标0，而非本刻缓动值。出生SCALE曲线已进入常数1阶段仍存在，会继续走该分支。
- API建议：保留`ParticleHandle.resize(scale,durationTicks,easing)`签名，修复底层基准尺寸与缓动取样；目标值只在动画结束落地，SCALE寿命曲线应乘当前缓动尺寸。`forEachParticleSample`与Bridge上屏尺寸需使用相同分数刻采样。
- 影响范围：`SawbladeFx.onDie`与共享`PixelVfx.retireBatch`；`SpellLightFx`也使用resize，需同时回归非曲线/带曲线、标量/各向异性组合，不能仅修锯片特例。
- 验收：最小复现覆盖无曲线、出生曲线完成后、曲线进行中、缩放中重定向，LINEAR/EASE_IN_OUT及0时长；同UUID无首帧归零/回满，结束零尺寸；95%→5%跨3–10帧、最大单帧面积损失≤35%、清零前≤10%。旧动态光强接续、位置运动、退场清理继续通过。
- 恢复条件：PD修复并发布匹配主/源码JAR后构建更新依赖，再跑`new_spells,sawblade_falling,spell_lighting,pd_api`；`saw-fragments-exits-continuously`必须原阈值通过，PS不预先把该失败豁免成通过。

移动光源的累计地形刷新、脏section排队与普通粒子只读visitor已由PD提供并原生复验；接线和验收边界见[PD接入](../design/effects-api.md#5-全亮与动态光照)，结论见[resolved](resolved.md)。并发预算、多人和短寿命高刷范围继续在[verify](verify.md)，不等同缺少API。

发现新缺陷时，按[接入政策](../design/effects-api.md#0-职责与缺陷处理)停止相应接入，不自建替代实现；记录目标、状态、源码/API、最小复现、期望语义、影响范围和验收。已提供的能力不在此保留需求原文。
