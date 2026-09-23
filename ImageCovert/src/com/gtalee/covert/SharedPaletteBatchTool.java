package com.gtalee.covert;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * 批量共享调色板工具，兼容 JDK 1.6。
 * 同一批 PNG 共用一个 IndexColorModel，索引 0 固定为完全透明。
 */
public class SharedPaletteBatchTool extends JFrame {
    private static final String PREF_INPUT = "sharedPaletteInput";
    private static final String PREF_OUTPUT = "sharedPaletteOutput";
    private static final String INFO_DIR = "palette_info";
    private static final String ACT_NAME = "shared_palette.act";
    private static final String PREVIEW_NAME = "shared_palette_preview.png";
    private static final String REPORT_NAME = "shared_palette_report.txt";
    private static final Preferences PREFS = Preferences.userNodeForPackage(SharedPaletteBatchTool.class);

    private final JTextField inputField = new JTextField(36);
    private final JTextField outputField = new JTextField(36);
    private final JSpinner colorSpinner = new JSpinner(new SpinnerNumberModel(248, 2, 256, 1));
    private final JSpinner alphaSpinner = new JSpinner(new SpinnerNumberModel(128, 0, 255, 1));
    private final JCheckBox ditherCheck = new JCheckBox("Floyd-Steinberg 抖动", false);

    public SharedPaletteBatchTool() {
        setTitle("PipImageCovert - 批量共享调色板");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        inputField.setText(PREFS.get(PREF_INPUT, ""));
        outputField.setText(PREFS.get(PREF_OUTPUT, ""));
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(directoryRow("输入目录：", inputField, true));
        form.add(directoryRow("输出目录：", outputField, false));
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
        options.add(new JLabel("总颜色数（含透明色）："));
        options.add(colorSpinner);
        options.add(new JLabel("Alpha阈值："));
        options.add(alphaSpinner);
        options.add(ditherCheck);
        form.add(options);
        add(form, BorderLayout.CENTER);

        JButton run = new JButton("开始批量转换");
        run.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                runConversion();
            }
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.add(run);
        add(bottom, BorderLayout.SOUTH);

