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
 * Người dùng giữ điện thoại trước ngực (màn hình hướng lên, máy dựng đứng).
 * Một squat hợp lệ phải đi qua đủ 3 pha, theo đúng thứ tự và đủ chậm:
 *   1. DOWN   – hạ người xuống  (gia tốc trục Y âm)
 *   2. BOTTOM – ngồi yên ở đáy  (gia tốc gần 0 trong một khoảng thời gian)
 *   3. UP     – đứng lên        (gia tốc trục Y dương)
 *
 * Lắc điện thoại bằng tay đảo chiều quá nhanh và không có pha BOTTOM nên bị loại –
 * đó là lý do phải kiểm tra thời gian chứ không chỉ kiểm tra ngưỡng gia tốc.
 */
public class SquatChallengeActivity extends ChallengeActivity implements SensorEventListener {

    // Ngưỡng gia tốc theo trục dọc (m/s², đã trừ trọng lực)
    private static final float DOWN_THRESHOLD   = -3.5f;
    private static final float UP_THRESHOLD     =  3.5f;
    private static final float CALM_THRESHOLD   =  2.5f;

    // Ràng buộc thời gian – đây là phần chặn gian lận bằng cách lắc máy
    private static final long MIN_BOTTOM_MS = 150;   // phải ngồi yên ở đáy
    private static final long MIN_REP_MS    = 900;   // 1 squat thật không thể nhanh hơn
    private static final long MAX_REP_MS    = 6000;  // quá lâu → coi như hỏng nhịp, reset
    private static final long REP_COOLDOWN_MS = 400;

    // Máy dựng đứng thì trọng lực dồn về trục Y (~9.8). Nếu không, người dùng đang
    // vung máy ở hướng khác chứ không phải squat.
    private static final float UPRIGHT_MIN_GRAVITY_Y = 6.5f;

    private static final float ALPHA = 0.3f;

    private enum Phase { IDLE, DOWN, BOTTOM }

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private int targetCount = 10;
    private int squatCount = 0;

    private Phase phase = Phase.IDLE;
    private long phaseStartMs = 0;
    private long bottomStartMs = 0;
    private long lastRepMs = 0;
    private long lastWarnMs = 0;

    private float filteredY = 0;
    private final float[] gravity = new float[3];

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

        // Tách trọng lực để biết máy có đang dựng đứng hay không
        for (int i = 0; i < 3; i++) {
            gravity[i] = (1 - ALPHA) * gravity[i] + ALPHA * event.values[i];
        }

        long now = System.currentTimeMillis();

        if (Math.abs(gravity[1]) < UPRIGHT_MIN_GRAVITY_Y) {
            // Máy không dựng đứng → không phải động tác squat
            resetPhase();
            warn(now, R.string.squat_hold_phone);
            return;
        }

        // Gia tốc dọc đã trừ trọng lực, làm mượt để bỏ nhiễu
        float verticalAccel = event.values[1] - gravity[1];
        filteredY = ALPHA * verticalAccel + (1 - ALPHA) * filteredY;

        if (now - lastRepMs < REP_COOLDOWN_MS) return;

        // Quá lâu chưa xong nhịp → bỏ, bắt đầu lại
        if (phase != Phase.IDLE && now - phaseStartMs > MAX_REP_MS) {
            resetPhase();
            return;
        }

        switch (phase) {
            case IDLE:
                if (filteredY < DOWN_THRESHOLD) {
                    phase = Phase.DOWN;
                    phaseStartMs = now;
                }
                break;

            case DOWN:
                // Đợi người dùng ngồi yên ở đáy
                if (Math.abs(filteredY) < CALM_THRESHOLD) {
                    phase = Phase.BOTTOM;
                    bottomStartMs = now;
                } else if (filteredY > UP_THRESHOLD) {
                    // Đảo chiều tức thì mà không có pha đáy → là lắc máy, không tính
                    resetPhase();
                    warn(now, R.string.squat_too_fast);
                }
                break;

            case BOTTOM:
                if (filteredY > UP_THRESHOLD) {
                    boolean bottomLongEnough = now - bottomStartMs >= MIN_BOTTOM_MS;
                    boolean repSlowEnough    = now - phaseStartMs >= MIN_REP_MS;
                    if (bottomLongEnough && repSlowEnough) {
                        countRep(now);
                    } else {
                        resetPhase();
                        warn(now, R.string.squat_too_fast);
                    }
                } else if (filteredY < DOWN_THRESHOLD) {
                    // Lại đi xuống → vẫn đang trong pha hạ người
                    phase = Phase.DOWN;
                }
                break;
        }
    }

    private void countRep(long now) {
        resetPhase();
        lastRepMs = now;
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
        bottomStartMs = 0;
    }

    /** Đổi gợi ý nhưng không quá 2 giây một lần, tránh nhấp nháy liên tục. */
    private void warn(long now, int stringRes) {
        if (now - lastWarnMs < 2000) return;
        lastWarnMs = now;
        tvHint.setText(getString(stringRes));
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.squat_progress, squatCount, targetCount));
        progressBar.setProgress(squatCount);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
