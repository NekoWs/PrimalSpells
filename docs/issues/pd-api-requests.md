# ParticleDrawing API 修改需求

当前无已确认的未决API缺口。运行依赖为1.0.21-ALPHA，主JAR与sources JAR同步构建和更新。

光强曲线、静态乘数、热更缓动、尺寸/alpha照明包络、光照缓存失效以及分数年龄采样已由PD提供。基础原生场景复验通过，使用说明见 [PD接入](../design/effects-api.md)，结论见 [resolved](resolved.md)。所有法术的入退场覆盖与短寿命高刷体验仍在 [verify](verify.md) 验证，不等同缺少API。

发现新缺陷时，按 [接入政策](../design/effects-api.md#0-职责与缺陷处理) 停止相应接入，不自建替代实现；逐条记录目标、状态、当前源码/API、最小复现、期望语义、影响范围和验收。已提供的能力不在此重复保留需求原文。
