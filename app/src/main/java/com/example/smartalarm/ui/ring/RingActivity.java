package com.example.smartalarm.ui.ring;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.example.smartalarm.R;
import com.example.smartalarm.data.database.AppDatabase;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.data.repository.AlarmRepository;
import com.example.smartalarm.databinding.ActivityRingBinding;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.service.AlarmRingingService;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.challenge.MathChallengeActivity;
import com.example.smartalarm.ui.challenge.QrChallengeActivity;
import com.example.smartalarm.ui.challenge.ShakeChallengeActivity;
import com.example.smartalarm.ui.challenge.SquatChallengeActivity;
import com.example.smartalarm.ui.challenge.StepChallengeActivity;
import com.example.smartalarm.ui.common.BaseActivity;

import java.util.concurrent.Executors;

/**
 * RingActivity – màn hình hiển thị khi báo thức reo.
 * Hiển thị trên lock screen, bật màn hình.
 * Nút Snooze (nếu bật trong settings) và Thức dậy (→ challenge hoặc dismiss thẳng).
 */
public class RingActivity extends BaseActivity {

    private ActivityRingBinding b;
    private Alarm alarm;
    private int alarmId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(android.content.Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        b = ActivityRingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        if (alarmId == -1) { finish(); return; }

        loadAlarm();
        startPulseAnimation();
    }

    // ===== LOAD ALARM =====

    private void loadAlarm() {
        Executors.newSingleThreadExecutor().execute(() -> {
            alarm = AppDatabase.getInstance(this).alarmDao().getByIdSync(alarmId);
            runOnUiThread(this::setupUI);
        });
    }

    private void setupUI() {
        if (alarm == null) { finish(); return; }

        // Giờ
        b.tvRingTime.setText(String.format("%02d:%02d", alarm.hour, alarm.minute));

        // Label
        if (alarm.label != null && !alarm.label.isEmpty()) {
            b.tvRingLabel.setText(alarm.label);
            b.tvRingLabel.setVisibility(View.VISIBLE);
        } else {
            b.tvRingLabel.setVisibility(View.GONE);
        }

        // Snooze – báo thức quan trọng chỉ hoãn được số lần giới hạn
        AppPreferences prefs = AppPreferences.getInstance(this);
        if (prefs.isSnoozeEnabled() && alarm.canSnoozeAgain()) {
            int snoozeMins = alarm.snoozeMinutes > 0
                    ? alarm.snoozeMinutes
                    : prefs.getSnoozeDuration();
            int left = alarm.snoozesLeft();
            b.btnSnooze.setText(left < 0
                    ? getString(R.string.ring_snooze, snoozeMins)
                    : getString(R.string.ring_snooze_left, snoozeMins, left));
            b.btnSnooze.setVisibility(View.VISIBLE);
            b.btnSnooze.setOnClickListener(v -> doSnooze(snoozeMins));
            b.tvPowerHint.setVisibility(View.VISIBLE);
        } else {
            b.btnSnooze.setVisibility(View.GONE);
            b.tvPowerHint.setVisibility(View.GONE);
        }

        // Dismiss / Challenge button
        if (alarm.challengeType != Alarm.CHALLENGE_NONE) {
            b.btnDismiss.setText(getString(R.string.ring_solve_challenge));
        } else {
            b.btnDismiss.setText(getString(R.string.ring_dismiss));
        }
        b.btnDismiss.setOnClickListener(v -> handleDismissOrChallenge());
    }

    // ===== ACTIONS =====

    private void doSnooze(int minutes) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_SNOOZE);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        sendBroadcast(intent);
        finish();
    }

    private void handleDismissOrChallenge() {
        if (alarm.challengeType == Alarm.CHALLENGE_NONE) {
            doDismiss();
            return;
        }
        // Mở màn hình challenge tương ứng
        Intent intent;
        switch (alarm.challengeType) {
            case Alarm.CHALLENGE_MATH:
                intent = new Intent(this, MathChallengeActivity.class); break;
            case Alarm.CHALLENGE_SHAKE:
                intent = new Intent(this, ShakeChallengeActivity.class); break;
            case Alarm.CHALLENGE_SQUAT:
                intent = new Intent(this, SquatChallengeActivity.class); break;
            case Alarm.CHALLENGE_STEP:
                intent = new Intent(this, StepChallengeActivity.class); break;
            case Alarm.CHALLENGE_QR:
                intent = new Intent(this, QrChallengeActivity.class); break;
            default:
                doDismiss(); return;
        }
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        startActivity(intent);
        // KHÔNG finish() ở đây – challenge sẽ finish() và quay về đây, rồi đây mới finish()
    }

    @Override
    protected boolean isAlarmScreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Báo thức đã tắt (từ challenge, notification hoặc do bị xóa) → không giữ màn hình này.
        if (!AlarmRingingService.isRinging) finish();
    }

    public void doDismiss() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_DISMISS);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        sendBroadcast(intent);
        finish();
    }

    // ===== PULSE ANIMATION =====

    private void startPulseAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(b.pulseView, "scaleX", 0.8f, 1.4f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(b.pulseView, "scaleY", 0.8f, 1.4f);
        ObjectAnimator alpha  = ObjectAnimator.ofFloat(b.pulseView, "alpha", 0.5f, 0f);

        // Lặp bằng ObjectAnimator


        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatMode(ObjectAnimator.REVERSE);

        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY, alpha);
        pulse.setDuration(1500);
        pulse.start();
    }

    // Không cho người dùng nhấn Back để trốn báo thức
    @Override
    public void onBackPressed() {
        // Không làm gì
    }
}
