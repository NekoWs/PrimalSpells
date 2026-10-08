"""重建全部注册法术的硬边像素卡图标；有限实色色板，无抗锯齿或半透明像素。"""
from pathlib import Path
import json, re
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/primalspells/textures/item'
MAGIC = ROOT / 'src/main/kotlin/work/nekow/primalspells/magic'
PROJECTILES = ('fireball','drone_bolt','trigger_fireball','delayed_cast','drone_swarm','laser',
               'small_explosion','large_explosion','gravity_field','black_hole','teleport',
               'dormant_crystal','crystal_detonation','chain_bolt','plasma_beam','repulsion_field')
MODIFIERS = ('damage_boost','gravity','bounce','double_cast','speed_boost','extended_lifetime',
             'piercing','bounce_explosion','homing','bounce_duplicate','gradual_accelerate',
             'gradual_decelerate','random_path','reciprocate','sine_wave','mana_injection',
             'diffusion','implosion','volatile','cluster_bomb','explosion_remove','explosion_projectile',
             'self_orbit','sphere_orbit','quantum_entanglement','energy_shield','long_distance_cast','freeze_charge')
IDS = PROJECTILES + MODIFIERS
WHITE, BLUE, DARK_BLUE = '#eef5e9', '#76d9e8', '#267eaa'
ORANGE, YELLOW, RED, PURPLE = '#ee7b37', '#f4d375', '#c94a48', '#ac82db'


