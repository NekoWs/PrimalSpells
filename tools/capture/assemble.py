#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""实机录制装配器：把 capture 驱动录下的逐 tick PNG 变成「能看的东西」，并顺手做自动判定。

输入（由 `gradlew runCapture` 产出，见 docs/build-and-run.md 第 9 节）::

    build/capture/out/<scene>/frames/%05d.png   每个 tick 一帧（20fps）
    build/capture/out/<scene>/frames.tsv        index / tick / gameTime
    build/capture/out/<scene>/meta.tsv          场景参数 + 场景内断言（check 行）
    build/capture/out/summary.tsv               各场景帧数/断言统计

输出（同一目录）::

    <scene>.mp4      H.264，20fps（发给别人 / 自己拖进度条看）
    <scene>.gif      640 宽、调色板优化（在聊天/浏览器里直接动）
    contact.png      12 帧拼图（一眼看完整条时间线）
    report.txt       判定结果（PASS/FAIL + 每帧覆盖曲线）
    index.html       画廊：三个场景并排，附判定结果

判定口径（**这是自动化测试的断言，不是画图**）：

1. **帧数**：恰好等于场景声明的 `lengthTicks`，且驱动侧 `framesDropped == 0`；
2. **场景内断言**：`meta.tsv` 里的 check 行全部 `ok=true`（施法/清理/被动装载等）；
3. **空场起飞**：开头 2 帧几乎没有任何特效像素（证明「进场动画」确实从无到有）；
4. **真的出现了**：峰值特效像素数超过阈值（证明粒子确实渲染到了屏幕上，而不是只有服务端状态）；
5. **收干净了**：最后 3 帧几乎没有任何特效像素（退场不留残余）；
6. **退场是渐变的**：从峰值衰减到 50% 再到 <5% 至少跨 4 帧（防「瞬间消失」回归 —— 第 17 轮就是靠这条
   发现护盾消散是一帧内完成的，根因见 docs/issues/pd-api-requests.md）。

用法::

    python tools/capture/assemble.py [--out build/capture/out] [--no-video] [--gif-width 640]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

# Windows 默认 GBK 不能打印损坏日志中的替代字符；报告文件始终是 UTF-8。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

try:
    import numpy as np
    from PIL import Image, ImageDraw
except ImportError as exc:  # pragma: no cover - 环境缺库时给一句人话
    print(f"[assemble] 需要 numpy 与 pillow：{exc}", file=sys.stderr)
    raise SystemExit(2)

# ---------------------------------------------------------------- 场景表

