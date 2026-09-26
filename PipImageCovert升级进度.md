# PipImageCovert 升级进度

## 当前状态

第一版“批量共享调色板”和第二版“图集与切分文件”均已完成，并已接入原 `ImageCovert` 主窗口。

## 已完成内容

新增独立启动类：

```text
com.gtalee.covert.SharedPaletteBatchTool
```

已实现：

1. 选择一个包含 PNG 的输入目录；
2. 选择独立输出目录；
3. 汇总目录内全部 PNG 的不透明像素；
4. 使用带像素频次权重的 Median Cut 为整批图片生成一套公共调色板；
5. 总颜色数可设置为 2～256，默认 248，包含固定的透明索引 0；
6. Alpha 阈值可设置为 0～255，默认 128；
7. 支持无抖动与 Floyd-Steinberg 抖动；
8. 所有输出 PNG 使用相同的 `IndexColorModel`、调色板内容和索引顺序；
9. 自动输出 ImageWorkShop 扩展 ACT：前768字节为256项RGB，末尾记录实际颜色数和透明索引0；
10. 自动输出 `shared_palette_preview.png` 调色板预览；
11. 自动输出 UTF-8 编码的 `shared_palette_report.txt`；
12. 输入、输出目录相同时拒绝执行；
13. 任一同名输出已存在时拒绝覆盖；
14. 输入目录仅处理当前层 PNG，不递归处理子目录；
15. 原有单图转换功能保持不变；主窗口新增“批量统一调色板”和“图集与切分文件”两个入口。

第二版已实现：

1. 读取第一版输出的同调色板索引 PNG；
2. 校验全部 PNG 的 `PLTE` 与 `tRNS` 完全一致；
3. 可选裁剪四周全透明区域；
4. 支持紧密行排列和规则网格两种图集布局；
5. 可配置图块间距和网格列数；
6. 校验图块数量不超过255、单图块宽高不超过255；
7. 输出保持原公共索引调色板的图集 PNG；
8. 输出 ImageWorkShop VERSION_4 `.s` 切分描述文件；
9. 输出图块来源、裁剪偏移、图集坐标及数据规模风险报告；
10. ImageWorkShop 的“切分图片”入口已兼容 VERSION_4 `.s`。

## 文件清单

- `PipImageCovert升级计划.md`：两版升级范围、验收标准和生产流程；
- `ImageCovert/src/com/gtalee/covert/SharedPaletteBatchTool.java`：第一版工具源码；
- `ImageCovert/src/com/gtalee/covert/SharedPaletteBatchToolTest.java`：JDK 1.6 回归测试；
- `ImageCovert/src/com/gtalee/covert/AtlasBuildTool.java`：第二版图集与切分文件工具；
- `ImageCovert/src/com/gtalee/covert/AtlasBuildToolTest.java`：第二版回归测试；
- `ImageCovert/bin/com/gtalee/covert/SharedPaletteBatchTool*.class`：已编译运行类；
- `ImageCovert/bin/com/gtalee/covert/SharedPaletteBatchToolTest.class`：已编译测试类。

## 编译验证

使用项目兼容环境：

```text
C:\Program Files\Java\jdk1.6.0_45\bin\javac.exe
-source 1.6
-target 1.6
-encoding GBK
```

两份新增 Java 源码使用工程一致的 GBK/CP936 编码。结果：编译通过。编译器仅提示使用了未检查的旧式集合操作，这是为保持 JDK 1.6 兼容采用的写法，不影响生成 class。

## 自动回归验证

测试程序动态生成两张包含透明区域和大量不同 RGB 颜色的 PNG，然后以32色模式批量转换并检查：

- 成功输出2张图片；
- 实际调色板不超过32色；
- 两张输出均为索引色 PNG；
- 两张输出的调色板长度、颜色内容和顺序完全一致；
- 调色板索引0为完全透明；
- ACT 文件长度为772字节，扩展字段正确记录实际颜色数和透明索引0；
- 调色板预览和处理报告存在；
- 输入图片仍然存在且未被覆盖。

测试结果：

```text
PASS files=2 palette=32
```

JDK 1.6 在当前 Windows 权限下打印了无法创建 Java Preferences 注册表节点的警告，但不影响核心批量转换和测试结果。图形界面保存的最近目录可能因此无法跨次启动记忆。

## 使用方法

