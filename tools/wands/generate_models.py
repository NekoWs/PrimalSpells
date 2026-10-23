#!/usr/bin/env python3
"""暮紫封印：黑曜杖身、骨白封印片和不对称紫晶；生成原生OBJ、像素图集与物品组合。"""
import json
import math
import random
import re
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/primalspells'
OUT = ASSETS / 'models/item'
GRIP_SOURCE = ROOT / 'src/main/kotlin/work/nekow/primalspells/wand/WandGrip.kt'
TEXTURE = 'primalspells:item/wand_dusk'
PALETTE = {
    'obsidian': (39, 34, 48), 'edge': (61, 51, 75),
    'ivory': (221, 215, 203), 'ivory_edge': (165, 156, 155),
    'gold': (168, 136, 73), 'grip': (71, 43, 83), 'wrap': (91, 57, 103),
    'gem': (142, 63, 222), 'gem_light': (215, 147, 255), 'gem_dark': (75, 28, 125),
    'rune': (194, 124, 249),
}
TILES = {name: i for i, name in enumerate(PALETTE)}


def grip_constant(name):
    """严格读取运行时标尺，不另存一组会逐渐漂移的发射/模型常量。"""
    match = re.search(r'const val ' + name + r' = (-?[\d.]+)', GRIP_SOURCE.read_text(encoding='utf-8'))
    if not match:
        raise ValueError('Missing wand grip constant: ' + name)
    return float(match[1])


class Mesh:
    def __init__(self):
        self.lines = ['# Dusk seal wand; sixteen model units per block', 'mtllib wand.mtl']
        self.vertices = 0
        self.faces = 0

    def group(self, name):
        self.lines.append('g ' + name)

    def face(self, points, material):
        # 极点/封盖用真实三角形，不把重复顶点写成退化四边形。
        points = [p for i, p in enumerate(points) if i == 0 or p != points[i - 1]]
        if len(points) > 2 and points[0] == points[-1]:
            points.pop()
        if len(points) < 3:
            return
        u = [points[1][i] - points[0][i] for i in range(3)]
        v = [points[2][i] - points[0][i] for i in range(3)]
        normal = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        length = math.sqrt(sum(c*c for c in normal))
        if length < 1e-8:
            return
        normal = [c/length for c in normal]
        tile = TILES.get(material.split('_glow')[0], TILES['gem'])
        # 发光材质共享原宝石贴图，只改变材质色阶。
        if material.startswith('gem_glow'):
            tile = TILES['gem' + ('_light' if material.endswith('_light') else '_dark' if material.endswith('_dark') else '')]
        x, y = tile % 4, tile // 4
        uv = [(0, 1), (1, 1), (1, 0), (0, 0)]
        self.lines.append('usemtl ' + material)
        for point, (a, b) in zip(points, uv):
            self.lines += [
                'v ' + ' '.join(f'{c/16:.7f}' for c in point),
                f'vt {(x*16+0.5+a*15)/64:.7f} {1-(y*16+0.5+b*15)/64:.7f}',
                'vn ' + ' '.join(f'{c:.7f}' for c in normal),
            ]
        self.lines.append('f ' + ' '.join(f'{self.vertices+j+1}/{self.vertices+j+1}/{self.vertices+j+1}' for j in range(len(points))))
        self.vertices += len(points)
        self.faces += 1

    def lathe(self, name, profile, material, sides=8, cx=8, cz=8, rotation=0):
        self.group(name)
        for (y0, r0), (y1, r1) in zip(profile, profile[1:]):
            for j in range(sides):
                a, b = (j+.5)*math.tau/sides+rotation, (j+1.5)*math.tau/sides+rotation
                mat = [material, material+'_light', material, material+'_dark'][j % 4] if material == 'gem' or material.startswith('gem_glow') else material
                self.face([(cx+r*math.cos(t), y, cz+r*math.sin(t))
                           for y, r, t in [(y0,r0,a),(y1,r1,a),(y1,r1,b),(y0,r0,b)]], mat)

    def prism(self, name, polygon, front, back, material, edge=None, tilt=0, pivot=(8, 0), yaw=0):
        """有厚度的平面轮廓；凹多边形耳切，保持托架内侧与外侧面法线一致。"""
        self.group(name)
        polygon = list(polygon)
        area = sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(polygon, polygon[1:]+polygon[:1]))
        if area < 0:
            polygon.reverse()

        def cross(a,b,c):
            return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])

        def transform(point, z):
            x, y = point[0]-pivot[0], point[1]-pivot[1]
            x, y = x*math.cos(tilt)-y*math.sin(tilt), x*math.sin(tilt)+y*math.cos(tilt)
            dz = z-8
            return (pivot[0]+x*math.cos(yaw)+dz*math.sin(yaw), y+pivot[1], 8-x*math.sin(yaw)+dz*math.cos(yaw))

        indices = list(range(len(polygon)))
        while len(indices) > 3:
            for i, b in enumerate(indices):
                a, c = indices[i-1], indices[(i+1) % len(indices)]
                if cross(polygon[a],polygon[b],polygon[c]) <= 1e-9:
                    continue
                if any(all(cross(polygon[u],polygon[v],polygon[p]) >= -1e-9 for u,v in [(a,b),(b,c),(c,a)])
                       for p in indices if p not in (a,b,c)):
                    continue
                tri = [polygon[a],polygon[b],polygon[c]]
                self.face([transform(p,front) for p in reversed(tri)],material)
                self.face([transform(p,back) for p in tri],material)
                indices.pop(i)
                break
            else:
                raise ValueError('Invalid polygon: ' + name)
        tri = [polygon[i] for i in indices]
        self.face([transform(p,front) for p in reversed(tri)],material)
        self.face([transform(p,back) for p in tri],material)
        for a,b in zip(polygon,polygon[1:]+polygon[:1]):
            self.face([transform(a,front),transform(b,front),transform(b,back),transform(a,back)],edge or material)

    def plate(self, name, cx, cy, width, height, depth, material, **kwargs):
        b = min(width, height) * .18
        x0, x1, y0, y1 = cx-width/2, cx+width/2, cy-height/2, cy+height/2
        self.prism(name, [(x0+b,y0),(x1-b,y0),(x1,y0+b),(x1,y1-b),(x1-b,y1),(x0+b,y1),(x0,y1-b),(x0,y0+b)],
                   8-depth/2, 8+depth/2, material, **kwargs)

    def write(self, path):
        path.write_text('\n'.join(self.lines)+'\n', encoding='utf-8')


