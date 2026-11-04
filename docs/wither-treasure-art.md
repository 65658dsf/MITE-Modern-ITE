# 凋零宝藏立体模型

作者：NingMeng123

凋零宝藏使用原生 Minecraft JSON 几何模型，在背包、掉落物和手持状态下均为实体箱形。外观参考用户提供的黑石、青色纹路和骨饰宝箱：黑石及深板岩箱体、青色封印纹路、骨白包角与立柱，以及具有凹陷眼窝、青色眼睛、鼻梁和独立牙齿的正面骷髅锁扣。模型不使用图片平面或 `minecraft:item/generated`。

模型由 `tools/wither_treasure_model.mjs` 确定性生成，运行 `node tools/wither_treasure_model.mjs` 可同时重建并验证三个 JSON。闭合模型包含 124 个具有正体积的立方体，严格等于箱体 74 个元素与盖子 50 个元素的拼接。各元素六面均使用原生合法面定义，所有贴图路径已在 Minecraft 1.21.1 资源中核对存在。

箱底、四壁和四边框分别建模，箱体中心保留真实空腔；内壁使用黑色混凝土。盖子、上包角和锁扣作为整体围绕 `(8, 8.1, 13)` 后铰链开合，骨饰跟随所属箱体或盖子移动。模型坐标和显示变换沿用宝石箱的手持规范，整体包围盒为 `[0.95, 0.3, 1.88]` 至 `[15.05, 11.65, 13.6]`，可复用其握持动作。

采用的原版贴图为 `blackstone`、`deepslate_tiles`、`polished_blackstone`、`black_concrete`、`cyan_concrete`、`light_blue_concrete`、`bone_block_side` 和 `bone_block_top`。青色封印为附着在表面的浅立方体，使用普通原版光照。

原始参考图 SHA-256 为 `36157C5976CA79A3D5AFD4904D398EF15E6757CDB7DEFE29BBEFEBB6488E3897`。之前经内置 `image_gen` 移除黑色外背景的 128×128 PNG 继续保留作样式参考，当前物品模型不再引用它。此次立体模型完全由代码和原版贴图生成，没有新增 AI 图像。

参考用透明 PNG 的生成原件为 1254×1254，SHA-256 为 `38D584BA2C0B6D8536A33FB65EE9815327ED2439387530340416D775581B7DB8`。使用 Windows `System.Drawing` 将其等比缩小为 128×128 的 RGBA PNG，保留生成的 alpha；未另行抠图、重绘或替换背景。

工程文件：

- `src/main/resources/assets/modern_ite/models/item/wither_treasure.json`
- `src/main/resources/assets/modern_ite/models/item/wither_treasure_body.json`
- `src/main/resources/assets/modern_ite/models/item/wither_treasure_lid.json`
- `tools/wither_treasure_model.mjs`
- `src/main/resources/assets/modern_ite/textures/item/wither_treasure.png`（参考图，模型不引用）

三个模型均继承 `minecraft:block/block`，其中闭合模型声明物品显示变换，箱体和盖子保留共用局部坐标用于动画渲染。

之前处理参考 PNG 使用的完整提示词：

```text
Use case: background-extraction. Asset type: Minecraft item icon PNG for a wither treasure chest. Input image 1 is the edit target. Remove only the solid black exterior background and make it genuinely transparent with alpha. Preserve the entire original closed chest exactly: its isometric view, black and dark gray blocky panels, cyan glowing seams and runes, ivory skull corner braces, central ivory skull clasp, pixelated texture, silhouette, shading, and position. Keep dark pixels belonging to the chest opaque. Center the complete chest in a square canvas with small clear margins, no cropping. No ground plane, no cast shadow, no text, no new objects, no glow outside silhouette, no checkerboard painted into the image. Only change the exterior background to alpha transparency.
```
