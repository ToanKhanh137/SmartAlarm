package com.example.smartalarm.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.data.repository.AlarmRepository;
import com.example.smartalarm.settings.AppPreferences;

/**
 * AlarmReceiver – BroadcastReceiver nhận các sự kiện liên quan đến báo thức.
 *
 * Các action được xử lý:
 *  1. ACTION_TRIGGER  – AlarmManager báo đến giờ → khởi động AlarmRingingService
 *  2. ACTION_SNOOZE   – Người dùng nhấn Hoãn (từ notification hoặc RingActivity)
 *  3. ACTION_DISMISS  – Người dùng tắt báo thức bình thường
 *  4. ACTION_DISABLE  – Tắt toàn bộ báo thức (từ notification upcoming)
 *
 * QUAN TRỌNG: BroadcastReceiver có thời gian xử lý rất ngắn (~10s).
 * Mọi thao tác database phải chạy nhanh hoặc dùng goAsync()/Service.
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_TRIGGER = "com.example.smartalarm.TRIGGER";
    public static final String ACTION_SNOOZE  = "com.example.smartalarm.SNOOZE";
    public static final String ACTION_DISMISS = "com.example.smartalarm.DISMISS";
    public static final String ACTION_DISABLE = "com.example.smartalarm.DISABLE";

    public static final String EXTRA_ALARM_ID = "alarm_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        int alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1);
        if (alarmId == -1) return;

        String action = intent.getAction();

        switch (action) {
            case ACTION_TRIGGER:
                handleTrigger(context, alarmId);
                break;
            case ACTION_SNOOZE:
                handleSnooze(context, alarmId);
                break;
            case ACTION_DISMISS:
                handleDismiss(context, alarmId);
                break;
            case ACTION_DISABLE:
                handleDisable(context, alarmId);
                break;
        }
    }

    // ===== TRIGGER =====

    /**
     * Khi đến giờ reo:
     * 1. Khởi động AlarmRingingService (foreground service phát nhạc)
     * 2. Nếu báo thức lặp: schedule lần reo tiếp theo
     */
    private void handleTrigger(Context context, int alarmId) {
        // Khởi động foreground service để phát nhạc + rung
        Intent serviceIntent = new Intent(context, AlarmRingingService.class);
        serviceIntent.putExtra(EXTRA_ALARM_ID, alarmId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // Schedule lần tiếp theo nếu là báo thức lặp (chạy trên background thread)
        new Thread(() -> {
            AlarmRepository repo = AlarmRepository.getInstance(context);
            // repo sẽ tự xử lý logic reschedule nếu cần
        }).start();
    }

    // ===== SNOOZE =====

    /**
     * Người dùng nhấn Hoãn:
     * 1. Dừng AlarmRingingService
     * 2. Đặt lại báo thức sau N phút
     */
    private void handleSnooze(Context context, int alarmId) {
        // Dừng service trước
        stopRingingService(context);

        // Lấy thời gian snooze từ preferences
        int snoozeMinutes = AppPreferences.getInstance(context).getSnoozeDuration();

        // Reschedule trên background thread
        new Thread(() -> {
            AlarmRepository.getInstance(context).snoozeSync(alarmId, snoozeMinutes);
        }).start();
    }

    // ===== DISMISS =====

    /**
     * Người dùng tắt báo thức (qua challenge hoặc bình thường):
     * 1. Dừng AlarmRingingService
     * 2. Xử lý hậu dismiss (xóa nếu một lần, giữ nếu lặp)
     */
    private void handleDismiss(Context context, int alarmId) {
        stopRingingService(context);

        new Thread(() -> {
            AlarmRepository.getInstance(context).finishDismissSync(alarmId);
        }).start();
    }

    // ===== DISABLE =====

    /**
     * Tắt toàn bộ báo thức (set inactive, hủy schedule).
     * Dùng trong notification "sắp tới" để người dùng có thể tắt trước khi reo.
     */
    private void handleDisable(Context context, int alarmId) {
        AlarmRepository.getInstance(context).setActive(alarmId, false);
    }

    // ===== HELPER =====

    private void stopRingingService(Context context) {
        Intent stopIntent = new Intent(context, AlarmRingingService.class);
        context.stopService(stopIntent);
    }
}
