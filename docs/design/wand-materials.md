# 分面模块法杖资源

`python tools/wands/generate_models.py` 重建原生组合物品模型及OBJ/MTL。模型放 `assets/primalspells/models/item/`，物品定义放items；不需要额外模型依赖或自制渲染器。

稳定主体包括八面杖身、分节握柄、束环、宽托架、连接护片和肩部锁扣；主宝石中心固定(8,23.4,8)。侧晶、聚焦叶片和尾晶采用32帧idle、48t循环轻浮动/扭转，原生composite与range_dispatch选层/帧，不替换物品栈。

主宝石16级离散材质亮态，成功施法由WandCastPayload通知施法者及追踪观察者；WandModelProperty按wandId从当前亮度接续1t升亮、6t收尾，登出清缓存。主体不跳动，模块idle不移动实际发射锚点。

资源使用原版white_concrete乘有限色材质，不使用写实图集。第一/第三人称姿势和出生点见 [持杖表现](wand-casting.md)，风格见 [视觉规范](minecraft-visuals.md)。
