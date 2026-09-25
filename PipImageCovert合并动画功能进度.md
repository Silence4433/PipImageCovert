# PipImageCovert 合并动画功能进度

更新日期：2026-09-25

## 当前方案
采用“基准帧 + 帧差补丁 + 组装描述”方案：第一帧作为基准图，其他帧只输出与基准帧不同的网格补丁；描述文件记录每帧补丁及其坐标。这样可避免把四帧完整图片全部保存。

## 已完成
- 新增 `FramePatchComposeBuilder`：扫描 PNG/JPG/JPEG，生成 `base.png`、补丁 PNG 和 `frame_patches.compose.properties`。
- 新增 `FramePatchComposeBuilderTest`：对 `temp_sprite_test_20260925` 四帧逐像素重建验证。
- 已验证：`PASS reconstruction diffPixels=0 frames=4 patches=102`。
- PipImageCovert 主窗口新增“帧差补丁转换”入口，打开 `FramePatchComposeTool`。

## 输出说明
- `base.png`：基准帧。
- `patch_XXXX.png`：按坐标保存的差异区域。
- `frame_patches.compose.properties`：记录格式、帧数、基准图以及 `frame.N` 的补丁文件和坐标。

## 编码纪律
旧版 PipImageCovert Java 文件保持 GBK/CP936；本次新增独立 Java 文件使用 UTF-8 无 BOM。修改 `ImageCovert.java` 前后均按 CP936 读取/写回，禁止整体转码。

## 尚未完成
- 尚未把组装描述接入 New_ImageWorkShop 的 AnimateEditor/CTS 保存链路。
- 尚未输出可直接被现有 PIP 解析器读取的正式 PIP 文件；当前输出为 PNG 补丁和组装描述，便于先验证算法。
- 旧版区域合并/配置切分功能暂不移除，待新链路在 Eclipse 和 ImageWorkShop 人工验证后再处理。

## 测试边界
核心生成与逐像素重建已在工作区完成；GUI 运行、CTS/CTN 实际保存和游戏内加载尚未验证。
## 2026-09-25 继续进展
- New_ImageWorkShop 新增 UTF-8 无 BOM 数据层：ComposeDescription、FramePatchComposer。
- 数据层已使用 JDK 1.6 编译通过，可读取 PipImageCovert 配置并按帧重建 BufferedImage。
- AnimateEditor 尚未直接改写，避免在未完成 API 对齐前破坏旧版 GBK 文件；下一步是将重建结果转换为现有 PipAnimateFrame/PipAnimateFramePiece。


## 2026-09-25 最终接入与验证
- `FramePatchCtsBuilder.indexedTemp` 已改为调用工程自身 `PngFile.writePng()` 路径（通过 `PngFile.writeIndexedPng`），不再使用 `ImageIO.write` 作为正式 PIP 输入 PNG 编码器。
- 修正 `PngFile.parseScanlines` 的 8 位索引行长度为实际宽度，避免 `PipImage.create` 读取时多出一个像素导致数组越界。
- `AnimateEditor.onLoadFramePatchCompose` 已接入完整 GUI 输出流程：选择 compose 配置、输出目录和 CTS 文件，调用 `FramePatchCtsBuilder.build` 生成 PIP/CTS。
- 针对 CTS 最多 64 个源文件的旧格式限制，超过 64 个帧差补丁时自动回退为逐帧完整 PIP；本次四帧测试输入生成 `frame_patch.cts`，验证结果：`PASS cts build bytes=356`。
- JDK 1.6 编译验证通过：`PngFile.java` 使用 GBK/CP936，新增数据层使用 UTF-8；`FramePatchCtsBuilder`、`ComposeDescription`、`FramePatchComposer`、测试类编译通过。
- `AnimateEditor.java` 修改前后已用 CP936 读写并完成编码往返检查。GUI 启动和真实游戏加载仍需在 Eclipse/SWT 环境与游戏资源链中验证。

## 用户补充信息
从现在开始如果你要在某个工程新增或修改旧文件，更改前必须确认当前文件编码格式，若是新建文件，则必须确认该工程内的其他源码文件编码格式，保持一致，对于ｐｉｐｉｍａｇｅｃｏｖｅｒｔ和ｎｅｗ＿ｉｍａｇｅｗｏｒｋｓｈｏｐ这两个工程应该是全部保持ｇｂｋ编码格式。你也可以改动前事前确认，然后再改！这点非常非常非常重要！不要在发生这种事故，刚才已经发生过两次了。第一次使我今天一整天的工作全部白做了！只能从ｇｉｔ恢复。把这段内容放到你的记忆memory里。
