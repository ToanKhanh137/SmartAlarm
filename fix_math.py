import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/ui/challenge/MathChallengeActivity.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add a field for ops mask
content = re.sub(r'private int wrongCount = 0;', 'private int wrongCount = 0;\n    private int customOpsMask = 3;', content)

# In executors
exec_logic = """if (currentDifficulty == Alarm.DIFFICULTY_CUSTOM && alarm != null) {
                  int val = alarm.customValue;
                  targetCount = val > 0 ? (val % 1000) : 5;
                  customOpsMask = val > 0 ? (val / 1000) : 3;
              } else if (currentDifficulty == Alarm.DIFFICULTY_HARD) {"""
content = re.sub(r'if \(currentDifficulty == Alarm.DIFFICULTY_CUSTOM && alarm != null\) \{\s*targetCount = alarm.customValue > 0 \? alarm.customValue : 5;\s*\} else if \(currentDifficulty == Alarm.DIFFICULTY_HARD\) \{', exec_logic, content)

# In generateQuestion(), handle CUSTOM operations
# We need to map diffToUse = Alarm.DIFFICULTY_EASY or MEDIUM or HARD based on the bits in customOpsMask?
# No! For custom, we just want to pick operations based on customOpsMask!
# The simplest way is to add a case Alarm.DIFFICULTY_CUSTOM: inside generateQuestion's switch, instead of mapping it.

gen_logic = """        if (diffToUse == Alarm.DIFFICULTY_CUSTOM) {
            java.util.List<Integer> ops = new java.util.ArrayList<>();
            if ((customOpsMask & 1) != 0) ops.add(0); // +
            if ((customOpsMask & 2) != 0) ops.add(1); // -
            if ((customOpsMask & 4) != 0) ops.add(2); // *
            if ((customOpsMask & 8) != 0) ops.add(3); // /
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
                currentAnswer = a + b;
                question = a + " + " + b;
            } else if (op == 1) {
                if (a < b) { int t = a; a = b; b = t; }
                currentAnswer = a - b;
                question = a + " - " + b;
            } else if (op == 2) {
                currentAnswer = a * b;
                question = a + " × " + b;
            } else {
                currentAnswer = a;
                a = a * b;
                question = a + " ÷ " + b;
            }
        } else {
            switch (diffToUse) {"""

content = re.sub(r'        if \(diffToUse == Alarm.DIFFICULTY_CUSTOM\) \{\s*diffToUse = random.nextInt\(3\);\s*\}\s*switch \(diffToUse\) \{', gen_logic, content)

# Close the else block inside generateQuestion()
content = re.sub(r'            case Alarm.DIFFICULTY_HARD:[\s\S]*?break;\s*\}', r'\g<0>\n        }', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
