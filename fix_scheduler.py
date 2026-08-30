import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/service/AlarmScheduler.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_LABEL, alarm.label);", "upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_LABEL, alarm.label);\n            upIntent.putExtra(\"alarm_hour\", alarm.hour);\n            upIntent.putExtra(\"alarm_minute\", alarm.minute);\n            upIntent.putExtra(\"alarm_challenge_type\", alarm.challengeType);")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
