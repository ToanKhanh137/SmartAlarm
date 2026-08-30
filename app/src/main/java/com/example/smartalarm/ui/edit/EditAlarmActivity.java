package com.example.smartalarm.ui.edit;

import android.media.RingtoneManager;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.view.View;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.data.repository.AlarmRepository;
import com.example.smartalarm.databinding.ActivityEditAlarmBinding;
import com.example.smartalarm.ui.common.BaseActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EditAlarmActivity – tạo mới hoặc chỉnh sửa báo thức.
 * Giao diện chia 3 bước (Stepper) qua ViewFlipper:
 *   Bước 1: Giờ & Ngày
 *   Bước 2: Âm thanh & Snooze
 *   Bước 3: Thử thách thức dậy
 */
public class EditAlarmActivity extends BaseActivity {

    public static final String EXTRA_ALARM_ID = "alarm_id";
    private static final int REQUEST_RINGTONE = 2001;

    private ActivityEditAlarmBinding b;
    private AlarmRepository repository;

    // Alarm đang chỉnh sửa (null = tạo mới)
    private Alarm alarm;
    private int currentStep = 0; // 0,1,2
    private boolean[] daySelected = new boolean[7]; // T2..CN

    private Uri ringtoneUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditAlarmBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repository = AlarmRepository.getInstance(this);

        // Toolbar back
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // Load alarm nếu là edit
        int alarmId = getIntent().getIntExtra(EXTRA_ALARM_ID, -1);
        if (alarmId != -1) {
            b.toolbar.setTitle(getString(R.string.edit_alarm_title_edit));
            loadAlarm(alarmId);
        } else {
            b.toolbar.setTitle(getString(R.string.edit_alarm_title_add));
            alarm = new Alarm();
            populateUI();
        }