#: 每个场景的期望帧数与判定阈值（帧数必须与 CaptureScenes.kt 里的 lengthTicks 一致）
SCENES = {
    "wand_design": {"length": 160, "title": "暮紫封印 / 户外低位持握 / 左右手", "metric": "violet_model", "quiet": (0, 6), "decay": "none", "decay_reason": "材质/占屏来自实际紫晶像素，装备和收杖保留原版动画；施法及粒子清理由原生断言覆盖"},
    "spell_performance": {"length": 140, "title": "原生 CPU / 分配基准", "metric": "warm", "quiet": (0, 6), "decay": "none", "decay_reason": "机制计时与真实光束展示分别取证，性能数据不设机器相关通过阈值"},
    "collision_precision": {"length": 140, "title": "高速反弹 / 双精度接点 / 连续余程", "metric": "warm", "quiet": (0, 6), "decay": "none", "decay_reason": "多次真实发射分别退场，机制读取实际扫掠与接触回调"},
    "homing_light": {"length": 150, "title": "六格追踪 / 弧线扫掠 / 聚光照明", "metric": "warm", "quiet": (0, 6), "decay": "none", "decay_reason": "范围/真实曲线碰撞与照明各有独立原生断言，多个发射阶段不共用全局退场峰值"},
    "particle_lighting": {"length": 110, "title": "粒子照明 / 出生与退场连续性", "metric": "blue", "quiet": (0, 6), "decay": "none", "decay_reason": "独立观察原生光场与透明度，连续性由光照数值断言验证"},
    "projectile_flight": {"length": 130, "title": "弩箭对比 / 路径追踪 / 拖尾淡出", "metric": "orange", "quiet": (0, 6), "decay": "none"},
    "progression": {"length": 240, "title": "数据包 / 安全世界作用 / 寻法 / 精英", "metric": "wand", "quiet": (0, 6), "decay": "none"},
    "foundations": {"length": 260, "title": "只读抽牌树 / 真实网络预览", "metric": "wand", "quiet": (0, 6), "decay": "none"},
    "machinegun_visual": {"length": 170, "title": "连续机枪 / 随机弧刃 / 六格追踪", "metric": "white", "white_roi": (540, 415, 800, 550), "quiet": (0, 6), "short_exit": True, "decay": "gradual", "fade_window": (86, 105)},
    "spell_chain": {"length": 400, "title": "束壳截点 / 穿刺触发 / 强追踪 / 机枪 / 假人统计", "metric": "blue", "quiet": (0, 6), "decay": "collapse"},
    "wand_aim": {"metric": "wand", "length": 160, "title": "近远准星命中 / 副手 / 遮挡 / 稳定握持", "decay": "gradual", "quiet": (0, 8), "fade_window": (129, 159)},
    "wand_casting_first_person": {"metric": "wand", "length": 220, "title": "第一视角低模持杖 / 常驻符印与冷却", "decay": "gradual", "quiet": (0, 8), "fade_window": (187, 216)},
    "wand_casting": {"wand_cooldowns": True, "metric": "wand", "length": 220, "title": "三角法阵展开 / 角点锁印 / 双层冷却导槽 / 切杖", "decay": "gradual", "quiet": (0, 8), "fade_window": (187, 216)},
    "spell_stability": {"length": 180, "title": "双场穿墙 / 即时追踪 / 晶体预算", "decay": "gradual", "quiet": (0, 8)},
    "spell_resonance": {"length": 320, "title": "法术联动 / 配方矩阵 / 晶体与护盾弹", "decay": "gradual", "quiet": (0, 8)},
    "expansion_crystals": {"length": 140, "title": "水晶粘附 / 连锁引爆 / 归属", "decay": "gradual", "quiet": (0, 8)},
    # 护盾受击玻璃亮度远高于电弧；在独立电弧窗口内量渲染和渐隐，不混用护盾峰值。
    "expansion_arc": {"length": 150, "title": "分段跃迁 / 四次放电 / 护盾", "decay": "gradual", "quiet": (0, 8), "fade_window": (8, 64)},
    "expansion_combinations": {"length": 180, "title": "多重施放 / 触发水晶 / 环绕光束", "decay": "gradual", "quiet": (0, 8)},
    "expansion_plasma": {"length": 210, "title": "持续光束 / 遮挡 / 寿命", "decay": "gradual", "quiet": (0, 8)},
    "expansion_utilities": {"length": 220, "title": "冰冻 / 远距 / 斥力", "decay": "gradual", "quiet": (0, 8)},
    # decay: gradual = 该效果**设计上**有退场动画（必须逐帧衰减）；instant = 设计上就是立刻消失；
    #        none    = 这一场有**两套互不相干的视觉系统**（玻璃板 + 爆炸烟）或收场不是蓝色像素，
    #                  全局峰值不可靠，退场交给场景专属判据（必须在 spec 里另有对应断言）
    "black_hole": {
        "length": 108, "title": "黑洞：暗核 / 星尘吸入 / 收束退场", "decay": "gradual",
    },
    "black_hole_moving": {
        "length": 108, "title": "黑洞：暗核与边界星点移动跟随", "decay": "gradual",
    },
    "gravity_field_moving": {
        "length": 130, "title": "重力场：移动球壳与地面井环跟随", "decay": "gradual",
    },
    "gravity_field": {
        "length": 130, "title": "重力场：球面经线 / 下压刻度波 / 地面双井环",
        # 第 47 轮由 instant 改 gradual：静止场（重力场/黑洞）现在有**共用的退场余晖**
        # （`Trajectory.emitFieldAfterglow`，寿命 6~24 tick 错开），本体一死是**逐颗淡出**、不再是一刀切断。
        # 原先的 instant 记的是"那时确实没有退场动画"；设计变了，口径跟着改（黑洞那边本来就是 gradual）。
        "decay": "gradual",
    },
    "explosion": {
        "length": 78,
        "title": "爆炸：随机火舌 / 径向破裂 / 余烬 / 烟团",
        # 第 29 轮起爆炸改成**青白色能量**（对齐用户给的参考）→ 标准判据用蓝（青满足 b>80 且 b-r>12）
        "metric": "warm",
        # 放弹（t=20）之前必须干净
        "quiet": (2, 16),
        # 暖色判据下的退场是**阈值悬崖**（球体 alpha 淡出 6 tick，但像素判据只看到跨阈值那一帧）——
        # 与玻璃面板同理；「收干净」由  +  负责
        "decay": "instant",
    },
    # 火球拖尾（第 43 轮）：跟拍一颗飞行中的火球，看 Noita 式的分层火花尾迹。
    # 第85轮提速后使用白热色阶，按白色本体判可见性；透明度连续性读取真实PD逐颗粒子。
    # 高速短尾可在1～2刻内自然结束，不能要求整场覆盖量必须跨4刻。
    "fireball_trail": {
        "length": 72,
        "title": "火球拖尾：白热核 + 沿轨迹的符文余痕（跟拍）",
        "metric": "white",
        "fade_window": (33, 65),
        "fade_min_peak_ratio": 0.0004, # 退场碎屑独立于遮挡前弹体，368px最低可读覆盖；主体门槛不变
        "decay": "alpha",
    },
    "energy_shield": {
        "length": 528,
        "title": "玻璃护盾：受击蓝玻璃 → 预算打穿 → 反弹箭矢 → 整罩碎 → 扛得住 1 个小白 / 扛不住 3 个",
        # 蓝色判据下**没有**可用的全局退场：这一场混了两套视觉系统 —— 受击玻璃板（一次性、alpha 淡出在
        # 像素判据里就是跨阈值的悬崖）与大爆的原版烟（随机粒子，峰值会与玻璃板逐次运行翻转）。
        # 护盾真正的收场是**粉色**破口轮廓，由下面的 `breach_fade` 单独卡；蓝色这边只保留
        # 「退场无残留」（末 3 帧）与空场起飞。
        "decay": "none",
        # 单块玻璃板只有整罩破盾的百分之几，阈值按峰值 5% 会把它判成「空」——这里单独调低到 2%
        "empty_ratio": 0.02,
        # 静止窗口：护盾平时必须**完全不可见**（这正是实机反馈「太挡视野」要解决的）
        "quiet": (2, 18),
        # 受击窗口：四次点射各出一个亮点（间隔 26 tick > 窗口，所以每一发都该独立亮起）。
        # 命中在 t≈33/59/85/111（发射 22/48/74/100 + 飞 11 tick）。
        "hits": (28, 120), "min_peaks": 4,
        # 破口窗口：取**大爆**那一次（第 41 轮重排后：276 结算 → 破口吃到上限 60 tick ⇒ 276..336）
        "breach": (284, 332), "min_breach_frames": 30,
        # 破口到期必须是**淡出**：这里卡**第一次破口**（两发普通火球超预算那一次，20 tick ⇒ ≈155..175）的尾段。
        # 窗口一直开到 190：轮廓淡完之后还有一段 ~150 px 的"红" —— 那是打穿那一发跳出的**伤害数字**文本
        # （不是轮廓），所以末尾必须留到它也消失之后，`seg[-1] == 0` 才成立。
        "breach_fade": (150, 192), "min_fade_frames": 3,
        # ⚠️ 「波及到的片全部显示」**不用像素判据**（第 40 轮改）：32 片的点阵在共享顶点/棱处大量重叠
        # （1056 个点只落在 ~420 个位置上），整罩的粉色像素只有 7 片时的 ~1.7 倍而不是 4 倍，
        # 再加上爆炸烟云会把粉色压暗 —— 这条量不出片数。现在由场景内断言 `blast-reaches-all-panels`
        # （直接读 `EnergyShield.breachPanelCount`）来卡，像素判据只负责"红色确实渲染出来了"。
    },
}

# 第53轮改成实色方粒；覆盖量衰减代表尺寸收缩/错峰到寿，不再代表透明度渐隐。
for _scene, _spec in SCENES.items():
    if _spec.get("decay", "gradual") == "gradual":
        _spec["decay"] = "collapse"