        JLabel help = new JLabel("先完成最终缩放；角色素材建议 248 色、Alpha 128、关闭抖动。不会覆盖已有输出。");
        add(help, BorderLayout.NORTH);
        pack();
        setSize(760, 230);
        setLocationRelativeTo(null);
    }

    private JPanel directoryRow(String label, final JTextField field, final boolean input) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel(label));
        row.add(field);
        JButton choose = new JButton("选择...");
        choose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                chooseDirectory(field, input);
            }
        });
        row.add(choose);
        return row;
    }

    private void chooseDirectory(JTextField field, boolean input) {
        File current = new File(field.getText().trim());
        JFileChooser chooser = current.isDirectory() ? new JFileChooser(current) : new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle(input ? "选择包含 PNG 的输入目录" : "选择独立输出目录");
        int answer = input ? chooser.showOpenDialog(this) : chooser.showSaveDialog(this);
        if (answer == JFileChooser.APPROVE_OPTION) field.setText(chooser.getSelectedFile().getAbsolutePath());
    }

    private void runConversion() {
        File input = new File(inputField.getText().trim());
        File output = new File(outputField.getText().trim());
        try {
            Result result = convertDirectory(input, output,
                    ((Integer) colorSpinner.getValue()).intValue(),
                    ((Integer) alphaSpinner.getValue()).intValue(), ditherCheck.isSelected());
            PREFS.put(PREF_INPUT, input.getAbsolutePath());
            PREFS.put(PREF_OUTPUT, output.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "转换完成。\n图片数：" + result.fileCount
                    + "\n实际调色板颜色数：" + result.paletteSize
                    + "\n输出：" + output.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.toString(), "转换失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static Result convertDirectory(File inputDir, File outputDir,
            int targetColors, int alphaThreshold, boolean dither) throws IOException {
        validate(inputDir, outputDir, targetColors, alphaThreshold);
        File[] files = listPngFiles(inputDir);
        if (files.length == 0) throw new IOException("输入目录没有 PNG 文件。");
        checkOutput(outputDir, files);

        ArrayList images = new ArrayList();
        HashMap histogram = new HashMap();
        long transparentPixels = 0;
        long opaquePixels = 0;
        for (int i = 0; i < files.length; i++) {
            BufferedImage image = ImageIO.read(files[i]);
            if (image == null) throw new IOException("无法读取：" + files[i].getAbsolutePath());
            images.add(image);
            long[] counts = addHistogram(image, histogram, alphaThreshold);
            transparentPixels += counts[0];
            opaquePixels += counts[1];
        }
        if (opaquePixels == 0) throw new IOException("所有输入图片均为全透明图片。");

        int[] opaquePalette = buildPalette(histogram, targetColors - 1);
        int[] argbPalette = new int[opaquePalette.length + 1];
        argbPalette[0] = 0x00000000;
        for (int i = 0; i < opaquePalette.length; i++) argbPalette[i + 1] = 0xFF000000 | opaquePalette[i];
        IndexColorModel model = createColorModel(argbPalette);
        HashMap nearestCache = new HashMap();

        if (!outputDir.exists() && !outputDir.mkdirs()) throw new IOException("不能创建输出目录。");
        for (int i = 0; i < files.length; i++) {
            BufferedImage result = mapImage((BufferedImage) images.get(i), model, opaquePalette,
                    alphaThreshold, dither, nearestCache);
            if (!ImageIO.write(result, "png", new File(outputDir, files[i].getName()))) {
                throw new IOException("Java 环境不能写出 PNG：" + files[i].getName());
            }
        }
        File infoDir = new File(outputDir, INFO_DIR);
        if (!infoDir.exists() && !infoDir.mkdirs()) throw new IOException("不能创建调色板信息目录。");
        writeAct(new File(infoDir, ACT_NAME), argbPalette);
        writePreview(new File(infoDir, PREVIEW_NAME), argbPalette);
        writeReport(new File(infoDir, REPORT_NAME), inputDir, outputDir, files,
                targetColors, argbPalette.length, alphaThreshold, dither,
                histogram.size(), transparentPixels, opaquePixels);
        return new Result(files.length, argbPalette.length);
    }

    private static void validate(File input, File output, int colors, int alpha) throws IOException {
        if (!input.isDirectory()) throw new IOException("输入目录不存在。");
        if (input.getCanonicalFile().equals(output.getCanonicalFile())) {
            throw new IOException("输入目录和输出目录不能相同。");
        }
        if (colors < 2 || colors > 256) throw new IOException("总颜色数必须是 2～256。");
        if (alpha < 0 || alpha > 255) throw new IOException("Alpha阈值必须是 0～255。");
    }

    private static File[] listPngFiles(File dir) {
        File[] entries = dir.listFiles();
        ArrayList files = new ArrayList();
        if (entries != null) for (int i = 0; i < entries.length; i++) {
            if (entries[i].isFile() && entries[i].getName().toLowerCase().endsWith(".png")) files.add(entries[i]);
        }
        Collections.sort(files, new Comparator() {
            public int compare(Object a, Object b) {
                return ((File) a).getName().compareToIgnoreCase(((File) b).getName());
            }
        });
        return (File[]) files.toArray(new File[files.size()]);
    }

    private static void checkOutput(File output, File[] inputs) throws IOException {
        if (!output.exists()) return;
        for (int i = 0; i < inputs.length; i++) {
            if (new File(output, inputs[i].getName()).exists()) throw new IOException("输出已存在：" + inputs[i].getName());
        }
        File infoDir = new File(output, INFO_DIR);
        String[] generated = { ACT_NAME, PREVIEW_NAME, REPORT_NAME };
        for (int i = 0; i < generated.length; i++) {
            if (new File(infoDir, generated[i]).exists()) throw new IOException("输出已存在：" + generated[i]);
        }
    }

    private static long[] addHistogram(BufferedImage image, HashMap histogram, int alphaThreshold) {
        long transparent = 0, opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if (((argb >>> 24) & 0xFF) < alphaThreshold) {
                transparent++;
            } else {
                Integer rgb = new Integer(argb & 0xFFFFFF);
                Integer count = (Integer) histogram.get(rgb);
                histogram.put(rgb, new Integer(count == null ? 1 : count.intValue() + 1));
                opaque++;
            }
        }
        return new long[] { transparent, opaque };
    }

    private static int[] buildPalette(HashMap histogram, int target) {
        ArrayList colors = new ArrayList();
        Iterator iterator = histogram.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            colors.add(new WeightedColor(((Integer) entry.getKey()).intValue(), ((Integer) entry.getValue()).intValue()));
        }
        if (colors.size() <= target) {
            Collections.sort(colors, new Comparator() {
                public int compare(Object a, Object b) { return ((WeightedColor) b).count - ((WeightedColor) a).count; }
            });
            int[] exact = new int[colors.size()];
            for (int i = 0; i < exact.length; i++) exact[i] = ((WeightedColor) colors.get(i)).rgb;
            return exact;
        }
        ArrayList boxes = new ArrayList();
        boxes.add(new ColorBox(colors));
        while (boxes.size() < target) {
            ColorBox selected = null;
            long best = -1;
            for (int i = 0; i < boxes.size(); i++) {
                ColorBox box = (ColorBox) boxes.get(i);
                if (box.colors.size() > 1 && box.score() > best) { selected = box; best = box.score(); }
            }
            if (selected == null) break;
            ColorBox[] split = selected.split();
            boxes.remove(selected);
            boxes.add(split[0]); boxes.add(split[1]);
        }
        int[] palette = new int[boxes.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = ((ColorBox) boxes.get(i)).average();
        return palette;
    }

    private static IndexColorModel createColorModel(int[] palette) {
        byte[] r = new byte[palette.length], g = new byte[palette.length];
        byte[] b = new byte[palette.length], a = new byte[palette.length];
        for (int i = 0; i < palette.length; i++) {
            r[i] = (byte) (palette[i] >> 16); g[i] = (byte) (palette[i] >> 8);
            b[i] = (byte) palette[i]; a[i] = (byte) (palette[i] >>> 24);
        }
        return new IndexColorModel(8, palette.length, r, g, b, a);
    }

    private static BufferedImage mapImage(BufferedImage source, IndexColorModel model,
            int[] palette, int alphaThreshold, boolean dither, HashMap cache) {
        WritableRaster raster = model.createCompatibleWritableRaster(source.getWidth(), source.getHeight());
        BufferedImage output = new BufferedImage(model, raster, false, null);
        int width = source.getWidth();
        float[] cr = new float[width + 2], cg = new float[width + 2], cb = new float[width + 2];
        float[] nr = new float[width + 2], ng = new float[width + 2], nb = new float[width + 2];
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < alphaThreshold) { raster.setSample(x, y, 0, 0); continue; }
                float red = clamp(((argb >> 16) & 0xFF) + cr[x + 1]);
                float green = clamp(((argb >> 8) & 0xFF) + cg[x + 1]);
                float blue = clamp((argb & 0xFF) + cb[x + 1]);
                int rgb = ((int) red << 16) | ((int) green << 8) | (int) blue;
                int index = nearest(rgb, palette, cache);
                raster.setSample(x, y, 0, index + 1);
                if (dither) {
                    int chosen = palette[index];
                    float er = red - ((chosen >> 16) & 0xFF), eg = green - ((chosen >> 8) & 0xFF), eb = blue - (chosen & 0xFF);
                    cr[x + 2] += er * 7 / 16; cg[x + 2] += eg * 7 / 16; cb[x + 2] += eb * 7 / 16;
                    nr[x] += er * 3 / 16; ng[x] += eg * 3 / 16; nb[x] += eb * 3 / 16;
                    nr[x + 1] += er * 5 / 16; ng[x + 1] += eg * 5 / 16; nb[x + 1] += eb * 5 / 16;
                    nr[x + 2] += er / 16; ng[x + 2] += eg / 16; nb[x + 2] += eb / 16;
                }
            }
            float[] swap = cr; cr = nr; nr = swap; swap = cg; cg = ng; ng = swap; swap = cb; cb = nb; nb = swap;
            clear(nr); clear(ng); clear(nb);
        }
        return output;
    }

    private static float clamp(float value) { return value < 0 ? 0 : value > 255 ? 255 : value; }
    private static void clear(float[] values) { for (int i = 0; i < values.length; i++) values[i] = 0; }

    private static int nearest(int rgb, int[] palette, HashMap cache) {
        Integer key = new Integer(rgb), old = (Integer) cache.get(key);
        if (old != null) return old.intValue();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF, result = 0;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int dr = r - ((palette[i] >> 16) & 0xFF), dg = g - ((palette[i] >> 8) & 0xFF), db = b - (palette[i] & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < best) { best = distance; result = i; }
        }
        cache.put(key, new Integer(result));
        return result;
    }

    private static void writeAct(File file, int[] palette) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            for (int i = 0; i < 256; i++) {
                int color = i < palette.length ? palette[i] : 0xFF000000;
                out.write((color >> 16) & 0xFF); out.write((color >> 8) & 0xFF); out.write(color & 0xFF);
            }
            // ImageWorkShop扩展：实际颜色数（2字节）和透明索引0。
            out.write((palette.length >> 8) & 0xFF);
            out.write(palette.length & 0xFF);
            out.write(0);
            out.write(0);
        } finally { out.close(); }
    }

    private static void writePreview(File file, int[] palette) throws IOException {
        int cell = 24;
        BufferedImage image = new BufferedImage(cell * 16, cell * 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            for (int i = 0; i < 256; i++) {
                int x = i % 16 * cell, y = i / 16 * cell;
                int color = i < palette.length ? palette[i] : 0x00000000;
                if ((color >>> 24) == 0) {
                    g.setColor(Color.LIGHT_GRAY); g.fillRect(x, y, cell, cell);
                    g.setColor(Color.WHITE); g.fillRect(x, y, cell / 2, cell / 2); g.fillRect(x + cell / 2, y + cell / 2, cell / 2, cell / 2);
                } else { g.setColor(new Color(color, true)); g.fillRect(x, y, cell, cell); }
                g.setColor(Color.DARK_GRAY); g.drawRect(x, y, cell - 1, cell - 1);
            }
        } finally { g.dispose(); }
        ImageIO.write(image, "png", file);
    }

    private static void writeReport(File file, File input, File output, File[] files,
            int target, int actual, int alpha, boolean dither, int sourceColors,
            long transparent, long opaque) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        try {
            writer.write("PipImageCovert 批量共享调色板处理报告\r\n");
            writer.write("输入目录：" + input.getAbsolutePath() + "\r\n输出目录：" + output.getAbsolutePath() + "\r\n");
            writer.write("图片数：" + files.length + "\r\n源不透明RGB颜色数：" + sourceColors + "\r\n");
            writer.write("目标总颜色数：" + target + "\r\n实际调色板颜色数：" + actual + "\r\n");
            writer.write("Alpha阈值：" + alpha + "\r\n抖动：" + (dither ? "Floyd-Steinberg" : "无") + "\r\n");
            writer.write("透明像素数：" + transparent + "\r\n不透明像素数：" + opaque + "\r\n\r\n文件：\r\n");
            for (int i = 0; i < files.length; i++) writer.write(files[i].getName() + "\r\n");
        } finally { writer.close(); }
    }

    public static class Result {
        public final int fileCount, paletteSize;
        Result(int fileCount, int paletteSize) { this.fileCount = fileCount; this.paletteSize = paletteSize; }
    }

    private static class WeightedColor {
        final int rgb, r, g, b, count;
        WeightedColor(int rgb, int count) {
            this.rgb = rgb; this.r = rgb >> 16 & 255; this.g = rgb >> 8 & 255; this.b = rgb & 255; this.count = count;
        }
    }

    private static class ColorBox {
        final ArrayList colors;
        int minR = 255, maxR, minG = 255, maxG, minB = 255, maxB;
        long population;
        ColorBox(List source) {
            colors = new ArrayList(source);
            for (int i = 0; i < colors.size(); i++) {
                WeightedColor c = (WeightedColor) colors.get(i);
                minR = Math.min(minR, c.r); maxR = Math.max(maxR, c.r); minG = Math.min(minG, c.g);
                maxG = Math.max(maxG, c.g); minB = Math.min(minB, c.b); maxB = Math.max(maxB, c.b); population += c.count;
            }
        }
        long score() { return population * (maxR - minR + 1L) * (maxG - minG + 1L) * (maxB - minB + 1L); }
        int channel() {
            int rr = maxR - minR, gr = maxG - minG, br = maxB - minB;
            return rr >= gr && rr >= br ? 0 : gr >= rr && gr >= br ? 1 : 2;
        }
        ColorBox[] split() {
            final int channel = channel();
            Collections.sort(colors, new Comparator() {
                public int compare(Object a, Object b) {
                    WeightedColor x = (WeightedColor) a, y = (WeightedColor) b;
                    return channel == 0 ? x.r - y.r : channel == 1 ? x.g - y.g : x.b - y.b;
                }
            });
            long half = population / 2, total = 0; int index = 1;
            for (int i = 0; i < colors.size() - 1; i++) { total += ((WeightedColor) colors.get(i)).count; index = i + 1; if (total >= half) break; }
            return new ColorBox[] { new ColorBox(colors.subList(0, index)), new ColorBox(colors.subList(index, colors.size())) };
        }
        int average() {
            long r = 0, g = 0, b = 0, total = 0;
            for (int i = 0; i < colors.size(); i++) {
                WeightedColor c = (WeightedColor) colors.get(i);
                r += (long) c.r * c.count; g += (long) c.g * c.count; b += (long) c.b * c.count; total += c.count;
            }
            return (int) (r / total) << 16 | (int) (g / total) << 8 | (int) (b / total);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new SharedPaletteBatchTool().setVisible(true); }
        });
    }
}
