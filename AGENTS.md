# AGENTS.md

`docs/` 是本项目的唯一权威说明；本文件只是路由表——具体内容写进 `docs/`。

## 动手前读哪篇

| 场景 | 读 |
|---|---|
| 初次接触、要理解运行时机制 | `docs/README.md`（索引）→ `docs/architecture.md` |
| 找文件/类，或按法术 id 找实现 | `docs/code-map.md` |
| 新增或修改法术内容（投射物、修饰器、特效、物品、贴图、语言键） | `docs/spell-development.md` |
| 落笔写代码前（包结构、命名、注释、端分离） | `docs/conventions.md` |
| 构建、运行、排障 | `docs/build-and-run.md` |
| 要「看一眼实机效果」/ 改完特效要留证据 / 没人操作电脑 | `docs/build-and-run.md` 第 9 节（实机录制流水线） |
| 改核心机制，或判断某设计是否有意为之 | `docs/architecture.md` 第 8 节「关键不变量与陷阱」+ `docs/issues/`（台账：未解决 / 待验证 / 已解决 / PD 需求） |
| 做粒子特效、升级 `particledrawing` 前置 | `docs/design/effects-api.md` |
| 改伤害 / 施法间隔 / 护盾强度与抗性（战斗数值） | `docs/design/combat-pricing.md` |

## 硬性要求

1. 动核心机制（法术结算、effect 顺序、牌堆、持久化）前，先读 `docs/architecture.md` 第 8 节。
2. 新文件按 `docs/conventions.md` 第 1–2 节的包结构与落位规则放置；改结构就同步那里的旧→新对照表。
3. 改完跑 `./gradlew build`（命令与故障排查见 `docs/build-and-run.md`）。
4. 新缺陷登记到 `docs/issues/open.md`，修好即从该文件移除并把结论回填 `docs/issues/resolved.md`；文档维护规则见 `docs/README.md` 第 6 节。
5. 新信息一律写进 `docs/` 对应文档，本文件保持为纯指针。
6. 前置模组 `particledrawing`（ParticleDrawing，ALPHA）出现 API 缺陷或能力缺失时，**不得在本模组自行另写一套替代实现**：停止该接入点的尝试，把「ParticleDrawing API 修改需求」列在产出末尾并登记到 `docs/issues/pd-api-requests.md`。**该清单只在本次功能要求涉及 ParticleDrawing API 时才出现**（政策全文见 `docs/conventions.md` 第 9 节与 `docs/design/effects-api.md` 第 0 节）。
7. 提交信息按 `docs/conventions.md` 第 10 节的 `目的：内容` 格式：简短、只说大概干了什么、不复述具体改动。
