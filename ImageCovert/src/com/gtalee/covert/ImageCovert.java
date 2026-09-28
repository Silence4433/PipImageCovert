package com.gtalee.covert;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowStateListener;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.WritableRaster;

/**
 * 颜色减少工具 - 兼容 JDK 1.6，输出索引色图像
 * 已支持处理透明图像
 */
public class ImageCovert extends JFrame {

    private static final String PREF_OPEN_DIRECTORY = "openDirectory";
    private static final String PREF_SAVE_DIRECTORY = "saveDirectory";

    private JLabel imageLabel;
    private JButton openButton, saveButton, processButton, sharedPaletteButton, atlasButton, framePatchButton, usageButton;
    private JComboBox colorCountCombo;
    private BufferedImage originalImage;
    private BufferedImage processedImage;

    private JButton scaleButton;	
    private JTextField widthField;
    private JTextField heightField;
    private JCheckBox keepRatioCheck;
    private JCheckBox applyToAllCheck;
    private File currentImageFile;
    private BufferedImage currentImage;  // 新增：当前工作图片
    // 多选打开后的全部输入文件；处理按钮会逐张转换并输出。
    private File[] selectedImageFiles = new File[0];
    private final Map processedImageFiles = new LinkedHashMap();
    private final Map loadedImages = new LinkedHashMap();
    private final Map thumbnailLabels = new LinkedHashMap();
    private JPanel thumbnailPanel;
    private JScrollPane imageScrollPane;
    private double imageZoom = 1.0;
    private Point dragStart;
    private JLabel zoomInfoLabel;
    
