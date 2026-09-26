package com.gtalee.covert;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import java.util.List;
import com.pipimage.png.PngFile;

/** 使用 New_ImageWorkShop 的原始 PNG 编码器检查合并模式容量（源码编码：GBK）。 */
public final class WorkshopPimValidator {
    public static final class Result {
        public boolean valid;
        public String reason;
        public int pdataLength;
        public int maxIdataLength;
        public int atlasCount;
        public long pdataCrc;
        public long[] idataCrc;
    }
    private static final Map<BufferedImage,byte[]> sharedIndexedCache = new WeakHashMap<BufferedImage,byte[]>();
    private WorkshopPimValidator() { }
    private static long crc(byte[] bytes) { java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(bytes); return c.getValue(); }

    public static Result validate(List<BufferedImage> images) throws IOException { return validate(images,true); }
    public static Result validateFast(List<BufferedImage> images) throws IOException { return validate(images,false); }
    public static Result validateWithPalette(List<BufferedImage> images,int[] sharedPalette) throws IOException { return validateWithPalette(images,sharedPalette,true,false); }
    public static Result validateFastWithPalette(List<BufferedImage> images,int[] sharedPalette) throws IOException { return validateWithPalette(images,sharedPalette,false,false); }
    public static Result validateProbeWithPalette(List<BufferedImage> images,int[] sharedPalette) throws IOException { return validateWithPalette(images,sharedPalette,false,true); }
    private static Result validate(List<BufferedImage> images, boolean bestCompress) throws IOException { return validateWithPalette(images,null,bestCompress,false); }
    private static Result validateWithPalette(List<BufferedImage> images,int[] sharedPalette, boolean bestCompress,boolean probeLayout) throws IOException {
        Result result = new Result();
        if (images == null || images.size() == 0 || images.size() > 255) {
            result.reason = "PIM image count must be 1..255"; return result;
        }
        Map<Integer, Integer> colorMap = new HashMap<Integer, Integer>();
        List<Integer> palette = new ArrayList<Integer>();
        if(sharedPalette!=null){for(int i=0;i<sharedPalette.length;i++){Integer value=Integer.valueOf(sharedPalette[i]);palette.add(value);colorMap.put(value,Integer.valueOf(i));}}
        List<byte[]> indexed = new ArrayList<byte[]>();
        Rectangle[] rects = new Rectangle[images.size()];
        for (int i = 0; i < images.size(); i++) {
            BufferedImage image = images.get(i);
            if (image == null) { result.reason = "Empty PIM patch"; return result; }
            int left = 0, top = 0, right = image.getWidth() - 1, bottom = image.getHeight() - 1;
            int width = image.getWidth(), height = image.getHeight();
            if (width < 1 || height < 1 || width > 255 || height > 255) { result.reason = "PIM patch dimensions exceed 255"; return result; }
            rects[i] = new Rectangle(0, 0, width, height);
            byte[] data = sharedPalette == null ? null : sharedIndexedCache.get(image);
            if (data == null || data.length != width * height) data = new byte[width * height];
            boolean cacheHit = sharedPalette != null && sharedIndexedCache.containsKey(image);
            int n = 0;
            /* Shared indexed frames can be copied directly without repeating color lookup. */
            if (!cacheHit && sharedPalette != null && image.getColorModel() instanceof java.awt.image.IndexColorModel) {
                for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                    int index = image.getRaster().getSample(x, y, 0);
                    if (index < 0 || index >= sharedPalette.length) { result.reason = "PIM palette index is not in shared palette"; return result; }
                    data[n++] = (byte)index;
                }
            } else if (!cacheHit) {
                for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                    int pixel = image.getRGB(x, y);
                    Integer index = colorMap.get(Integer.valueOf(pixel));
                    if (index == null && (pixel >>> 24) == 0) {
                        for (int j = 0; j < palette.size(); j++) if ((palette.get(j).intValue() >>> 24) == 0) index = Integer.valueOf(j);
                    }
                    if (index == null) {
                        if (sharedPalette != null) { result.reason = "PIM palette color is not in shared palette"; return result; }
                        if (palette.size() >= 256) { result.reason = "PIM palette exceeds 256"; return result; }
                        index = Integer.valueOf(palette.size()); palette.add(Integer.valueOf(pixel));
                        colorMap.put(Integer.valueOf(pixel), index);
                    }
                    data[n++] = (byte)index.intValue();
                }
            }
            if (sharedPalette != null && !sharedIndexedCache.containsKey(image)) sharedIndexedCache.put(image,data);
            indexed.add(data);
        }        Rectangle[] bounds = probeLayout?PimLayout.layoutProbe(rects).bounds:PimLayout.layout(rects).bounds;
        result.atlasCount = bounds.length;
        result.idataCrc = new long[bounds.length];
        if (bounds.length < 1 || bounds.length > 4) { result.reason = "PIM atlas count exceeds format"; return result; }
        byte[][][] atlases = new byte[bounds.length][][];
        for (int a = 0; a < bounds.length; a++) atlases[a] = new byte[bounds[a].height][bounds[a].width];
        for (int i = 0; i < images.size(); i++) { int a=(rects[i].x>>14)&3; int x0=rects[i].x&0x3fff; byte[] data=indexed.get(i); for(int y=0;y<rects[i].height;y++) System.arraycopy(data,y*rects[i].width,atlases[a][y+rects[i].y],x0,rects[i].width); }
        result.idataCrc = new long[bounds.length];
        try { savePim(palette,rects,bounds,atlases,result,bestCompress); } catch (IOException ex) { result.reason=ex.getMessage(); return result; }
        result.valid = true; result.reason = "OK"; return result;
    }

    /** 按 PipImage.save(DataOutputStream,true) 的完整 PIM 顺序写入内存流。 */
    private static byte[] savePim(List<Integer> palette,Rectangle[] rects,Rectangle[] bounds,byte[][][] atlases,Result result,boolean bestCompress)throws IOException{
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        DataOutputStream dos=new DataOutputStream(bos);
        dos.writeByte('P'); dos.writeByte('I'); dos.writeByte('M');
        if(palette.size()>256)throw new IOException("PIM palette exceeds 256");
        dos.writeByte(palette.size());
        dos.writeInt(palette.size()); dos.writeByte('P'); dos.writeByte('L'); dos.writeByte('T'); dos.writeByte('E');
        for(int i=0;i<palette.size();i++)dos.writeInt(palette.get(i).intValue());
        ByteArrayOutputStream fib=new ByteArrayOutputStream();
        GZIPOutputStream gz=new GZIPOutputStream(fib); DataOutputStream fd=new DataOutputStream(gz);
        fd.writeByte(rects.length);
        for(int i=0;i<rects.length;i++){fd.writeShort(rects[i].x);fd.writeShort(rects[i].y);fd.writeByte(rects[i].width);fd.writeByte(rects[i].height);}
        fd.flush();fd.close(); byte[] frameInfo=fib.toByteArray();
        if(frameInfo.length>65535)throw new IOException("PIM frame info exceeds 65535");
        dos.writeShort(frameInfo.length); dos.write(frameInfo); dos.writeByte(bounds.length);
        for(int a=0;a<bounds.length;a++){
            ByteArrayOutputStream idata=new ByteArrayOutputStream(); ByteArrayOutputStream pdata=new ByteArrayOutputStream();
            PngFile png=new PngFile(); png.width=bounds[a].width; png.height=bounds[a].height; png.bitDepth=8; png.colorType=3;
            png.palette=new int[palette.size()]; png.transparency=new byte[palette.size()];
            for(int j=0;j<palette.size();j++){png.palette[j]=palette.get(j).intValue()&0xffffff;png.transparency[j]=(byte)(palette.get(j).intValue()>>>24);}
            png.scanlines=new java.util.ArrayList<byte[]>(bounds[a].height);
            for(int y=0;y<bounds[a].height;y++)png.scanlines.add(atlases[a][y]);
            png.writePngSpecial(new DataOutputStream(idata),a==0?new DataOutputStream(pdata):null,bestCompress);
            if(a==0){byte[] pb=pdata.toByteArray();result.pdataLength=pb.length;result.pdataCrc=crc(pb);if(pb.length>65535)throw new IOException("PIM pdata exceeds 65535");dos.writeShort(pb.length);dos.write(pb);}
            byte[] ib=idata.toByteArray();result.idataCrc[a]=crc(ib);if(ib.length>result.maxIdataLength)result.maxIdataLength=ib.length;if(ib.length>65535)throw new IOException("PIM idata exceeds 65535");dos.writeShort(ib.length);dos.write(ib);
        }
        dos.flush(); return bos.toByteArray();
    }
}