### Eclipse 启动

1. 在 Eclipse 中打开或刷新 `PipImageCovert/ImageCovert` 工程；
2. 找到 `src/com/gtalee/covert/ImageCovert.java`；
3. 右键选择 `Run As -> Java Application`；
4. 主窗口顶部可使用“批量统一调色板”和“图集与切分文件”两个按钮。

### 命令行启动

在 `PipImageCovert` 目录执行：

```bat
"C:\Program Files\Java\jdk1.6.0_45\bin\java.exe" -cp ImageCovert\bin com.gtalee.covert.SharedPaletteBatchTool
```

### 第一版操作步骤

1. 把属于同一个 PIP 的全部 PNG 放入同一个输入目录；
2. 确保图片已经完成最终尺寸缩放，转换后不要再单独缩放；
3. 选择输入目录；
4. 选择一个不同的空输出目录；
5. 角色素材第一次建议设置：总颜色数248、Alpha阈值128、关闭抖动；
6. 点击“开始批量转换”；
7. 输出目录根层只保留可导入的素材 PNG；ACT、预览图和报告位于 `palette_info` 子目录；
8. 在 ImageWorkShop 中只导入输出目录根层的素材 PNG，不要导入 `palette_info/shared_palette_preview.png`；
9. 保存 PIP 时选择合并256色模式，并继续检查单图尺寸、图块数量及65535字节限制；
10. PIP、CTS、CTN 配套导出后进行真实客户端验证。

### 第二版操作步骤

1. 点击主窗口“图集与切分文件”；
2. 输入目录选择第一版的输出根目录，不要选择 `palette_info`；
3. 输出目录选择另一个空目录，填写图集名称，例如 `585_0`；
4. 一般选择“紧密排列”、间距1，并勾选透明边界裁剪；规则网格模式下才需要设置列数；
5. 执行后得到同名图集 PNG、`.s` 和报告；
6. 在 ImageWorkShop 中新建空 PIP，载入图集 PNG；
7. 使用“切分图片”选择生成的 `.s`，将图集恢复为多个图块；
8. 选择“合并256色模式、不允许变色”保存；
9. 若报告提示图集 PNG 接近或超过60000字节，或者 ImageWorkShop 仍提示单张图片过大，应拆成多个输入目录并生成多个 PIP；
10. `.s` 只保存切分矩形，不会自动生成 CTS/CTN，也不会自动把裁剪偏移写入动画锚点；需要根据报告中的偏移调整拼装关系。

## 已知边界

- 第一版不会缩放图片；应先用原单图工具或美术软件完成最终缩放；
- 第一版不会自动裁剪透明边缘；裁剪和图集由第二版完成；
- 第一版不会自动识别头、身体、手臂、武器等部件；
- 正常创建 PIP 时不需要先导入 ACT，应先载入索引 PNG，让 PNG 自带的透明调色板初始化 PIP；
- ACT 用于调色板留档或新增换色调色板；修正版会向 ImageWorkShop 声明实际颜色数及透明索引0；
- 统一到256色只能解决调色板兼容问题，不能单独保证合并后的 PNG 数据一定小于65535字节；
- 是否出现画质损失、锚点偏移或客户端绘制异常仍需人工预览和游戏内验证。

## 当前仍需人工验证

- Eclipse 完整构建及两个窗口实际点击；
- ImageWorkShop 导入真实素材图集并使用 `.s` 切分；
- PIP 实际保存是否低于其合并数据限制；
- CTS/CTN 拼装坐标、裁剪偏移和客户端最终显示效果。

## 2026-09-26 帧差补丁生产流程与窗口响应修复

- 已将 `FramePatchComposeTool` 的耗时转换从 Swing EDT 移到 JDK 1.6 兼容的 `SwingWorker` 后台任务。
- 转换期间禁用“开始转换并验证”按钮，显示“正在转换，请稍候...”和不确定进度条，避免重复启动任务。
- 转换期间窗口可以继续重绘、移动和响应操作；后台任务完成后在 EDT 中显示成功或失败提示窗口，并恢复按钮状态。
- `frame_patches.compose.properties` 仍在全部帧读取、差异计算、补丁切分、PIM 容量预检、PNG 输出完成后写入，因此大图片集或复杂帧差导致较晚出现是当前输出顺序下的正常现象，不代表配置文件先行生成。
- 但转换窗口在此前完全阻塞是不正常的 GUI 实现问题，根因是按钮监听器直接在 EDT 调用 `FramePatchComposeBuilder.build(...)`；本次已修复，不再把该阻塞视为正常等待。
- 已保留输出清理、patch 引用一致性、统一调色板、PIM 容量预检、自动分组和重建验证链路；已完成 JDK 1.6 + GBK 编译和帧差补丁回归测试；GUI 实际点击仍需人工交接验证。
## 2026-09-26 修复帧差组装兼容性

