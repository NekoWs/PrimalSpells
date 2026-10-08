# 分面星辉法杖资源（第59轮）

运行 `python tools/wands/generate_models.py` 重建OBJ/MTL和7个显示模型。
低模网格使用八面杖身、分段握柄、阶梯束环、宽银色托架和六面蓝宝石；462个四边形，不依赖YSM。宝石中心保持(8,23.4,8)。

材质采用7种离散颜色：深靛杖身、靛灰握柄、银色托架、暗金束环，以及蓝/亮蓝/深蓝宝石切面。MTL的map_Kd绑定JSON的base纹理槽，使用原版minecraft:block/white_concrete乘Kd，避免写实材质脱离游戏画面。宝石弱环境发光，其他部件接受游戏面光照。
第58轮imagegen写实四区图集已移除；本轮直接生成原生几何和有限材质，不需要位图绘制或后处理。

第三人称使用WandArmPose官方枚举扩展，握柄处于手心。设计及锚点见[wand-casting.md](wand-casting.md)。
