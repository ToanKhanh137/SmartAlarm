package com.example.smartalarm.data.repository;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.LiveData;

import com.example.smartalarm.data.database.AlarmDao;
import com.example.smartalarm.data.database.AppDatabase;
import com.example.smartalarm.data.model.Alarm;
import com.example.smartalarm.service.AlarmReceiver;
import com.example.smartalarm.service.AlarmRingingService;
import com.example.smartalarm.service.AlarmScheduler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository – trung gian giữa UI, Room Database và AlarmScheduler.
 *
 * Mọi thao tác database đều chạy trên executor thread riêng (không block UI).
 * UI chỉ quan sát LiveData, không gọi database trực tiếp.
 */
public class AlarmRepository {

    private static AlarmRepository instance;

    private final AlarmDao dao;
    private final AlarmScheduler scheduler;
    private final Context appContext;
    // Single background thread cho DB operations
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AlarmRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        dao = db.alarmDao();
        scheduler = new AlarmScheduler(appContext);
    }

    public static AlarmRepository getInstance(Context context) {
        if (instance == null) {
            instance = new AlarmRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ===== OBSERVE =====

    /** Trả về LiveData danh sách toàn bộ báo thức – UI tự cập nhật. */
    public LiveData<List<Alarm>> observeAll() {
        return dao.getAllAlarms();
    }

    // ===== SAVE (INSERT / UPDATE) =====

    /**
     * Lưu báo thức mới hoặc cập nhật báo thức đã có.
     * Tự động đặt lịch AlarmManager nếu báo thức đang bật.
     * callback nhận id sau khi lưu xong (có thể null nếu không cần).
     */
    public void save(Alarm alarm, OnSaveCallback callback) {
        executor.execute(() -> {
            if (alarm.id == 0) {
                // Insert mới
                long newId = dao.insert(alarm);
                alarm.id = (int) newId;
            } else {
                dao.update(alarm);
                scheduler.cancel(alarm.id);
            }
            if (alarm.isActive) {
                scheduler.schedule(alarm);
            }
            if (callback != null) callback.onSaved(alarm.id);
        });
    }

    // ===== TOGGLE ACTIVE =====

    /**
     * Bật hoặc tắt báo thức.
     * Nếu tắt: hủy lịch AlarmManager.
     * Nếu bật: cần load alarm đầy đủ để schedule lại.
     */
    public void setActive(int alarmId, boolean active) {
        // Tắt toggle lúc đang reo thì phải im ngay.
        if (!active && AlarmRingingService.isRinging && AlarmRingingService.ringingAlarmId == alarmId) {
            stopRinging(alarmId);
        }
        executor.execute(() -> {
            dao.setActive(alarmId, active);
            if (!active) {
                scheduler.cancel(alarmId);
            } else {
                Alarm alarm = dao.getByIdSync(alarmId);
                if (alarm != null) scheduler.schedule(alarm);
            }
        });
    }

    // ===== DELETE =====

    public void delete(int alarmId) {
        // Nếu báo thức này đang reo thì phải tắt nhạc ngay, không đợi ghi DB xong.
        if (AlarmRingingService.isRinging && AlarmRingingService.ringingAlarmId == alarmId) {
            stopRinging(alarmId);
        }
        executor.execute(() -> {
            scheduler.cancel(alarmId);
            dao.deleteById(alarmId);
        });
    }

    /** Tắt báo thức đang reo (dùng khi người dùng xóa hoặc tắt báo thức lúc đang reo). */
    private void stopRinging(int alarmId) {
        Intent intent = new Intent(appContext, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_DISMISS);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        appContext.sendBroadcast(intent);
    }

    // ===== SNOOZE =====

    /**
     * Hoãn báo thức N phút (được gọi từ AlarmReceiver hoặc notification action).
     * Chạy trên background thread vì gọi từ BroadcastReceiver cần xử lý nhanh.
     */
    public void snoozeSync(int alarmId, int snoozeMinutes) {
        Alarm alarm = dao.getByIdSync(alarmId);
        if (alarm == null) return;
        // Báo thức quan trọng chỉ được hoãn một số lần nhất định
        if (!alarm.canSnoozeAgain()) return;
        dao.incrementSnoozeCount(alarmId);
        scheduler.scheduleSnooze(alarm, snoozeMinutes);
    }

    // ===== DISMISS (sau khi tắt báo thức) =====

    /**
     * Xử lý sau khi báo thức bị tắt (dismiss):
     *  - Nếu lặp: đặt lại lần reo tiếp theo (đã làm trong AlarmReceiver)
     *  - Nếu một lần và keepAfterDismiss=false: xóa khỏi DB
     *  - Nếu một lần và keepAfterDismiss=true: giữ lại, set inactive
     */
    public void finishDismissSync(int alarmId) {
        Alarm alarm = dao.getByIdSync(alarmId);
        if (alarm == null) return;
        // Lần reo này kết thúc → cho phép hoãn lại đủ số lần ở lần reo sau
        dao.resetSnoozeCount(alarmId);
        if (!alarm.repeats() && !alarm.isQuickAlarm) {
            // Fix BUG-04: Luôn giữ báo thức lại sau khi kêu xong, chỉ tắt toggle
            dao.setActive(alarmId, false);
        }
        // Báo thức lặp: AlarmReceiver đã schedule lần tiếp theo, không cần làm gì thêm
    }

    // ===== SKIP NEXT =====

    /**
     * Toggle skip lần reo tiếp theo.
     * Nếu đang skip → bỏ skip (skipUntilMillis = 0) rồi reschedule.
     * Nếu chưa skip → tính trigger tiếp theo, set skipUntilMillis = trigger + 1ms.
     */
    public void toggleSkipNext(Alarm alarm) {
        executor.execute(() -> {
            if (alarm.skipUntilMillis > 0) {
                dao.setSkipUntil(alarm.id, 0);
                alarm.skipUntilMillis = 0;
                scheduler.schedule(alarm);
            } else {
                long nextTrigger = scheduler.calculateNextTrigger(alarm);
                if (nextTrigger > 0) {
                    dao.setSkipUntil(alarm.id, nextTrigger + 1);
                    alarm.skipUntilMillis = nextTrigger + 1;
                    scheduler.schedule(alarm);
                }
            }
        });
    }

    // ===== RESCHEDULE ALL (sau reboot / đổi giờ hệ thống) =====

    /**
     * Đặt lại tất cả báo thức đang bật.
     * AlarmManager mất lịch sau khi thiết bị reboot.
     */
    public void rescheduleAll() {
        executor.execute(() -> {
            List<Alarm> active = dao.getActiveAlarmsSync();
            for (Alarm alarm : active) {
                scheduler.schedule(alarm);
            }
        });
    }

    // ===== CALLBACK =====

    public interface OnSaveCallback {
        void onSaved(int alarmId);
    }
}
