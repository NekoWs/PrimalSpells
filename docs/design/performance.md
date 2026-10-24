# 性能与原生基准

## 已确认的开销

- Move原先每1/16格扫描27～36个邻格；64格/t的单弹约1024子步，反弹还会重新分配整段未来路径。
- SweptCollision在大量未命中测试中创建数组；临时BlockPos、空列表addAll、子步对象/排序器进一步放大分配。
- SpellInteractions曾对不具备反应关系的普通弹全配对。大量冰冻弹之间也无需枚举。
- Level.getBlockState会请求区块，远飞弹体可能沿途同步加载/生成地形；运动查询现仅使用已加载区块。

## 当前实现

曲率步长、双精度、解析接点和反弹余时保持。规划器只计算正在行驶的下一子步，复用局部向量；同格复用邻域形状，块缓存用原生long键，空气直接跳过。缓存每刻清空，命中回调后失效；不能跨刻缓存可变化地形。slab未命中分支不分配数组，实际命中才创建Contact。

反应元数据每刻取一次，按水晶/黑洞、冰、火分类配对，保持原先i/j顺序；路径距离逐段解析，不创建整条Pair列表。粒子数量、光脊间距和索敌频率保持内容定义。穿刺接触频率按[战斗规则](combat-pricing.md#6-速度质量与后坐力)计时，空间积分精度不能放大伤害；目标时钟只保留未到期条目，逐刻清理，不累计命中过的全部实体。

必要邻格尚未加载时，Move保留最后已知位置与速度，不把未知地形当空气/墙，不触发命中。寿命照常消耗，区块就绪后可继续移动；高度外的已知虚空不需要区块。该边界策略避免同步生成地形，也避免穿过未知碰撞体。

## 原生测量

`spell_performance`在真实ServerLevel摄影棚中准备固定对象，先预热6次，再各记录17个样本；System.nanoTime计墙钟耗时，ThreadMXBean计当前服务端线程分配。32发64格/t的Move分别测试直线和固定目标制导；联动测试512发普通弹及512发冻结弹。

物理基准直接调用已装配的真实Move，隔离粒子和伤害表现；不改被测算法，也不复制其实现。另有正常汇聚之光显示与视觉门禁。这里测的是固定负载的组件耗时，不代表整合包总体MSPT或客户端帧率；内存数值为线程累计分配，不是存活堆大小或实测GC停顿。

| 工作负载 | 优化前中位耗时 | 优化后中位耗时 | 优化前/后分配 |
|---|---:|---:|---:|
| 32发直线64格/t | 8.296 ms | 3.126 ms | 36.906 / 3.858 MiB |
| 32发制导64格/t | 9.933 ms | 3.572 ms | 35.686 / 3.798 MiB |
| 512发普通弹联动 | 1.707 ms | 0.257 ms | <0.001 / 0.016 MiB |
| 512发冻结弹联动 | 未采集 | 0.136 ms | 未采集 / 0.082 MiB |

优化前为a833fca运行时加同一测量场景，原始数据在build/capture/performance-baseline/spell_performance/performance.tsv；当前运行时拆分后的独立复测在build/capture/out/spell_performance/performance.tsv。拆分前同机记录在build/capture/before-architecture/spell_performance/performance.tsv；直线/制导分配仍约3.8MiB，耗时样本有波动，不能据此宣称架构拆分带来性能提升。机器负载/JIT会影响数值，比较相同条件的中位数，不给不同硬件设伪精确通用门槛。

## 复现与保真验证

```powershell
pwsh tools/capture/capture.ps1 -Offline -Scene spell_performance,collision_precision,homing_light,projectile_flight,spell_chain,recoil_light,shield_balance,wand_aim,crystal_lifecycle -TimeoutSec 600
```

上面的命令覆盖运动与性能回归。当前运行时拆分后的八场306项机制检查及视觉门禁通过；独立性能复测4项检查、140帧、零丢帧通过。完整范围和证据见[架构验证](../architecture.md#10-扩展边界与自动检查)。

后台启动规则见[构建指南](../build-and-run.md)。先归档out再运行以保留基线。性能场景输出逐样本performance.tsv，不把FPS/录帧数量当服务器计时。collision_precision覆盖薄形状、拼缝、墙角、斜反射、实际路径联动与反应先后；另验证未加载区块不生成、到未知边界不穿透/伪命中、高空虚空不误停。

联动混合场景spell_resonance额外验证12项机制；其全局退场门禁仍有已登记缺口，不能用机制成功代替视觉成功，见[问题台账](../issues/open.md)。
