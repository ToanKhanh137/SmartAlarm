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
 * SquatChallengeActivity – squat thật để tắt báo thức.
 *
 * Một squat hợp lệ phải đi qua đủ 3 pha, theo thứ tự và đủ chậm:
 *   1. DOWN   – hạ người xuống  (gia tốc hướng xuống)
 *   2. BOTTOM – dừng một nhịp ở đáy (gia tốc gần 0)
 *   3. UP     – đứng lên        (gia tốc hướng lên)
 *
 * Lắc điện thoại đảo chiều quá nhanh và không có pha BOTTOM nên bị loại.
 * Điều kiện chặn gian lận là THỜI GIAN, không phải biên độ gia tốc – squat chậm
 * sinh ra gia tốc rất nhỏ, nên ngưỡng biên độ phải để thấp.
 *
 * Gia tốc dọc được tính bằng cách chiếu gia tốc tuyến tính lên vector trọng lực,
 * nên hoạt động ở mọi hướng cầm máy (dựng đứng, nằm ngang hay nghiêng).
 */
public class SquatChallengeActivity extends ChallengeActivity implements SensorEventListener {

    // Ngưỡng gia tốc dọc (m/s²). Squat chậm chỉ tạo ra khoảng 1.5–3 m/s².
    private static final float DOWN_THRESHOLD = -1.6f;
    private static final float UP_THRESHOLD   =  1.6f;
    private static final float CALM_THRESHOLD =  1.0f;

    // Ràng buộc thời gian – phần thực sự phân biệt squat với lắc máy.
    // Lắc tay không bao giờ có quãng đứng yên ở đáy, nên MIN_BOTTOM_MS mới là chốt chặn;
    // MIN_REP_MS để thấp để squat nhanh vẫn được tính.
    private static final long MIN_BOTTOM_MS   = 100;
    private static final long MIN_REP_MS      = 500;
    /** Nhịp chậm đến mức này thì chắc chắn không phải lắc máy, tính luôn dù không thấy đáy. */
    private static final long SLOW_REP_MS     = 1800;
    private static final long MAX_REP_MS      = 10000;
    private static final long REP_COOLDOWN_MS = 250;

    private static final float GRAVITY_ALPHA = 0.15f;
    private static final float SIGNAL_ALPHA  = 0.35f;

    private enum Phase { IDLE, DOWN }

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private int targetCount = 10;
    private int squatCount = 0;

    private Phase phase = Phase.IDLE;
    private long phaseStartMs = 0;
    /** Tổng thời gian đứng yên ở đáy trong nhịp hiện tại. */
    private long calmMs = 0;
    private long lastSampleMs = 0;
    private long lastRepMs = 0;
    private long lastHintMs = 0;

    private final float[] gravity = new float[3];
    private boolean gravityReady = false;
    private float verticalAccel = 0;

    private TextView tvInstruction, tvProgress, tvHint;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_squat);

        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        tvHint        = findViewById(R.id.tvHint);
        progressBar   = findViewById(R.id.progressBar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        setupChallenge();
    }

    @Override
    protected void onAlarmLoaded(@Nullable Alarm alarm) {
        targetCount = effectiveCount(alarm, 10);
        tvInstruction.setText(getString(R.string.squat_instruction, targetCount));
        tvHint.setText(getString(R.string.squat_hold_phone));
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

        if (!gravityReady) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            gravityReady = true;
            return;
        }
        for (int i = 0; i < 3; i++) {
            gravity[i] = (1 - GRAVITY_ALPHA) * gravity[i] + GRAVITY_ALPHA * event.values[i];
        }

        float gMagnitude = (float) Math.sqrt(
                gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]);
        if (gMagnitude < 1f) return; // chưa đủ dữ liệu để biết đâu là chiều dọc

        // Chiếu gia tốc tuyến tính lên chiều trọng lực → dương là đang tăng tốc lên trên.
        float dot = (event.values[0] - gravity[0]) * gravity[0]
                  + (event.values[1] - gravity[1]) * gravity[1]
                  + (event.values[2] - gravity[2]) * gravity[2];
        float vertical = dot / gMagnitude;
        verticalAccel = SIGNAL_ALPHA * vertical + (1 - SIGNAL_ALPHA) * verticalAccel;

        long now = System.currentTimeMillis();
        long dt = lastSampleMs == 0 ? 0 : now - lastSampleMs;
        lastSampleMs = now;

        if (now - lastRepMs < REP_COOLDOWN_MS) return;

        if (phase == Phase.DOWN && now - phaseStartMs > MAX_REP_MS) {
            resetPhase();
            hint(now, R.string.squat_hold_phone);
            return;
        }

        if (phase == Phase.IDLE) {
            if (verticalAccel < DOWN_THRESHOLD) {
                phase = Phase.DOWN;
                phaseStartMs = now;
                calmMs = 0;
                hint(now, R.string.squat_phase_down);
            }
            return;
        }

        // Đang trong một nhịp squat.
        // Giữa lúc hạ người, cơ thể giảm tốc ở đáy cũng sinh ra gia tốc dương vượt
        // ngưỡng "đứng lên". Vì vậy KHÔNG được hủy nhịp khi gặp lần vượt ngưỡng quá
        // sớm – chỉ bỏ qua và chờ lần đứng lên thật, nếu không cú squat sẽ bị mất.
        if (Math.abs(verticalAccel) < CALM_THRESHOLD) {
            if (calmMs == 0) hint(now, R.string.squat_phase_bottom);
            calmMs += dt;
            return;
        }

        if (verticalAccel > UP_THRESHOLD) {
            long elapsed = now - phaseStartMs;
            boolean pausedAtBottom = calmMs >= MIN_BOTTOM_MS && elapsed >= MIN_REP_MS;
            boolean clearlyTooSlowToBeShaking = elapsed >= SLOW_REP_MS;
            if (pausedAtBottom || clearlyTooSlowToBeShaking) {
                countRep(now);
            } else {
                hint(now, R.string.squat_too_fast);
            }
        }
    }

    private void countRep(long now) {
        resetPhase();
        lastRepMs = now;
        lastHintMs = 0;
        squatCount++;
        updateUI();
        tvHint.setText(getString(R.string.squat_hold_phone));
        if (squatCount >= targetCount) {
            sensorManager.unregisterListener(this);
            dismissAlarm();
        }
    }

    private void resetPhase() {
        phase = Phase.IDLE;
        phaseStartMs = 0;
        calmMs = 0;
    }

    /** Đổi gợi ý nhưng không quá nhanh, tránh chữ nhấp nháy liên tục. */
    private void hint(long now, int stringRes) {
        if (now - lastHintMs < 700) return;
        lastHintMs = now;
        tvHint.setText(getString(stringRes));
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.squat_progress, squatCount, targetCount));
        progressBar.setProgress(squatCount);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
