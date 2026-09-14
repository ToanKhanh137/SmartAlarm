package com.example.smartalarm.ui.common;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.settings.LocaleHelper;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.service.AlarmRingingService;
import com.example.smartalarm.ui.ring.RingActivity;

/**
 * BaseActivity – áp dụng ngôn ngữ và theme cho toàn bộ app.
 * Mọi Activity đều extend BaseActivity thay vì AppCompatActivity.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // Night mode phải được set TRƯỚC khi tạo config context, nếu không uiMode trong
        // config sẽ là của hệ thống → màu chữ và màu nền lấy từ hai bộ resource khác nhau.
        applyNightMode(newBase);
        super.attachBaseContext(LocaleHelper.wrapWithTheme(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyNightMode(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AlarmRingingService.isRinging && !isAlarmScreen()) {
            returnToRingScreen();
        }
    }

    /** Màn hình được phép hiện khi báo thức đang reo (ring + các challenge). */
    protected boolean isAlarmScreen() {
        return false;
    }

    private void returnToRingScreen() {
        Intent intent = new Intent(this, RingActivity.class);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, AlarmRingingService.ringingAlarmId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private static void applyNightMode(Context context) {
        String theme = AppPreferences.getInstance(context).getTheme();
        switch (theme) {
            case AppPreferences.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case AppPreferences.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
