import os

def add_fallback_to_activity(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    if "fallbackHandler" in content:
        return # already added

    import_handler = "import android.os.Handler;\nimport android.os.Looper;\nimport android.widget.Button;\nimport android.view.View;\n"
    if "import android.os.Handler;" not in content:
        content = content.replace("import android.os.Bundle;", "import android.os.Bundle;\n" + import_handler)

    handler_declaration = "    private Handler fallbackHandler = new Handler(Looper.getMainLooper());\n    private Runnable fallbackRunnable;\n"
    import re
    content = re.sub(r'(public class .*? \{)', r'\1\n' + handler_declaration, content, count=1)

    on_create_addition = """
        fallbackRunnable = () -> {
            Button btnFallback = new Button(this);
            btnFallback.setText("Bỏ qua thử thách");
            btnFallback.setBackgroundColor(android.graphics.Color.RED);
            btnFallback.setTextColor(android.graphics.Color.WHITE);
            btnFallback.setOnClickListener(v -> dismissAlarm());
            
            // Add to root layout
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
        fallbackHandler.postDelayed(fallbackRunnable, 60000); // 60 seconds timeout
"""
    content = content.replace("super.onCreate(savedInstanceState);", "super.onCreate(savedInstanceState);\n" + on_create_addition)

    on_destroy = """
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fallbackHandler != null && fallbackRunnable != null) {
            fallbackHandler.removeCallbacks(fallbackRunnable);
        }
    }
}
"""
    # Find the last closing brace and replace it with onDestroy
    idx = content.rfind("}")
    if idx != -1:
        content = content[:idx] + on_destroy
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

activities = [
    'app/src/main/java/com/example/smartalarm/ui/challenge/MathChallengeActivity.java',
    'app/src/main/java/com/example/smartalarm/ui/challenge/ShakeChallengeActivity.java',
    'app/src/main/java/com/example/smartalarm/ui/challenge/SquatChallengeActivity.java',
    'app/src/main/java/com/example/smartalarm/ui/challenge/StepChallengeActivity.java'
]

for act in activities:
    add_fallback_to_activity(act)

print("Done")
