package com.autostart.carlife.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigManager {

    private static final String PREF_NAME = "carlife_autostart_config";

    public static final String KEY_AUTO_START_ENABLED = "auto_start_enabled";
    public static final String KEY_DELAY_SECONDS = "delay_seconds";
    public static final String KEY_TARGET_PACKAGE = "target_package";

    public static final String DEFAULT_CARLIFE_VEHICLE_PKG = "com.baidu.carlifevehicle";
    public static final String DEFAULT_CARLIFE_GENERAL_PKG = "com.baidu.carlife";

    public static final int DEFAULT_DELAY_SECONDS = 8;
    public static final boolean DEFAULT_AUTO_START_ENABLED = true;

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoStartEnabled() {
        return prefs.getBoolean(KEY_AUTO_START_ENABLED, DEFAULT_AUTO_START_ENABLED);
    }

    public void setAutoStartEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).apply();
    }

    public int getDelaySeconds() {
        return prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS);
    }

    public void setDelaySeconds(int seconds) {
        prefs.edit().putInt(KEY_DELAY_SECONDS, seconds).apply();
    }

    public String getTargetPackage() {
        return prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_CARLIFE_VEHICLE_PKG);
    }

    public void setTargetPackage(String packageName) {
        if (packageName != null && !packageName.trim().isEmpty()) {
            prefs.edit().putString(KEY_TARGET_PACKAGE, packageName.trim()).apply();
        }
    }
}
