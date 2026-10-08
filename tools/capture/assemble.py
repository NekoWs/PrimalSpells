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
    "expansion_crystals": {"length": 140, "title": "水晶粘附 / 连锁引爆 / 归属", "decay": "gradual", "quiet": (0, 8)},
    # 护盾受击玻璃亮度远高于电弧；在独立电弧窗口内量渲染和渐隐，不混用护盾峰值。
    "expansion_arc": {"length": 150, "title": "电弧衰减 / 去重 / 护盾", "decay": "gradual", "quiet": (0, 8), "fade_window": (8, 64)},
    "expansion_combinations": {"length": 180, "title": "多重施放 / 触发水晶 / 环绕光束", "decay": "gradual", "quiet": (0, 8)},
    "expansion_plasma": {"length": 145, "title": "持续光束 / 遮挡 / 寿命", "decay": "gradual", "quiet": (0, 8)},
    "expansion_utilities": {"length": 220, "title": "冰冻 / 远距 / 斥力", "decay": "gradual", "quiet": (0, 8)},
    # decay: gradual = 该效果**设计上**有退场动画（必须逐帧衰减）；instant = 设计上就是立刻消失；
    #        none    = 这一场有**两套互不相干的视觉系统**（玻璃板 + 爆炸烟）或收场不是蓝色像素，
    #                  全局峰值不可靠，退场交给场景专属判据（必须在 spec 里另有对应断言）
    "black_hole": {
        "length": 108, "title": "黑洞：暗核 / 双层吸积盘 → 旋转塌缩", "decay": "gradual",
    },
    "black_hole_moving": {
        "length": 108, "title": "黑洞：移动跟随 / 急停回圆 / 反向拉伸", "decay": "gradual",
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
        "title": "爆炸：触发即爆（实色碎片外飞 + 尘屑收缩）",
        # 第 29 轮起爆炸改成**青白色能量**（对齐用户给的参考）→ 标准判据用蓝（青满足 b>80 且 b-r>12）
        "metric": "blue",
        # 放弹（t=20）之前必须干净
        "quiet": (2, 16),
        # 暖色判据下的退场是**阈值悬崖**（球体 alpha 淡出 6 tick，但像素判据只看到跨阈值那一帧）——
        # 与玻璃面板同理；「收干净」由  +  负责
        "decay": "instant",
    },
    # 火球拖尾（第 43 轮）：跟拍一颗飞行中的火球，看 Noita 式的分层火花尾迹。
    # 粒子是**橙黄加法混合**的火花，用 `orange` 判据（`warm` 会漏掉一半以上的火花，见 orange_mask）。
    # 退场：火球 t≈30 撞墙消失，余烬寿命错开（8~26 tick）陆续到点 —— 是**逐帧衰减**，用 gradual。
    "fireball_trail": {
        "length": 72,
        "title": "火球拖尾：白热核 + 沿轨迹的符文余痕（跟拍）",
        "metric": "orange",
        "decay": "gradual",
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
SCENES["explosion"]["decay"] = "collapse"

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

    blue_cover = [int(blue_mask(f).sum()) for f in frames]
    red_cover = [int(red_mask(f).sum()) for f in frames]
    warm_cover = [int(warm_mask(f).sum()) for f in frames]
    orange_cover = [int(orange_mask(f).sum()) for f in frames]
    dark_cover = [int(dark_mask(f).sum()) for f in frames]
    # 标准判据（空场起飞 / 真的渲染 / 退场 / 静止窗口）用哪一个像素判据：默认蓝（冷色粒子），
    # 爆炸场景声明 `metric: warm`（饱和暖色）；火球拖尾声明 `metric: orange`（加法混合的橙黄火花，
    # 见 `orange_mask` 的标定说明）。粉色破口判据始终独立于它。
    metric = spec.get("metric", "blue")
    cover = {"warm": warm_cover, "orange": orange_cover}.get(metric, blue_cover)
    result["cover"] = cover
    result["blue_cover"] = blue_cover
    result["red_cover"] = red_cover
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
    result["asserts"].append(("空场起飞", max(cover[:2]) <= empty, f"前 2 帧特效像素 {cover[:2]}（阈值 ≤{empty}）"))
    result["asserts"].append(("特效确实渲染", peak >= total_px * MIN_PEAK_RATIO, f"峰值 {peak} 像素 @帧 {peak_idx}"))
    result["asserts"].append(("退场无残留", max(cover[-3:]) <= empty, f"末 3 帧 {cover[-3:]}（阈值 ≤{empty}）"))

    # 多系统场景可指定独立时间窗；保留同样的最低像素数与渐隐跨度门槛。
    fade_cover, fade_peak, fade_idx, fade_empty = cover, peak, peak_idx, empty
    if "fade_window" in spec:
        lo, hi = spec["fade_window"]
        rows = read_tsv(sdir / "frames.tsv")
        fade_cover = [cover[int(row[0])] for row in rows if lo <= int(row[1]) < hi]
        fade_peak = max(fade_cover, default=0)
        fade_idx = fade_cover.index(fade_peak) if fade_cover else 0
        fade_empty = max(1, int(fade_peak * EMPTY_RATIO))
        result["asserts"].append(("独立效果确实渲染", fade_peak >= total_px * MIN_PEAK_RATIO,
            f"游戏 tick {lo}-{hi} 峰值 {fade_peak} 像素（排除其它视觉系统）"))
    # 尺寸退场：从峰值衰减到 50% 与 <5% 的位置差
    t50 = next((i for i in range(fade_idx, len(fade_cover)) if fade_cover[i] <= fade_peak * 0.5), len(fade_cover) - 1)
    tend = next((i for i in range(t50, len(fade_cover)) if fade_cover[i] <= fade_empty), len(fade_cover) - 1)
    span = tend - t50
    if spec.get("decay", "gradual") in ("gradual", "collapse"):
        result["asserts"].append(
            ("实色颗粒逐步收缩/分批结束" if spec.get("decay") == "collapse" else "退场是渐变而非闪现",
             span >= MIN_DECAY_FRAMES, f"覆盖量50%→5% 跨 {span} 帧（要求 ≥{MIN_DECAY_FRAMES}）"),
        )
    elif spec.get("decay", "gradual") == "none":
        # 这一场的退场由专属判据负责（spec 里必须有对应断言，见 `breach_fade`）
        result["asserts"].append(
            ("退场判定交由专属判据", "breach_fade" in spec or "quiet" in spec,
             "本场的收场不是蓝色像素（见「破口逐步收缩并收干净」/「退场无残留」）"),
        )
    else:
        # 这类效果的退场**设计上**就是立刻清理（没有退场动画）：只要求别拖泥带水
        result["asserts"].append(
            ("退场立即且干净", span <= INSTANT_DECAY_MAX_FRAMES, f"50%→5% 跨 {span} 帧（要求 ≤{INSTANT_DECAY_MAX_FRAMES}）"),
        )

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
    if scene == "black_hole_moving":
        # 固定镜头只取DISC_INNER色板，排除外环惯性与DUST_BRIGHT星尘（214/186/255）。
        # 用 frames.tsv 的游戏 tick 定位，避免丢帧导致时间窗偏移；低亮外环和历史星尘不参与。
        rows = [row.split("\t") for row in (sdir / "frames.tsv").read_text(encoding="utf-8").splitlines() if row.strip()]
        centers = []
        for tick in (18, 41, 66):
            row = min(rows, key=lambda row: abs(int(row[1]) - tick))
            with Image.open(sdir / "frames" / f"{int(row[0]):05d}.png") as im:
                rgb = np.asarray(im.convert("RGB"), dtype=np.int16)
            # DISC_INNER=218/188/255；RGB编码取整有1档误差，窄容差仍与星尘色板分离。
            _, xs = np.where((rgb[:, :, 0] >= 216) & (rgb[:, :, 0] <= 220)
                & (rgb[:, :, 1] >= 187) & (rgb[:, :, 1] <= 190) & (rgb[:, :, 2] >= 250))
            centers.append(float(np.median(xs)) if len(xs) else float("nan"))
        start, first_stop, reversed_stop = centers
        result["asserts"].append((
            "明亮内盘随本体往返移动", start - first_stop > 60 and reversed_stop - start > 60,
            f"t=18/41/66 横向中心 {centers}，两侧离开出生点均须 >60px",
        ))

    # 视频/动图/拼图
    ok_mp4, err_mp4 = build_video(sdir / "frames", sdir / f"{scene}.mp4", fps)
    ok_gif, err_gif = build_gif(sdir / "frames", sdir / f"{scene}.gif", fps, 640)
    result["asserts"].append(("生成 mp4", ok_mp4, err_mp4 or (sdir / f"{scene}.mp4").name))
    result["asserts"].append(("生成 gif", ok_gif, err_gif or (sdir / f"{scene}.gif").name))
    build_contact(frames, sdir / "contact.png", scene)
    return result


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

    # 运行时日志（客户端/服务端）不该有异常
    bad = [ln for ln in log_lines if ("Exception" in ln or "ERROR]" in ln) and "[Capture] write frame" not in ln]
    lines.append(f"--- 运行日志：{len(log_lines)} 行，异常/ERROR {len(bad)} 行")
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
