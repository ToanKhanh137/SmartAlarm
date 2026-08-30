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
 * SquatChallengeActivity – squat để tắt báo thức.
 *
 * Nguyên lý: User cầm điện thoại trên tay.
 * Khi ngồi xuống: trục Y của accelerometer tăng mạnh (gia tốc lên).
 * Khi đứng lên: trục Y giảm mạnh.
 * Detect một "nhịp" lên-xuống = 1 squat.
 *
 * State machine: IDLE → GOING_DOWN → GOING_UP → counted → IDLE
 */
public class SquatChallengeActivity extends BaseActivity implements SensorEventListener {
    private Handler fallbackHandler = new Handler(Looper.getMainLooper());
    private Runnable fallbackRunnable;


    // Ngưỡng phát hiện chuyển động đứng ngồi (m/s²)
    private static final float DOWN_THRESHOLD  = -4f;  // xuống (Y giảm)
    private static final float UP_THRESHOLD    =  4f;  // lên (Y tăng)
    private static final long  COOLDOWN_MS     = 600;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private int alarmId;
    private int targetCount;
    private int squatCount = 0;
    private boolean waitingForUp = false;
    private long lastSquatTime = 0;

    // Low-pass filter để loại nhiễu
    private float filteredY = 0;
    private static final float ALPHA = 0.3f;

    private TextView tvInstruction, tvProgress, tvHint;
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

        setContentView(R.layout.activity_challenge_squat);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        tvHint        = findViewById(R.id.tvHint);
        progressBar   = findViewById(R.id.progressBar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        Executors.newSingleThreadExecutor().execute(() -> {
            Alarm alarm = AppDatabase.getInstance(this).alarmDao().getByIdSync(alarmId);
            targetCount = alarm != null ? alarm.getEffectiveCount() : 10;
            if (targetCount <= 0) targetCount = 10;
            runOnUiThread(() -> {
                tvInstruction.setText(getString(R.string.squat_instruction, targetCount));
                tvHint.setText(getString(R.string.squat_hint));
                progressBar.setMax(targetCount);
                updateUI();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float rawX = event.values[0];
        float rawY = event.values[1] - SensorManager.GRAVITY_EARTH;
        float rawZ = event.values[2];

        // Fix BUG-05: Lọc bỏ nhiễu nếu người dùng lắc ngang (X) hoặc lắc tới lui (Z) quá mạnh
        if (Math.abs(rawX) > 3.0f || Math.abs(rawZ) > 3.0f) {
            return; // Không phải chuyển động dọc (squat)
        }

        // Low-pass filter trên trục Y (lên/xuống)
        filteredY = ALPHA * rawY + (1 - ALPHA) * filteredY;

        long now = System.currentTimeMillis();
        if (now - lastSquatTime < COOLDOWN_MS) return;

        if (!waitingForUp && filteredY < DOWN_THRESHOLD) {
            // Bắt đầu đi xuống
            waitingForUp = true;
        } else if (waitingForUp && filteredY > UP_THRESHOLD) {
            // Đã đứng lên → đếm 1 squat
            waitingForUp = false;
            lastSquatTime = now;
            squatCount++;
            runOnUiThread(() -> {
                updateUI();
                Toast.makeText(this,
                        getString(R.string.squat_detected, squatCount),
                        Toast.LENGTH_SHORT).show();
                if (squatCount >= targetCount) dismissAlarm();
            });
        }
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.squat_progress, squatCount, targetCount));
        progressBar.setProgress(squatCount);
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
