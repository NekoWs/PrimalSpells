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
    'obsidian': (38, 33, 47), 'edge': (77, 65, 91),
    'ivory': (222, 210, 193), 'ivory_edge': (154, 143, 143), 'ivory_light': (249, 239, 218),
    'gold': (190, 151, 83), 'gold_light': (241, 214, 154), 'gold_dark': (102, 76, 42),
    'grip': (65, 35, 77), 'wrap': (100, 58, 115),
    'gem': (146, 49, 221), 'gem_light': (206, 130, 255), 'gem_dark': (79, 22, 135),
    'gem_core': (233, 185, 255), 'gem_vein': (255, 228, 255),
    'rune': (189, 112, 248),
}
TILES = {name: i for i, name in enumerate(PALETTE)}
ATLAS_SIZE = 128
TILE_SIZE = 16
ATLAS_COLUMNS = ATLAS_SIZE // TILE_SIZE
MODEL_YAW = -20



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
        self.groups = {}

    def group(self, name):
        # NeoForge按组名建立部件表；重复束环/封印片不能覆盖同名的前一块。
        index = self.groups.get(name, 0)
        self.groups[name] = index + 1
        self.lines.append(f'g {name}_{index}')

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
        # 亮态仍使用同一块基底UV，不能在施法时跳到另一种材质纹理。
        tile = TILES.get(material.split('_glow')[0], TILES['gem'])
        x, y = tile % ATLAS_COLUMNS, tile // ATLAS_COLUMNS
        uv = [(0, 1), (1, 1), (1, 0), (0, 0)]
        self.lines.append('usemtl ' + material)
        for point, (a, b) in zip(points, uv):
            self.lines += [
                'v ' + ' '.join(f'{c/16:.7f}' for c in point),
                f'vt {(x*TILE_SIZE+0.5+a*(TILE_SIZE-1))/ATLAS_SIZE:.7f} {1-(y*TILE_SIZE+0.5+b*(TILE_SIZE-1))/ATLAS_SIZE:.7f}',
                'vn ' + ' '.join(f'{c:.7f}' for c in normal),
            ]
        self.lines.append('f ' + ' '.join(f'{self.vertices+j+1}/{self.vertices+j+1}/{self.vertices+j+1}' for j in range(len(points))))
        self.vertices += len(points)
        self.faces += 1

    def lathe(self, name, profile, material, sides=8, cx=8, cz=8, rotation=0, trim=None):
        self.group(name)
        for (y0, r0), (y1, r1) in zip(profile, profile[1:]):
            for j in range(sides):
                a, b = (j+.5)*math.tau/sides+rotation, (j+1.5)*math.tau/sides+rotation
                mat = [material, material+'_light', material, material+'_dark'][j % 4] if material == 'gem' or material.startswith('gem_glow') else material
                if trim and r0 > 0 and r1 > 0 and r0 != r1:
                    mat = trim
                self.face([(cx+r*math.cos(t), y, cz+r*math.sin(t))
                           for y, r, t in [(y0,r0,a),(y1,r1,a),(y1,r1,b),(y0,r0,b)]], mat)

    def prism(self, name, polygon, front, back, material, edge=None, tilt=0, pivot=(8, 0), yaw=0, bevel=0, rim=None):
        """封闭挤出并切出真实倒角；凹轮廓先内缩，随后对端面耳切。"""
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

        def inset(points, distance):
            result=[]
            for i,b in enumerate(points):
                a,c=points[i-1],points[(i+1)%len(points)]
                u=(b[0]-a[0],b[1]-a[1]);v=(c[0]-b[0],c[1]-b[1])
                ul,vl=math.hypot(*u),math.hypot(*v)
                n0=(-u[1]/ul,u[0]/ul);n1=(-v[1]/vl,v[0]/vl)
                denom=1+n0[0]*n1[0]+n0[1]*n1[1]
                if denom < 1e-6:
                    raise ValueError('Invalid bevel corner: '+name)
                result.append((b[0]+distance*(n0[0]+n1[0])/denom,b[1]+distance*(n0[1]+n1[1])/denom))
            return result

        cap=inset(polygon,bevel) if bevel else polygon
        indices=list(range(len(cap)))
        triangles=[]
        while len(indices)>3:
            for i,b in enumerate(indices):
                a,c=indices[i-1],indices[(i+1)%len(indices)]
                if cross(cap[a],cap[b],cap[c])<=1e-9:
                    continue
                if any(all(cross(cap[u],cap[v],cap[p])>=-1e-9 for u,v in [(a,b),(b,c),(c,a)]) for p in indices if p not in (a,b,c)):
                    continue
                triangles.append((a,b,c));indices.pop(i);break
            else:
                raise ValueError('Invalid polygon: '+name)
        triangles.append(tuple(indices))
        for tri in triangles:
            self.face([transform(cap[i],front) for i in reversed(tri)],material)
            self.face([transform(cap[i],back) for i in tri],material)
        for i,a in enumerate(polygon):
            j=(i+1)%len(polygon);b=polygon[j]
            self.face([transform(a,front+bevel),transform(b,front+bevel),transform(b,back-bevel),transform(a,back-bevel)],edge or material)
            if bevel:
                self.face([transform(a,front+bevel),transform(cap[i],front),transform(cap[j],front),transform(b,front+bevel)],rim or material)
                self.face([transform(a,back-bevel),transform(b,back-bevel),transform(cap[j],back),transform(cap[i],back)],rim or material)

    def plate(self, name, cx, cy, width, height, depth, material, **kwargs):
        b = min(width, height) * .18
        x0, x1, y0, y1 = cx-width/2, cx+width/2, cy-height/2, cy+height/2
        self.prism(name, [(x0+b,y0),(x1-b,y0),(x1,y0+b),(x1,y1-b),(x1-b,y1),(x0+b,y1),(x0,y1-b),(x0,y0+b)],
                   8-depth/2, 8+depth/2, material, **kwargs)

    def write(self, path):
        path.write_text('\n'.join(self.lines)+'\n', encoding='utf-8')


