import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/service/UpcomingReceiver.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

replacement = """
            String title = context.getString(R.string.upcoming_channel_name);
            int hour = intent.getIntExtra("alarm_hour", 7);
            int minute = intent.getIntExtra("alarm_minute", 0);
            int challengeType = intent.getIntExtra("alarm_challenge_type", 0);
            
            String challengeName = "";
            switch (challengeType) {
                case 1: challengeName = context.getString(R.string.challenge_math); break; // CHALLENGE_MATH
                case 2: challengeName = context.getString(R.string.challenge_shake); break; // CHALLENGE_SHAKE
                case 3: challengeName = context.getString(R.string.challenge_squat); break; // CHALLENGE_SQUAT
                case 4: challengeName = context.getString(R.string.challenge_step); break; // CHALLENGE_STEP
                case 5: challengeName = context.getString(R.string.challenge_qr); break; // CHALLENGE_QR
                default: challengeName = context.getString(R.string.challenge_none); break; // CHALLENGE_NONE
            }
            
            String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute);
            String text = timeStr + " - " + challengeName;
            if (label != null && !label.isEmpty()) {
                text += " (" + label + ")";
            }
"""

content = re.sub(r'            String label = alarm.getLabel\(\);\s*String title = context.getString\(R.string.upcoming_channel_name\);\s*String challengeName = "";\s*switch \(alarm.getChallengeType\(\)\) \{.*?\n            \}\s*String timeStr = String.format\(java.util.Locale.getDefault\(\), "%02d:%02d", alarm.getHour\(\), alarm.getMinute\(\)\);\s*String text = timeStr \+ " - " \+ challengeName;\s*if \(label != null && !label.isEmpty\(\)\) \{\s*text \+= " \(" \+ label \+ "\)";\s*\}', replacement, content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
