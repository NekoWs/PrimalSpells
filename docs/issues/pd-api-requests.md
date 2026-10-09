# ParticleDrawing API 需求（只放**未决**）

> **本文件只放还没实现的需求。** 已闭环的条目一律在 [resolved.md](resolved.md) 附录 B
> 「ParticleDrawing 已闭环条目」里（含当时的登记原文），本文件不保留已解决内容。
> **政策**：见 [conventions.md](../conventions.md) §9 与 [design/effects-api.md](../design/effects-api.md) §0 ——
> 碰到 `particledrawing` 的 API 缺陷 / 签名不符 / 能力缺失时，**不得在本模组自行另写一套实现**；
> 应停止该接入点的尝试，把需求登记在这里，并在本次产出末尾一并列出。
>
> **顺序（硬性）**：先写清**目标模型** → 对着 PD**现有能力**列缺口并登记 → **然后**才选临时降级
> （并在代码注释里标明"这是降级、需求落地后删"）。先绕后记账是本项目此前最大的结构性问题。
>
> **出现时机**：需求清单与登记**只在本次功能要求涉及 ParticleDrawing API 时**才出现
> （特效接线、升级导致 API 变化、需求要求用它的能力）；与之无关的工作不要在这里添条目。
>
> 当前锁定版本：见 `gradle.properties` 的 `particledrawing_version`（现为 `1.0.17-ALPHA`，
> `lib/particledrawing-1.0.17-ALPHA.jar` 另附 `-sources.jar` 便于核对签名）。

---

**当前未决：1 条。**

---

## 第73轮：变量完成终点丢失与重定向兜底过期（2026-10-09）

- 状态：1.0.17-ALPHA实机确认，停止变量缓动完成回报接入；有限expression时长的客户端回报已通过。
- 目标：主法阵presence归零、冷却提示及等离子束退场，在客户端确实完成并留足桥接余量后清理；缓动重定向不能沿用旧兜底提前完成。
- 复现：PdApiScene在t50对presence缓动20t并登记回调/retire，t55重定向为24t。最后有效终点t79（相对beganAt=t12为67），实机回调却在相对78，即t90，正好等于第一次登记t50+20t缓动+20t兜底；不是客户端上报。重定向后的正确兜底应为t99（相对87）。WandCastingScenes放下主手t140后，t154仍有240粒子，直到旧服务端兜底到时才回收；副手t190后t219仍有240粒子。
- 源码：ClientAnimationProgramManager.tick先取ledgerEnd，再applyVarEases；后者在now到达终点时立即移除ease。reportCompletionIfDue要求now>=ledgerEnd+50ms，当帧未到、下一帧终点已从账本消失，纯变量表达式组永远错过信号。ServerProgramCompletion.scheduleFallback的任务不带登记版本，重定向后旧任务仍直接移除最新entry并fire。
- 期望：保存已完成变量的最终账本端点直到上报（重定向更新/失效旧端点），或在完成那帧记录待上报终点；旧fallback用版本/令牌取消，只有最后有效登记可触发。onAnimationComplete真正由客户端完成时调用，有限expression和变量混合亦需覆盖。
- 临时处理：沿用上个已验证版本的有限清理，不自写回调、客户端播放器或伪造有限指令。WandCastingFx原有presence8t+12t清理与冷却表达式7t清理保留；等离子presence6t+8t清理保留。原生有限expression施法迸发可正常使用expression(code,5).retire。
- 验收：上述重定向在相对67～77以内收到客户端回调，不能在相对78旧兜底时伪装完成；无客户端只按最后登记t99兜底。主/副手法阵分别在t154/t208清空；切维、登出无残留。

---

## 1. 已知的能力边界（不是缺陷，但接线前必须知道）

| 项 | 现状 | 影响 | 我们的处理 |
|---|---|---|---|
| `EffectVarStore.getParam` 只能读回「最近一次 `setParam`/`updateVariable` 设过的值」 | 变量真实状态在客户端动画运行时里，服务端读不到 | 服务端无法用 `getParam` 查询客户端当前值 | 只把它用于「我们刚设过的值」的回读；确需读客户端真值时按本文件提需求 |
| `.pdrawc` 动画特效尚未在 PrimalSpells 侧接线 | 播放器侧 API（含 `EffectCallbacks`/`EffectVarStore`）已具备 | 法术目前只有程序化粒子（`Trajectory`）+ 发射器，没有编辑器导出的动画 | 记为功能排期项，**不是** API 缺陷 |
| 位置指令会清掉速度与力 | `ServerParticleEngine.takeOverPosition`：`move`/`track`/`trackAll` 一律 `velocity=0`、`accel=0` | 「位置驱动」与「速度/力驱动」不能混用在同一颗粒子上 | 同一粒子只选一种驱动方式；见 [design/effects-api.md §0.3](../design/effects-api.md) |
| 运动指令一律解除实体锚点 | 位置（`move`/`track`）、速度（`setVelocity`/`setVelocityAll`）、力（`applyForce`/`applyForceAll`）都接管位置 | 先 `attachTo` 再给速度/力＝锚点被解掉，粒子不再跟随实体 | 1.0.11-ALPHA 起口径统一 |
| 发射器与可移动组轴心按相邻服务器样本插值 | PD 1.0.17-ALPHA提供Movable样本和updateAnchor | 无需本侧压后一tick | Trajectory传当前位置，与track头部同相位；固定朝向结构显式Orient.WORLD |
| **粒子「实际尺寸」不是 `scale` 本身** | `BridgeParticle`：`quadSize = scale × EDITOR_TO_MC_SCALE × texScale`；16×16 贴图 `texScale = 1` ⇒ **渲染整宽 = scale × 0.2 格** | 按「scale = 格数」估密度会偏小；要按世界格给尺寸用 `anisoWorld` | 本侧的换算：`scale = 格数 × 5`（见 `Trajectory` 的尺寸常量注释）；`anisoWorld` 口径的粒子**吃不到** `resize` |

---

## 2. 需求登记模板（新增需求时照抄）

```
### [需求] <一句话标题>
- 状态：未决
- 目标能力：<我们在 PrimalSpells 想实现的效果 + 触发场景>
- 现有 API 为什么不够：<类 + 方法/属性 + 编译错误原文或运行现象 + 复现步骤>
- 期望 API 形态：<签名草案（类/函数/参数/返回）；服务端还是客户端；线程与生命周期约定>
- 影响面：<本模组受影响文件>
- 临时降级：<降级范围 + 恢复条件；没有则写「无（该功能暂不可用）」>
```

> 需求落地后：把本条**整段搬进** [resolved.md](resolved.md) 附录 B（连同当时的原文），
> 本文件只留标题式的一行指针都不要留 —— 保持"打开这个文件看到的全是未决项"。
