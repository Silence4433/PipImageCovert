package com.gtalee.covert;

import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.CRC32;

/** PIM合并模式容量预检。独立于New_ImageWorkShop工程。 */
public final class PimCapacitySimulator {
    public static final int MAX_IMAGES = 255;
    public static final int MAX_SIDE = 255;
    public static final int MAX_DATA = 65535;

    public static final class Result {
        public boolean valid;
        public String reason;
        public int imageCount;
        public int colorCount;
        public int atlasCount;
        public int pdataLength;
        public int maxIdataLength;
        public int[] idataLengths;
        public int[] atlasWidth;
        public int[] atlasHeight;
        public int[] x;
        public int[] y;
        public int[] atlasIndex;
    }

    public static final class PreparedPatch {
        public final int width;
        public final int height;
        public final int[] pixels;
        public final java.util.Set<Integer> colors = new java.util.LinkedHashSet<Integer>();
        public PreparedPatch(BufferedImage image) {
            width = image.getWidth(); height = image.getHeight(); pixels = new int[width * height];
            int n = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int c = image.getRGB(x, y); pixels[n++] = c; colors.add(Integer.valueOf(c));
            }
        }
    }

    public static PreparedPatch prepare(BufferedImage image) {
        if (image == null) return null;
        return new PreparedPatch(image);
    }

    public static Result simulatePrepared(List<PreparedPatch> images) throws IOException {
        Result r = new Result();
        r.imageCount = images == null ? 0 : images.size();
        if (images == null || images.size() == 0) return fail(r, "没有可合并的图块");
        if (images.size() > MAX_IMAGES) return fail(r, "合并模式图块数量不能超过255");
        r.x = new int[images.size()]; r.y = new int[images.size()]; r.atlasIndex = new int[images.size()];
        Map<Integer, Integer> colors = new java.util.LinkedHashMap<Integer, Integer>();
        Rectangle[] rects = new Rectangle[images.size()];
        for (int j = 0; j < images.size(); j++) {
            PreparedPatch im = images.get(j);
            if (im == null || im.width <= 0 || im.height <= 0) return fail(r, "存在空图块");
            if (im.width > MAX_SIDE || im.height > MAX_SIDE) return fail(r, "合并模式中每张图片的宽度和高度不能超过255");
            for (Integer c : im.colors) {
                if (!colors.containsKey(c)) colors.put(c, Integer.valueOf(colors.size()));
                if (colors.size() > 256) return fail(r, "合并模式不能超过256色");
            }
            rects[j] = new Rectangle(0, 0, im.width, im.height);
        }
        r.colorCount = colors.size();
        PimLayout.Result layout = PimLayout.layout(rects);
        int[] atlasFor = new int[images.size()];
        for (int i = 0; i < images.size(); i++) {
            r.x[i] = rects[i].x & 0x3FFF; r.y[i] = rects[i].y;
            atlasFor[i] = (rects[i].x >> 14) & 0x03;
            if (atlasFor[i] >= layout.bounds.length) atlasFor[i] = 0;
            r.atlasIndex[i] = atlasFor[i];
        }
        r.atlasCount = layout.bounds.length; r.atlasWidth = new int[r.atlasCount]; r.atlasHeight = new int[r.atlasCount];
        int[] dataLengths = new int[images.size()];
        for (int i = 0; i < images.size(); i++) {
            PreparedPatch im = images.get(i);
            dataLengths[i] = im.pixels.length;
            if (dataLengths[i] > MAX_DATA) return fail(r, "单个图块过大，idata超过65535");
            if (dataLengths[i] > r.maxIdataLength) r.maxIdataLength = dataLengths[i];
        }
        for (int a = 0; a < layout.bounds.length; a++) {
            Rectangle bound = layout.bounds[a]; r.atlasWidth[a] = bound.width; r.atlasHeight[a] = bound.height;
            byte[][] pixels = new byte[bound.height][bound.width];
            for (int i = 0; i < images.size(); i++) if (atlasFor[i] == a) {
                PreparedPatch im = images.get(i); int pos = 0;
                for (int yy = 0; yy < im.height; yy++) for (int xx = 0; xx < im.width; xx++) {
                    int py = yy + r.y[i], px = xx + r.x[i];
                    if (py >= 0 && py < bound.height && px >= 0 && px < bound.width)
                        pixels[py][px] = (byte)colors.get(Integer.valueOf(im.pixels[pos])).intValue();
                    pos++;
                }
            }
            byte[] png = encodePng(bound.width, bound.height, pixels, colors);
            if (a == 0) r.pdataLength = png.length;
            if (png.length > MAX_DATA) return fail(r, a == 0 ? "合并调色板图片过大，pdata超过65535" : "合并图块图片过大，idata超过65535");
        }
        r.idataLengths = dataLengths; r.valid = true; r.reason = "OK"; return r;
    }

    private PimCapacitySimulator() {}

    public static Result simulate(List<BufferedImage> images) throws IOException {
        if (images == null) return simulatePrepared(null);
        List<PreparedPatch> prepared = new ArrayList<PreparedPatch>(images.size());
        for (BufferedImage image : images) prepared.add(prepare(image));
        return simulatePrepared(prepared);
    }    private static Result fail(Result r, String reason) { r.valid = false; r.reason = reason; return r; }

    private static byte[] encodeIndexed(BufferedImage im, Map<Integer,Integer> colors) { byte[] out=new byte[im.getWidth()*im.getHeight()]; int p=0; for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)out[p++]=(byte)colors.get(Integer.valueOf(im.getRGB(x,y))).intValue(); return out; }
    private static byte[] encodePng(int w,int h,byte[][] px,Map<Integer,Integer> colors) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(out); d.writeLong(0x89504E470D0A1A0AL); ByteArrayOutputStream ih=new ByteArrayOutputStream(); DataOutputStream i=new DataOutputStream(ih); i.writeInt(w);i.writeInt(h);i.writeByte(8);i.writeByte(3);i.writeByte(0);i.writeByte(0);i.writeByte(0); chunk(d,"IHDR",ih.toByteArray());
        byte[] pl=new byte[colors.size()*3]; int pi=0; for(Integer c:colors.keySet()){int v=c.intValue();pl[pi++]=(byte)(v>>16);pl[pi++]=(byte)(v>>8);pl[pi++]=(byte)v;} chunk(d,"PLTE",pl); ByteArrayOutputStream raw=new ByteArrayOutputStream(); for(byte[] row:px){raw.write(0);raw.write(row);} Deflater def=new Deflater(9); def.setInput(raw.toByteArray());def.finish();byte[] z=new byte[raw.size()+128];int n=def.deflate(z);byte[] zd=new byte[n];System.arraycopy(z,0,zd,0,n);chunk(d,"IDAT",zd);chunk(d,"IEND",new byte[0]);d.close();return out.toByteArray();
    }
    private static void chunk(DataOutputStream d,String name,byte[] data)throws IOException{CRC32 c=new CRC32();byte[] n=name.getBytes("ISO-8859-1");d.writeInt(data.length);d.write(n);d.write(data);c.update(n);c.update(data);d.writeInt((int)c.getValue());}
}