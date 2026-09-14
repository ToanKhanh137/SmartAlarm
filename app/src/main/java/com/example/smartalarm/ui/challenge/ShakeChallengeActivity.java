package com.example.smartalarm.ui.challenge;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;

/**
 * ShakeChallengeActivity – lắc điện thoại để tắt báo thức.
 * Dùng accelerometer, đếm số lần lắc vượt ngưỡng.
 */
public class ShakeChallengeActivity extends ChallengeActivity implements SensorEventListener {

    // Ngưỡng gia tốc để tính là 1 lần lắc (m/s²)
    private static final float SHAKE_THRESHOLD = 20f;
    // Thời gian tối thiểu giữa 2 lần lắc (ms)
    private static final long SHAKE_COOLDOWN_MS = 400;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private int targetCount = 20;
    private int currentCount = 0;
    private long lastShakeTime = 0;

    private TextView tvInstruction, tvProgress;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_shake);

        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        progressBar   = findViewById(R.id.progressBar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        setupChallenge();
    }

    @Override
    protected void onAlarmLoaded(@Nullable Alarm alarm) {
        targetCount = effectiveCount(alarm, 20);
        tvInstruction.setText(getString(R.string.shake_instruction, targetCount));
        progressBar.setMax(targetCount);
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Loại trừ trọng lực (g ≈ 9.8 m/s²)
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

        long now = System.currentTimeMillis();
        if (Math.abs(magnitude) > SHAKE_THRESHOLD && (now - lastShakeTime) > SHAKE_COOLDOWN_MS) {
            lastShakeTime = now;
            currentCount++;
            updateUI();
            if (currentCount >= targetCount) {
                sensorManager.unregisterListener(this);
                dismissAlarm();
            }
        }
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.shake_progress, currentCount, targetCount));
        progressBar.setProgress(currentCount);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
