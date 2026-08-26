# PROJECT TRACKER – Smart Alarm Android App

> **Mục đích file này**: Ghi lại toàn bộ quyết định, tiến độ, kiến trúc để bất kỳ AI model nào cũng có thể đọc và tiếp tục công việc.
> **Cập nhật**: Sau mỗi lần hoàn thành một nhóm file.

---

## 📌 Thông tin cơ bản

| Mục | Giá trị |
|---|---|
| Tên app | **Smart Alarm** |
| Package name | `com.example.smartalarm` |
| Ngôn ngữ | **Java** |
| Build script | **Groovy DSL** (`.gradle`, không phải `.gradle.kts`) |
| UI | **XML + View Binding** (KHÔNG dùng Jetpack Compose) |
| Min SDK | **API 26** (Android 8.0 Oreo) |
| Target SDK | **API 28** (Android 9.0 Pie) |
| Project path | `D:\Android\SmartAlarm` |
| Cơ sở tham khảo | https://github.com/Phongdzfk/samsung-alarm |

---

## 🗂 Kiến trúc tổng thể (MVVM)

```
com.example.smartalarm/
│
├── data/
│   ├── model/
│   │   └── Alarm.java                  # Room Entity – toàn bộ thuộc tính báo thức
│   ├── database/
│   │   ├── AlarmDao.java               # DAO: insert/update/delete/query
│   │   └── AppDatabase.java            # Room singleton, version 1
│   └── repository/
│       └── AlarmRepository.java        # Trung gian DB ↔ UI ↔ Service
│
├── service/
│   ├── AlarmScheduler.java             # Tính giờ, gọi AlarmManager.setAlarmClock()
│   ├── AlarmReceiver.java              # BroadcastReceiver: trigger / snooze / dismiss / disable
│   ├── AlarmRingingService.java        # Foreground Service: phát nhạc, rung, full-screen
│   ├── BootReceiver.java               # Khôi phục báo thức sau reboot / time change
│   └── StepCounterService.java         # Foreground Service đếm bước chân (pedometer challenge)
│
├── settings/
│   └── AppPreferences.java             # SharedPreferences wrapper (theme, ngôn ngữ, snooze toggle)
│
└── ui/
    ├── common/
    │   └── BaseActivity.java           # Xử lý locale (ngôn ngữ) chung cho mọi Activity
    ├── main/
    │   ├── MainActivity.java           # Màn hình chính, Bottom Navigation 4 tab
    │   ├── AlarmAdapter.java           # RecyclerView adapter cho danh sách báo thức
    │   └── TimerController.java        # Logic Timer + Stopwatch (fragment/controller)
    ├── edit/
    │   └── EditAlarmActivity.java      # Tạo / sửa báo thức – Stepper 3 bước
    ├── ring/
    │   └── RingActivity.java           # Màn hình reo: full-screen, nút Snooze + Thức dậy
    ├── challenge/
    │   ├── MathChallengeActivity.java   # Giải toán – adaptive difficulty
    │   ├── ShakeChallengeActivity.java  # Lắc điện thoại – accelerometer
    │   ├── SquatChallengeActivity.java  # Squat – accelerometer nhịp lên-xuống
    │   ├── QrChallengeActivity.java     # Quét QR – ZXing
    │   └── StepChallengeActivity.java   # Đếm bước chân – step counter sensor
    ├── qr/
    │   └── QrGeneratorActivity.java    # Tạo, hiển thị, lưu, in QR code
    └── settings/
        └── SettingsActivity.java       # Cài đặt: theme, ngôn ngữ, snooze toggle, snooze duration
```

---

## 📦 Thư viện (dependencies) – app/build.gradle

```groovy
// Room Database
implementation "androidx.room:room-runtime:2.6.1"
annotationProcessor "androidx.room:room-compiler:2.6.1"

// ViewModel + LiveData
implementation "androidx.lifecycle:lifecycle-viewmodel:2.7.0"
implementation "androidx.lifecycle:lifecycle-livedata:2.7.0"

// ZXing – tạo và quét QR
implementation "com.journeyapps:zxing-android-embedded:4.3.0"

// RecyclerView
implementation "androidx.recyclerview:recyclerview:1.3.2"

// CardView
implementation "androidx.cardview:cardview:1.0.0"

// Material Components (Material 3 style trên XML)
implementation "com.google.android.material:material:1.11.0"

// ViewBinding – bật trong build.gradle (android block):
// viewBinding { enabled = true }
```

---

