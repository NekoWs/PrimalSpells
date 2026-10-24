"""低分辨率法术原稿：12×12主体配16×16类型框，每字符一个像素。"""
from PIL import Image, ImageDraw


# 类型框不表示额外法术行为；与主体分开配色，允许后续独立重画。
FRAME_COLORS = {
    'projectile': ('#ef7950', '#8d3735'),
    'modifier': ('#65bde8', '#305d91'),
    'other': ('#9972c8', '#533f74'),
}

SPRITES = {
    'fireball': ({
        'D': '#ac2834', 'R': '#ef4829', 'O': '#ff9029',
        'Y': '#ffe164', 'W': '#fffde9',
    }, (
        '...RR.......',
        '....RO......',
        '..R.ROO.....',
        '...ROYO.....',
        '....OYYOO...',
        '...ROYYWYO..',
        '....OYWWWYO.',
        '...ROWWWWYO.',
        '....OYWWYYO.',
        '....ROYYYOR.',
        '.....ROOOR..',
        '......RRD...',
    )),
    'concentrated_light': ({
        'G': '#ffcc58', 'Y': '#ffe180', 'W': '#fffde9',
    }, (
        '............',
        '............',
        '.........YW.',
        '........YWY.',
        '.......YWY..',
        '......YWY...',
        '.....YWW....',
        '....YWWG....',
        '...YWWG.....',
        '..YWWG......',
        '..GWG.......',
        '............',
    )),
    'black_hole': ({
        'P': '#442074', 'V': '#8241cf', 'L': '#c898fa',
        'B': '#6974e8', 'K': '#080811',
    }, (
        '....PVB.....',
        '...PVLLB....',
        '..VLLPPVB...',
        '.VLPKKKKVP..',
        '.VLKKKKKKVP.',
        'PVPKKKKKKVP.',
        'PVKKKKKKKLV.',
        '.VPKKKKKPLV.',
        '..VPKKKPLLV.',
        '...VVPLLLV..',
        '....BVLLV...',
        '.....BVP....',
    )),
    'sawblade': ({
        'D': '#444353', 'S': '#9293a9', 'H': '#bfc3d3', 'W': '#eef1f7',
    }, (
        '....WW......',
        '..WW.HH..W..',
        '...HHSSH.HW.',
        '.WHSSSSSHHW.',
        '.HSSSSSSSSW.',
        'WHSSSDDSSH..',
        '..HSSDDSSHW.',
        '.WSSSSSSSSH.',
        '.WHHSSSSHW..',
        '.WH.HSSHH...',
        '..W..HH.WW..',
        '......WW....',
    )),
    'piercing': ({
        'D': '#385831', 'S': '#4f853b', 'G': '#83bf4b', 'H': '#b9e975',
        'B': '#2896b2', 'C': '#76deee', 'W': '#eefcfa',
    }, (
        '............',
        '............',
        '...SSSSSS...',
        '..SHHHHGGG..',
        '..SGDGGDGG..',
        '..SGDGGDGG..',
        '..SGGDDGGGC.',
        'BCWWCGCCWWWC',
        '..SGGGGGGGC.',
        '..SSGGGGSS..',
        '...SSSSSS...',
        '............',
    )),
    'double_cast': ({
        'D': '#514555', 'S': '#c8a66b', 'P': '#f4d9a2', 'W': '#fff4d6',
    }, (
        '....DD......',
        '..DDWWD.....',
        '.DWWPPD.DD..',
        '.DWPPPDWWWD.',
        '..DWPPDWPPSD',
        '..DWPDWWPPSD',
        '..DWPDWPPSD.',
        '...DPDWPPSD.',
        '...DSDWPPSD.',
        '....DDWPPSD.',
        '.....DSSSD..',
        '......DDD...',
    )),
}


def pixel_icon(name, category):
    """将逐像素主体置于原尺寸类型框内；未重画的卡交给旧主体生成器。"""
    if name not in SPRITES:
        return None
    palette, rows = SPRITES[name]
    if len(rows) != 12 or any(len(row) != 12 for row in rows):
        raise ValueError(f'{name}: 主体原稿必须为12行×12列')
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    rim, shadow = FRAME_COLORS[category]
    draw.polygon(((4, 0), (11, 0), (15, 4), (15, 11), (11, 15), (4, 15), (0, 11), (0, 4)), fill='#11151e')
    draw.polygon(((4, 1), (11, 1), (14, 4), (14, 11), (11, 14), (4, 14), (1, 11), (1, 4)), fill=shadow)
    draw.line(((1, 9), (1, 4), (4, 1), (11, 1), (13, 3)), fill=rim)
    draw.polygon(((5, 2), (10, 2), (13, 5), (13, 10), (10, 13), (5, 13), (2, 10), (2, 5)), fill='#202431')
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != '.':
                image.putpixel((x + 2, y + 2), tuple(bytes.fromhex(palette[char][1:])) + (255,))
    return image
