package com.example.smartalarm.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.smartalarm.R;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.common.BaseActivity;
import com.example.smartalarm.ui.main.MainActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends BaseActivity {

    private AppPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = AppPreferences.getInstance(this);

        // Back
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        // Snooze
        SwitchMaterial switchSnooze = findViewById(R.id.switchSnooze);
        switchSnooze.setChecked(prefs.isSnoozeEnabled());
        switchSnooze.setOnCheckedChangeListener((btn, checked) ->
                prefs.setSnoozeEnabled(checked));

        // QR Manager
        LinearLayout rowQr = findViewById(R.id.rowQrManager);
        if (rowQr != null) {
            rowQr.setOnClickListener(v ->
                    startActivity(new Intent(this, com.example.smartalarm.ui.qr.QrGeneratorActivity.class)));
        }

        // Theme buttons
        setupThemeButtons();

        // Language buttons
        setupLanguageButtons();
    }

    // ===== THEME =====

    private void setupThemeButtons() {
        LinearLayout btnDark   = findViewById(R.id.btnThemeDark);
        LinearLayout btnLight  = findViewById(R.id.btnThemeLight);
        LinearLayout btnSystem = findViewById(R.id.btnThemeSystem);

        refreshThemeUI();

        btnDark.setOnClickListener(v   -> applyThemeChoice(AppPreferences.THEME_DARK));
        btnLight.setOnClickListener(v  -> applyThemeChoice(AppPreferences.THEME_LIGHT));
        btnSystem.setOnClickListener(v -> applyThemeChoice(AppPreferences.THEME_SYSTEM));
    }

    private void applyThemeChoice(String theme) {
        prefs.setTheme(theme);
        refreshThemeUI();
        switch (theme) {
            case AppPreferences.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case AppPreferences.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
        // Restart toàn bộ app để áp dụng ngay
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void refreshThemeUI() {
        String current = prefs.getTheme();
        setButtonSelected(R.id.btnThemeDark,   AppPreferences.THEME_DARK.equals(current));
        setButtonSelected(R.id.btnThemeLight,  AppPreferences.THEME_LIGHT.equals(current));
        setButtonSelected(R.id.btnThemeSystem, AppPreferences.THEME_SYSTEM.equals(current));
    }

    // ===== LANGUAGE =====

    private void setupLanguageButtons() {
        LinearLayout btnVi = findViewById(R.id.btnLangVi);
        LinearLayout btnEn = findViewById(R.id.btnLangEn);

        refreshLangUI();

        btnVi.setOnClickListener(v -> applyLanguageChoice("vi"));
        btnEn.setOnClickListener(v -> applyLanguageChoice("en"));
    }

    private void applyLanguageChoice(String lang) {
        prefs.setLanguage(lang);
        // Restart toàn bộ app để áp dụng ngôn ngữ mới
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void refreshLangUI() {
        String current = prefs.getLanguage();
        setButtonSelected(R.id.btnLangVi, "vi".equals(current));
        setButtonSelected(R.id.btnLangEn, "en".equals(current));
    }

    // ===== HELPER =====

    private void setButtonSelected(int viewId, boolean selected) {
        android.view.View v = findViewById(viewId);
        if (v != null) {
            v.setBackgroundResource(selected
                    ? R.drawable.bg_challenge_card_selected
                    : R.drawable.bg_challenge_card);
        }
    }
}
