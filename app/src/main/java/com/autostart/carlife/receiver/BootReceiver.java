package com.autostart.carlife.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.autostart.carlife.service.LaunchService;
import com.autostart.carlife.utils.ConfigManager;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "CarLifeAutoStart";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "BootReceiver 收到系统广播: " + action);

        ConfigManager configManager = new ConfigManager(context);
        if (!configManager.isAutoStartEnabled()) {
            Log.i(TAG, "开机自启开关为关闭状态，忽略该广播。");
            return;
        }

        // 启动后台延时服务
        try {
            Intent serviceIntent = new Intent(context, LaunchService.class);
            context.startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "启动 LaunchService 失败: " + e.getMessage(), e);
        }
    }
}