        setupStepNavigation();
        setupDayChips();
        setupChallengeRadio();
        setupRingtoneRow();
        setupSnoozeWheels();
    }

    // ===== LOAD ALARM FROM DB =====

    private void loadAlarm(int id) {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.execute(() -> {
            alarm = com.example.smartalarm.data.database.AppDatabase
                    .getInstance(this).alarmDao().getByIdSync(id);
            runOnUiThread(this::populateUI);
        });
    }

    private void populateUI() {
        if (alarm == null) return;
        b.timePicker.setHour(alarm.hour);
        b.timePicker.setMinute(alarm.minute);
        b.etLabel.setText(alarm.label);

        // Days
        daySelected[0] = alarm.mon;
        daySelected[1] = alarm.tue;
        daySelected[2] = alarm.wed;
        daySelected[3] = alarm.thu;
        daySelected[4] = alarm.fri;
        daySelected[5] = alarm.sat;
        daySelected[6] = alarm.sun;
        refreshDayChips();

        // Sound
        b.seekVolume.setProgress(alarm.volume);
        b.switchGradual.setChecked(alarm.gradualVolume);
        b.switchVibrate.setChecked(alarm.vibrate);
        b.pickerSnooze.setValue(alarm.snoozeMinutes);

        // Ringtone name
        if (alarm.ringtoneUri != null) {
            if ("silent".equals(alarm.ringtoneUri)) {
                this.ringtoneUri = Uri.parse("silent");
                b.tvRingtoneName.setText(getString(R.string.ringtone_silent));
            } else {
                try {
                    Uri uri = Uri.parse(alarm.ringtoneUri);
                    this.ringtoneUri = uri;
                    android.media.Ringtone r = RingtoneManager.getRingtone(this, uri);
                    if (r != null) b.tvRingtoneName.setText(r.getTitle(this));
                } catch (Exception ignored) {}
            }
        }

        // Challenge – sync card UI state
        int cardRadioId;
        switch (alarm.challengeType) {
            case Alarm.CHALLENGE_MATH:  cardRadioId = R.id.rbMath;  break;
            case Alarm.CHALLENGE_SHAKE: cardRadioId = R.id.rbShake; break;
            case Alarm.CHALLENGE_SQUAT: cardRadioId = R.id.rbSquat; break;
            case Alarm.CHALLENGE_STEP:  cardRadioId = R.id.rbStep;  break;
            case Alarm.CHALLENGE_QR:    cardRadioId = R.id.rbQr;    break;
            default:                    cardRadioId = R.id.rbNone;
        }
        selectChallengeCard(cardRadioId);
        switch (alarm.difficulty) {
            case Alarm.DIFFICULTY_EASY:   b.rgDifficulty.check(R.id.rbEasy);   break;
            case Alarm.DIFFICULTY_HARD:   b.rgDifficulty.check(R.id.rbHard);   break;
            case Alarm.DIFFICULTY_CUSTOM: b.rgDifficulty.check(R.id.rbCustom); break;
            default: b.rgDifficulty.check(R.id.rbMedium);
        }
    }

    // ===== STEP NAVIGATION =====

    private void setupStepNavigation() {
        b.btnNext.setOnClickListener(v -> {
            if (currentStep < 2) {
                currentStep++;
                b.stepFlipper.setDisplayedChild(currentStep);
                updateStepUI();
            } else {
                saveAndFinish();
            }
        });
        b.btnBack.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                b.stepFlipper.setDisplayedChild(currentStep);
                updateStepUI();
            } else {
                finish();
            }
        });
        updateStepUI();
    }

    private void updateStepUI() {
        // Màu step indicator
        int active = getResources().getColor(R.color.accent, getTheme());
        int inactive = getResources().getColor(R.color.text_secondary, getTheme());
        b.tvStep1.setTextColor(currentStep == 0 ? active : inactive);
        b.tvStep2.setTextColor(currentStep == 1 ? active : inactive);
        b.tvStep3.setTextColor(currentStep == 2 ? active : inactive);

        // Nút Back ẩn ở bước 0
        b.btnBack.setVisibility(currentStep == 0 ? View.INVISIBLE : View.VISIBLE);

        // Nút Next đổi thành "Lưu" ở bước cuối
        b.btnNext.setText(currentStep == 2
                ? getString(R.string.save)
                : getString(R.string.next));
    }

    // ===== DAY CHIPS =====

    private void setupDayChips() {
        TextView[] chips = {b.dayMon, b.dayTue, b.dayWed, b.dayThu, b.dayFri, b.daySat, b.daySun};
        for (int i = 0; i < chips.length; i++) {
            int idx = i;
            chips[i].setOnClickListener(v -> {
                daySelected[idx] = !daySelected[idx];
                refreshDayChips();
            });
        }
    }

    private void refreshDayChips() {
        TextView[] chips = {b.dayMon, b.dayTue, b.dayWed, b.dayThu, b.dayFri, b.daySat, b.daySun};
        for (int i = 0; i < chips.length; i++) {
            if (daySelected[i]) {
                chips[i].setBackgroundResource(R.drawable.bg_day_chip_active);
                chips[i].setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.day_active_text));
            } else {
                chips[i].setBackgroundResource(R.drawable.bg_day_chip);
                chips[i].setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.day_inactive_text));
            }
        }
    }

    // ===== CHALLENGE CARD PICKER =====

    private void selectChallengeCard(int radioId) {
        b.rgChallenge.check(radioId);

        int[][] pairs = {
            {R.id.cardChallengeNone,  R.id.ivCheckNone},
            {R.id.cardChallengeMath,  R.id.ivCheckMath},
            {R.id.cardChallengeShake, R.id.ivCheckShake},
            {R.id.cardChallengeSquat, R.id.ivCheckSquat},
            {R.id.cardChallengeStep,  R.id.ivCheckStep},
            {R.id.cardChallengeQr,    R.id.ivCheckQr},
        };
        int[] radioIds = {R.id.rbNone, R.id.rbMath, R.id.rbShake, R.id.rbSquat, R.id.rbStep, R.id.rbQr};

        for (int i = 0; i < pairs.length; i++) {
            boolean selected = (radioIds[i] == radioId);
            b.getRoot().findViewById(pairs[i][0]).setBackgroundResource(
                selected ? R.drawable.bg_challenge_card_selected : R.drawable.bg_challenge_card);
            b.getRoot().findViewById(pairs[i][1]).setVisibility(
                selected ? android.view.View.VISIBLE : android.view.View.GONE);
        }

        boolean showDiff = (radioId == R.id.rbMath || radioId == R.id.rbShake
                || radioId == R.id.rbSquat || radioId == R.id.rbStep);
        b.llDifficulty.setVisibility(showDiff ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void setupChallengeRadio() {
        b.cardChallengeNone.setOnClickListener(v  -> selectChallengeCard(R.id.rbNone));
        b.cardChallengeMath.setOnClickListener(v  -> selectChallengeCard(R.id.rbMath));
        b.cardChallengeShake.setOnClickListener(v -> selectChallengeCard(R.id.rbShake));
        b.cardChallengeSquat.setOnClickListener(v -> selectChallengeCard(R.id.rbSquat));
        b.cardChallengeStep.setOnClickListener(v  -> {
            selectChallengeCard(R.id.rbStep);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 301);
                }
            }
        });
        b.cardChallengeQr.setOnClickListener(v    -> {
            selectChallengeCard(R.id.rbQr);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 302);
            }
        });

        b.rgDifficulty.setOnCheckedChangeListener((group, checkedId) -> {
            boolean showCustom = checkedId == R.id.rbCustom;

            if (showCustom) {
                  int checkedChallenge = b.rgChallenge.getCheckedRadioButtonId();
                  if (checkedChallenge == R.id.rbMath) {
                      b.llCustomMath.setVisibility(View.VISIBLE);
                      b.llCustomGeneric.setVisibility(View.GONE);
                  } else {
                      b.llCustomMath.setVisibility(View.GONE);
                      b.llCustomGeneric.setVisibility(View.VISIBLE);
                  }
              } else {
                  b.llCustomMath.setVisibility(View.GONE);
                  b.llCustomGeneric.setVisibility(View.GONE);
              }
            if (showCustom) setupCustomSlider();
        });
    }

    private void setupCustomSlider() {
        int checkedChallenge = b.rgChallenge.getCheckedRadioButtonId();
        int max = 50;
        int min = 5;
        if (checkedChallenge == R.id.rbSquat) { max = 30; min = 3; }
        else if (checkedChallenge == R.id.rbStep) { max = 200; min = 10; }
        b.seekCustomValue.setMax(max - min);
        int current = alarm != null && alarm.customValue > 0 ? alarm.customValue : (max + min) / 2;
        b.seekCustomValue.setProgress(current - min);
        b.tvCustomValue.setText(String.valueOf(current));
        int finalMin = min;
        b.seekCustomValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                b.tvCustomValue.setText(String.valueOf(p + finalMin));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    // ===== RINGTONE PICKER =====

    private void setupRingtoneRow() {
        b.rowRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            if (ringtoneUri != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);
            }
            startActivityForResult(intent, REQUEST_RINGTONE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RINGTONE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri == null) {
                this.ringtoneUri = Uri.parse("silent");
                b.tvRingtoneName.setText(getString(R.string.ringtone_silent));
            } else {
                this.ringtoneUri = uri;
                android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, uri);
                b.tvRingtoneName.setText(r != null ? r.getTitle(this) : uri.getLastPathSegment());
            }
        }
    }

    // ===== SNOOZE PICKER =====

    private void setupSnoozeWheels() {
        b.pickerSnooze.setMinValue(1);
        b.pickerSnooze.setMaxValue(30);
        b.pickerSnooze.setValue(5);
    }

    // ===== SAVE =====

    private void saveAndFinish() {
        if (alarm == null) alarm = new Alarm();

        // Bước 1: Giờ & Ngày
        alarm.hour   = b.timePicker.getHour();
        alarm.minute = b.timePicker.getMinute();
        String label = b.etLabel.getText() != null ? b.etLabel.getText().toString().trim() : "";
        alarm.label  = label;
        alarm.mon = daySelected[0];
        alarm.tue = daySelected[1];
        alarm.wed = daySelected[2];
        alarm.thu = daySelected[3];
        alarm.fri = daySelected[4];
        alarm.sat = daySelected[5];
        alarm.sun = daySelected[6];

        // Bước 2: Âm thanh
        alarm.ringtoneUri   = ringtoneUri != null ? ringtoneUri.toString() : null;
        alarm.volume        = b.seekVolume.getProgress();
        alarm.gradualVolume = b.switchGradual.isChecked();
        alarm.vibrate       = b.switchVibrate.isChecked();
        alarm.snoozeMinutes = b.pickerSnooze.getValue();

        // Bước 3: Challenge
        int challengeChecked = b.rgChallenge.getCheckedRadioButtonId();
        if (challengeChecked == R.id.rbMath)       alarm.challengeType = Alarm.CHALLENGE_MATH;
        else if (challengeChecked == R.id.rbShake) alarm.challengeType = Alarm.CHALLENGE_SHAKE;
        else if (challengeChecked == R.id.rbSquat) alarm.challengeType = Alarm.CHALLENGE_SQUAT;
        else if (challengeChecked == R.id.rbStep)  alarm.challengeType = Alarm.CHALLENGE_STEP;
        else if (challengeChecked == R.id.rbQr)    alarm.challengeType = Alarm.CHALLENGE_QR;
        else                                        alarm.challengeType = Alarm.CHALLENGE_NONE;

        int diffChecked = b.rgDifficulty.getCheckedRadioButtonId();
        if (diffChecked == R.id.rbEasy)        alarm.difficulty = Alarm.DIFFICULTY_EASY;
        else if (diffChecked == R.id.rbHard)   alarm.difficulty = Alarm.DIFFICULTY_HARD;
        else if (diffChecked == R.id.rbCustom) {
            alarm.difficulty  = Alarm.DIFFICULTY_CUSTOM;
            int minVal = 5;
            if (alarm.challengeType == Alarm.CHALLENGE_SQUAT) minVal = 3;
            else if (alarm.challengeType == Alarm.CHALLENGE_STEP) minVal = 10;
            alarm.customValue = b.seekCustomValue.getProgress() + minVal;
        } else alarm.difficulty = Alarm.DIFFICULTY_MEDIUM;

        alarm.isActive = true;

        repository.save(alarm, id -> runOnUiThread(() -> {
            setResult(RESULT_OK);
            finish();
        }));
    }
}