def create_texture():
    # 材质各有自己的大色块结构；亮边由造型和明确色阶承担，不用统一棋盘噪点。
    image=Image.new('RGBA',(ATLAS_SIZE,ATLAS_SIZE),(255,255,255,255))
    draw=ImageDraw.Draw(image)
    rng=random.Random(9471)
    for name,index in TILES.items():
        ox,oy=index%ATLAS_COLUMNS*TILE_SIZE,index//ATLAS_COLUMNS*TILE_SIZE
        def rect(box,gray):
            draw.rectangle(tuple(v+(ox if i%2==0 else oy) for i,v in enumerate(box)),fill=(gray,gray,gray,255))
        def polygon(points,gray):
            draw.polygon([(ox+x,oy+y) for x,y in points],fill=(gray,gray,gray,255))
        rect((0,0,15,15),245)
        if name=='gem_vein':
            rect((0,0,15,15),255)
        elif name=='gem_core':
            rect((0,0,15,15),248)
            polygon([(0,0),(8,0),(3,15),(0,15)],255)
            polygon([(8,0),(15,8),(15,15),(3,15)],240)
        elif name.startswith('gem'):
            rect((0,0,15,15),212)
            polygon([(0,0),(8,0),(4,9),(0,15)],252)
            polygon([(8,0),(15,0),(15,11),(4,9)],232)
            polygon([(4,9),(15,11),(15,15),(0,15)],180)
        elif name.startswith('ivory'):
            rect((0,0,15,1),255);rect((0,2,1,15),252)
            rect((2,13,15,15),211);rect((13,2,15,12),224)
            for _ in range(7):
                x,y=rng.randrange(2,12,2),rng.randrange(3,12,2)
                rect((x,y,x+1,y+1),rng.choice([218,229,252]))
        elif name.startswith('gold'):
            rect((0,0,15,2),255);rect((0,3,15,4),239)
            rect((0,11,15,12),207);rect((0,13,15,15),180)
        elif name in ('grip','wrap'):
            rect((0,0,15,2),255);rect((0,13,15,15),187)
            polygon([(0,7),(0,10),(15,3),(15,0)],220)
        elif name!='rune':
            rect((0,0,2,15),255);rect((13,0,15,15),184)
            rect((4,0,5,15),233);rect((0,13,15,15),213)
    target=ASSETS/'textures/item/wand_dusk.png'
    target.parent.mkdir(parents=True,exist_ok=True)
    image.save(target)