## 🔐 Permissions – AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>           <!-- API 32+ -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>      <!-- API 31+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>         <!-- API 33+ -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION"/>       <!-- pedometer -->
<uses-permission android:name="android.permission.CAMERA"/>                     <!-- QR scan -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>
```

---

## 🎨 Thiết kế UI – Nguyên tắc

- **1 màn hình = 1 mục đích** – không nhét quá nhiều thứ
- Dùng **icon** thay text khi có thể
- **EditAlarm chia 3 bước** (ViewPager/Stepper): `Giờ & Ngày` → `Âm thanh` → `Thử thách`
- Màn hình **Reo**: full-screen tối, tối đa 2 nút (Snooze + Thức dậy)
- Màn hình **Challenge**: 1 focus, progress bar, không có back button
- **Settings**: 2 nhóm, tối đa ~10 dòng

### Theme màu
- Nền tối: `#121212`
- Surface card: `#1E1E2E`
- Accent chính: `#7C6FF7` (tím xanh)
- Text chính: `#FFFFFF`
- Text phụ: `#9E9E9E`
- Màu nguy hiểm (xóa): `#FF5252`

### Card báo thức
```
┌─────────────────────────────────┐
│  06:30              [  ●  ]     │  ← Giờ to, toggle bật/tắt
│  Đi làm                         │  ← Label
│  T2 T3 T4 T5 T6    🔢           │  ← Ngày lặp + icon challenge
└─────────────────────────────────┘
```

---

## 🧠 Hệ thống Adaptive Challenge

Khi người dùng sai nhiều lần:
```
Sai lần 1–2  → Toast "Thử lại!"
Sai lần 3    → Tự giảm 1 bậc độ khó (ví dụ: Hard → Medium)
Sai lần 5+   → Dialog: "Chuyển sang Lắc điện thoại?" (luôn có cách thoát)
```

### Bảng độ khó mặc định

| Challenge | Easy | Medium (default) | Hard | Custom |
|---|---|---|---|---|
| 🔢 Toán | Cộng/trừ 1–10 | Nhân/chia ≤99 | Đa bước ≥100 | Chọn phép + range |
| 📳 Lắc | 10 lần | 20 lần | 35 lần | Slider 5–50 |
| 🏋️ Squat | 5 lần | 10 lần | 20 lần | Slider 3–30 |
| 👟 Bước chân | 20 bước | 50 bước | 100 bước | Slider 10–200 |
| 📷 QR | Quét đúng mã | Quét đúng mã | Quét đúng mã | Nhiều mã vị trí khác nhau |

> **Squat**: User phải CẦM điện thoại trên tay – accelerometer detect nhịp lên-xuống biên độ lớn.

---

## ⚙️ Settings – Danh sách cài đặt toàn cục (AppPreferences.java)

| Key | Type | Default | Mô tả |
|---|---|---|---|
| `theme` | String | `"dark"` | `"dark"` / `"light"` |
| `language` | String | `"vi"` | `"vi"` / `"en"` |
| `snooze_enabled` | boolean | `true` | Bật/tắt snooze toàn cục |
| `snooze_duration` | int | `5` | Số phút snooze mặc định |

---

## 📋 Tiến độ thực hiện

### Ký hiệu
| Ký hiệu | Ý nghĩa |
|---|---|
| ⬜ | Chưa làm |
| 🔄 | Đang làm |
| ✅ | Hoàn thành |
| ❌ | Bỏ qua |
| ⚠️ | Có vấn đề |

---

### Phase 1 – Nền tảng

| # | File / Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 1 | `app/build.gradle` – thêm dependencies | ✅ | Room, ZXing, Material, RecyclerView |
| 2 | `AndroidManifest.xml` – permissions + components | ✅ | |
| 3 | `res/values/colors.xml` | ✅ | Dark theme palette |
| 4 | `res/values/themes.xml` | ✅ | Material3 dark theme |
| 5 | `res/values/strings.xml` | ✅ | Strings tiếng Việt |
| 6 | `res/values-en/strings.xml` | ✅ | Strings tiếng Anh |
| 7 | `Alarm.java` – Room Entity | ✅ | Toàn bộ thuộc tính |
| 8 | `AlarmDao.java` | ✅ | insert/update/delete/getAll/getActive/getById |
| 9 | `AppDatabase.java` | ✅ | Singleton, version 1 |
| 10 | `AppPreferences.java` | ✅ | SharedPreferences wrapper |
| 11 | `AlarmRepository.java` | ✅ | save/delete/setActive/snooze/reschedule |
| 12 | `AlarmScheduler.java` | ✅ | schedule/cancel/calculateNext |
| 13 | `AlarmReceiver.java` | ✅ | trigger/snooze/dismiss/disable actions |
| 14 | `AlarmRingingService.java` | ✅ | Foreground service, MediaPlayer, Vibrator |
| 15 | `BootReceiver.java` | ✅ | BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED |
| 16 | `BaseActivity.java` | ✅ | Locale wrapper |
| 17 | `activity_main.xml` | ✅ | Bottom Navigation + ViewPager |
| 18 | `MainActivity.java` | ✅ | 4 tabs: Alarm, Clock, Timer, Stopwatch |
| 19 | `item_alarm.xml` | ✅ | Card layout |
| 20 | `AlarmAdapter.java` | ✅ | RecyclerView adapter |
| 21 | `activity_edit_alarm.xml` | ✅ | Stepper 3 bước |
| 22 | `EditAlarmActivity.java` | ✅ | Tạo/sửa báo thức |
| 23 | `activity_ring.xml` | ✅ | Full-screen, clock lớn |
| 24 | `RingActivity.java` | ✅ | Show on lock screen, 2 nút |

