import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/ui/edit/EditAlarmActivity.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace b.llCustomValue.setVisibility... with new logic
content = content.replace("b.llCustomValue.setVisibility(showCustom ? View.VISIBLE : View.GONE);", """if (showCustom) {
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
              }""")

# Replace setupCustomSlider() body
setup_custom = """private void setupCustomSlider() {
          int checkedChallenge = b.rgChallenge.getCheckedRadioButtonId();
          if (checkedChallenge == R.id.rbMath) {
              int current = alarm != null && alarm.customValue > 0 ? alarm.customValue : 0;
              int questions = current > 0 ? (current % 1000) : 5;
              int ops = current > 0 ? (current / 1000) : 3;
              if (questions > 20 || questions < 1) questions = 5;
              b.seekMathQuestions.setProgress(questions);
              b.tvMathQuestions.setText(String.valueOf(questions));
              b.seekMathQuestions.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                  @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                      b.tvMathQuestions.setText(String.valueOf(Math.max(1, p)));
                  }
                  @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
                  @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
              });
              b.cbMathAdd.setChecked((ops & 1) != 0);
              b.cbMathSub.setChecked((ops & 2) != 0);
              b.cbMathMul.setChecked((ops & 4) != 0);
              b.cbMathDiv.setChecked((ops & 8) != 0);
              if (ops == 0) { b.cbMathAdd.setChecked(true); b.cbMathSub.setChecked(true); }
              return;
          }
          
          int max = 50;
          int min = 5;
          String label = "Số lần:";
          if (checkedChallenge == R.id.rbSquat) { max = 30; min = 3; label = "Số lần squat:"; }
          else if (checkedChallenge == R.id.rbStep) { max = 200; min = 10; label = "Số bước chân:"; }
          else if (checkedChallenge == R.id.rbShake) { label = "Số lần lắc:"; }
          
          b.tvCustomLabel.setText(label);
          b.seekCustomValue.setMax(max - min);
          int current = alarm != null && alarm.customValue > 0 ? alarm.customValue : (max + min) / 2;
          b.seekCustomValue.setProgress(current - min);
          b.tvCustomValue.setText(String.valueOf(current));
          int finalMin = min;
          b.seekCustomValue.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
              @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                  b.tvCustomValue.setText(String.valueOf(p + finalMin));
              }
              @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
              @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
          });
      }"""

content = re.sub(r'private void setupCustomSlider\(\) \{.*?\n      \}', setup_custom, content, flags=re.DOTALL)

# Replace saveAlarm customValue saving logic
save_alarm_logic = """if (b.rgDifficulty.getCheckedRadioButtonId() == R.id.rbCustom) {
              int checkedChallenge = b.rgChallenge.getCheckedRadioButtonId();
              if (checkedChallenge == R.id.rbMath) {
                  int questions = Math.max(1, b.seekMathQuestions.getProgress());
                  int ops = 0;
                  if (b.cbMathAdd.isChecked()) ops |= 1;
                  if (b.cbMathSub.isChecked()) ops |= 2;
                  if (b.cbMathMul.isChecked()) ops |= 4;
                  if (b.cbMathDiv.isChecked()) ops |= 8;
                  if (ops == 0) ops = 1;
                  alarm.customValue = questions + (ops * 1000);
              } else {
                  alarm.customValue = Integer.parseInt(b.tvCustomValue.getText().toString());
              }
          }"""

content = re.sub(r'if \(b.rgDifficulty.getCheckedRadioButtonId\(\) == R.id.rbCustom\) \{\s*alarm.customValue = Integer.parseInt\(b.tvCustomValue.getText\(\).toString\(\)\);\s*\}', save_alarm_logic, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
