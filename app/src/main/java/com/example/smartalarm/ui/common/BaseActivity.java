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
