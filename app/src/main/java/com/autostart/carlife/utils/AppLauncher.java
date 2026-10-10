package com.autostart.carlife.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppLauncher {

    public static final String TAG = "CarLifeAutoStart";

    /**
     * 候选互联应用数据模型
     */
    public static class CandidateApp {
        public final String name;
        public final String packageName;
        public final boolean isInstalled;
        public final String versionName;

        public CandidateApp(String name, String packageName, boolean isInstalled, String versionName) {
            this.name = name;
            this.packageName = packageName;
            this.isInstalled = isInstalled;
            this.versionName = versionName;
        }

        public String getDisplayName() {
            if (packageName == null || packageName.trim().isEmpty()) {
                return name;
            }
            StringBuilder sb = new StringBuilder();
            if (isInstalled) {
                sb.append("【已安装】").append(name);
                if (versionName != null && !versionName.isEmpty()) {
                    sb.append(" v").append(versionName);
                }
            } else {
                sb.append("【未安装】").append(name);
            }
            sb.append(" - ").append(packageName);
            return sb.toString();
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    /**
     * 预设的车载互联方案（支持 CarLife 与 各类 Apple CarPlay/Display/盒子方案）
     */
    private static final String[][] PRESET_APPS = new String[][] {
            {"百度 CarLife (车载定制版)", ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG},
            {"DiPlay (Apple CarPlay 方案)", ConfigManager.PACKAGE_DIPLAY},
            {"百度 CarLife (通用互联版)", ConfigManager.DEFAULT_CARLIFE_GENERAL_PKG},
            {"ZLink CarPlay (车机互联)", "com.zjinnova.zlink"},
            {"AutoKit CarPlay (盒子互联)", "cn.manstep.phonemirrorbox"},
            {"Carlinke 互联 (CarPlay)", "com.carlinke.carplay"},
            {"系统原生 CarPlay", "com.apple.carplay"},
            {"华为 HiCar 智行", "com.huawei.hicar.carlink"}
    };

    /**
     * 获取所有候选应用列表（已安装靠前，未安装靠后，末尾附带自定义选项）
     */
    public static List<CandidateApp> getCandidateApps(Context context) {
        List<CandidateApp> list = new ArrayList<CandidateApp>();
        Set<String> addedPackages = new HashSet<String>();

        // 1. 检查预设应用
        for (String[] preset : PRESET_APPS) {
            String name = preset[0];
            String pkg = preset[1];
            boolean installed = isPackageInstalled(context, pkg);
            String ver = installed ? getPackageVersionName(context, pkg) : null;
            list.add(new CandidateApp(name, pkg, installed, ver));
            addedPackages.add(pkg.toLowerCase());
        }

        // 2. 动态扫描车机中其他可能存在的互联/Display/CarPlay 解决方案
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

            if (resolveInfos != null) {
                for (ResolveInfo ri : resolveInfos) {
                    if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    String pkgLower = pkg.toLowerCase();

                    // 避免重复与自身
                    if (addedPackages.contains(pkgLower) || context.getPackageName().equalsIgnoreCase(pkg)) {
                        continue;
                    }

                    CharSequence labelSeq = ri.loadLabel(pm);
                    String label = (labelSeq != null ? labelSeq.toString() : pkg);
                    String labelLower = label.toLowerCase();

                    // 匹配互联常见关键字
                    if (pkgLower.contains("carplay") || pkgLower.contains("carlife") ||
                        pkgLower.contains("zlink") || pkgLower.contains("autokit") ||
                        pkgLower.contains("hicar") || pkgLower.contains("display") ||
                        pkgLower.contains("mirror") || labelLower.contains("carplay") ||
                        labelLower.contains("carlife") || labelLower.contains("互联") ||
                        labelLower.contains("投屏")) {

                        String ver = getPackageVersionName(context, pkg);
                        list.add(new CandidateApp(label, pkg, true, ver));
                        addedPackages.add(pkgLower);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "动态扫描互联应用出现异常: " + t.getMessage());
        }

        // 3. 排序：已安装的排在前面，未安装的排在后面
        Collections.sort(list, new Comparator<CandidateApp>() {
            @Override
            public int compare(CandidateApp o1, CandidateApp o2) {
                if (o1.isInstalled && !o2.isInstalled) return -1;
                if (!o1.isInstalled && o2.isInstalled) return 1;
                return 0;
            }
        });

        // 4. 结尾追加“自定义包名”选项
        list.add(new CandidateApp("✍️ 自定义包名（手动输入）", "", false, null));

        return list;
    }

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
     * 自动探测车机中安装的 CarLife 包名（保留旧兼容调用）
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

        String pkg = targetPackage.trim();
        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(pkg);

        // 如果找不到标准 LaunchIntent，尝试通过显式组件名启动车载版主 Activity
        if (launchIntent == null && ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG.equals(pkg)) {
            Log.w(TAG, "未获取到标准 LaunchIntent，尝试使用显式组件启动 CarlifeActivity");
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setComponent(new ComponentName(pkg, "com.baidu.carlifevehicle.CarlifeActivity"));
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                Log.i(TAG, "正在启动目标应用: " + pkg);
                context.startActivity(launchIntent);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "调用 startActivity 出现异常: " + e.getMessage(), e);
                return false;
            }
        } else {
            Log.e(TAG, "未找到目标应用或该应用未声明可启动入口: " + pkg);
            return false;
        }
    }
}
