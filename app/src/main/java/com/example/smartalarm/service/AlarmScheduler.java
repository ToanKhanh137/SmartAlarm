package com.example.smartalarm.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.smartalarm.data.model.Alarm;

import java.util.Calendar;

/**
 * AlarmScheduler – tính thời gian reo tiếp theo và giao tiếp với AlarmManager.
 *
 * Sử dụng setAlarmClock() để đảm bảo báo thức hoạt động trong Doze mode.
 * setAlarmClock() là cách duy nhất đáng tin cậy cho alarm app thực sự.
 */
public class AlarmScheduler {

    private final Context context;
    private final AlarmManager alarmManager;

    // Request code offset để tránh conflict PendingIntent giữa các alarm
    private static final int PENDING_INTENT_BASE = 1000;

    public AlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    // ===== SCHEDULE =====

    /**
     * Đặt báo thức. Tính lần reo tiếp theo và gọi AlarmManager.
     * Nếu không tính được (alarm không lặp và giờ đã qua), không làm gì.
     */
    public void schedule(Alarm alarm) {
        long triggerTime = calculateNextTrigger(alarm);
        if (triggerTime <= 0) return;

        PendingIntent pi = buildPendingIntent(alarm.id);

        // setAlarmClock = hiển thị icon đồng hồ trên status bar + hoạt động trong Doze
        AlarmManager.AlarmClockInfo info =
                new AlarmManager.AlarmClockInfo(triggerTime, pi);
        alarmManager.setAlarmClock(info, pi);

        // Đặt Upcoming Notification trước 30 phút
        long upcomingTime = triggerTime - (30 * 60 * 1000);
        if (upcomingTime > System.currentTimeMillis()) {
            Intent upIntent = new Intent(context, UpcomingReceiver.class);
            upIntent.setAction(UpcomingReceiver.ACTION_UPCOMING);
            upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_ID, alarm.id);
            upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_LABEL, alarm.label);
            
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent piUpcoming = PendingIntent.getBroadcast(
                    context, PENDING_INTENT_BASE + alarm.id + 10000, upIntent, flags);
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, upcomingTime, piUpcoming);
        }
    }

    /**
     * Đặt báo thức Snooze (N phút từ bây giờ).
     */
    public void scheduleSnooze(Alarm alarm, int snoozeMinutes) {
        long triggerTime = System.currentTimeMillis() + (long) snoozeMinutes * 60 * 1000;
        PendingIntent pi = buildPendingIntent(alarm.id);
        AlarmManager.AlarmClockInfo info =
                new AlarmManager.AlarmClockInfo(triggerTime, pi);
        alarmManager.setAlarmClock(info, pi);
    }

    // ===== CANCEL =====

    /** Hủy báo thức khỏi AlarmManager. */
    public void cancel(int alarmId) {
        PendingIntent pi = buildPendingIntent(alarmId);
        alarmManager.cancel(pi);
        pi.cancel();
    }

    // ===== CALCULATE NEXT TRIGGER =====

    /**
     * Tính thời điểm reo tiếp theo (milliseconds).
     * Trả về 0 nếu không tính được (alarm đã hết hạn).
     */
    public long calculateNextTrigger(Alarm alarm) {
        // Quick alarm: dùng thời gian tuyệt đối
        if (alarm.isQuickAlarm) {
            if (alarm.triggerAtMillis > System.currentTimeMillis()) {
                return alarm.triggerAtMillis;
            }
            return 0;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
        cal.set(Calendar.MINUTE, alarm.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Nếu giờ hôm nay đã qua, chuyển sang ngày mai
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (!alarm.repeats()) {
            // Báo thức một lần: trigger là cal hiện tại (hôm nay hoặc ngày mai)
            long trigger = cal.getTimeInMillis();
            // Kiểm tra skip
            if (alarm.skipUntilMillis > 0 && trigger < alarm.skipUntilMillis) {
                return 0; // bị skip, không đặt lịch
            }
            return trigger;
        }

        // Báo thức lặp: tìm ngày tiếp theo được bật
        for (int i = 0; i < 7; i++) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun, 2=Mon...7=Sat
            if (alarm.isEnabledOnDay(dayOfWeek)) {
                long trigger = cal.getTimeInMillis();
                // Kiểm tra skip
                if (alarm.skipUntilMillis > 0 && trigger < alarm.skipUntilMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    continue;
                }
                return trigger;
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return 0; // Không tìm được ngày nào (không thể xảy ra nếu repeats() = true)
    }

    // ===== PERMISSION CHECK =====

    /**
     * Kiểm tra quyền đặt báo thức chính xác (Android 12+).
     * Trả về true nếu được phép hoặc API < 31.
     */
    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Tạo PendingIntent để trigger AlarmReceiver khi đến giờ.
     * requestCode = PENDING_INTENT_BASE + alarmId để mỗi alarm có PI riêng.
     */
    private PendingIntent buildPendingIntent(int alarmId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_TRIGGER);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(
                context,
                PENDING_INTENT_BASE + alarmId,
                intent,
                flags
        );
    }
}
