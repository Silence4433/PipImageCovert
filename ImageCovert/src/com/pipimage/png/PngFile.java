package com.pipimage.png;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.jcraft.jzlib.*;

/**
 * This class represent a PNG file. It provides the function to read or write
 * PNG files. Theoretically this class can support all kinds of PNG files, but
 * it only implemented PNG8(type = 3) and PNG32(type = 6) up to now.
 *
 * Please see PNG specification at http://www.w3.org/TR/REC-png-multi.html
 */
public class PngFile {
    public int width;                              // image width
    public int height;                             // image height
    public byte bitDepth;                          // bit depth, only support 1, 2, 4, 8
    public byte colorType;                         // color type, only support 3 or 6
    public byte compressionMethod = (byte)0;       // reserved field
    public byte filterMethod = (byte)0;            // reserved field
    public byte interlaceMethod = (byte)0;         // reserved field
    public int[] palette;                          // palette data in RGB(only for color type 3)
    public byte[] transparency;                    // alpha transparency(only for color type 3)
    public ArrayList<byte[]> scanlines = null;             // pure scanline data
    private Inflater inflater = null;
    private ByteArrayOutputStream tmpData = null;

    public static long headerData = 0x89504E470D0A1A0AL;  // static header

    // write PNG header
    private void writeHeader(DataOutputStream os) throws IOException {
        os.writeLong(headerData);
    }

    // write IHDR chunk, this must be the first trunk.
    private void writeIHDRChunk(DataOutputStream os) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(width);
        dos.writeInt(height);
        dos.writeByte(bitDepth);
        dos.writeByte(colorType);
        dos.writeByte(compressionMethod);
        dos.writeByte(filterMethod);
        dos.writeByte(interlaceMethod);
        dos.close();
        bos.close();

