package com.autostart.carlife.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.autostart.carlife.utils.AppLauncher;
import com.autostart.carlife.utils.ConfigManager;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "CarLifeAutoStart";
    private static volatile boolean isExecuting = false;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "BootReceiver 收到系统广播: " + action);

        final ConfigManager configManager = new ConfigManager(context);
        if (!configManager.isAutoStartEnabled()) {
            Log.i(TAG, "开机自启开关为关闭状态，忽略该广播。");
            return;
        }

        if (isExecuting) {
            Log.w(TAG, "启动调度已在排队中，忽略重复事件。");
            return;
        }

        final int delaySeconds = configManager.getDelaySeconds();
        final String targetPkg = configManager.getTargetPackage();

        Log.i(TAG, "开机广播捕获成功，将在 " + delaySeconds + " 秒后拉起: " + targetPkg);
        isExecuting = true;

        // 使用 Android 官方标准的 goAsync() 机制保持广播接收器异步存活
        // 兼容 Android 4.4 到 Android 14，彻底规避 Android 8.0+ 的后台服务启动限制
        final PendingResult pendingResult = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "延时倒计时结束，执行拉起...");
                    boolean success = AppLauncher.launchApp(context, targetPkg);
                    Log.i(TAG, "拉起结果: " + success);
                } catch (Exception e) {
                    Log.e(TAG, "拉起异常: " + e.getMessage(), e);
                } finally {
                    isExecuting = false;
                    if (pendingResult != null) {
                        try {
                            pendingResult.finish();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }, delaySeconds * 1000L);
    }
}
