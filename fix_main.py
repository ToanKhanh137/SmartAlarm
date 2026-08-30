import os
import re

file_path = 'app/src/main/java/com/example/smartalarm/ui/main/MainActivity.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

import_statement = "import android.content.SharedPreferences;\nimport org.json.JSONArray;\nimport org.json.JSONObject;\nimport java.util.ArrayList;\nimport java.util.List;\n"
if "import org.json.JSONArray;" not in content:
    content = content.replace("import android.os.Bundle;", "import android.os.Bundle;\n" + import_statement)

# Add members
members = """    private List<WorldClockAdapter.Clock> clocks;
    private WorldClockAdapter clockAdapter;
"""
content = re.sub(r'    private ActivityMainBinding b;\n    private AlarmAdapter alarmAdapter;', r'    private ActivityMainBinding b;\n    private AlarmAdapter alarmAdapter;\n' + members, content)

# Setup world clocks logic
setup_clocks = """    private void setupWorldClocks() {
        clocks = loadClocks();
        clockAdapter = new WorldClockAdapter(clocks);
        b.rvWorldClocks.setLayoutManager(new LinearLayoutManager(this));
        b.rvWorldClocks.setAdapter(clockAdapter);
        
        b.getRoot().findViewById(R.id.btnAddCity).setOnClickListener(v -> {
            String[] cities = {"New York", "London", "Tokyo", "Paris", "Sydney", "Dubai", "Singapore", "Berlin", "Seoul", "Moscow", "Los Angeles", "Chicago", "Hong Kong", "Bangkok", "Istanbul", "Mumbai", "Sao Paulo", "Buenos Aires", "Cairo", "Johannesburg", "Ho Chi Minh"};
            String[] ids = {"America/New_York", "Europe/London", "Asia/Tokyo", "Europe/Paris", "Australia/Sydney", "Asia/Dubai", "Asia/Singapore", "Europe/Berlin", "Asia/Seoul", "Europe/Moscow", "America/Los_Angeles", "America/Chicago", "Asia/Hong_Kong", "Asia/Bangkok", "Europe/Istanbul", "Asia/Kolkata", "America/Sao_Paulo", "America/Argentina/Buenos_Aires", "Africa/Cairo", "Africa/Johannesburg", "Asia/Ho_Chi_Minh"};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Add City")
                .setItems(cities, (dialog, which) -> {
                    clocks.add(new WorldClockAdapter.Clock(cities[which], ids[which]));
                    clockAdapter.notifyItemInserted(clocks.size() - 1);
                    saveClocks(clocks);
                })
                .show();
        });
    }

    private List<WorldClockAdapter.Clock> loadClocks() {
        SharedPreferences prefs = getSharedPreferences("world_clocks", MODE_PRIVATE);
        String json = prefs.getString("clocks_json", null);
        List<WorldClockAdapter.Clock> list = new ArrayList<>();
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new WorldClockAdapter.Clock(obj.getString("name"), obj.getString("id")));
                }
            } catch (Exception e) {}
        }
        if (list.isEmpty()) {
            list.add(new WorldClockAdapter.Clock("New York", "America/New_York"));
            list.add(new WorldClockAdapter.Clock("London", "Europe/London"));
            list.add(new WorldClockAdapter.Clock("Tokyo", "Asia/Tokyo"));
        }
        return list;
    }

    private void saveClocks(List<WorldClockAdapter.Clock> list) {
        try {
            JSONArray arr = new JSONArray();
            for (WorldClockAdapter.Clock c : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", c.cityName);
                obj.put("id", c.timeZoneId);
                arr.put(obj);
            }
            getSharedPreferences("world_clocks", MODE_PRIVATE).edit().putString("clocks_json", arr.toString()).apply();
        } catch (Exception e) {}
    }"""

content = re.sub(r'    private void setupWorldClocks\(\) \{.*?\}\s*(?=    private void setupTimer)', setup_clocks + '\n\n', content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
