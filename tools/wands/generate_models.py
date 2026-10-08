#!/usr/bin/env python3
"""原创星辉法杖OBJ：旋切杖身、曲线金属托架、长切面宝石和绘制图集。"""
import json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/primalspells/models/item'
lines=['# Original astral wand; block-unit coordinates', 'mtllib wand.mtl']; vertex=0

def face(points, material, patch=(0,0,1,1)):
    global vertex
    ox,oy={'obsidian':(0,0),'gold':(.5,0),'gem':(0,.5),'leather':(.5,.5)}[material]
    u0,v0,u1,v1=patch
    lines.append('usemtl '+material)
    for p,(u,v) in list(zip(points,[(u0,v1),(u1,v1),(u1,v0),(u0,v0)]))[::-1]:
        lines.append('v '+' '.join(f'{c/16:.7f}' for c in p))
        lines.append(f'vt {ox+.015+u*.47:.7f} {oy+.015+v*.47:.7f}')
    lines.append('f '+' '.join(f'{vertex+j+1}/{vertex+j+1}' for j in range(len(points))))
    vertex+=len(points)

def lathe(name, profile, material, sides=16, twist=0):
    lines.append('g '+name)
    for k,((y0,r0),(y1,r1)) in enumerate(zip(profile,profile[1:])):
        for j in range(sides):
            a,b=j*math.tau/sides,(j+1)*math.tau/sides
            points=[(8+r*math.cos(t+y*twist),y,8+r*math.sin(t+y*twist)) for y,r,t in [(y0,r0,a),(y0,r0,b),(y1,r1,b),(y1,r1,a)]]
            face(points,material,(j/sides,k/(len(profile)-1),(j+1)/sides,(k+1)/(len(profile)-1)))

def tube(name,path,radii,material,sides=8):
    lines.append('g '+name)
    for k in range(len(path)-1):
        for j in range(sides):
            a,b=j*math.tau/sides,(j+1)*math.tau/sides
            points=[(p[0]+r*math.cos(t),p[1],p[2]+r*math.sin(t)) for p,r,t in [(path[k],radii[k],a),(path[k],radii[k],b),(path[k+1],radii[k+1],b),(path[k+1],radii[k+1],a)]]
            face(points,material,(j/sides,k/(len(path)-1),(j+1)/sides,(k+1)/(len(path)-1)))

lathe('obsidian_spine',[(-2.8,0),(-2.4,.55),(0,.62),(7.5,.63),(9,.8),(12,.53),(17,.4),(20,.35),(20.2,0)],'obsidian',20,.07)
lathe('stitched_grip',[(0,.64),(.3,.74),(6.6,.72),(7.1,.64)],'leather',20)
for y in [-2.3,0,7.1,8.8,17.4]:
    lathe('engraved_collar',[(y,.62),(y+.12,.85),(y+.35,.85),(y+.5,.59)],'gold',24)
lathe('pommel_sapphire',[(-3.5,0),(-3,.46),(-2.55,.3)],'gem',8)
# 双螺旋镶线与四根错高的细托爪，留下宝石周围负空间。
for lane in range(2):
    path=[]
    for j in range(65):
        p=j/64; y=7.7+p*10; a=lane*math.pi+p*math.tau*1.25; r=.62-.2*p
        path.append((8+r*math.cos(a),y,8+r*math.sin(a)))
    tube('gold_inlay',path,[.07]*len(path),'gold',6)
for lane in range(4):
    path=[];radii=[]
    for j in range(49):
        p=j/48;a=lane*math.pi/2+.75*p;r=.4+2.1*math.sin(math.pi*p*.87);y=17.7+p*(9.3 if lane%2==0 else 7.5)
        path.append((8+r*math.cos(a),y,8+r*math.sin(a)));radii.append(.17*(1-p)+.025)
    tube('swept_crown',path,radii,'gold',8)
# 光学中心与第一视角粒子锚点保持同一坐标。
lathe('astral_sapphire',[(19.8,0),(21.8,1.35),(24.8,1.2),(27,0)],'gem',8,.012)
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'wand.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
materials=[]
for material in ['obsidian','gold','gem','leather']:
    materials+=['newmtl '+material,'Kd 1 1 1','Ka '+('0.6 0.6 0.6' if material=='gem' else '0 0 0'),'map_Kd atlas','']
(OUT/'wand.mtl').write_text('\n'.join(materials),encoding='utf-8')
def display(r,t,s):return {'rotation':r,'translation':t,'scale':[s]*3}
model={'loader':'neoforge:obj','model':'primalspells:models/item/wand.obj','flip_v':False,'automatic_culling':False,'shade_quads':True,'emissive_ambient':True,'ambientocclusion':False,'textures':{'particle':'primalspells:item/wand_atlas','atlas':'primalspells:item/wand_atlas'},'display':{
'firstperson_righthand':display([0,0,0],[-2.88,0,-2.08],.8),
'firstperson_lefthand':display([0,0,0],[-2.88,0,-2.08],.8),
# 握柄而非杖身中心进入手心，杖身与臂轴交叉。
 'thirdperson_righthand':display([0,0,0],[0,2.5,0],.5),
 'thirdperson_lefthand':display([0,0,0],[0,2.5,0],.5),
 'gui':display([15,-35,-30],[0,-3.2,0],.58),'ground':display([0,0,0],[0,3,0],.45),'fixed':display([0,0,0],[0,-3,0],.6)}}
(OUT/'wand.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
for tier in range(1,7):(OUT/f'wand_t{tier}.json').write_text(json.dumps({'parent':'primalspells:item/wand'},indent=2)+'\n',encoding='utf-8')
print(f'7 models; {vertex} vertices; original atlas; gem=(8,23.4,8)')
