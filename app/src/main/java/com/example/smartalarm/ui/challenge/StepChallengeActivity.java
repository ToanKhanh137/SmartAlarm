package com.example.smartalarm.ui.challenge;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;

/**
 * StepChallengeActivity – đi X bước để tắt báo thức.
 *
 * Dùng TYPE_STEP_DETECTOR: mỗi bước là một event nên chỉ cần cộng dồn.
 * (TYPE_STEP_COUNTER trả về tổng số bước từ lúc boot và event đầu tiên có thể là
 * giá trị cũ đã cache, khiến bộ đếm nhảy vọt ngay khi vừa bước vài bước.)
 *
 * Nếu máy không có step detector, hoặc chưa có quyền ACTIVITY_RECOGNITION,
 * sẽ chuyển sang đếm bằng accelerometer – cách này không cần quyền nên thử thách
 * không bao giờ bị kẹt vì thiếu quyền.
 */
public class StepChallengeActivity extends ChallengeActivity implements SensorEventListener {

    // Đếm bằng accelerometer: ngưỡng và nhịp bước tối thiểu
    private static final float STEP_PEAK_THRESHOLD = 3.2f;
    private static final long  MIN_STEP_INTERVAL_MS = 260;
    private static final float ALPHA = 0.2f;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private boolean usingAccelerometer;

    private int targetSteps = 50;
    private int stepCount = 0;

    // State cho bộ đếm accelerometer
    private final float[] gravity = new float[3];
    private long lastStepMs = 0;
    private boolean abovePeak = false;

    private TextView tvInstruction, tvProgress, tvHint;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_step);

        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        tvHint        = findViewById(R.id.tvHint);
        progressBar   = findViewById(R.id.progressBar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        pickSensor();

        setupChallenge();
    }

    /** Chọn step detector nếu dùng được, nếu không thì accelerometer. */
    private void pickSensor() {
        if (hasActivityPermission()) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }
        if (stepSensor == null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            usingAccelerometer = true;
        }
    }

    private boolean hasActivityPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onAlarmLoaded(@Nullable Alarm alarm) {
        targetSteps = effectiveCount(alarm, 50);
        tvInstruction.setText(getString(R.string.step_instruction, targetSteps));
        tvHint.setText(getString(R.string.step_hint));
        progressBar.setMax(targetSteps);
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (usingAccelerometer) {
            detectStepFromAccelerometer(event);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            // Mỗi event là đúng 1 bước
            addStep();
        }
    }

    /**
     * Đếm bước bằng biên độ gia tốc: mỗi lần vượt ngưỡng rồi tụt xuống là 1 bước,
     * kèm khoảng cách tối thiểu giữa 2 bước để không đếm trùng do rung lắc.
     */
    private void detectStepFromAccelerometer(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        for (int i = 0; i < 3; i++) {
            gravity[i] = (1 - ALPHA) * gravity[i] + ALPHA * event.values[i];
        }
        float dx = event.values[0] - gravity[0];
        float dy = event.values[1] - gravity[1];
        float dz = event.values[2] - gravity[2];
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        long now = System.currentTimeMillis();
        if (magnitude > STEP_PEAK_THRESHOLD) {
            if (!abovePeak && now - lastStepMs > MIN_STEP_INTERVAL_MS) {
                abovePeak = true;
                lastStepMs = now;
                addStep();
            }
        } else if (magnitude < STEP_PEAK_THRESHOLD * 0.5f) {
            abovePeak = false;
        }
    }

    private void addStep() {
        if (stepCount >= targetSteps) return;
        stepCount++;
        updateUI();
        if (stepCount >= targetSteps) {
            sensorManager.unregisterListener(this);
            dismissAlarm();
        }
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.step_progress, stepCount, targetSteps));
        progressBar.setProgress(stepCount);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