def binding(mesh,name,y,radius=.95):
    mesh.lathe(name,[(y,0),(y,radius-.14),(y+.10,radius),(y+.37,radius),(y+.48,radius-.14),(y+.48,0)],'gold',trim='gold_light')
    mesh.lathe(name+'_dark_seam',[(y+.18,radius+.005),(y+.25,radius+.005)],'gold_dark')


def static_body():
    mesh=Mesh()
    mesh.lathe('obsidian_spine',[(-3,0),(-2.5,.6),(7,.6),(8,.7),(18.8,.6),(19.2,0)],'obsidian')
    mesh.lathe('plum_grip',[(0,0),(0,.8),(.4,.88),(5.9,.88),(6.4,.8),(6.4,0)],'grip')
    for y in (0,6.4,8.8):
        binding(mesh,'chamfered_gold_binding',y)
    # 真正跨棱面的斜缠握带，区别于一圈圈圆柱束环。
    mesh.group('diagonal_plum_wrap')
    for row in range(4):
        for j in range(8):
            a,b=(j+.5)*math.tau/8,(j+1.5)*math.tau/8
            ya=.45+row*1.32+j/8*.9;yb=ya+.9/8
            mesh.face([(8+.918*math.cos(angle),y,8+.918*math.sin(angle)) for angle,y in [(a,ya),(a,ya+.67),(b,yb+.67),(b,yb)]], 'wrap' if j%3 else 'grip')
    binding(mesh,'pommel_socket',-2.7,.91)
    mesh.prism('obsidian_pommel',[(7.02,-2.7),(8,-4.8),(8.98,-2.7),(8.65,-1.65),(7.35,-1.65)],7.4,8.6,'obsidian','edge',bevel=.13,rim='edge')
    mesh.lathe('pommel_inset',[(-4.05,0),(-3.3,.40),(-2.6,0)],'gem',4)
    # 宽窄变化、真实倒角和内侧暗边让折臂成为立体零件，而非平面套圈。
    mesh.prism('ivory_seal_crown',[(7.45,18.9),(5.68,21.22),(5.68,22.16),(6.94,23.26),(7.10,25.98),(8.22,26.75),(8.74,26.75),
                                  (7.98,25.42),(7.86,22.85),(6.58,21.64),(6.62,21.33),(8.18,19.62)],7.40,8.60,'ivory','ivory_edge',bevel=.13,rim='ivory_light')
    mesh.prism('obsidian_crown_spine',[(8.35,19.0),(9.78,21.2),(9.78,23.55),(8.95,25.95),(9.35,26.5),
                                     (10.52,23.65),(10.52,20.95),(9.0,18.8)],7.46,8.54,'obsidian','edge',bevel=.10,rim='edge')
    binding(mesh,'lower_crystal_collar',18.28,1.05)
    binding(mesh,'upper_crystal_collar',19.08,.91)
    # 颈部插片形成上下错层，不把主晶重新关进四面笼架。
    mesh.plate('neck_socket',8,18.05,1.45,.85,1.28,'obsidian',edge='edge',bevel=.10)
    return mesh


