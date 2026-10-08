# 分面模块法杖资源（第60轮）

运行 `python tools/wands/generate_models.py` 重建原生组合物品模型及OBJ/MTL。
主体584面：八面杖身、分段握柄、阶梯束环、宽银托架、四组连接护片和肩部锁扣。主宝石中心保持(8,23.4,8)。
模型分为稳定主体、悬浮模块、主宝石三个原生minecraft:composite层；主体不跳动。四枚侧晶、四枚聚焦叶片和尾晶组成32帧待机模块，2.4秒循环轻浮动与小幅扭转。WandModelProperty由NeoForge数值属性注册事件注册，原生range_dispatch选帧；不替换物品栈、不自制渲染器。
主宝石有四种离散亮度资源。真实成功施法发送WandCastPayload给施法者及追踪观察者，客户端按wandId记录短闪；3→2→1→0，6tick（约300ms）复原。亮态MTL使用满环境发光和离散浅蓝色，常态为深蓝色切面。断线清理，事件时清理过期记录。
模型JSON资源43个（7根杖+32待机+4晶体），OBJ共37个。全部使用原版white_concrete乘有限色材质；不新增写实图集或第三方模型依赖。
第一/第三视角沿用固定握柄和施法点，侧晶的运动不移动真实发射位置。详见[wand-casting.md](wand-casting.md)。