- 发现 New_ImageWorkShop 保存 CTS 时提示 `java.io.IOException: unsupported compose format`。
- 根因已确认：`New_ImageWorkShop1.0/src/com/pipimage/data/ComposeDescription.java` 只接受 `format=frame-patch-v1`，而 PipImageCovert 的 `FramePatchComposeBuilder` 曾输出 `format=frame-patch-v3`。
- 已将 PipImageCovert 输出格式标识改回 `frame-patch-v1`。补丁坐标、帧引用和 PIM 分组等扩展属性仍保留；旧版读取器会忽略不认识的扩展属性，因此不影响现有组装逻辑。
- 该问题不是 CTS 路径权限或输出目录问题，而是生产端配置格式版本标识与 New_ImageWorkShop 读取器不一致。
## 2026-09-25 帧差转换进度信息增强

- 已确认配置文件生成前的主要耗时阶段不仅是写入 properties，还包括对所有补丁图块反复执行 PIM 容量模拟，检查图块数量、调色板数量、atlas PNG 的 pdata 长度及各图块 idata 长度是否满足限制。
- 已在 `FramePatchComposeBuilder` 增加进度回调，报告读取帧、分析帧差、输出图块、PIM 容量检查、生成配置索引和写入配置文件等阶段。
- `FramePatchComposeTool` 已将后台进度回调发布到 Swing EDT，进度条旁状态文本会显示例如“正在检查 PIM 容量：xx/xx 个图块”，并显示当前阶段的确定性进度。
- 仍保留后台线程执行，窗口不会因为进度更新重新阻塞。
## 2026-09-25 进度显示、路径记忆与实际PIP容量问题

- 发现之前测试使用临时目录编译，Eclipse/主程序实际加载的 `ImageCovert/bin` 仍可能是旧 class，因此界面只显示“正在转换，请稍候...”。本次已将包含进度回调的源码重新编译到 `PipImageCovert/ImageCovert/bin`。
- 帧差转换窗口已增加阶段进度回调，当前 class 会显示读取帧、分析帧差、输出图块、PIM容量检查和写入配置等信息；PIM检查阶段格式为“正在检查 PIM 容量：xx/xx 个图块”。
- 已将输入目录和输出目录保存到 Java Preferences，关闭并重新打开 PipImageCovert 后会恢复上次路径。
- 已复现并确认“单张图片过大”不是 PipImageCovert 的 65535 预检直接漏报，而是 New_ImageWorkShop 的 `FramePatchCtsBuilder` 当前没有使用 compose.properties 中的 PIM 分组信息，仍把所有唯一图块放入同一个运行时 PIP 分组；最终由 `PipImage.save` 的 atlas `pdata/idata` 检查报错。
- 若要彻底修复该问题，需要修改 `New_ImageWorkShop1.0` 的组装器，使其按 compose 中的 `patch.*.group` / `pim.group.*` 分组并在组边界创建新的 PIP。该跨工程修改需先确认后实施。
## 2026-09-25 New_ImageWorkShop 分组接入修复

- 已按确认方案修改 `New_ImageWorkShop1.0/src/com/pipimage/data/ComposeDescription.java`，读取 `patch.<name>.group`。
- 已修改 `New_ImageWorkShop1.0/src/com/pipimage/data/FramePatchCtsBuilder.java`：组装 patch 时按照 compose.properties 的 group 编号创建不同的运行时 PIP，不再把所有 patch 强制放入同一个 PIP。
- 实测发现仅依赖当前独立模拟器的容量结果仍可能与旧 ImageWorkShop 的真实 atlas 编码存在差异，因此 PipImageCovert 额外采用每组最多 32 个 patch 的保守分组策略；基准图会加入第 0 组。
- 12帧测试样本的 99 个 patch 被拆成 4 个 PIM 组；New_ImageWorkShop 真实 `PipImage.save` 组装测试通过，生成 CTS 文件成功。
- PipImageCovert 和 New_ImageWorkShop 相关源码均已使用 JDK 1.6 编译到实际 `bin` 目录。
## 2026-09-26 进度显示链路修复

