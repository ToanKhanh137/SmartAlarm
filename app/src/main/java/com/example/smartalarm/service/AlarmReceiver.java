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
    public static final String ACTION_SKIP_ONCE = "com.example.smartalarm.SKIP_ONCE";

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
            case ACTION_SKIP_ONCE:
                handleSkipOnce(context, alarmId);
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

        // Schedule lần reo tiếp theo nếu là báo thức lặp.
        // calculateNextTrigger() tính từ "hôm nay hh:mm" – thời điểm đó vừa qua nên
        // nó sẽ tự nhảy sang ngày được bật kế tiếp.
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Alarm alarm = com.example.smartalarm.data.database.AppDatabase
                        .getInstance(context).alarmDao().getByIdSync(alarmId);
                if (alarm != null && alarm.isActive && alarm.repeats()) {
                    new AlarmScheduler(context).schedule(alarm);
                }
            } finally {
                pending.finish();
            }
        }).start();
    }

    // ===== SNOOZE =====

    /**
     * Người dùng nhấn Hoãn:
     * 1. Dừng AlarmRingingService
     * 2. Đặt lại báo thức sau N phút
     */
    private void handleSnooze(Context context, int alarmId) {
        int snoozeMinutes = AppPreferences.getInstance(context).getSnoozeDuration();

        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                long triggerAt = AlarmRepository.getInstance(context)
                        .snoozeSync(alarmId, snoozeMinutes);
                // Hết lượt hoãn (báo thức quan trọng) thì phải tiếp tục reo,
                // nếu dừng service ở đây là báo thức biến mất luôn.
                if (triggerAt <= 0) return;

                stopRingingService(context);
                SnoozeNotifier.show(context, alarmId, triggerAt);
            } finally {
                pending.finish();
            }
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

    private void handleSkipOnce(Context context, int alarmId) {
        new Thread(() -> {
            AlarmRepository repo = AlarmRepository.getInstance(context);
            com.example.smartalarm.data.database.AppDatabase db = com.example.smartalarm.data.database.AppDatabase.getInstance(context);
            Alarm alarm = db.alarmDao().getByIdSync(alarmId);
            if (alarm != null) {
                if (alarm.repeats()) {
                    repo.toggleSkipNext(alarm);
                } else {
                    repo.setActive(alarmId, false);
                }
            }
        }).start();
    }

    // ===== HELPER =====

    /**
     * Dừng báo thức qua ACTION_STOP thay vì stopService().
     * stopService() có thể chạy khi service còn đang load alarm từ DB, khiến nhạc
     * vẫn được bật lên sau đó và không còn service nào để tắt nó.
     */
    static void stopRingingService(Context context) {
        if (AlarmRingingService.isRinging) {
            // Service đang là foreground service nên startService() được phép,
            // và không bị giới hạn 5 giây của startForegroundService().
            Intent stopIntent = new Intent(context, AlarmRingingService.class);
            stopIntent.setAction(AlarmRingingService.ACTION_STOP);
            try {
                context.startService(stopIntent);
                return;
            } catch (Exception ignored) {
                // rơi xuống stopService bên dưới
            }
        }
        context.stopService(new Intent(context, AlarmRingingService.class));
    }
}
