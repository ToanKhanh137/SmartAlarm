package com.example.smartalarm.ui.common;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartalarm.settings.AppPreferences;

import java.util.Locale;

/**
 * BaseActivity – wrap locale (ngôn ngữ) cho toàn bộ app.
 * Mọi Activity đều extend BaseActivity thay vì AppCompatActivity.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // Áp dụng ngôn ngữ đã lưu trước khi inflate layout
        AppPreferences prefs = AppPreferences.getInstance(newBase);
        String lang = prefs.getLanguage();
        super.attachBaseContext(applyLocale(newBase, lang));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
