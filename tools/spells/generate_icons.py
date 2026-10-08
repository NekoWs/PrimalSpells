"""生成第 52 轮七张法术卡的原生像素符号；沿用现有 64px 圆形底牌。"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/primalspells/textures/item"
IDS = ("dormant_crystal", "crystal_detonation", "chain_bolt", "long_distance_cast",
       "plasma_beam", "repulsion_field", "freeze_charge")


def icon(name):
    im = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(im)
    rim = "#ad73e8" if name in ("long_distance_cast", "freeze_charge") else "#ff9f42"
    d.ellipse((1, 1, 30, 30), fill=rim)
    d.ellipse((3, 3, 28, 28), fill="#20263e")
    if name == "dormant_crystal":
        d.polygon(((16, 6), (23, 15), (16, 25), (9, 15)), fill="#8252c9", outline="#debaff")
        d.line(((16, 6), (14, 15), (16, 25)), fill="#f4e2ff", width=2)
        d.line(((9, 15), (14, 15), (23, 15)), fill="#b18aef")
    elif name == "crystal_detonation":
        d.polygon(((16, 11), (20, 16), (16, 21), (12, 16)), fill="#ffe4a0")
        for a, b in (((16, 5), (16, 8)), ((16, 24), (16, 27)), ((5, 16), (8, 16)),
                     ((24, 16), (27, 16)), ((7, 7), (10, 10)), ((22, 22), (25, 25)),
                     ((7, 25), (10, 22)), ((22, 10), (25, 7))):
            d.line((a, b), fill="#ffad62", width=2)
    elif name == "chain_bolt":
        d.line(((7, 10), (13, 17), (18, 9), (25, 21)), fill="#91dfff", width=2)
        for x, y in ((7, 10), (18, 9), (25, 21)):
            d.rectangle((x-2, y-2, x+2, y+2), fill="#edfaff")
    elif name == "long_distance_cast":
        d.line(((6, 16), (24, 16)), fill="#d3a3ff", width=2)
        d.line(((18, 10), (24, 16), (18, 22)), fill="#f1dfff", width=2)
        d.line(((8, 9), (8, 23)), fill="#815ab2", width=2)
        d.line(((26, 9), (26, 23)), fill="#b991eb", width=2)
    elif name == "plasma_beam":
        d.polygon(((6, 12), (11, 16), (6, 20)), fill="#abffdf")
        d.line(((10, 16), (26, 16)), fill="#45cfaf", width=5)
        d.line(((10, 16), (26, 16)), fill="#f0fffd", width=1)
        d.line(((13, 11), (13, 21)), fill="#73edce")
    elif name == "repulsion_field":
        d.ellipse((11, 11, 21, 21), outline="#ffda93", width=2)
        for a, b, c in (((16, 11), (16, 5), (13, 8)), ((16, 21), (16, 27), (19, 24)),
                        ((11, 16), (5, 16), (8, 19)), ((21, 16), (27, 16), (24, 13))):
            d.line((a, b, c), fill="#ffad62", width=2)
    else:
        d.line(((16, 6), (16, 26)), fill="#cef4ff", width=2)
        d.line(((7, 11), (25, 21)), fill="#cef4ff", width=2)
        d.line(((7, 21), (25, 11)), fill="#cef4ff", width=2)
        d.line(((12, 8), (16, 12), (20, 8)), fill="#72c9f5")
        d.line(((12, 24), (16, 20), (20, 24)), fill="#72c9f5")
    return im.resize((64, 64), Image.Resampling.NEAREST)


if __name__ == "__main__":
    for name in IDS:
        icon(name).save(OUT / f"{name}.png")
