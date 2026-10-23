# 修复结论与回归入口

仅保留当前仍适用的结论。旧实现、逐次过程、过期录制统计和已撤销方案查Git，不在这里与当前规则并列。未通过的门禁仍留在 [open](open.md)，不能作为修复结论。

| 主题 | 当前保证 | 回归入口 |
|---|---|---|
| 抽牌作用域 | 同链修正无牌序；触发荷载独立，子链散射不污染载体；付费一次、链锯清整次延迟 | spell_chain、foundations |
| 复制与后代 | 按配方重建独立effect/荷载，抵消重复速度倍率，量子复制有本体与真实命中 | spell_mechanics、spell_resonance |
| 寿命触发 | 定时到期当刻释放；休眠水晶到期爆炸，提前死亡与expire区分 | spell_timing、crystal_lifecycle |
| 碰撞与反射 | 普通轨道经过Move；墙反弹与实体穿刺分开，高速盾反弹在死亡/命中前完成，视觉使用真实壳面接点 | spell_chain、shield_balance |
| 等离子束 | 默认静态，本体随位置；有效射线在墙/护盾截断，视觉与伤害共用截点，允许自伤 | expansion_plasma、spell_chain |
| 追踪与弹速 | 扫掠预计飞行段找一格侧向范围内的目标中心，再由Move命中；基础初速超过原版弩箭 | projectile_flight |
| MP与存档 | 当前MP封顶；空槽/牌堆保序；完整刻迁移旧秒/毫秒；复制不共享wandId | spell_mechanics、exploration、magic_hud |
| 伤害 | 独立分类/死亡文本；正治疗恢复、负治疗伤害；按实际接受强度击退，假人无击退 | damage_mass、combat_impact、new_spells |
| 多卡护盾 | 卡数提高抗性与立方窗口预算，破盾当次仍吸收；无常驻壳和多余圆环 | shield_balance |
| 黑洞 | 高质量互吸缓慢、静态源不碰撞、原维度持续结算，创造飞行免吸 | damage_mass、spell_stability |
| 晶体 | 外向晶面、多角可读、临爆加速白闪和爆碎 | crystal_lifecycle、sawblade_falling |
| 持杖与模型 | 原生低模idle/宝石余光，准星瞄准，长按不减速，成功使用不重举 | wand_casting、wand_aim |
| 脚下法阵 | 实时跟实体、独立慢自转；透明出生，PD首次接管原子同步，正常退场等原生完成 | pd_api、wand_casting |
| HUD | 无重复充能准心；短尖芒命中、径向绘制/外散击杀与方向受击；本机布局拖动缩放 | magic_hud |
| 原编杖 | 原容器滚轮/右键，V只读分色；创造专用包校验，生存原版协议 | foundations |
| 获取与整合 | 原结构箱子追加、真实持杖敌人原物掉落、池/等级原子重载、不重掷旧杖 | exploration、progression |
| 录制诊断 | 捕获启动崩溃/FATAL/超时/驱动错误，保留当前日志与部分帧，构建失败不误引旧MC日志 | capture.ps1、diagnostics.ps1 |
| OSHI处理 | 按用户要求忽略系统信息采集记录及其已知关联堆栈，保留原日志，不作为待修或待重启项；其他错误/FATAL照常阻塞 | assemble.classify_log_errors、test_log_filter.py |

## 已接入的PD能力

ParticleBatch批量设速/施力、实体锚点、运行时Emitter、Movable相邻样本、spawnAll与寿命曲线已使用。原生Bridge负责跟随/外观插值；首次程序接管初始化两端，热更变量终点可参与完成回报，法阵和等离子退场可retire。

PD 1.0.21新增LIGHT、静态光强乘数与从当前值接续的热更；动态光照使用alpha/尺寸包络，小数强度变化刷新静止区块和受光缓存。分数年龄采样和零末端由PD实现。particle_lighting 4/4、pd_api 10/10、fireball_trail 5/5，三场302帧无丢帧，原阈值均通过；全法术覆盖与1～2t高刷实机仍保留在verify，不能扩大通过范围。

## 文档维护

目录说明改为当前机制/入口，移除轮次叙述、重复历史数值、旧方案和过时已解决状态；Git保留完整改动记录。后续在本表按主题更新结论，不追加开发流水账。
