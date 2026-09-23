package com.gtalee.covert;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

/** 第二版命令行回归测试。 */
public class AtlasBuildToolTest {
    public static void main(String[] args) throws Exception {
        File base = new File(System.getProperty("java.io.tmpdir"), "pip_shared_palette_test");
        File input = new File(base, "output");
        File output = new File(base, "atlas");
        delete(output);
        AtlasBuildTool.Result result = AtlasBuildTool.build(input, output, "test_atlas", false, 4, 1, true);
        if (result.tileCount != 2) throw new Exception("图块数量错误");
        File png = new File(output, "test_atlas.png");
        File desc = new File(output, "test_atlas.s");
        File report = new File(output, "test_atlas_atlas_report.txt");
        if (!png.isFile() || !desc.isFile() || !report.isFile()) throw new Exception("输出文件不完整");
        DataInputStream in = new DataInputStream(new FileInputStream(desc));
        try {
            if (in.readUnsignedByte() != 4) throw new Exception(".s版本不是VERSION_4");
            if (in.readUnsignedShort() != 2) throw new Exception(".s图块数量错误");
            for (int i = 0; i < 2; i++) {
                in.readUnsignedShort(); in.readUnsignedShort();
                int width = in.readUnsignedShort(), height = in.readUnsignedShort();
                if (width > 255 || height > 255) throw new Exception("图块尺寸超限");
                if (in.readUnsignedByte() != 0 || in.readUnsignedByte() != 0) throw new Exception("图块参数错误");
            }
        } finally { in.close(); }
        System.out.println("PASS atlas tiles=" + result.tileCount + " size=" + result.width + "x" + result.height
                + " bytes=" + result.pngBytes);
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
