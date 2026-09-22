package com.gtalee.covert;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
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
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ImageCovert.class);

    private JLabel imageLabel;
    private JButton openButton, saveButton, processButton;
    private JComboBox colorCountCombo;
    private BufferedImage originalImage;
    private BufferedImage processedImage;

    private JButton scaleButton;	
    private JTextField widthField;
    private JTextField heightField;
    private JCheckBox keepRatioCheck;
    private BufferedImage currentImage;  // 新增：当前工作图片
    
    public ImageCovert() {
        setTitle("颜色减少工具 (索引色输出)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());
        openButton = new JButton("打开图片");
        saveButton = new JButton("保存结果");
        processButton = new JButton("处理");
        scaleButton = new JButton("缩放像素");
        
        // 缩放控件
        widthField = new JTextField(5);
        heightField = new JTextField(5);
        keepRatioCheck = new JCheckBox("保持比例", true);
        topPanel.add(widthField);
        topPanel.add(new JLabel(" x "));
        topPanel.add(heightField);
        topPanel.add(keepRatioCheck);

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

        topPanel.add(openButton);
        topPanel.add(new JLabel("颜色数:"));
        topPanel.add(colorCountCombo);
        topPanel.add(processButton);
        topPanel.add(saveButton);
        
        topPanel.add(scaleButton);
        add(topPanel, BorderLayout.NORTH);

        imageLabel = new JLabel("请打开一张图片", JLabel.CENTER);
        imageLabel.setPreferredSize(new Dimension(640, 480));
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);
        
        
        // 使用说明
        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setBackground(new Color(245, 245, 245));
        infoArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        infoArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        infoArea.setText(
            "【推荐操作流程】\n" +
            "① 打开图片\n" +
            "② 输入目标宽高（可选），点击“缩放” → 可保存缩放后的图片\n" +
            "③ 选择颜色数，点击“处理” → 可保存减色后的图片\n\n" +
            "【为什么要先缩放再处理？】\n" +
            " 缩放会通过插值计算新像素，必然引入新颜色。\n" +
            " 如果先减色再缩放，缩放会破坏减色效果，使颜色数重新增多。\n" +
            " 先缩放再减色，能确保最终图像同时满足尺寸和颜色数要求。\n\n" +
            "【提示】\n" +
            " 缩放后可直接保存（不进行减色）。\n" +
            " 处理（减色）后也可直接保存。\n" +
            " 透明背景全程保留。"
        );
        
        infoArea.setPreferredSize(new Dimension(640, 180));
        add(infoArea, BorderLayout.SOUTH);
        
        openButton.addActionListener(new ActionListener() {
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

        pack();
        
        setSize(900, 650);// 适当增大窗口高度
        
        setLocationRelativeTo(null);
    }

    private void openImage() {
        JFileChooser chooser = createFileChooser(PREF_OPEN_DIRECTORY);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "图片文件", "jpg", "jpeg", "png", "bmp", "gif"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            rememberDirectory(PREF_OPEN_DIRECTORY, file);
            try {
                originalImage = ImageIO.read(file);
                if (originalImage == null) {
                    JOptionPane.showMessageDialog(this, "无法读取图片格式");
                    return;
                }
                currentImage = originalImage;  // 新增
                showImage(currentImage);       // 改为显示 currentImage
                saveButton.setEnabled(false);
                processedImage = null;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "读取图片失败: " + ex.getMessage());
            }
        }
    }

    private void processImage() {
        if (currentImage == null) {  // 改为检查 currentImage
            JOptionPane.showMessageDialog(this, "请先打开一张图片");
            return;
        }
        Object selected = colorCountCombo.getSelectedItem();
        int targetColors = ((Integer) selected).intValue();
        processedImage = medianCutQuantize(currentImage, targetColors);  // 基于 currentImage
        currentImage = processedImage;  // 更新当前工作图片
        showImage(currentImage);
        saveButton.setEnabled(true);
    }

    private void saveImage() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "没有可保存的处理结果");
            return;
        }
        JFileChooser chooser = createFileChooser(PREF_SAVE_DIRECTORY);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PNG 图片", "png"));
        int result = chooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }
            rememberDirectory(PREF_SAVE_DIRECTORY, file);
            try {
                ImageIO.write(currentImage, "png", file);
                JOptionPane.showMessageDialog(this, "保存成功");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage());
            }
        }
    }

    private JFileChooser createFileChooser(String preferenceKey) {
        String directoryPath = PREFERENCES.get(preferenceKey, null);
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
            PREFERENCES.put(preferenceKey, directory.getAbsolutePath());
        }
    }

    private void showImage(BufferedImage img) {
        // 如果图像是索引色，先转为真彩色再缩放显示
        BufferedImage displayImg = img;
        if (img.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
        	displayImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = displayImg.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }

        ImageIcon icon = new ImageIcon(displayImg.getScaledInstance(
                Math.min(displayImg.getWidth(), 640),
                Math.min(displayImg.getHeight(), 480),
                Image.SCALE_SMOOTH));
        imageLabel.setIcon(icon);
        imageLabel.setText("");
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
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "请先打开一张图片");
            return;
        }

        String widthText = widthField.getText().trim();
        String heightText = heightField.getText().trim();
        int newWidth = -1, newHeight = -1;

        if (widthText.isEmpty() && heightText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入目标宽度或高度");
            return;
        }

        try {
            if (!widthText.isEmpty()) {
                newWidth = Integer.parseInt(widthText);
                if (newWidth <= 0) throw new NumberFormatException();
            }
            if (!heightText.isEmpty()) {
                newHeight = Integer.parseInt(heightText);
                if (newHeight <= 0) throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "宽度和高度必须为正整数");
            return;
        }

        // 如果启用了保持比例，自动计算缺失的尺寸
        if (keepRatioCheck.isSelected()) {
            int origW = originalImage.getWidth();
            int origH = originalImage.getHeight();
            if (newWidth > 0 && newHeight <= 0) {
                newHeight = (int) ((double) origH * newWidth / origW);
                if (newHeight < 1) newHeight = 1;
                heightField.setText(String.valueOf(newHeight));
            } else if (newHeight > 0 && newWidth <= 0) {
                newWidth = (int) ((double) origW * newHeight / origH);
                if (newWidth < 1) newWidth = 1;
                widthField.setText(String.valueOf(newWidth));
            }
        }
        
        // 确保两个尺寸都已设置,避免出现错误尺寸
        if (newWidth <= 0 || newHeight <= 0) {
            JOptionPane.showMessageDialog(this, "请同时输入宽度和高度，或勾选“保持比例”只输入一个");
            return;
        }
        
        // 执行缩放
        BufferedImage scaled = scaleImage(originalImage, newWidth, newHeight);
        // 将缩放后的图片设为当前工作图片（替换 originalImage，以便后续颜色减少使用）
        originalImage = scaled;
        currentImage = scaled;       // 更新当前工作图片
        showImage(currentImage);
        // 清空之前处理结果，防止混淆
        //processedImage = null;
        saveButton.setEnabled(true); // 启用保存
    }
    
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