# 这两场包含真实爆炸/锯齿飞散，使用已有解体门禁（至少4帧），不能要求瞬间消失。
SCENES["recoil_light"] = {"length": 220, "title": "后坐力 / 创造飞行豁免 / 蓄能强光", "metric": "warm", "fade_window": (58, 85), "quiet": (0, 8), "decay": "collapse"}
SCENES["shield_balance"] = {"length": 250, "title": "单卡与八卡护盾 / 破盾当次保护 / 靶子固定", "metric": "blue", "fade_window": (138, 225), "quiet": (0, 8), "decay": "collapse"}
SCENES["concentrated_light_first_person"] = {"length": 180, "title": "第一人称汇聚之光 / 原地聚光阵与锁定方向", "metric": "warm", "warm_roi": (0, 0, 1280, 300), "quiet": (0, 8), "decay": "collapse"}
SCENES["pd_api"] = {"length": 120, "title": "PD跟踪曲线 / 静止受光 / 帧间尺寸 / 完成销毁", "metric": "blue", "quiet": (0, 6), "decay": "collapse"}
SCENES["damage_mass"] = {"length": 220, "title": "伤害渠道 / 质量受力 / 每刻伤害 / 双黑洞互吸", "metric": "blue", "quiet": (0, 8), "decay": "collapse"}
SCENES["combat_impact"] = {"length": 260, "title": "跃迁魔弹 / 强度击退 / 自伤 / 黑洞暗核", "metric": "warm", "quiet": (0, 8), "decay": "collapse"}
SCENES["crystal_lifecycle"] = {"length": 180, "title": "晶面四向 / 加速白闪 / 到期爆炸", "metric": "warm", "quiet": (0, 2), "decay": "collapse"}
# 此场景验证完整战斗与夺杖流程，多个独立生命周期不适用单一峰值退场跨度。
SCENES["exploration"] = {"length": 240, "title": "结构战利品 / 游荡术士 / 实际持杖掉落", "metric": "warm", "quiet": (0, 6), "decay": "none", "decay_reason": "本场包含多次独立施法；玩法断言检查战斗收场粒子/投射物归零，并保留尾帧无残留判据"}
SCENES["magic_hud"] = {"length": 200, "title": "魔法HUD / 双冷却 / 原生拖动缩放保存", "metric": "blue", "quiet": (0, 6), "decay": "none", "decay_reason": "HUD状态切换与编辑器预览各有独立生命周期；原生输入/配置断言与最终空场检查共同验收"}
SCENES["sawblade_falling"] = {"length": 130, "title": "锯齿钢盘 / 下坠反弹 / 金属碎散", "metric": "white", "quiet": (0, 6), "short_exit": True, "fade_window": (65, 85), "decay": "collapse"}
SCENES["new_spells"] = {"length": 180, "title": "复制抽牌 / 追加条件 / 正负治疗 / 锯片与火种", "metric": "white", "quiet": (0, 6), "decay": "instant"}
SCENES["spell_timing"] = {"length": 150, "title": "链锯连射 / 移动延时锁印 / 同刻释放", "metric": "warm", "quiet": (0, 2), "decay": "collapse"}
SCENES["spell_mechanics"] = {"length": 220, "title": "寿命触发 / 刻迁移 / 量子命中 / 环绕 / 短激光", "metric": "blue", "quiet": (0, 2), "decay": "instant"}
SCENES["explosion"]["decay"] = "collapse"
SCENES["wand_casting_wide"] = {**SCENES["wand_casting_first_person"], "title": "宽视野90度 / 宝石位置校准 / 副手镜像"}
SCENES["wand_casting_front"] = {**SCENES["wand_casting_first_person"], "title": "第三人称正面握柄 / 左右手姿势 / 块状符印"}

for _name in ("wand_aim", "wand_casting", "wand_casting_first_person", "wand_casting_wide", "wand_casting_front"):
    SCENES[_name]["short_exit"] = True

#: 「特效像素」判据：**偏冷的亮**像素（蓝 ≥ 红）。黑底摄影棚里只有粒子满足：
#: 木桩是棕色（红 > 蓝）、地面是灰黑（不够亮）、火球拖尾是橙色（红 > 蓝）。
#: 间隙从 25 放宽到 12：护盾受击那块玻璃第 24 轮改成**冷白**（226,242,255），
#: 差值只有 29 的高光边缘、以及叠加变淡后的像素会低于 25，卡太死会把「白了一下」判成空场。
BLUE_MIN = 80
BLUE_RED_GAP = 12

#: 峰值像素下限（占全画面比例）：低于它说明特效根本没渲染出来
MIN_PEAK_RATIO = 0.0008

#: 允许的丢帧比例：录制是同步回读帧缓冲，偶发一次 GPU 同步 stall 就会少一帧。
#: 关键不是「一帧都不许丢」，而是**如实报出来**并确认没丢成片段（>5% 说明帧率真的撑不住）。
MAX_DROP_RATIO = 0.05

#: 「几乎为空」的判据（占峰值比例）
EMPTY_RATIO = 0.05

#: 退场从 50% 衰减到 <5% 至少要跨几帧（防「一帧闪没」）
MIN_DECAY_FRAMES = 4

#: 「设计上就瞬间消失」的效果，50%→5% 最多允许跨几帧
INSTANT_DECAY_MAX_FRAMES = 3

CONTACT_PICKS = 12


def wand_mask(path: Path) -> "np.ndarray":
    # 宝石为饱和蓝、银架的蓝红差仅24；只计青色符印/法阵，白色冷却另有独立门禁。
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (g > 150) & (b > 160) & (g > b * 0.80) & ((b - r) > 40)


def violet_model_mask(path: Path) -> "np.ndarray":
    # 户外天空/草木不属于紫晶色域，不用世界蓝色像素冒充模型可见性。
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (b > 90) & (b > r * 1.12) & (r > g * 1.35)


def white_spell_mask(path: Path) -> "np.ndarray":
    # 只取发光白金弹道/切痕，排除摄影棚木桩；机枪ROI另排除头顶伤害跳字。
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (r > 235) & (g > 220) & (b > 180)


def blue_mask(path: Path) -> "np.ndarray":
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, b = a[..., 0], a[..., 2]
    return (b > BLUE_MIN) & ((b - r) > BLUE_RED_GAP)


#: 「破口像素」判据（第 26 轮新增）：**偏粉的亮红**，用来把破口轮廓从场景里挑出来。
#:
#: 标定过程（别退回「红 > 蓝」那种粗判据）：摄影棚里的木桩与木构件是棕色 (135,109,68)、
#: 火球拖尾是橙色 (219,122,21)，两者都满足「红 > 蓝」——用粗判据会把它们算成破口
#: （实测末帧仍有 1 万「红」像素，而那时破口早没了）。破口颜色是 `Color.ofInt(255,118,118,150)`：
#: **红远高于绿、且绿≈蓝**，两个条件一起用就能同时排掉木头与火焰。
RED_MIN = 120
RED_GREEN_GAP = 40
GREEN_BLUE_MAX_DIFF = 25


def red_mask(path: Path) -> "np.ndarray":
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (r > RED_MIN) & ((r - g) > RED_GREEN_GAP) & (np.abs(g - b) <= GREEN_BLUE_MAX_DIFF)


#: 「暖色亮像素」判据（第 28 轮）：爆炸的内爆/爆发是暖色（橙红 / 亮黄），**蓝判据完全看不见**。
#: 中灰摄影棚（r≈g≈b）与冷白玻璃都不满足，所以这条只挑爆炸自己的粒子。
WARM_MIN = 150
WARM_BLUE_GAP = 60


def warm_mask(path: Path) -> "np.ndarray":
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, b = a[..., 0], a[..., 2]
    return (r > WARM_MIN) & ((r - b) > WARM_BLUE_GAP)


#: 「亮橙像素」判据（第 43 轮新增，给火球拖尾用）：橙黄发光的**粒子**，门槛比 [warm_mask] 低一档。
#:
#: 为什么要单独一条：`warm` 要求 `r > 150 且 r − b > 60`，那是给爆炸的**饱和暖色**标定的；
#: 而拖尾是**加法混合**的橙黄火花 —— 叠在黑底上整体偏亮偏黄（实测尾迹像素均值 ≈ (175, 120, 60)，
#: 一大半落在 `r−b` 40~60 之间），用 `warm` 量会漏掉一半以上的火花（实测同一帧 warm=219 / orange=441）。
#: 反过来这条也不会把摄影棚误判进来：黑混凝土亮度很低（r≈30）、空气/天空是蓝的（r−g 为负）。
ORANGE_MIN = 130
ORANGE_BLUE_GAP = 40
ORANGE_GREEN_GAP = 15


