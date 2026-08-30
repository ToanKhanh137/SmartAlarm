package com.example.smartalarm.ui.challenge;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.view.View;

import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smartalarm.R;
import com.example.smartalarm.data.database.AppDatabase;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.ui.common.BaseActivity;

import java.util.concurrent.Executors;

/**
 * ShakeChallengeActivity – lắc điện thoại để tắt báo thức.
 * Dùng accelerometer, đếm số lần lắc vượt ngưỡng.
 */
public class ShakeChallengeActivity extends BaseActivity implements SensorEventListener {
    private Handler fallbackHandler = new Handler(Looper.getMainLooper());
    private Runnable fallbackRunnable;


    // Ngưỡng gia tốc để tính là 1 lần lắc (m/s²)
    private static final float SHAKE_THRESHOLD = 20f;
    // Thời gian tối thiểu giữa 2 lần lắc (ms)
    private static final long SHAKE_COOLDOWN_MS = 400;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private int alarmId;
    private int targetCount;  // số lần lắc cần thiết
    private int currentCount = 0;
    private long lastShakeTime = 0;

    private TextView tvInstruction, tvProgress;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fallbackRunnable = () -> {
            Button btnFallback = new Button(this);
            btnFallback.setText("Bỏ qua thử thách");
            btnFallback.setBackgroundColor(android.graphics.Color.RED);
            btnFallback.setTextColor(android.graphics.Color.WHITE);
            btnFallback.setOnClickListener(v -> dismissAlarm());
            
            // Add to root layout
            android.view.ViewGroup root = (android.view.ViewGroup) ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            if (root instanceof android.widget.LinearLayout) {
                root.addView(btnFallback);
            } else if (root instanceof android.widget.RelativeLayout) {
                android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, 
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
                root.addView(btnFallback, params);
            }
        };
        fallbackHandler.postDelayed(fallbackRunnable, 60000); // 60 seconds timeout

        setContentView(R.layout.activity_challenge_shake);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);

        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        progressBar   = findViewById(R.id.progressBar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Load target count từ alarm
        Executors.newSingleThreadExecutor().execute(() -> {
            Alarm alarm = AppDatabase.getInstance(this).alarmDao().getByIdSync(alarmId);
            targetCount = alarm != null ? alarm.getEffectiveCount() : 20;
            if (targetCount <= 0) targetCount = 20;
            runOnUiThread(() -> {
                tvInstruction.setText(getString(R.string.shake_instruction, targetCount));
                progressBar.setMax(targetCount);
                progressBar.setProgress(0);
                tvProgress.setText(getString(R.string.shake_progress, 0, targetCount));
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
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
        if (Math.abs(magnitude) > SHAKE_THRESHOLD
                && (now - lastShakeTime) > SHAKE_COOLDOWN_MS) {
            lastShakeTime = now;
            currentCount++;
            updateUI();
            if (currentCount >= targetCount) {
                dismissAlarm();
            }
        }
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.shake_progress, currentCount, targetCount));
        progressBar.setProgress(currentCount);
    }

    private void dismissAlarm() {
        sensorManager.unregisterListener(this);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_DISMISS);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        sendBroadcast(intent);
        finishAffinity();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onBackPressed() {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fallbackHandler != null && fallbackRunnable != null) {
            fallbackHandler.removeCallbacks(fallbackRunnable);
        }
    }
}
