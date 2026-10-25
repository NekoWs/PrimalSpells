# ParticleDrawing API 修改需求

当前无未决API修改需求。运行依赖1.0.21-ALPHA来自源码提交`ebf3055`，匹配JAR与哈希见[构建指南](../build-and-run.md#2-依赖与升级)。

运行期resize与SCALE寿命曲线的叠加已由PD修复，锯片、光源及普通API原阈值复验通过；结论见[resolved](resolved.md)。预算压力、多人和短寿命高刷范围继续在[verify](verify.md)，不等同缺少API。

发现新缺陷时，按[接入政策](../design/effects-api.md#0-职责与缺陷处理)停止相应接入，不自建替代实现；记录目标、状态、源码/API、最小复现、期望语义、影响范围和验收。已解决需求原文查Git，不在此保留。
