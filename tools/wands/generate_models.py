#!/usr/bin/env python3
"""分面低模星辉法杖：宽托架、阶梯束环和实色蓝宝石，无写实图集。"""
import json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/primalspells/models/item'
lines=['# Faceted astral wand; block-unit coordinates', 'mtllib wand.mtl']; vertex=0
PALETTE={'body':(33,38,65),'grip':(63,59,81),'silver':(183,197,207),'gold':(210,166,77),'gem':(47,117,189),'gem_light':(113,172,236),'gem_dark':(36,65,120)}

def face(points, material):
    global vertex
    lines.append('usemtl '+material)
    for p,(u,v) in list(zip(points,[(0,1),(1,1),(1,0),(0,0)]))[::-1]:
        lines.append('v '+' '.join(f'{c/16:.7f}' for c in p))
        lines.append(f'vt {u} {v}')
    lines.append('f '+' '.join(f'{vertex+j+1}/{vertex+j+1}' for j in range(len(points))))
    vertex+=len(points)

def lathe(name,profile,material,sides=8):
    lines.append('g '+name)
    for (y0,r0),(y1,r1) in zip(profile,profile[1:]):
        for j in range(sides):
            a,b=(j+.5)*math.tau/sides,(j+1.5)*math.tau/sides
            mat=material
            if material=='gem': mat=['gem','gem_light','gem','gem_dark'][j%4]
            face([(8+r*math.cos(t),y,8+r*math.sin(t)) for y,r,t in [(y0,r0,a),(y0,r0,b),(y1,r1,b),(y1,r1,a)]],mat)

def tube(name,path,radii,material,sides=4):
    lines.append('g '+name)
    for k in range(len(path)-1):
        for j in range(sides):
            a,b=(j+.5)*math.tau/sides,(j+1.5)*math.tau/sides
            face([(p[0]+r*math.cos(t),p[1],p[2]+r*math.sin(t)) for p,r,t in [(path[k],radii[k],a),(path[k],radii[k],b),(path[k+1],radii[k+1],b),(path[k+1],radii[k+1],a)]],material)

lathe('beveled_spine',[(-3,0),(-2.5,.9),(7.5,.9),(9,1.1),(10,.7),(17.8,.65),(19,.95),(19.4,0)],'body')
lathe('segmented_grip',[(0,0),(0,.99),(1,1.08),(6,1.08),(7,.99),(7,0)],'grip')
for y in [-2.4,0,6.8,9,17.4]:
    lathe('stepped_binding',[(y,0),(y,1.12),(y+.35,1.12),(y+.65,.87),(y+.65,0)],'gold')
for y in [1.7,3.4,5.1]:
    lathe('grip_band',[(y,0),(y,1.1),(y+.25,1.1),(y+.25,0)],'body')
lathe('pommel_stone',[(-3.6,0),(-3.1,.7),(-2.5,.4)],'gem',6)
# 宽幅镶片和少量折线代替细螺旋；四根托架采用明确的三段轮廓。
for lane in range(4):
    a=lane*math.pi/2
    path=[(8+r*math.cos(a),y,8+r*math.sin(a)) for r,y in [(0.7,17.8),(2.6,20.3),(2.6,24.9),(1.7,26.2 if lane%2==0 else 25.5)]]
    tube('angular_crown',path,[.55,.45,.38,.22],'silver')
    path=[(8+r*math.cos(a),y,8+r*math.sin(a)) for r,y in [(0.73,10),(0.73,13),(0.65,16.9)]]
    tube('broad_inlay',path,[.18,.25,.18],'gold')
lathe('crown_binding',[(18.4,0),(18.4,1.25),(19,1.25),(19,0)],'gold')
# 分面宝石中心保持(8,23.4,8)，使实体投射物与原来的模型锚点一致。
lathe('faceted_sapphire',[(20,0),(21.4,1.75),(24.9,1.75),(26.8,0)],'gem',6)
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'wand.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
materials=[]
for material,rgb in PALETTE.items():
    materials+=['newmtl '+material,'Kd '+' '.join(f'{v/255:.6f}' for v in rgb),'Ka '+('0.25 0.25 0.25' if material.startswith('gem') else '0 0 0'),'map_Kd base','']
(OUT/'wand.mtl').write_text('\n'.join(materials),encoding='utf-8')
def display(r,t,s):return {'rotation':r,'translation':t,'scale':[s]*3}
model={'loader':'neoforge:obj','model':'primalspells:models/item/wand.obj','flip_v':False,'automatic_culling':False,'shade_quads':True,'emissive_ambient':True,'ambientocclusion':False,'textures':{'particle':'minecraft:block/white_concrete','base':'minecraft:block/white_concrete'},'display':{
'firstperson_righthand':display([0,0,0],[-2.88,0,-2.08],.8),
'firstperson_lefthand':display([0,0,0],[-2.88,0,-2.08],.8),
 'thirdperson_righthand':display([0,0,0],[0,2.5,0],.5),
 'thirdperson_lefthand':display([0,0,0],[0,2.5,0],.5),
 'gui':display([15,-35,-30],[0,-3.2,0],.58),'ground':display([0,0,0],[0,3,0],.45),'fixed':display([0,0,0],[0,-3,0],.6)}}
(OUT/'wand.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
for tier in range(1,7):(OUT/f'wand_t{tier}.json').write_text(json.dumps({'parent':'primalspells:item/wand'},indent=2)+'\n',encoding='utf-8')
print(f'7 models; {vertex} vertices; {vertex//4} faces; 7 flat materials; gem=(8,23.4,8)')