def icon(name):
    im = Image.new('RGBA', (32,32))
    d = ImageDraw.Draw(im)
    rim = '#d6a650' if name in PROJECTILES else '#9972c8'
    dark = '#78552f' if name in PROJECTILES else '#533f74'
    outline = ((9,1),(22,1),(27,4),(30,9),(30,22),(27,27),(22,30),(9,30),(4,27),(1,22),(1,9),(4,4))
    d.polygon(outline, fill='#101721')
    d.polygon(((10,2),(21,2),(26,5),(29,10),(29,21),(26,26),(21,29),(10,29),(5,26),(2,21),(2,10),(5,5)), fill=dark)
    d.line(((3,18),(3,10),(6,6),(10,3),(21,3),(26,6)), fill=rim, width=2)
    d.polygon(((10,5),(21,5),(26,10),(26,21),(21,26),(10,26),(5,21),(5,10)), fill='#202b38')
    d.line(((10,26),(21,26),(26,21)),fill='#151e2b',width=2)
    def line(points,c=WHITE,w=2): d.line(points,fill=c,width=w)
    def box(x,y,c=WHITE,r=2): d.rectangle((x-r,y-r,x+r,y+r),fill=c)
    def arrow(a,b,c=WHITE,w=2):
        line((a,b),c,w)
        dx,dy=b[0]-a[0],b[1]-a[1]
        if abs(dx)>=abs(dy): line(((b[0]-(3 if dx>0 else -3),b[1]-3),b,(b[0]-(3 if dx>0 else -3),b[1]+3)),c,w)
        else: line(((b[0]-3,b[1]-(3 if dy>0 else -3)),b,(b[0]+3,b[1]-(3 if dy>0 else -3))),c,w)
    def plus(x,y,c=YELLOW): line(((x-3,y),(x+3,y)),c);line(((x,y-3),(x,y+3)),c)
    def flame(x=16,y=16):
        d.polygon(((x-7,y+6),(x-7,y),(x-3,y-4),(x-3,y-8),(x+1,y-5),(x+4,y-8),(x+7,y),(x+5,y+7),(x-3,y+8)),fill=ORANGE)
        d.polygon(((x-3,y+5),(x-3,y),(x,y-4),(x+4,y+1),(x+3,y+6)),fill=YELLOW)
        box(x,y+4,WHITE,1)
    def burst(x=16,y=16,r=8,c=YELLOW):
        d.polygon(((x-r,y),(x-4,y-3),(x-4,y-6),(x-1,y-4),(x+2,y-r),(x+3,y-3),(x+r,y-4),(x+5,y+1),(x+r,y+4),(x+3,y+4),(x+1,y+r),(x-2,y+5),(x-6,y+7),(x-5,y+2)),fill=c)
        box(x,y,WHITE,2)
    def crystal(x=16,y=16,r=8):
        d.polygon(((x,y-r),(x+6,y),(x,y+r),(x-6,y)),fill=PURPLE)
        d.polygon(((x,y-r),(x,y+r),(x-6,y)),fill='#6b4d9c')
        line(((x,y-r),(x-2,y),(x,y+r)),WHITE,1)
    def hourglass():
        line(((9,8),(23,8)),YELLOW);line(((9,24),(23,24)),YELLOW)
        line(((11,9),(11,12),(20,21),(20,23)),WHITE)
        line(((21,9),(21,12),(12,21),(12,23)),WHITE)
        line(((13,11),(19,11)),ORANGE);line(((14,21),(18,21)),ORANGE)
    def orbit(two=False):
        d.ellipse((6,10,26,22),outline=PURPLE,width=2)
        if two: d.ellipse((11,5,21,27),outline=DARK_BLUE,width=2)
        box(16,16,WHITE,2);box(25,14,BLUE,2)
    def bounce():
        line(((6,21),(12,10),(18,21)),BLUE)
        arrow((18,21),(24,10),BLUE)
        line(((7,24),(25,24)),WHITE)
    if name in ('fireball','trigger_fireball'):
        flame()
        if name=='trigger_fireball': plus(24,8,WHITE)
    elif name=='drone_bolt':
        d.rectangle((8,10,18,22),fill='#707f8c',outline=WHITE,width=2);box(13,16,BLUE,2);arrow((20,16),(25,16),BLUE)
    elif name=='drone_swarm':
        for x,y in ((9,12),(23,12),(16,22)):
            d.rectangle((x-4,y-4,x+3,y+3),fill='#6c7b86',outline=WHITE);box(x,y,BLUE,1)
    elif name in ('delayed_cast','extended_lifetime'):
        hourglass()
        if name=='extended_lifetime': plus(24,8,BLUE)
    elif name in ('laser','plasma_beam'):
        line(((7,16),(25,16)),DARK_BLUE,5 if name=='plasma_beam' else 3)
        line(((7,16),(25,16)),BLUE,3 if name=='plasma_beam' else 1)
        line(((9,16),(25,16)),WHITE,1);box(6,16,BLUE,2)
        if name=='plasma_beam':line(((12,10),(12,22)),BLUE)
    elif name in ('small_explosion','large_explosion','explosion_projectile'):
        burst(r=6 if name=='small_explosion' else 10)
        if name=='explosion_projectile':arrow((6,24),(13,20),ORANGE)
    elif name=='black_hole':
        d.ellipse((5,11,27,21),outline=PURPLE,width=2)
        d.rectangle((12,10,20,22),fill='#070b14');d.rectangle((10,12,22,20),fill='#070b14')
        line(((9,14),(11,11),(14,9)),WHITE,1);box(25,15,WHITE,1)
    elif name=='gravity_field':
        d.ellipse((6,7,26,25),outline=DARK_BLUE,width=1)
        for x in (10,16,22):arrow((x,9),(x,20),BLUE)
        line(((7,25),(25,25)),WHITE)
    elif name=='teleport':
        d.rectangle((6,8,11,24),outline=PURPLE,width=2);d.rectangle((21,8,26,24),outline=BLUE,width=2)
        arrow((12,16),(20,16),WHITE)
    elif name=='dormant_crystal':crystal()
    elif name=='crystal_detonation':burst(c=ORANGE);crystal(r=4)
    elif name=='chain_bolt':
        line(((7,9),(13,18),(18,10),(25,22)),BLUE)
        for x,y in ((7,9),(18,10),(25,22)):box(x,y,WHITE,2)
    elif name in ('repulsion_field','implosion'):
        box(16,16,ORANGE,2)
        for outer,inner in (((16,5),(16,11)),((16,27),(16,21)),((5,16),(11,16)),((27,16),(21,16))):
            arrow(inner,outer,ORANGE) if name=='repulsion_field' else arrow(outer,inner,PURPLE)
    elif name=='damage_boost':
        line(((9,24),(23,10)),RED,4);line(((10,18),(16,24)),YELLOW);plus(9,8,WHITE)
    elif name=='gravity':
        arrow((16,6),(16,17),BLUE);d.rectangle((9,21,23,25),fill='#9aa8b1');d.rectangle((12,18,20,21),fill=WHITE)
    elif name in ('bounce','bounce_explosion','bounce_duplicate'):
        bounce()
        if name=='bounce_explosion':burst(9,8,4,ORANGE)
        if name=='bounce_duplicate':box(7,9,YELLOW,2);box(25,22,YELLOW,2)
    elif name=='double_cast':
        arrow((7,11),(25,11),BLUE);arrow((7,21),(25,21),YELLOW)
    elif name in ('speed_boost','gradual_accelerate','gradual_decelerate'):
        sizes=(5,5,5) if name=='speed_boost' else ((2,4,6) if name=='gradual_accelerate' else (6,4,2))
        for x,r in zip((8,15,22),sizes):line(((x-3,16-r),(x+2,16),(x-3,16+r)),BLUE)
    elif name=='piercing':
        for x in (11,20):line(((x,7),(x,25)),PURPLE,3)
        arrow((6,16),(27,16),WHITE)
    elif name=='homing':
        d.ellipse((18,7,26,15),outline=RED);plus(22,11,RED)
        line(((6,24),(11,24),(16,20),(18,15)),BLUE);arrow((18,15),(22,11),WHITE,1)
    elif name=='random_path':
        line(((6,23),(10,12),(15,21),(20,8)),PURPLE);arrow((20,8),(26,11),BLUE)
    elif name=='reciprocate':
        arrow((7,11),(25,11),BLUE);arrow((25,21),(7,21),YELLOW)
    elif name=='sine_wave':
        line(((5,16),(8,10),(12,10),(16,22),(20,22),(24,10),(27,16)),BLUE)
    elif name=='mana_injection':
        d.polygon(((15,6),(8,16),(8,21),(12,25),(18,25),(22,21),(22,16)),fill=DARK_BLUE)
        line(((12,15),(10,19),(12,22)),BLUE);plus(23,9,WHITE)
    elif name=='diffusion':
        for b in ((25,7),(26,16),(25,25)):arrow((7,16),b,BLUE)
    elif name=='volatile':
        d.ellipse((8,12,24,27),fill='#52626e',outline=WHITE,width=1)
        line(((16,12),(16,8),(21,8)),ORANGE);plus(23,7,YELLOW)
    elif name=='cluster_bomb':
        for x,y in ((10,10),(23,12),(16,23)):
            d.ellipse((x-4,y-4,x+4,y+4),fill='#67727d',outline=ORANGE);box(x-1,y-1,YELLOW,1)
    elif name=='explosion_remove':
        burst(c=ORANGE);line(((7,25),(25,7)),RED,3);line(((6,24),(24,6)),WHITE,1)
    elif name in ('self_orbit','sphere_orbit'):orbit(name=='sphere_orbit')
    elif name=='quantum_entanglement':
        line(((10,12),(22,20)),BLUE)
        for x,y in ((10,12),(22,20)):
            d.polygon(((x,y-5),(x+5,y),(x,y+5),(x-5,y)),outline=PURPLE);box(x,y,WHITE,1)
    elif name=='energy_shield':
        d.polygon(((7,8),(16,5),(25,8),(25,17),(21,23),(16,27),(11,23),(7,17)),fill=DARK_BLUE,outline=BLUE)
        line(((16,8),(16,23)),WHITE);line(((11,13),(21,13)),WHITE)
    elif name=='long_distance_cast':
        line(((7,9),(7,24)),PURPLE);line(((26,9),(26,24)),DARK_BLUE);arrow((11,16),(24,16),WHITE)
    elif name=='freeze_charge':
        for a,b in (((16,6),(16,26)),((7,11),(25,21)),((7,21),(25,11))):line((a,b),BLUE)
        line(((12,8),(16,12),(20,8)),WHITE,1);line(((12,24),(16,20),(20,24)),WHITE,1);box(16,16,WHITE,1)
    else:raise ValueError('No glyph: '+name)
    return im.resize((64,64),Image.Resampling.NEAREST)