def seal_modules(frame):
    mesh=Mesh()
    phase=frame*math.tau/32
    # 封印片收成斜向分层的短簇；轻微呼吸不能破坏层次或发射锚点。
    for i,(cy,tilt,width) in enumerate([(13.35,-.20,2.65),(15.0,.18,3.05),(16.65,-.18,2.80)]):
        bob=.065*math.sin(phase+i*.65)
        yaw=.05*math.sin(phase+i*.7)
        pivot=(8,cy+bob)
        mesh.plate('seal_shadow_base',8,cy+bob-.31,width-.28,.68,1.25,'ivory_edge',tilt=tilt,pivot=pivot,yaw=yaw,bevel=.07)
        mesh.plate('chamfered_seal_tablet',8,cy+bob,width,1.08,1.65,'ivory',edge='ivory_edge',tilt=tilt,pivot=pivot,yaw=yaw,bevel=.095,rim='ivory_light')
        for front in (True,False):
            z0,z1=(7.125,7.16) if front else (8.84,8.875)
            mesh.prism('inset_rune_socket',[(7.64,cy+bob-.34),(8.36,cy+bob-.34),(8.36,cy+bob+.34),(7.64,cy+bob+.34)],z0,z1,'obsidian','edge',tilt=tilt,pivot=pivot,yaw=yaw,bevel=.025)
            z0,z1=(7.095,7.11) if front else (8.89,8.905)
            # 方印外框包住中间暗芯，在小尺寸下也保留“封印”而非钻石贴纸的形状。
            for k in range(4):
                points=[(-.24,-.24),(.24,-.24),(.24,.24),(-.24,.24)]
                a,b=points[k],points[(k+1)%4]
                ai,bi=(a[0]*.61,a[1]*.61),(b[0]*.61,b[1]*.61)
                polygon=[(8+x,cy+bob+y) for x,y in [a,b,bi,ai]]
                mesh.prism('violet_square_seal',polygon,z0,z1,'rune',tilt=tilt,pivot=pivot,yaw=yaw)
            mesh.prism('seal_inner_facet',[(8,cy+bob-.105),(8.105,cy+bob),(8,cy+bob+.105),(7.895,cy+bob)],z0-.005,z1+.005,'gem_core',tilt=tilt,pivot=pivot,yaw=yaw)
        binding(mesh,'tablet_spacer',cy-.96,.77)
    return mesh


def crystal(glow):
    mesh=Mesh()
    def material(base):
        if glow==0:
            return base
        name=base+f'_glow{glow}'
        rgb=PALETTE[base]
        PALETTE[name]=tuple(round(v+(255-v)*glow/15*.58) for v in rgb)
        return name
    y=grip_constant('GEM_MODEL_Y')
    top=(8.03,y+2.7,8.02);bottom=(7.97,y-2.7,7.98)
    ring=[(6.94,y,8),(8,y,7.18),(9.06,y,8),(8,y,8.82)]
    mesh.group('cut_amethyst_facets')
    # 直接把晶面切成互不重叠的色阶区，亮脉属于表面本身，避免贴面深度冲突。
    boundary=[(1,0,0),(.57,.43,0),(.43,.57,0),(0,1,0),(0,.55,.45),(0,.45,.55),(0,0,1),(.45,0,.55),(.55,0,.45)]
    center=(.31,.35,.34)
    for i,a in enumerate(ring):
        b=ring[(i+1)%4]
        for half,(tri,base) in enumerate([((a,top,b),['gem_light','gem','gem_dark','gem'][i]),((a,b,bottom),['gem','gem_dark','gem','gem_light'][i])]):
            def point(weights):
                return tuple(sum(tri[j][k]*weights[j] for j in range(3)) for k in range(3))
            for part,weights in enumerate(boundary):
                next_weights=boundary[(part+1)%len(boundary)]
                face_material='gem_vein' if part==1 else 'gem_core' if part in (0,3,4,6,7) else 'gem_light' if part==5 else base
                mesh.face([point(weights),point(next_weights),point(center)],material(face_material))
    return mesh


def display(rotation,translation,scale):
    return {'rotation':rotation,'translation':translation,'scale':[scale]*3,'right_rotation':[0,MODEL_YAW,0]}


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
        ka='1 1 1' if '_glow' in name or name in ('rune','gem_vein') else '.9 .9 .9' if name=='gem_core' else '.75 .75 .75' if name.startswith('gem') else '0 0 0'
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