- 排查确认之前的 `FramePatchComposeTool` 实际仍调用 `FramePatchComposeBuilder.build(in, out, threshold, gap, minPixels, maxPatch)` 无回调重载，因此构建器内部的 `ProgressListener` 虽然存在，窗口没有任何回调数据可接收；这不是单纯的窗口越界问题。
- 原窗口底部使用同一个 `BorderLayout`，把 `status` 放在 `CENTER`、进度条放在 `WEST`、按钮放在 `EAST`。已改为：状态标签独占 `NORTH`；进度条与按钮放在下方 action 行；进度条固定宽度 500，并启用 `setStringPainted(true)`，避免状态文字被挤压或覆盖。
- `startConversion()` 已改为调用带 `ProgressListener` 的完整构建重载，并将回调通过 `SwingWorker.publish()` 发送到 EDT；`process()` 同步更新状态标签和进度条内部文字。
- 已保持 PipImageCovert 源文件原 GBK/CP936 编码，检查未产生字面量 `` `r`n ``。
- 已使用 JDK 1.6、`-encoding GBK` 编译全部源码到实际运行目录 `PipImageCovert/ImageCovert/bin`，结果 `ACTUAL_BIN_COMPILE=0`。
- 构建器现有进度阶段包括：读取帧、分析帧差、输出图块、检查 PIM 容量 `xx/xx 个图块`、生成配置索引、写入 `frame_patches.compose.properties`。
- 尚未进行用户机器上的实际 GUI 点击验证；下一步应确认启动使用的 classpath 指向 `ImageCovert/bin`，并观察进度条内部文字是否从“正在转换，请稍候...”切换到上述阶段信息。
## 2026-09-26 完善流程建议与生产验证边界

### 当前已实现并通过工具链验证的部分

- PipImageCovert 已完成帧差补丁生成、统一调色板、透明区域处理、递归切块、PIM 容量预检、PIP 分组信息输出、配置文件生成和后台进度显示。
- New_ImageWorkShop1.0 已完成按 `patch.*.group` 组装多个运行时 PIP、CTS 输出链路以及保存错误修复后的编译验证。
- 已通过工具侧逐像素重建验证、PIM 模拟器测试和 CTS 生成测试。

### 仍需作为生产流程补齐的验证

1. 使用真实 Eclipse/SWT 环境启动 New_ImageWorkShop，加载实际帧差配置并保存 CTS。
2. 使用客户端真实资源加载链路验证生成的 CTS/PIP：确认 CTN/CTS 解析、PIP 模式识别、图块坐标、帧顺序、透明色和所有帧显示均正确。
3. 在客户端真实运行条件下测量内存峰值、资源加载时间和缓存占用，不能仅以编译或离线 CTS 文件生成成功作为最终完成依据。
4. 用原版动画和新生成动画做同尺寸、同帧数、同颜色模式的文件大小及内存对比，避免把不同 PIP 模式或不同调色板条件下的数据直接比较。

### 当前流程优化建议

- 先保留当前“安全分组 + 容量预检”作为正确性基线，不要直接删除；优化应以真实 `PipImage.save` 输出和客户端加载结果为验收条件。
- 将“总 PIP 数、每个 PIP 文件大小、每个 atlas 的 pdata/idata 长度、每帧引用数量、重复像素比例”写入构建报告，后续才能定位体积膨胀来自切分、调色板、PNG 编码还是 CTS/PIP 组织方式。
- 将当前固定 `SAFE_PIM_GROUP_SIZE=32` 视为保守兜底，而不是最终最优值；在确认模拟器与真实 `PipImage` 编码一致后，再使用二分/逐步装箱把每组尽量填满 255 图块和 65535 字节限制。
- 帧差算法应先统一比较空间：在同一共享调色板、同一透明规则、同一量化结果上比较帧差；否则颜色量化噪声会把本来相同的区域误判为差异。
- 组装阶段应尽量复用完全相同或可安全复用的图块，但必须保留每个引用的坐标和帧归属；不能只按文件名去重而丢失不同位置的引用。
## 2026-09-26 分组数量优化第一阶段

