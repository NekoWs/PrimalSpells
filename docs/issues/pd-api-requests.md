# ParticleDrawing API 修改需求

## 普通全亮粒子的只读观察入口

- 状态：待PD提供；不阻塞零照明默认值或“光”修正的运行。停止`fireball_trail`、`projectile_flight`中依赖光源列表的逐颗alpha观察，不能为通过测试把普通拖尾重新设为光源。
- 证据：1.0.21-ALPHA的ClientParticleEngine只公开`getGlowingParticles()`、`snapshot(id)`和activeCount。光源索引仅在`glowing && lightLevel > 0`时登记；snapshot不返回曲线求值后的alpha。发射器不对应用返回每个出生粒子的UUID。默认拖尾改为全亮/零光源后，旧观察器得到空列表。
- API建议：提供不分配整份列表的只读visitor（例如`forEachParticleSample(partialTick, consumer)`），返回id、位置、实际alpha/尺寸、全亮与光照等级；或提供发射器出生ID与相同的按ID只读求值入口。不得让诊断方修改内部集合/渲染状态。
- 验收：零光源发射器的普通全亮拖尾仍能在同一id上逐渲染帧观察alpha下降，观察本身不新增光源、不提交顶点、不影响生命周期；停止和最后一帧仍可检查。恢复上述两个原生场景的逐颗alpha断言。
- 当前替代证据边界：`spell_lighting`直接比较昼夜截图并观察原生光场/光源数，但不以它代替普通尾迹逐颗透明度回归。PD曲线/尺寸照明的`particle_lighting`验证仍有效。

发现新缺陷时，按 [接入政策](../design/effects-api.md#0-职责与缺陷处理) 停止相应接入，不自建替代实现；逐条记录目标、状态、当前源码/API、最小复现、期望语义、影响范围和验收。已提供的能力不在此重复保留需求原文。
