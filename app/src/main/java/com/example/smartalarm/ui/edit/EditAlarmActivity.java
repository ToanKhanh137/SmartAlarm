package com.example.smartalarm.ui.edit;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    private static final int PERM_CAMERA = 302;
    private static final int PERM_ACTIVITY = 301;
    private static final int PERM_AUDIO = 303;

    private static final int MATH_QUESTIONS_MIN = 1;
    private static final int MATH_QUESTIONS_MAX = 15;

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

        // Đổi loại thử thách thì phạm vi giá trị tùy chỉnh cũng khác
        if (showDiff) refreshCustomSection();
    }

    private void setupChallengeRadio() {
        b.cardChallengeNone.setOnClickListener(v  -> selectChallengeCard(R.id.rbNone));
        b.cardChallengeMath.setOnClickListener(v  -> selectChallengeCard(R.id.rbMath));
        b.cardChallengeShake.setOnClickListener(v -> selectChallengeCard(R.id.rbShake));
        b.cardChallengeSquat.setOnClickListener(v -> selectChallengeCard(R.id.rbSquat));
        // Xin quyền ngay lúc chọn thử thách, không đợi đến lúc báo thức reo mới xin –
        // khi đó màn hình đang khóa và hộp thoại quyền rất dễ bị bỏ qua.
        b.cardChallengeStep.setOnClickListener(v  -> {
            selectChallengeCard(R.id.rbStep);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestIfMissing(Manifest.permission.ACTIVITY_RECOGNITION, PERM_ACTIVITY);
            }
        });
        b.cardChallengeQr.setOnClickListener(v    -> {
            selectChallengeCard(R.id.rbQr);
            requestIfMissing(Manifest.permission.CAMERA, PERM_CAMERA);
        });

        b.rgDifficulty.setOnCheckedChangeListener((group, checkedId) -> refreshCustomSection());

        b.seekCustomValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                b.tvCustomValue.setText(String.valueOf(p + customMin()));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        b.seekMathQuestions.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                b.tvMathQuestions.setText(String.valueOf(p + MATH_QUESTIONS_MIN));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    /**
     * Phần Tùy chỉnh hiển thị bộ điều khiển khác nhau tùy loại thử thách:
     * Toán cần số câu hỏi + tập phép tính, các loại còn lại chỉ cần một con số.
     */
    private void refreshCustomSection() {
        boolean isCustom = b.rgDifficulty.getCheckedRadioButtonId() == R.id.rbCustom;
        boolean isMath = b.rgChallenge.getCheckedRadioButtonId() == R.id.rbMath;

        b.llCustomMath.setVisibility(isCustom && isMath ? View.VISIBLE : View.GONE);
        b.llCustomGeneric.setVisibility(isCustom && !isMath ? View.VISIBLE : View.GONE);

        if (!isCustom) return;

        if (isMath) {
            b.seekMathQuestions.setMax(MATH_QUESTIONS_MAX - MATH_QUESTIONS_MIN);
            int questions = alarm != null && alarm.difficulty == Alarm.DIFFICULTY_CUSTOM
                    ? alarm.mathQuestionCount() : 3;
            questions = clamp(questions, MATH_QUESTIONS_MIN, MATH_QUESTIONS_MAX);
            b.seekMathQuestions.setProgress(questions - MATH_QUESTIONS_MIN);
            b.tvMathQuestions.setText(String.valueOf(questions));

            int ops = alarm != null && alarm.difficulty == Alarm.DIFFICULTY_CUSTOM
                    ? alarm.mathOpsMask() : Alarm.OPS_DEFAULT;
            b.cbMathAdd.setChecked((ops & Alarm.OP_ADD) != 0);
            b.cbMathSub.setChecked((ops & Alarm.OP_SUB) != 0);
            b.cbMathMul.setChecked((ops & Alarm.OP_MUL) != 0);
            b.cbMathDiv.setChecked((ops & Alarm.OP_DIV) != 0);
        } else {
            int min = customMin(), max = customMax();
            b.seekCustomValue.setMax(max - min);
            int current = alarm != null && alarm.difficulty == Alarm.DIFFICULTY_CUSTOM
                    && alarm.customValue > 0 ? alarm.customValue : (max + min) / 2;
            current = clamp(current, min, max);
            b.seekCustomValue.setProgress(current - min);
            b.tvCustomValue.setText(String.valueOf(current));
        }
    }

    private int customMin() {
        int challenge = b.rgChallenge.getCheckedRadioButtonId();
        if (challenge == R.id.rbSquat) return 3;
        if (challenge == R.id.rbStep) return 10;
        return 5;
    }

    private int customMax() {
        int challenge = b.rgChallenge.getCheckedRadioButtonId();
        if (challenge == R.id.rbSquat) return 30;
        if (challenge == R.id.rbStep) return 200;
        return 50;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void requestIfMissing(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
        }
    }

    // ===== RINGTONE PICKER =====

    private void setupRingtoneRow() {
        b.rowRingtone.setOnClickListener(v -> {
            // Không có quyền đọc nhạc thì nhạc chuông tự chọn sẽ không phát được
            // và báo thức âm thầm quay về nhạc mặc định.
            requestAudioPermissionIfNeeded();
            openRingtonePicker();
        });
    }

    private void openRingtonePicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        if (ringtoneUri != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);
        }
        startActivityForResult(intent, REQUEST_RINGTONE);
    }

    private boolean canRead(Uri uri) {
        // content://settings/... (nhạc mặc định hệ thống) không mở được bằng openInputStream
        // nhưng MediaPlayer vẫn phát được, nên không coi là lỗi.
        if ("settings".equals(uri.getAuthority())) return true;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void requestAudioPermissionIfNeeded() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERM_AUDIO);
        }
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
                android.media.Ringtone r = RingtoneManager.getRingtone(this, uri);
                b.tvRingtoneName.setText(r != null ? r.getTitle(this) : uri.getLastPathSegment());

                // Báo ngay nếu không mở được file: lúc báo thức reo nó sẽ âm thầm
                // quay về nhạc mặc định và rất khó hiểu tại sao.
                if (!canRead(uri)) {
                    Toast.makeText(this, getString(R.string.ringtone_unreadable),
                            Toast.LENGTH_LONG).show();
                }
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
        if (diffChecked == R.id.rbEasy) {
            alarm.difficulty = Alarm.DIFFICULTY_EASY;
        } else if (diffChecked == R.id.rbHard) {
            alarm.difficulty = Alarm.DIFFICULTY_HARD;
        } else if (diffChecked == R.id.rbCustom) {
            if (!saveCustomDifficulty()) return; // thiếu phép tính → ở lại để người dùng chọn
        } else {
            alarm.difficulty = Alarm.DIFFICULTY_MEDIUM;
        }

        alarm.isActive = true;

        repository.save(alarm, id -> runOnUiThread(() -> {
            setResult(RESULT_OK);
            finish();
        }));
    }

    /** Trả về false nếu người dùng chưa chọn phép tính nào cho thử thách Toán. */
    private boolean saveCustomDifficulty() {
        alarm.difficulty = Alarm.DIFFICULTY_CUSTOM;

        if (alarm.challengeType == Alarm.CHALLENGE_MATH) {
            int opsMask = 0;
            if (b.cbMathAdd.isChecked()) opsMask |= Alarm.OP_ADD;
            if (b.cbMathSub.isChecked()) opsMask |= Alarm.OP_SUB;
            if (b.cbMathMul.isChecked()) opsMask |= Alarm.OP_MUL;
            if (b.cbMathDiv.isChecked()) opsMask |= Alarm.OP_DIV;

            if (opsMask == 0) {
                Toast.makeText(this, getString(R.string.math_operators_required),
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            int questions = b.seekMathQuestions.getProgress() + MATH_QUESTIONS_MIN;
            alarm.customValue = Alarm.packMathCustom(opsMask, questions);
        } else {
            alarm.customValue = b.seekCustomValue.getProgress() + customMin();
        }
        return true;
    }
}