- 根据当前首要目标，先优化 PIP 分组数量，暂不修改帧差重复度和最终体积算法。
- 已将 `FramePatchComposeBuilder` 中原先“容量检查 + 固定 `SAFE_PIM_GROUP_SIZE=32` 强制封组”的逻辑改为纯容量驱动：只有加入下一个图块后 `PimCapacitySimulator.simulate(...)` 失败时才创建新组。
- 配置索引生成阶段已同步采用相同的容量驱动规则，避免实际 `patch.*.group` 与 `pim.group.*` 不一致。
- 保留 `SAFE_PIM_GROUP_SIZE` 常量作为兼容记录，但不再作为正常分组条件；后续确认真实客户端加载正常后，可再清理无效常量或改为明确的失败保护。
- 使用 JDK 1.6、GBK 编译临时输出并运行 `PimCapacitySimulatorTest`、`FramePatchComposeBuilderTest`：均通过；实际测试样本 99 个图块从原先 4 组变为 1 组，逐像素重建仍为 `diffPixels=0`。
- 已重新编译到实际运行目录 `PipImageCovert/ImageCovert/bin`，结果 `ACTUAL_BIN_COMPILE=0`。
- 当前仍未宣称客户端生产验证完成；下一步应使用该分组结果生成 CTS/PIP，并在客户端确认全部帧、坐标、透明色和内存表现正常。如果客户端测试通过，再继续处理动画总体积和跨帧图块复用问题。
## 2026-09-26 容量分组重复计算优化

- 已修复分组流程中第二次重复调用 `PimCapacitySimulator.simulate(...)` 的问题。
- 第一次容量分组时直接记录 `patch` 到 group 的映射；生成 `patch.*.group` 配置索引时复用该映射，不再重新构造前缀列表并重复进行完整容量模拟。
- `PimCapacitySimulatorTest` 和 `FramePatchComposeBuilderTest` 均通过，逐像素重建仍为 `diffPixels=0`。
- 已重新编译到实际 `PipImageCovert/ImageCovert/bin`，结果 `ACTUAL_BIN_COMPILE=0`。
- 这次只消除了第二轮重复模拟；第一轮仍会对每个候选前缀做完整模拟。后续真实核心移植或增量状态池优化时，再处理第一轮的主要耗时。
## 2026-09-26 图块状态缓存第一阶段

- 已按方案加入 `PimCapacitySimulator.PreparedPatch`：每个图块首次进入容量检查时，将宽高、ARGB 像素数组和颜色集合保存到实例对象中。
- 分组前缀检查使用 `ArrayList<PreparedPatch>` 保存当前组；加入第 N 个图块时复用前 N-1 个已准备对象，只为新图块执行一次准备，不再重新扫描旧 `BufferedImage` 的颜色和像素。
- `simulatePrepared` 已直接使用已缓存的 PreparedPatch 进行颜色、布局和 atlas 像素构造；仍保留整体 atlas PNG 精确压缩检查，避免把不可简单相加的 PNG 容量错误地当作单图容量之和。
- 已保持 PipImageCovert 源文件 GBK/CP936 编码，并使用 JDK 1.6、`-encoding GBK` 编译到实际 `ImageCovert/bin`，结果 `ACTUAL_BIN_COMPILE=0`。
- 该阶段解决的是“旧图块重复读取/颜色扫描/单图索引准备”问题；每个候选前缀仍会重新进行整体布局和 atlas PNG 精确验证，这是为了保持容量判断正确，后续再结合真实 New_ImageWorkShop 核心评估能否继续增量化。
## 2026-09-26 真实 PNG 合并容量预检移植中

已原字节复制 New_ImageWorkShop 的 PngFile/PngTrunk 与 jzlib 源码到 PipImageCovert，新增 WorkshopPimValidator，按组重建 atlas 并调用真实 PngFile.writePngSpecial(bestCompress=true) 检查 pdata/idata 65535 限制。布局仍调用 PipLayout（现已修复浅拷贝以保留原矩形坐标），尚需与 SWTUtils.getBestLayout 做差分确认；另需用实际 New_ImageWorkShop 输出进行逐组容量及组数对照，不能宣称完全等价。四帧/99 图块重建测试通过，客户端仍待测。

### 本轮实测

