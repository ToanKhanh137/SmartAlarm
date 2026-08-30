import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/ui/edit/EditAlarmActivity.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("b.btnBack.setVisibility(currentStep > 0 ? android.view.View.VISIBLE : android.view.View.INVISIBLE);", "b.btnBack.setVisibility(currentStep > 0 ? android.view.View.VISIBLE : android.view.View.GONE);")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
