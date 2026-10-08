#!/usr/bin/env python3
"""生成原创MC方块法杖：杖身、托架与阶梯切面宝石；不生成位图或复用旧轮廓。"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/primalspells/models/item"
ELEMENTS = []


def box(name, start, end, texture, rotation=None, front=None):
    faces = {f: {"texture": "#" + texture, "uv": [0, 0, 16, 16]} for f in
             ["north", "south", "east", "west", "up", "down"]}
    if front:
        faces["north"]["texture"] = "#" + front
    part = {"name": name, "from": start, "to": end, "faces": faces}
    if rotation:
        part["rotation"] = rotation
    ELEMENTS.append(part)


box("ebony_spine", [7.25, -2, 7.25], [8.75, 20.2, 8.75], "shaft")
box("grip", [6.95, 0.8, 6.95], [9.05, 8, 9.05], "grip")
for y in [1, 3, 5, 7]:
    box("grip_band", [6.8, y, 6.8], [9.2, y + 0.45, 9.2], "darkmetal")
for y in [-1.8, 8.1, 13.4, 18.8]:
    box("collar", [6.5, y, 6.5], [9.5, y + 0.8, 9.5], "metal")
box("inlay", [7.7, 9.1, 7.03], [8.3, 18.5, 7.24], "gem")
box("pommel", [6.8, -2.8, 6.8], [9.2, -1.8, 9.2], "metal")
box("cradle", [5.9, 19.4, 5.9], [10.1, 20.8, 10.1], "darkmetal")
box("cradle_rim", [5.65, 20.2, 5.65], [10.35, 20.9, 10.35], "metal")
for x in [4.7, 10.05]:
    box("gold_prong", [x, 20.2, 7.35], [x+1.25, 24.2, 8.65], "metal")
    box("prong_tip", [x, 23.7, 7.15], [x+1.25, 24.5, 8.85], "darkmetal")
for z in [4.7, 10.05]:
    box("gold_prong", [7.35, 20.2, z], [8.65, 22.7, z+1.25], "metal")
# 宝石中心(8,23.4,8)，与第一人称常规持杖矩阵的(.38,.25,-.85)严格配套。
for y, half, height, tex in [(21, 1.25, .8, "gemdark"), (21.8, 1.7, 3.2, "gem"),
                           (25, 1.25, .8, "gemlight"), (25.8, .65, .6, "highlight")]:
    box("gem_facet", [8-half, y, 8-half], [8+half, y+height, 8+half], tex,
        {"origin": [8, 23.4, 8], "axis": "y", "angle": 45}, front="gemlight")


def display(rotation, translation, scale):
    return {"rotation": rotation, "translation": translation, "scale": [scale]*3}


model = {
    "ambientocclusion": False,
    "textures": {
        "particle": "minecraft:block/stripped_dark_oak_log",
        "shaft": "minecraft:block/stripped_dark_oak_log", "grip": "minecraft:block/dark_oak_planks",
        "darkmetal": "minecraft:block/deepslate_tiles", "metal": "minecraft:block/gold_block",
        "gemdark": "minecraft:block/cyan_concrete", "gem": "minecraft:block/light_blue_concrete",
        "gemlight": "minecraft:block/light_blue_concrete", "highlight": "minecraft:block/white_concrete",
    },
    "elements": ELEMENTS,
    "display": {
        "firstperson_righthand": display([0, 0, 0], [-2.88, 0, -2.08], .8),
        "firstperson_lefthand": display([0, 0, 0], [-2.88, 0, -2.08], .8),
        "thirdperson_righthand": display([90, 0, 0], [0, 1.5, 1.5], 1.15),
        "thirdperson_lefthand": display([90, 0, 0], [0, 1.5, 1.5], 1.15),
        "gui": display([15, -35, -38], [0, -3.2, 0], .58),
        "ground": display([0, 0, 0], [0, 3, 0], .45),
        "fixed": display([0, 0, 0], [0, -3, 0], .6),
    },
}
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "wand.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
for tier in range(1, 7):
    # 所有等级共享宝石坐标；低阶铜托，中阶铁托，高阶金托，不再沿用二维旧外观。
    metal = "copper_block" if tier < 3 else "iron_block" if tier < 5 else "gold_block"
    child = {"parent": "primalspells:item/wand", "textures": {"metal": "minecraft:block/" + metal}}
    (OUT / f"wand_t{tier}.json").write_text(json.dumps(child, indent=2) + "\n", encoding="utf-8")
print(f"7 models, {len(ELEMENTS)} cuboids; gem=(8,23.4,8)")