def orange_mask(path: Path) -> "np.ndarray":
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (r > ORANGE_MIN) & ((r - b) > ORANGE_BLUE_GAP) & ((r - g) > ORANGE_GREEN_GAP)


#: 「暗核心」判据（第 28 轮）：内爆的黑色核心球在中灰棚里是**唯一**的暗区。
#: 亮度 = 0.299r + 0.587g + 0.114b：中灰混凝土 ≈ 150、木桩 ≈ 112、黑球 < 40。
DARK_MAX_LUMA = 40


def dark_mask(path: Path) -> "np.ndarray":
    a = np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)
    luma = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
    return luma < DARK_MAX_LUMA


def read_tsv(path: Path) -> list[list[str]]:
    if not path.exists():
        return []
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(line.split("\t"))
    return rows


def parse_meta(path: Path) -> tuple[dict[str, str], list[tuple[str, bool]]]:
    meta: dict[str, str] = {}
    checks: list[tuple[str, bool]] = []
    for row in read_tsv(path):
        if row[0] == "check":
            # 整行形如：check <TAB> <scene> <TAB> t=<tick> <TAB> <name> <TAB> ok=true <TAB> <detail>
            fields = [f for f in row[1:] if f]
            name, ok = "?", False
            for field in fields:
                if field.startswith("ok="):
                    ok = field[len("ok="):] == "true"
                elif "=" not in field:
                    name = field
            checks.append((name, ok))
        elif len(row) >= 2:
            meta[row[0]] = row[1]
    return meta, checks


def run(cmd: list[str]) -> tuple[int, str]:
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return proc.returncode, (proc.stdout or "") + (proc.stderr or "")


def build_video(frames_dir: Path, out_mp4: Path, fps: int) -> tuple[bool, str]:
    if shutil.which("ffmpeg") is None:
        return False, "ffmpeg 不在 PATH"
    code, log = run([
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-framerate", str(fps), "-i", str(frames_dir / "%05d.png"),
        "-c:v", "libx264", "-preset", "slow", "-crf", "18",
        "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out_mp4),
    ])
    return code == 0, log.strip().splitlines()[-1] if log.strip() else ""


def build_gif(frames_dir: Path, out_gif: Path, fps: int, width: int) -> tuple[bool, str]:
    if shutil.which("ffmpeg") is None:
        return False, "ffmpeg 不在 PATH"
    vf = f"fps={fps},scale={width}:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse=dither=none"
    code, log = run([
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-framerate", str(fps), "-i", str(frames_dir / "%05d.png"),
        "-filter_complex", vf, "-loop", "0", str(out_gif),
    ])
    return code == 0, log.strip().splitlines()[-1] if log.strip() else ""