def registered_ids():
    classes = re.findall(r'register \{ (\w+)\(\) }', (MAGIC/'core/MagicManager.kt').read_text(encoding='utf-8'))
    result=[]
    for cls in classes:
        path=next(p for p in MAGIC.rglob(cls+'.kt'))
        result.append(re.search(r'override val id = "([^"]+)"',path.read_text(encoding='utf-8'))[1])
    return result


def main():
    assert set(registered_ids())==set(IDS), 'Glyph table must cover the registry exactly'
    for name in IDS: icon(name).save(OUT/(name+'.png'))
    # 验收图不进入游戏资源；中文名称与原生16px栏位同时展示。
    labels=json.loads((OUT.parents[1]/'lang/zh_cn.json').read_text(encoding='utf-8'))
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',13)
    sheet=Image.new('RGB',(8*136,6*120),'#192330'); d=ImageDraw.Draw(sheet)
    for i,name in enumerate(IDS):
        x,y=(i%8)*136,(i//8)*120
        im=icon(name);sheet.paste(im,(x+36,y+8),im)
        tiny=im.resize((16,16),Image.Resampling.NEAREST);sheet.paste(tiny,(x+110,y+52),tiny)
        d.text((x+8,y+76),labels['item.primalspells.'+name],fill='#f0e7d1',font=font)
        d.text((x+8,y+98),name,fill='#8499b3',font=ImageFont.load_default(size=10))
    target=ROOT/'build/verify/spell-icons-mc.png';target.parent.mkdir(parents=True,exist_ok=True);sheet.save(target)
    print(f'Rebuilt {len(IDS)} icons; preview: {target}')

if __name__=='__main__':main()
