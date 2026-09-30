# 问题跟踪（issues/）

本目录是 PrimalSpells 的**唯一问题台账**。原先全部塞在 `docs/known-issues.md` 一个文件里（近 500 行）——
已解决的记录把未解决的问题淹没了，所以按**状态**拆开：**只把「还没解决」的留在待办文件里**。

## 文件分工

| 文件 | 里面放什么 | 什么时候动它 |
|---|---|---|
| [open.md](open.md) | **仍未解决**：待继续开发的功能、性能与表现问题 | 发现新缺陷/技术债 → 追加；修好了 → 删掉并写进 `resolved.md` |
| [verify.md](verify.md) | **待实机验证清单**（静态编译过、但手感/表现要进游戏确认） | 改完视觉/手感 → 追加条目；验完 → 勾掉或删掉 |
| [resolved.md](resolved.md) | **已解决记录**：按轮次编号的修复清单（含 PD 侧已闭环条目、历史崩溃） | 每轮收尾回填；**只增不改**，作为「当初为什么这么改」的备查 |
| [pd-api-requests.md](pd-api-requests.md) | **要提给 ParticleDrawing 的需求**（未决）+ 登记模板 + 已知能力边界 | 碰到 PD 的 API 缺陷/能力缺失 → 按政策停下并登记 |

## 严重度图例

🔴 崩溃/数据损坏/明显错误玩法 ｜ 🟠 影响体验或可维护性 ｜ 🟡 清洁度问题 ｜ 🟢 已处理或可接受（只出现在 `verify.md`/`resolved.md`）

## 维护规则（与 `docs/README.md` 第 6 节一致）

1. **一条一个问题**：现象、复现、影响、涉及文件、建议 —— 写清这五项才算合格条目。
2. **修好即搬家**：`open.md` 里的条目修好后，从 `open.md` **删掉**，把结论写进 `resolved.md` 的轮次表。
   台账里不留「已解决」条目（否则又会变回那个 500 行的文件）。
3. **设计约定不写在这里**：「这个行为是刻意的、不是 bug」这类内容属于
   [architecture.md §8](../architecture.md)，不占问题台账。
4. **ParticleDrawing 的问题**：一律走 [pd-api-requests.md](pd-api-requests.md)，
   **不得在本模组自行另写一套实现**（政策全文见 [conventions.md §9](../conventions.md) 与
   [design/effects-api.md §0](../design/effects-api.md)）。
5. **历史链接**：`docs/known-issues.md` 保留为**纯指针**（旧链接不失效），新内容不要再往那里写。
