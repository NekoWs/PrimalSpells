"""从原生像素稿生成完整注册图标；缺稿、多稿或类型不明时停止。"""
from pathlib import Path
import json
import re
from functools import cache
from PIL import Image, ImageDraw, ImageFont
from pixel_icons import SPRITES, pixel_icon

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/primalspells/textures/item'
MAGIC = ROOT / 'src/main/kotlin/work/nekow/primalspells/magic'
SAMPLES = ('fireball', 'concentrated_light', 'black_hole', 'sawblade', 'piercing', 'double_cast')


@cache
def registered_categories():
    """按已注册类的继承链识别类型，避免新卡遗漏在手写分类表之外。"""
    classes = re.findall(r'register \{ (\w+)\(\) }', (ROOT/'src/main/kotlin/work/nekow/primalspells/registry/ModSpells.kt').read_text(encoding='utf-8'))
    sources = {p.stem: p.read_text(encoding='utf-8') for p in MAGIC.rglob('*.kt')}
    roots = {'Projectile': 'projectile', 'Modifier': 'modifier', 'DrawAction': 'other'}
    result={}
    for cls in classes:
        name = re.search(r'override val id = "([^"]+)"', sources[cls])[1]
        ancestor = cls
        visited = set()
        while ancestor not in roots:
            if ancestor in visited:
                raise ValueError(f'循环继承：{cls}')
            visited.add(ancestor)
            parent = re.search(r'\bclass\s+' + re.escape(ancestor) + r'\b[^{]*:\s*(\w+)', sources[ancestor])
            if parent is None:
                raise ValueError(f'无法识别类型：{cls} → {ancestor}')
            ancestor = parent[1]
        result[name] = roots[ancestor]
    return result


def main():
    categories = registered_categories()
    missing, extra = categories.keys() - SPRITES.keys(), SPRITES.keys() - categories.keys()
    if missing or extra:
        raise ValueError(f'原稿必须覆盖注册目录：缺少{sorted(missing)}，多余{sorted(extra)}')
    ids = sorted(categories, key=lambda name: ('projectile', 'modifier', 'other').index(categories[name]))
    images = {name: pixel_icon(name, categories[name]) for name in ids}
    for name, image in images.items():
        path = OUT / (name + '.png')
        # 编码器变化不应导致像素未改的资源出现二进制噪声。
        if path.exists():
            with Image.open(path) as previous:
                if previous.size == image.size and previous.convert('RGBA').tobytes() == image.tobytes():
                    continue
        image.save(path)
    labels = json.loads((OUT.parents[1] / 'lang/zh_cn.json').read_text(encoding='utf-8'))
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 13)
    small = ImageFont.load_default(size=10)
    sheet = Image.new('RGB', (8 * 144, ((len(ids) + 7) // 8) * 126), '#192330')
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(ids):
        x, y = (i % 8) * 144, (i // 8) * 126
        native = images[name]
        enlarged = native.resize((64, 64), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x + 32, y + 8), enlarged)
        draw.rectangle((x + 111, y + 49, x + 130, y + 68), fill='#6f6f76', outline='#a9a9b0')
        sheet.paste(native, (x + 113, y + 51), native)
        draw.text((x + 8, y + 76), labels['item.primalspells.' + name], fill='#f0e7d1', font=font)
        # 最长的ID在词边界换行，避免跨栏盖住相邻图标。
        words = name.split('_')
        lines = ['']
        for word in words:
            if len(lines[-1]) + len(word) + 1 > 22:
                lines.append(word)
            else:
                lines[-1] += ('_' if lines[-1] else '') + word
        draw.multiline_text((x + 8, y + 97), '\n'.join(lines), fill='#8499b3', font=small, spacing=1)
    target = ROOT / 'build/verify/spell-icons-mc.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)
    sample = Image.new('RGB', (len(SAMPLES) * 144, 210), '#25212d')
    preview = ImageDraw.Draw(sample)
    for i, name in enumerate(SAMPLES):
        x = i * 144
        native = images[name]
        enlarged = native.resize((112, 112), Image.Resampling.NEAREST)
        sample.paste(enlarged, (x + 16, 16), enlarged)
        preview.rectangle((x + 62, 140, x + 81, 159), fill='#6f6f76', outline='#a9a9b0')
        sample.paste(native, (x + 64, 142), native)
        preview.text((x + 8, 174), labels['item.primalspells.' + name], fill='#f0e7d1', font=font)
    sample.save(target.with_name('spell-icons-selected.png'))
    print(f'Rebuilt {len(ids)} native 16px icons; preview: {target}')


if __name__ == '__main__':
    main()
