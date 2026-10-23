# ParticleDrawing API 修改需求

## 移动光源的地形烘焙刷新丢失（光照截断）

- 状态：阻塞用户可见的照明修复，需PD修复。运行依赖1.0.21-ALPHA；停止本模组侧绕过，不通过增加光源数量、反复重建源、强制区块刷新或自制光照修补。
- 目标：单颗恒定14级光源慢速移动、跨section边界时，地形光照应与当前光场一致，不能留下旧亮区或沿区块边界截断。之前大量尾迹光源的持续新增/移除会请求区块重建，可能掩盖此缺陷；这是源码推断，不作为修复方案。
- 源码证据：同版本sources JAR的`lighting/DynamicLightManager.kt`第128–140行仅在距`lastBaked`超过0.25格或量化光强变化时请求重建；第162–164行却每帧无条件清空/重写`lastBaked`。小于阈值的连续位移不能累计，空间光场索引更新而地形网格不刷新。`applyDirtySections`在帧预算耗尽时直接break，未提交条目也没有保留队列，且其基准已提前更新；该分支是另一个需审查的漏刷路径，尚未单独压力复现。
- 最小原生复现：`pwsh tools/capture/capture.ps1 -Offline -Scene light_boundary -TimeoutSec 600`。只调用PD原生Builder/track/remove，不使用SpellLightFx。光源从(79.5,floor+2,63.5)按0.1格/t移动到(87.4,floor+2,63.5)，停止后第120帧取景；第130刻仅为对照，在同位置、同光强重建一个源，第150帧取景。
- 实测：原生查询点光照重建前后同为13.125，地形RGB差>5的像素却占47.40%（门禁要求≤1%）；180/180帧、0丢帧，光场等价/最终清理2项机制检查通过，地形一致性门禁失败，日志无阻塞错误。证据在`build/capture/out/light_boundary`，第120/150帧及report.txt；运行其他场景前应先归档。
- 修改要求：`lastBaked`必须代表已请求/完成地形刷新所对应的基准，小位移应对该基准累计；源移动时刷新旧、新影响范围。预算未提交的脏section应排队保留，不能先清基准再丢请求。光场关闭/清空也应使旧受光地形恢复；配置半径变化需同样失效。全部实现归PD。
- 验收：上述等价光场像素门禁通过；增加0.05/0.1格/t慢移、正负坐标和跨section边界；预算超限后最终能收敛且退场无残光；同时保留particle_lighting与spell_lighting的原生光强/去重/退出检查。PD发布修复并更新匹配主/源码JAR后再恢复本项目实机验收。

## 普通全亮粒子的只读观察入口

- 状态：待PD提供；不阻塞零照明默认值或“光”修正的运行。停止`fireball_trail`、`projectile_flight`中依赖光源列表的逐颗alpha观察，不能为通过测试把普通拖尾重新设为光源。
- 证据：1.0.21-ALPHA的ClientParticleEngine只公开`getGlowingParticles()`、`snapshot(id)`和activeCount。光源索引仅在`glowing && lightLevel > 0`时登记；snapshot不返回曲线求值后的alpha。发射器不对应用返回每个出生粒子的UUID。默认拖尾改为全亮/零光源后，旧观察器得到空列表。
- API建议：提供不分配整份列表的只读visitor（例如`forEachParticleSample(partialTick, consumer)`），返回id、位置、实际alpha/尺寸、全亮与光照等级；或提供发射器出生ID与相同的按ID只读求值入口。不得让诊断方修改内部集合/渲染状态。
- 验收：零光源发射器的普通全亮拖尾仍能在同一id上逐渲染帧观察alpha下降，观察本身不新增光源、不提交顶点、不影响生命周期；停止和最后一帧仍可检查。恢复上述两个原生场景的逐颗alpha断言。
- 当前替代证据边界：`spell_lighting`直接比较昼夜截图并观察原生光场/光源数，但不以它代替普通尾迹逐颗透明度回归。PD曲线/尺寸照明的`particle_lighting`验证仍有效。

发现新缺陷时，按 [接入政策](../design/effects-api.md#0-职责与缺陷处理) 停止相应接入，不自建替代实现；逐条记录目标、状态、当前源码/API、最小复现、期望语义、影响范围和验收。已提供的能力不在此重复保留需求原文。
