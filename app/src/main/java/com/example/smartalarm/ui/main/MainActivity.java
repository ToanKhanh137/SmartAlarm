package com.example.smartalarm.ui.main;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.data.repository.AlarmRepository;
import com.example.smartalarm.databinding.ActivityMainBinding;
import com.example.smartalarm.service.AlarmScheduler;
import com.example.smartalarm.settings.AppPreferences;
import com.example.smartalarm.ui.common.BaseActivity;
import com.example.smartalarm.ui.edit.EditAlarmActivity;
import com.example.smartalarm.ui.settings.SettingsActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
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
    private static final int REQUEST_TIMER_RINGTONE = 1002;
    private static final int PERM_REQUEST = 100;

    // ===== VIEW BINDING =====
    private ActivityMainBinding binding;

    // ===== DATA =====
    private AlarmRepository repository;
    private AlarmAdapter adapter;
    private AppPreferences prefs;

    // Nhạc báo khi hết giờ hẹn
    private MediaPlayer timerPlayer;

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
        prefs = AppPreferences.getInstance(this);

        setupBottomNav();
        setupAlarmList();
        setupWorldClock();
        setupTimer();
        setupStopwatch();
        setupFab();
        checkPermissions();
    }

    /**
     * Chỉ xin những quyền đã khai báo trong manifest và hợp lệ với API level đang chạy.
     * Gộp một quyền chưa khai báo vào mảng sẽ làm cả lần xin quyền bị hệ thống bỏ qua
     * mà không hiện hộp thoại nào.
     */
    private void checkPermissions() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(needed, android.Manifest.permission.POST_NOTIFICATIONS);
            addIfMissing(needed, android.Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            addIfMissing(needed, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addIfMissing(needed, android.Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (needed.isEmpty()) {
            refreshPermissionBanner();
        } else {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERM_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Hỏi tiếp các quyền phải vào Cài đặt, sau khi hộp thoại quyền thường đã xong
        if (requestCode == PERM_REQUEST) refreshPermissionBanner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng có thể vừa cấp quyền ở màn hình Cài đặt rồi quay lại
        refreshPermissionBanner();
    }

    private void addIfMissing(List<String> target, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            target.add(permission);
        }
    }

    /**
     * Hai quyền không xin được bằng requestPermissions, phải đưa người dùng sang Cài đặt:
     *  - Thông báo toàn màn hình: Android 14+ mặc định CHẶN với app không phải gọi điện,
     *    thiếu nó thì báo thức không tự mở màn hình tắt khi điện thoại đang khóa.
     *  - Báo thức chính xác: thiếu nó thì báo thức có thể reo trễ.
     *
     * Hiện bằng banner thường trực chứ không chỉ một hộp thoại, vì bỏ lỡ hộp thoại là
     * báo thức im lìm khi khóa máy mà không có dấu hiệu gì để lần ra nguyên nhân.
     */
    private void refreshPermissionBanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                showBanner(R.string.perm_fullscreen_title, R.string.perm_fullscreen_msg,
                        new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.fromParts("package", getPackageName(), null)));
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !new AlarmScheduler(this).canScheduleExactAlarms()) {
            showBanner(R.string.perm_exact_alarm_title, R.string.perm_exact_alarm_msg,
                    new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.fromParts("package", getPackageName(), null)));
            return;
        }
        binding.bannerPermission.setVisibility(View.GONE);
    }

    private void showBanner(int titleRes, int messageRes, Intent settingsIntent) {
        binding.bannerPermission.setVisibility(View.VISIBLE);
        binding.tvBannerTitle.setText(getString(titleRes));
        binding.tvBannerDesc.setText(getString(messageRes));
        binding.btnBannerFix.setOnClickListener(v -> {
            try {
                startActivity(settingsIntent);
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            }
        });
    }

    private void setupWorldClock() {
        worldClockAdapter = new WorldClockAdapter(this::confirmRemoveCity);
        binding.rvWorldClocks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvWorldClocks.setAdapter(worldClockAdapter);
        worldClockAdapter.submit(prefs.getWorldClocks());

        binding.btnAddCity.setOnClickListener(v -> showAddCityDialog());
    }

    private void showAddCityDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_add_city, null);
        EditText etSearch = content.findViewById(R.id.etCitySearch);
        ListView lvCities = content.findViewById(R.id.lvCities);

        List<String> all = new ArrayList<>();
        for (String id : TimeZone.getAvailableIDs()) {
            // Chỉ lấy ID dạng Vùng/Thành_phố, bỏ các alias kiểu "EST" hay "GMT+7"
            if (id.indexOf('/') > 0 && !id.startsWith("Etc/") && !id.startsWith("SystemV/")) {
                all.add(id);
            }
        }
        Collections.sort(all);

        List<String> shown = new ArrayList<>(all);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lvCities.setAdapter(adapter);

        Runnable refresh = () -> {
            adapter.clear();
            for (String id : shown) {
                adapter.add(WorldClockAdapter.displayName(id) + "  ·  " + id.replace('_', ' '));
            }
            adapter.notifyDataSetChanged();
        };
        refresh.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.world_clock_add))
                .setView(content)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                for (String id : all) {
                    if (query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)) {
                        shown.add(id);
                    }
                }
                refresh.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        lvCities.setOnItemClickListener((parent, view, position, id) -> {
            addCity(shown.get(position));
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addCity(String timezoneId) {
        List<String> current = prefs.getWorldClocks();
        if (!current.contains(timezoneId)) {
            current.add(timezoneId);
            prefs.setWorldClocks(current);
            worldClockAdapter.submit(current);
        }
        Toast.makeText(this,
                getString(R.string.world_clock_added, WorldClockAdapter.displayName(timezoneId)),
                Toast.LENGTH_SHORT).show();
    }

    private void confirmRemoveCity(String timezoneId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.world_clock_remove))
                .setMessage(WorldClockAdapter.displayName(timezoneId))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    List<String> current = prefs.getWorldClocks();
                    current.remove(timezoneId);
                    prefs.setWorldClocks(current);
                    worldClockAdapter.submit(current);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
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
            binding.tvNextAlarm.setText(hours > 0
                    ? getString(R.string.next_in_hours_minutes, hours, minutes)
                    : getString(R.string.next_in_minutes, minutes));
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

        binding.rowTimerSound.setOnClickListener(v -> pickTimerSound());
        refreshTimerSoundName();
    }

    // ===== TIMER SOUND =====

    private void pickTimerSound() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        String saved = prefs.getTimerRingtone();
        if (saved != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(saved));
        }
        startActivityForResult(intent, REQUEST_TIMER_RINGTONE);
    }

    private void refreshTimerSoundName() {
        String saved = prefs.getTimerRingtone();
        if (saved == null) {
            binding.tvTimerSoundName.setText(getString(R.string.ringtone_default));
            return;
        }
        if ("silent".equals(saved)) {
            binding.tvTimerSoundName.setText(getString(R.string.ringtone_silent));
            return;
        }
        Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(saved));
        binding.tvTimerSoundName.setText(r != null
                ? r.getTitle(this)
                : getString(R.string.ringtone_default));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TIMER_RINGTONE && resultCode == RESULT_OK) {
            Uri uri = data != null
                    ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) : null;
            prefs.setTimerRingtone(uri != null ? uri.toString() : "silent");
            refreshTimerSoundName();
        }
    }

    /** Phát nhạc báo hết giờ, kèm hộp thoại có nút dừng. Trước đây hết giờ không kêu gì cả. */
    private void onTimerFinished() {
        String saved = prefs.getTimerRingtone();
        if (!"silent".equals(saved)) {
            Uri uri = saved != null
                    ? Uri.parse(saved)
                    : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            playTimerSound(uri);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.timer_done))
                .setPositiveButton(getString(R.string.timer_stop), (d, w) -> stopTimerSound())
                .setOnDismissListener(d -> stopTimerSound())
                .show();
    }

    private void playTimerSound(Uri uri) {
        stopTimerSound();
        try {
            timerPlayer = new MediaPlayer();
            timerPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            timerPlayer.setDataSource(this, uri);
            timerPlayer.setLooping(true);
            timerPlayer.prepare();
            timerPlayer.start();
        } catch (Exception e) {
            stopTimerSound();
        }
    }

    private void stopTimerSound() {
        if (timerPlayer == null) return;
        try {
            if (timerPlayer.isPlaying()) timerPlayer.stop();
            timerPlayer.release();
        } catch (Exception ignored) {
        } finally {
            timerPlayer = null;
        }
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
                    binding.llTimerPicker.setVisibility(View.VISIBLE);
                    onTimerFinished();
                }
            }.start();
            timerRunning = true;
            binding.btnTimerStart.setText(getString(R.string.timer_pause));
        }
    }

    private void resetTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        stopTimerSound();
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
        lapAdapter.addLap(getString(R.string.lap_format, lapCount, m, s, c));
    }

    // ===== LIFECYCLE CLEANUP =====

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        stopwatchHandler.removeCallbacksAndMessages(null);
        stopTimerSound();
    }
}
