import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/ui/edit/EditAlarmActivity.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

import_statement = "import android.Manifest;\nimport android.content.pm.PackageManager;\nimport androidx.core.app.ActivityCompat;\nimport androidx.core.content.ContextCompat;\n"
if "import android.Manifest;" not in content:
    content = content.replace("import android.os.Bundle;", "import android.os.Bundle;\n" + import_statement)

# Replace setupChallengeRadio
setup_radio = """    private void setupChallengeRadio() {
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
"""

content = re.sub(r'    private void setupChallengeRadio\(\) \{.*?\n        b.rgDifficulty.setOnCheckedChangeListener\(\(group, checkedId\) -> \{[^\n]*\n            boolean showCustom = checkedId == R.id.rbCustom;', setup_radio, content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