    public ImageCovert() {
        setTitle("ImageCovert(图像处理工具) @gtalee");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        openButton = new JButton("打开图片");
        saveButton = new JButton("保存结果");
        processButton = new JButton("处理");
        scaleButton = new JButton("缩放像素");
        sharedPaletteButton = new JButton("批量统一调色板");
        atlasButton = new JButton("图集与切分文件");
        framePatchButton = new JButton("帧差补丁转换");
        usageButton = new JButton("使用建议");

        // 尺寸和缩放属于同一组功能：按钮放在尺寸输入框左侧。
        widthField = new JTextField(5);
        heightField = new JTextField(5);
        keepRatioCheck = new JCheckBox("保持比例", true);
        topPanel.add(scaleButton);
        topPanel.add(widthField);
        topPanel.add(new JLabel(" x "));
        topPanel.add(heightField);

        // 颜色处理控件位于左侧；复选框统一放到右侧。
        applyToAllCheck = new JCheckBox("应用到全部已打开图片", true);

        // 颜色数输入框放在左侧，说明标签位于中间，处理按钮在最左。
        colorCountCombo = new JComboBox();
        colorCountCombo.addItem(new Integer(16));
        colorCountCombo.addItem(new Integer(32));
        colorCountCombo.addItem(new Integer(48));
        colorCountCombo.addItem(new Integer(64));
        colorCountCombo.addItem(new Integer(96));
        colorCountCombo.addItem(new Integer(128));
        colorCountCombo.addItem(new Integer(144));
        colorCountCombo.addItem(new Integer(160));
        colorCountCombo.addItem(new Integer(176));
        colorCountCombo.addItem(new Integer(192));
        colorCountCombo.addItem(new Integer(208));
        colorCountCombo.addItem(new Integer(224));
        colorCountCombo.addItem(new Integer(232));
        colorCountCombo.addItem(new Integer(240));
        colorCountCombo.addItem(new Integer(248));
        colorCountCombo.addItem(new Integer(250));
        colorCountCombo.addItem(new Integer(252));
        colorCountCombo.addItem(new Integer(254));
        // 颜色数区域保留在顶部：处理按钮在左，说明标签居中，颜色数选择框在右。
        topPanel.add(processButton);
        topPanel.add(new JLabel("指定颜色的数量(减色)"));
        topPanel.add(colorCountCombo);
        topPanel.add(keepRatioCheck);
        topPanel.add(applyToAllCheck);
        add(topPanel, BorderLayout.NORTH);

        // 除处理和缩放像素外，其余操作按钮统一放在右侧纵向排列。
        JPanel rightButtonPanel = new JPanel();
        rightButtonPanel.setLayout(new BoxLayout(rightButtonPanel, BoxLayout.Y_AXIS));
        addRightButton(rightButtonPanel, openButton);
        addRightButton(rightButtonPanel, saveButton);
        addRightButton(rightButtonPanel, sharedPaletteButton);
        // 【已弃用】图集与切分文件功能入口已停用，不加入界面。
        addRightButton(rightButtonPanel, framePatchButton);
        addRightButton(rightButtonPanel, usageButton);
        add(rightButtonPanel, BorderLayout.EAST);
        // 左侧显示本次选择的全部图片缩略图；避免打开图片时只保留第一张。
        thumbnailPanel = new JPanel();
        thumbnailPanel.setLayout(new BoxLayout(thumbnailPanel, BoxLayout.Y_AXIS));
        JScrollPane thumbnailScrollPane = new JScrollPane(thumbnailPanel);
        thumbnailScrollPane.setPreferredSize(new Dimension(190, 500));
        thumbnailScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        thumbnailScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(thumbnailScrollPane, BorderLayout.WEST);
        imageLabel = new JLabel("请打开一张图片", JLabel.CENTER);
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        imageLabel.setPreferredSize(new Dimension(640, 480));
        imageScrollPane = new JScrollPane(imageLabel);
        imageScrollPane.setWheelScrollingEnabled(false);
        installImageViewerHandlers();
        JPanel viewerPanel = new JPanel(new BorderLayout());
        viewerPanel.add(imageScrollPane, BorderLayout.CENTER);
        JPanel zoomInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        zoomInfoLabel = new JLabel("缩放: 100%");
        zoomInfoLabel.setOpaque(true);
        zoomInfoLabel.setBackground(new Color(255, 255, 225));
        zoomInfoLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        zoomInfoPanel.add(zoomInfoLabel);
        viewerPanel.add(zoomInfoPanel, BorderLayout.SOUTH);
        add(viewerPanel, BorderLayout.CENTER);
        
        
        final String usageText =
            "【推荐操作流程】\n" +
            "第一步：使用“打开图片”选择全部序列帧。建议缩放为180 x 180像素，接近游戏内人物显示大小，避免尺寸过大造成比例失调。然后点击“缩放像素”。\n\n" +
            "第二步：缩放完成后选择颜色数，建议选择128色，然后点击“处理”。\n\n" +
            "第三步：点击“保存结果”，选择指定目录保存处理后的图片集。\n\n" +
            "第四步：点击“批量统一调色板”，输入第三步保存的图片目录，选择任意输出目录，其余参数保持默认，点击“开始批量转换”。\n\n" +
            "第五步：点击“帧差补丁转换”，输入第四步生成的图集目录，输出选择任意目录，参数保持默认，点击“开始转换并验证”。\n\n" +
            "完成第五步后，可在 New_ImageWorkShop1.0（美术工具）中使用“帧差组装”功能，组装为动画文件。\n\n" +
            "流程说明：第一步至第三步用于把原始图片集处理为统一尺寸、统一颜色格式的图片，供后续步骤使用。第四步用于统一图片调色板和颜色数量，使其符合PIP合并模式要求，并生成调色板文件；调色板文件当前暂不参与后续导入。第五步会按照约定算法把图片分割为图块、去重并去除透明边界，以节省内存；美术工具负责后续动画组装。最终可得到PIP图片和CTS动画文件。";
                usageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTextArea usageArea = new JTextArea(usageText);
                usageArea.setEditable(false);
                usageArea.setLineWrap(true);
                usageArea.setWrapStyleWord(true);
                usageArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
                usageArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                JScrollPane usageScrollPane = new JScrollPane(usageArea);
                usageScrollPane.setPreferredSize(new Dimension(720, 520));
                JOptionPane.showMessageDialog(ImageCovert.this, usageScrollPane, "使用建议", JOptionPane.INFORMATION_MESSAGE);
            }
        });        openButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openImage();
            }
        });
        processButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                processImage();
            }
        });
        scaleButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                scaleOnly();
            }
        });
        saveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveImage();
            }
        });
        sharedPaletteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new SharedPaletteBatchTool().setVisible(true);
            }
        });
        framePatchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new FramePatchComposeTool().setVisible(true);
            }
        });

        // 【已弃用】图集与切分文件入口已注释，不再打开 AtlasBuildTool。

        pack();
        
        setSize(1150, 790);// 增大窗口高度，完整显示原说明和新增功能说明
        
        setLocationRelativeTo(null);
    }

    private void addRightButton(JPanel panel, JButton button) {
        // 右侧按钮统一尺寸，并增加按钮之间的垂直间距。
        Dimension buttonSize = new Dimension(150, 30);
        button.setPreferredSize(buttonSize);
        button.setMinimumSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(button);
        panel.add(javax.swing.Box.createRigidArea(new Dimension(0, 8)));
    }

    private void openImage() {
        File[] files = chooseImageFilesByDrag();
        if (files == null || files.length == 0) return;
        selectedImageFiles = files;
        rememberDirectory(PREF_OPEN_DIRECTORY, selectedImageFiles[0]);
        if (loadedImages == null || processedImageFiles == null || thumbnailPanel == null) {
            JOptionPane.showMessageDialog(this, "图片查看区域尚未初始化，请重新启动工具");
            return;
        }
        loadedImages.clear(); processedImageFiles.clear(); thumbnailPanel.removeAll();
        StringBuffer errors = new StringBuffer();
        for (int i = 0; i < selectedImageFiles.length; i++) {
            try {
                BufferedImage image = ImageIO.read(selectedImageFiles[i]);
                if (image == null) throw new IOException("无法读取图片格式");
                loadedImages.put(selectedImageFiles[i], image); addThumbnail(selectedImageFiles[i], image);
            } catch (IOException ex) { errors.append(selectedImageFiles[i].getName()).append(": ").append(ex.getMessage()).append("\n"); }
        }
        thumbnailPanel.revalidate(); thumbnailPanel.repaint();
        if (!loadedImages.isEmpty()) { File first = (File) loadedImages.keySet().iterator().next(); selectImage(first); saveButton.setEnabled(false); setTitle("ImageCovert(图像处理工具) @gtalee - 已打开 " + loadedImages.size() + " 张图片"); }
        if (errors.length() > 0) JOptionPane.showMessageDialog(this, "部分图片读取失败：\n" + errors.toString());
    }

    /** 使用JList的多区间选择，支持鼠标左键拖拽框选文件，并可切换任意目录。 */
    private File[] chooseImageFilesByDrag() {
        File initial = new File(AppPreferences.get(PREF_OPEN_DIRECTORY, System.getProperty("user.home", ".")));
        if (!initial.isDirectory()) initial = new File(System.getProperty("user.home", "."));
        final JDialog dialog = new JDialog(this, "选择图片（鼠标左键拖拽框选，可多选）", true);
        final JTextField directoryField = new JTextField(initial.getAbsolutePath());
        final JList list = new JList();
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL); list.setVisibleRowCount(18);
        list.setFixedCellHeight(24);
        final DefaultListModel model = new DefaultListModel(); list.setModel(model);
        installRubberSelection(list);
        final File[][] selectedHolder = new File[1][]; final boolean[] cancelled = new boolean[] { true };
        final Runnable refresh = new Runnable() { public void run() {
            model.clear(); File dir = new File(directoryField.getText().trim()); if (!dir.isDirectory()) return;
            File[] images = dir.listFiles(new FilenameFilter() { public boolean accept(File d, String name) {
                String n=name.toLowerCase(); return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".bmp")||n.endsWith(".gif"); }});
            if (images == null) return; java.util.Arrays.sort(images, new Comparator() { public int compare(Object a,Object b) { return ((File)a).getName().compareToIgnoreCase(((File)b).getName()); }});
            for (int i=0;i<images.length;i++) model.addElement(images[i]);
        }};
        JButton chooseDirectory = new JButton("选择目录..."); JButton refreshButton = new JButton("刷新");
        chooseDirectory.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = new JFileChooser(new File(directoryField.getText().trim())); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog)==JFileChooser.APPROVE_OPTION) { directoryField.setText(chooser.getSelectedFile().getAbsolutePath()); refresh.run(); }
        }});
        refreshButton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { refresh.run(); }});
        JPanel top = new JPanel(new BorderLayout(4,4)); top.add(refreshButton,BorderLayout.WEST); top.add(directoryField,BorderLayout.CENTER); top.add(chooseDirectory,BorderLayout.EAST);
        JButton ok = new JButton("打开"); JButton cancel = new JButton("取消");
        ok.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
            Object[] selected=list.getSelectedValues(); if (selected==null||selected.length==0) { JOptionPane.showMessageDialog(dialog,"请至少选择一张图片"); return; }
            File[] result=new File[selected.length]; for(int i=0;i<selected.length;i++) result[i]=(File)selected[i]; selectedHolder[0]=result; cancelled[0]=false;
            AppPreferences.put(PREF_OPEN_DIRECTORY,new File(directoryField.getText().trim()).getAbsolutePath()); dialog.dispose();
        }});
        cancel.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { dialog.dispose(); }});
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(ok); buttons.add(cancel);
        dialog.setLayout(new BorderLayout(6,6)); dialog.add(top,BorderLayout.NORTH); dialog.add(new JScrollPane(list),BorderLayout.CENTER); dialog.add(buttons,BorderLayout.SOUTH);
        refresh.run(); dialog.setSize(760,560); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
        return cancelled[0] ? null : selectedHolder[0];
    }

    /**
     * JList默认只在拖过已有单元格时提供连续选择，且没有明确的框选反馈。
     * 这里补充无Ctrl/Shift时的鼠标左键拖拽选择；Ctrl/Shift仍交给JList原生逻辑处理。
     */
    private void installRubberSelection(final JList list) {
        final int[] anchor = new int[] { -1 };
        final boolean[] rubberSelecting = new boolean[] { false };
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                int modifiers = e.getModifiersEx();
                boolean withCtrlOrShift = (modifiers & (java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK)) != 0;
                anchor[0] = list.locationToIndex(e.getPoint());
                rubberSelecting[0] = e.getButton() == java.awt.event.MouseEvent.BUTTON1 && !withCtrlOrShift && anchor[0] >= 0;
                if (rubberSelecting[0]) {
                    list.setSelectedIndex(anchor[0]);
                    e.consume();
                }
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                rubberSelecting[0] = false;
                anchor[0] = -1;
            }
        });
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (!rubberSelecting[0] || anchor[0] < 0) return;
                int current = list.locationToIndex(e.getPoint());
                if (current < 0) return;
                int first = Math.min(anchor[0], current);
                int last = Math.max(anchor[0], current);
                list.setSelectionInterval(first, last);
                e.consume();
            }
        });
    }

    private void addThumbnail(final File file, BufferedImage image) {
        int maxWidth=140, maxHeight=100;
        double ratio=Math.min((double)maxWidth/image.getWidth(),(double)maxHeight/image.getHeight());
        int width=Math.max(1,(int)Math.round(image.getWidth()*ratio));
        int height=Math.max(1,(int)Math.round(image.getHeight()*ratio));
        final JLabel thumb=new JLabel(new ImageIcon(image.getScaledInstance(width,height,Image.SCALE_SMOOTH)));
        thumb.setToolTipText(file.getAbsolutePath());
        thumb.setBorder(BorderFactory.createLineBorder(Color.GRAY,1));
        thumb.addMouseListener(new java.awt.event.MouseAdapter(){public void mouseClicked(java.awt.event.MouseEvent e){selectImage(file);}});
        thumb.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel nameLabel=new JLabel(file.getName(),JLabel.CENTER);
        nameLabel.setToolTipText(file.getAbsolutePath()); nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel item=new JPanel(); item.setLayout(new BoxLayout(item,BoxLayout.Y_AXIS)); item.setAlignmentX(Component.CENTER_ALIGNMENT);
        item.add(thumb); item.add(nameLabel); thumbnailLabels.put(file,thumb); thumbnailPanel.add(item);
    }

    private void selectImage(File file) {
        BufferedImage image = (BufferedImage) loadedImages.get(file);
        if (image == null) return;
        currentImageFile = file;
        originalImage = image; currentImage = image; processedImage = (BufferedImage) processedImageFiles.get(file);
        if (processedImage != null) currentImage = processedImage;
        imageZoom = 1.0; showImage(currentImage); updateThumbnailFocus();
    }

    private void updateThumbnailFocus() {
        for (Object key : thumbnailLabels.keySet()) {
            JLabel label = (JLabel)thumbnailLabels.get(key);
            label.setBorder(BorderFactory.createLineBorder(key.equals(currentImageFile) ? new Color(30, 120, 255) : Color.GRAY, key.equals(currentImageFile) ? 3 : 1));
        }
        thumbnailPanel.revalidate(); thumbnailPanel.repaint();
    }

    private void processImage() {
        if (loadedImages.isEmpty()) { JOptionPane.showMessageDialog(this, "请先打开图片"); return; }
        int targetColors=((Integer)colorCountCombo.getSelectedItem()).intValue(); List targets=new ArrayList();
        if (applyToAllCheck.isSelected()) targets.addAll(loadedImages.keySet());
        else if (currentImageFile!=null && loadedImages.containsKey(currentImageFile)) targets.add(currentImageFile);
        if (targets.isEmpty()) { JOptionPane.showMessageDialog(this,"请先选择当前图片"); return; }
        int success=0; StringBuffer errors=new StringBuffer();
        for(int i=0;i<targets.size();i++){ File input=(File)targets.get(i); try{ processedImageFiles.put(input,medianCutQuantize((BufferedImage)loadedImages.get(input),targetColors)); success++; }catch(RuntimeException ex){ errors.append(input.getName()).append(": ").append(ex.getMessage()).append("\n"); }}
        if(currentImageFile!=null) selectImage(currentImageFile); saveButton.setEnabled(!processedImageFiles.isEmpty());
        String message="已处理 "+success+" / "+targets.size()+" 张图片。"; if(errors.length()>0) message+="\n失败明细：\n"+errors.toString(); JOptionPane.showMessageDialog(this,message);
    }

    private void saveImage() {
        if (processedImageFiles.isEmpty()) { JOptionPane.showMessageDialog(this, "没有可保存的处理结果"); return; }
        JFileChooser chooser = createFileChooser(PREF_SAVE_DIRECTORY); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择处理结果保存目录");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File outputDirectory = chooser.getSelectedFile(); AppPreferences.put(PREF_SAVE_DIRECTORY, outputDirectory.getAbsolutePath());
        int success = 0;
        try { for (Object key : processedImageFiles.keySet()) {
            File input = (File) key; BufferedImage image = (BufferedImage) processedImageFiles.get(key); String name = input.getName(); int dot = name.lastIndexOf('.'); if (dot > 0) name = name.substring(0, dot);
            ImageIO.write(image, "png", new File(outputDirectory, name + "_processed.png")); success++;
        }} catch (IOException ex) { JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage()); return; }
        JOptionPane.showMessageDialog(this, "已保存 " + success + " 张图片");
    }

    private void installImageViewerHandlers() {
        imageLabel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (currentImage == null) return;
                JViewport viewport = imageScrollPane.getViewport();
                Point mouseInViewport = SwingUtilities.convertPoint(imageLabel, e.getPoint(), viewport);
                Point oldView = viewport.getViewPosition();
                double oldZoom = imageZoom;
                double imageX = (oldView.x + mouseInViewport.x) / oldZoom;
                double imageY = (oldView.y + mouseInViewport.y) / oldZoom;
                imageZoom *= e.getWheelRotation() < 0 ? 1.15 : 0.87;
                if (imageZoom < 0.1) imageZoom = 0.1;
                if (imageZoom > 8.0) imageZoom = 8.0;
                showImage(currentImage);
                Point newView = new Point(
                    (int)Math.round(imageX * imageZoom - mouseInViewport.x),
                    (int)Math.round(imageY * imageZoom - mouseInViewport.y));
                setViewportPosition(newView);
                e.consume();
            }
        });
        imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                boolean rightButton = e.getButton() == java.awt.event.MouseEvent.BUTTON3;
                boolean shiftLeft = e.getButton() == java.awt.event.MouseEvent.BUTTON1 &&
                    (e.getModifiersEx() & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0;
                if (rightButton || shiftLeft) {
                    dragStart = SwingUtilities.convertPoint(imageLabel, e.getPoint(), imageScrollPane.getViewport());
                    e.consume();
                }
            }
            public void mouseReleased(java.awt.event.MouseEvent e) { dragStart = null; }
        });
        imageLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (dragStart == null) return;
                Point now = SwingUtilities.convertPoint(imageLabel, e.getPoint(), imageScrollPane.getViewport());
                int dx = dragStart.x - now.x;
                int dy = dragStart.y - now.y;
                Point position = imageScrollPane.getViewport().getViewPosition();
                setViewportPosition(new Point(position.x + dx, position.y + dy));
                dragStart = now;
                e.consume();
            }
        });
    }

    private void setViewportPosition(Point position) {
        if (imageScrollPane == null || imageLabel == null) return;
        JViewport viewport = imageScrollPane.getViewport();
        Dimension viewSize = imageLabel.getPreferredSize();
        Dimension extent = viewport.getExtentSize();
        int maxX = Math.max(0, viewSize.width - extent.width);
        int maxY = Math.max(0, viewSize.height - extent.height);
        position.x = Math.max(0, Math.min(position.x, maxX));
        position.y = Math.max(0, Math.min(position.y, maxY));
        viewport.setViewPosition(position);
    }

    private JFileChooser createFileChooser(String preferenceKey) {
        String directoryPath = AppPreferences.get(preferenceKey, null);
        if (directoryPath != null) {
            File directory = new File(directoryPath);
            if (directory.isDirectory()) {
                return new JFileChooser(directory);
            }
        }
        return new JFileChooser();
    }

    private void rememberDirectory(String preferenceKey, File file) {
        File directory = file.getParentFile();
        if (directory != null && directory.isDirectory()) {
            AppPreferences.put(preferenceKey, directory.getAbsolutePath());
        }
    }

    private void showImage(BufferedImage img) {
        // 如果图像是索引色，先转为真彩色再缩放显示
        if (img == null) return;
        BufferedImage displayImg = img;
        if (img.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
        	displayImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = displayImg.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }

        int width = Math.max(1, (int)Math.round(displayImg.getWidth() * imageZoom));
        int height = Math.max(1, (int)Math.round(displayImg.getHeight() * imageZoom));
        ImageIcon icon = new ImageIcon(displayImg.getScaledInstance(width, height, Image.SCALE_SMOOTH));
        Dimension extent = imageScrollPane.getViewport().getExtentSize();
        imageLabel.setPreferredSize(new Dimension(Math.max(width, extent.width), Math.max(height, extent.height)));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        imageLabel.setIcon(icon);
        imageLabel.setText("");
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        if (zoomInfoLabel != null) zoomInfoLabel.setText("缩放: " + (int)Math.round(imageZoom * 100.0) + "%");
        imageLabel.revalidate();
        imageLabel.repaint();
        imageScrollPane.revalidate();
        imageScrollPane.repaint();
    }

    // ---------- 中位切分算法核心 ----------
    private static class Pixel {
        int r, g, b;
        Pixel(int rgb) {
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8) & 0xFF;
            b = rgb & 0xFF;
        }
        int getRGB() {
            return (r << 16) | (g << 8) | b;
        }
    }

    private static class Box {
        List pixels;
        int rMin, rMax, gMin, gMax, bMin, bMax;

        Box(List pixels) {
            this.pixels = pixels;
            computeRange();
        }

        void computeRange() {
            rMin = 255; rMax = 0;
            gMin = 255; gMax = 0;
            bMin = 255; bMax = 0;
            for (int i = 0; i < pixels.size(); i++) {
                Pixel p = (Pixel) pixels.get(i);
                if (p.r < rMin) rMin = p.r;
                if (p.r > rMax) rMax = p.r;
                if (p.g < gMin) gMin = p.g;
                if (p.g > gMax) gMax = p.g;
                if (p.b < bMin) bMin = p.b;
                if (p.b > bMax) bMax = p.b;
            }
        }

        int getVolume() {
            return (rMax - rMin + 1) * (gMax - gMin + 1) * (bMax - bMin + 1);
        }

        int getLongestChannel() {
            int rLen = rMax - rMin;
            int gLen = gMax - gMin;
            int bLen = bMax - bMin;
            if (rLen >= gLen && rLen >= bLen) return 0;
            if (gLen >= rLen && gLen >= bLen) return 1;
            return 2;
        }
    }

    private BufferedImage medianCutQuantize(BufferedImage src, int targetColors) {
        int w = src.getWidth();
        int h = src.getHeight();

        // 1. 收集所有不透明像素（忽略透明像素，透明像素保持透明）
        List opaquePixels = new ArrayList();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha >= 128) { // 不透明或半透明视为不透明
                    opaquePixels.add(new Pixel(argb));
                }
            }
        }

        // 如果没有不透明像素，直接返回原图的透明副本
        if (opaquePixels.size() == 0) {
            BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            return result;
        }

        // 2. 中位切分量化（仅对不透明像素），预留一个透明色
        int effectiveTarget = targetColors - 1; // 实际不透明颜色数
        if (effectiveTarget < 1) effectiveTarget = 1;

        List boxes = new ArrayList();
        Box initialBox = new Box(opaquePixels);
        boxes.add(initialBox);
        while (boxes.size() < effectiveTarget) {
            Box bestBox = null;
            double bestScore = -1;
            for (int i = 0; i < boxes.size(); i++) {
                Box box = (Box) boxes.get(i);
                if (box.pixels.size() > 1) {
                    double score = box.pixels.size() * (double) box.getVolume();
                    if (score > bestScore) {
                        bestScore = score;
                        bestBox = box;
                    }
                }
            }
            if (bestBox == null) break;

            final int channel = bestBox.getLongestChannel();
            Collections.sort(bestBox.pixels, new Comparator() {
                public int compare(Object a, Object b) {
                    Pixel pa = (Pixel) a;
                    Pixel pb = (Pixel) b;
                    int va = (channel == 0) ? pa.r : (channel == 1) ? pa.g : pa.b;
                    int vb = (channel == 0) ? pb.r : (channel == 1) ? pb.g : pb.b;
                    return va - vb;
                }
            });
            int mid = bestBox.pixels.size() / 2;
            List leftPixels = new ArrayList(bestBox.pixels.subList(0, mid));
            List rightPixels = new ArrayList(bestBox.pixels.subList(mid, bestBox.pixels.size()));
            boxes.remove(bestBox);
            boxes.add(new Box(leftPixels));
            boxes.add(new Box(rightPixels));
        }

        // 3. 计算调色板（不透明颜色）
        int paletteSize = boxes.size();
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            Box box = (Box) boxes.get(i);
            long sumR = 0, sumG = 0, sumB = 0;
            for (int j = 0; j < box.pixels.size(); j++) {
                Pixel p = (Pixel) box.pixels.get(j);
                sumR += p.r;
                sumG += p.g;
                sumB += p.b;
            }
            int avgR = (int)(sumR / box.pixels.size());
            int avgG = (int)(sumG / box.pixels.size());
            int avgB = (int)(sumB / box.pixels.size());
            palette[i] = 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;
        }

        // 4. 创建 ARGB 输出图像，并应用 Floyd-Steinberg 抖动
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[][] errR = new float[h][w];
        float[][] errG = new float[h][w];
        float[][] errB = new float[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha < 128) {
                    // 透明像素直接设为透明
                    result.setRGB(x, y, 0x00000000);
                    continue;
                }
                // 不透明像素：加上累积误差
                float r = ((argb >> 16) & 0xFF) + errR[y][x];
                float g = ((argb >> 8) & 0xFF) + errG[y][x];
                float b = (argb & 0xFF) + errB[y][x];
                if (r < 0) r = 0; if (r > 255) r = 255;
                if (g < 0) g = 0; if (g > 255) g = 255;
                if (b < 0) b = 0; if (b > 255) b = 255;
                int quantizedRgb = (((int)r) << 16) | (((int)g) << 8) | ((int)b);

                // 找到调色板中最接近的颜色
                int nearest = findNearest(palette, quantizedRgb);
                int palRgb = palette[nearest];
                result.setRGB(x, y, palRgb); // 不透明（alpha=255）

                // 计算误差并扩散
                float er = r - ((palRgb >> 16) & 0xFF);
                float eg = g - ((palRgb >> 8) & 0xFF);
                float eb = b - (palRgb & 0xFF);

                if (x + 1 < w) {
                    errR[y][x+1] += er * 7.0f / 16.0f;
                    errG[y][x+1] += eg * 7.0f / 16.0f;
                    errB[y][x+1] += eb * 7.0f / 16.0f;
                }
                if (y + 1 < h) {
                    if (x > 0) {
                        errR[y+1][x-1] += er * 3.0f / 16.0f;
                        errG[y+1][x-1] += eg * 3.0f / 16.0f;
                        errB[y+1][x-1] += eb * 3.0f / 16.0f;
                    }
                    errR[y+1][x] += er * 5.0f / 16.0f;
                    errG[y+1][x] += eg * 5.0f / 16.0f;
                    errB[y+1][x] += eb * 5.0f / 16.0f;
                    if (x + 1 < w) {
                        errR[y+1][x+1] += er * 1.0f / 16.0f;
                        errG[y+1][x+1] += eg * 1.0f / 16.0f;
                        errB[y+1][x+1] += eb * 1.0f / 16.0f;
                    }
                }
            }
        }
        return result;
    }

    private int findNearest(int[] palette, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int bestIdx = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int pr = (palette[i] >> 16) & 0xFF;
            int pg = (palette[i] >> 8) & 0xFF;
            int pb = palette[i] & 0xFF;
            long dr = r - pr;
            long dg = g - pg;
            long db = b - pb;
            long dist = dr*dr + dg*dg + db*db;
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
    
    // 将任意图像转换为索引色图像（指定最大颜色数，自动使用内置量化）
    private BufferedImage convertToIndexed(BufferedImage src, int maxColors) {
        // 使用 Java 内置的索引色转换
        BufferedImage indexed = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D g2d = indexed.createGraphics();
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return indexed;
    }

    private BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = copy.getGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private void scaleOnly() {
        if (loadedImages.isEmpty() || currentImageFile==null) { JOptionPane.showMessageDialog(this,"请先打开一张图片"); return; }
        String wt=widthField.getText().trim(), ht=heightField.getText().trim(); int nw=-1,nh=-1;
        if(wt.isEmpty()&&ht.isEmpty()){JOptionPane.showMessageDialog(this,"请输入目标宽度或高度");return;}
        try{if(!wt.isEmpty()){nw=Integer.parseInt(wt);if(nw<=0)throw new NumberFormatException();}if(!ht.isEmpty()){nh=Integer.parseInt(ht);if(nh<=0)throw new NumberFormatException();}}catch(NumberFormatException e){JOptionPane.showMessageDialog(this,"宽度和高度必须为正整数");return;}
        List targets=new ArrayList(); if(applyToAllCheck.isSelected())targets.addAll(loadedImages.keySet());else targets.add(currentImageFile);
        int success=0; for(int i=0;i<targets.size();i++){File file=(File)targets.get(i);BufferedImage source=(BufferedImage)loadedImages.get(file);int w=nw,h=nh;if(keepRatioCheck.isSelected()){if(w>0&&h<=0)h=Math.max(1,(int)((double)source.getHeight()*w/source.getWidth()));else if(h>0&&w<=0)w=Math.max(1,(int)((double)source.getWidth()*h/source.getHeight()));}if(w<=0||h<=0){JOptionPane.showMessageDialog(this,"请同时输入宽度和高度，或勾选保持比例只输入一个");return;}loadedImages.put(file,scaleImage(source,w,h));processedImageFiles.remove(file);success++;}
        refreshThumbnails(); selectImage(currentImageFile); saveButton.setEnabled(false); JOptionPane.showMessageDialog(this,"已缩放 "+success+" 张图片。");
    }
    private void refreshThumbnails(){thumbnailPanel.removeAll();for(Object key:loadedImages.keySet())addThumbnail((File)key,(BufferedImage)loadedImages.get(key));thumbnailPanel.revalidate();thumbnailPanel.repaint();}

    private BufferedImage scaleImage(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return scaled;
    }
    
    // ---------- 主函数 ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new ImageCovert().setVisible(true);
            }
        });
    }
}