- JDK 1.6 -encoding GBK 编译通过；FramePatchComposeBuilderTest 四帧/99 图块逐像素重建通过（diffPixels=0）。
- 使用真实 New_ImageWorkShop PNG 特殊编码器后，该样本分为 2 组，首组 67 图块，pdata=1048、maxIdata=56068；与旧模拟器预测的 1 组不同，说明旧模拟器确实不能作为最终裁决。
- 编码器虽然一致，仍未直接运行 New_ImageWorkShop 的 PipImage.save 作端到端产物对照；客户端加载亦未验证。此状态不能称作最终生产闭环。

## 2026-09-26 合并模式真实产物对照完成

- 已完成 WorkshopPimValidator 与真实 PipImage.save() 的逐组字节特征对照：pdata 长度/CRC、各 atlas 的 idata CRC、atlas 数量均一致。
- 已修复容量预检与生产 CTN 组装的两个关键一致性问题：第 0 组必须包含 base.png 分块；预检必须保持与 PipImage.addFrame 相同的透明索引复用规则。
- 四帧/99 个补丁样本已完成：帧差重建 diffPixels=0；真实 CTN 构建通过；CTS/PIP 回读并逐补丁匹配通过（99/99）。
- 该样本最终分为 2 个 PIP 组：第 0 组 34 个补丁加 base 分块，共 38 个图像帧；第 1 组 65 个补丁。实际 PIP 与预检的 pdata/idata 特征一致。
- PipImageCovert 全部 Java 源码使用 JDK 1.6、-encoding GBK 全量编译通过。
- 客户端真实加载/播放仍未执行，因此尚未交接客户端测试；当前已达到可进行客户端测试的工具链交接条件。

## 2026-09-26 真实 PIM 保存格式分组与回归

- `FramePatchComposeBuilder` 的生产分组不再调用旧版 `PimCapacitySimulator`；旧模拟器当前仅由独立测试类引用，未进入生产转换路径。
- 分组边界采用指数探测与二分定位，避免对每个 `1..N` 前缀逐次完整压缩；每次候选判断都会重建完整 PIM 保存字节流，包括 `PIM` 头、调色板、帧位置压缩数据、atlas `pdata` 和各 `idata`，并按 `PipImage.save(DataOutputStream,true)` 的字段顺序执行 65535 限制检查。
- 已修正并核对真实格式细节：合并模式允许最多 256 色；调色板保存后直接写帧信息长度，不额外写重复帧数；`pdata` 只写一次，随后写各 atlas 的 `idata`。
- 四帧／99 图块回归：四帧重建 `diffPixels=0`；GBK/JDK 1.6 编译通过；真实 `New_ImageWorkShop1.0 FramePatchCtsBuilder` 生成 CTS 701 bytes；配置中的 3 个 PIM 分组实际生成 3 个 PIP（112516、26556、130 bytes），CTS 构建通过。
- 修改文件已完成 CP936/GBK 解码和 CRLF 字节审计，无 UTF-8 BOM、无裸 LF。
- 仍未完成客户端真实加载/播放验证；本项工具链验证通过后才进入客户端测试交接。


## 2026-09-26 性能优化阶段
- 针对 303 图块/36 帧处理约 12 分钟的问题，确认主要耗时仍在 WorkshopPimValidator 的候选组完整 PIM/PNG 验证。
- FramePatchComposeBuilder 已缓存 Piece 的透明裁剪结果，避免重复 trimPip。
- 已缓存同一分组起点与候选数量的验证结果，并复用最终边界验证结果，取消 writeGroup 的无条件二次完整验证。
- JDK 1.6、-encoding GBK 全量编译通过；FramePatchComposeBuilder 保持 GBK/CP936、CRLF、无 BOM，无字面量转义换行。
- 尚未宣称性能达标：需要使用相同 36 帧/303 图块样本重新测量；如仍偏慢，下一阶段优化验证器的增量 atlas/快速预检。

- 后续进一步复用 WorkshopPimValidator 的最终 PIM 内存写出：容量检查与结果字段（pdata/idata 长度及 CRC）在同一次写出中完成，避免先编码统计、再重复编码统计。
- 4 帧样本回归：JDK 1.6 GBK 编译通过，重建 diffPixels=0、99/99 patch 引用通过；该样本耗时受完整 PIM 验证影响，不能代表 303 图块最终基准。