def build_contact(frames: list[Path], out_png: Path, title: str) -> None:
    picks = [frames[round(i * (len(frames) - 1) / (CONTACT_PICKS - 1))] for i in range(CONTACT_PICKS)]
    tw, th, cols = 320, 180, 4
    rows = (len(picks) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * th), (16, 16, 20))
    draw = ImageDraw.Draw(sheet)
    for i, f in enumerate(picks):
        im = Image.open(f).convert("RGB").resize((tw, th))
        x, y = (i % cols) * tw, (i // cols) * th
        sheet.paste(im, (x, y))
        draw.rectangle((x, y, x + 58, y + 15), fill=(0, 0, 0))
        draw.text((x + 4, y + 3), f.stem, fill=(255, 90, 90))
    sheet.save(out_png)


def analyse(scene: str, spec: dict, root: Path, log_lines: list[str]) -> dict:
    sdir = root / scene
    frames = sorted((sdir / "frames").glob("*.png"))
    meta, checks = parse_meta(sdir / "meta.tsv")
    fps = int(meta.get("fps", "20") or 20)
    length = int(meta.get("lengthTicks", spec["length"]) or spec["length"])
    dropped = int(meta.get("framesDropped", "0") or 0)

    result: dict = {
        "scene": scene,
        "title": spec["title"],
        "dir": sdir,
        "frames": len(frames),
        "length": length,
        "dropped": dropped,
        "fps": fps,
        "checks": checks,
        "asserts": [],
        "cover": [],
    }
    if not frames:
        result["asserts"].append(("有帧产出", False, "frames/ 为空 —— 录制没跑起来或被删了"))
        return result

    expected_size = (1280, 720)
    sizes = set()
    for frame in frames:
        with Image.open(frame) as im:
            sizes.add(im.size)
    result["asserts"].append((
        "录制分辨率正确", sizes == {expected_size},
        f"实际 {sorted(sizes)}，要求 {expected_size}（隐藏窗口不能退化成 1×1）",
    ))

    # 只读取本场判据使用的色域，避免每个新增持杖取景都重复解码五遍整段录制。
    metric = spec.get("metric", "blue")
    blue_cover = [int(blue_mask(f).sum()) for f in frames] if metric not in ("wand", "warm", "orange", "white", "violet_model") else []
    red_cover = [int(red_mask(f).sum()) for f in frames] if "breach" in spec or "breach_fade" in spec else []
    # 第一人称蓄能取杖首上方，排除收杖后原版裸手的肤色；保留同样的可见性/退场门槛。
    warm_roi = spec.get("warm_roi")
    warm_cover = [int((warm_mask(f)[warm_roi[1]:warm_roi[3], warm_roi[0]:warm_roi[2]] if warm_roi else warm_mask(f)).sum()) for f in frames] if metric == "warm" else []
    white_roi = spec.get("white_roi")
    white_cover = [int((white_spell_mask(f)[white_roi[1]:white_roi[3], white_roi[0]:white_roi[2]] if white_roi else white_spell_mask(f)).sum()) for f in frames] if metric == "white" else []
    orange_cover = [int(orange_mask(f).sum()) for f in frames] if metric == "orange" else []
    dark_cover = [int(dark_mask(f).sum()) for f in frames] if "dark_core" in spec else []
    # 标准判据（空场起飞 / 真的渲染 / 退场 / 静止窗口）用哪一个像素判据：默认蓝（冷色粒子），
    # 爆炸场景声明 `metric: warm`（饱和暖色）；火球拖尾声明 `metric: orange`（加法混合的橙黄火花，
    # 见 `orange_mask` 的标定说明）。粉色破口判据始终独立于它。
    violet_cover = [int(violet_model_mask(f).sum()) for f in frames] if metric == "violet_model" else []
    cover = [int(wand_mask(f).sum()) for f in frames] if metric == "wand" else {"violet_model": violet_cover, "warm": warm_cover, "orange": orange_cover, "white": white_cover}.get(metric, blue_cover)
    result["cover"] = cover
    result["blue_cover"] = blue_cover
    result["red_cover"] = red_cover
    result["white_cover"] = white_cover
    result["warm_cover"] = warm_cover
    result["orange_cover"] = orange_cover
    result["dark_cover"] = dark_cover
    peak = max(cover)
    peak_idx = cover.index(peak)
    total_px = Image.open(frames[0]).size[0] * Image.open(frames[0]).size[1]
    empty = max(1, int(peak * spec.get("empty_ratio", EMPTY_RATIO)))

    # 「完整」= 每个 tick 要么出了帧、要么被如实记为丢帧：录制是同步回读帧缓冲，
    # 偶发一次 GPU 同步 stall 就会少一两帧（那是录制环境的问题，不是动画的问题）；
    # 丢帧率另有预算（MAX_DROP_RATIO）单独卡，丢成片段才会 FAIL。
    result["asserts"].append((
        "帧数完整",
        len(frames) + dropped == length,
        f"{len(frames)}/{length} 帧 + 丢帧 {dropped}",
    ))
    drop_budget = max(1, int(length * MAX_DROP_RATIO))
    result["asserts"].append(
        ("丢帧在预算内", dropped <= drop_budget, f"framesDropped={dropped}（预算 ≤{drop_budget}）"),
    )
    result["asserts"].append(
        ("场景内断言全过", all(ok for _, ok in checks) and bool(checks),
         f"{sum(1 for _, ok in checks if ok)}/{len(checks)}"),
    )
    if spec.get("metric") == "wand":
        missing = [line for line in log_lines if "Missing texture" in line and "primalspells:item/wand" in line]
        result["asserts"].append(("法杖材质完整", not missing, f"缺失材质警告={len(missing)}"))
    result["asserts"].append(("空场起飞", max(cover[:2]) <= empty, f"前 2 帧特效像素 {cover[:2]}（阈值 ≤{empty}）"))
    result["asserts"].append(("特效确实渲染", peak >= total_px * MIN_PEAK_RATIO, f"峰值 {peak} 像素 @帧 {peak_idx}"))
    result["asserts"].append(("退场无残留", max(cover[-3:]) <= empty, f"末 3 帧 {cover[-3:]}（阈值 ≤{empty}）"))

    if scene == "wand_design":
        ticks = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        for tick, right in ((26, True), (66, False), (118, False)):
            mask = violet_model_mask(ticks[tick])
            ys, xs = np.where(mask)
            bounds = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())) if len(xs) else (0, 0, 0, 0)
            centered = int(mask[200:460, 480:800].sum())
            lower_side = len(xs) >= 100 and bounds[1] >= 350 and (bounds[0] >= 800 if right else bounds[2] <= 480)
            result["asserts"].append((f"低位紫晶与中央视野留空-{tick}", lower_side and centered == 0,
                f"紫晶范围={bounds}，中央覆盖={centered}，要求模型位于下半屏外侧"))
        missing = [line for line in log_lines if "Missing texture" in line and "primalspells:item/wand" in line]
        result["asserts"].append(("暮紫封印图集完整", not missing, f"缺失贴图={len(missing)}"))

    if scene == "magic_hud":
        tick_frames = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        def feedback_mask(t, red=False):
            rgb = np.asarray(Image.open(tick_frames[t]).convert("RGB"), dtype=np.int16)
            r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
            return ((r > 150) & (g < 145) & (b > 40) & (r - b > 60)) if red else ((r > 100) & (b > 170) & (b - r > 20) & (b - g > 35))
        idle = [int(feedback_mask(t)[300:420, 580:700].sum()) for t in (24, 27, 39)]
        hit = max(int(feedback_mask(t)[310:410, 590:690].sum()) for t in (56, 57, 58))
        kill = feedback_mask(62, red=True)
        corners = [int(kill[y:y+28, x:x+28].sum()) for y in (321, 371) for x in (601, 651)]
        cardinal = int(kill[318:402, 638:643].sum()) + int(kill[358:363, 598:682].sum())
        right = int(feedback_mask(70, red=True)[330:390, 696:734].sum())
        left = int(feedback_mask(70, red=True)[330:390, 546:584].sum())
        front = int(feedback_mask(77, red=True)[270:310, 610:670].sum())
        result["asserts"].append(("准心不再重复显示充能菱框", max(idle) == 0, f"静止/充能窗口紫色像素={idle}"))
        result["asserts"].append(("真实命中魔法十字可见", hit >= 50, f"中心紫色覆盖={hit}"))
        result["asserts"].append(("真实击杀只有红色斜十字", min(corners) >= 10 and cardinal == 0,
            f"四角红色覆盖={corners}，水平/竖直中心线红色={cardinal}"))
        result["asserts"].append(("真实受击方向弧可见", right >= 10 and left == 0 and front >= 10,
            f"右/左/前向红色覆盖={right}/{left}/{front}"))
        # 前半程描出笔画，后半程径向移走，验证实际像素而不是只读取动画参数。
        radii = []
        for t in (61, 62, 64):
            ys, xs = np.where(feedback_mask(t, red=True)[300:420, 580:700])
            radii.append(int(np.max(np.maximum(np.abs(xs - 60), np.abs(ys - 60)))) if len(xs) else 0)
        result["asserts"].append(("击杀从中心画出并向外散开", 0 < radii[0] < radii[1] < radii[2], f"斜十字半径={radii}"))
        protected = [int((feedback_mask(t) | feedback_mask(t, red=True))[336:384, 616:664].sum())
                     for t in range(56, 70)]
        result["asserts"].append(("命中和击杀与原版准星留空", max(protected) == 0,
            f"准星外围保护区最大彩色像素={max(protected)}"))

    if scene == "new_spells":
        tick_frames = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        blades = [white_spell_mask(tick_frames[t])[340:450, 700:850] for t in (80, 90)]
        counts = [int(a.sum()) for a in blades]
        result["asserts"].append(("锯片本体持续可见且旋转移动", min(counts) >= 50 and int((blades[0] != blades[1]).sum()) >= 20,
            f"独立锯片覆盖={counts}，图形变化={int((blades[0] != blades[1]).sum())}像素"))

    if scene == "pd_api":
        tick_frames = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        # 固定机位下只框住右侧track方片，排除左侧受光片和中间编排/变量夹具。
        counts = {t: int(blue_mask(tick_frames[t])[410:520, 700:850].sum())
                  for t in (10, 24, 65) if t in tick_frames}
        result["asserts"].append(("跟踪片元出生与熄灭曲线真正渲染",
            len(counts) == 3 and counts[24] > 1000 and counts[10] < counts[24] * 0.25 and counts[65] < counts[24] * 0.25,
            f"游戏刻10/24/65的独立片元覆盖={counts}"))

    if scene in ("wand_casting_first_person", "wand_casting_wide"):
        # 杖首由原生模型idle承担：未施法窗口不再出现错位的眼锚悬浮符印。
        tick_frames = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        idle_pixels = []
        for tick in range(22, 28):
            a = np.asarray(Image.open(tick_frames[tick]).convert("RGB"), dtype=np.int16)[20:440, 640:1220]
            r, g, b = a[..., 0], a[..., 1], a[..., 2]
            idle_pixels.append(int(((r <= 150) & (g >= 225) & (b >= 245)).sum()))
        result["asserts"].append(("杖首无错位悬浮符印", max(idle_pixels) <= 8,
            f"未施法窗口青色悬浮片元{idle_pixels}；模型idle另由实机属性断言验证"))

    # 多系统场景可指定独立时间窗；保留同样的最低像素数与渐隐跨度门槛。
    fade_cover, fade_peak, fade_idx, fade_empty = cover, peak, peak_idx, empty
    if "fade_window" in spec:
        lo, hi = spec["fade_window"]
        rows = read_tsv(sdir / "frames.tsv")
        fade_cover = [cover[int(row[0])] for row in rows if lo <= int(row[1]) < hi]
        fade_peak = max(fade_cover, default=0)
        fade_idx = fade_cover.index(fade_peak) if fade_cover else 0
        fade_empty = max(1, int(fade_peak * EMPTY_RATIO))
        result["asserts"].append(("独立效果确实渲染", fade_peak >= total_px * spec.get("fade_min_peak_ratio", MIN_PEAK_RATIO),
            f"游戏 tick {lo}-{hi} 峰值 {fade_peak} 像素（排除其它视觉系统）"))
    if scene in ("wand_casting_first_person", "wand_casting_wide"):
        tick_frames = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        center = []
        for t in range(22, 28):
            # 杖身银色材质也通过通用冷色判据，使用与idle门禁相同的符印色域排除模型。
            roi = np.asarray(Image.open(tick_frames[t]).convert("RGB"), dtype=np.int16)[240:450, 520:760]
            r, g, b = roi[..., 0], roi[..., 1], roi[..., 2]
            center.append(int(((r <= 150) & (g >= 225) & (b >= 245)).sum()))
        result["asserts"].append(("待机不遮挡准星周围", max(center) <= 8,
            f"首次施法前中央240×210区域覆盖{center}，要求≤8像素"))

    # 尺寸退场：从峰值衰减到 50% 与 <5% 的位置差
    t50 = next((i for i in range(fade_idx, len(fade_cover)) if fade_cover[i] <= fade_peak * 0.5), len(fade_cover) - 1)
    tend = next((i for i in range(t50, len(fade_cover)) if fade_cover[i] <= fade_empty), len(fade_cover) - 1)
    span = tend - t50
    if spec.get("short_exit"):
        # 小颗粒栅格化在50%附近差几个像素就会移一整帧，观察整段起落并约束最大单帧丢失。
        begin = next((i for i in range(fade_idx, len(fade_cover)) if fade_cover[i] <= fade_peak * .95), len(fade_cover)-1)
        extent = tend - begin
        largest = max((max(0, fade_cover[i]-fade_cover[i+1]) for i in range(fade_idx, len(fade_cover)-1)), default=fade_peak)
        zero = next((i for i in range(tend, len(fade_cover)) if fade_cover[i] == 0), len(fade_cover)-1)
        last = fade_cover[zero-1] if zero > 0 else fade_peak
        result["asserts"].append(("短促退场连续且无末帧硬切",
            3 <= extent <= 10 and largest <= fade_peak * .35 and last <= fade_peak * .10,
            f"95%→5%跨{extent}帧，最大单帧丢失{largest/max(1,fade_peak):.1%}，归零前覆盖{last/max(1,fade_peak):.1%}；要求3–10帧/≤35%/≤10%"))
    elif spec.get("decay") == "alpha":
        alpha_checks = [ok for name, ok in checks if name == "trail-alpha-decreases"]
        result["asserts"].append(("实际尾迹逐颗连续淡出", len(alpha_checks) == 1 and alpha_checks[0],
            "读取原生PD粒子逐帧透明度；高速尾迹短寿命不以整场50%覆盖量跨4刻代替"))
    elif spec.get("decay", "gradual") in ("gradual", "collapse"):
        result["asserts"].append(
            ("实色结构解体/颗粒分批结束" if spec.get("decay") == "collapse" else "退场是渐变而非闪现",
             span >= MIN_DECAY_FRAMES, f"覆盖量50%→5% 跨 {span} 帧（要求 ≥{MIN_DECAY_FRAMES}）"),
        )
    elif spec.get("decay", "gradual") == "none":
        # 这一场的退场由专属判据负责（spec 里必须有对应断言，见 `breach_fade`）
        result["asserts"].append(
            ("退场判定交由专属判据", "breach_fade" in spec or "quiet" in spec,
             spec.get("decay_reason", "本场的收场不是蓝色像素（见「破口逐步收缩并收干净」/「退场无残留」）")),
        )
    else:
        # 这类效果的退场**设计上**就是立刻清理（没有退场动画）：只要求别拖泥带水
        result["asserts"].append(
            ("退场立即且干净", span <= INSTANT_DECAY_MAX_FRAMES, f"50%→5% 跨 {span} 帧（要求 ≤{INSTANT_DECAY_MAX_FRAMES}）"),
        )

    if spec.get("wand_cooldowns"):
        # 固定第三人称机位，脚下范围排除模型和上方火球；独立验证两种冷却完成后的收尾。
        ticks = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}

        def cooldown_pixels(tick, charge=False, entry=False):
            roi = (slice(460, 700), slice(320, 960))
            a = np.asarray(Image.open(ticks[tick]).convert("RGB"), dtype=np.int16)[roi]
            r, g, b = a[..., 0], a[..., 1], a[..., 2]
            mask = ((r <= 110) & (g >= (65 if entry else 135)) & (g <= 190) & (b >= (160 if entry else 225)) & ((b-g) > 78)) if charge else (
                (r >= 220) & (g >= 230) & (b >= 245) & ((b-r) > 12))
            return int(mask.sum())

        def triangle_pixels(tick):
            # 只看法阵的青绿色，排除拿起法杖时同样进入ROI的银色模型与白/蓝冷却条。
            a = np.asarray(Image.open(ticks[tick]).convert("RGB"), dtype=np.int16)[460:700, 320:960]
            r, g, b = a[..., 0], a[..., 1], a[..., 2]
            return int(((r < 170) & (g >= 190) & (b >= 210)).sum())
        entry_mask = [triangle_pixels(t) for t in range(8, 17)]
        result["asserts"].append(("法阵首次入场无完整形态闪现", max(entry_mask[:3]) <= max(entry_mask) * .10 and entry_mask[-1] > entry_mask[3],
            f"入场8–16刻覆盖={entry_mask}，首3刻≤最终10%并持续展开"))

        switch = [triangle_pixels(t) for t in range(94, 113)]
        jump = max(max(0, switch[i+1]-switch[i]) for i in range(len(switch)-1))
        result["asserts"].append(("切杖时旧法阵退场与新法阵入场无闪亮尖峰", jump <= max(switch)*.40 and max(switch) <= max(switch[0],switch[-1])*1.35,
            f"切杖94–112刻覆盖={switch}，最大单帧增加={jump/max(1,max(switch)):.1%}"))
        for label, charge in [("施法恢复白色内边", False), ("回洗聚能蓝色外边", True)]:
            hidden = [cooldown_pixels(t, charge, entry=True) for t in range(28, 32)]
            result["asserts"].append((label + "出生时无完整形态闪现", hidden[0] <= max(hidden) * .25 and hidden[-1] > hidden[0],
                f"施法当刻起覆盖={hidden}，首帧≤展开峰值25%"))
            entry = [cooldown_pixels(t, charge, entry=True) for t in range(29, 32)]
            first = max(entry)
            seq = [cooldown_pixels(t, charge) for t in (range(65, 73) if charge else range(45, 52))]
            result["asserts"].append((label + "施法后连续展开", first >= 30 and entry[-1] >= entry[0],
                f"施法后前三tick覆盖{entry}像素，150ms内展开且不能等待半程"))
            result["asserts"].append((label + "短促收尾后归零",
                max(seq) > 30 and sum(v > 10 for v in seq) >= 2 and seq[-1] <= max(seq) * .05,
                f"独立颜色覆盖{seq}，至少2帧可见且末尾≤5%；150–175ms收尾"))

    # 护盾专属：静止窗口必须干净、受击窗口必须有多次「亮起→消失」
    if "quiet" in spec:
        lo, hi = spec["quiet"]
        mx = max(cover[lo:hi]) if cover[lo:hi] else 0
        result["asserts"].append(
            ("平时完全不可见", mx <= empty, f"静止窗口 {lo}-{hi} 帧最大覆盖 {mx} 像素（阈值 ≤{empty}）"),
        )
    if "hits" in spec:
        lo, hi = spec["hits"]
        seg = cover[lo:hi]
        # ⚠️ 基线取**窗口内**：场景后面可能有量级大得多的特效（第 29 轮起爆炸是青白色，
        # 峰值 28 万像素），用全局空场阈值会把窗口里的玻璃板亮点（~1.4k 像素）全判成"不亮"。
        local = max(seg) if seg else 0
        floor = max(local * 0.25, 2.0)
        peaks = sum(
            1 for i in range(1, len(seg) - 1)
            if seg[i] > floor and seg[i] >= seg[i - 1] and seg[i] > seg[i + 1]
        )
        need = spec.get("min_peaks", 2)
        result["asserts"].append(
            ("受击才亮起（多次亮点）", peaks >= need,
             f"受击窗口 {lo}-{hi} 帧检出 {peaks} 个亮点（窗口内基线 {floor:.0f}，要求 ≥{need}）"),
        )
    if "breach" in spec:
        lo, hi = spec["breach"]
        seg = red_cover[lo:hi]
        red_peak = max(seg) if seg else 0
        result["asserts"].append(
            ("破口真的渲染", red_peak >= total_px * MIN_PEAK_RATIO,
             f"破口窗口 {lo}-{hi} 暖色峰值 {red_peak} 像素"),
        )
        persist = sum(1 for v in seg if v >= red_peak * 0.4)
        result["asserts"].append(
            ("破口常驻显示", persist >= spec.get("min_breach_frames", 20),
             f"持续 {persist}/{len(seg)} 帧 ≥40% 峰值"),
        )
    if "dark_core" in spec:
        lo, hi = spec["dark_core"]
        seg = dark_cover[lo:hi]
        peak = max(seg) if seg else 0
        persist = sum(1 for v in seg if v >= peak * 0.5)
        result["asserts"].append(
            ("蓄力期有黑色核心",
             peak >= total_px * MIN_PEAK_RATIO and persist >= spec.get("min_dark_frames", 5),
             f"暗像素峰值 {peak}、{persist}/{len(seg)} 帧 ≥50% 峰值"),
        )
    if "breach_fade" in spec:
        lo, hi = spec["breach_fade"]
        seg = red_cover[lo:hi]
        visible = sum(1 for v in seg if v > 0)
        result["asserts"].append(
            ("破口逐步收缩并收干净",
             visible >= spec.get("min_fade_frames", 3) and seg and seg[-1] == 0,
             f"尾段 {lo}-{hi} 有 {visible} 帧仍可见、末帧 {seg[-1] if seg else '-'}"),
        )
    if scene in ("black_hole", "black_hole_moving"):
        # 纯结构塌缩独立于运动星尘，按固定边界色域观察死亡后时窗。
        tick_frames = {int(row[1]): frames[int(row[0])] for row in read_tsv(sdir / "frames.tsv")}
        boundary = []
        for tick in range(70, 85):
            a = np.asarray(Image.open(tick_frames[tick]).convert("RGB"), dtype=np.int16)
            boundary.append(int(((abs(a[..., 0]-110) <= 2) & (abs(a[..., 1]-74) <= 2) & (abs(a[..., 2]-174) <= 2)).sum()))
        bp = max(boundary); bi = boundary.index(bp)
        half = next((i for i in range(bi, len(boundary)) if boundary[i] <= bp * .5), len(boundary)-1)
        clean = next((i for i in range(half, len(boundary)) if boundary[i] <= bp * .05), len(boundary)-1)
        result["asserts"].append(("黑洞固定边界连续塌缩", bp >= 50 and 2 <= clean-half <= 8 and max(boundary[12:]) == 0,
            f"死亡后边界覆盖{boundary}，50%→5%跨{clean-half}帧，12tick后严格归零；运动星尘独立"))

    if scene == "black_hole_moving":
        # 固定镜头只取BOUNDARY_COLOR色板；固定吸积盘与外环已经移除。
        # 用frames.tsv游戏tick定位；已发射的历史星尘不参与本体跟随判据。
        rows = [row.split("\t") for row in (sdir / "frames.tsv").read_text(encoding="utf-8").splitlines() if row.strip()]
        centers = []
        for tick in (18, 41, 66):
            row = min(rows, key=lambda row: abs(int(row[1]) - tick))
            with Image.open(sdir / "frames" / f"{int(row[0]):05d}.png") as im:
                rgb = np.asarray(im.convert("RGB"), dtype=np.int16)
            # BOUNDARY_COLOR=110/74/174；容忍RGB取整，仍与两种运动星尘色板分离。
            _, xs = np.where((rgb[:, :, 0] >= 108) & (rgb[:, :, 0] <= 112)
                & (rgb[:, :, 1] >= 72) & (rgb[:, :, 1] <= 76) & (rgb[:, :, 2] >= 172) & (rgb[:, :, 2] <= 176))
            centers.append(float(np.median(xs)) if len(xs) else float("nan"))
        start, first_stop, reversed_stop = centers
        result["asserts"].append((
            "边界星点随本体往返移动", start - first_stop > 60 and reversed_stop - start > 60,
            f"t=18/41/66 横向中心 {centers}，两侧离开出生点均须 >60px",
        ))

    # 视频/动图/拼图
    ok_mp4, err_mp4 = build_video(sdir / "frames", sdir / f"{scene}.mp4", fps)
    ok_gif, err_gif = build_gif(sdir / "frames", sdir / f"{scene}.gif", fps, 640)
    result["asserts"].append(("生成 mp4", ok_mp4, err_mp4 or (sdir / f"{scene}.mp4").name))
    result["asserts"].append(("生成 gif", ok_gif, err_gif or (sdir / f"{scene}.gif").name))
    build_contact(frames, sdir / "contact.png", scene)
    return result


