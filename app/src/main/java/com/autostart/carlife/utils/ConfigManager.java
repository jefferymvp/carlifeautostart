package com.autostart.carlife.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Properties;

public class ConfigManager {

    private static final String TAG = "CarLifeAutoStart";
    private static final String PREF_NAME = "carlife_autostart_config";
    private static final String CONFIG_FILE_NAME = "carlife_autostart_config.ini";

    public static final String KEY_AUTO_START_ENABLED = "auto_start_enabled";
    public static final String KEY_DELAY_SECONDS = "delay_seconds";
    public static final String KEY_TARGET_PACKAGE = "target_package";
    public static final String KEY_USE_FILE_STORAGE = "use_file_storage";

    public static final String DEFAULT_CARLIFE_VEHICLE_PKG = "com.baidu.carlifevehicle";
    public static final String DEFAULT_CARLIFE_GENERAL_PKG = "com.baidu.carlife";
    public static final String PACKAGE_DIPLAY = "com.shihab.diplay";


    public static final int DEFAULT_DELAY_SECONDS = 30;
    public static final boolean DEFAULT_AUTO_START_ENABLED = true;
    public static final boolean DEFAULT_USE_FILE_STORAGE = false;

    private final Context context;
    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        this.context = context.getApplicationContext();

        // 统一存储上下文：Android 7.0+ 统一使用设备保护存储，避免开机广播(DE)与界面(CE)存储隔离
        Context safeContext = this.context;
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                if (!safeContext.isDeviceProtectedStorage()) {
                    safeContext = safeContext.createDeviceProtectedStorageContext();
                }
            } catch (Throwable t) {
                safeContext = this.context;
            }
        }
        this.prefs = safeContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoStartEnabled() {
        if (isUseFileStorage()) {
            Boolean val = getBooleanFromFile(KEY_AUTO_START_ENABLED);
            if (val != null) {
                return val;
            }
        }
        return prefs.getBoolean(KEY_AUTO_START_ENABLED, DEFAULT_AUTO_START_ENABLED);
    }

    public void setAutoStartEnabled(boolean enabled) {
        // 使用 commit() 保证同步落盘，避免车机拔钥匙掉电丢数据
        prefs.edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).commit();
        if (isUseFileStorage()) {
            syncToFile();
        }
    }

    public int getDelaySeconds() {
        if (isUseFileStorage()) {
            Integer val = getIntFromFile(KEY_DELAY_SECONDS);
            if (val != null) {
                return val;
            }
        }
        return prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS);
    }

    public void setDelaySeconds(int seconds) {
        prefs.edit().putInt(KEY_DELAY_SECONDS, seconds).commit();
        if (isUseFileStorage()) {
            syncToFile();
        }
    }

    public String getTargetPackage() {
        if (isUseFileStorage()) {
            String val = getStringFromFile(KEY_TARGET_PACKAGE);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_CARLIFE_VEHICLE_PKG);
    }

    public void setTargetPackage(String packageName) {
        if (packageName != null && !packageName.trim().isEmpty()) {
            prefs.edit().putString(KEY_TARGET_PACKAGE, packageName.trim()).commit();
            if (isUseFileStorage()) {
                syncToFile();
            }
        }
    }

    public boolean isUseFileStorage() {
        // 先检查配置标记，若标记为开启或外部磁盘已有配置文件，则判定为启用
        boolean enabled = prefs.getBoolean(KEY_USE_FILE_STORAGE, DEFAULT_USE_FILE_STORAGE);
        if (!enabled) {
            // 若 SharedPreferences 异常重置，但外部文件存在且有效，自动容灾激活
            File file = getPrimaryConfigFile();
            if (file != null && file.exists() && file.length() > 0) {
                return true;
            }
        }
        return enabled;
    }

    public void setUseFileStorage(boolean use) {
        prefs.edit().putBoolean(KEY_USE_FILE_STORAGE, use).commit();
        if (use) {
            syncToFile();
        }
    }

    /**
     * 保存所有设置并执行硬件物理落盘
     */
    public void saveAll(boolean autoStart, int delaySeconds, String targetPackage, boolean useFileStorage) {
        prefs.edit()
                .putBoolean(KEY_AUTO_START_ENABLED, autoStart)
                .putInt(KEY_DELAY_SECONDS, delaySeconds)
                .putString(KEY_TARGET_PACKAGE, (targetPackage != null ? targetPackage.trim() : DEFAULT_CARLIFE_VEHICLE_PKG))
                .putBoolean(KEY_USE_FILE_STORAGE, useFileStorage)
                .commit(); // 同步强制写盘

        if (useFileStorage) {
            syncToFile();
        }
    }

    // ================= 文件存储与容灾核心逻辑 =================

    /**
     * 获取首选外部配置文件对象 (/sdcard/carlife_autostart_config.ini)
     */
    public File getPrimaryConfigFile() {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            if (externalDir != null) {
                return new File(externalDir, CONFIG_FILE_NAME);
            }
        } catch (Throwable t) {
            Log.w(TAG, "获取外部存储根目录失败: " + t.getMessage());
        }
        return getFallbackConfigFile();
    }

    /**
     * 获取免权限应用专属目录/内部闪存兜底文件
     */
    public File getFallbackConfigFile() {
        try {
            File appExt = context.getExternalFilesDir(null);
            if (appExt != null) {
                return new File(appExt, CONFIG_FILE_NAME);
            }
        } catch (Throwable ignored) {}
        return new File(context.getFilesDir(), CONFIG_FILE_NAME);
    }

    /**
     * 同步持久化写入配置文件，并调用系统 fsync 强制固化到 Flash 物理芯片
     */
    public synchronized boolean syncToFile() {
        File primary = getPrimaryConfigFile();
        File fallback = getFallbackConfigFile();

        boolean s1 = writePropertiesToFile(primary);
        boolean s2 = writePropertiesToFile(fallback);
        return s1 || s2;
    }

    private boolean writePropertiesToFile(File targetFile) {
        if (targetFile == null) return false;
        FileOutputStream fos = null;
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Properties props = new Properties();
            props.setProperty(KEY_AUTO_START_ENABLED, String.valueOf(prefs.getBoolean(KEY_AUTO_START_ENABLED, DEFAULT_AUTO_START_ENABLED)));
            props.setProperty(KEY_DELAY_SECONDS, String.valueOf(prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS)));
            props.setProperty(KEY_TARGET_PACKAGE, prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_CARLIFE_VEHICLE_PKG));
            props.setProperty(KEY_USE_FILE_STORAGE, "true");

            fos = new FileOutputStream(targetFile);
            props.store(new OutputStreamWriter(fos, "UTF-8"), "CarLife AutoStarter Persistent Configuration");
            
            // 关键：强制 Linux 内核将缓存脏页刷入硬件闪存，防车机拔钥匙直接掉电
            fos.getFD().sync();
            Log.i(TAG, "配置已成功物理落盘至: " + targetFile.getAbsolutePath());
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "写入配置文件异常 [" + targetFile.getAbsolutePath() + "]: " + e.getMessage());
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }
    }

    private Properties loadPropertiesFromFile() {
        Properties props = new Properties();
        File primary = getPrimaryConfigFile();
        if (readProperties(primary, props)) {
            return props;
        }
        File fallback = getFallbackConfigFile();
        if (readProperties(fallback, props)) {
            return props;
        }
        return null;
    }

    private boolean readProperties(File file, Properties props) {
        if (file == null || !file.exists() || file.length() == 0) return false;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            props.load(new InputStreamReader(fis, "UTF-8"));
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "读取配置文件异常 [" + file.getAbsolutePath() + "]: " + e.getMessage());
            return false;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
        }
    }

    private String getStringFromFile(String key) {
        Properties p = loadPropertiesFromFile();
        return (p != null ? p.getProperty(key) : null);
    }

    private Integer getIntFromFile(String key) {
        Properties p = loadPropertiesFromFile();
        if (p != null && p.containsKey(key)) {
            try {
                return Integer.parseInt(p.getProperty(key).trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Boolean getBooleanFromFile(String key) {
        Properties p = loadPropertiesFromFile();
        if (p != null && p.containsKey(key)) {
            try {
                return Boolean.parseBoolean(p.getProperty(key).trim());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
