package com.example.smartalarm.ui.common;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.smartalarm.settings.AppPreferences;

import java.util.Locale;

/**
 * BaseActivity – áp dụng ngôn ngữ và theme cho toàn bộ app.
 * Mọi Activity đều extend BaseActivity thay vì AppCompatActivity.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        AppPreferences prefs = AppPreferences.getInstance(newBase);
        String lang = prefs.getLanguage();
        super.attachBaseContext(applyLocale(newBase, lang));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Fix BUG-09: Chặn người dùng dùng app khi đang reo báo thức
        if (com.example.smartalarm.service.AlarmRingingService.isRinging 
            && !(this instanceof com.example.smartalarm.ui.ring.RingActivity)
            && !(this instanceof com.example.smartalarm.ui.challenge.MathChallengeActivity)
            && !(this instanceof com.example.smartalarm.ui.challenge.ShakeChallengeActivity)
            && !(this instanceof com.example.smartalarm.ui.challenge.SquatChallengeActivity)
            && !(this instanceof com.example.smartalarm.ui.challenge.StepChallengeActivity)
            && !(this instanceof com.example.smartalarm.ui.challenge.QrChallengeActivity)) {
            
            android.content.Intent intent = new android.content.Intent(this, com.example.smartalarm.ui.ring.RingActivity.class);
            intent.putExtra(com.example.smartalarm.service.AlarmReceiver.EXTRA_ALARM_ID, 
                com.example.smartalarm.service.AlarmRingingService.ringingAlarmId);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }

    /** Áp dụng theme từ preferences trước khi inflate layout. */
    public void applyTheme() {
        AppPreferences prefs = AppPreferences.getInstance(this);
        String theme = prefs.getTheme();
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

    /** Trả về Context với Locale đã được set. */
    public static Context applyLocale(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
