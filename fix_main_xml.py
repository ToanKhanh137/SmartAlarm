import os
import re

file_path = 'app/src/main/res/layout/activity_main.xml'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add an Add button to pageClock
replacement = """            <RelativeLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:paddingTop="16dp" android:paddingBottom="12dp">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/nav_clock"
                    android:textSize="28sp"
                    android:textColor="@color/text_primary"
                    android:textStyle="bold"
                    android:layout_centerVertical="true"/>
                <ImageButton
                    android:id="@+id/btnAddCity"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:layout_alignParentEnd="true"
                    android:layout_centerVertical="true"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@drawable/ic_add"
                    app:tint="@color/accent"
                    android:contentDescription="Add City"/>
            </RelativeLayout>"""

content = re.sub(r'            <TextView\s*android:layout_width="wrap_content"\s*android:layout_height="wrap_content"\s*android:text="@string/nav_clock"\s*android:textSize="28sp"\s*android:textColor="@color/text_primary"\s*android:textStyle="bold"\s*android:paddingTop="16dp"\s*android:paddingBottom="12dp"/>', replacement, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
