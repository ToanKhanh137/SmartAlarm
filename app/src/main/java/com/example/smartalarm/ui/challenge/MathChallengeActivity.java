package com.example.smartalarm.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.smartalarm.R;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.service.AlarmReceiver;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MathChallengeActivity – giải toán để tắt báo thức.
 *
 * Adaptive difficulty:
 *  - Sai 1–2 lần: nhắc thử lại
 *  - Sai 3 lần: tự giảm 1 bậc độ khó
 *  - Sai 5+ lần: hỏi có muốn chuyển sang Lắc không
 */
public class MathChallengeActivity extends ChallengeActivity {

    private static final int WRONG_REDUCE_AT = 3;
    private static final int WRONG_OFFER_SWITCH_AT = 5;

    private int currentDifficulty = Alarm.DIFFICULTY_MEDIUM;
    private int opsMask = Alarm.OPS_DEFAULT;
    private int wrongCount = 0;

    private int targetCount = 1;
    private int currentCorrect = 0;
    private int correctAnswer;

    private TextView tvInstruction, tvQuestion, tvAttempts, tvProgress;
    private TextInputEditText etAnswer;
    private MaterialButton btnSubmit;
    private ProgressBar progressBar;

    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_math);

        tvInstruction = findViewById(R.id.tvInstruction);
        tvQuestion    = findViewById(R.id.tvQuestion);
        tvAttempts    = findViewById(R.id.tvAttempts);
        tvProgress    = findViewById(R.id.tvProgress);
        etAnswer      = findViewById(R.id.etAnswer);
        btnSubmit     = findViewById(R.id.btnSubmit);
        progressBar   = findViewById(R.id.progressBar);

        tvInstruction.setText(getString(R.string.math_instruction));
        btnSubmit.setOnClickListener(v -> checkAnswer());
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkAnswer();
                return true;
            }
            return false;
        });

        setupChallenge();
    }

    @Override
    protected void onAlarmLoaded(@Nullable Alarm alarm) {
        currentDifficulty = alarm != null ? alarm.difficulty : Alarm.DIFFICULTY_MEDIUM;

        if (currentDifficulty == Alarm.DIFFICULTY_CUSTOM && alarm != null) {
            targetCount = alarm.mathQuestionCount();
            opsMask = alarm.mathOpsMask();
        } else if (currentDifficulty == Alarm.DIFFICULTY_HARD) {
            targetCount = 3;
        } else {
            targetCount = 1;
        }

        progressBar.setMax(targetCount);
        updateProgress();
        generateQuestion();
    }

    private void updateProgress() {
        boolean multi = targetCount > 1;
        tvProgress.setVisibility(multi ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(multi ? View.VISIBLE : View.GONE);
        if (multi) {
            tvProgress.setText(getString(R.string.math_progress,
                    Math.min(currentCorrect + 1, targetCount), targetCount));
            progressBar.setProgress(currentCorrect);
        }
    }

    // ===== SINH ĐỀ =====

    private void generateQuestion() {
        String question = currentDifficulty == Alarm.DIFFICULTY_CUSTOM
                ? buildCustomQuestion()
                : buildQuestionForDifficulty(currentDifficulty);

        tvQuestion.setText(question);
        etAnswer.setText("");
        etAnswer.requestFocus();
    }

    private String buildQuestionForDifficulty(int difficulty) {
        switch (difficulty) {
            case Alarm.DIFFICULTY_EASY:   return buildEasy();
            case Alarm.DIFFICULTY_HARD:   return buildHard();
            default:                      return buildMedium();
        }
    }

    /** Hai toán hạng, cộng hoặc trừ, số nhỏ. */
    private String buildEasy() {
        int a = rand(2, 20);
        int b = rand(2, 20);
        if (random.nextBoolean()) {
            correctAnswer = a + b;
            return a + " + " + b + " = ?";
        }
        int big = Math.max(a, b), small = Math.min(a, b);
        correctAnswer = big - small;
        return big + " − " + small + " = ?";
    }

    /** Ba toán hạng, có nhân – không nhẩm ngay được nhưng vẫn làm trong đầu. */
    private String buildMedium() {
        switch (random.nextInt(3)) {
            case 0: {
                int a = rand(11, 29), b = rand(3, 9), c = rand(10, 99);
                boolean add = random.nextBoolean();
                correctAnswer = add ? a * b + c : a * b - c;
                return a + " × " + b + (add ? " + " : " − ") + c + " = ?";
            }
            case 1: {
                int a = rand(10, 99), b = rand(10, 99), c = rand(10, 99);
                correctAnswer = a + b + c;
                return a + " + " + b + " + " + c + " = ?";
            }
            default: {
                int b = rand(4, 12), result = rand(6, 40);
                int a = b * result;
                int d = rand(10, 60);
                boolean add = random.nextBoolean();
                correctAnswer = add ? result + d : result - d;
                return a + " ÷ " + b + (add ? " + " : " − ") + d + " = ?";
            }
        }
    }

    /** Bốn toán hạng hoặc số lớn – phải tính ra giấy hoặc tỉnh hẳn mới làm được. */
    private String buildHard() {
        switch (random.nextInt(4)) {
            case 0: {
                int a = rand(12, 24), b = rand(11, 19), c = rand(5, 15), d = rand(4, 12);
                boolean add = random.nextBoolean();
                correctAnswer = add ? a * b + c * d : a * b - c * d;
                return "(" + a + " × " + b + ")" + (add ? " + " : " − ") + "(" + c + " × " + d + ") = ?";
            }
            case 1: {
                int a = rand(23, 59), b = rand(7, 19), c = rand(100, 999);
                boolean add = random.nextBoolean();
                correctAnswer = add ? a * b + c : a * b - c;
                return a + " × " + b + (add ? " + " : " − ") + c + " = ?";
            }
            case 2: {
                int c = rand(6, 16), quotient = rand(11, 40);
                int a = c * quotient;
                int d = rand(11, 29), e = rand(3, 9);
                boolean add = random.nextBoolean();
                correctAnswer = add ? quotient + d * e : quotient - d * e;
                return "(" + a + " ÷ " + c + ")" + (add ? " + " : " − ") + "(" + d + " × " + e + ") = ?";
            }
            default: {
                int a = rand(13, 39), b = rand(13, 39);
                int c = rand(100, 500);
                correctAnswer = a * b - c;
                return a + " × " + b + " − " + c + " = ?";
            }
        }
    }

    /** Người dùng tự chọn tập phép tính trong phần Tùy chỉnh. */
    private String buildCustomQuestion() {
        List<Integer> ops = new ArrayList<>();
        if ((opsMask & Alarm.OP_ADD) != 0) ops.add(Alarm.OP_ADD);
        if ((opsMask & Alarm.OP_SUB) != 0) ops.add(Alarm.OP_SUB);
        if ((opsMask & Alarm.OP_MUL) != 0) ops.add(Alarm.OP_MUL);
        if ((opsMask & Alarm.OP_DIV) != 0) ops.add(Alarm.OP_DIV);
        if (ops.isEmpty()) ops.add(Alarm.OP_ADD);

        int op = ops.get(random.nextInt(ops.size()));
        switch (op) {
            case Alarm.OP_MUL: {
                int a = rand(12, 39), b = rand(4, 19);
                correctAnswer = a * b;
                return a + " × " + b + " = ?";
            }
            case Alarm.OP_DIV: {
                int b = rand(3, 19), quotient = rand(4, 40);
                int a = b * quotient;
                correctAnswer = quotient;
                return a + " ÷ " + b + " = ?";
            }
            case Alarm.OP_SUB: {
                int a = rand(30, 199), b = rand(10, 99);
                int big = Math.max(a, b), small = Math.min(a, b);
                correctAnswer = big - small;
                return big + " − " + small + " = ?";
            }
            default: {
                int a = rand(15, 199), b = rand(15, 199);
                correctAnswer = a + b;
                return a + " + " + b + " = ?";
            }
        }
    }

    private int rand(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    // ===== TRẢ LỜI =====

    private void checkAnswer() {
        String input = etAnswer.getText() != null ? etAnswer.getText().toString().trim() : "";
        if (input.isEmpty()) return;

        int answer;
        try {
            answer = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.math_invalid_input), Toast.LENGTH_SHORT).show();
            return;
        }

        if (answer != correctAnswer) {
            wrongCount++;
            handleWrongAnswer();
            return;
        }

        currentCorrect++;
        if (currentCorrect >= targetCount) {
            Toast.makeText(this, getString(R.string.math_correct), Toast.LENGTH_SHORT).show();
            dismissAlarm();
        } else {
            updateProgress();
            generateQuestion();
        }
    }

    private void handleWrongAnswer() {
        tvAttempts.setVisibility(View.VISIBLE);
        tvAttempts.setText(getString(R.string.math_attempts, wrongCount));
        etAnswer.setText("");

        if (wrongCount >= WRONG_OFFER_SWITCH_AT) {
            offerSwitchToShake();
            return;
        }

        if (wrongCount == WRONG_REDUCE_AT && currentDifficulty > Alarm.DIFFICULTY_EASY
                && currentDifficulty != Alarm.DIFFICULTY_CUSTOM) {
            currentDifficulty--;
            Toast.makeText(this, getString(R.string.math_wrong_reduce), Toast.LENGTH_LONG).show();
            generateQuestion();
            return;
        }

        Toast.makeText(this, getString(R.string.math_wrong), Toast.LENGTH_SHORT).show();
    }

    private void offerSwitchToShake() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.challenge_switch_title))
                .setMessage(getString(R.string.challenge_switch_message, wrongCount))
                .setPositiveButton(getString(R.string.math_switch_yes), (d, w) -> switchToShake())
                .setNegativeButton(getString(R.string.math_switch_no), (d, w) -> generateQuestion())
                .setCancelable(false)
                .show();
    }

    private void switchToShake() {
        Intent intent = new Intent(this, ShakeChallengeActivity.class);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        startActivity(intent);
        finish();
    }
}
