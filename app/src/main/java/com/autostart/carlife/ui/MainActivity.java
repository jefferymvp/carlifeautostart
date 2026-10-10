package com.autostart.carlife.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.autostart.carlife.R;
import com.autostart.carlife.utils.AppLauncher;
import com.autostart.carlife.utils.ConfigManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_STORAGE_PERMISSION_CODE = 1001;

    private ConfigManager configManager;

    private CheckBox cbAutoStart;
    private CheckBox cbUseFileStorage;
    private TextView tvFileStorageDesc;
    private TextView tvDelayLabel;
    private SeekBar sbDelay;
    private Spinner spTargetApp;
    private EditText etPackageName;
    private Button btnTestLaunch;
    private Button btnSave;

    private List<AppLauncher.CandidateApp> candidateApps = new ArrayList<AppLauncher.CandidateApp>();
    private ArrayAdapter<AppLauncher.CandidateApp> spinnerAdapter;
    private boolean isUpdatingFromSpinner = false;
    private boolean isUpdatingFromEditText = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configManager = new ConfigManager(this);

        initViews();
        initCandidateApps();
        loadSettings();
    }

    private void initViews() {
        cbAutoStart = (CheckBox) findViewById(R.id.cb_autostart);
        cbUseFileStorage = (CheckBox) findViewById(R.id.cb_use_file_storage);
        tvFileStorageDesc = (TextView) findViewById(R.id.tv_file_storage_desc);
        tvDelayLabel = (TextView) findViewById(R.id.tv_delay_label);
        sbDelay = (SeekBar) findViewById(R.id.sb_delay);
        spTargetApp = (Spinner) findViewById(R.id.sp_target_app);
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

        spTargetApp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingFromEditText) return;
                if (position >= 0 && position < candidateApps.size()) {
                    AppLauncher.CandidateApp selected = candidateApps.get(position);
                    isUpdatingFromSpinner = true;
                    if (selected.packageName != null && !selected.packageName.isEmpty()) {
                        etPackageName.setText(selected.packageName);
                    } else {
                        // 选择了最后一项：自定义包名
                        etPackageName.requestFocus();
                    }
                    isUpdatingFromSpinner = false;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        etPackageName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdatingFromSpinner) return;
                String input = s.toString().trim();
                isUpdatingFromEditText = true;
                syncSpinnerSelection(input);
                isUpdatingFromEditText = false;
            }

            @Override
            public void afterTextChanged(Editable s) {}
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

    private void initCandidateApps() {
        candidateApps = AppLauncher.getCandidateApps(this);
        spinnerAdapter = new ArrayAdapter<AppLauncher.CandidateApp>(
                this,
                R.layout.item_app_spinner,
                candidateApps
        );
        spinnerAdapter.setDropDownViewResource(R.layout.item_app_dropdown);
        spTargetApp.setAdapter(spinnerAdapter);
    }

    private void syncSpinnerSelection(String currentPkg) {
        if (candidateApps == null || candidateApps.isEmpty()) return;
        int customIndex = candidateApps.size() - 1;
        int targetIndex = customIndex;

        for (int i = 0; i < candidateApps.size(); i++) {
            AppLauncher.CandidateApp app = candidateApps.get(i);
            if (app.packageName != null && app.packageName.equalsIgnoreCase(currentPkg)) {
                targetIndex = i;
                break;
            }
        }

        if (spTargetApp.getSelectedItemPosition() != targetIndex) {
            spTargetApp.setSelection(targetIndex);
        }
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
        if (configuredPkg == null || configuredPkg.trim().isEmpty()) {
            // 若未配置，优先从已安装候选列表中选用第一个已安装应用
            for (AppLauncher.CandidateApp app : candidateApps) {
                if (app.isInstalled && app.packageName != null && !app.packageName.isEmpty()) {
                    configuredPkg = app.packageName;
                    break;
                }
            }
            if (configuredPkg == null) {
                configuredPkg = ConfigManager.DEFAULT_CARLIFE_VEHICLE_PKG;
            }
        }

        etPackageName.setText(configuredPkg);
        syncSpinnerSelection(configuredPkg);
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
