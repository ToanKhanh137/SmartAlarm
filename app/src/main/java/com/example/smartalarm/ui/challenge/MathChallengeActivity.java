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
    private int customOpsMask = 3;

    private TextView tvInstruction, tvQuestion, tvAttempts;
    private EditText etAnswer;
    private Button btnSubmit;

    private int correctAnswer;
    private final Random random = new Random();
    private int targetCount = 1;
    private int currentCorrect = 0;
    
    private Handler fallbackHandler = new Handler(Looper.getMainLooper());
    private Runnable fallbackRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_math);

        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        
        fallbackRunnable = () -> {
            Button btnFallback = new Button(this);
            btnFallback.setText("Bỏ qua thử thách");
            btnFallback.setBackgroundColor(android.graphics.Color.RED);
            btnFallback.setTextColor(android.graphics.Color.WHITE);
            btnFallback.setOnClickListener(v -> dismissAlarm());
            
            android.view.ViewGroup root = (android.view.ViewGroup) ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            if (root instanceof android.widget.LinearLayout) {
                root.addView(btnFallback);
            } else if (root instanceof android.widget.RelativeLayout) {
                android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, 
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
                root.addView(btnFallback, params);
            }
        };
        fallbackHandler.postDelayed(fallbackRunnable, 60000);

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
            
            if (currentDifficulty == Alarm.DIFFICULTY_CUSTOM && alarm != null) {
                int val = alarm.customValue;
                targetCount = val > 0 ? (val % 1000) : 5;
                customOpsMask = val > 0 ? (val / 1000) : 3;
            } else if (currentDifficulty == Alarm.DIFFICULTY_HARD) {
                targetCount = 3;
            }
            
            runOnUiThread(() -> {
                updateInstruction();
                generateQuestion();
            });
        });

        btnSubmit.setOnClickListener(v -> checkAnswer());
    }

    private void updateInstruction() {
        if (targetCount > 1) {
            tvInstruction.setText(getString(R.string.math_instruction) + " (" + currentCorrect + "/" + targetCount + ")");
        } else {
            tvInstruction.setText(getString(R.string.math_instruction));
        }
    }

    private void generateQuestion() {
        String question;
        int diffToUse = currentDifficulty;
        if (diffToUse == Alarm.DIFFICULTY_CUSTOM) {
            java.util.List<Integer> ops = new java.util.ArrayList<>();
            if ((customOpsMask & 1) != 0) ops.add(0);
            if ((customOpsMask & 2) != 0) ops.add(1);
            if ((customOpsMask & 4) != 0) ops.add(2);
            if ((customOpsMask & 8) != 0) ops.add(3);
            if (ops.isEmpty()) ops.add(0);
            
            int op = ops.get(random.nextInt(ops.size()));
            int a, b;
            if (op == 0 || op == 1) {
                a = random.nextInt(90) + 10;
                b = random.nextInt(90) + 10;
            } else {
                a = random.nextInt(10) + 2;
                b = random.nextInt(10) + 2;
            }
            
            if (op == 0) {
                correctAnswer = a + b;
                question = a + " + " + b + " = ?";
            } else if (op == 1) {
                if (a < b) { int t = a; a = b; b = t; }
                correctAnswer = a - b;
                question = a + " − " + b + " = ?";
            } else if (op == 2) {
                correctAnswer = a * b;
                question = a + " × " + b + " = ?";
            } else {
                correctAnswer = a;
                a = a * b;
                question = a + " ÷ " + b + " = ?";
            }
        } else {
            switch (diffToUse) {
                case Alarm.DIFFICULTY_EASY:
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
                    int a3 = random.nextInt(19) + 2;
                    int b3 = random.nextInt(19) + 2;
                    int c3 = random.nextInt(99) + 1;
                    boolean add = random.nextBoolean();
                    question = "(" + a3 + " × " + b3 + ") " + (add ? "+ " : "− ") + c3 + " = ?";
                    correctAnswer = add ? (a3 * b3 + c3) : (a3 * b3 - c3);
                    break;
    
                default: // MEDIUM (1)
                    if (random.nextBoolean()) {
                        int a2 = random.nextInt(90) + 10;
                        int b2 = random.nextInt(8) + 2;
                        question = a2 + " × " + b2 + " = ?";
                        correctAnswer = a2 * b2;
                    } else {
                        int a2 = random.nextInt(90) + 10;
                        int b2 = random.nextInt(90) + 10;
                        int c2 = random.nextInt(90) + 10;
                        question = a2 + " + " + b2 + " + " + c2 + " = ?";
                        correctAnswer = a2 + b2 + c2;
                    }
                    break;
            }
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
                // Đúng
                currentCorrect++;
                if (currentCorrect >= targetCount) {
                    Toast.makeText(this, getString(R.string.math_correct), Toast.LENGTH_SHORT).show();
                    dismissAlarm();
                } else {
                    updateInstruction();
                    generateQuestion();
                }
            } else {
                // Sai
                wrongCount++;
                handleWrongAnswer();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.math_invalid_input), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWrongAnswer() {
        tvAttempts.setVisibility(View.VISIBLE);
        tvAttempts.setText(getString(R.string.math_attempts, wrongCount));

        if (wrongCount >= 5) {
            // Đề xuất chuyển sang lắc
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Khó quá?")
                    .setMessage(getString(R.string.math_wrong_switch))
                    .setPositiveButton(getString(R.string.math_switch_yes), (d, w) -> switchToShake())
                    .setNegativeButton(getString(R.string.math_switch_no), (d, w) -> generateQuestion())
                    .setCancelable(false)
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fallbackHandler != null && fallbackRunnable != null) {
            fallbackHandler.removeCallbacks(fallbackRunnable);
        }
    }
}

