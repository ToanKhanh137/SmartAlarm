package com.example.smartalarm.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Entity – đại diện cho một báo thức trong database.
 *
 * Các nhóm thuộc tính:
 *  - Core: id, hour, minute, label, isActive
 *  - Repeat: mon..sun (ngày lặp trong tuần)
 *  - Sound: ringtoneUri, volume, gradualVolume, vibrate
 *  - Snooze: snoozeMinutes
 *  - Auto: autoAction, autoAfterMinutes
 *  - Challenge: challengeType, difficulty, customValue, qrCode
 *  - Advanced: skipUntilMillis, keepAfterDismiss
 *  - Quick alarm: isQuickAlarm, triggerAtMillis
 */
@Entity(tableName = "alarms")
public class Alarm {

    // ===== CONSTANTS – challenge types =====
    public static final int CHALLENGE_NONE  = 0;
    public static final int CHALLENGE_MATH  = 1;
    public static final int CHALLENGE_SHAKE = 2;
    public static final int CHALLENGE_SQUAT = 3;
    public static final int CHALLENGE_STEP  = 4;
    public static final int CHALLENGE_QR    = 5;

    // ===== CONSTANTS – difficulty levels =====
    public static final int DIFFICULTY_EASY   = 0;
    public static final int DIFFICULTY_MEDIUM = 1;
    public static final int DIFFICULTY_HARD   = 2;
    public static final int DIFFICULTY_CUSTOM = 3;

    // ===== CONSTANTS – phép tính cho MATH custom (bitmask) =====
    public static final int OP_ADD = 1;
    public static final int OP_SUB = 2;
    public static final int OP_MUL = 4;
    public static final int OP_DIV = 8;

    /** Tập phép tính mặc định khi người dùng chưa chọn gì: cộng và trừ. */
    public static final int OPS_DEFAULT = OP_ADD | OP_SUB;

    /** Báo thức quan trọng chỉ được hoãn tối đa bấy nhiêu lần. */
    public static final int MAX_SNOOZE_IMPORTANT = 2;

    private static final int MATH_OPS_MULTIPLIER = 1000;

    // ===== CONSTANTS – auto action =====
    public static final int AUTO_NONE    = 0;
    public static final int AUTO_SNOOZE  = 1;
    public static final int AUTO_DISMISS = 2;

    // ===== CORE =====
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int hour;            // 0–23
    public int minute;          // 0–59
    public String label;        // tên báo thức, có thể rỗng
    public boolean isActive;    // báo thức đang bật hay tắt

    // ===== REPEAT (ngày lặp) =====
    public boolean mon;
    public boolean tue;
    public boolean wed;
    public boolean thu;
    public boolean fri;
    public boolean sat;
    public boolean sun;

    // ===== SOUND =====
    public String ringtoneUri;  // null = dùng mặc định hệ thống, "silent" = im lặng
    public int volume;          // 0–100
    public boolean gradualVolume; // tăng âm lượng dần trong 60 giây
    public boolean vibrate;
    /** Mỗi lần reo chọn ngẫu nhiên một bài trong shufflePlaylist, bỏ qua ringtoneUri. */
    public boolean shuffleRingtone;
    /**
     * Danh sách nhạc để bốc ngẫu nhiên, các URI ngăn nhau bởi "\n".
     * Rỗng = dùng toàn bộ nhạc báo thức của hệ thống.
     */
    public String shufflePlaylist;

    // ===== SNOOZE =====
    public int snoozeMinutes;   // số phút hoãn, mặc định 5
    /**
     * Báo thức quan trọng: chỉ hoãn được tối đa MAX_SNOOZE_IMPORTANT lần và âm lượng
     * không bao giờ tự nhỏ đi. Báo thức thường: hoãn thoải mái, kêu lâu thì nhỏ dần.
     */
    public boolean importantAlarm;
    /** Số lần đã hoãn cho lần reo hiện tại, về 0 khi tắt hẳn. */
    public int snoozeCount;

    // ===== AUTO ACTION =====
    public int autoAction;          // AUTO_NONE / AUTO_SNOOZE / AUTO_DISMISS
    public int autoAfterMinutes;    // số phút chờ trước khi tự xử lý

    // ===== WAKE-UP CHALLENGE =====
    public int challengeType;   // CHALLENGE_* constant
    public int difficulty;      // DIFFICULTY_* constant
    /**
     * customValue: ý nghĩa tùy theo challengeType
     *  - MATH:  không dùng (dùng difficulty)
     *  - SHAKE: số lần lắc (5–50)
     *  - SQUAT: số lần squat (3–30)
     *  - STEP:  số bước (10–200)
     *  - QR:    không dùng (dùng qrCode)
     */
    public int customValue;
    /**
     * qrCode: chuỗi bí mật dùng để xác thực mã QR.
     * Được lưu khi người dùng tạo QR trong QrGeneratorActivity.
     * Khi quét, so sánh nội dung QR với chuỗi này.
     */
    public String qrCode;

    // ===== ADVANCED =====
    /**
     * skipUntilMillis: bỏ qua lần reo tiếp theo nếu triggerTime < skipUntilMillis.
     * 0 = không skip.
     */
    public long skipUntilMillis;
    /**
     * keepAfterDismiss: nếu true, báo thức một lần (không lặp) vẫn được giữ lại
     * trong danh sách sau khi tắt (ở trạng thái inactive).
     */
    public boolean keepAfterDismiss;

