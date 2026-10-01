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

try:
    import numpy as np
    from PIL import Image, ImageDraw
except ImportError as exc:  # pragma: no cover - 环境缺库时给一句人话
    print(f"[assemble] 需要 numpy 与 pillow：{exc}", file=sys.stderr)
    raise SystemExit(2)

# ---------------------------------------------------------------- 场景表

#: 每个场景的期望帧数与判定阈值（帧数必须与 CaptureScenes.kt 里的 lengthTicks 一致）
SCENES = {
    # decay: gradual = 该效果**设计上**有退场动画（必须逐帧衰减）；instant = 设计上就是立刻消失；
    #        none    = 这一场有**两套互不相干的视觉系统**（玻璃板 + 爆炸烟）或收场不是蓝色像素，
    #                  全局峰值不可靠，退场交给场景专属判据（必须在 spec 里另有对应断言）
    "black_hole": {
        "length": 108, "title": "黑洞：进场长出 → 稳态 → 向心塌缩", "decay": "gradual",
    },
    "gravity_field": {
        "length": 130, "title": "重力场：球形范围 + 下坠粒子 + 落地波环", "decay": "instant",
    },
    "explosion": {
        "length": 78,
        "title": "爆炸：触发即爆（粒子云撑到半径 + 外飞 + 烟渐隐）",
        # 第 29 轮起爆炸改成**青白色能量**（对齐用户给的参考）→ 标准判据用蓝（青满足 b>80 且 b-r>12）
        "metric": "blue",
        # 放弹（t=20）之前必须干净
        "quiet": (2, 16),
        # 暖色判据下的退场是**阈值悬崖**（球体 alpha 淡出 6 tick，但像素判据只看到跨阈值那一帧）——
        # 与玻璃面板同理；「收干净」由  +  负责
        "decay": "instant",
    },
    "energy_shield": {
        "length": 232, "title": "玻璃护盾：平时不可见 → 受击玻璃板 → 大爆抵扣 → 法力不足打穿 + 破口",
        # 蓝色判据下**没有**可用的全局退场：这一场混了两套视觉系统 —— 受击玻璃板（一次性、alpha 淡出在
        # 像素判据里就是跨阈值的悬崖）与大爆的原版烟（随机粒子，峰值会与玻璃板逐次运行翻转）。
        # 护盾真正的收场是**粉色**破口轮廓，由下面的 `breach_fade` 单独卡；蓝色这边只保留
        # 「退场无残留」（末 3 帧）与空场起飞。
        "decay": "none",
        # 单块玻璃板只有整罩破盾的百分之几，阈值按峰值 5% 会把它判成「空」——这里单独调低到 2%
        "empty_ratio": 0.02,
        # 静止窗口：护盾平时必须**完全不可见**（这正是实机反馈「太挡视野」要解决的）
        "quiet": (2, 18),
        # 受击窗口：应当出现多次「亮起 → 消失」（每次格挡各出一片）。
        # 窗口从 40 起：攻击者在 5.5 格外，火球飞 11 tick 才到壳上（发射在 t=34/46/58）。
        "hits": (40, 72), "min_peaks": 3,
        # 破口窗口：第 28 轮起大爆要蓄力 18 tick 才结算（放弹 108 → 结算 126），窗口整体后移
        "breach": (120, 170), "min_breach_frames": 35,
        # 破口到期必须是**淡出**：尾段还能看见几帧、然后在窗口末尾归零
        # 淡出：粉色判据看到的是阈值穿越（实测 2124 → 1025 → 0，两帧），设计要求是 6 tick 淡出
        "breach_fade": (172, 188), "min_fade_frames": 2,
    },
}

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

    blue_cover = [int(blue_mask(f).sum()) for f in frames]
    red_cover = [int(red_mask(f).sum()) for f in frames]
    warm_cover = [int(warm_mask(f).sum()) for f in frames]
    dark_cover = [int(dark_mask(f).sum()) for f in frames]
    # 标准判据（空场起飞 / 真的渲染 / 退场 / 静止窗口）用哪一个像素判据：默认蓝（冷色粒子），
    # 爆炸场景声明 `metric: warm`（暖色粒子）。粉色破口判据始终独立于它。
    cover = warm_cover if spec.get("metric", "blue") == "warm" else blue_cover
    result["cover"] = cover
    result["blue_cover"] = blue_cover
    result["red_cover"] = red_cover
    result["warm_cover"] = warm_cover
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

    # 退场渐变：从峰值衰减到 50% 与 <5% 的位置差
    t50 = next((i for i in range(peak_idx, len(cover)) if cover[i] <= peak * 0.5), len(cover) - 1)
    tend = next((i for i in range(t50, len(cover)) if cover[i] <= empty), len(cover) - 1)
    span = tend - t50
    if spec.get("decay", "gradual") == "gradual":
        result["asserts"].append(
            ("退场是渐变而非闪现", span >= MIN_DECAY_FRAMES, f"50%→5% 跨 {span} 帧（要求 ≥{MIN_DECAY_FRAMES}）"),
        )
    elif spec.get("decay", "gradual") == "none":
        # 这一场的退场由专属判据负责（spec 里必须有对应断言，见 `breach_fade`）
        result["asserts"].append(
            ("退场判定交由专属判据", "breach_fade" in spec or "quiet" in spec,
             "本场的收场不是蓝色像素（见「破口是淡出而不是一帧消失」/「退场无残留」）"),
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
            ("破口是淡出而不是一帧消失",
             visible >= spec.get("min_fade_frames", 3) and seg and seg[-1] == 0,
             f"尾段 {lo}-{hi} 有 {visible} 帧仍可见、末帧 {seg[-1] if seg else '-'}"),
        )

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
    lines.append("判定口径：帧数/丢帧 + 场景内断言 + 特效像素覆盖（空场起飞 / 真的渲染 / 收干净 / 退场渐变）")
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
