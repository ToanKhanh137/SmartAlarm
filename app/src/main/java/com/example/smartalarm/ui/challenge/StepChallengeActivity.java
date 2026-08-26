package com.example.smartalarm.ui.challenge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartalarm.R;
import com.example.smartalarm.data.database.AppDatabase;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.ui.common.BaseActivity;

import java.util.concurrent.Executors;

/**
 * StepChallengeActivity – đi X bước để tắt báo thức.
 * Dùng TYPE_STEP_COUNTER (đếm từ lúc boot) hoặc TYPE_STEP_DETECTOR (mỗi bước).
 * TYPE_STEP_DETECTOR: phù hợp hơn vì không cần giá trị baseline.
 */
public class StepChallengeActivity extends BaseActivity implements SensorEventListener {

    private static final int PERM_REQUEST_CODE = 301;

    private SensorManager sensorManager;
    private Sensor stepDetector;

    private int alarmId;
    private int targetSteps;
    private int stepCount = 0;

    private TextView tvInstruction, tvProgress, tvHint;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_step);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        tvHint        = findViewById(R.id.tvHint);
        progressBar   = findViewById(R.id.progressBar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepDetector  = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        Executors.newSingleThreadExecutor().execute(() -> {
            Alarm alarm = AppDatabase.getInstance(this).alarmDao().getByIdSync(alarmId);
            targetSteps = alarm != null ? alarm.getEffectiveCount() : 50;
            if (targetSteps <= 0) targetSteps = 50;
            runOnUiThread(() -> {
                tvInstruction.setText(getString(R.string.step_instruction, targetSteps));
                tvHint.setText(getString(R.string.step_hint));
                progressBar.setMax(targetSteps);
                updateUI();
            });
        });

        // Xin quyền ACTIVITY_RECOGNITION (Android 10+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    PERM_REQUEST_CODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepDetector != null)
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            stepCount++;
            runOnUiThread(() -> {
                updateUI();
                if (stepCount >= targetSteps) dismissAlarm();
            });
        }
    }

    private void updateUI() {
        tvProgress.setText(getString(R.string.step_progress, stepCount, targetSteps));
        progressBar.setProgress(stepCount);
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
}
