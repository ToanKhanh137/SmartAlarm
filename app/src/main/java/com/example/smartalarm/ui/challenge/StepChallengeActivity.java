package com.example.smartalarm.ui.challenge;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
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
 *
 * Hai cái bẫy đã gặp và phải chặn:
 *  - TYPE_STEP_COUNTER trả tổng số bước từ lúc boot, và event đầu tiên có thể là
 *    giá trị cũ đã cache → baseline lệch → bộ đếm nhảy vọt. Không dùng nữa.
 *  - Step detector có thể BATCH: lúc đăng ký listener, phần cứng xả cả hàng đợi
 *    những bước đã đi TRƯỚC khi mở màn hình, làm số bước vọt lên ngay lập tức.
 *    Nên phải tắt batching và bỏ qua event cũ hơn thời điểm đăng ký.
 *
 * Nếu máy không có step detector hoặc chưa có quyền ACTIVITY_RECOGNITION thì đếm
 * bằng accelerometer – cách này không cần quyền nên thử thách không bao giờ bị kẹt.
 */
public class StepChallengeActivity extends ChallengeActivity implements SensorEventListener {

    private static final String TAG = "StepChallenge";

    /**
     * Bỏ qua event trong khoảng này sau khi đăng ký, để loại phần hàng đợi cũ.
     * Để ngắn thôi: lọc theo dấu thời gian mới là cách chính, cửa sổ này chỉ để dự phòng
     * cho máy báo timestamp không đáng tin – dài quá thì người dùng thấy đếm bị trễ.
     */
    private static final long WARMUP_MS = 250;

    // Đếm bằng accelerometer: cần một đỉnh rồi tụt hẳn xuống mới tính 1 bước
    private static final float STEP_PEAK_THRESHOLD  = 3.2f;
    private static final float STEP_VALLEY_THRESHOLD = 1.4f;
    private static final long  MIN_STEP_INTERVAL_MS = 320;
    private static final float ALPHA = 0.2f;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private boolean usingAccelerometer;

    private int targetSteps = 50;
    private int stepCount = 0;

    private long listenerStartMs = 0;
    private long listenerStartNanos = 0;

    // State cho bộ đếm accelerometer
    private final float[] gravity = new float[3];
    private boolean gravityReady = false;
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

    private void pickSensor() {
        if (hasActivityPermission()) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }
        if (stepSensor == null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            usingAccelerometer = true;
        }
        Log.i(TAG, "Đếm bước bằng " + (usingAccelerometer ? "accelerometer" : "step detector"));
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
        if (stepSensor == null) return;

        listenerStartMs = System.currentTimeMillis();
        listenerStartNanos = SystemClock.elapsedRealtimeNanos();
        abovePeak = false;
        gravityReady = false;

        // SENSOR_DELAY_FASTEST + maxReportLatencyUs = 0 → không batch, gửi ngay từng bước.
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Bỏ qua khoảng đầu: đây là lúc phần cứng xả hàng đợi bước cũ.
        if (System.currentTimeMillis() - listenerStartMs < WARMUP_MS) return;

        if (usingAccelerometer) {
            detectStepFromAccelerometer(event);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            // Event có dấu thời gian trước lúc đăng ký là bước đã đi từ trước.
            if (event.timestamp > 0 && event.timestamp < listenerStartNanos) return;
            addStep();
        }
    }

    /** Một bước = một đỉnh gia tốc rồi tụt hẳn xuống, cách bước trước đủ lâu. */
    private void detectStepFromAccelerometer(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        if (!gravityReady) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            gravityReady = true;
            return;
        }
        for (int i = 0; i < 3; i++) {
            gravity[i] = (1 - ALPHA) * gravity[i] + ALPHA * event.values[i];
        }
        float dx = event.values[0] - gravity[0];
        float dy = event.values[1] - gravity[1];
        float dz = event.values[2] - gravity[2];
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        long now = System.currentTimeMillis();
        if (!abovePeak && magnitude > STEP_PEAK_THRESHOLD) {
            abovePeak = true;
            if (now - lastStepMs > MIN_STEP_INTERVAL_MS) {
                lastStepMs = now;
                addStep();
            }
        } else if (abovePeak && magnitude < STEP_VALLEY_THRESHOLD) {
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
