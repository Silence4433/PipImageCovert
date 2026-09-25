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