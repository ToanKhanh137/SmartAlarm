package com.example.smartalarm.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smartalarm.R;
import com.example.smartalarm.data.database.AppDatabase;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.ui.common.BaseActivity;
import com.example.smartalarm.ui.ring.RingActivity;

import java.util.Random;
import java.util.concurrent.Executors;

/**
 * MathChallengeActivity – giải toán để tắt báo thức.
 *
 * Adaptive difficulty:
 *  - Sai 1–2 lần: Toast "Thử lại"
 *  - Sai 3 lần: tự giảm 1 bậc độ khó + thông báo
 *  - Sai 5+ lần: hỏi có muốn chuyển sang Lắc không
 */
public class MathChallengeActivity extends BaseActivity {

    private int alarmId;
    private int currentDifficulty;
    private int wrongCount = 0;

    private TextView tvInstruction, tvQuestion, tvAttempts;
    private EditText etAnswer;
    private Button btnSubmit;

    private int correctAnswer;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_math);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);

        tvInstruction = findViewById(R.id.tvInstruction);
        tvQuestion    = findViewById(R.id.tvQuestion);
        tvAttempts    = findViewById(R.id.tvAttempts);
        etAnswer      = findViewById(R.id.etAnswer);
        btnSubmit     = findViewById(R.id.btnSubmit);

        tvInstruction.setText(getString(R.string.math_instruction));
        tvAttempts.setVisibility(View.GONE);

        // Load difficulty từ DB
        Executors.newSingleThreadExecutor().execute(() -> {
            Alarm alarm = AppDatabase.getInstance(this).alarmDao().getByIdSync(alarmId);
            currentDifficulty = alarm != null ? alarm.difficulty : Alarm.DIFFICULTY_MEDIUM;
            runOnUiThread(this::generateQuestion);
        });

        btnSubmit.setOnClickListener(v -> checkAnswer());
    }

    private void generateQuestion() {
        String question;
        switch (currentDifficulty) {
            case Alarm.DIFFICULTY_EASY:
                // Cộng/trừ 1–10
                int a1 = random.nextInt(10) + 1;
                int b1 = random.nextInt(10) + 1;
                if (random.nextBoolean()) {
                    question = a1 + " + " + b1 + " = ?";
                    correctAnswer = a1 + b1;
                } else {
                    int big = Math.max(a1, b1), small = Math.min(a1, b1);
                    question = big + " − " + small + " = ?";
                    correctAnswer = big - small;
                }
                break;

            case Alarm.DIFFICULTY_HARD:
                // Đa bước: (a × b) + c
                int a3 = random.nextInt(12) + 2;
                int b3 = random.nextInt(12) + 2;
                int c3 = random.nextInt(50) + 1;
                boolean add = random.nextBoolean();
                question = "(" + a3 + " × " + b3 + ") " + (add ? "+ " : "− ") + c3 + " = ?";
                correctAnswer = add ? (a3 * b3 + c3) : (a3 * b3 - c3);
                break;

            default: // MEDIUM
                // Nhân/chia 2 chữ số
                int a2 = random.nextInt(12) + 2;
                int b2 = random.nextInt(12) + 2;
                if (random.nextBoolean()) {
                    question = a2 + " × " + b2 + " = ?";
                    correctAnswer = a2 * b2;
                } else {
                    int product = a2 * b2;
                    question = product + " ÷ " + a2 + " = ?";
                    correctAnswer = b2;
                }
                break;
        }
        tvQuestion.setText(question);
        etAnswer.setText("");
        etAnswer.requestFocus();
    }

    private void checkAnswer() {
        String input = etAnswer.getText() != null ? etAnswer.getText().toString().trim() : "";
        if (input.isEmpty()) return;

        try {
            int answer = Integer.parseInt(input);
            if (answer == correctAnswer) {
                // Đúng → dismiss
                Toast.makeText(this, getString(R.string.math_correct), Toast.LENGTH_SHORT).show();
                dismissAlarm();
            } else {
                // Sai
                wrongCount++;
                handleWrongAnswer();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Nhập số hợp lệ", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWrongAnswer() {
        tvAttempts.setVisibility(View.VISIBLE);
        tvAttempts.setText(getString(R.string.math_attempts, wrongCount));

        if (wrongCount >= 5) {
            // Đề xuất chuyển sang lắc
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Khó quá?")
                    .setMessage(getString(R.string.math_wrong_switch))
                    .setPositiveButton(getString(R.string.math_switch_yes), (d, w) -> switchToShake())
                    .setNegativeButton(getString(R.string.math_switch_no), (d, w) -> generateQuestion())
                    .show();
        } else if (wrongCount == 3) {
            // Giảm độ khó
            if (currentDifficulty > Alarm.DIFFICULTY_EASY) {
                currentDifficulty--;
                Toast.makeText(this, getString(R.string.math_wrong_reduce), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.math_wrong), Toast.LENGTH_SHORT).show();
            }
            generateQuestion();
        } else {
            Toast.makeText(this, getString(R.string.math_wrong), Toast.LENGTH_SHORT).show();
            etAnswer.setText("");
        }
    }

    private void switchToShake() {
        Intent intent = new Intent(this, ShakeChallengeActivity.class);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        startActivity(intent);
        finish();
    }

    private void dismissAlarm() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_DISMISS);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        sendBroadcast(intent);
        finish();
        // Finish RingActivity cũng
        finishAffinity();
    }

    @Override
    public void onBackPressed() { /* Không cho back */ }
}
