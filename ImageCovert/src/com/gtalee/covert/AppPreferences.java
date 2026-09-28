package com.gtalee.covert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

/**
 * ImageCovert 统一配置管理。所有功能模块共用文档目录下的同一个配置文件。
 * 配置文件：%USERPROFILE%\Documents\ImageCovert\config.properties
 * 文件编码：GBK。
 */
public final class AppPreferences {
    private static final String ENCODING = "GBK";
    private static final String CONFIG_DIR = "ImageCovert";
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private AppPreferences() { }

    public static synchronized String get(String key, String fallback) {
        load();
        String value = VALUES.getProperty(key);
        return value == null ? fallback : value;
    }

    public static synchronized void put(String key, String value) {
        load();
        if (value == null) VALUES.remove(key); else VALUES.setProperty(key, value);
        save();
    }

    public static File getConfigFile() {
        String home = System.getProperty("user.home", ".");
        return new File(new File(new File(home), "Documents"), CONFIG_DIR + File.separator + CONFIG_FILE);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        File file = getConfigFile();
        if (!file.isFile()) return;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), ENCODING);
            VALUES.load(reader);
        } catch (IOException ignored) {
            VALUES.clear();
        } finally { if (reader != null) try { reader.close(); } catch (IOException ignored) { } }
    }

    private static void save() {
        File file = getConfigFile();
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) return;
        File temp = new File(parent, CONFIG_FILE + ".tmp");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(temp), ENCODING);
            VALUES.store(writer, "ImageCovert 统一配置");
        } catch (IOException ignored) {
            if (writer != null) try { writer.close(); } catch (IOException ignored2) { }
            return;
        } finally { if (writer != null) try { writer.close(); } catch (IOException ignored) { } }
        if (file.exists() && !file.delete()) return;
        temp.renameTo(file);
    }
}
