# 原创星辉法杖资源

运行 `python tools/wands/generate_models.py` 重建OBJ/MTL和7个显示模型。
网格用旋切和细管曲线生成，不使用原版方块材质；宝石光学中心固定为(8,23.4,8)。
材质使用内置imagegen工具生成，保存原始PNG到 `src/main/resources/assets/primalspells/textures/item/wand_atlas.png`，未经裁切或位图后处理。

## 最终提示词

Use case: stylized-concept. Asset type: production albedo material atlas for an original luxurious fantasy magic wand 3D game model, NOT Minecraft or pixel art. One square image divided into exactly four equal square quadrants, no margins or separators, each quadrant an uninterrupted tiling surface photographed orthographically: TOP LEFT: deep midnight blue black polished obsidian with delicate sparse engraved celestial scrollwork, no big motifs; TOP RIGHT: warm antique champagne gold, finely brushed, delicate chased filigree, refined luxurious metal with subdued patina; BOTTOM LEFT: deep blue violet luminous crystalline sapphire gemstone surface with angular refracting highlights and internal starlight, no object silhouette; BOTTOM RIGHT: dark indigo leather fine grain with subtle stitched texture. Beautiful premium fantasy craftsmanship; clean usable texture map, evenly lit, no text, labels, objects, staff, border, watermark or background. Quadrants MUST align at half width and half height.

第三人称使用WandArmPose的官方枚举扩展。参考：[NeoForge枚举扩展文档](https://docs.neoforged.net/docs/advanced/extensibleenums/)，构造器签名以当前26.2本地源码为准。
