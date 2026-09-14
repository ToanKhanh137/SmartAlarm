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

    /** Thông báo "sắp tới" hiện trước giờ reo bao lâu. */
    private static final long UPCOMING_LEAD_MS = 30 * 60 * 1000L;
    /** Gần hơn mức này thì không cần thông báo "sắp tới" nữa. */
    private static final long UPCOMING_MIN_LEAD_MS = 45 * 1000L;

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

        scheduleUpcomingNotification(alarm, triggerTime);
    }

    /**
     * Thông báo "sắp tới" mặc định hiện trước 30 phút. Nếu báo thức gần hơn thế thì mốc
     * đó đã ở quá khứ và thông báo sẽ không bao giờ chạy, nên trường hợp đó hiện luôn –
     * miễn là còn kịp trước khi reo.
     */
    private void scheduleUpcomingNotification(Alarm alarm, long triggerTime) {
        long now = System.currentTimeMillis();
        long upcomingTime = triggerTime - UPCOMING_LEAD_MS;

        if (upcomingTime <= now) {
            if (triggerTime - now < UPCOMING_MIN_LEAD_MS) return; // quá sát giờ reo
            upcomingTime = now + 5000;
        }

        PendingIntent piUpcoming = buildUpcomingPendingIntent(alarm, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, upcomingTime, piUpcoming);
    }

    /**
     * Đặt báo thức Snooze (N phút từ bây giờ).
     */
    /** Trả về thời điểm sẽ reo lại (ms). */
    public long scheduleSnooze(Alarm alarm, int snoozeMinutes) {
        long triggerTime = System.currentTimeMillis() + (long) snoozeMinutes * 60 * 1000;
        PendingIntent pi = buildPendingIntent(alarm.id);
        AlarmManager.AlarmClockInfo info =
                new AlarmManager.AlarmClockInfo(triggerTime, pi);
        alarmManager.setAlarmClock(info, pi);
        return triggerTime;
    }

    // ===== CANCEL =====

    /** Hủy báo thức khỏi AlarmManager, kể cả thông báo "sắp tới" đã đặt trước 30 phút. */
    public void cancel(int alarmId) {
        PendingIntent pi = buildPendingIntent(alarmId);
        alarmManager.cancel(pi);
        pi.cancel();

        PendingIntent piUpcoming = buildUpcomingPendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE);
        if (piUpcoming != null) {
            alarmManager.cancel(piUpcoming);
            piUpcoming.cancel();
        }
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

    private PendingIntent buildUpcomingPendingIntent(Alarm alarm, int extraFlags) {
        Intent upIntent = new Intent(context, UpcomingReceiver.class);
        upIntent.setAction(UpcomingReceiver.ACTION_UPCOMING);
        upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_ID, alarm.id);
        upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_LABEL, alarm.label);
        upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_HOUR, alarm.hour);
        upIntent.putExtra(UpcomingReceiver.EXTRA_ALARM_MINUTE, alarm.minute);
        upIntent.putExtra(UpcomingReceiver.EXTRA_CHALLENGE_TYPE, alarm.challengeType);
        return PendingIntent.getBroadcast(context,
                upcomingRequestCode(alarm.id), upIntent, pendingFlags(extraFlags));
    }

    /** Dùng khi hủy: chỉ cần trùng requestCode + Intent component, không cần extras. */
    private PendingIntent buildUpcomingPendingIntent(int alarmId, int extraFlags) {
        Intent upIntent = new Intent(context, UpcomingReceiver.class);
        upIntent.setAction(UpcomingReceiver.ACTION_UPCOMING);
        return PendingIntent.getBroadcast(context,
                upcomingRequestCode(alarmId), upIntent, pendingFlags(extraFlags));
    }

    private static int upcomingRequestCode(int alarmId) {
        return PENDING_INTENT_BASE + alarmId + 10000;
    }

    private static int pendingFlags(int extraFlags) {
        int flags = extraFlags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
