package com.example.smartalarm.settings;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Bọc Context theo ngôn ngữ người dùng chọn trong Settings.
 * Service và BroadcastReceiver không tự áp dụng locale của app nên phải gọi
 * wrap() trước khi getString(), nếu không notification sẽ hiện theo ngôn ngữ hệ thống.
 */
public final class LocaleHelper {

    private LocaleHelper() {}

    /** Context với locale (và uiMode) của app – dùng cho mọi getString() ngoài Activity. */
    public static Context wrap(Context base) {
        AppPreferences prefs = AppPreferences.getInstance(base);
        return wrap(base, prefs.getLanguage());
    }

    public static Context wrap(Context base, String language) {
        Locale locale = new Locale(language);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    /**
     * Context với cả locale và night mode của app.
     * Activity cần cái này để màu light/dark không bị lấy lẫn giữa hai bộ resource.
     */
    public static Context wrapWithTheme(Context base) {
        AppPreferences prefs = AppPreferences.getInstance(base);
        Locale locale = new Locale(prefs.getLanguage());
        Locale.setDefault(locale);

        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);

        String theme = prefs.getTheme();
        if (AppPreferences.THEME_LIGHT.equals(theme)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
        } else if (AppPreferences.THEME_DARK.equals(theme)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
        }
        return base.createConfigurationContext(config);
    }
}
