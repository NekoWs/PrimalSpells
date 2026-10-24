"""已选画风的16×16逐像素原稿；只表达法术行为，不附加通用徽章。"""
from PIL import Image


# 点号为透明；每个字符严格对应一个逻辑像素，不缩小概念插画。
SPRITES = {
    'fireball': ({
        'D': '#ac2834', 'R': '#ef4829', 'O': '#ff9029',
        'Y': '#ffe164', 'W': '#fffde9',
    }, (
        'RR..............',
        '.ROR............',
        '..ROO...........',
        '.R.ROYO.........',
        '.RO.ROYO........',
        '..ROOOYOO.R.....',
        '...ROYOYOOOO....',
        '...ROYYWYYOO....',
        '....OYYWWWYYO...',
        '...ROYWWWWWYYO..',
        '....OYYWWWWYO...',
        '....ROYYWWWYO...',
        '.....ROYYYYOR...',
        '......ROOOOR....',
        '.......RRRD.....',
        '................',
    )),
    'concentrated_light': ({
        'A': '#f6c142', 'G': '#ffcc58', 'Y': '#ffe180', 'W': '#fffde9',
    }, (
        '................',
        '................',
        '................',
        '................',
        '.AGG............',
        '..GYYG..........',
        '.GYYWYYG........',
        '..YWWWWWWYYG....',
        '.GWWWWWWWWWWWWWW',
        '..YWWWWWWYYG....',
        '.GYYWYYG........',
        '..GYYG..........',
        '.AGG............',
        '................',
        '................',
        '................',
    )),
    'black_hole': ({
        'D': '#23103d', 'P': '#5720a4', 'V': '#8c40e7',
        'L': '#c58bff', 'B': '#6664ed', 'K': '#090511',
    }, (
        '......PPBB......',
        '....PP.VLLB.....',
        '..PPVV..VLLB....',
        '.PVLLV...VLLB...',
        '.VLLVPP...VLV...',
        'VLVPPKKKK..VV.P.',
        'VVP.KKKKKK.VP.V.',
        'VP.KKKKKKKKPP.V.',
        'P.VKKKKKKKKP.VL.',
        '..VLKKKKKKP.VLV.',
        '..VLV.KKKPVLLV..',
        '..BVLVPPVLLVP...',
        '...BVLLLLVVP....',
        '....BVVVPP......',
        '.....BPP........',
        '................',
    )),
    'sawblade': ({
        'D': '#444353', 'S': '#9293a9', 'H': '#bfc3d3', 'W': '#eef1f7',
    }, (
        '......WW........',
        '...WW..WH.......',
        '....WH.HSH..WW..',
        '..WWHHSSSSH.WH..',
        '.WHSSSSSSSSHHH..',
        '..HSSSSSSSSSSHW.',
        '.WHSSSSDDSSSSHW.',
        'WHSSSSDDDDSSSH..',
        '.HSSSSDDDDSSSHW.',
        '.WHSSSSDDSSSSSHW',
        '.WHSSSSSSSSSSH..',
        '..HHHSSSSSSSHW..',
        '..HW.HSSSSHHWW..',
        '..WW..HSH.HW....',
        '.......HW..WW...',
        '........WW......',
    )),
    'piercing': ({
        'D': '#385831', 'S': '#4f853b', 'G': '#83bf4b', 'H': '#b9e975',
        'B': '#2896b2', 'C': '#76deee', 'W': '#eefcfa',
    }, (
        '................',
        '................',
        '................',
        '....SSSSSSSS....',
        '...SHHHHHHGGG...',
        '...SHGGGGGGGG...',
        '...SGDDGGDDGG...',
        '...SGDDGGDDGG...',
        '...SGGGDDGGGG.C.',
        'BCWWWCCCGCCCWWWC',
        '...SGGGGGGGGG.C.',
        '...SSGGGGGGSS...',
        '....SSSSSSSS....',
        '................',
        '................',
        '................',
    )),
    'double_cast': ({
        'D': '#43344e', 'S': '#d5ad70', 'P': '#f6dca0', 'W': '#fff3d1',
    }, (
        '................',
        '..DDDDDD........',
        '.DWWWWWWD.......',
        '.DWWPPPPD.......',
        '.DWPPPPPDDDDD...',
        '.DWPPPPDWWWWWD..',
        '.DWPPPPDWPPPPD..',
        '.DWPPPPDWPPPPD..',
        '.DWPPPPDWPPPPD..',
        '.DWPPPPDWPPPPD..',
        '.DPPSSSDWPPPPD..',
        '..DDDDDDWPPPPD..',
        '.......DPSSSSD..',
        '........DDDDD...',
        '................',
        '................',
    )),
}


def pixel_icon(name):
    """从显式像素稿生成原生16px图像；尚未重画的卡交给原生成器。"""
    if name not in SPRITES:
        return None
    palette, rows = SPRITES[name]
    if len(rows) != 16 or any(len(row) != 16 for row in rows):
        raise ValueError(f'{name}: 每张原稿必须为16行×16列')
    image = Image.new('RGBA', (16, 16))
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != '.':
                image.putpixel((x, y), tuple(bytes.fromhex(palette[char][1:])) + (255,))
    return image