def create_texture():
    # 16px一块材质，粗颗粒、固定色阶；不使用照片、噪点渐变或半透明。
    image = Image.new('RGBA', (64,64), (255,255,255,255))
    draw = ImageDraw.Draw(image)
    rng = random.Random(9471)
    for name, index in TILES.items():
        ox, oy = index % 4 * 16, index // 4 * 16
        if name.startswith('gem'):
            for y in range(0,16,4):
                for x in range(0,16,4):
                    gray = [207,231,255][((x//4)+(y//4)*2) % 3]
                    draw.rectangle((ox+x,oy+y,ox+x+3,oy+y+3),fill=(gray,gray,gray,255))
        elif name == 'rune':
            # 小方印整体浮刻，几何负责轮廓，纹理保持纯色清晰。
            draw.rectangle((ox,oy,ox+15,oy+15),fill=(255,255,255,255))
        else:
            base = 245 if name in ('ivory','ivory_edge','gold') else 235
            draw.rectangle((ox,oy,ox+15,oy+15),fill=(base,base,base,255))
            for _ in range(12):
                x,y=rng.randrange(0,14,2),rng.randrange(0,14,2)
                gray = rng.choice([211,225,255])
                draw.rectangle((ox+x,oy+y,ox+x+1,oy+y+1),fill=(gray,gray,gray,255))
    target=ASSETS/'textures/item/wand_dusk.png'
    target.parent.mkdir(parents=True,exist_ok=True)
    image.save(target)


def static_body():
    mesh=Mesh()
    mesh.lathe('obsidian_spine',[(-3,0),(-2.5,.6),(7,.6),(8,.7),(18.8,.6),(19.2,0)],'obsidian')
    mesh.lathe('plum_grip',[(0,0),(0,.8),(.4,.88),(5.9,.88),(6.4,.8),(6.4,0)],'grip')
    for y in (0,6.4,8.8,18.6):
        mesh.lathe('muted_gold_binding',[(y,0),(y,.95),(y+.28,.95),(y+.45,.73),(y+.45,0)],'gold')
    for y in (1.2,2.5,3.8,5.1):
        mesh.lathe('wide_grip_wrap',[(y,.88),(y+.28,.96),(y+.48,.88)],'wrap')
    mesh.lathe('pommel_socket',[(-2.7,0),(-2.7,.84),(-1.8,.84),(-1.8,0)],'gold')
    # 下端反向短封印：小紫晶嵌入黑曜斜切尾锥，呼应杖首。
    mesh.prism('obsidian_pommel',[(7.1,-2.8),(8,-4.8),(8.9,-2.8),(8.65,-1.75),(7.35,-1.75)],7.45,8.55,'obsidian','edge')
    mesh.lathe('pommel_inset',[(-4.05,0),(-3.3,.38),(-2.75,0)],'gem',4)
    # 开放式不对称托架：骨白折臂只占左边，黑曜脊柱在另一边，保留晶体周围负空间。
    mesh.prism('ivory_seal_crown',[(7.4,18.9),(5.9,21.0),(5.9,22.0),(6.9,23.15),(7.05,25.9),(8.15,26.75),(8.6,26.75),
                                  (7.85,25.5),(7.75,22.85),(6.7,21.65),(6.7,21.25),(8.15,19.6)],7.55,8.45,'ivory','ivory_edge')
    mesh.prism('obsidian_crown_spine',[(8.35,19.0),(9.75,21.2),(9.75,23.55),(8.95,25.95),(9.35,26.5),
                                     (10.45,23.65),(10.45,20.95),(9.0,18.8)],7.6,8.4,'obsidian','edge')
    mesh.lathe('crystal_neck',[ (18.4,0),(18.4,1.02),(18.8,1.02),(19.3,.76),(19.3,0)],'gold')
    return mesh


def seal_modules(frame):
    mesh=Mesh()
    phase=frame*math.tau/32
    # 仅三枚封印片低幅呼吸/偏转；主体和主晶不动，不影响发射点。
    for i,(cy,tilt,width) in enumerate([(11.7,-.16,2.85),(14.1,.18,3.10),(16.35,-.13,2.75)]):
        bob=.08*math.sin(phase+i*.65)
        yaw=.055*math.sin(phase+i*.7)
        pivot=(8,cy+bob)
        mesh.plate('seal_tablet',8,cy+bob,width,.88,1.50,'ivory',edge='ivory_edge',tilt=tilt,pivot=pivot,yaw=yaw)
        # 符印置于正反两面，随封印片同轴变换，不另挂粒子。
        for front in (True,False):
            z0,z1=(7.205,7.245) if front else (8.755,8.795)
            mesh.prism('obsidian_rune_socket',[(7.68,cy+bob-.29),(8.32,cy+bob-.29),(8.32,cy+bob+.29),(7.68,cy+bob+.29)],
                       z0,z1,'obsidian',tilt=tilt,pivot=pivot,yaw=yaw)
            z0,z1=(7.18,7.195) if front else (8.805,8.82)
            diamond=[(8,cy+bob-.19),(8.19,cy+bob),(8,cy+bob+.19),(7.81,cy+bob)]
            mesh.prism('violet_seal',diamond,z0,z1,'rune',tilt=tilt,pivot=pivot,yaw=yaw)
        mesh.lathe('seal_spacer',[(cy-.78,0),(cy-.78,.76),(cy-.52,.76),(cy-.52,0)],'gold')
    return mesh


def crystal(glow):
    mesh=Mesh()
    material='gem' if glow==0 else f'gem_glow{glow}'
    if glow:
        for suffix in ('','_light','_dark'):
            rgb=PALETTE['gem'+suffix]
            PALETTE[material+suffix]=tuple(round(v+(255-v)*glow/15*.62) for v in rgb)
    y=grip_constant('GEM_MODEL_Y')
    mesh.lathe('faceted_amethyst',[(y-2.7,0),(y-.5,1.12),(y+.55,1.02),(y+2.7,0)],material,6)
    return mesh


def display(rotation,translation,scale):
    return {'rotation':rotation,'translation':translation,'scale':[scale]*3}


def write_resources():
    OUT.mkdir(parents=True,exist_ok=True)
    create_texture()
    body=static_body();body.write(OUT/'wand.obj')
    for frame in range(32):
        seal_modules(frame).write(OUT/f'wand_motion_{frame:02}.obj')
    for glow in range(16):
        crystal(glow).write(OUT/f'wand_crystal_{glow}.obj')
    materials=[]
    for name,rgb in PALETTE.items():
        ka='1 1 1' if name.startswith('gem_glow') or name=='rune' else '.25 .25 .25' if name.startswith('gem') else '0 0 0'
        materials+=['newmtl '+name,'Kd '+' '.join(f'{v/255:.6f}' for v in rgb),'Ka '+ka,'map_Kd base','']
    (OUT/'wand.mtl').write_text('\n'.join(materials),encoding='utf-8')
    # 从宝石世界出生点反解原版手持变换，缩放/倾斜之后仍与发射口对齐。
    scale=grip_constant('FIRST_PERSON_SCALE')
    lean=math.radians(grip_constant('FIRST_PERSON_LEAN'))
    gem_y=(grip_constant('GEM_MODEL_Y')-8)/16*scale
    translation=[(grip_constant('SIDE')-.56+gem_y*math.sin(lean))*16,
                 (grip_constant('UP')+.52-gem_y*math.cos(lean))*16,
                 (.72-grip_constant('FORWARD'))*16]
    translation=[round(v,7) for v in translation]
    third_scale=grip_constant('THIRD_PERSON_SCALE')
    third_y=(8-grip_constant('GRIP_MODEL_Y'))*third_scale
    model={'loader':'neoforge:obj','model':'primalspells:models/item/wand.obj','flip_v':False,'automatic_culling':False,
           'shade_quads':True,'emissive_ambient':True,'ambientocclusion':False,
           'textures':{'particle':TEXTURE,'base':TEXTURE},'display':{
               'firstperson_righthand':display([0,0,grip_constant('FIRST_PERSON_LEAN')],translation,scale),
               'firstperson_lefthand':display([0,0,grip_constant('FIRST_PERSON_LEAN')],translation,scale),
               'thirdperson_righthand':display([0,0,0],[0,third_y,0],third_scale),
               'thirdperson_lefthand':display([0,0,0],[0,third_y,0],third_scale),
               'gui':display([10,-25,-26],[0,-3.1,0],.58),
               'ground':display([0,0,0],[0,3,0],.45),'fixed':display([0,0,0],[0,-3,0],.6)}}
    def write_json(path,value):
        path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    write_json(OUT/'wand.json',model)
    for tier in range(1,7):
        write_json(OUT/f'wand_t{tier}.json',{'parent':'primalspells:item/wand'})
    for kind,count in [('motion',32),('crystal',16)]:
        for index in range(count):
            name=f'wand_motion_{index:02}' if kind=='motion' else f'wand_crystal_{index}'
            write_json(OUT/f'{name}.json',{**model,'model':f'primalspells:models/item/{name}.obj'})
    def baked(name):
        return {'type':'minecraft:model','model':'primalspells:item/'+name}
    def dispatch(is_crystal):
        count=16 if is_crystal else 32
        return {'type':'minecraft:range_dispatch','property':'primalspells:wand_motion','crystal':is_crystal,
                'fallback':baked('wand_crystal_0' if is_crystal else 'wand_motion_00'),
                'entries':[{'threshold':i,'model':baked(f'wand_crystal_{i}' if is_crystal else f'wand_motion_{i:02}')} for i in range(count)]}
    item={'model':{'type':'minecraft:composite','models':[baked('wand'),dispatch(False),dispatch(True)]}}
    for name in ['wand']+[f'wand_t{i}' for i in range(1,7)]:
        write_json(ASSETS/f'items/{name}.json',item)
    print(f'Dusk seal: {body.faces} static faces, 32 seal frames, 16 crystal states; origin=({grip_constant("SIDE")}, {grip_constant("UP")}, {grip_constant("FORWARD")})')


if __name__=='__main__':
    write_resources()
