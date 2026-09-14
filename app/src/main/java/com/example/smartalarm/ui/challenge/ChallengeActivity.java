package com.example.smartalarm.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.smartalarm.R;
import com.example.smartalarm.data.database.AppDatabase;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.ui.common.BaseActivity;

import java.util.concurrent.Executors;

/**
 * Lớp cha cho mọi thử thách thức dậy.
 *
 * Gánh phần dùng chung: load alarm, chặn nút Back, và nút "bỏ qua thử thách" –
 * đường thoát bắt buộc phải có để người dùng không bị kẹt nếu cảm biến hỏng
 * hoặc thiếu quyền (ví dụ camera bị từ chối khi quét QR).
 */
public abstract class ChallengeActivity extends BaseActivity {

    /** Sau bao lâu thì cho phép bỏ qua thử thách. */
    private static final long SKIP_DELAY_MS = 60_000L;

    protected int alarmId;
    protected Alarm alarm;

    private final Handler skipHandler = new Handler(Looper.getMainLooper());
    private View btnSkip;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
    }

    /** Gọi sau setContentView() trong onCreate của lớp con. */
    protected void setupChallenge() {
        setupSkipButton();
        loadAlarm();
    }

    // ===== SKIP =====

    private void setupSkipButton() {
        btnSkip = findViewById(R.id.btnSkipChallenge);
        if (btnSkip == null) return;

        btnSkip.setVisibility(View.GONE);
        btnSkip.setOnClickListener(v -> dismissAlarm());

        skipHandler.postDelayed(() -> {
            if (btnSkip == null) return;
            btnSkip.setAlpha(0f);
            btnSkip.setVisibility(View.VISIBLE);
            btnSkip.animate().alpha(1f).setDuration(400).start();
        }, SKIP_DELAY_MS);
    }

    // ===== ALARM =====

    private void loadAlarm() {
        Executors.newSingleThreadExecutor().execute(() -> {
            Alarm loaded = AppDatabase.getInstance(this).alarmDao().getByIdSync(alarmId);
            runOnUiThread(() -> {
                alarm = loaded;
                onAlarmLoaded(loaded);
            });
        });
    }

    /** alarm có thể null nếu người dùng đã xóa báo thức. */
    protected abstract void onAlarmLoaded(@Nullable Alarm alarm);

    /** Số lần / số bước cần thực hiện, theo độ khó đã chọn. */
    protected int effectiveCount(@Nullable Alarm alarm, int fallback) {
        if (alarm == null) return fallback;
        int count = alarm.getEffectiveCount();
        return count > 0 ? count : fallback;
    }

    // ===== DISMISS =====

    protected void dismissAlarm() {
        skipHandler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_DISMISS);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        sendBroadcast(intent);
        finishAffinity();
    }

    @Override
    protected boolean isAlarmScreen() {
        return true;
    }

    @Override
    public void onBackPressed() {
        // Không cho trốn báo thức bằng nút Back.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        skipHandler.removeCallbacksAndMessages(null);
        btnSkip = null;
    }
}