def classify_log_errors(log_lines: list[str]) -> tuple[list[str], list[str]]:
    """按完整日志记录识别OSHI诊断及其堆栈，返回阻塞项和已忽略项；不修改原日志。"""
    header = re.compile(r"^\[[^\r\n]*?\]\s+\[[^\]\r\n]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]")
    process_info = re.compile(
        r"\[net\.minecraft\.SystemReport/\]: Failed to get system info for "
        r"(?:Process Loads|Process Virtual Size \(MiB\)|Process Resident Size \(MiB\))\s*$"
    )
    null_process = re.compile(
        r'java\.lang\.NullPointerException: Cannot invoke "oshi\.software\.os\.OSProcess\.'
        r'get(?:UserTime|VirtualSize|ResidentSetSize)\(\)" because "process" is null'
    )
    blocks: list[list[str]] = []
    for line in log_lines:
        if header.match(line) or not blocks:
            blocks.append([])
        blocks[-1].append(line)
    bad: list[str] = []
    ignored: list[str] = []
    for block in blocks:
        first = block[0]
        # 用户要求忽略OSHI系统信息采集；其他JNA/NPE、模组报错和FATAL不在例外内。
        known_oshi = bool(header.match(first)) and "/FATAL]" not in first and (
            "[oshi." in first or (
                process_info.search(first) is not None
                and any(null_process.search(line) for line in block[1:])
            )
        )
        candidates = [line for line in block if (
            "Exception" in line or "ERROR]" in line or "/FATAL]" in line
        ) and "[Capture] write frame" not in line]
        (ignored if known_oshi else bad).extend(candidates)
    return bad, ignored


