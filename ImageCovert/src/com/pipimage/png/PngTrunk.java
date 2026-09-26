package com.pipimage.png;

import java.io.*;
import java.util.zip.CRC32;

/**
 * This class represents a chunk in PNG file. A trunk consists of a 4-bytes data
 * length, a 4-bytes trunk name, trunk data and a 4-bytes CRC.
 */
public class PngTrunk {
    private String name;
    private byte[] data;
    private CRC32 crc = new CRC32();

    /**
     * Create an empty trunk.
     */
    public PngTrunk() {}

    /**
     * Create a trunk with specified name and data.
     */
    public PngTrunk(String n, byte[] d) {
        name = n;
        data = d;
    }

    /**
     * Get name of trunk.
     */
    public String getName() {
        return name;
    }

    /**
     * Get trunk data.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Write trunk to a stream.
     */
    public void writeTrunk(DataOutputStream os) throws IOException {
        os.writeInt(data.length);
        crc.reset();
        byte[] nameData = name.getBytes("ISO-8859-1");
        crc.update(nameData);
        os.write(nameData);
        crc.update(data);
        os.write(data);
        os.writeInt((int)crc.getValue());
    }

    /**
     * Read trunk. This method also checks the CRC.
     */
    public void readTrunk(DataInputStream is) throws IOException {
        int len = is.readInt();
        crc.reset();
        byte[] nameData = new byte[4];
        is.read(nameData);
        name = new String(nameData, "ISO-8859-1");
        crc.update(nameData);
        data = new byte[len];
        is.readFully(data);
        crc.update(data);
        int readCRC = is.readInt();
        if ((int)crc.getValue() != readCRC) {
            throw new IOException("Bad trunk CRC!");
        }
    }
}
