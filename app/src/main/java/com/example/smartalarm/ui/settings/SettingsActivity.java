package com.example.smartalarm.ui.settings;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.smartalarm.R;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.common.BaseActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        AppPreferences prefs = AppPreferences.getInstance(this);

        // Snooze toggle
        SwitchMaterial switchSnooze = findViewById(R.id.switchSnooze);
        switchSnooze.setChecked(prefs.isSnoozeEnabled());
        switchSnooze.setOnCheckedChangeListener((btn, checked) ->
                prefs.setSnoozeEnabled(checked));

        // QR Manager
        LinearLayout rowQr = findViewById(R.id.rowQrManager);
        if (rowQr != null) {
            rowQr.setOnClickListener(v -> {
                startActivity(new android.content.Intent(
                        this, com.example.smartalarm.ui.qr.QrGeneratorActivity.class));
            });
        }

        // Back
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());
    }
}
