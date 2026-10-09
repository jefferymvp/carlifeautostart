package com.autostart.carlife.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.autostart.carlife.utils.AppLauncher;
import com.autostart.carlife.utils.ConfigManager;

public class LaunchService extends Service {

    private static final String TAG = "CarLifeAutoStart";
    private static volatile boolean isLaunching = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConfigManager configManager;

    @Override
    public void onCreate() {
        super.onCreate();
        configManager = new ConfigManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!configManager.isAutoStartEnabled()) {
            Log.i(TAG, "已接收到启动调度，但用户已关闭开机自启开关，跳过启动。");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (isLaunching) {
            Log.w(TAG, "自启动任务已在进行中，忽略重复触发广播。");
            return START_NOT_STICKY;
        }

        final int delaySeconds = configManager.getDelaySeconds();
        final String targetPkg = configManager.getTargetPackage();

        isLaunching = true;
        Log.i(TAG, "已捕获开机事件，将在 " + delaySeconds + " 秒后拉起应用: " + targetPkg);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "延时倒计时结束，开始拉起: " + targetPkg);
                    boolean success = AppLauncher.launchApp(LaunchService.this, targetPkg);
                    if (success) {
                        Log.i(TAG, "成功触发拉起指令！");
                    } else {
                        Log.e(TAG, "拉起应用失败，请检查包名是否正确或应用是否已安装。");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "执行拉起过程中抛出异常: " + e.getMessage(), e);
                } finally {
                    isLaunching = false;
                    // 启动完毕后，主动销毁服务，不占用车机有限内存
                    Log.i(TAG, "任务完成，退出 LaunchService 释放系统资源。");
                    stopSelf();
                }
            }
        }, delaySeconds * 1000L);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        isLaunching = false;
        super.onDestroy();
    }
}
