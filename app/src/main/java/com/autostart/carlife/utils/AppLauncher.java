package com.autostart.carlife.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

public class AppLauncher {

    public static final String TAG = "CarLifeAutoStart";

    /**
     * 判断指定的包名是否安装
     */
    public static boolean isPackageInstalled(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName.trim(), PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取安装应用的版本名称
     */
    public static String getPackageVersionName(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * 自动探测车机中安装的 CarLife 包名
     */
    public static String detectInstalledCarLifePackage(Context context) {
        if (isPackageInstalled(context, ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG)) {
            return ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG;
        }
        if (isPackageInstalled(context, ConfigManager.DEFAULT_CARLIFE_GENERAL_PKG)) {
            return ConfigManager.DEFAULT_CARLIFE_GENERAL_PKG;
        }
        return null;
    }

    /**
     * 核心启动逻辑：拉起目标应用到前台
     */
    public static boolean launchApp(Context context, String targetPackage) {
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            Log.e(TAG, "启动失败：未指定目标包名");
            return false;
        }

        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(targetPackage.trim());

        // 如果找不到标准 LaunchIntent，尝试通过显式组件名启动车载版主 Activity
        if (launchIntent == null && ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG.equals(targetPackage.trim())) {
            Log.w(TAG, "未获取到标准 LaunchIntent，尝试使用显式组件启动 CarlifeActivity");
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setComponent(new ComponentName(targetPackage.trim(), "com.baidu.carlifevehicle.CarlifeActivity"));
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                Log.i(TAG, "正在启动目标应用: " + targetPackage);
                context.startActivity(launchIntent);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "调用 startActivity 出现异常: " + e.getMessage(), e);
                return false;
            }
        } else {
            Log.e(TAG, "未找到目标应用或该应用未声明可启动入口: " + targetPackage);
            return false;
        }
    }
}
