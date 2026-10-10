package com.autostart.carlife.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.autostart.carlife.R;
import com.autostart.carlife.utils.AppLauncher;
import com.autostart.carlife.utils.ConfigManager;

import java.io.File;

public class MainActivity extends Activity {

    private static final int REQUEST_STORAGE_PERMISSION_CODE = 1001;

    private ConfigManager configManager;

    private TextView tvStatus;
    private TextView tvPackageInfo;
    private CheckBox cbAutoStart;
    private CheckBox cbUseFileStorage;
    private TextView tvFileStorageDesc;
    private TextView tvDelayLabel;
    private SeekBar sbDelay;
    private EditText etPackageName;
    private Button btnTestLaunch;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configManager = new ConfigManager(this);

        initViews();
        loadSettings();
        checkAndDisplayStatus();
    }

    private void initViews() {
        tvStatus = (TextView) findViewById(R.id.tv_carlife_status);
        tvPackageInfo = (TextView) findViewById(R.id.tv_package_info);
        cbAutoStart = (CheckBox) findViewById(R.id.cb_autostart);
        cbUseFileStorage = (CheckBox) findViewById(R.id.cb_use_file_storage);
        tvFileStorageDesc = (TextView) findViewById(R.id.tv_file_storage_desc);
        tvDelayLabel = (TextView) findViewById(R.id.tv_delay_label);
        sbDelay = (SeekBar) findViewById(R.id.sb_delay);
        etPackageName = (EditText) findViewById(R.id.et_package_name);
        btnTestLaunch = (Button) findViewById(R.id.btn_test_launch);
        btnSave = (Button) findViewById(R.id.btn_save);

        sbDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDelayLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cbUseFileStorage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateFileStorageDesc(isChecked);
                if (isChecked && Build.VERSION.SDK_INT >= 23) {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_STORAGE_PERMISSION_CODE);
                    }
                }
            }
        });

        btnTestLaunch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 测试前自动先静默保存一次，防车友以为测试过就不用点保存
                saveSettings();

                String targetPkg = etPackageName.getText().toString().trim();
                Toast.makeText(MainActivity.this, R.string.toast_launch_success, Toast.LENGTH_SHORT).show();
                boolean success = AppLauncher.launchApp(MainActivity.this, targetPkg);
                if (!success) {
                    Toast.makeText(MainActivity.this, R.string.toast_launch_failed, Toast.LENGTH_LONG).show();
                }
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                Toast.makeText(MainActivity.this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSettings() {
        cbAutoStart.setChecked(configManager.isAutoStartEnabled());
        boolean useFileStorage = configManager.isUseFileStorage();
        cbUseFileStorage.setChecked(useFileStorage);
        updateFileStorageDesc(useFileStorage);

        int delay = configManager.getDelaySeconds();
        sbDelay.setProgress(delay);
        updateDelayLabel(delay);

        String configuredPkg = configManager.getTargetPackage();
        etPackageName.setText(configuredPkg);
    }

    private void updateDelayLabel(int seconds) {
        tvDelayLabel.setText("开机延时启动时间: " + seconds + " 秒");
    }

    private void updateFileStorageDesc(boolean enabled) {
        if (enabled) {
            File file = configManager.getPrimaryConfigFile();
            String path = (file != null ? file.getAbsolutePath() : "/sdcard/carlife_autostart_config.ini");
            tvFileStorageDesc.setText("已启用文件优先存储，配置将硬件刷盘固化至:\n" + path);
            tvFileStorageDesc.setTextColor(getResources().getColor(R.color.accent_green));
        } else {
            tvFileStorageDesc.setText(R.string.desc_file_storage);
            tvFileStorageDesc.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void checkAndDisplayStatus() {
        String detectedPkg = AppLauncher.detectInstalledCarLifePackage(this);
        if (detectedPkg != null) {
            String versionName = AppLauncher.getPackageVersionName(this, detectedPkg);
            String desc = (ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG.equals(detectedPkg) ? "车载定制版" : "通用互联版");
            if (versionName != null) {
                desc += " v" + versionName;
            }
            tvStatus.setText(String.format(getString(R.string.status_detected), desc));
            tvPackageInfo.setText("已识别包名: " + detectedPkg);

            // 若当前输入框为空或未配置，自动填入检测到的包名
            if (etPackageName.getText().toString().trim().isEmpty()) {
                etPackageName.setText(detectedPkg);
            }
        } else {
            tvStatus.setText(R.string.status_not_found);
            tvStatus.setTextColor(getResources().getColor(R.color.accent_red));
            tvPackageInfo.setText("当前车机未检测到官方 CarLife 安装包");
        }
    }

    private void saveSettings() {
        boolean autoStart = cbAutoStart.isChecked();
        int delay = sbDelay.getProgress();
        String pkg = etPackageName.getText().toString().trim();
        boolean useFile = cbUseFileStorage.isChecked();

        configManager.saveAll(autoStart, delay, pkg, useFile);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 界面切入后台时自动静默保存，防意外退出丢数据
        saveSettings();
    }
}
