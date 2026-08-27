package com.example.smartalarm.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Wrapper cho SharedPreferences – lưu cài đặt toàn cục của app.
 *
 * Keys:
 *  - theme:           "dark" / "light"
 *  - language:        "vi" / "en"
 *  - snooze_enabled:  boolean (true = hiện nút Snooze)
 *  - snooze_duration: int (số phút, mặc định 5)
 *  - qr_code:         String (mã QR đã đăng ký để dùng trong QR challenge)
 */
public class AppPreferences {

    private static final String PREFS_NAME      = "smart_alarm_prefs";
    private static final String KEY_THEME       = "theme";
    private static final String KEY_LANGUAGE    = "language";
    private static final String KEY_SNOOZE_ON   = "snooze_enabled";
    private static final String KEY_SNOOZE_MIN  = "snooze_duration";
    private static final String KEY_QR_CODE     = "qr_code";

    public static final String THEME_DARK   = "dark";
    public static final String THEME_LIGHT  = "light";
    public static final String THEME_SYSTEM = "system";

    private static AppPreferences instance;
    private final SharedPreferences prefs;

    private AppPreferences(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AppPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new AppPreferences(context);
        }
        return instance;
    }

    // ===== THEME =====

    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
    }

    public boolean isDarkTheme() {
        return THEME_DARK.equals(getTheme());
    }

    // ===== LANGUAGE =====

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "vi");
    }

    public void setLanguage(String lang) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply();
    }

    // ===== SNOOZE ENABLED =====

    /**
     * Trả về true nếu tính năng Snooze đang được bật.
     * Khi false, nút Snooze sẽ bị ẩn ở cả RingActivity và notification.
     */
    public boolean isSnoozeEnabled() {
        return prefs.getBoolean(KEY_SNOOZE_ON, true);
    }

    public void setSnoozeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SNOOZE_ON, enabled).apply();
    }

    // ===== SNOOZE DURATION =====

    public int getSnoozeDuration() {
        return prefs.getInt(KEY_SNOOZE_MIN, 5);
    }

    public void setSnoozeDuration(int minutes) {
        prefs.edit().putInt(KEY_SNOOZE_MIN, minutes).apply();
    }

    // ===== QR CODE =====

    /**
     * Lưu chuỗi bí mật dùng để xác thực mã QR.
     * QrGeneratorActivity sẽ encode chuỗi này vào ảnh QR.
     * QrChallengeActivity sẽ decode và so sánh với chuỗi này.
     */
    public String getQrCode() {
        return prefs.getString(KEY_QR_CODE, null);
    }

    public void setQrCode(String code) {
        prefs.edit().putString(KEY_QR_CODE, code).apply();
    }

    public boolean hasQrCode() {
        return getQrCode() != null && !getQrCode().isEmpty();
    }
}