### Phase 2 – Wake-up Challenges

| # | File / Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 25 | `MathChallengeActivity.java` + layout | ✅ | Adaptive, 3 mức |
| 26 | `ShakeChallengeActivity.java` + layout | ✅ | Accelerometer |
| 27 | `SquatChallengeActivity.java` + layout | ✅ | Accelerometer nhịp |
| 28 | `StepCounterService.java` | ✅ | Background step counting |
| 29 | `StepChallengeActivity.java` + layout | ✅ | Pedometer |
| 30 | `QrChallengeActivity.java` + layout | ✅ | ZXing scan |

### Phase 3 – QR Manager + Settings

| # | File / Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 31 | `QrGeneratorActivity.java` + layout | ✅ | Tạo, hiển thị, lưu, in |
| 32 | `SettingsActivity.java` + layout | ✅ | Theme, ngôn ngữ, snooze |

### Phase 4 – Timer / Stopwatch / World Clock

| # | File / Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 33 | Timer UI + logic | ✅ | CountDownTimer |
| 34 | Stopwatch UI + Lap | ✅ | Có RecyclerView LapAdapter |
| 35 | World Clock | ✅ | 10 múi giờ |

### Phase 5 – Polish

| # | File / Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 36 | Notification sắp tới (−30 phút) | ✅ | UpcomingReceiver |
| 37 | Gradual volume | ✅ | Tăng âm dần trong 60s |
| 38 | Auto snooze / dismiss | ✅ | Handler delay |
| 39 | Skip next | ✅ | Nút Skip trên Upcoming Notification |
| 40 | Quick Alarm | ⬜ | Đặt báo thức nhanh (Sẽ bổ sung nếu cần) |
| 41 | Build APK debug + test | 🔄 | Đang chạy Gradle build |

---

## 📅 Nhật ký

| Ngày | Việc đã làm |
|---|---|
| 2026-08-26 | Chốt toàn bộ tech stack, feature list, kiến trúc. Tạo project Android Studio tại D:\Android\SmartAlarm (Java, Groovy DSL, API 26). |
| 2026-08-26 | Hoàn thành Phase 1 phần foundation (file 1–15): build.gradle, Manifest, colors/themes/strings (vi+en), animations, Alarm model, AlarmDao, AppDatabase, AppPreferences, AlarmRepository, AlarmScheduler, AlarmReceiver, AlarmRingingService, BootReceiver, icons. Tiếp theo: BaseActivity, MainActivity, AlarmAdapter, EditAlarmActivity, RingActivity. |
| 2026-08-26 | Hoàn thành Phase 1 UI (16-24), Phase 2 Wake-up Challenges (25-27, 29-30), và cơ bản SettingsActivity (32). |
| | *(Cập nhật tiếp theo sau mỗi phase)* |

---

## 🔁 Hướng dẫn cho AI model tiếp theo

Nếu bạn là AI đọc file này để tiếp tục:

1. **Đọc bảng tiến độ** – tìm dòng đầu tiên còn `⬜` để biết làm tiếp từ đâu
2. **Không thay đổi tech stack** – Java + XML + Groovy, không được dùng Kotlin/Compose
3. **Không đổi package name** – `com.example.smartalarm`
4. **Project path**: `D:\Android\SmartAlarm`
5. **Sau khi viết file** → đánh dấu `✅` trong bảng tiến độ
6. **Adaptive challenge**: sai 3 lần giảm độ khó, sai 5+ lần đề xuất đổi loại
7. **UI**: tối giản, 1 màn hình 1 mục đích, dùng icon
8. **Theme**: dark mode là chủ đạo, accent `#7C6FF7`