        PngTrunk trunk = new PngTrunk("IHDR", bos.toByteArray());
        trunk.writeTrunk(os);
    }

    // write PLTE chunk(palette data).
    private void writePLTEChunk(DataOutputStream os) throws IOException {
        if (palette == null) {
            return;
        }
        int colors = palette.length;
        byte[] paletteData = new byte[colors * 3];
        int pind = 0;
        for (int i = 0; i < colors; i++) {
            int rgb = palette[i];
            paletteData[pind++] = (byte)(rgb >> 16);
            paletteData[pind++] = (byte)(rgb >> 8);
            paletteData[pind++] = (byte)rgb;
        }

        PngTrunk trunk = new PngTrunk("PLTE", paletteData);
        trunk.writeTrunk(os);
    }

    // write TRNS chunk(alpha transparency)
    private void writeTRNSChunk(DataOutputStream os) throws IOException {
        if (transparency == null) {
            return;
        }
        PngTrunk trunk = new PngTrunk("tRNS", transparency);
        trunk.writeTrunk(os);
    }

    // write scanline data
    private void writeScanline(byte[] scanline, OutputStream out) throws IOException {
        if (colorType == (byte)3) {
            byte sample = 0;
            byte sampledBits = 0;
            for (int i = 0; i < width; i++) {
                sample = (byte)(sample | (scanline[i] << (8 - sampledBits - bitDepth)));
                sampledBits += bitDepth;
                if (sampledBits == 8) {
                    out.write(sample);
                    sample = 0;
                    sampledBits = 0;
                }
            }
            if (sampledBits > 0) {
                out.write(sample);
            }
        } else if (colorType == (byte)6) {
            out.write(scanline);
        }
    }

    // write IDAT chunk. This method uses a tool named by "MiniZip" to compress
    // image data.
    private void writeIDATChunk(DataOutputStream os, boolean bestCompress) throws IOException {
        // Convert scanline data into image data with filter type 0.
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream(10240);
        for (int i = 0; i < scanlines.size(); i++) {
            byte[] scanline = (byte[])scanlines.get(i);
            outBytes.write(0);        // filter method
            writeScanline(scanline, outBytes);
        }

        // Use jzlib to compress data
        byte[] zipdata = null;
        if (bestCompress) {
            for (int wbs = 9; wbs <= 15; wbs++) {
                ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
                ZOutputStream zout = new ZOutputStream(zipBytes, JZlib.Z_BEST_COMPRESSION, wbs);
                zout.write(outBytes.toByteArray());
                zout.close();
                if (zipdata == null || zipBytes.toByteArray().length < zipdata.length) {
                    zipdata = zipBytes.toByteArray();
                }
            }
        } else {
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            ZOutputStream zout = new ZOutputStream(zipBytes, JZlib.Z_BEST_COMPRESSION, 12);
            zout.write(outBytes.toByteArray());
            zout.close();
            if (zipdata == null || zipBytes.toByteArray().length < zipdata.length) {
                zipdata = zipBytes.toByteArray();
            }
        }

        // Write compressed data
        PngTrunk trunk = new PngTrunk("IDAT", zipdata);
        trunk.writeTrunk(os);
    }

    // write IEND chunk. This must be the last chunk.
    private void writeIENDChunk(DataOutputStream os) throws IOException {
        PngTrunk trunk = new PngTrunk("IEND", new byte[0]);
        trunk.writeTrunk(os);
    }

    /**
     * Write optimized PNG file. The file only contains 5 necessary chunks:
     * IHDR, PLTE, TRNS, IDAT and IEND.
     */
    /** Write an indexed PNG using this legacy encoder, preserving the source palette and alpha. */
    public static void writeIndexedPng(File file, java.awt.image.BufferedImage image, boolean bestCompress) throws IOException {
        if (file == null || image == null) throw new NullPointerException();
        java.awt.image.IndexColorModel cm = (java.awt.image.IndexColorModel) image.getColorModel();
        PngFile png = new PngFile();
        png.width = image.getWidth(); png.height = image.getHeight();
        png.bitDepth = (byte)8; png.colorType = (byte)3;
        int colors = cm.getMapSize();
        png.palette = new int[colors]; png.transparency = new byte[colors];
        for (int i = 0; i < colors; i++) {
            png.palette[i] = (cm.getRed(i) << 16) | (cm.getGreen(i) << 8) | cm.getBlue(i);
            png.transparency[i] = (byte)cm.getAlpha(i);
        }
        png.scanlines = new ArrayList<byte[]>(png.height);
        for (int y = 0; y < png.height; y++) {
            byte[] line = new byte[png.width];
            for (int x = 0; x < png.width; x++) line[x] = (byte)image.getRaster().getSample(x, y, 0);
            png.scanlines.add(line);
        }
        DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
        try { png.writePng(os, bestCompress); } finally { os.close(); }
    }
    public void writePng(DataOutputStream os, boolean bestCompress) throws IOException {
        writeHeader(os);
        writeIHDRChunk(os);
        if (colorType == (byte)3) {
            writePLTEChunk(os);
            writeTRNSChunk(os);
        }
        writeIDATChunk(os, bestCompress);
        writeIENDChunk(os);
    }

    /**
     * Write optimized PNG file. The file only contains 5 necessary chunks:
     * IHDR, PLTE, TRNS, IDAT and IEND.
     */
    public void writePngSpecial(DataOutputStream os1, DataOutputStream os2, boolean bestCompress) throws IOException {
        writeHeader(os1);
        writeIHDRChunk(os1);
        if (os2 != null) {
            writePLTEChunk(os2);
            writeTRNSChunk(os2);
        }
        writeIDATChunk(os1, bestCompress);
        writeIENDChunk(os1);
    }

    // read IHDR chunk
    private void readIHDRChunk(DataInputStream dis) throws IOException {
        width = dis.readInt();
        height = dis.readInt();
        bitDepth = dis.readByte();
        colorType = dis.readByte();
        compressionMethod = dis.readByte();
        filterMethod = dis.readByte();
        interlaceMethod = dis.readByte();
    }

    // read PLTE chunk(palette data)
    private void readPLTEChunk(byte[] data) throws IOException {
        int colors = data.length / 3;
        palette = new int[colors];
        for (int i = 0; i < colors; i++) {
            byte r = data[i * 3];
            byte g = data[i * 3 + 1];
            byte b = data[i * 3 + 2];
            palette[i] = ((r & 0xFF) << 16) + ((g & 0xFF) << 8) + ((b & 0xFF));
        }
    }

    // read IDAT chunk. This method also decompress the image data into a
    // temporary byte array stream. Because a PNG file may contains more than
    // one IDAT chunk, those data should be decompressed in whole.
    private void readIDATChunk(byte[] data) throws IOException {
        if (inflater == null) {
            inflater = new Inflater();
            tmpData = new ByteArrayOutputStream(10240);
        }
        try {
            inflater.setInput(data);
            byte[] buf = new byte[256];
            int resultLen = 0;
            while ((resultLen = inflater.inflate(buf)) > 0) {
                tmpData.write(buf, 0, resultLen);
            }
        } catch (Exception e) {
            throw new IOException(e.toString());
        }
    }

    // parse scanline datas. This method only support filter method 0.
    private void parseScanlines(byte[] data) throws IOException {
        scanlines = new ArrayList<byte[]>(height);
        if (colorType == 3) {
            int pixelPerByte = 8 / (int)bitDepth;
            int bytesPerLine = (width + pixelPerByte - 1) / pixelPerByte + 1;
            if (data.length != bytesPerLine * height) {
                throw new IOException("Image data length mismatched!");
            }
            for (int i = 0; i < height; i++) {
                int rowHead = i * bytesPerLine;
                byte filter = data[rowHead];
                if (filter != (byte)0) {
                    throw new IOException("Only filter method 0 is supported!");
                }
                byte[] scanline = new byte[width];
                int xpos = 0;
                for (int j = 0; j < bytesPerLine - 1; j++) {
                    byte sample = data[rowHead + 1 + j];
                    switch (pixelPerByte) {
                    case 1:
                        scanline[xpos++] = sample;
                        break;
                    case 2:
                        scanline[xpos++] = (byte)((sample >> 4) & 0x0F);
                        scanline[xpos++] = (byte)(sample & 0x0F);
                        break;
                    case 4:
                        scanline[xpos++] = (byte)((sample >> 6) & 0x03);
                        scanline[xpos++] = (byte)((sample >> 4) & 0x03);
                        scanline[xpos++] = (byte)((sample >> 2) & 0x03);
                        scanline[xpos++] = (byte)(sample & 0x03);
                        break;
                    case 8:
                        scanline[xpos++] = (byte)((sample >> 7) & 0x01);
                        scanline[xpos++] = (byte)((sample >> 6) & 0x03);
                        scanline[xpos++] = (byte)((sample >> 5) & 0x03);
                        scanline[xpos++] = (byte)((sample >> 4) & 0x01);
                        scanline[xpos++] = (byte)((sample >> 3) & 0x03);
                        scanline[xpos++] = (byte)((sample >> 2) & 0x03);
                        scanline[xpos++] = (byte)((sample >> 1) & 0x01);
                        scanline[xpos++] = (byte)(sample & 0x01);
                        break;
                    }
                }
                scanlines.add(scanline);
            }
        } else if (colorType == 6) {
            if (bitDepth != 8) {
                throw new IOException("Only bitdepth 8 is supported for PNG32!");
            }
            int bytesPerLine = width * 4 + 1;
            if (data.length != bytesPerLine * height) {
                throw new IOException("Image data length mismatched!");
            }
            for (int i = 0; i < height; i++) {
                int rowHead = i * bytesPerLine;
                byte filter = data[rowHead];
                if (filter != (byte)0) {
                    throw new IOException("Only filter method 0 is supported!");
                }
                byte[] scanline = new byte[width * 4];
                System.arraycopy(data, rowHead + 1, scanline, 0, width * 4);
                scanlines.add(scanline);
            }
        } else {
            throw new IOException("Only color type 3 or 6 is supported!");
        }
    }

    /**
     * Read PNG file.
     */
    public void readPng(DataInputStream is) throws IOException {
        long hdr = is.readLong();
        if (hdr != headerData) {
            throw new IOException("Invalid PNG header!");
        }
        while (true) {
            PngTrunk trunk = new PngTrunk();
            try {
                trunk.readTrunk(is);
            } catch (EOFException e) {
                break;
            }
            if (trunk.getName().equals("IHDR")) {
                ByteArrayInputStream bis = new ByteArrayInputStream(trunk.getData());
                DataInputStream dis = new DataInputStream(bis);
                readIHDRChunk(dis);
                if (colorType != (byte)3 && colorType != (byte)6) {
                    throw new IOException("Only color type 3 or 6 is supported!");
                }
                if (bitDepth != 1 && bitDepth != 2 && bitDepth != 4 && bitDepth != 8) {
                    throw new IOException("Bit depth must be 1, 2, 4, or 8!");
                }
                if (colorType == (byte)6 && bitDepth != 8) {
                    throw new IOException("Bit depth must be 8 for PNG32!");
                }
                if (compressionMethod != (byte)0) {
                    throw new IOException("Only compression method 0 is supported!");
                }
                if (interlaceMethod != (byte)0) {
                    throw new IOException("Only interlace method 0 is supported!");
                }
            } else if (trunk.getName().equals("PLTE") && palette == null) {
                readPLTEChunk(trunk.getData());
            } else if (trunk.getName().equals("tRNS") && transparency == null) {
                transparency = trunk.getData();
            } else if (trunk.getName().equals("IDAT")) {
                readIDATChunk(trunk.getData());
            } else if (trunk.getName().equals("IEND")) {
                break;
            } else {
                System.out.println("unknown trunk: " + trunk.getName());
            }
        }
        if (inflater != null) {
            parseScanlines(tmpData.toByteArray());
            inflater = null;
            tmpData = null;
            if (colorType == (byte)6) {
                buildPaletteForPNG32();
            }
        }
    }
    
    /*
     * for PNG32£¬list all used color into palette array for use lately. max 65536.
     */
    private void buildPaletteForPNG32() {
        int[] tmp = new int[width * height];
        Set<Integer> usedColors = new HashSet<Integer>();
        int usedCount = 0;
        for (byte[] scanline : scanlines) {
            for (int i = 0; i < width; i++) {
                byte r = scanline[i * 4];
                byte g = scanline[i * 4 + 1];
                byte b = scanline[i * 4 + 2];
                byte a = scanline[i * 4 + 3];
                int c = ((a << 24) & 0xFF000000) | ((r << 16) & 0xFF0000) | ((g << 8) & 0xFF00) | (b & 0xFF);
                if (!usedColors.contains(c)) {
                    usedColors.add(c);
                    tmp[usedCount++] = c;
                }
            }
        }
        palette = new int[usedCount];
        System.arraycopy(tmp, 0, palette, 0, usedCount);
    }
}