def write_report(results: list[dict], root: Path, log_lines: list[str]) -> bool:
    lines: list[str] = []
    lines.append("=== Primal Spells 实机录制报告 ===")
    lines.append("")
    lines.append("判定口径：帧数/丢帧 + 场景内断言 + 特效像素覆盖（空场起飞 / 真的渲染 / 收干净 / 尺寸退场）")
    lines.append("")
    all_ok = True
    for r in results:
        ok = all(a[1] for a in r["asserts"])
        all_ok = all_ok and ok
        lines.append(f"--- {r['scene']}  [{r['title']}]  {'PASS' if ok else 'FAIL'}")
        lines.append(f"    帧数 {r['frames']}/{r['length']}  丢帧 {r['dropped']}  fps {r['fps']}")
        if r["cover"]:
            peak = max(r["cover"])
            lines.append(f"    特效像素：首 {r['cover'][:3]} 峰 {peak} 末 {r['cover'][-3:]}")
        for name, good, detail in r["asserts"]:
            lines.append(f"    [{'ok' if good else 'XX'}] {name}: {detail}")
        lines.append(f"    产物：{r['dir']}")
        lines.append("")

    bad, ignored = classify_log_errors(log_lines)
    lines.append(f"--- 运行日志：{len(log_lines)} 行，阻塞异常/ERROR {len(bad)} 行，忽略OSHI {len(ignored)} 行")
    for ln in bad[:10]:
        lines.append(f"    !! {ln.strip()[:220]}")
    if bad:
        all_ok = False

    lines.append("")
    lines.append(f"总体：{'全部通过' if all_ok else '存在失败项'}")
    text = "\n".join(lines)
    (root / "report.txt").write_text(text, encoding="utf-8")
    print(text)
    return all_ok


