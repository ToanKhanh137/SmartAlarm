import os

file_path = 'app/src/main/res/layout/activity_edit_alarm.xml'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

replacements = {
    'android:text="Lặp lại"': 'android:text="@string/repeat_label"',
    'android:text="Thời gian hoãn"': 'android:text="@string/snooze_time_label"',
    'android:text=" phút"': 'android:text="@string/minutes_unit"',
    'android:text="Thử thách thức dậy"': 'android:text="@string/challenge_title"',
    'android:text="Chọn cách bạn muốn tắt báo thức"': 'android:text="@string/challenge_subtitle"',
    'android:text="Tắt bình thường"': 'android:text="@string/challenge_none"',
    'android:text="Chỉ cần nhấn nút để tắt"': 'android:text="@string/challenge_none_desc"',
    'android:text="Giải toán"': 'android:text="@string/challenge_math"',
    'android:text="Giải phép tính để tắt báo thức"': 'android:text="@string/challenge_math_desc"',
    'android:text="Lắc điện thoại"': 'android:text="@string/challenge_shake"',
    'android:text="Lắc mạnh một số lần để tắt"': 'android:text="@string/challenge_shake_desc"',
    'android:text="Squat"': 'android:text="@string/challenge_squat"',
    'android:text="Thực hiện squat để tắt báo thức"': 'android:text="@string/challenge_squat_desc"',
    'android:text="Đếm bước chân"': 'android:text="@string/challenge_step"',
    'android:text="Đi bộ một đoạn ngắn để tắt"': 'android:text="@string/challenge_step_desc"',
    'android:text="Quét QR"': 'android:text="@string/challenge_qr"',
    'android:text="Quét mã QR đặt ở nơi xa giường"': 'android:text="@string/challenge_qr_desc"'
}

for k, v in replacements.items():
    content = content.replace(k, v)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
