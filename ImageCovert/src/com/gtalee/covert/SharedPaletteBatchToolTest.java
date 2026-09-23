package com.gtalee.covert;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import javax.imageio.ImageIO;

/** 简单的命令行回归测试，兼容 JDK 1.6。 */
public class SharedPaletteBatchToolTest {
    public static void main(String[] args) throws Exception {
        File base = new File(System.getProperty("java.io.tmpdir"), "pip_shared_palette_test");
        File input = new File(base, "input");
        File output = new File(base, "output");
        delete(output);
        delete(input);
        input.mkdirs();

        createImage(new File(input, "frame_01.png"), 1);
        createImage(new File(input, "frame_02.png"), 2);
        SharedPaletteBatchTool.Result result = SharedPaletteBatchTool.convertDirectory(input, output, 32, 128, false);
        if (result.fileCount != 2 || result.paletteSize > 32) throw new Exception("颜色或文件计数失败");

        BufferedImage first = ImageIO.read(new File(output, "frame_01.png"));
        BufferedImage second = ImageIO.read(new File(output, "frame_02.png"));
        if (!(first.getColorModel() instanceof IndexColorModel)) throw new Exception("输出不是索引色 PNG");
        if (!(second.getColorModel() instanceof IndexColorModel)) throw new Exception("输出不是索引色 PNG");
        IndexColorModel a = (IndexColorModel) first.getColorModel();
        IndexColorModel b = (IndexColorModel) second.getColorModel();
        if (a.getMapSize() != b.getMapSize()) throw new Exception("调色板长度不一致");
        byte[] paletteA = readChunk(new File(output, "frame_01.png"), "PLTE");
        byte[] paletteB = readChunk(new File(output, "frame_02.png"), "PLTE");
        if (paletteA.length / 3 > 32) throw new Exception("PNG内PLTE超过目标颜色数");
        if (!equals(paletteA, paletteB)) throw new Exception("PNG内PLTE内容不一致");
        if ((a.getRGB(0) >>> 24) != 0) throw new Exception("索引0不是透明色");
        File info = new File(output, "palette_info");
        if (new File(info, "shared_palette.act").length() != 772) throw new Exception("ACT长度错误");
        if (!new File(info, "shared_palette_preview.png").isFile()) throw new Exception("缺少预览图");
        if (!new File(info, "shared_palette_report.txt").isFile()) throw new Exception("缺少报告");
        if (!new File(input, "frame_01.png").isFile()) throw new Exception("输入文件被修改");
        System.out.println("PASS files=" + result.fileCount + " palette=" + result.paletteSize + " output=" + output.getAbsolutePath());
    }

    private static byte[] readChunk(File file, String target) throws Exception {
        DataInputStream input = new DataInputStream(new FileInputStream(file));
        try {
            input.readLong();
            while (true) {
                int length = input.readInt();
                byte[] typeData = new byte[4];
                input.readFully(typeData);
                String type = new String(typeData, "ISO-8859-1");
                byte[] data = new byte[length];
                input.readFully(data);
                input.readInt();
                if (target.equals(type)) return data;
                if ("IEND".equals(type)) throw new Exception("PNG缺少" + target);
            }
        } finally { input.close(); }
    }

    private static boolean equals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static void createImage(File file, int seed) throws Exception {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (x < 4 || y < 4) image.setRGB(x, y, 0x00000000);
            else image.setRGB(x, y, 0xFF000000 | ((x * 17 + seed * 31) & 255) << 16
                    | ((y * 23 + seed * 47) & 255) << 8 | ((x * y + seed * 13) & 255));
        }
        ImageIO.write(image, "png", file);
    }

    private static void delete(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (int i = 0; i < children.length; i++) delete(children[i]);
        }
        file.delete();
    }
}
