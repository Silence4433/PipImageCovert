package com.gtalee.covert;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** 第二版：透明边界裁剪、图集以及 ImageWorkShop VERSION_4 .s 文件。 */
public class AtlasBuildTool extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AtlasBuildTool.class);
    private final JTextField inputField = new JTextField(34);
    private final JTextField outputField = new JTextField(34);
    private final JTextField nameField = new JTextField("atlas", 12);
    private final JComboBox modeCombo = new JComboBox(new String[] { "紧密排列", "规则网格" });
    private final JSpinner columnsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 64, 1));
    private final JSpinner paddingSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 16, 1));
    private final JCheckBox trimCheck = new JCheckBox("裁剪透明边界", true);

    public AtlasBuildTool() {
        setTitle("PipImageCovert - 图集与切分文件");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        inputField.setText(PREFS.get("atlasInput", ""));
        outputField.setText(PREFS.get("atlasOutput", ""));
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(directoryRow("输入目录：", inputField, true));
        form.add(directoryRow("输出目录：", outputField, false));
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
        options.add(new JLabel("输出名：")); options.add(nameField);
        options.add(new JLabel("排列：")); options.add(modeCombo);
        options.add(new JLabel("网格列数：")); options.add(columnsSpinner);
        options.add(new JLabel("间隔：")); options.add(paddingSpinner); options.add(trimCheck);
        form.add(options);
        add(form, BorderLayout.CENTER);
        add(new JLabel("输入应为第一版输出的同调色板索引 PNG；生成 PNG + VERSION_4 .s + 报告。"), BorderLayout.NORTH);
        JButton run = new JButton("生成图集与切分文件");
        run.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) { runBuild(); }
        });
        JPanel bottom = new JPanel(new FlowLayout()); bottom.add(run); add(bottom, BorderLayout.SOUTH);
        setSize(830, 235); setLocationRelativeTo(null);
    }

    private JPanel directoryRow(String label, final JTextField field, final boolean input) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel(label)); row.add(field);
        JButton choose = new JButton("选择...");
        choose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                JFileChooser chooser = new JFileChooser(field.getText().trim());
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                int result = input ? chooser.showOpenDialog(AtlasBuildTool.this) : chooser.showSaveDialog(AtlasBuildTool.this);
                if (result == JFileChooser.APPROVE_OPTION) field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(choose); return row;
    }

    private void runBuild() {
        try {
            File input = new File(inputField.getText().trim());
            File output = new File(outputField.getText().trim());
            String name = nameField.getText().trim();
            Result result = build(input, output, name, modeCombo.getSelectedIndex() == 1,
                    ((Integer) columnsSpinner.getValue()).intValue(),
                    ((Integer) paddingSpinner.getValue()).intValue(), trimCheck.isSelected());
            PREFS.put("atlasInput", input.getAbsolutePath()); PREFS.put("atlasOutput", output.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "生成完成。\n图块：" + result.tileCount + "\n图集："
                    + result.width + "x" + result.height + "\nPNG字节：" + result.pngBytes
                    + (result.risk ? "\n警告：超过或接近65535字节，请拆分图集。" : ""));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.toString(), "生成失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static Result build(File inputDir, File outputDir, String name, boolean grid,
            int columns, int padding, boolean trim) throws IOException {
        if (!inputDir.isDirectory()) throw new IOException("输入目录不存在。");
        if (inputDir.getCanonicalFile().equals(outputDir.getCanonicalFile())) throw new IOException("输入输出目录不能相同。");
        if (!name.matches("[A-Za-z0-9_\\-]+")) throw new IOException("输出名只能使用英文、数字、下划线或减号。");
        File[] files = listPngFiles(inputDir);
        if (files.length == 0) throw new IOException("输入目录没有PNG。");
        if (files.length > 255) throw new IOException("图块数量超过合并模式上限255：" + files.length);
        if (padding < 0) throw new IOException("间隔不能小于0。");

        File pngFile = new File(outputDir, name + ".png");
        File descFile = new File(outputDir, name + ".s");
        File reportFile = new File(outputDir, name + "_atlas_report.txt");
        if (pngFile.exists() || descFile.exists() || reportFile.exists()) throw new IOException("输出文件已存在，未覆盖。");

        PaletteInfo palette = readPalette(files[0]);
        ArrayList tiles = new ArrayList();
        for (int i = 0; i < files.length; i++) {
            PaletteInfo current = readPalette(files[i]);
            if (!palette.equals(current)) throw new IOException("PNG调色板不一致：" + files[i].getName());
            BufferedImage image = ImageIO.read(files[i]);
            if (!(image.getColorModel() instanceof IndexColorModel)) throw new IOException("不是索引色PNG：" + files[i].getName());
            Tile tile = makeTile(files[i], image, trim);
            if (tile.width > 255 || tile.height > 255) throw new IOException("图块超过255x255：" + files[i].getName()
                    + " = " + tile.width + "x" + tile.height);
            tiles.add(tile);
        }
        Size size = grid ? placeGrid(tiles, columns, padding) : placeTight(tiles, padding);
        IndexColorModel model = palette.toColorModel();
        WritableRaster atlasRaster = model.createCompatibleWritableRaster(size.width, size.height);
        BufferedImage atlas = new BufferedImage(model, atlasRaster, false, null);
        for (int i = 0; i < tiles.size(); i++) copyTile((Tile) tiles.get(i), atlasRaster);

        if (!outputDir.exists() && !outputDir.mkdirs()) throw new IOException("不能创建输出目录。");
        if (!ImageIO.write(atlas, "png", pngFile)) throw new IOException("不能写出图集PNG。");
        writeDescription(descFile, tiles);
        long bytes = pngFile.length();
        boolean risk = bytes >= 60000;
        writeReport(reportFile, inputDir, pngFile, descFile, tiles, size, bytes, risk, grid, columns, padding, trim, palette.size());
        return new Result(tiles.size(), size.width, size.height, bytes, risk);
    }

    private static File[] listPngFiles(File dir) {
        File[] all = dir.listFiles(); ArrayList list = new ArrayList();
        if (all != null) for (int i = 0; i < all.length; i++) {
            if (all[i].isFile() && all[i].getName().toLowerCase().endsWith(".png")) list.add(all[i]);
        }
        Collections.sort(list, new Comparator() {
            public int compare(Object a, Object b) { return ((File) a).getName().compareToIgnoreCase(((File) b).getName()); }
        });
        return (File[]) list.toArray(new File[list.size()]);
    }

    private static Tile makeTile(File file, BufferedImage image, boolean trim) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        if (trim) for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (((image.getRGB(x, y) >>> 24) & 255) != 0) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        if (!trim) { minX = 0; minY = 0; maxX = image.getWidth() - 1; maxY = image.getHeight() - 1; }
        if (maxX < minX) { minX = minY = 0; maxX = maxY = 0; }
        return new Tile(file, image, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Size placeGrid(ArrayList tiles, int columns, int padding) {
        int cellW = 1, cellH = 1;
        for (int i = 0; i < tiles.size(); i++) { Tile t = (Tile) tiles.get(i); cellW = Math.max(cellW, t.width); cellH = Math.max(cellH, t.height); }
        int rows = (tiles.size() + columns - 1) / columns;
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = (Tile) tiles.get(i); t.atlasX = padding + (i % columns) * (cellW + padding); t.atlasY = padding + (i / columns) * (cellH + padding);
        }
        return new Size(padding + columns * (cellW + padding), padding + rows * (cellH + padding));
    }

    private static Size placeTight(ArrayList tiles, int padding) {
        ArrayList sorted = new ArrayList(tiles);
        Collections.sort(sorted, new Comparator() {
            public int compare(Object a, Object b) { return ((Tile) b).height - ((Tile) a).height; }
        });
        long area = 0; int maxWidth = 1;
        for (int i = 0; i < sorted.size(); i++) { Tile t = (Tile) sorted.get(i); area += (long) (t.width + padding) * (t.height + padding); maxWidth = Math.max(maxWidth, t.width); }
        int targetWidth = Math.max(maxWidth + padding * 2, (int) Math.sqrt(area) + padding * 2);
        int x = padding, y = padding, rowHeight = 0, usedWidth = 1;
        for (int i = 0; i < sorted.size(); i++) {
            Tile t = (Tile) sorted.get(i);
            if (x > padding && x + t.width + padding > targetWidth) { x = padding; y += rowHeight + padding; rowHeight = 0; }
            t.atlasX = x; t.atlasY = y; x += t.width + padding; rowHeight = Math.max(rowHeight, t.height); usedWidth = Math.max(usedWidth, x);
        }
        return new Size(usedWidth, y + rowHeight + padding);
    }

    private static void copyTile(Tile tile, WritableRaster target) {
        WritableRaster source = tile.image.getRaster();
        for (int y = 0; y < tile.height; y++) for (int x = 0; x < tile.width; x++) {
            target.setSample(tile.atlasX + x, tile.atlasY + y, 0,
                    source.getSample(tile.trimX + x, tile.trimY + y, 0));
        }
    }

    private static void writeDescription(File file, ArrayList tiles) throws IOException {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
        try {
            out.writeByte(4); out.writeShort(tiles.size());
            for (int i = 0; i < tiles.size(); i++) {
                Tile t = (Tile) tiles.get(i);
                out.writeShort(t.atlasX); out.writeShort(t.atlasY); out.writeShort(t.width); out.writeShort(t.height);
                out.writeByte(0); out.writeByte(0);
            }
        } finally { out.close(); }
    }

    private static PaletteInfo readPalette(File file) throws IOException {
        DataInputStream in = new DataInputStream(new FileInputStream(file));
        byte[] plte = null, trns = null;
        try {
            if (in.readLong() != 0x89504E470D0A1A0AL) throw new IOException("不是PNG：" + file.getName());
            while (true) {
                int length = in.readInt(); byte[] nameBytes = new byte[4]; in.readFully(nameBytes);
                String name = new String(nameBytes, "ISO-8859-1"); byte[] data = new byte[length]; in.readFully(data); in.readInt();
                if ("PLTE".equals(name)) plte = data; else if ("tRNS".equals(name)) trns = data; else if ("IEND".equals(name)) break;
            }
        } finally { in.close(); }
        if (plte == null || plte.length == 0 || plte.length % 3 != 0) throw new IOException("PNG没有有效PLTE：" + file.getName());
        return new PaletteInfo(plte, trns);
    }

    private static void writeReport(File file, File input, File png, File desc, ArrayList tiles,
            Size size, long bytes, boolean risk, boolean grid, int columns, int padding, boolean trim, int colors) throws IOException {
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        try {
            w.write("PipImageCovert 图集报告\r\n输入：" + input.getAbsolutePath() + "\r\n");
            w.write("图集：" + png.getName() + "\r\n切分：" + desc.getName() + "\r\n调色板颜色：" + colors + "\r\n");
            w.write("排列：" + (grid ? "规则网格，列数=" + columns : "紧密排列") + "\r\n间隔：" + padding + "\r\n裁剪：" + trim + "\r\n");
            w.write("图块数：" + tiles.size() + "\r\n图集尺寸：" + size.width + "x" + size.height + "\r\nPNG字节：" + bytes + "\r\n");
            w.write("65535风险：" + (risk ? "是，请拆分" : "否（仍需ImageWorkShop实际保存验证）") + "\r\n\r\n");
            w.write("序号\t文件\t图集X,Y,W,H\t原图裁剪X,Y\r\n");
            for (int i = 0; i < tiles.size(); i++) {
                Tile t = (Tile) tiles.get(i);
                w.write(i + "\t" + t.file.getName() + "\t" + t.atlasX + "," + t.atlasY + "," + t.width + "," + t.height
                        + "\t" + t.trimX + "," + t.trimY + "\r\n");
            }
        } finally { w.close(); }
    }

    private static class Tile {
        final File file; final BufferedImage image; final int trimX, trimY, width, height; int atlasX, atlasY;
        Tile(File file, BufferedImage image, int trimX, int trimY, int width, int height) {
            this.file = file; this.image = image; this.trimX = trimX; this.trimY = trimY; this.width = width; this.height = height;
        }
    }
    private static class Size { final int width, height; Size(int w, int h) { width = w; height = h; } }
    private static class PaletteInfo {
        final byte[] plte, trns;
        PaletteInfo(byte[] p, byte[] t) { plte = p; trns = t == null ? new byte[0] : t; }
        int size() { return plte.length / 3; }
        boolean equals(PaletteInfo other) { return same(plte, other.plte) && same(trns, other.trns); }
        private boolean same(byte[] a, byte[] b) {
            if (a.length != b.length) return false; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false; return true;
        }
        IndexColorModel toColorModel() {
            int count = size(); byte[] r = new byte[count], g = new byte[count], b = new byte[count], a = new byte[count];
            for (int i = 0; i < count; i++) { r[i] = plte[i * 3]; g[i] = plte[i * 3 + 1]; b[i] = plte[i * 3 + 2]; a[i] = i < trns.length ? trns[i] : (byte) 255; }
            return new IndexColorModel(8, count, r, g, b, a);
        }
    }
    public static class Result {
        public final int tileCount, width, height; public final long pngBytes; public final boolean risk;
        Result(int count, int w, int h, long bytes, boolean risk) { tileCount = count; width = w; height = h; pngBytes = bytes; this.risk = risk; }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() { public void run() { new AtlasBuildTool().setVisible(true); } });
    }
}