    // ===== QUICK ALARM =====
    public boolean isQuickAlarm;   // quick alarm không có ngày lặp
    public long triggerAtMillis;   // thời điểm tuyệt đối (ms) cho quick alarm

    // ===== CONSTRUCTOR – giá trị mặc định hợp lý =====
    public Alarm() {
        hour = 7;
        minute = 0;
        label = "";
        isActive = true;
        // Không lặp theo mặc định
        mon = tue = wed = thu = fri = sat = sun = false;
        ringtoneUri = null;         // dùng ringtone mặc định
        volume = 80;
        gradualVolume = false;
        vibrate = true;
        shuffleRingtone = false;
        shufflePlaylist = null;
        snoozeMinutes = 5;
        importantAlarm = false;
        snoozeCount = 0;
        autoAction = AUTO_NONE;
        autoAfterMinutes = 5;
        challengeType = CHALLENGE_NONE;
        difficulty = DIFFICULTY_MEDIUM;
        customValue = 0;
        qrCode = null;
        skipUntilMillis = 0;
        keepAfterDismiss = false;
        isQuickAlarm = false;
        triggerAtMillis = 0;
    }

    // ===== HELPER METHODS =====

    /**
     * Kiểm tra báo thức có lặp theo ít nhất một ngày trong tuần không.
     */
    public boolean repeats() {
        return mon || tue || wed || thu || fri || sat || sun;
    }

    /**
     * Tên hiển thị: nếu label rỗng, trả về chuỗi giờ phút.
     */
    public String getDisplayLabel() {
        if (label != null && !label.isEmpty()) return label;
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * Lấy giá trị mặc định cho customValue dựa trên challengeType và difficulty.
     * Dùng khi người dùng chọn Easy/Medium/Hard (không phải Custom).
     */
    public int getEffectiveCount() {
        // MATH không dùng số lần: customValue của nó là giá trị đóng gói,
        // đọc qua mathQuestionCount() / mathOpsMask().
        if (challengeType == CHALLENGE_MATH) return 0;
        if (difficulty == DIFFICULTY_CUSTOM) return customValue;
        switch (challengeType) {
            case CHALLENGE_SHAKE:
                return difficulty == DIFFICULTY_EASY ? 10 : difficulty == DIFFICULTY_MEDIUM ? 20 : 35;
            case CHALLENGE_SQUAT:
                return difficulty == DIFFICULTY_EASY ? 5  : difficulty == DIFFICULTY_MEDIUM ? 10 : 20;
            case CHALLENGE_STEP:
                return difficulty == DIFFICULTY_EASY ? 20 : difficulty == DIFFICULTY_MEDIUM ? 50 : 100;
            default:
                return 0;
        }
    }

    /** Danh sách nhạc đã chọn để shuffle. Rỗng nghĩa là dùng toàn bộ nhạc hệ thống. */
    public java.util.List<String> shuffleUris() {
        java.util.List<String> uris = new java.util.ArrayList<>();
        if (shufflePlaylist == null || shufflePlaylist.isEmpty()) return uris;
        for (String uri : shufflePlaylist.split("\n")) {
            if (!uri.isEmpty()) uris.add(uri);
        }
        return uris;
    }

    public void setShuffleUris(java.util.List<String> uris) {
        shufflePlaylist = (uris == null || uris.isEmpty()) ? null : String.join("\n", uris);
    }

    /** Báo thức thường hoãn bao nhiêu lần cũng được; báo thức quan trọng thì có hạn. */
    public boolean canSnoozeAgain() {
        return !importantAlarm || snoozeCount < MAX_SNOOZE_IMPORTANT;
    }

    /** Số lần hoãn còn lại, -1 nghĩa là không giới hạn. */
    public int snoozesLeft() {
        return importantAlarm ? Math.max(0, MAX_SNOOZE_IMPORTANT - snoozeCount) : -1;
    }

    /**
     * MATH custom cần lưu 2 thông tin (tập phép tính + số câu hỏi) trong một cột int,
     * nên đóng gói dạng opsMask * 1000 + questionCount.
     */
    public static int packMathCustom(int opsMask, int questionCount) {
        return opsMask * MATH_OPS_MULTIPLIER + questionCount;
    }

    /** Số câu hỏi phải trả lời đúng khi độ khó là Custom. */
    public int mathQuestionCount() {
        int count = customValue % MATH_OPS_MULTIPLIER;
        return count > 0 ? count : 5;
    }

    /** Bitmask các phép tính được dùng khi độ khó là Custom. */
    public int mathOpsMask() {
        int mask = customValue / MATH_OPS_MULTIPLIER;
        return mask > 0 ? mask : OPS_DEFAULT;
    }

    /**
     * Kiểm tra ngày cụ thể (1=Mon, 7=Sun theo Calendar.DAY_OF_WEEK style dịch chuyển)
     * dayOfWeek: Calendar.MONDAY=2 ... Calendar.SUNDAY=1
     */
    public boolean isEnabledOnDay(int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case 2: return mon;
            case 3: return tue;
            case 4: return wed;
            case 5: return thu;
            case 6: return fri;
            case 7: return sat;
            case 1: return sun;
            default: return false;
        }
    }
}
