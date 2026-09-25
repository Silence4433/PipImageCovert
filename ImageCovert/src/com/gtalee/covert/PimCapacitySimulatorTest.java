package com.gtalee.covert;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class PimCapacitySimulatorTest {
    public static void main(String[] args) throws Exception {
        List<BufferedImage> ok = new ArrayList<BufferedImage>();
        for (int i = 0; i < 3; i++) {
            BufferedImage im = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) im.setRGB(x, y, new Color(i * 40, y * 30, x * 20, 255).getRGB());
            ok.add(im);
        }
        PimCapacitySimulator.Result r = PimCapacitySimulator.simulate(ok);
        if (!r.valid || r.atlasCount != 1 || r.pdataLength <= 0) throw new RuntimeException("FAIL small PIM");
        List<BufferedImage> many = new ArrayList<BufferedImage>();
        for (int i = 0; i < 256; i++) many.add(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        if (PimCapacitySimulator.simulate(many).valid) throw new RuntimeException("FAIL count limit");
        List<BufferedImage> bad = new ArrayList<BufferedImage>();
        bad.add(new BufferedImage(256, 1, BufferedImage.TYPE_INT_ARGB));
        if (PimCapacitySimulator.simulate(bad).valid) throw new RuntimeException("FAIL width limit");
        System.out.println("PASS PIM simulator colors=" + r.colorCount + " pdata=" + r.pdataLength + " atlas=" + r.atlasCount);
    }
}