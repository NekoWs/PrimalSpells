# ParticleDrawing API 修改需求

当前无已确认的未决API缺口。运行依赖为1.0.21-ALPHA，主JAR与sources JAR来自同一构建，源码提交/哈希见[构建指南](../build-and-run.md#2-依赖与升级)。

移动光源的累计地形刷新、脏section排队与普通粒子只读visitor已由PD提供并原生复验；接线和验收边界见[PD接入](../design/effects-api.md#5-全亮与动态光照)，结论见[resolved](resolved.md)。并发预算、多人和短寿命高刷范围继续在[verify](verify.md)，不等同缺少API。

发现新缺陷时，按[接入政策](../design/effects-api.md#0-职责与缺陷处理)停止相应接入，不自建替代实现；记录目标、状态、源码/API、最小复现、期望语义、影响范围和验收。已提供的能力不在此保留需求原文。