def write_gallery(results: list[dict], root: Path) -> None:
    rows = []
    for r in results:
        ok = all(a[1] for a in r["asserts"])
        name = r["scene"]
        failed = [f"{a[0]}: {a[2]}" for a in r["asserts"] if not a[1]]
        rows.append(f"""
  <section class="scene">
    <h2>{name} <span class="tag">{r['title']}</span> <span class="{'pass' if ok else 'fail'}">{'PASS' if ok else 'FAIL'}</span></h2>
    <video src="{name}/{name}.mp4" controls loop muted playsinline></video>
    <p class="meta">{r['frames']} 帧 @ {r['fps']}fps（每 tick 一帧）　丢帧 {r['dropped']}　场景内断言 {sum(1 for _, o in r['checks'] if o)}/{len(r['checks'])}</p>
    <img class="gif" src="{name}/{name}.gif" alt="{name} 动图">
    <img class="sheet" src="{name}/contact.png" alt="{name} 拼图">
    {''.join(f'<p class="fail">✗ {f}</p>' for f in failed)}
  </section>""")
    html = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>Primal Spells 实机录制</title>
<style>
  :root {{ color-scheme: dark; }}
  body {{ background:#111318; color:#dfe3ea; font:14px/1.6 "Segoe UI",system-ui,sans-serif; margin:0; padding:24px 32px; }}
  h1 {{ font-size:22px; margin:0 0 4px; }}
  .lead {{ color:#8b93a3; margin:0 0 20px; }}
  .scene {{ border:1px solid #232a35; border-radius:10px; padding:14px 16px; margin-bottom:22px; background:#161a21; }}
  h2 {{ font-size:16px; margin:0 0 10px; }}
  .tag {{ font-weight:400; color:#8b93a3; font-size:13px; }}
  .pass {{ color:#57d38c; }} .fail {{ color:#ff6b6b; }}
  video {{ width:100%; max-width:960px; border-radius:8px; display:block; background:#000; }}
  img.gif {{ width:100%; max-width:640px; margin-top:12px; border-radius:8px; }}
  img.sheet {{ width:100%; max-width:1100px; margin-top:12px; border-radius:8px; }}
  .meta {{ color:#8b93a3; margin:8px 0 0; }}
  code {{ background:#0d1014; padding:1px 5px; border-radius:4px; }}
</style></head><body>
<h1>Primal Spells 实机录制</h1>
<p class="lead">由 <code>tools/capture/capture.ps1</code> 自动生成：真客户端 + 集成服务端跑真实施法路径，
每个 tick 抓一帧（20fps），再合成视频。画面里的黑底摄影棚是为看清粒子对比度布置的取景环境。</p>
{''.join(rows)}
</body></html>
"""
    (root / "index.html").write_text(html, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="build/capture/out")
    ap.add_argument("--no-video", action="store_true", help="只做判定与拼图，不调 ffmpeg")
    args = ap.parse_args()

    root = Path(args.out).resolve()
    if not root.is_dir():
        print(f"[assemble] 找不到录制输出目录：{root}", file=sys.stderr)
        return 2

    log_path_candidates = [Path("build/capture/capture.log"), Path("run/capture/logs/latest.log")]
    log_lines: list[str] = []
    for p in log_path_candidates:
        if p.exists():
            log_lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
            break

    results = []
    for scene, spec in SCENES.items():
        if not (root / scene / "frames").is_dir():
            continue
        results.append(analyse(scene, spec, root, log_lines))

    if not results:
        print(f"[assemble] {root} 下没有任何场景目录", file=sys.stderr)
        return 2

    ok = write_report(results, root, log_lines)
    write_gallery(results, root)
    print(f"\n[assemble] 画廊：{root / 'index.html'}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
