package com.example.smartalarm.ui.main;

import android.content.Intent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.data.repository.AlarmRepository;
import com.example.smartalarm.databinding.ActivityMainBinding;
import com.example.smartalarm.service.AlarmScheduler;
import com.example.smartalarm.ui.common.BaseActivity;
import com.example.smartalarm.ui.edit.EditAlarmActivity;
import com.example.smartalarm.ui.settings.SettingsActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.os.CountDownTimer;
import java.util.concurrent.TimeUnit;

/**
 * MainActivity – màn hình chính chứa 4 tab qua ViewFlipper:
 * 0: Danh sách báo thức
 * 1: Đồng hồ thế giới
 * 2: Hẹn giờ (Timer)
 * 3: Bấm giờ (Stopwatch)
 */
public class MainActivity extends BaseActivity
        implements AlarmAdapter.OnAlarmActionListener {

    // ===== CONSTANTS =====
    private static final int TAB_ALARM    = 0;
    private static final int TAB_CLOCK    = 1;
    private static final int TAB_TIMER    = 2;
    private static final int TAB_STOPWATCH = 3;

    public static final int REQUEST_EDIT_ALARM = 1001;

    // ===== VIEW BINDING =====
    private ActivityMainBinding binding;

    // ===== DATA =====
    private AlarmRepository repository;
    private AlarmAdapter adapter;

    // ===== TIMER =====
    private CountDownTimer countDownTimer;
    private long timerDurationMs = 0;
    private long timerRemainingMs = 0;
    private boolean timerRunning = false;

    // ===== STOPWATCH =====
    private Handler stopwatchHandler = new Handler(Looper.getMainLooper());
    private long stopwatchStartTime = 0;
    private long stopwatchElapsed = 0;
    private boolean stopwatchRunning = false;
    private int lapCount = 0;
    private LapAdapter lapAdapter;
    private WorldClockAdapter worldClockAdapter;

    // ===== LIFECYCLE =====

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = AlarmRepository.getInstance(this);

        setupBottomNav();
        setupAlarmList();
        setupWorldClock();
        setupTimer();
        setupStopwatch();
        setupFab();
        checkPermissions();
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            String[] perms = {
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            };
            requestPermissions(perms, 100);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            String[] perms = {
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            };
            requestPermissions(perms, 100);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    private void setupWorldClock() {
        worldClockAdapter = new WorldClockAdapter();
        binding.rvWorldClocks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvWorldClocks.setAdapter(worldClockAdapter);
    }

    // ===== NAVIGATION =====

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_alarm) {
                showTab(TAB_ALARM);
            } else if (id == R.id.nav_clock) {
                showTab(TAB_CLOCK);
            } else if (id == R.id.nav_timer) {
                showTab(TAB_TIMER);
            } else if (id == R.id.nav_stopwatch) {
                showTab(TAB_STOPWATCH);
            }
            return true;
        });
        binding.bottomNav.setSelectedItemId(R.id.nav_alarm);
    }

    private void showTab(int tab) {
        binding.viewFlipper.setDisplayedChild(tab);
        // FAB chỉ hiện ở tab Alarm
        binding.fabAddAlarm.setVisibility(tab == TAB_ALARM ? View.VISIBLE : View.GONE);
    }

    // ===== ALARM LIST =====

    private void setupAlarmList() {
        adapter = new AlarmAdapter(this);
        binding.rvAlarms.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAlarms.setAdapter(adapter);

        // Quan sát LiveData từ Room
        repository.observeAll().observe(this, alarms -> {
            adapter.submitList(alarms);
            updateEmptyState(alarms);
            updateNextAlarmText(alarms);
        });

        // Settings button
        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void updateEmptyState(List<Alarm> alarms) {
        boolean empty = alarms == null || alarms.isEmpty();
        binding.rvAlarms.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.llEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void updateNextAlarmText(List<Alarm> alarms) {
        if (alarms == null || alarms.isEmpty()) {
            binding.tvNextAlarm.setText(getString(R.string.alarm_off));
            return;
        }
        AlarmScheduler scheduler = new AlarmScheduler(this);
        long nearest = Long.MAX_VALUE;
        for (Alarm a : alarms) {
            if (!a.isActive) continue;
            long t = scheduler.calculateNextTrigger(a);
            if (t > 0 && t < nearest) nearest = t;
        }
        if (nearest == Long.MAX_VALUE) {
            binding.tvNextAlarm.setText(getString(R.string.alarm_off));
        } else {
            long diff = nearest - System.currentTimeMillis();
            long hours   = TimeUnit.MILLISECONDS.toHours(diff);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
            String text;
            if (hours > 0) {
                text = String.format(Locale.getDefault(), "Còn %d giờ %d phút", hours, minutes);
            } else {
                text = String.format(Locale.getDefault(), "Còn %d phút", minutes);
            }
            binding.tvNextAlarm.setText(text);
        }
    }

    // ===== FAB =====

    private void setupFab() {
        binding.fabAddAlarm.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditAlarmActivity.class);
            startActivityForResult(intent, REQUEST_EDIT_ALARM);
        });
    }

    // ===== ALARM ACTIONS (từ Adapter) =====

    @Override
    public void onToggle(Alarm alarm, boolean isActive) {
        repository.setActive(alarm.id, isActive);
    }

    @Override
    public void onEdit(Alarm alarm) {
        Intent intent = new Intent(this, EditAlarmActivity.class);
        intent.putExtra(EditAlarmActivity.EXTRA_ALARM_ID, alarm.id);
        startActivityForResult(intent, REQUEST_EDIT_ALARM);
    }

    @Override
    public void onDelete(Alarm alarm) {
        String label = alarm.getDisplayLabel();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_alarm_title))
                .setMessage(getString(R.string.delete_alarm_msg, label))
                .setPositiveButton(getString(R.string.delete), (d, w) ->
                        repository.delete(alarm.id))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // ===== TIMER =====

    private void setupTimer() {
        // NumberPicker ranges
        binding.pickerHour.setMinValue(0);
        binding.pickerHour.setMaxValue(23);
        binding.pickerMinute.setMinValue(0);
        binding.pickerMinute.setMaxValue(59);
        binding.pickerSecond.setMinValue(0);
        binding.pickerSecond.setMaxValue(59);

        // Preset buttons
        binding.btn5min.setOnClickListener(v  -> setTimerPreset(0, 5, 0));
        binding.btn10min.setOnClickListener(v -> setTimerPreset(0, 10, 0));
        binding.btn25min.setOnClickListener(v -> setTimerPreset(0, 25, 0));
        binding.btn30min.setOnClickListener(v -> setTimerPreset(0, 30, 0));

        binding.btnTimerStart.setOnClickListener(v -> toggleTimer());
        binding.btnTimerReset.setOnClickListener(v -> resetTimer());
    }

    private void setTimerPreset(int h, int m, int s) {
        binding.pickerHour.setValue(h);
        binding.pickerMinute.setValue(m);
        binding.pickerSecond.setValue(s);
        updateTimerDisplay(((h * 3600L) + (m * 60L) + s) * 1000L);
    }

    private void toggleTimer() {
        if (timerRunning) {
            // Pause
            if (countDownTimer != null) countDownTimer.cancel();
            timerRunning = false;
            binding.btnTimerStart.setText(getString(R.string.timer_start));
        } else {
            // Start / Resume
            if (timerRemainingMs <= 0) {
                // Fresh start: lấy từ pickers
                int h = binding.pickerHour.getValue();
                int m = binding.pickerMinute.getValue();
                int s = binding.pickerSecond.getValue();
                timerDurationMs = ((h * 3600L) + (m * 60L) + s) * 1000L;
                timerRemainingMs = timerDurationMs;
            }
            if (timerRemainingMs <= 0) return;

            binding.llTimerPicker.setVisibility(View.GONE);
            countDownTimer = new CountDownTimer(timerRemainingMs, 100) {
                @Override
                public void onTick(long millisUntilFinished) {
                    timerRemainingMs = millisUntilFinished;
                    updateTimerDisplay(millisUntilFinished);
                }
                @Override
                public void onFinish() {
                    timerRemainingMs = 0;
                    timerRunning = false;
                    binding.tvTimerDisplay.setText(getString(R.string.timer_done));
                    binding.btnTimerStart.setText(getString(R.string.timer_start));
                    // TODO: phát âm thanh khi hết giờ
                }
            }.start();
            timerRunning = true;
            binding.btnTimerStart.setText(getString(R.string.timer_pause));
        }
    }

    private void resetTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        timerRemainingMs = 0;
        binding.btnTimerStart.setText(getString(R.string.timer_start));
        binding.tvTimerDisplay.setText("00:00:00");
        binding.llTimerPicker.setVisibility(View.VISIBLE);
    }

    private void updateTimerDisplay(long ms) {
        long h = ms / 3600000;
        long m = (ms % 3600000) / 60000;
        long s = (ms % 60000) / 1000;
        binding.tvTimerDisplay.setText(
                String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
    }

    // ===== STOPWATCH =====

    private void setupStopwatch() {
        lapAdapter = new LapAdapter();
        binding.rvLaps.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLaps.setAdapter(lapAdapter);

        binding.btnStopwatchStart.setOnClickListener(v -> toggleStopwatch());
        binding.btnLap.setOnClickListener(v -> recordLap());
    }

    private void toggleStopwatch() {
        if (stopwatchRunning) {
            // Pause
            stopwatchElapsed += System.currentTimeMillis() - stopwatchStartTime;
            stopwatchRunning = false;
            stopwatchHandler.removeCallbacksAndMessages(null);
            binding.btnStopwatchStart.setText(getString(R.string.timer_start));
        } else {
            // Start/Resume
            stopwatchStartTime = System.currentTimeMillis();
            stopwatchRunning = true;
            stopwatchHandler.post(stopwatchRunnable);
            binding.btnStopwatchStart.setText(getString(R.string.timer_pause));
        }
    }

    private final Runnable stopwatchRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsed = stopwatchElapsed + (System.currentTimeMillis() - stopwatchStartTime);
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            long centis  = (elapsed % 1000) / 10;
            binding.tvStopwatchDisplay.setText(
                    String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, centis));
            stopwatchHandler.postDelayed(this, 16); // ~60fps
        }
    };

    private void recordLap() {
        if (!stopwatchRunning && stopwatchElapsed == 0) return;
        lapCount++;
        long elapsed = stopwatchElapsed +
                (stopwatchRunning ? System.currentTimeMillis() - stopwatchStartTime : 0);
        long m = elapsed / 60000;
        long s = (elapsed % 60000) / 1000;
        long c = (elapsed % 1000) / 10;
        String lapText = String.format(Locale.getDefault(),
                "Vòng %d  %02d:%02d.%02d", lapCount, m, s, c);
        lapAdapter.addLap(lapText);
    }

    // ===== LIFECYCLE CLEANUP =====

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        stopwatchHandler.removeCallbacksAndMessages(null);
    }
}