- 2026-09-26：继续排查 PNG 压缩热点。PngFile 的原始扫描线已缓存为 byte[]，避免每个 zlib window 重复生成 rawData 和候选结果的多次 byte[] 拷贝；JDK 1.6 GBK 编译通过，4 帧/99 图块重建 diffPixels=0。
- 该样本本轮耗时受机器当前负载及完整验证影响，未能证明 303 图块性能已达标；尚未进入测试交接。

- 2026-09-26：新增容量探测快速路径：候选组先使用同一 PIM/atlas 构造但单次 zlib 压缩；快速结果通过时可证明最佳压缩也不会超限。快速压缩失败时已回退完整最佳压缩，避免因压缩级别差异导致错误分组。最终写组仍使用完整最佳压缩。
- 该优化已通过 JDK 1.6 GBK 全量编译及编码审计；尚需在同一 36 帧/303 图块样本上基准并核对分组数量与真实 New_ImageWorkShop 输出。

- 2026-09-26：容量验证器新增共享调色板入口，候选组验证直接复用帧差生产阶段已建立的统一调色板，不再为每次候选范围重新建立颜色哈希/调色板；atlas 布局和真实 PNG/PIM 压缩仍逐次执行，保持验证语义不变。
- JDK 1.6 GBK 全量编译及 4 帧/99 图块 diffPixels=0 回归通过；同一小样本耗时仍受机器负载和完整压缩验证影响，303 图块基准尚未完成。


## 2026-09-26 性能优化继续
- 对共享调色板验证增加按图块实例缓存，避免候选容量验证重复执行同一图块的颜色索引转换。
- 分组探测仍保留真实 WorkshopPimValidator 验证；未关闭 pdata/idata、布局、调色板和 65535 限制检查。
- 生产分组继续使用指数探测与二分边界，避免对每个新增图块从头执行全部探测。
- JDK 1.6、GBK 编译通过；36帧实测仍需完整跑完后再确定交接。

- 2026-09-26：4帧回归样本在当前缓存实现下完成重建验证，diffPixels=0、99图块，JDK6实测约36.9秒；36帧303图块仍未完成可接受基准，不能交接。

## 2026-09-26 36帧／303图块性能优化完成

- 将容量边界搜索改为“两阶段验证”：候选前缀使用同一调色板、同一 atlas 像素构造、同一 PIM/PNG 字段及 65535 限制，但 atlas 布局仅探测少量确定性候选宽度；每个最终分组仍执行与 New_ImageWorkShop 对齐的完整最佳布局和最佳压缩验证。
- 快速探测只用于寻找候选边界，不作为最终通过依据；如果快速探测给出的边界未通过完整验证，会对该范围执行完整验证二分回退，直到取得真实可保存边界，因此没有为速度关闭容量验证。
- 使用工作区 `36帧测试图` 连续两次独立运行：分别约 18.61 秒和 18.84 秒；最终版本再次运行约 18.79 秒。三次均生成 303 个图块，36 帧逐像素重组均为 `diffPixels=0`。
- 输出稳定为 4 个 PIM 组，图块数为 65、92、88、58；完整验证记录的 `maxIdata` 分别为 53035、34863、56966、34236，全部小于 65535；`pdata` 均为 1016。
- 两次输出的有效 properties 键值完全一致；303 个 PNG 全部存在，303 个 `patch.*.group` 引用均存在且组号有效。
- 已使用 New_ImageWorkShop 的 `FramePatchCtsBuilder` 合并模式实际读取该配置并生成 CTS/PIP：CTS 1474 bytes，4 个 PIP 均成功生成；随后使用 New_ImageWorkShop 的 `PipImage.load` 和 `PipAnimateSet.load` 回读成功，共 304 个 PIP 图像帧（base 1 + patch 303）。
- PipImageCovert 全量源码已用 JDK 1.6、`-encoding GBK` 编译通过。修改的 `PimLayout.java`、`WorkshopPimValidator.java`、`FramePatchComposeBuilder.java` 均通过 CP936 往返校验，保持 CRLF、无 BOM、无裸 LF。
- 相比此前同素材约 12 分钟基线，转换测试已降低至约 19 秒。工具链已达到客户端测试交接条件；客户端实际加载和播放仍需由用户执行，不能在工具侧冒充已验证。